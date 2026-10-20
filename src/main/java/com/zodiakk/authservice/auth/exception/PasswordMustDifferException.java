package com.zodiakk.authservice.auth.exception;

import com.zodiakk.authservice.common.exception.BusinessException;

public class PasswordMustDifferException extends BusinessException {

    public PasswordMustDifferException() {
        super("Новый пароль должен отличаться от текущего");
    }
}