package com.zodiakk.authservice.auth.exception;

import com.zodiakk.authservice.common.exception.BusinessException;
import com.zodiakk.authservice.common.exception.ErrorMessages;

public class PasswordMustDifferException extends BusinessException {

    public PasswordMustDifferException() {
        super(ErrorMessages.PASSWORD_MUST_DIFFER);
    }
}