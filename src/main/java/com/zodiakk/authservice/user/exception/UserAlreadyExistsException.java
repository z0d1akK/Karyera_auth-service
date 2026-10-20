package com.zodiakk.authservice.user.exception;

import com.zodiakk.authservice.common.exception.BusinessException;
import com.zodiakk.authservice.common.exception.ErrorMessages;

public class UserAlreadyExistsException extends BusinessException {

    public UserAlreadyExistsException(String identifier) {
        super(ErrorMessages.USER_ALREADY_EXISTS.formatted(identifier));
    }
}
