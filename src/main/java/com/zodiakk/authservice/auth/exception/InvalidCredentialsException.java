package com.zodiakk.authservice.auth.exception;

import com.zodiakk.authservice.common.exception.ErrorMessages;
import com.zodiakk.authservice.common.exception.UnauthorizedException;

public class InvalidCredentialsException extends UnauthorizedException {

    public InvalidCredentialsException() {
        super(ErrorMessages.INVALID_LOGIN_OR_PASSWORD);
    }
}
