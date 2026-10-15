package dev.ronaldofojr.billing.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class RetryPolicyTest {

    private final Instant now = Instant.parse("2026-03-10T12:00:00Z");
    private final RetryPolicy policy = new RetryPolicy(List.of(24, 72, 120));

    @Test
    void allowsFourAttemptsInTotal() {
        assertThat(policy.maxAttempts()).isEqualTo(4);
    }

    @Test
    void schedulesEachRetryWithItsOwnDelay() {
        assertThat(policy.nextRetryAfterFailure(1, now)).contains(now.plus(Duration.ofHours(24)));
        assertThat(policy.nextRetryAfterFailure(2, now)).contains(now.plus(Duration.ofHours(72)));
        assertThat(policy.nextRetryAfterFailure(3, now)).contains(now.plus(Duration.ofHours(120)));
    }

    @Test
    void givesUpAfterTheLastAttempt() {
        assertThat(policy.nextRetryAfterFailure(4, now)).isEmpty();
        assertThat(policy.nextRetryAfterFailure(10, now)).isEmpty();
    }

    @Test
    void ignoresInvalidAttemptCounts() {
        assertThat(policy.nextRetryAfterFailure(0, now)).isEmpty();
    }

    @Test
    void emptyDelaysMeansNoRetryAtAll() {
        RetryPolicy noRetry = new RetryPolicy(List.of());

        assertThat(noRetry.maxAttempts()).isEqualTo(1);
        assertThat(noRetry.nextRetryAfterFailure(1, now)).isEmpty();
    }
}
