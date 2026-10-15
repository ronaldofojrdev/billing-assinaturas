package dev.ronaldofojr.billing.api;

import dev.ronaldofojr.billing.api.Dtos.BillingRunResponse;
import dev.ronaldofojr.billing.service.BillingService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dispara na mao o que os jobs fazem sozinhos. Util para demonstrar e depurar.
 * ATENCAO: sem autenticacao, em producao isso ficaria protegido ou removido.
 */
@RestController
@RequestMapping("/admin/billing")
public class AdminController {

    private final BillingService billing;

    public AdminController(BillingService billing) {
        this.billing = billing;
    }

    @PostMapping("/run")
    public BillingRunResponse run() {
        int expired = billing.expireStalePendingAttempts();
        int generated = billing.runBillingCycle();
        int retries = billing.retryDueInvoices();
        return new BillingRunResponse(generated, retries, expired);
    }
}
