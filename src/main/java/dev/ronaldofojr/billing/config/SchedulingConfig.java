package dev.ronaldofojr.billing.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Os testes desligam os jobs (billing.jobs.enabled=false) e chamam o BillingService direto. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "billing.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
