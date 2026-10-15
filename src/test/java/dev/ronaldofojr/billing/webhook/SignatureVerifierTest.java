package dev.ronaldofojr.billing.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ronaldofojr.billing.config.BillingProperties;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class SignatureVerifierTest {

    private SignatureVerifier verifierWithSecret(String secret) {
        return new SignatureVerifier(new BillingProperties(
                new BillingProperties.Retry(List.of(24), Duration.ofHours(24)),
                new BillingProperties.Webhook(secret)));
    }

    private final SignatureVerifier verifier = verifierWithSecret("segredo-de-teste");
    private final String body = "{\"eventId\":\"evt_1\",\"type\":\"payment.succeeded\",\"chargeId\":\"ch_1\"}";

    @Test
    void acceptsTheSignatureItGenerated() {
        assertThat(verifier.isValid(body, verifier.sign(body))).isTrue();
    }

    @Test
    void signatureHasTheExpectedFormat() {
        assertThat(verifier.sign(body)).matches("sha256=[0-9a-f]{64}");
    }

    @Test
    void rejectsTamperedBody() {
        String signature = verifier.sign(body);

        assertThat(verifier.isValid(body.replace("ch_1", "ch_2"), signature)).isFalse();
    }

    @Test
    void rejectsSignatureMadeWithAnotherSecret() {
        String forged = verifierWithSecret("outro-segredo").sign(body);

        assertThat(verifier.isValid(body, forged)).isFalse();
    }

    @Test
    void rejectsMissingOrMalformedSignature() {
        assertThat(verifier.isValid(body, null)).isFalse();
        assertThat(verifier.isValid(body, "")).isFalse();
        assertThat(verifier.isValid(body, "sha256=abc")).isFalse();
    }
}
