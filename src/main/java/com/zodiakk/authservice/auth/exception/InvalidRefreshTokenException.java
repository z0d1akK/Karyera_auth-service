package com.zodiakk.authservice.auth.exception;

import com.zodiakk.authservice.common.exception.ErrorMessages;
import com.zodiakk.authservice.common.exception.UnauthorizedException;

public class InvalidRefreshTokenException extends UnauthorizedException {

    public InvalidRefreshTokenException() {
        super(ErrorMessages.INVALID_REFRESH_TOKEN);
    }
}