package dev.ronaldofojr.billing.service;

import dev.ronaldofojr.billing.config.BillingProperties;
import dev.ronaldofojr.billing.domain.AttemptStatus;
import dev.ronaldofojr.billing.domain.Invoice;
import dev.ronaldofojr.billing.domain.InvoiceStatus;
import dev.ronaldofojr.billing.domain.PaymentAttempt;
import dev.ronaldofojr.billing.domain.Plan;
import dev.ronaldofojr.billing.domain.Subscription;
import dev.ronaldofojr.billing.domain.SubscriptionStatus;
import dev.ronaldofojr.billing.repository.InvoiceRepository;
import dev.ronaldofojr.billing.repository.PaymentAttemptRepository;
import dev.ronaldofojr.billing.repository.PlanRepository;
import dev.ronaldofojr.billing.repository.SubscriptionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Ciclo de cobranca recorrente, retry de faturas e limpeza de cobrancas sem resposta. */
@Service
public class BillingService {

    private static final Logger log = LoggerFactory.getLogger(BillingService.class);

    private final SubscriptionRepository subscriptions;
    private final PlanRepository plans;
    private final InvoiceRepository invoices;
    private final PaymentAttemptRepository attempts;
    private final ChargeService chargeService;
    private final PaymentResultService results;
    private final BillingProperties properties;
    private final TransactionTemplate tx;
    private final Clock clock;

    public BillingService(SubscriptionRepository subscriptions, PlanRepository plans, InvoiceRepository invoices,
                          PaymentAttemptRepository attempts, ChargeService chargeService,
                          PaymentResultService results, BillingProperties properties,
                          TransactionTemplate tx, Clock clock) {
        this.subscriptions = subscriptions;
        this.plans = plans;
        this.invoices = invoices;
        this.attempts = attempts;
        this.chargeService = chargeService;
        this.results = results;
        this.properties = properties;
        this.tx = tx;
        this.clock = clock;
    }

    /**
     * Gera e cobra a fatura de toda assinatura ACTIVE com vencimento ate hoje.
     * Rodar duas vezes no mesmo dia nao gera faturas duplicadas: a primeira execucao avanca
     * a data de cobranca, e a unique (assinatura, inicio do periodo) barra qualquer corrida.
     *
     * @return quantas faturas novas foram geradas
     */
    public int runBillingCycle() {
        LocalDate today = LocalDate.now(clock);
        List<UUID> due = subscriptions.findIdsDue(SubscriptionStatus.ACTIVE, today);
        log.info("Ciclo de cobranca: {} assinaturas vencidas ate {}", due.size(), today);

        int generated = 0;
        for (UUID subscriptionId : due) {
            try {
                if (billSubscription(subscriptionId).isPresent()) {
                    generated++;
                }
            } catch (RuntimeException e) {
                // uma assinatura com problema nao pode derrubar o ciclo das outras
                log.error("Falha ao cobrar a assinatura {}", subscriptionId, e);
            }
        }
        return generated;
    }

    /** Gera a fatura do periodo atual (se a assinatura estiver vencida) e tenta cobrar. */
    public Optional<UUID> billSubscription(UUID subscriptionId) {
        Optional<UUID> invoiceId = Optional.ofNullable(tx.execute(status -> createInvoice(subscriptionId)));
        invoiceId.ifPresent(chargeService::charge);
        return invoiceId;
    }

    private UUID createInvoice(UUID subscriptionId) {
        LocalDate today = LocalDate.now(clock);
        Subscription subscription = subscriptions.findById(subscriptionId).orElseThrow();
        if (!subscription.isDueOn(today)) {
            return null; // outra execucao ja cuidou dela
        }
        Plan plan = plans.findById(subscription.getPlanId()).orElseThrow();

        LocalDate periodStart = subscription.getNextBillingDate();
        LocalDate periodEnd = plan.getBillingInterval().addTo(periodStart);
        Invoice invoice = invoices.saveAndFlush(new Invoice(subscriptionId, periodStart, periodEnd, plan.getPrice()));
        subscription.advanceBillingDate(plan.getBillingInterval());
        return invoice.getId();
    }

    /** Tenta de novo as faturas que falharam e ja chegaram na hora do retry. */
    public int retryDueInvoices() {
        List<UUID> due = invoices.findIdsDueForRetry(InvoiceStatus.FAILED, clock.instant());
        int sent = 0;
        for (UUID invoiceId : due) {
            try {
                if (chargeService.charge(invoiceId)) {
                    sent++;
                }
            } catch (RuntimeException e) {
                log.error("Falha no retry da fatura {}", invoiceId, e);
            }
        }
        return sent;
    }

    /**
     * Cobranca enviada ao gateway que nunca recebeu webhook: da como falha para o retry
     * assumir. Se o webhook de sucesso chegar depois, PaymentResultService ainda registra o pagamento.
     */
    public int expireStalePendingAttempts() {
        Instant limit = clock.instant().minus(properties.retry().pendingTimeout());
        List<PaymentAttempt> stale = attempts.findByStatusAndCreatedAtBefore(AttemptStatus.PENDING, limit);
        for (PaymentAttempt attempt : stale) {
            try {
                results.applyFailure(attempt.getId(), "Sem resposta do gateway");
            } catch (RuntimeException e) {
                log.error("Falha ao expirar a tentativa {}", attempt.getId(), e);
            }
        }
        return stale.size();
    }
}
