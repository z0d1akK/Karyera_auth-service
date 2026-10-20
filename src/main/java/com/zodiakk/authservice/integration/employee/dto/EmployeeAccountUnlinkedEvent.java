package com.zodiakk.authservice.integration.employee.dto;

import java.util.UUID;

public record EmployeeAccountUnlinkedEvent(
        UUID userId,
        UUID employeeId
) {
}