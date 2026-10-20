package com.zodiakk.authservice.auth.exception;

import com.zodiakk.authservice.common.exception.ErrorMessages;
import com.zodiakk.authservice.common.exception.UnauthorizedException;

public class InvalidJwtTokenException extends UnauthorizedException {

    public InvalidJwtTokenException() {
        super(ErrorMessages.INVALID_JWT_TOKEN);
    }
}