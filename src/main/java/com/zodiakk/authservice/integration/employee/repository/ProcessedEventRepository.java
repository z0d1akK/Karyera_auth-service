package com.zodiakk.authservice.integration.employee.repository;

import com.zodiakk.authservice.integration.employee.entity.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.UUID;

@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {

    @Modifying
    @Query(value = """
            INSERT INTO processed_events (event_id, processed_at)
            VALUES (:eventId, CURRENT_TIMESTAMP)
            ON CONFLICT (event_id) DO NOTHING
            """, nativeQuery = true)
    int registerIfAbsent(@Param("eventId") UUID eventId);

    @Modifying
    @Query(value = "DELETE FROM processed_events WHERE processed_at < :cutoff", nativeQuery = true)
    int deleteProcessedBefore(@Param("cutoff") OffsetDateTime cutoff);
}