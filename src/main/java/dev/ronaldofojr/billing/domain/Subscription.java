package dev.ronaldofojr.billing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "subscriptions")
public class Subscription {

    @Id
    private UUID id;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @Column(name = "plan_id", nullable = false, updatable = false)
    private UUID planId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionStatus status;

    // token do cartao no gateway (nunca guardamos o numero do cartao)
    @Column(name = "payment_token", nullable = false)
    private String paymentToken;

    @Column(name = "started_at", nullable = false, updatable = false)
    private LocalDate startedAt;

    @Column(name = "next_billing_date", nullable = false)
    private LocalDate nextBillingDate;

    @Column(name = "canceled_at")
    private Instant canceledAt;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Subscription() {
    }

    public Subscription(UUID customerId, UUID planId, String paymentToken, LocalDate startDate) {
        this.id = UUID.randomUUID();
        this.customerId = customerId;
        this.planId = planId;
        this.paymentToken = paymentToken;
        this.status = SubscriptionStatus.ACTIVE;
        this.startedAt = startDate;
        this.nextBillingDate = startDate;
        this.createdAt = Instant.now();
    }

    public boolean isDueOn(LocalDate date) {
        return status == SubscriptionStatus.ACTIVE && !nextBillingDate.isAfter(date);
    }

    /**
     * Avanca a proxima cobranca ancorando no dia em que a assinatura comecou. Sem a ancora o dia "escorrega":
     * 31/01 vira 28/02 e depois 28/03 (o 28 nunca volta para 31). Com ela, vira 28/02, 31/03, 30/04...
     */
    public void advanceBillingDate(BillingInterval interval) {
        LocalDate next = interval.addTo(nextBillingDate);
        int anchorDay = startedAt.getDayOfMonth();
        this.nextBillingDate = next.withDayOfMonth(Math.min(anchorDay, next.lengthOfMonth()));
    }

    public void markPastDue() {
        if (status == SubscriptionStatus.ACTIVE) {
            status = SubscriptionStatus.PAST_DUE;
        }
    }

    /** Pagamento regularizado: volta a ser cobrada normalmente. */
    public void reactivate() {
        if (status == SubscriptionStatus.PAST_DUE) {
            status = SubscriptionStatus.ACTIVE;
        }
    }

    public void cancel(Instant when) {
        if (status != SubscriptionStatus.CANCELED) {
            status = SubscriptionStatus.CANCELED;
            canceledAt = when;
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public UUID getPlanId() {
        return planId;
    }

    public SubscriptionStatus getStatus() {
        return status;
    }

    public String getPaymentToken() {
        return paymentToken;
    }

    public LocalDate getStartedAt() {
        return startedAt;
    }

    public LocalDate getNextBillingDate() {
        return nextBillingDate;
    }

    public Instant getCanceledAt() {
        return canceledAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
