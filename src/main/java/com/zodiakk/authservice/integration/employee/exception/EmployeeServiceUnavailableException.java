package com.zodiakk.authservice.integration.employee.exception;

import com.zodiakk.authservice.common.exception.BusinessException;

public class EmployeeServiceUnavailableException extends BusinessException {

    public EmployeeServiceUnavailableException() {
        super("Employee Service is temporarily unavailable");
    }
}
