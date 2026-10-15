package dev.ronaldofojr.billing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_attempts")
public class PaymentAttempt {

    @Id
    private UUID id;

    @Column(name = "invoice_id", nullable = false, updatable = false)
    private UUID invoiceId;

    @Column(name = "attempt_number", nullable = false, updatable = false)
    private int attemptNumber;

    @Column(name = "gateway_charge_id")
    private String gatewayChargeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AttemptStatus status;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "settled_at")
    private Instant settledAt;

    protected PaymentAttempt() {
    }

    public PaymentAttempt(UUID invoiceId, int attemptNumber, Instant now) {
        this.id = UUID.randomUUID();
        this.invoiceId = invoiceId;
        this.attemptNumber = attemptNumber;
        this.status = AttemptStatus.PENDING;
        this.createdAt = now;
    }

    public void assignCharge(String chargeId) {
        this.gatewayChargeId = chargeId;
    }

    public boolean isPending() {
        return status == AttemptStatus.PENDING;
    }

    public void markSucceeded(Instant when) {
        this.status = AttemptStatus.SUCCEEDED;
        this.settledAt = when;
    }

    public void markFailed(String reason, Instant when) {
        this.status = AttemptStatus.FAILED;
        this.failureReason = reason;
        this.settledAt = when;
    }

    public UUID getId() {
        return id;
    }

    public UUID getInvoiceId() {
        return invoiceId;
    }

    public int getAttemptNumber() {
        return attemptNumber;
    }

    public String getGatewayChargeId() {
        return gatewayChargeId;
    }

    public AttemptStatus getStatus() {
        return status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getSettledAt() {
        return settledAt;
    }
}
