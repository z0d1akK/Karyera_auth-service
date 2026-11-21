package com.zodiakk.authservice.integration.employee.config;

import feign.Request;
import feign.RetryableException;
import feign.Retryer;

public final class IdempotentGetRetryer implements Retryer {

    private final Retryer.Default delegate = new Retryer.Default(200, 500, 2);

    @Override
    public void continueOrPropagate(RetryableException exception) {
        if (exception.method() != Request.HttpMethod.GET) {
            throw exception;
        }
        delegate.continueOrPropagate(exception);
    }

    @Override
    public Retryer clone() {
        return new IdempotentGetRetryer();
    }
}
