package dev.ronaldofojr.billing.domain;

public enum InvoiceStatus {
    /** Gerada, aguardando cobranca ou resultado do gateway. */
    OPEN,
    PAID,
    /** Ultima tentativa falhou e ha um retry agendado (nextRetryAt). */
    FAILED,
    /** Acabaram as tentativas. A assinatura e cancelada. */
    UNCOLLECTIBLE
}
