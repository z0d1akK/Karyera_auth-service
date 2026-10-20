package com.zodiakk.authservice.user.exception;

import com.zodiakk.authservice.common.exception.ErrorMessages;
import com.zodiakk.authservice.common.exception.ResourceNotFoundException;

public class UserNotFoundByEmailException extends ResourceNotFoundException {

    public UserNotFoundByEmailException(String email) {
        super(ErrorMessages.USER_NOT_FOUND_BY_EMAIL.formatted(email));
    }
}