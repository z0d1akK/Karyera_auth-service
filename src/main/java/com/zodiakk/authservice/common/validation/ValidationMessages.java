package com.zodiakk.authservice.common.validation;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ValidationMessages {

    public static final String USER_EMAIL_REQUIRED = "Электронная почта пользователя обязательна";

    public static final String USER_EMAIL_SIZE = "Электронная почта пользователя не должна превышать 255 символов";

    public static final String USER_PASSWORD_REQUIRED = "Пароль пользователя обязателен";

    public static final String USER_PASSWORD_SIZE = "Пароль пользователя должен содержать от 8 до 255 символов";

    public static final String REFRESH_TOKEN_REQUIRED = "Refresh-токен обязателен";

    public static final String TOKEN_REQUIRED = "Токен обязателен";

    public static final String EMPLOYEE_ID_REQUIRED = "Идентификатор сотрудника обязателен";

    public static final String ROLE_REQUIRED = "Роль обязательна";

    public static final String ACTIVE_REQUIRED = "Признак активности обязателен";

    public static final String NEW_PASSWORD_SIZE = "Новый пароль должен содержать от 12 до 255 символов";

    public static final String TEMPORARY_PASSWORD_SIZE = "Временный пароль должен содержать от 12 до 128 символов";
}