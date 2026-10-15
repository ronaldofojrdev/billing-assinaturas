package dev.ronaldofojr.billing;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import dev.ronaldofojr.billing.domain.BillingInterval;
import dev.ronaldofojr.billing.domain.Customer;
import dev.ronaldofojr.billing.domain.PaymentAttempt;
import dev.ronaldofojr.billing.domain.Plan;
import dev.ronaldofojr.billing.domain.Subscription;
import dev.ronaldofojr.billing.gateway.PaymentGateway;
import dev.ronaldofojr.billing.repository.InvoiceRepository;
import dev.ronaldofojr.billing.repository.PaymentAttemptRepository;
import dev.ronaldofojr.billing.repository.SubscriptionRepository;
import dev.ronaldofojr.billing.service.BillingService;
import dev.ronaldofojr.billing.service.CatalogService;
import dev.ronaldofojr.billing.service.SubscriptionService;
import dev.ronaldofojr.billing.webhook.SignatureVerifier;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Postgres real (Testcontainers), gateway de pagamento mockado e relogio controlado pelo teste.
 * Os jobs agendados ficam desligados: os testes chamam o BillingService direto.
 */
@SpringBootTest(properties = "billing.jobs.enabled=false")
@AutoConfigureMockMvc
@Import(IntegrationTestBase.TestClockConfig.class)
public abstract class IntegrationTestBase {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    /** Relogio que o teste move para simular a passagem dos dias. */
    public static class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        public void set(Instant instant) {
            this.now = instant;
        }

        public void advance(Duration duration) {
            this.now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @TestConfiguration
    static class TestClockConfig {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(START);
        }
    }

    protected static final Instant START = Instant.parse("2026-01-15T10:00:00Z");

    @MockitoBean
    protected PaymentGateway gateway;

    @Autowired
    protected MutableClock clock;
    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected CatalogService catalog;
    @Autowired
    protected SubscriptionService subscriptionService;
    @Autowired
    protected BillingService billing;
    @Autowired
    protected SignatureVerifier signatureVerifier;
    @Autowired
    protected SubscriptionRepository subscriptions;
    @Autowired
    protected InvoiceRepository invoices;
    @Autowired
    protected PaymentAttemptRepository attempts;

    @BeforeEach
    void resetState() {
        jdbc.execute("TRUNCATE webhook_events, payment_attempts, invoices, subscriptions, customers, plans CASCADE");
        clock.set(START);
        reset(gateway);
        // padrao: o gateway aceita a cobranca e devolve um id novo
        when(gateway.charge(any())).thenAnswer(inv -> "ch_" + UUID.randomUUID().toString().substring(0, 8));
    }

    protected Subscription subscribe(String price, String token) {
        Plan plan = catalog.createPlan("Plano " + UUID.randomUUID(), new BigDecimal(price), BillingInterval.MONTHLY);
        Customer customer = catalog.createCustomer("Cliente", UUID.randomUUID() + "@teste.com");
        return subscriptionService.subscribe(customer.getId(), plan.getId(), token);
    }

    protected List<PaymentAttempt> attemptsOf(UUID subscriptionId) {
        return invoices.findBySubscriptionIdOrderByPeriodStartDesc(subscriptionId).stream()
                .flatMap(i -> attempts.findByInvoiceIdOrderByAttemptNumber(i.getId()).stream())
                .toList();
    }

    protected PaymentAttempt lastAttemptOf(UUID subscriptionId) {
        List<PaymentAttempt> all = attemptsOf(subscriptionId);
        return all.get(all.size() - 1);
    }

    protected String webhookBody(String eventId, String type, String chargeId, String reason) {
        return "{\"eventId\":\"%s\",\"type\":\"%s\",\"chargeId\":\"%s\",\"reason\":%s}".formatted(
                eventId, type, chargeId, reason == null ? "null" : "\"" + reason + "\"");
    }

    protected ResultActions sendWebhook(String body, String signature) throws Exception {
        var request = post("/webhooks/payments").contentType(MediaType.APPLICATION_JSON).content(body);
        if (signature != null) {
            request = request.header("X-Signature", signature);
        }
        return mvc.perform(request);
    }

    protected ResultActions sendSignedWebhook(String eventId, String type, String chargeId, String reason) throws Exception {
        String body = webhookBody(eventId, type, chargeId, reason);
        return sendWebhook(body, signatureVerifier.sign(body));
    }
}
