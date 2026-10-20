package com.zodiakk.authservice.integration.employee.exception;

import com.zodiakk.authservice.common.exception.ResourceNotFoundException;

import java.util.UUID;

public class EmployeeNotFoundException extends ResourceNotFoundException {

    public EmployeeNotFoundException(UUID employeeId) {
        super("Employee not found: " + employeeId);
    }
}
