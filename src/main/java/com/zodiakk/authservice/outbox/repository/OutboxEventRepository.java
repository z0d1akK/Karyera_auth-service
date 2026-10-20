package com.zodiakk.authservice.outbox.repository;

import com.zodiakk.authservice.outbox.entity.OutboxEvent;
import com.zodiakk.authservice.outbox.entity.OutboxEventStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    @Query(value = """
            SELECT *
            FROM outbox_events
            WHERE (
                (status = 'PENDING' AND next_attempt_at <= :now)
                OR
                (status = 'PROCESSING' AND locked_until <= :now)
            )
            ORDER BY created_at, id
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> findAvailableForUpdate(@Param("now") OffsetDateTime now, @Param("batchSize") int batchSize);

    @Modifying
    @Query("""
        UPDATE OutboxEvent e
        SET e.status = :published,
            e.publishedAt = :publishedAt,
            e.lockedUntil = null
        WHERE e.id = :eventId
          AND e.status = :processing
          AND e.attempts = :attempt
          AND e.lockedUntil > :publishedAt
        """)
    int markPublished(
            @Param("eventId") UUID eventId,
            @Param("attempt") int attempt,
            @Param("processing") OutboxEventStatus processing,
            @Param("published") OutboxEventStatus published,
            @Param("publishedAt") OffsetDateTime publishedAt
    );

    @Modifying
    @Query("""
        UPDATE OutboxEvent e
        SET e.status = :pending,
            e.lockedUntil = null,
            e.nextAttemptAt = :nextAttemptAt
        WHERE e.id = :eventId
          AND e.status = :processing
          AND e.attempts = :attempt
          AND e.lockedUntil > :now
        """)
    int markFailed(
            @Param("eventId") UUID eventId,
            @Param("attempt") int attempt,
            @Param("processing") OutboxEventStatus processing,
            @Param("pending") OutboxEventStatus pending,
            @Param("now") OffsetDateTime now,
            @Param("nextAttemptAt") OffsetDateTime nextAttemptAt
    );

    boolean existsByAggregateIdAndEventTypeAndStatusIn(
            UUID aggregateId,
            String eventType,
            Collection<OutboxEventStatus> statuses
    );

    @Modifying
    @Query("""
        DELETE FROM OutboxEvent e
        WHERE e.status = :status
          AND e.publishedAt < :cutoff
        """)
    int deletePublishedBefore(@Param("status") OutboxEventStatus status, @Param("cutoff") OffsetDateTime cutoff);
}