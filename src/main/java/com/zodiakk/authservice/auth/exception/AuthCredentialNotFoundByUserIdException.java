package com.zodiakk.authservice.auth.exception;

import com.zodiakk.authservice.common.exception.ErrorMessages;
import com.zodiakk.authservice.common.exception.ResourceNotFoundException;

import java.util.UUID;

public class AuthCredentialNotFoundByUserIdException extends ResourceNotFoundException {

    public AuthCredentialNotFoundByUserIdException(UUID userId) {
        super(ErrorMessages.AUTH_CREDENTIAL_NOT_FOUND_BY_USER_ID.formatted(userId));
    }
}