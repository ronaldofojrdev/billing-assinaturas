package dev.ronaldofojr.billing.service;

import dev.ronaldofojr.billing.domain.AttemptStatus;
import dev.ronaldofojr.billing.domain.Invoice;
import dev.ronaldofojr.billing.domain.PaymentAttempt;
import dev.ronaldofojr.billing.domain.Plan;
import dev.ronaldofojr.billing.domain.Subscription;
import dev.ronaldofojr.billing.repository.CustomerRepository;
import dev.ronaldofojr.billing.repository.InvoiceRepository;
import dev.ronaldofojr.billing.repository.PaymentAttemptRepository;
import dev.ronaldofojr.billing.repository.PlanRepository;
import dev.ronaldofojr.billing.repository.SubscriptionRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class SubscriptionService {

    public record InvoiceDetail(Invoice invoice, List<PaymentAttempt> attempts) {
    }

    private final SubscriptionRepository subscriptions;
    private final CustomerRepository customers;
    private final PlanRepository plans;
    private final InvoiceRepository invoices;
    private final PaymentAttemptRepository attempts;
    private final BillingService billing;
    private final TransactionTemplate tx;
    private final Clock clock;

    public SubscriptionService(SubscriptionRepository subscriptions, CustomerRepository customers,
                               PlanRepository plans, InvoiceRepository invoices,
                               PaymentAttemptRepository attempts, BillingService billing,
                               TransactionTemplate tx, Clock clock) {
        this.subscriptions = subscriptions;
        this.customers = customers;
        this.plans = plans;
        this.invoices = invoices;
        this.attempts = attempts;
        this.billing = billing;
        this.tx = tx;
        this.clock = clock;
    }

    /** Cria a assinatura e ja cobra a primeira fatura. Se o gateway falhar, o retry assume. */
    public Subscription subscribe(UUID customerId, UUID planId, String paymentToken) {
        Subscription created = tx.execute(status -> {
            if (!customers.existsById(customerId)) {
                throw new NotFoundException("Cliente", customerId);
            }
            Plan plan = plans.findById(planId).orElseThrow(() -> new NotFoundException("Plano", planId));
            if (!plan.isActive()) {
                throw new BusinessRuleException("O plano '" + plan.getName() + "' nao esta mais disponivel");
            }
            return subscriptions.save(new Subscription(customerId, planId, paymentToken, LocalDate.now(clock)));
        });
        billing.billSubscription(created.getId());
        return get(created.getId());
    }

    @Transactional(readOnly = true)
    public Subscription get(UUID id) {
        return subscriptions.findById(id).orElseThrow(() -> new NotFoundException("Assinatura", id));
    }

    /**
     * Cancela a assinatura. Cancelar duas vezes nao e erro: a segunda chamada nao muda nada.
     *
     * Faturas ja pagas nao mudam. Faturas em aberto ou que falharam e esperam retry sao encerradas agora
     * (UNCOLLECTIBLE), para ninguem cobrar o cliente depois do cancelamento. Se ja existe uma cobranca
     * enviada ao gateway esperando resposta, a fatura fica como esta: se o pagamento for aprovado o webhook
     * ainda o registra (o dinheiro entrou), e se for recusado ela encerra sozinha.
     */
    @Transactional
    public Subscription cancel(UUID id) {
        Subscription subscription = subscriptions.findById(id).orElseThrow(() -> new NotFoundException("Assinatura", id));
        subscription.cancel(clock.instant());

        for (Invoice invoice : invoices.findBySubscriptionIdOrderByPeriodStartDesc(id)) {
            boolean waitingForGateway = attempts.existsByInvoiceIdAndStatus(invoice.getId(), AttemptStatus.PENDING);
            if (invoice.canBeCharged() && !waitingForGateway) {
                invoice.registerFailure("Assinatura cancelada", Optional.empty());
            }
        }
        return subscription;
    }

    @Transactional(readOnly = true)
    public List<InvoiceDetail> invoicesOf(UUID subscriptionId) {
        if (!subscriptions.existsById(subscriptionId)) {
            throw new NotFoundException("Assinatura", subscriptionId);
        }
        return invoices.findBySubscriptionIdOrderByPeriodStartDesc(subscriptionId).stream()
                .map(i -> new InvoiceDetail(i, attempts.findByInvoiceIdOrderByAttemptNumber(i.getId())))
                .toList();
    }
}
