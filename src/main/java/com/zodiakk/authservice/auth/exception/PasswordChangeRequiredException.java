package com.zodiakk.authservice.auth.exception;

import com.zodiakk.authservice.common.exception.BusinessException;
import com.zodiakk.authservice.common.exception.ErrorMessages;

public class PasswordChangeRequiredException extends BusinessException {

    public PasswordChangeRequiredException() {
        super(ErrorMessages.PASSWORD_CHANGE_REQUIRED);
    }
}