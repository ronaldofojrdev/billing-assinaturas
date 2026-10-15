package dev.ronaldofojr.billing.repository;

import dev.ronaldofojr.billing.domain.Invoice;
import dev.ronaldofojr.billing.domain.InvoiceStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    List<Invoice> findBySubscriptionIdOrderByPeriodStartDesc(UUID subscriptionId);

    @Query("""
            select i from Invoice i
            where i.subscriptionId in (select s.id from Subscription s where s.customerId = :customerId)
            """)
    Page<Invoice> findByCustomerId(@Param("customerId") UUID customerId, Pageable pageable);

    @Query("select i.id from Invoice i where i.status = :status and i.nextRetryAt <= :now")
    List<UUID> findIdsDueForRetry(@Param("status") InvoiceStatus status, @Param("now") Instant now);
}
