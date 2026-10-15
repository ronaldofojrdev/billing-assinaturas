package dev.ronaldofojr.billing.webhook;

/**
 * Corpo do webhook. type: "payment.succeeded" ou "payment.failed".
 * eventId e unico por evento (o gateway reenvia o mesmo id se nao receber 2xx).
 */
public record PaymentWebhook(String eventId, String type, String chargeId, String reason) {

    public static final String SUCCEEDED = "payment.succeeded";
    public static final String FAILED = "payment.failed";

    public boolean isComplete() {
        return notBlank(eventId) && notBlank(type) && notBlank(chargeId);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
