package dev.ronaldofojr.billing.gateway;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Gateway de mentira para desenvolvimento e testes. Aceita qualquer cobranca e devolve um id;
 * o resultado voce simula mandando o webhook na mao (veja o README).
 * O token "tok_gateway_down" simula o gateway fora do ar.
 */
@Component
public class FakePaymentGateway implements PaymentGateway {

    public static final String DOWN_TOKEN = "tok_gateway_down";

    private static final Logger log = LoggerFactory.getLogger(FakePaymentGateway.class);

    @Override
    public String charge(ChargeRequest request) {
        if (DOWN_TOKEN.equals(request.paymentToken())) {
            throw new GatewayUnavailableException("Gateway fora do ar (simulado)");
        }
        String chargeId = "ch_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        log.info("[fake-gateway] cobranca {} de {} aceita (fatura {})", chargeId, request.amount(), request.invoiceId());
        return chargeId;
    }
}
