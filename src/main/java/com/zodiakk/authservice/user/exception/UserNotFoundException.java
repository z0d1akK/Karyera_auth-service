package com.zodiakk.authservice.user.exception;

import com.zodiakk.authservice.common.exception.ErrorMessages;
import com.zodiakk.authservice.common.exception.ResourceNotFoundException;

import java.util.UUID;

public class UserNotFoundException extends ResourceNotFoundException {

    public UserNotFoundException(UUID id) {
        super(ErrorMessages.USER_NOT_FOUND.formatted(id));
    }
}
