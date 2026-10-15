package dev.ronaldofojr.billing.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "billing")
public record BillingProperties(Retry retry, Webhook webhook) {

    /** delaysHours: espera depois de cada falha. Ex: [24, 72, 120] = 3 retries, 4 tentativas no total. */
    public record Retry(List<Integer> delaysHours, Duration pendingTimeout) {
    }

    public record Webhook(String secret) {
    }
}
