package dev.ronaldofojr.billing.api;

import dev.ronaldofojr.billing.api.Dtos.CreateCustomerRequest;
import dev.ronaldofojr.billing.api.Dtos.CreatePlanRequest;
import dev.ronaldofojr.billing.api.Dtos.CustomerInvoiceResponse;
import dev.ronaldofojr.billing.api.Dtos.CustomerResponse;
import dev.ronaldofojr.billing.api.Dtos.InvoicePage;
import dev.ronaldofojr.billing.api.Dtos.PlanResponse;
import dev.ronaldofojr.billing.domain.Invoice;
import dev.ronaldofojr.billing.service.CatalogService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CatalogController {

    private final CatalogService catalog;

    public CatalogController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @PostMapping("/plans")
    public ResponseEntity<PlanResponse> createPlan(@Valid @RequestBody CreatePlanRequest request) {
        PlanResponse body = PlanResponse.from(catalog.createPlan(request.name(), request.price(), request.interval()));
        return ResponseEntity.created(URI.create("/plans/" + body.id())).body(body);
    }

    @GetMapping("/plans")
    public List<PlanResponse> listPlans() {
        return catalog.listActivePlans().stream().map(PlanResponse::from).toList();
    }

    /** Historico de cobrancas do cliente, paginado (size de 1 a 100), da mais recente para a mais antiga. */
    @GetMapping("/customers/{id}/invoices")
    public InvoicePage customerInvoices(@PathVariable UUID id,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size) {
        Page<Invoice> result = catalog.invoicesOfCustomer(id, page, size);
        return new InvoicePage(result.map(CustomerInvoiceResponse::from).getContent(), result.getNumber(),
                result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @PostMapping("/customers")
    public ResponseEntity<CustomerResponse> createCustomer(@Valid @RequestBody CreateCustomerRequest request) {
        CustomerResponse body = CustomerResponse.from(catalog.createCustomer(request.name(), request.email()));
        return ResponseEntity.created(URI.create("/customers/" + body.id())).body(body);
    }

    @GetMapping("/customers/{id}")
    public CustomerResponse getCustomer(@PathVariable UUID id) {
        return CustomerResponse.from(catalog.getCustomer(id));
    }
}
