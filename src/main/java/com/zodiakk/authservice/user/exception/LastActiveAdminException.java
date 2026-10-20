package com.zodiakk.authservice.user.exception;

import com.zodiakk.authservice.common.exception.BusinessException;
import com.zodiakk.authservice.common.exception.ErrorMessages;

public class LastActiveAdminException extends BusinessException {

    public LastActiveAdminException() {
        super(ErrorMessages.LAST_ACTIVE_ADMIN);
    }
}