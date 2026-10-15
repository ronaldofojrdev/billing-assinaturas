package dev.ronaldofojr.billing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.ronaldofojr.billing.IntegrationTestBase;
import dev.ronaldofojr.billing.domain.Invoice;
import dev.ronaldofojr.billing.domain.InvoiceStatus;
import dev.ronaldofojr.billing.domain.Subscription;
import dev.ronaldofojr.billing.domain.SubscriptionStatus;
import dev.ronaldofojr.billing.gateway.GatewayUnavailableException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Cancelar antes e depois da cobranca. */
class CancellationIT extends IntegrationTestBase {

    private Invoice firstInvoiceOf(Subscription s) {
        return invoices.findBySubscriptionIdOrderByPeriodStartDesc(s.getId()).get(0);
    }

    private Subscription reload(Subscription s) {
        return subscriptions.findById(s.getId()).orElseThrow();
    }

    @Test
    void cancelingBeforeAnyChargeSucceededClosesTheInvoiceAndNoRetryCharges() {
        when(gateway.charge(any())).thenThrow(new GatewayUnavailableException("fora do ar"));
        Subscription subscription = subscribe("10.00", "tok_ok"); // fatura FAILED, retry agendado
        assertThat(firstInvoiceOf(subscription).getStatus()).isEqualTo(InvoiceStatus.FAILED);

        subscriptionService.cancel(subscription.getId());

        Invoice invoice = firstInvoiceOf(subscription);
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.UNCOLLECTIBLE);
        assertThat(invoice.getFailureReason()).isEqualTo("Assinatura cancelada");
        assertThat(invoice.getNextRetryAt()).isNull();
        assertThat(reload(subscription).getStatus()).isEqualTo(SubscriptionStatus.CANCELED);

        clock.advance(Duration.ofDays(2));
        assertThat(billing.retryDueInvoices()).isZero();
        verify(gateway, times(1)).charge(any());
    }

    @Test
    void cancelingAfterThePaymentKeepsTheInvoicePaidAndStopsFutureBilling() throws Exception {
        Subscription subscription = subscribe("10.00", "tok_ok");
        sendSignedWebhook("evt_paga", "payment.succeeded", lastAttemptOf(subscription.getId()).getGatewayChargeId(), null);

        subscriptionService.cancel(subscription.getId());

        assertThat(firstInvoiceOf(subscription).getStatus()).isEqualTo(InvoiceStatus.PAID);
        assertThat(reload(subscription).getStatus()).isEqualTo(SubscriptionStatus.CANCELED);

        clock.advance(Duration.ofDays(40));
        assertThat(billing.runBillingCycle()).isZero();
        assertThat(invoices.findBySubscriptionIdOrderByPeriodStartDesc(subscription.getId())).hasSize(1);
    }

    @Test
    void cancelingWhileTheGatewayStillHasAPendingChargeDoesNotThrowTheMoneyAway() throws Exception {
        Subscription subscription = subscribe("10.00", "tok_ok"); // cobranca PENDING esperando o webhook

        subscriptionService.cancel(subscription.getId());

        assertThat(firstInvoiceOf(subscription).getStatus()).as("segue aguardando o gateway").isEqualTo(InvoiceStatus.OPEN);

        // o pagamento foi aprovado mesmo assim: a fatura fica paga e a assinatura continua cancelada
        sendSignedWebhook("evt_tardio", "payment.succeeded", lastAttemptOf(subscription.getId()).getGatewayChargeId(), null);
        assertThat(firstInvoiceOf(subscription).getStatus()).isEqualTo(InvoiceStatus.PAID);
        assertThat(reload(subscription).getStatus()).isEqualTo(SubscriptionStatus.CANCELED);
    }

    @Test
    void aDeclinedChargeAfterCancelingClosesTheInvoiceWithoutScheduling() throws Exception {
        Subscription subscription = subscribe("10.00", "tok_ok");
        subscriptionService.cancel(subscription.getId());

        sendSignedWebhook("evt_recusada", "payment.failed", lastAttemptOf(subscription.getId()).getGatewayChargeId(), "recusado");

        // a politica de retry agendaria nova tentativa, mas a assinatura esta cancelada: o retry nao cobra
        clock.advance(Duration.ofDays(2));
        assertThat(billing.retryDueInvoices()).isZero();
        assertThat(firstInvoiceOf(subscription).getStatus()).isIn(InvoiceStatus.FAILED, InvoiceStatus.UNCOLLECTIBLE);
        verify(gateway, times(1)).charge(any());
    }

    @Test
    void cancelingTwiceKeepsTheFirstCancellationDate() {
        Subscription subscription = subscribe("10.00", "tok_ok");

        Subscription first = subscriptionService.cancel(subscription.getId());
        clock.advance(Duration.ofDays(3));
        Subscription second = subscriptionService.cancel(subscription.getId());

        assertThat(second.getCanceledAt()).isEqualTo(first.getCanceledAt());
        assertThat(second.getStatus()).isEqualTo(SubscriptionStatus.CANCELED);
    }

    @Test
    void invoicesOfOtherSubscriptionsAreNotAffected() {
        when(gateway.charge(any())).thenThrow(new GatewayUnavailableException("fora do ar"));
        Subscription canceled = subscribe("10.00", "tok_ok");
        Subscription other = subscribe("10.00", "tok_ok");

        subscriptionService.cancel(canceled.getId());

        List<Invoice> otherInvoices = invoices.findBySubscriptionIdOrderByPeriodStartDesc(other.getId());
        assertThat(otherInvoices.get(0).getStatus()).isEqualTo(InvoiceStatus.FAILED);
        assertThat(reload(other).getStatus()).isEqualTo(SubscriptionStatus.PAST_DUE);
    }
}
