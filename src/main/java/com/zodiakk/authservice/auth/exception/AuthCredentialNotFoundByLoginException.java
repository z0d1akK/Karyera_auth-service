package com.zodiakk.authservice.auth.exception;

import com.zodiakk.authservice.common.exception.ErrorMessages;
import com.zodiakk.authservice.common.exception.ResourceNotFoundException;

public class AuthCredentialNotFoundByLoginException extends ResourceNotFoundException {

    public AuthCredentialNotFoundByLoginException(String login) {
        super(ErrorMessages.AUTH_CREDENTIAL_NOT_FOUND_BY_LOGIN.formatted(login));
    }
}
