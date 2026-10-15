package dev.ronaldofojr.billing.service;

import dev.ronaldofojr.billing.domain.Invoice;
import dev.ronaldofojr.billing.domain.InvoiceStatus;
import dev.ronaldofojr.billing.domain.PaymentAttempt;
import dev.ronaldofojr.billing.domain.Subscription;
import dev.ronaldofojr.billing.repository.InvoiceRepository;
import dev.ronaldofojr.billing.repository.PaymentAttemptRepository;
import dev.ronaldofojr.billing.repository.SubscriptionRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aplica o resultado de uma tentativa de cobranca na fatura e na assinatura.
 * Usado pelo webhook, pela falha direta do gateway e pelo timeout de cobrancas sem resposta.
 */
@Service
public class PaymentResultService {

    private static final Logger log = LoggerFactory.getLogger(PaymentResultService.class);

    private final PaymentAttemptRepository attempts;
    private final InvoiceRepository invoices;
    private final SubscriptionRepository subscriptions;
    private final RetryPolicy retryPolicy;
    private final Clock clock;

    public PaymentResultService(PaymentAttemptRepository attempts, InvoiceRepository invoices,
                                SubscriptionRepository subscriptions, RetryPolicy retryPolicy, Clock clock) {
        this.attempts = attempts;
        this.invoices = invoices;
        this.subscriptions = subscriptions;
        this.retryPolicy = retryPolicy;
        this.clock = clock;
    }

    @Transactional
    public void applySuccess(UUID attemptId) {
        PaymentAttempt attempt = attempts.findById(attemptId).orElseThrow();
        Invoice invoice = invoices.findById(attempt.getInvoiceId()).orElseThrow();
        Instant now = clock.instant();

        if (invoice.getStatus() == InvoiceStatus.PAID) {
            // o dinheiro entrou duas vezes (ex: uma tentativa deu timeout e a cobranca passou mesmo assim)
            log.warn("Fatura {} ja estava paga e a tentativa {} tambem foi aprovada: possivel cobranca em duplicidade, conferir estorno",
                    invoice.getId(), attempt.getAttemptNumber());
            if (attempt.isPending()) {
                attempt.markSucceeded(now);
            }
            return;
        }

        // o gateway diz que cobrou, entao o dinheiro vale mesmo que a tentativa tivesse sido dada como falha
        attempt.markSucceeded(now);
        invoice.markPaid(now);
        Subscription subscription = subscriptions.findById(invoice.getSubscriptionId()).orElseThrow();
        subscription.reactivate();
        log.info("Fatura {} paga (tentativa {})", invoice.getId(), attempt.getAttemptNumber());
    }

    @Transactional
    public void applyFailure(UUID attemptId, String reason) {
        PaymentAttempt attempt = attempts.findById(attemptId).orElseThrow();
        if (!attempt.isPending()) {
            log.info("Tentativa {} ja estava resolvida ({}), ignorando falha", attemptId, attempt.getStatus());
            return;
        }
        Instant now = clock.instant();
        attempt.markFailed(reason, now);

        Invoice invoice = invoices.findById(attempt.getInvoiceId()).orElseThrow();
        if (invoice.getStatus() == InvoiceStatus.PAID || invoice.getStatus() == InvoiceStatus.UNCOLLECTIBLE) {
            return;
        }

        Optional<Instant> retryAt = retryPolicy.nextRetryAfterFailure(invoice.getAttempts(), now);
        invoice.registerFailure(reason, retryAt);

        Subscription subscription = subscriptions.findById(invoice.getSubscriptionId()).orElseThrow();
        if (retryAt.isPresent()) {
            subscription.markPastDue();
            log.warn("Fatura {} falhou ({}), nova tentativa em {}", invoice.getId(), reason, retryAt.get());
        } else {
            subscription.cancel(now);
            log.warn("Fatura {} falhou em todas as {} tentativas, assinatura {} cancelada",
                    invoice.getId(), invoice.getAttempts(), subscription.getId());
        }
    }
}
