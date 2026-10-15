package dev.ronaldofojr.billing.repository;

import dev.ronaldofojr.billing.domain.AttemptStatus;
import dev.ronaldofojr.billing.domain.PaymentAttempt;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, UUID> {

    Optional<PaymentAttempt> findByGatewayChargeId(String gatewayChargeId);

    boolean existsByInvoiceIdAndStatus(UUID invoiceId, AttemptStatus status);

    List<PaymentAttempt> findByStatusAndCreatedAtBefore(AttemptStatus status, Instant limit);

    List<PaymentAttempt> findByInvoiceIdOrderByAttemptNumber(UUID invoiceId);
}
