package dev.ronaldofojr.billing.gateway;

import java.math.BigDecimal;
import java.util.UUID;

/** attemptId vai como chave de idempotencia no gateway: reenviar o mesmo pedido nao cobra duas vezes. */
public record ChargeRequest(UUID attemptId, UUID invoiceId, BigDecimal amount, String paymentToken) {
}
