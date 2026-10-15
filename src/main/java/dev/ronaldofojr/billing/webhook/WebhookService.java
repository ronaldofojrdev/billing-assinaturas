package dev.ronaldofojr.billing.webhook;

import dev.ronaldofojr.billing.domain.PaymentAttempt;
import dev.ronaldofojr.billing.repository.PaymentAttemptRepository;
import dev.ronaldofojr.billing.repository.WebhookEventRepository;
import dev.ronaldofojr.billing.service.PaymentResultService;
import java.time.Clock;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WebhookService {

    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);

    private final WebhookEventRepository events;
    private final PaymentAttemptRepository attempts;
    private final PaymentResultService results;
    private final Clock clock;

    public WebhookService(WebhookEventRepository events, PaymentAttemptRepository attempts,
                          PaymentResultService results, Clock clock) {
        this.events = events;
        this.attempts = attempts;
        this.results = results;
        this.clock = clock;
    }

    /**
     * Tudo numa transacao so: o registro do evento e o efeito na fatura entram juntos ou nao entram.
     * Se o processamento falhar, o evento NAO fica gravado e o reenvio do gateway e processado normalmente.
     * Se dois envios iguais chegarem ao mesmo tempo, so um consegue registrar o evento (INSERT ... ON CONFLICT
     * DO NOTHING); os outros recebem DUPLICATE.
     */
    @Transactional
    public WebhookResult handle(PaymentWebhook event) {
        boolean succeeded = PaymentWebhook.SUCCEEDED.equals(event.type());
        boolean failed = PaymentWebhook.FAILED.equals(event.type());
        if (!succeeded && !failed) {
            if (!registerEvent(event)) {
                return WebhookResult.DUPLICATE;
            }
            log.info("Webhook {} de tipo {} nao tratado, ignorando", event.eventId(), event.type());
            return WebhookResult.IGNORED;
        }

        Optional<PaymentAttempt> attempt = attempts.findByGatewayChargeId(event.chargeId());
        if (attempt.isEmpty()) {
            log.warn("Webhook {} para cobranca desconhecida {}", event.eventId(), event.chargeId());
            return WebhookResult.UNKNOWN_CHARGE;
        }

        // registrar o evento e a unica barreira contra duplicidade; so quem consegue registrar processa
        if (!registerEvent(event)) {
            return WebhookResult.DUPLICATE;
        }
        if (succeeded) {
            results.applySuccess(attempt.get().getId());
        } else {
            String reason = event.reason() == null || event.reason().isBlank() ? "Pagamento recusado" : event.reason();
            results.applyFailure(attempt.get().getId(), reason);
        }
        return WebhookResult.PROCESSED;
    }

    private boolean registerEvent(PaymentWebhook event) {
        boolean registered = events.insertIfAbsent(event.eventId(), event.type(), clock.instant()) == 1;
        if (!registered) {
            log.info("Webhook {} duplicado, ignorando", event.eventId());
        }
        return registered;
    }
}
