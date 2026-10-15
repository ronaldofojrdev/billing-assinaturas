package dev.ronaldofojr.billing.webhook;

public enum WebhookResult {
    PROCESSED,
    /** Evento ja recebido antes: nada a fazer. */
    DUPLICATE,
    /** Tipo de evento que nao tratamos. */
    IGNORED,
    /** Cobranca que ainda nao conhecemos. Respondemos 404 para o gateway tentar de novo mais tarde. */
    UNKNOWN_CHARGE
}
