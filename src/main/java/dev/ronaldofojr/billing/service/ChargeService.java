package dev.ronaldofojr.billing.service;

import dev.ronaldofojr.billing.domain.AttemptStatus;
import dev.ronaldofojr.billing.domain.Invoice;
import dev.ronaldofojr.billing.domain.InvoiceStatus;
import dev.ronaldofojr.billing.domain.PaymentAttempt;
import dev.ronaldofojr.billing.domain.Subscription;
import dev.ronaldofojr.billing.domain.SubscriptionStatus;
import dev.ronaldofojr.billing.gateway.ChargeRequest;
import dev.ronaldofojr.billing.gateway.GatewayUnavailableException;
import dev.ronaldofojr.billing.gateway.PaymentGateway;
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
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Faz uma tentativa de cobranca de uma fatura. A chamada ao gateway (rede) fica FORA das
 * transacoes do banco: primeiro grava a tentativa, depois chama o gateway, depois grava o retorno.
 */
@Service
public class ChargeService {

    private static final Logger log = LoggerFactory.getLogger(ChargeService.class);

    private record StartedAttempt(UUID attemptId, UUID invoiceId, java.math.BigDecimal amount, String token) {
    }

    private final InvoiceRepository invoices;
    private final SubscriptionRepository subscriptions;
    private final PaymentAttemptRepository attempts;
    private final PaymentGateway gateway;
    private final PaymentResultService results;
    private final TransactionTemplate tx;
    private final Clock clock;

    public ChargeService(InvoiceRepository invoices, SubscriptionRepository subscriptions,
                         PaymentAttemptRepository attempts, PaymentGateway gateway,
                         PaymentResultService results, TransactionTemplate tx, Clock clock) {
        this.invoices = invoices;
        this.subscriptions = subscriptions;
        this.attempts = attempts;
        this.gateway = gateway;
        this.results = results;
        this.tx = tx;
        this.clock = clock;
    }

    /** @return true se uma cobranca foi enviada ao gateway */
    public boolean charge(UUID invoiceId) {
        StartedAttempt started = tx.execute(status -> beginAttempt(invoiceId));
        if (started == null) {
            return false;
        }

        try {
            String chargeId = gateway.charge(
                    new ChargeRequest(started.attemptId(), started.invoiceId(), started.amount(), started.token()));
            tx.executeWithoutResult(status -> attempts.findById(started.attemptId()).orElseThrow().assignCharge(chargeId));
            return true;
        } catch (GatewayUnavailableException e) {
            log.warn("Gateway indisponivel na fatura {}: {}", invoiceId, e.getMessage());
            results.applyFailure(started.attemptId(), e.getMessage());
            return false;
        }
    }

    private StartedAttempt beginAttempt(UUID invoiceId) {
        Optional<Invoice> found = invoices.findById(invoiceId);
        if (found.isEmpty() || !found.get().canBeCharged()) {
            return null;
        }
        Invoice invoice = found.get();
        Instant now = clock.instant();

        // ja existe cobranca esperando resposta do gateway: cobrar de novo seria cobrar em duplicidade
        if (attempts.existsByInvoiceIdAndStatus(invoiceId, AttemptStatus.PENDING)) {
            return null;
        }
        if (invoice.getStatus() == InvoiceStatus.FAILED && invoice.getNextRetryAt().isAfter(now)) {
            return null;
        }

        Subscription subscription = subscriptions.findById(invoice.getSubscriptionId()).orElseThrow();
        if (subscription.getStatus() == SubscriptionStatus.CANCELED) {
            invoice.registerFailure("Assinatura cancelada", Optional.empty());
            return null;
        }

        int number = invoice.startAttempt();
        PaymentAttempt attempt = attempts.save(new PaymentAttempt(invoiceId, number, now));
        return new StartedAttempt(attempt.getId(), invoiceId, invoice.getAmount(), subscription.getPaymentToken());
    }
}
