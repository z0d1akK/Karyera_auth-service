package com.zodiakk.authservice.common.exception;

import lombok.experimental.UtilityClass;

@UtilityClass
public class ErrorMessages {

    public static final String AUTH_CREDENTIAL_NOT_FOUND_BY_USER_ID = "Учётные данные не найдены для идентификатора пользователя: %s";

    public static final String USER_NOT_FOUND = "Пользователь не найден с идентификатором: %s";

    public static final String USER_ALREADY_EXISTS = "Пользователь уже существует с этим идентификатором: %s";

    public static final String USER_ACCOUNT_BEING_DELETED = "Учётная запись находится в процессе удаления: %s";

    public static final String USER_NOT_IN_DELETING_STATE = "Пользователь не находится в статусе DELETING: %s";

    public static final String LAST_ACTIVE_ADMIN = "Нельзя деактивировать или понизить роль последнего активного администратора";

    public static final String EMPLOYEE_NOT_FOUND = "Сотрудник не найден с идентификатором: %s";

    public static final String EMPLOYEE_ID_MISMATCH = "Идентификатор сотрудника не совпадает для пользователя: %s";

    public static final String EMPLOYEE_ACCOUNT_CREATION_FORBIDDEN = "Статус занятости сотрудника не позволяет создать учётную запись";

    public static final String EMPLOYEE_EMAIL_MISSING = "Не указана корпоративная почта сотрудника";

    public static final String EMPLOYEE_SERVICE_UNAVAILABLE = "Сервис сотрудников временно недоступен";

    public static final String EMPLOYEE_SERVICE_REJECTED_REQUEST = "Сервис сотрудников отклонил внутренний запрос";

    public static final String PASSWORD_CHANGE_REQUIRED = "Перед доступом к приложению необходимо сменить пароль";

    public static final String PASSWORD_MUST_DIFFER = "Новый пароль должен отличаться от текущего";

    public static final String INVALID_JWT_TOKEN = "Недействительный JWT-токен";

    public static final String EXPIRED_JWT_TOKEN = "Срок действия JWT-токена истёк";

    public static final String INVALID_REFRESH_TOKEN = "Недействительный refresh-токен";

    public static final String INVALID_LOGIN_OR_PASSWORD = "Неверный логин или пароль";

    public static final String INVALID_INTERNAL_API_KEY = "Неверный внутренний API-ключ";

    public static final String UNAUTHORIZED = "Пользователь не авторизован";

    public static final String ACCESS_DENIED = "Доступ запрещён";

    public static final String DATA_INTEGRITY_VIOLATION = "Операция нарушает ограничения целостности данных";

    public static final String UNSUPPORTED_EMPLOYEE_ACCOUNT_EVENT = "Неподдерживаемое событие учётной записи сотрудника";

    public static final String OUTBOX_EVENT_SERIALIZATION_FAILED = "Не удалось сериализовать outbox-событие: %s";

    public static final String INVALID_REQUEST_BODY = "Некорректное тело запроса";

    public static final String RESOURCE_NOT_FOUND = "Запрошенный ресурс не найден";

    public static final String METHOD_NOT_ALLOWED = "Метод запроса не поддерживается";

    public static final String UNSUPPORTED_MEDIA_TYPE = "Неподдерживаемый тип содержимого запроса";

    public static final String INVALID_PARAMETER = "Некорректное значение параметра: %s";

    public static final String MISSING_PARAMETER = "Обязательный параметр отсутствует: %s";

    public static final String INTERNAL_SERVER_ERROR = "Внутренняя ошибка сервера";
}