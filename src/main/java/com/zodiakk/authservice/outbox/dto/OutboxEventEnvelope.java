package com.zodiakk.authservice.outbox.dto;

import tools.jackson.databind.JsonNode;

import java.time.OffsetDateTime;
import java.util.UUID;

public record OutboxEventEnvelope(
        UUID eventId,
        String eventType,
        int eventVersion,
        OffsetDateTime occurredAt,
        JsonNode payload
) {
}