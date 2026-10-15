package dev.ronaldofojr.billing.gateway;

/**
 * Porta para o gateway de pagamento (Stripe, Pagar.me, etc).
 * A cobranca e assincrona: charge() so aceita o pedido e devolve o id da cobranca.
 * O resultado (pago ou recusado) chega depois por webhook.
 */
public interface PaymentGateway {

    /** @throws GatewayUnavailableException se o gateway nao respondeu (timeout, 5xx, rede) */
    String charge(ChargeRequest request);
}
