package com.zodiakk.authservice.common.exception;

import lombok.experimental.UtilityClass;

@UtilityClass
public class ErrorMessages {

    public static final String AUTH_CREDENTIAL_NOT_FOUND_BY_LOGIN = "Учётные данные не найдены для логина: %s";

    public static final String AUTH_CREDENTIAL_NOT_FOUND_BY_USER_ID = "Учётные данные не найдены для идентификатора пользователя: %s";

    public static final String AUTH_CREDENTIAL_ALREADY_EXISTS = "Учётные данные уже существуют для логина: %s";

    public static final String USER_NOT_FOUND = "Пользователь не найден с идентификатором: %s";

    public static final String USER_NOT_FOUND_BY_EMAIL = "Пользователь не найден с почтой: %s";

    public static final String USER_ALREADY_EXISTS = "Пользователь уже существует с этим идентификатором: %s";

    public static final String INVALID_JWT_TOKEN = "Недействительный JWT-токен";

    public static final String EXPIRED_JWT_TOKEN = "Срок действия JWT-токена истёк";

    public static final String INVALID_REFRESH_TOKEN = "Недействительный refresh-токен";

    public static final String INVALID_LOGIN_OR_PASSWORD = "Неверный логин или пароль";

    public static final String UNAUTHORIZED = "Пользователь не авторизован";

    public static final String ACCESS_DENIED = "Доступ запрещён";

    public static final String INTERNAL_SERVER_ERROR = "Внутренняя ошибка сервера";
}