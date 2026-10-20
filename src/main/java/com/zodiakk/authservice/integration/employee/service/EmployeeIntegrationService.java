package com.zodiakk.authservice.integration.employee.service;

import com.zodiakk.authservice.integration.employee.dto.EmployeeAccountInfoDto;

import java.util.UUID;

public interface EmployeeIntegrationService {

    EmployeeAccountInfoDto getAccountInfo(UUID employeeId);
}
