package dev.ronaldofojr.billing.api;

import dev.ronaldofojr.billing.api.Dtos.CreateSubscriptionRequest;
import dev.ronaldofojr.billing.api.Dtos.InvoiceResponse;
import dev.ronaldofojr.billing.api.Dtos.SubscriptionResponse;
import dev.ronaldofojr.billing.service.SubscriptionService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/subscriptions")
public class SubscriptionController {

    private final SubscriptionService service;

    public SubscriptionController(SubscriptionService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<SubscriptionResponse> subscribe(@Valid @RequestBody CreateSubscriptionRequest request) {
        SubscriptionResponse body = SubscriptionResponse.from(
                service.subscribe(request.customerId(), request.planId(), request.paymentToken()));
        return ResponseEntity.created(URI.create("/subscriptions/" + body.id())).body(body);
    }

    @GetMapping("/{id}")
    public SubscriptionResponse get(@PathVariable UUID id) {
        return SubscriptionResponse.from(service.get(id));
    }

    @PostMapping("/{id}/cancel")
    public SubscriptionResponse cancel(@PathVariable UUID id) {
        return SubscriptionResponse.from(service.cancel(id));
    }

    @GetMapping("/{id}/invoices")
    public List<InvoiceResponse> invoices(@PathVariable UUID id) {
        return service.invoicesOf(id).stream().map(InvoiceResponse::from).toList();
    }
}
