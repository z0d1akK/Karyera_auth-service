package com.zodiakk.authservice.user.exception;

import com.zodiakk.authservice.common.exception.BusinessException;

public class EmployeeAccountCreationException extends BusinessException {

    public EmployeeAccountCreationException(String message) {
        super(message);
    }
}