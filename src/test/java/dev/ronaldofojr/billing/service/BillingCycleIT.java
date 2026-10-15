package dev.ronaldofojr.billing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.ronaldofojr.billing.IntegrationTestBase;
import dev.ronaldofojr.billing.domain.AttemptStatus;
import dev.ronaldofojr.billing.domain.Invoice;
import dev.ronaldofojr.billing.domain.InvoiceStatus;
import dev.ronaldofojr.billing.domain.Subscription;
import dev.ronaldofojr.billing.domain.SubscriptionStatus;
import dev.ronaldofojr.billing.gateway.ChargeRequest;
import dev.ronaldofojr.billing.gateway.GatewayUnavailableException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class BillingCycleIT extends IntegrationTestBase {

    private Subscription reload(Subscription s) {
        return subscriptions.findById(s.getId()).orElseThrow();
    }

    private List<Invoice> invoicesOf(Subscription s) {
        return invoices.findBySubscriptionIdOrderByPeriodStartDesc(s.getId());
    }

    @Test
    void subscribingChargesTheFirstInvoiceRightAway() {
        Subscription subscription = subscribe("29.90", "tok_ok");

        List<Invoice> list = invoicesOf(subscription);
        assertThat(list).hasSize(1);
        Invoice invoice = list.get(0);
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.OPEN);
        assertThat(invoice.getAmount()).isEqualByComparingTo("29.90");
        assertThat(invoice.getAttempts()).isEqualTo(1);
        assertThat(invoice.getPeriodStart()).isEqualTo(LocalDate.of(2026, 1, 15));
        assertThat(invoice.getPeriodEnd()).isEqualTo(LocalDate.of(2026, 2, 15));

        ArgumentCaptor<ChargeRequest> request = ArgumentCaptor.forClass(ChargeRequest.class);
        verify(gateway).charge(request.capture());
        assertThat(request.getValue().amount()).isEqualByComparingTo("29.90");
        assertThat(request.getValue().paymentToken()).isEqualTo("tok_ok");

        assertThat(lastAttemptOf(subscription.getId()).getStatus()).isEqualTo(AttemptStatus.PENDING);
        assertThat(lastAttemptOf(subscription.getId()).getGatewayChargeId()).startsWith("ch_");
        assertThat(reload(subscription).getNextBillingDate()).isEqualTo(LocalDate.of(2026, 2, 15));
    }

    @Test
    void runningTheCycleTwiceOnTheSameDayDoesNotBillTwice() {
        Subscription subscription = subscribe("10.00", "tok_ok");

        assertThat(billing.runBillingCycle()).isZero();
        assertThat(billing.runBillingCycle()).isZero();

        assertThat(invoicesOf(subscription)).hasSize(1);
        verify(gateway, times(1)).charge(any());
    }

    @Test
    void cycleBillsTheNextPeriodWhenTheDateArrives() {
        Subscription subscription = subscribe("10.00", "tok_ok");

        clock.advance(Duration.ofDays(31)); // 2026-02-15
        assertThat(billing.runBillingCycle()).isEqualTo(1);

        List<Invoice> list = invoicesOf(subscription);
        assertThat(list).hasSize(2);
        assertThat(list.get(0).getPeriodStart()).isEqualTo(LocalDate.of(2026, 2, 15));
        assertThat(reload(subscription).getNextBillingDate()).isEqualTo(LocalDate.of(2026, 3, 15));
    }

    @Test
    void cycleDoesNotBillBeforeTheDateNorCanceledOrPastDueSubscriptions() {
        Subscription canceled = subscribe("10.00", "tok_ok");
        subscriptionService.cancel(canceled.getId());
        Subscription pastDue = subscribe("10.00", "tok_ok");
        jdbc.update("UPDATE subscriptions SET status = 'PAST_DUE' WHERE id = ?", pastDue.getId());

        clock.advance(Duration.ofDays(40));

        assertThat(billing.runBillingCycle()).isZero();
        assertThat(invoicesOf(canceled)).hasSize(1);
        assertThat(invoicesOf(pastDue)).hasSize(1);
    }

    @Test
    void twoInstancesRunningTheCycleAtTheSameTimeGenerateOneInvoice() throws Exception {
        Subscription subscription = subscribe("10.00", "tok_ok");
        clock.advance(Duration.ofDays(31));

        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            Callable<Integer> job = billing::runBillingCycle;
            List<Future<Integer>> futures = List.of(pool.submit(job), pool.submit(job), pool.submit(job), pool.submit(job));
            for (Future<Integer> f : futures) {
                f.get();
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(invoicesOf(subscription)).hasSize(2); // a primeira + a do novo periodo, nunca duas do mesmo
        verify(gateway, times(2)).charge(any());
    }

    @Test
    void gatewayOutageSchedulesRetryAndMarksSubscriptionPastDue() {
        when(gateway.charge(any())).thenThrow(new GatewayUnavailableException("fora do ar"));

        Subscription subscription = subscribe("10.00", "tok_ok");

        Invoice invoice = invoicesOf(subscription).get(0);
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.FAILED);
        assertThat(invoice.getNextRetryAt()).isEqualTo(START.plus(Duration.ofHours(24)));
        assertThat(invoice.getFailureReason()).isEqualTo("fora do ar");
        assertThat(reload(subscription).getStatus()).isEqualTo(SubscriptionStatus.PAST_DUE);
        assertThat(lastAttemptOf(subscription.getId()).getStatus()).isEqualTo(AttemptStatus.FAILED);
    }

    @Test
    void retryOnlyHappensWhenItsTimeComes() {
        when(gateway.charge(any())).thenThrow(new GatewayUnavailableException("fora do ar"));
        Subscription subscription = subscribe("10.00", "tok_ok");

        clock.advance(Duration.ofHours(23));
        assertThat(billing.retryDueInvoices()).isZero();

        doReturn("ch_retry_1").when(gateway).charge(any());
        clock.advance(Duration.ofHours(1));
        assertThat(billing.retryDueInvoices()).isEqualTo(1);

        Invoice invoice = invoicesOf(subscription).get(0);
        assertThat(invoice.getAttempts()).isEqualTo(2);
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.OPEN);
        assertThat(lastAttemptOf(subscription.getId()).getGatewayChargeId()).isEqualTo("ch_retry_1");
    }

    @Test
    void afterAllRetriesFailTheInvoiceIsUncollectibleAndTheSubscriptionIsCanceled() {
        when(gateway.charge(any())).thenThrow(new GatewayUnavailableException("fora do ar"));
        Subscription subscription = subscribe("10.00", "tok_ok"); // tentativa 1

        clock.advance(Duration.ofHours(24));
        billing.retryDueInvoices(); // tentativa 2
        clock.advance(Duration.ofHours(72));
        billing.retryDueInvoices(); // tentativa 3
        assertThat(reload(subscription).getStatus()).isEqualTo(SubscriptionStatus.PAST_DUE);
        clock.advance(Duration.ofHours(120));
        billing.retryDueInvoices(); // tentativa 4: ultima

        Invoice invoice = invoicesOf(subscription).get(0);
        assertThat(invoice.getAttempts()).isEqualTo(4);
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.UNCOLLECTIBLE);
        assertThat(reload(subscription).getStatus()).isEqualTo(SubscriptionStatus.CANCELED);

        clock.advance(Duration.ofDays(30));
        assertThat(billing.retryDueInvoices()).isZero();
        verify(gateway, times(4)).charge(any());
    }

    @Test
    void doesNotChargeAgainWhileThereIsAPendingAttempt() {
        Subscription subscription = subscribe("10.00", "tok_ok");
        UUID invoiceId = invoicesOf(subscription).get(0).getId();

        // forca a fatura a parecer "vencida para retry" com uma cobranca ainda esperando o webhook
        jdbc.update("UPDATE invoices SET status = 'FAILED', next_retry_at = ? WHERE id = ?",
                java.sql.Timestamp.from(START.minusSeconds(60)), invoiceId);

        assertThat(billing.retryDueInvoices()).isZero();
        verify(gateway, times(1)).charge(any());
    }

    @Test
    void attemptWithoutWebhookExpiresAndIsRetried() {
        Subscription subscription = subscribe("10.00", "tok_ok");

        clock.advance(Duration.ofHours(23));
        assertThat(billing.expireStalePendingAttempts()).isZero();

        clock.advance(Duration.ofHours(2));
        assertThat(billing.expireStalePendingAttempts()).isEqualTo(1);

        Invoice invoice = invoicesOf(subscription).get(0);
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.FAILED);
        assertThat(invoice.getFailureReason()).isEqualTo("Sem resposta do gateway");
        assertThat(lastAttemptOf(subscription.getId()).getStatus()).isEqualTo(AttemptStatus.FAILED);
    }

    @Test
    void retryOfAnInvoiceWhoseSubscriptionWasCanceledDoesNotCharge() {
        when(gateway.charge(any())).thenThrow(new GatewayUnavailableException("fora do ar"));
        Subscription subscription = subscribe("10.00", "tok_ok");
        subscriptionService.cancel(subscription.getId());

        clock.advance(Duration.ofHours(24));
        assertThat(billing.retryDueInvoices()).isZero();

        assertThat(invoicesOf(subscription).get(0).getStatus()).isEqualTo(InvoiceStatus.UNCOLLECTIBLE);
        verify(gateway, times(1)).charge(any());
    }
}
