package com.zodiakk.authservice.user.event;

import java.util.UUID;

public record UserDeletedEvent(
        UUID userId,
        UUID employeeId
) {
}