package com.zodiakk.authservice.outbox.service;

import com.zodiakk.authservice.outbox.dto.OutboxEventEnvelope;
import com.zodiakk.authservice.outbox.entity.OutboxEvent;
import com.zodiakk.authservice.outbox.entity.OutboxEventStatus;
import com.zodiakk.authservice.outbox.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OutboxEventService {

    private final OutboxEventRepository outboxEventRepository;

    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public void saveEvent(
            UUID aggregateId,
            String aggregateType,
            String eventType,
            int eventVersion,
            Object payload
    ) {
        UUID eventId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        try {
            OutboxEventEnvelope envelope = new OutboxEventEnvelope(
                    eventId,
                    eventType,
                    eventVersion,
                    now,
                    objectMapper.valueToTree(payload)
            );

            String json = objectMapper.writeValueAsString(envelope);

            OutboxEvent event = OutboxEvent.builder()
                    .id(eventId)
                    .aggregateId(aggregateId)
                    .aggregateType(aggregateType)
                    .eventType(eventType)
                    .eventVersion(eventVersion)
                    .payload(json)
                    .status(OutboxEventStatus.PENDING)
                    .attempts(0)
                    .createdAt(now)
                    .nextAttemptAt(now)
                    .build();

            outboxEventRepository.save(event);

        } catch (JacksonException ex) {
            throw new IllegalStateException(
                    "Failed to serialize outbox event: " + eventType,
                    ex
            );
        }
    }
}