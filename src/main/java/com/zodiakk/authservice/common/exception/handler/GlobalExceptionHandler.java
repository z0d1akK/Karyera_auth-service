package com.zodiakk.authservice.common.exception.handler;

import com.zodiakk.authservice.auth.exception.*;
import com.zodiakk.authservice.common.dto.field.ValidationErrorField;
import com.zodiakk.authservice.common.dto.response.ApiErrorResponse;
import com.zodiakk.authservice.common.dto.response.ValidationErrorResponse;
import com.zodiakk.authservice.common.exception.BusinessException;
import com.zodiakk.authservice.common.exception.ErrorMessages;
import com.zodiakk.authservice.common.exception.ResourceNotFoundException;
import com.zodiakk.authservice.common.exception.UnauthorizedException;
import com.zodiakk.authservice.integration.employee.exception.EmployeeIntegrationException;
import com.zodiakk.authservice.integration.employee.exception.EmployeeServiceUnavailableException;
import com.zodiakk.authservice.user.exception.LastActiveAdminException;
import com.zodiakk.authservice.user.exception.UserAlreadyExistsException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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
                ErrorMessages.EMPLOYEE_SERVICE_UNAVAILABLE
        );
    }

    @ExceptionHandler(EmployeeIntegrationException.class)
    public ResponseEntity<ApiErrorResponse> handleEmployeeIntegration(EmployeeIntegrationException ex) {
        log.warn("Employee service rejected internal request", ex);

        return error(
                HttpStatus.BAD_GATEWAY,
                "EMPLOYEE_SERVICE_REJECTED_REQUEST",
                ErrorMessages.EMPLOYEE_SERVICE_REJECTED_REQUEST
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

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleNotReadable(HttpMessageNotReadableException ex) {
        return error(
                HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST_BODY",
                ErrorMessages.INVALID_REQUEST_BODY
        );
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNoResourceFound(NoResourceFoundException ex) {
        return error(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                ErrorMessages.RESOURCE_NOT_FOUND
        );
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return error(
                HttpStatus.METHOD_NOT_ALLOWED,
                "METHOD_NOT_ALLOWED",
                ErrorMessages.METHOD_NOT_ALLOWED
        );
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex) {
        return error(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "UNSUPPORTED_MEDIA_TYPE",
                ErrorMessages.UNSUPPORTED_MEDIA_TYPE
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return error(
                HttpStatus.BAD_REQUEST,
                "INVALID_PARAMETER",
                ErrorMessages.INVALID_PARAMETER.formatted(ex.getName())
        );
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingParameter(MissingServletRequestParameterException ex) {
        return error(
                HttpStatus.BAD_REQUEST,
                "MISSING_PARAMETER",
                ErrorMessages.MISSING_PARAMETER.formatted(ex.getParameterName())
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Database integrity constraint violation", ex);

        return error(
                HttpStatus.CONFLICT,
                "DATA_INTEGRITY_VIOLATION",
                ErrorMessages.DATA_INTEGRITY_VIOLATION
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
