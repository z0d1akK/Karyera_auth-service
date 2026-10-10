package com.zodiakk.authservice.registration.entity;

import lombok.Getter;

@Getter
public enum RegistrationRequestStatus {

    PENDING("В обработке"),
    APPROVED("Подтвержден"),
    REJECTED("Отказан");

    private final String statusName;

    RegistrationRequestStatus(String statusName) {
        this.statusName = statusName;
    }
}