package com.zodiakk.authservice.common.exception.handler;

import com.zodiakk.authservice.auth.exception.*;
import com.zodiakk.authservice.common.dto.field.ValidationErrorField;
import com.zodiakk.authservice.common.dto.response.ApiErrorResponse;
import com.zodiakk.authservice.common.dto.response.ValidationErrorResponse;
import com.zodiakk.authservice.common.exception.BusinessException;
import com.zodiakk.authservice.common.exception.ErrorMessages;
import com.zodiakk.authservice.common.exception.ResourceNotFoundException;
import com.zodiakk.authservice.common.exception.UnauthorizedException;
import com.zodiakk.authservice.integration.employee.exception.EmployeeServiceUnavailableException;
import com.zodiakk.authservice.user.exception.LastActiveAdminException;
import com.zodiakk.authservice.user.exception.UserAlreadyExistsException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(PasswordChangeRequiredException.class)
    public ResponseEntity<ApiErrorResponse> handlePasswordChangeRequired(PasswordChangeRequiredException ex) {
        return error(
                HttpStatus.FORBIDDEN,
                "PASSWORD_CHANGE_REQUIRED",
                ex.getMessage()
        );
    }

    @ExceptionHandler(LastActiveAdminException.class)
    public ResponseEntity<ApiErrorResponse> handleLastActiveAdmin(LastActiveAdminException ex) {
        return error(
                HttpStatus.CONFLICT,
                "LAST_ACTIVE_ADMIN",
                ex.getMessage()
        );
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<ApiErrorResponse> handleUserAlreadyExists(UserAlreadyExistsException ex) {
        return error(
                HttpStatus.CONFLICT,
                "USER_ALREADY_EXISTS",
                ex.getMessage()
        );
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(ResourceNotFoundException ex) {
        return error(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                ex.getMessage()
        );
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiErrorResponse> handleUnauthorized(UnauthorizedException ex) {
        String code = switch (ex) {
            case InvalidCredentialsException ignored -> "INVALID_CREDENTIALS";
            case InvalidRefreshTokenException ignored -> "INVALID_REFRESH_TOKEN";
            case ExpiredJwtTokenException ignored -> "EXPIRED_JWT_TOKEN";
            default -> "UNAUTHORIZED";
        };

        return error(
                HttpStatus.UNAUTHORIZED,
                code,
                ex.getMessage(),
                false
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        return error(
                HttpStatus.FORBIDDEN,
                "ACCESS_DENIED",
                ErrorMessages.ACCESS_DENIED
        );
    }

    @ExceptionHandler(EmployeeServiceUnavailableException.class)
    public ResponseEntity<ApiErrorResponse> handleEmployeeUnavailable(EmployeeServiceUnavailableException ex) {
        return error(
                HttpStatus.SERVICE_UNAVAILABLE,
                "EMPLOYEE_SERVICE_UNAVAILABLE",
                "Сервис сотрудников временно недоступен"
        );
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiErrorResponse> handleBusiness(BusinessException ex) {
        return error(
                HttpStatus.BAD_REQUEST,
                "BUSINESS_ERROR",
                ex.getMessage()
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        List<ValidationErrorField> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::mapFieldError)
                .toList();

        return validationError(errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ValidationErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        List<ValidationErrorField> errors = ex.getConstraintViolations()
                .stream()
                .map(violation -> ValidationErrorField.builder()
                        .field(violation.getPropertyPath().toString())
                        .message(violation.getMessage())
                        .build())
                .toList();

        return validationError(errors);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Database integrity constraint violation", ex);

        return error(
                HttpStatus.CONFLICT,
                "DATA_INTEGRITY_VIOLATION",
                "Операция нарушает ограничения целостности данных"
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleInternal(Exception ex) {
        log.error("Unhandled exception", ex);

        return error(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_SERVER_ERROR",
                ErrorMessages.INTERNAL_SERVER_ERROR
        );
    }

    private ResponseEntity<ApiErrorResponse> error(HttpStatus status, String code, String message) {
        return error(status, code, message, null);
    }

    private ResponseEntity<ApiErrorResponse> error(HttpStatus status, String code, String message, Boolean valid) {
        ApiErrorResponse response = ApiErrorResponse.builder()
                .valid(valid)
                .status(status.value())
                .code(code)
                .error(status.getReasonPhrase())
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.status(status).body(response);
    }

    private ResponseEntity<ValidationErrorResponse> validationError(List<ValidationErrorField> errors) {
        ValidationErrorResponse response =
                ValidationErrorResponse.builder()
                        .status(HttpStatus.BAD_REQUEST.value())
                        .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                        .errors(errors)
                        .timestamp(LocalDateTime.now())
                        .build();
        return ResponseEntity.badRequest().body(response);
    }

    private ValidationErrorField mapFieldError(FieldError error) {
        return ValidationErrorField.builder()
                .field(error.getField())
                .message(error.getDefaultMessage())
                .build();
    }
}
