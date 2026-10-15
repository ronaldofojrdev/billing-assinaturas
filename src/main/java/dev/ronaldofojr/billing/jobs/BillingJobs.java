package dev.ronaldofojr.billing.jobs;

import dev.ronaldofojr.billing.service.BillingService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** So agenda os metodos; a logica fica no BillingService (que e o que os testes chamam). */
@Component
public class BillingJobs {

    private final BillingService billing;

    public BillingJobs(BillingService billing) {
        this.billing = billing;
    }

    @Scheduled(cron = "${billing.jobs.cycle-cron}")
    public void billingCycle() {
        billing.runBillingCycle();
    }

    @Scheduled(fixedDelayString = "${billing.jobs.retry-interval-ms}")
    public void retryAndExpire() {
        billing.expireStalePendingAttempts();
        billing.retryDueInvoices();
    }
}
