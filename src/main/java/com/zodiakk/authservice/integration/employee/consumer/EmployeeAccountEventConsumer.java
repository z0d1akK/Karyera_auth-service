package com.zodiakk.authservice.integration.employee.consumer;

import com.zodiakk.authservice.integration.employee.dto.EmployeeAccountUnlinkedEvent;
import com.zodiakk.authservice.outbox.dto.OutboxEventEnvelope;
import com.zodiakk.authservice.user.service.UserDeletionCompletionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmployeeAccountEventConsumer {

    private final ObjectMapper objectMapper;

    private final UserDeletionCompletionService deletionService;

    @KafkaListener(
            topics = "${app.kafka.topics.employee-account-events}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consume(String message) {
        OutboxEventEnvelope envelope =
                objectMapper.readValue(message, OutboxEventEnvelope.class);

        if (!"EMPLOYEE_ACCOUNT_UNLINKED".equals(envelope.eventType())
                || envelope.eventVersion() != 1) {
            throw new IllegalArgumentException(
                    "Unsupported employee account event"
            );
        }

        EmployeeAccountUnlinkedEvent event = objectMapper.treeToValue(
                envelope.payload(),
                EmployeeAccountUnlinkedEvent.class
        );

        deletionService.completeDeletion(envelope.eventId(), event);

        log.info(
                "Completed account deletion: userId={}, eventId={}",
                event.userId(),
                envelope.eventId()
        );
    }
}
