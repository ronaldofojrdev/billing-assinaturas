package dev.ronaldofojr.billing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Entity
@Table(name = "invoices")
public class Invoice {

    @Id
    private UUID id;

    @Column(name = "subscription_id", nullable = false, updatable = false)
    private UUID subscriptionId;

    @Column(name = "period_start", nullable = false, updatable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false, updatable = false)
    private LocalDate periodEnd;

    @Column(nullable = false, updatable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvoiceStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "failure_reason")
    private String failureReason;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    protected Invoice() {
    }

    public Invoice(UUID subscriptionId, LocalDate periodStart, LocalDate periodEnd, BigDecimal amount) {
        this.id = UUID.randomUUID();
        this.subscriptionId = subscriptionId;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.amount = amount;
        this.status = InvoiceStatus.OPEN;
        this.attempts = 0;
        this.createdAt = Instant.now();
    }

    public boolean canBeCharged() {
        return status == InvoiceStatus.OPEN || status == InvoiceStatus.FAILED;
    }

    /** Registra o inicio de uma tentativa de cobranca e devolve o numero dela (1, 2, 3...). */
    public int startAttempt() {
        if (!canBeCharged()) {
            throw new IllegalStateException("Fatura " + id + " nao pode ser cobrada no estado " + status);
        }
        attempts++;
        status = InvoiceStatus.OPEN;
        nextRetryAt = null;
        return attempts;
    }

    public void markPaid(Instant when) {
        if (status == InvoiceStatus.PAID) {
            return;
        }
        status = InvoiceStatus.PAID;
        paidAt = when;
        nextRetryAt = null;
        failureReason = null;
    }

    /**
     * Registra a falha da tentativa. Com retryAt agenda nova tentativa (FAILED);
     * sem retryAt nao ha mais o que tentar (UNCOLLECTIBLE).
     */
    public void registerFailure(String reason, Optional<Instant> retryAt) {
        failureReason = reason;
        if (retryAt.isPresent()) {
            status = InvoiceStatus.FAILED;
            nextRetryAt = retryAt.get();
        } else {
            status = InvoiceStatus.UNCOLLECTIBLE;
            nextRetryAt = null;
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getSubscriptionId() {
        return subscriptionId;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public InvoiceStatus getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getNextRetryAt() {
        return nextRetryAt;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPaidAt() {
        return paidAt;
    }
}
