package com.zodiakk.authservice.auth.exception;

import com.zodiakk.authservice.common.exception.ErrorMessages;
import com.zodiakk.authservice.common.exception.UnauthorizedException;

public class ExpiredJwtTokenException extends UnauthorizedException {

    public ExpiredJwtTokenException() {
        super(ErrorMessages.EXPIRED_JWT_TOKEN);
    }
}