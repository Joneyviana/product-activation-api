package br.com.example.activation.outbox.repository;

import br.com.example.activation.outbox.domain.OutboxEvent;
import br.com.example.activation.outbox.domain.OutboxStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
    @Query("""
            select e from OutboxEvent e
            where (e.status = :pending and e.nextAttemptAt <= :now)
               or (e.status = :processing and e.lockedUntil <= :now)
            order by e.createdAt asc
            """)
    List<OutboxEvent> findEligible(@Param("now") Instant now,
                                   @Param("pending") OutboxStatus pending,
                                   @Param("processing") OutboxStatus processing,
                                   Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from OutboxEvent e where e.id = :id")
    Optional<OutboxEvent> findByIdForUpdate(@Param("id") UUID id);
}
