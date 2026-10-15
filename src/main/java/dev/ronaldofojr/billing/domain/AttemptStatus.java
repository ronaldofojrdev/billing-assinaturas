package dev.ronaldofojr.billing.domain;

public enum AttemptStatus {
    /** Cobranca enviada ao gateway, esperando o webhook com o resultado. */
    PENDING,
    SUCCEEDED,
    FAILED
}
