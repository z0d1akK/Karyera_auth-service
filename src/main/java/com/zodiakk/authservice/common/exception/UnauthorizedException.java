package com.zodiakk.authservice.common.exception;

public abstract class UnauthorizedException extends RuntimeException {

    protected UnauthorizedException(String message) {
        super(message);
    }
}