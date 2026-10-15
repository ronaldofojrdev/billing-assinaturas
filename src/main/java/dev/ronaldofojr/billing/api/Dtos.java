package dev.ronaldofojr.billing.api;

import dev.ronaldofojr.billing.domain.AttemptStatus;
import dev.ronaldofojr.billing.domain.BillingInterval;
import dev.ronaldofojr.billing.domain.Customer;
import dev.ronaldofojr.billing.domain.Invoice;
import dev.ronaldofojr.billing.domain.InvoiceStatus;
import dev.ronaldofojr.billing.domain.PaymentAttempt;
import dev.ronaldofojr.billing.domain.Plan;
import dev.ronaldofojr.billing.domain.Subscription;
import dev.ronaldofojr.billing.domain.SubscriptionStatus;
import dev.ronaldofojr.billing.service.SubscriptionService.InvoiceDetail;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Requests e responses da API num lugar so. */
public final class Dtos {

    private Dtos() {
    }

    public record CreatePlanRequest(
            @NotBlank @Size(max = 100) String name,
            @NotNull @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal price,
            @NotNull BillingInterval interval) {
    }

    public record PlanResponse(UUID id, String name, BigDecimal price, BillingInterval interval, boolean active) {
        public static PlanResponse from(Plan p) {
            return new PlanResponse(p.getId(), p.getName(), p.getPrice(), p.getBillingInterval(), p.isActive());
        }
    }

    public record CreateCustomerRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Email @Size(max = 160) String email) {
    }

    public record CustomerResponse(UUID id, String name, String email) {
        public static CustomerResponse from(Customer c) {
            return new CustomerResponse(c.getId(), c.getName(), c.getEmail());
        }
    }

    public record CreateSubscriptionRequest(
            @NotNull UUID customerId,
            @NotNull UUID planId,
            @NotBlank @Size(max = 100) String paymentToken) {
    }

    public record SubscriptionResponse(UUID id, UUID customerId, UUID planId, SubscriptionStatus status,
                                       LocalDate nextBillingDate, Instant canceledAt) {
        public static SubscriptionResponse from(Subscription s) {
            return new SubscriptionResponse(s.getId(), s.getCustomerId(), s.getPlanId(), s.getStatus(),
                    s.getNextBillingDate(), s.getCanceledAt());
        }
    }

    public record AttemptResponse(int number, AttemptStatus status, String chargeId, String failureReason) {
        static AttemptResponse from(PaymentAttempt a) {
            return new AttemptResponse(a.getAttemptNumber(), a.getStatus(), a.getGatewayChargeId(), a.getFailureReason());
        }
    }

    public record InvoiceResponse(UUID id, LocalDate periodStart, LocalDate periodEnd, BigDecimal amount,
                                  InvoiceStatus status, Instant nextRetryAt, String failureReason, Instant paidAt,
                                  List<AttemptResponse> attempts) {
        public static InvoiceResponse from(InvoiceDetail detail) {
            Invoice i = detail.invoice();
            return new InvoiceResponse(i.getId(), i.getPeriodStart(), i.getPeriodEnd(), i.getAmount(), i.getStatus(),
                    i.getNextRetryAt(), i.getFailureReason(), i.getPaidAt(),
                    detail.attempts().stream().map(AttemptResponse::from).toList());
        }
    }

    public record CustomerInvoiceResponse(UUID id, UUID subscriptionId, LocalDate periodStart, LocalDate periodEnd,
                                          BigDecimal amount, InvoiceStatus status, int attempts, Instant createdAt,
                                          Instant paidAt) {
        public static CustomerInvoiceResponse from(Invoice i) {
            return new CustomerInvoiceResponse(i.getId(), i.getSubscriptionId(), i.getPeriodStart(), i.getPeriodEnd(),
                    i.getAmount(), i.getStatus(), i.getAttempts(), i.getCreatedAt(), i.getPaidAt());
        }
    }

    public record InvoicePage(List<CustomerInvoiceResponse> content, int page, int size, long totalElements,
                              int totalPages) {
    }

    public record BillingRunResponse(int invoicesGenerated, int retriesSent, int attemptsExpired) {
    }
}
