package com.zodiakk.authservice.user.event;

import com.zodiakk.authservice.common.constants.KafkaEventConstants;
import com.zodiakk.authservice.outbox.service.OutboxEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserEventService {

    private final OutboxEventService outboxEventService;

    public void recordUserCreated(UUID userId, UUID employeeId) {
        outboxEventService.saveEvent(
                employeeId,
                KafkaEventConstants.AGGREGATE_TYPE_EMPLOYEE_ACCOUNT,
                KafkaEventConstants.USER_CREATED,
                KafkaEventConstants.EVENT_VERSION_V1,
                new UserCreatedEvent(userId, employeeId)
        );
    }

    public void recordUserDeleted(UUID userId, UUID employeeId) {
        outboxEventService.saveEvent(
                employeeId,
                KafkaEventConstants.AGGREGATE_TYPE_EMPLOYEE_ACCOUNT,
                KafkaEventConstants.USER_DELETED,
                KafkaEventConstants.EVENT_VERSION_V1,
                new UserDeletedEvent(userId, employeeId)
        );
    }
}