package dev.ronaldofojr.billing.repository;

import dev.ronaldofojr.billing.domain.WebhookEvent;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface WebhookEventRepository extends JpaRepository<WebhookEvent, String> {

    /**
     * Registra o evento so se ainda nao existir e diz se registrou (1) ou se ja existia (0).
     *
     * Nao da para usar save(): o id do evento vem do gateway (nao e gerado por nos), entao o Spring Data
     * faz um merge, que primeiro consulta o banco. Se outro envio do mesmo evento ja tinha commitado,
     * o merge encontra a linha e segue em frente sem erro, e o evento seria processado duas vezes.
     * O INSERT ... ON CONFLICT e atomico: um unico envio ganha, os outros recebem 0.
     */
    @Transactional
    @Modifying
    @Query(value = """
            INSERT INTO webhook_events (event_id, type, received_at)
            VALUES (:eventId, :type, :receivedAt)
            ON CONFLICT (event_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("eventId") String eventId, @Param("type") String type,
                       @Param("receivedAt") Instant receivedAt);
}
