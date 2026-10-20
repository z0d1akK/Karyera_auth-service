package com.zodiakk.authservice.user.event;

import com.zodiakk.authservice.outbox.service.OutboxEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserEventService {

    private final OutboxEventService outboxEventService;

    public void publishUserCreated(UUID userId, UUID employeeId) {
        outboxEventService.saveEvent(
                employeeId,
                "EMPLOYEE_ACCOUNT",
                "USER_CREATED",
                1,
                new UserCreatedEvent(userId, employeeId)
        );
    }

    public void recordUserDeleted(UUID userId, UUID employeeId) {
        outboxEventService.saveEvent(
                employeeId,
                "EMPLOYEE_ACCOUNT",
                "USER_DELETED",
                1,
                new UserDeletedEvent(userId, employeeId)
        );
    }
}