package com.zodiakk.authservice.user.exception;

import com.zodiakk.authservice.common.exception.BusinessException;

public class LastActiveAdminException extends BusinessException {

    public LastActiveAdminException() {
        super("Нельзя деактивировать или понизить роль последнего активного администратора");
    }
}