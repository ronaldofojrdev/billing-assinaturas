package dev.ronaldofojr.billing.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Datas de cobranca em meses de tamanhos diferentes e em ano bissexto. */
class BillingIntervalTest {

    @Test
    void monthlyFromThe31stClampsToTheLastDayOfShortMonths() {
        assertThat(BillingInterval.MONTHLY.addTo(LocalDate.of(2026, 1, 31))).isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(BillingInterval.MONTHLY.addTo(LocalDate.of(2026, 3, 31))).isEqualTo(LocalDate.of(2026, 4, 30));
        assertThat(BillingInterval.MONTHLY.addTo(LocalDate.of(2026, 5, 31))).isEqualTo(LocalDate.of(2026, 6, 30));
    }

    @Test
    void monthlyFromJanuary31InALeapYearGoesToFebruary29() {
        assertThat(BillingInterval.MONTHLY.addTo(LocalDate.of(2028, 1, 31))).isEqualTo(LocalDate.of(2028, 2, 29));
        assertThat(BillingInterval.MONTHLY.addTo(LocalDate.of(2028, 1, 30))).isEqualTo(LocalDate.of(2028, 2, 29));
        assertThat(BillingInterval.MONTHLY.addTo(LocalDate.of(2028, 1, 29))).isEqualTo(LocalDate.of(2028, 2, 29));
    }

    @Test
    void monthlyFromJanuary29To31InACommonYearAllGoToFebruary28() {
        assertThat(BillingInterval.MONTHLY.addTo(LocalDate.of(2026, 1, 29))).isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(BillingInterval.MONTHLY.addTo(LocalDate.of(2026, 1, 30))).isEqualTo(LocalDate.of(2026, 2, 28));
    }

    @Test
    void monthlyAcrossTheNewYearKeepsTheDay() {
        assertThat(BillingInterval.MONTHLY.addTo(LocalDate.of(2026, 12, 31))).isEqualTo(LocalDate.of(2027, 1, 31));
        assertThat(BillingInterval.MONTHLY.addTo(LocalDate.of(2026, 12, 15))).isEqualTo(LocalDate.of(2027, 1, 15));
    }

    @Test
    void yearlyFromFebruary29GoesToFebruary28InACommonYear() {
        assertThat(BillingInterval.YEARLY.addTo(LocalDate.of(2028, 2, 29))).isEqualTo(LocalDate.of(2029, 2, 28));
    }

    @Test
    void yearlyBetweenTwoLeapYearsKeepsFebruary29() {
        assertThat(BillingInterval.YEARLY.addTo(LocalDate.of(2024, 2, 29))).isEqualTo(LocalDate.of(2025, 2, 28));
        assertThat(BillingInterval.YEARLY.addTo(LocalDate.of(2027, 2, 28))).isEqualTo(LocalDate.of(2028, 2, 28));
    }

    @Test
    void yearlyFromAnOrdinaryDateJustAddsOneYear() {
        assertThat(BillingInterval.YEARLY.addTo(LocalDate.of(2026, 10, 14))).isEqualTo(LocalDate.of(2027, 10, 14));
    }
}
