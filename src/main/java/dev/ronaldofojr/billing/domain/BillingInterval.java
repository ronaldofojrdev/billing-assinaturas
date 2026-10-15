package dev.ronaldofojr.billing.domain;

import java.time.LocalDate;

public enum BillingInterval {
    MONTHLY,
    YEARLY;

    public LocalDate addTo(LocalDate date) {
        return this == MONTHLY ? date.plusMonths(1) : date.plusYears(1);
    }
}
