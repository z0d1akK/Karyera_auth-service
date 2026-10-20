package com.zodiakk.authservice.user.exception;

import com.zodiakk.authservice.common.exception.BusinessException;

import java.util.UUID;

public class UserAccountBeingDeletedException extends BusinessException {

    public UserAccountBeingDeletedException(UUID userId) {
        super("User account is being deleted: " + userId);
    }
}