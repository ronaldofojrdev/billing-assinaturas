package dev.ronaldofojr.billing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InvoiceTest {

    private final Instant now = Instant.parse("2026-03-10T12:00:00Z");

    private Invoice newInvoice() {
        return new Invoice(UUID.randomUUID(), LocalDate.of(2026, 3, 1), LocalDate.of(2026, 4, 1), new BigDecimal("49.90"));
    }

    @Test
    void startsOpenWithoutAttempts() {
        Invoice invoice = newInvoice();

        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.OPEN);
        assertThat(invoice.getAttempts()).isZero();
        assertThat(invoice.canBeCharged()).isTrue();
    }

    @Test
    void eachAttemptIncrementsTheCounter() {
        Invoice invoice = newInvoice();

        assertThat(invoice.startAttempt()).isEqualTo(1);
        invoice.registerFailure("recusado", Optional.of(now.plusSeconds(3600)));
        assertThat(invoice.startAttempt()).isEqualTo(2);
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.OPEN);
        assertThat(invoice.getNextRetryAt()).isNull();
    }

    @Test
    void failureWithRetrySchedulesIt() {
        Invoice invoice = newInvoice();
        invoice.startAttempt();

        invoice.registerFailure("cartao recusado", Optional.of(now.plusSeconds(86400)));

        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.FAILED);
        assertThat(invoice.getNextRetryAt()).isEqualTo(now.plusSeconds(86400));
        assertThat(invoice.getFailureReason()).isEqualTo("cartao recusado");
    }

    @Test
    void failureWithoutRetryBecomesUncollectible() {
        Invoice invoice = newInvoice();
        invoice.startAttempt();

        invoice.registerFailure("cartao recusado", Optional.empty());

        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.UNCOLLECTIBLE);
        assertThat(invoice.getNextRetryAt()).isNull();
        assertThat(invoice.canBeCharged()).isFalse();
    }

    @Test
    void paidInvoiceCannotBeChargedAgain() {
        Invoice invoice = newInvoice();
        invoice.startAttempt();

        invoice.markPaid(now);

        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
        assertThat(invoice.getPaidAt()).isEqualTo(now);
        assertThatThrownBy(invoice::startAttempt).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void markPaidTwiceKeepsTheFirstPaymentDate() {
        Invoice invoice = newInvoice();

        invoice.markPaid(now);
        invoice.markPaid(now.plusSeconds(500));

        assertThat(invoice.getPaidAt()).isEqualTo(now);
    }
}
