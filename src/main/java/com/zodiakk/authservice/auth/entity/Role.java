package com.zodiakk.authservice.auth.entity;

import lombok.Getter;

@Getter
public enum Role {

    EMPLOYEE("Сотрудник"),
    MANAGER("Менеджер"),
    HR("HR специалист"),
    ADMIN("Администратор");

    private final String roleName;

    Role(String roleName) {
        this.roleName = roleName;
    }
}