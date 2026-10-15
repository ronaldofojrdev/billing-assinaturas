package dev.ronaldofojr.billing.service;

import dev.ronaldofojr.billing.domain.BillingInterval;
import dev.ronaldofojr.billing.domain.Customer;
import dev.ronaldofojr.billing.domain.Invoice;
import dev.ronaldofojr.billing.domain.Plan;
import dev.ronaldofojr.billing.repository.CustomerRepository;
import dev.ronaldofojr.billing.repository.InvoiceRepository;
import dev.ronaldofojr.billing.repository.PlanRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Planos e clientes. */
@Service
public class CatalogService {

    private final PlanRepository plans;
    private final CustomerRepository customers;
    private final InvoiceRepository invoices;

    public CatalogService(PlanRepository plans, CustomerRepository customers, InvoiceRepository invoices) {
        this.plans = plans;
        this.customers = customers;
        this.invoices = invoices;
    }

    /** Todas as faturas do cliente (de todas as assinaturas dele), da mais recente para a mais antiga. */
    @Transactional(readOnly = true)
    public Page<Invoice> invoicesOfCustomer(UUID customerId, int page, int size) {
        getCustomer(customerId);
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        return invoices.findByCustomerId(customerId,
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    @Transactional
    public Plan createPlan(String name, BigDecimal price, BillingInterval interval) {
        try {
            return plans.saveAndFlush(new Plan(name, price, interval));
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("Ja existe um plano chamado '" + name + "'");
        }
    }

    @Transactional(readOnly = true)
    public List<Plan> listActivePlans() {
        return plans.findByActiveTrueOrderByPriceAsc();
    }

    @Transactional
    public Customer createCustomer(String name, String email) {
        if (customers.existsByEmail(email.toLowerCase())) {
            throw new ConflictException("Ja existe um cliente com o e-mail " + email);
        }
        try {
            return customers.saveAndFlush(new Customer(name, email));
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("Ja existe um cliente com o e-mail " + email);
        }
    }

    @Transactional(readOnly = true)
    public Customer getCustomer(UUID id) {
        return customers.findById(id).orElseThrow(() -> new NotFoundException("Cliente", id));
    }
}
