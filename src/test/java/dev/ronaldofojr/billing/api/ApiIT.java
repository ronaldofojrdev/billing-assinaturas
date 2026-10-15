package dev.ronaldofojr.billing.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.ronaldofojr.billing.IntegrationTestBase;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class ApiIT extends IntegrationTestBase {

    private String createAndGetId(String url, String json) throws Exception {
        String response = mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    @Test
    void fullFlowPlanCustomerSubscriptionInvoices() throws Exception {
        String planId = createAndGetId("/plans", "{\"name\":\"Pro\",\"price\":49.9,\"interval\":\"MONTHLY\"}");
        String customerId = createAndGetId("/customers", "{\"name\":\"Ana\",\"email\":\"ana@teste.com\"}");
        String subscriptionId = createAndGetId("/subscriptions",
                "{\"customerId\":\"%s\",\"planId\":\"%s\",\"paymentToken\":\"tok_ok\"}".formatted(customerId, planId));

        mvc.perform(get("/subscriptions/" + subscriptionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.nextBillingDate").value("2026-02-15"));

        mvc.perform(get("/subscriptions/" + subscriptionId + "/invoices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("OPEN"))
                .andExpect(jsonPath("$[0].amount").value(49.9))
                .andExpect(jsonPath("$[0].attempts[0].status").value("PENDING"));

        mvc.perform(get("/plans")).andExpect(jsonPath("$[0].name").value("Pro"));
    }

    @Test
    void cancelIsIdempotent() throws Exception {
        String planId = createAndGetId("/plans", "{\"name\":\"Basico\",\"price\":9.9,\"interval\":\"YEARLY\"}");
        String customerId = createAndGetId("/customers", "{\"name\":\"Bia\",\"email\":\"bia@teste.com\"}");
        String subscriptionId = createAndGetId("/subscriptions",
                "{\"customerId\":\"%s\",\"planId\":\"%s\",\"paymentToken\":\"tok_ok\"}".formatted(customerId, planId));

        mvc.perform(post("/subscriptions/" + subscriptionId + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELED"));
        mvc.perform(post("/subscriptions/" + subscriptionId + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELED"));
    }

    @Test
    void validationErrorsReturn400() throws Exception {
        mvc.perform(post("/plans").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"price\":-1,\"interval\":\"MONTHLY\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/customers").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\",\"email\":\"nao-e-email\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/subscriptions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentToken\":\"tok\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void duplicatedPlanNameAndEmailReturn409() throws Exception {
        String plan = "{\"name\":\"Unico\",\"price\":10,\"interval\":\"MONTHLY\"}";
        createAndGetId("/plans", plan);
        mvc.perform(post("/plans").contentType(MediaType.APPLICATION_JSON).content(plan))
                .andExpect(status().isConflict());

        String customer = "{\"name\":\"Caio\",\"email\":\"caio@teste.com\"}";
        createAndGetId("/customers", customer);
        mvc.perform(post("/customers").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Caio 2\",\"email\":\"CAIO@teste.com\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void unknownCustomerOrPlanReturn404() throws Exception {
        String planId = createAndGetId("/plans", "{\"name\":\"Existe\",\"price\":10,\"interval\":\"MONTHLY\"}");

        mvc.perform(post("/subscriptions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":\"%s\",\"planId\":\"%s\",\"paymentToken\":\"tok\"}"
                                .formatted(UUID.randomUUID(), planId)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/subscriptions/" + UUID.randomUUID())).andExpect(status().isNotFound());
        mvc.perform(get("/customers/" + UUID.randomUUID())).andExpect(status().isNotFound());
    }

    @Test
    void adminRunEndpointExecutesTheCycle() throws Exception {
        createAndGetId("/plans", "{\"name\":\"Ciclo\",\"price\":10,\"interval\":\"MONTHLY\"}");

        mvc.perform(post("/admin/billing/run"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoicesGenerated").value(0));
    }
}
