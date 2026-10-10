package com.zodiakk.authservice.common.validation;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ValidationMessages {

    public static final String USER_ID_REQUIRED = "Идентификатор пользователя обязателен";

    public static final String USER_EMAIL_REQUIRED = "Электронная почта пользователя обязательна";

    public static final String USER_EMAIL_VALID = "Электронная почта пользователя должна быть действительной";

    public static final String USER_EMAIL_SIZE = "Электронная почта пользователя не должна превышать 255 символов";

    public static final String USER_PASSWORD_REQUIRED = "Пароль пользователя обязателен";

    public static final String USER_PASSWORD_SIZE = "Пароль пользователя должен содержать от 8 до 255 символов";

    public static final String USER_ROLE_REQUIRED = "Роль пользователя обязательна";

    public static final String REFRESH_TOKEN_REQUIRED = "Refresh-токен обязателен";

    public static final String TOKEN_REQUIRED = "Токен обязателен";

    public static final String REGISTRATION_REJECTION_REASON_SIZE = "Причина отклонения регистрации не должна превышать 1000 символов";
}