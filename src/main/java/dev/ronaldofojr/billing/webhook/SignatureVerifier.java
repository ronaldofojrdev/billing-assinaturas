package dev.ronaldofojr.billing.webhook;

import dev.ronaldofojr.billing.config.BillingProperties;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Confere a assinatura do webhook: o gateway manda o header "X-Signature: sha256=<hex>" com o
 * HMAC-SHA256 do corpo cru usando um segredo combinado. Sem isso qualquer um poderia
 * chamar o endpoint e marcar faturas como pagas.
 */
@Component
public class SignatureVerifier {

    private static final String PREFIX = "sha256=";

    private final byte[] secret;

    public SignatureVerifier(BillingProperties properties) {
        this.secret = properties.webhook().secret().getBytes(StandardCharsets.UTF_8);
    }

    public String sign(String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return PREFIX + HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Nao foi possivel calcular o HMAC", e);
        }
    }

    public boolean isValid(String body, String signatureHeader) {
        if (signatureHeader == null || body == null) {
            return false;
        }
        // comparacao em tempo constante, para nao vazar a assinatura por diferenca de tempo
        return MessageDigest.isEqual(
                sign(body).getBytes(StandardCharsets.UTF_8),
                signatureHeader.trim().getBytes(StandardCharsets.UTF_8));
    }
}
