package com.zodiakk.authservice.auth.exception;

import com.zodiakk.authservice.common.exception.BusinessException;

public class PasswordChangeRequiredException extends BusinessException {

    public PasswordChangeRequiredException() {
        super("Password change is required before accessing the application");
    }
}
