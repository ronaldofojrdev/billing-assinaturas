package dev.ronaldofojr.billing.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SubscriptionTest {

    private final LocalDate start = LocalDate.of(2026, 1, 15);

    private Subscription newSubscription() {
        return new Subscription(UUID.randomUUID(), UUID.randomUUID(), "tok_ok", start);
    }

    @Test
    void isDueOnTheBillingDateAndAfter() {
        Subscription subscription = newSubscription();

        assertThat(subscription.isDueOn(start.minusDays(1))).isFalse();
        assertThat(subscription.isDueOn(start)).isTrue();
        assertThat(subscription.isDueOn(start.plusDays(10))).isTrue();
    }

    @Test
    void advancingMovesTheDateByTheInterval() {
        Subscription monthly = newSubscription();
        monthly.advanceBillingDate(BillingInterval.MONTHLY);
        assertThat(monthly.getNextBillingDate()).isEqualTo(LocalDate.of(2026, 2, 15));

        Subscription yearly = newSubscription();
        yearly.advanceBillingDate(BillingInterval.YEARLY);
        assertThat(yearly.getNextBillingDate()).isEqualTo(LocalDate.of(2027, 1, 15));
    }

    @Test
    void pastDueSubscriptionIsNotBilledAndCanBeReactivated() {
        Subscription subscription = newSubscription();

        subscription.markPastDue();
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.PAST_DUE);
        assertThat(subscription.isDueOn(start.plusDays(5))).isFalse();

        subscription.reactivate();
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    void canceledSubscriptionStaysCanceledAndKeepsTheFirstCancelDate() {
        Subscription subscription = newSubscription();
        Instant first = Instant.parse("2026-02-01T10:00:00Z");

        subscription.cancel(first);
        subscription.cancel(first.plusSeconds(999));
        subscription.reactivate();
        subscription.markPastDue();

        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.CANCELED);
        assertThat(subscription.getCanceledAt()).isEqualTo(first);
        assertThat(subscription.isDueOn(start.plusYears(1))).isFalse();
    }

    @Test
    void monthlyBillingStartedOnThe31stReturnsToThe31stInLongMonths() {
        Subscription subscription = new Subscription(UUID.randomUUID(), UUID.randomUUID(), "tok_ok", LocalDate.of(2026, 1, 31));

        subscription.advanceBillingDate(BillingInterval.MONTHLY);
        assertThat(subscription.getNextBillingDate()).isEqualTo(LocalDate.of(2026, 2, 28));
        subscription.advanceBillingDate(BillingInterval.MONTHLY);
        assertThat(subscription.getNextBillingDate()).as("nao pode ficar no dia 28 para sempre").isEqualTo(LocalDate.of(2026, 3, 31));
        subscription.advanceBillingDate(BillingInterval.MONTHLY);
        assertThat(subscription.getNextBillingDate()).isEqualTo(LocalDate.of(2026, 4, 30));
        subscription.advanceBillingDate(BillingInterval.MONTHLY);
        assertThat(subscription.getNextBillingDate()).isEqualTo(LocalDate.of(2026, 5, 31));
    }

    @Test
    void monthlyBillingStartedOnThe30thOrThe29thAlsoKeepsItsDay() {
        Subscription on30 = new Subscription(UUID.randomUUID(), UUID.randomUUID(), "tok_ok", LocalDate.of(2026, 1, 30));
        on30.advanceBillingDate(BillingInterval.MONTHLY);
        on30.advanceBillingDate(BillingInterval.MONTHLY);
        assertThat(on30.getNextBillingDate()).isEqualTo(LocalDate.of(2026, 3, 30));

        Subscription on29 = new Subscription(UUID.randomUUID(), UUID.randomUUID(), "tok_ok", LocalDate.of(2028, 1, 29));
        on29.advanceBillingDate(BillingInterval.MONTHLY);
        assertThat(on29.getNextBillingDate()).isEqualTo(LocalDate.of(2028, 2, 29));
        on29.advanceBillingDate(BillingInterval.MONTHLY);
        assertThat(on29.getNextBillingDate()).isEqualTo(LocalDate.of(2028, 3, 29));
    }

    @Test
    void yearlyBillingStartedOnFebruary29ReturnsToTheLeapDayInTheNextLeapYear() {
        Subscription subscription = new Subscription(UUID.randomUUID(), UUID.randomUUID(), "tok_ok", LocalDate.of(2028, 2, 29));

        subscription.advanceBillingDate(BillingInterval.YEARLY);
        assertThat(subscription.getNextBillingDate()).isEqualTo(LocalDate.of(2029, 2, 28));
        subscription.advanceBillingDate(BillingInterval.YEARLY);
        subscription.advanceBillingDate(BillingInterval.YEARLY);
        subscription.advanceBillingDate(BillingInterval.YEARLY);
        assertThat(subscription.getNextBillingDate()).isEqualTo(LocalDate.of(2032, 2, 29));
    }
}
