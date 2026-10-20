package com.zodiakk.authservice.user.event;

import java.util.UUID;

public record UserCreatedEvent(
        UUID userId,
        UUID employeeId
) {
}