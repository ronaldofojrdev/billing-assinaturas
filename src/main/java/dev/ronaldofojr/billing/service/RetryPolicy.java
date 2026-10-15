package dev.ronaldofojr.billing.service;

import dev.ronaldofojr.billing.config.BillingProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Define quando tentar cobrar de novo. Com delays [24, 72, 120] (horas):
 * falhou a 1a tentativa -> tenta de novo em 24h; falhou a 2a -> 72h; falhou a 3a -> 120h;
 * falhou a 4a -> desiste.
 */
@Component
public class RetryPolicy {

    private final List<Integer> delaysHours;

    @Autowired
    public RetryPolicy(BillingProperties properties) {
        this(properties.retry().delaysHours());
    }

    public RetryPolicy(List<Integer> delaysHours) {
        this.delaysHours = List.copyOf(delaysHours);
    }

    public int maxAttempts() {
        return delaysHours.size() + 1;
    }

    /** @param attemptsMade quantas tentativas ja foram feitas (contando a que acabou de falhar) */
    public Optional<Instant> nextRetryAfterFailure(int attemptsMade, Instant now) {
        if (attemptsMade < 1 || attemptsMade > delaysHours.size()) {
            return Optional.empty();
        }
        return Optional.of(now.plus(Duration.ofHours(delaysHours.get(attemptsMade - 1))));
    }
}
