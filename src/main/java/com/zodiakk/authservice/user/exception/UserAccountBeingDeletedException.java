package com.zodiakk.authservice.user.exception;

import com.zodiakk.authservice.common.exception.BusinessException;
import com.zodiakk.authservice.common.exception.ErrorMessages;

import java.util.UUID;

public class UserAccountBeingDeletedException extends BusinessException {

    public UserAccountBeingDeletedException(UUID userId) {
        super(ErrorMessages.USER_ACCOUNT_BEING_DELETED.formatted(userId));
    }
}