package dev.ronaldofojr.billing.repository;

import dev.ronaldofojr.billing.domain.Subscription;
import dev.ronaldofojr.billing.domain.SubscriptionStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    @Query("select s.id from Subscription s where s.status = :status and s.nextBillingDate <= :date")
    List<UUID> findIdsDue(@Param("status") SubscriptionStatus status, @Param("date") LocalDate date);
}
