package com.zodiakk.authservice.auth.exception;

import com.zodiakk.authservice.common.exception.BusinessException;
import com.zodiakk.authservice.common.exception.ErrorMessages;

public class AuthCredentialAlreadyExistsException extends BusinessException {

    public AuthCredentialAlreadyExistsException(String login) {
        super(ErrorMessages.AUTH_CREDENTIAL_ALREADY_EXISTS.formatted(login));
    }
}
