package dev.ronaldofojr.billing.domain;

public enum SubscriptionStatus {
    /** Em dia: entra no ciclo de cobranca. */
    ACTIVE,
    /** Uma fatura falhou e ainda ha retries. Nao gera fatura nova ate regularizar. */
    PAST_DUE,
    /** Cancelada pelo cliente ou por falta de pagamento depois de todos os retries. */
    CANCELED
}
