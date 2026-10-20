package com.zodiakk.authservice.outbox.service;

import com.zodiakk.authservice.outbox.config.OutboxProperties;
import com.zodiakk.authservice.outbox.entity.OutboxEvent;
import com.zodiakk.authservice.outbox.entity.OutboxEventStatus;
import com.zodiakk.authservice.outbox.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OutboxClaimService {

    private final OutboxEventRepository repository;
    private final OutboxProperties properties;

    @Transactional
    public List<OutboxEvent> claimBatch() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        List<OutboxEvent> events = repository.findAvailableForUpdate(
                now,
                properties.batchSize()
        );

        for (OutboxEvent event : events) {
            event.setStatus(OutboxEventStatus.PROCESSING);
            event.setAttempts(event.getAttempts() + 1);
            event.setLockedUntil(
                    now.plusSeconds(properties.leaseSeconds())
            );
        }

        repository.flush();

        return List.copyOf(events);
    }

    @Transactional
    public boolean markPublished(UUID eventId, int attempt) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        return repository.markPublished(
                eventId,
                attempt,
                OutboxEventStatus.PROCESSING,
                OutboxEventStatus.PUBLISHED,
                now
        ) == 1;
    }

    @Transactional
    public boolean markFailed(UUID eventId, int attempt) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        long delaySeconds = Math.min(
                300L,
                1L << Math.min(attempt, 8)
        );

        return repository.markFailed(
                eventId,
                attempt,
                OutboxEventStatus.PROCESSING,
                OutboxEventStatus.PENDING,
                now,
                now.plusSeconds(delaySeconds)
        ) == 1;
    }
}
