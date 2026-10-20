package com.zodiakk.authservice.integration.employee.dto;

import java.util.UUID;

public record EmployeeAccountInfoDto(
        UUID employeeId,
        String email,
        EmploymentStatus employmentStatus,
        UUID userId
) {
}