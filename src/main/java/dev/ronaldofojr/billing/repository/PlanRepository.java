package dev.ronaldofojr.billing.repository;

import dev.ronaldofojr.billing.domain.Plan;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanRepository extends JpaRepository<Plan, UUID> {

    List<Plan> findByActiveTrueOrderByPriceAsc();
}
