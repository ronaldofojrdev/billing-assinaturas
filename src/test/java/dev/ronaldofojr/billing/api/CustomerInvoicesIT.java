package dev.ronaldofojr.billing.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.ronaldofojr.billing.IntegrationTestBase;
import dev.ronaldofojr.billing.domain.BillingInterval;
import dev.ronaldofojr.billing.domain.Customer;
import dev.ronaldofojr.billing.domain.Plan;
import dev.ronaldofojr.billing.domain.Subscription;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Historico de cobrancas de um cliente, somando todas as assinaturas dele. */
class CustomerInvoicesIT extends IntegrationTestBase {

    private Customer newCustomer() {
        return catalog.createCustomer("Cliente", UUID.randomUUID() + "@teste.com");
    }

    private Plan newPlan(String price) {
        return catalog.createPlan("Plano " + UUID.randomUUID(), new BigDecimal(price), BillingInterval.MONTHLY);
    }

    @Test
    void listsTheInvoicesOfAllTheSubscriptionsOfTheCustomerNewestFirst() throws Exception {
        Customer customer = newCustomer();
        Subscription basic = subscriptionService.subscribe(customer.getId(), newPlan("10.00").getId(), "tok_ok");
        clock.advance(Duration.ofDays(1));
        Subscription pro = subscriptionService.subscribe(customer.getId(), newPlan("50.00").getId(), "tok_ok");

        mvc.perform(get("/customers/" + customer.getId() + "/invoices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].subscriptionId").value(pro.getId().toString()))
                .andExpect(jsonPath("$.content[0].amount").value(50.0))
                .andExpect(jsonPath("$.content[1].subscriptionId").value(basic.getId().toString()))
                .andExpect(jsonPath("$.content[1].status").value("OPEN"));
    }

    @Test
    void includesTheMonthsThatWereBilledLater() throws Exception {
        Customer customer = newCustomer();
        subscriptionService.subscribe(customer.getId(), newPlan("10.00").getId(), "tok_ok");
        clock.advance(Duration.ofDays(31));
        billing.runBillingCycle();
        clock.advance(Duration.ofDays(28));
        billing.runBillingCycle();

        mvc.perform(get("/customers/" + customer.getId() + "/invoices"))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].periodStart").value("2026-03-15"))
                .andExpect(jsonPath("$.content[2].periodStart").value("2026-01-15"));
    }

    @Test
    void doesNotMixInvoicesOfOtherCustomers() throws Exception {
        Customer one = newCustomer();
        Customer other = newCustomer();
        subscriptionService.subscribe(one.getId(), newPlan("10.00").getId(), "tok_ok");
        subscriptionService.subscribe(other.getId(), newPlan("20.00").getId(), "tok_ok");
        subscriptionService.subscribe(other.getId(), newPlan("30.00").getId(), "tok_ok");

        mvc.perform(get("/customers/" + one.getId() + "/invoices")).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/customers/" + other.getId() + "/invoices")).andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void customerWithoutInvoicesGetsAnEmptyPage() throws Exception {
        Customer customer = newCustomer();

        mvc.perform(get("/customers/" + customer.getId() + "/invoices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    @Test
    void pageSizeIsRespectedAndOutOfRangeValuesAreAdjusted() throws Exception {
        Customer customer = newCustomer();
        for (int i = 0; i < 3; i++) {
            subscriptionService.subscribe(customer.getId(), newPlan("10.00").getId(), "tok_ok");
        }

        mvc.perform(get("/customers/" + customer.getId() + "/invoices?size=2&page=1"))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalPages").value(2));
        mvc.perform(get("/customers/" + customer.getId() + "/invoices?size=100000"))
                .andExpect(jsonPath("$.size").value(100));
        mvc.perform(get("/customers/" + customer.getId() + "/invoices?size=0&page=-1"))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.page").value(0));
        mvc.perform(get("/customers/" + customer.getId() + "/invoices?page=50"))
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void unknownCustomerReturns404() throws Exception {
        mvc.perform(get("/customers/" + UUID.randomUUID() + "/invoices")).andExpect(status().isNotFound());
    }
}
