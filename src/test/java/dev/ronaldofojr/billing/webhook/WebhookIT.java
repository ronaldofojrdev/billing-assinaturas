package dev.ronaldofojr.billing.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.ronaldofojr.billing.IntegrationTestBase;
import dev.ronaldofojr.billing.domain.AttemptStatus;
import dev.ronaldofojr.billing.domain.Invoice;
import dev.ronaldofojr.billing.domain.InvoiceStatus;
import dev.ronaldofojr.billing.domain.Subscription;
import dev.ronaldofojr.billing.domain.SubscriptionStatus;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class WebhookIT extends IntegrationTestBase {

    private Invoice firstInvoiceOf(Subscription s) {
        return invoices.findBySubscriptionIdOrderByPeriodStartDesc(s.getId()).get(0);
    }

    private Integer count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    @Test
    void rejectsMissingOrInvalidSignature() throws Exception {
        Subscription subscription = subscribe("10.00", "tok_ok");
        String chargeId = lastAttemptOf(subscription.getId()).getGatewayChargeId();
        String body = webhookBody("evt_1", "payment.succeeded", chargeId, null);

        sendWebhook(body, null).andExpect(status().isUnauthorized());
        sendWebhook(body, "sha256=" + "0".repeat(64)).andExpect(status().isUnauthorized());

        assertThat(firstInvoiceOf(subscription).getStatus()).isEqualTo(InvoiceStatus.OPEN);
        assertThat(count("webhook_events")).isZero();
    }

    @Test
    void successWebhookPaysTheInvoice() throws Exception {
        Subscription subscription = subscribe("10.00", "tok_ok");
        String chargeId = lastAttemptOf(subscription.getId()).getGatewayChargeId();

        sendSignedWebhook("evt_1", "payment.succeeded", chargeId, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("PROCESSED"));

        Invoice invoice = firstInvoiceOf(subscription);
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
        assertThat(invoice.getPaidAt()).isEqualTo(START);
        assertThat(lastAttemptOf(subscription.getId()).getStatus()).isEqualTo(AttemptStatus.SUCCEEDED);
    }

    @Test
    void failureWebhookSchedulesRetryAndMarksPastDue() throws Exception {
        Subscription subscription = subscribe("10.00", "tok_ok");
        String chargeId = lastAttemptOf(subscription.getId()).getGatewayChargeId();

        sendSignedWebhook("evt_1", "payment.failed", chargeId, "Cartao sem limite").andExpect(status().isOk());

        Invoice invoice = firstInvoiceOf(subscription);
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.FAILED);
        assertThat(invoice.getFailureReason()).isEqualTo("Cartao sem limite");
        assertThat(invoice.getNextRetryAt()).isEqualTo(START.plus(Duration.ofHours(24)));
        assertThat(subscriptions.findById(subscription.getId()).orElseThrow().getStatus())
                .isEqualTo(SubscriptionStatus.PAST_DUE);
    }

    @Test
    void paymentAfterAFailureReactivatesTheSubscription() throws Exception {
        Subscription subscription = subscribe("10.00", "tok_ok");
        sendSignedWebhook("evt_1", "payment.failed", lastAttemptOf(subscription.getId()).getGatewayChargeId(), "recusado")
                .andExpect(status().isOk());

        clock.advance(Duration.ofHours(24));
        assertThat(billing.retryDueInvoices()).isEqualTo(1);
        sendSignedWebhook("evt_2", "payment.succeeded", lastAttemptOf(subscription.getId()).getGatewayChargeId(), null)
                .andExpect(status().isOk());

        assertThat(firstInvoiceOf(subscription).getStatus()).isEqualTo(InvoiceStatus.PAID);
        assertThat(subscriptions.findById(subscription.getId()).orElseThrow().getStatus())
                .isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    void sameEventSentTwiceIsProcessedOnce() throws Exception {
        Subscription subscription = subscribe("10.00", "tok_ok");
        String chargeId = lastAttemptOf(subscription.getId()).getGatewayChargeId();

        sendSignedWebhook("evt_dup", "payment.failed", chargeId, "recusado")
                .andExpect(jsonPath("$.result").value("PROCESSED"));
        sendSignedWebhook("evt_dup", "payment.failed", chargeId, "recusado")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("DUPLICATE"));

        assertThat(firstInvoiceOf(subscription).getAttempts()).isEqualTo(1);
        assertThat(count("webhook_events")).isEqualTo(1);
    }

    @Test
    void sameEventSentManyTimesAtOnceIsProcessedOnce() throws Exception {
        Subscription subscription = subscribe("10.00", "tok_ok");
        String chargeId = lastAttemptOf(subscription.getId()).getGatewayChargeId();

        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<String> results = new ArrayList<>();
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                futures.add(pool.submit(() -> sendSignedWebhook("evt_race", "payment.succeeded", chargeId, null)
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString()));
            }
            for (Future<String> f : futures) {
                results.add(f.get());
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(results).filteredOn(r -> r.contains("PROCESSED")).hasSize(1);
        assertThat(results).filteredOn(r -> r.contains("DUPLICATE")).hasSize(7);
        assertThat(firstInvoiceOf(subscription).getStatus()).isEqualTo(InvoiceStatus.PAID);
        assertThat(count("webhook_events")).isEqualTo(1);
    }

    @Test
    void unknownChargeGets404AndTheEventIsNotStoredSoTheGatewayRetryWorks() throws Exception {
        Subscription subscription = subscribe("10.00", "tok_ok");

        sendSignedWebhook("evt_early", "payment.succeeded", "ch_ainda_nao_existe", null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.result").value("UNKNOWN_CHARGE"));
        assertThat(count("webhook_events")).isZero();

        // quando a cobranca passa a existir, o reenvio do mesmo evento funciona
        jdbc.update("UPDATE payment_attempts SET gateway_charge_id = 'ch_ainda_nao_existe' WHERE invoice_id = ?",
                firstInvoiceOf(subscription).getId());
        sendSignedWebhook("evt_early", "payment.succeeded", "ch_ainda_nao_existe", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("PROCESSED"));
        assertThat(firstInvoiceOf(subscription).getStatus()).isEqualTo(InvoiceStatus.PAID);
    }

    @Test
    void unsupportedEventTypeIsAcknowledgedAndIgnored() throws Exception {
        sendSignedWebhook("evt_other", "customer.updated", "ch_x", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("IGNORED"));
    }

    @Test
    void malformedPayloadsReturn400() throws Exception {
        String notJson = "isso nao e json";
        sendWebhook(notJson, signatureVerifier.sign(notJson)).andExpect(status().isBadRequest());

        String missingFields = "{\"eventId\":\"evt_1\"}";
        sendWebhook(missingFields, signatureVerifier.sign(missingFields)).andExpect(status().isBadRequest());
    }

    @Test
    void lateSuccessAfterTimeoutStillRecordsThePayment() throws Exception {
        Subscription subscription = subscribe("10.00", "tok_ok");
        String chargeId = lastAttemptOf(subscription.getId()).getGatewayChargeId();
        clock.advance(Duration.ofHours(25));
        billing.expireStalePendingAttempts(); // o webhook nao veio: tentativa dada como falha

        sendSignedWebhook("evt_late", "payment.succeeded", chargeId, null).andExpect(status().isOk());

        // o dinheiro entrou, entao a fatura fica paga mesmo com a tentativa marcada como falha antes
        assertThat(firstInvoiceOf(subscription).getStatus()).isEqualTo(InvoiceStatus.PAID);
        assertThat(subscriptions.findById(subscription.getId()).orElseThrow().getStatus())
                .isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    void secondSuccessForAnAlreadyPaidInvoiceChangesNothing() throws Exception {
        Subscription subscription = subscribe("10.00", "tok_ok");
        sendSignedWebhook("evt_1", "payment.succeeded", lastAttemptOf(subscription.getId()).getGatewayChargeId(), null)
                .andExpect(status().isOk());
        Invoice paid = firstInvoiceOf(subscription);

        // outra tentativa (ex: retry que passou mesmo assim) tambem aprovada
        jdbc.update("INSERT INTO payment_attempts (id, invoice_id, attempt_number, gateway_charge_id, status, created_at) "
                + "VALUES (gen_random_uuid(), ?, 2, 'ch_dup', 'PENDING', now())", paid.getId());
        sendSignedWebhook("evt_2", "payment.succeeded", "ch_dup", null).andExpect(status().isOk());

        Invoice after = firstInvoiceOf(subscription);
        assertThat(after.getStatus()).isEqualTo(InvoiceStatus.PAID);
        assertThat(after.getPaidAt()).isEqualTo(paid.getPaidAt());
    }
}
