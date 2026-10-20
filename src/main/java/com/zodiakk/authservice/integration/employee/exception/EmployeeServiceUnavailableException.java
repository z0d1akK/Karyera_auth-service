package com.zodiakk.authservice.integration.employee.exception;

import com.zodiakk.authservice.common.exception.BusinessException;
import com.zodiakk.authservice.common.exception.ErrorMessages;

public class EmployeeServiceUnavailableException extends BusinessException {

    public EmployeeServiceUnavailableException() {
        super(ErrorMessages.EMPLOYEE_SERVICE_UNAVAILABLE);
    }
}
