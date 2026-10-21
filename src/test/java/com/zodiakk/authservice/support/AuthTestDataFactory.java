package com.zodiakk.authservice.support;

import com.zodiakk.authservice.auth.dto.request.ChangeInitialPasswordRequestDto;
import com.zodiakk.authservice.auth.dto.request.LoginRequestDto;
import com.zodiakk.authservice.auth.dto.request.RefreshTokenRequestDto;
import com.zodiakk.authservice.auth.dto.request.ValidateTokenRequestDto;
import com.zodiakk.authservice.auth.entity.AuthCredential;
import com.zodiakk.authservice.auth.entity.RefreshToken;
import com.zodiakk.authservice.auth.entity.Role;
import com.zodiakk.authservice.auth.security.jwt.JwtTokenClaims;
import com.zodiakk.authservice.auth.security.jwt.JwtTokenType;
import com.zodiakk.authservice.integration.employee.dto.EmployeeAccountInfoDto;
import com.zodiakk.authservice.integration.employee.dto.EmploymentStatus;
import com.zodiakk.authservice.user.dto.CreateUserRequestDto;
import com.zodiakk.authservice.user.dto.ResetUserPasswordRequestDto;
import com.zodiakk.authservice.user.dto.UpdateUserRequestDto;
import com.zodiakk.authservice.user.dto.UserResponseDto;
import com.zodiakk.authservice.user.entity.AccountDeletionStatus;
import com.zodiakk.authservice.user.entity.User;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

public final class AuthTestDataFactory {

    public static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    public static final UUID OTHER_USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    public static final UUID EMPLOYEE_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    public static final UUID OTHER_EMPLOYEE_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    public static final UUID TOKEN_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");

    public static final String EMAIL = "user@company.com";

    public static final String OTHER_EMAIL = "other@company.com";

    public static final String RAW_PASSWORD = "Password12345";

    public static final String NEW_PASSWORD = "BrandNewPassword12345";

    public static final String TEMPORARY_PASSWORD = "TemporaryPassword12345";

    public static final String ENCODED_PASSWORD = "$2a$10$encoded-password-hash";

    public static final String ACCESS_TOKEN = "access-token-value";

    public static final String REFRESH_TOKEN = "refresh-token-value";

    private AuthTestDataFactory() {
    }

    public static User user() {
        return user(USER_ID, EMPLOYEE_ID, EMAIL, true, AccountDeletionStatus.NONE);
    }

    public static User user(
            UUID id,
            UUID employeeId,
            String email,
            boolean active,
            AccountDeletionStatus deletionStatus
    ) {
        return User.builder()
                .id(id)
                .employeeId(employeeId)
                .email(email)
                .active(active)
                .deletionStatus(deletionStatus)
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                .updatedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();
    }

    public static AuthCredential credential() {
        return credential(USER_ID, EMAIL, Role.EMPLOYEE, false);
    }

    public static AuthCredential credential(
            UUID userId,
            String login,
            Role role,
            boolean passwordChangeRequired
    ) {
        return AuthCredential.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .login(login)
                .passwordHash(ENCODED_PASSWORD)
                .role(role)
                .passwordChangeRequired(passwordChangeRequired)
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                .updatedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();
    }

    public static RefreshToken refreshToken() {
        return refreshToken(REFRESH_TOKEN, USER_ID, OffsetDateTime.now(ZoneOffset.UTC).plusDays(7), false);
    }

    public static RefreshToken refreshToken(
            String token,
            UUID userId,
            OffsetDateTime expiresAt,
            boolean revoked
    ) {
        return RefreshToken.builder()
                .userId(userId)
                .token(token)
                .expiresAt(expiresAt)
                .revoked(revoked)
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                .updatedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();
    }

    public static JwtTokenClaims claims() {
        return claims(USER_ID, Role.EMPLOYEE, EMAIL, JwtTokenType.ACCESS);
    }

    public static JwtTokenClaims claims(UUID userId, Role role, String email, JwtTokenType tokenType) {
        Instant now = Instant.now();
        return new JwtTokenClaims(
                userId,
                role,
                email,
                tokenType,
                TOKEN_ID,
                now,
                now.plusSeconds(3600)
        );
    }

    public static LoginRequestDto loginRequest() {
        return LoginRequestDto.builder()
                .login(EMAIL)
                .password(RAW_PASSWORD)
                .build();
    }

    public static RefreshTokenRequestDto refreshTokenRequest() {
        return RefreshTokenRequestDto.builder()
                .refreshToken(REFRESH_TOKEN)
                .build();
    }

    public static ValidateTokenRequestDto validateTokenRequest() {
        return ValidateTokenRequestDto.builder()
                .token(ACCESS_TOKEN)
                .build();
    }

    public static ChangeInitialPasswordRequestDto changeInitialPasswordRequest() {
        return ChangeInitialPasswordRequestDto.builder()
                .login(EMAIL)
                .initialPassword(RAW_PASSWORD)
                .newPassword(NEW_PASSWORD)
                .build();
    }

    public static CreateUserRequestDto createUserRequest(Role role) {
        return CreateUserRequestDto.builder()
                .employeeId(EMPLOYEE_ID)
                .role(role)
                .initialPassword(RAW_PASSWORD)
                .build();
    }

    public static UpdateUserRequestDto updateUserRequest(Role role, boolean active) {
        return UpdateUserRequestDto.builder()
                .role(role)
                .active(active)
                .build();
    }

    public static ResetUserPasswordRequestDto resetPasswordRequest() {
        ResetUserPasswordRequestDto request = new ResetUserPasswordRequestDto();
        request.setTemporaryPassword(TEMPORARY_PASSWORD);
        return request;
    }

    public static UserResponseDto userResponse() {
        return UserResponseDto.builder()
                .id(USER_ID)
                .employeeId(EMPLOYEE_ID)
                .email(EMAIL)
                .role(Role.EMPLOYEE)
                .active(true)
                .passwordChangeRequired(false)
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                .updatedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();
    }

    public static EmployeeAccountInfoDto employee(
            UUID employeeId,
            String email,
            EmploymentStatus status,
            UUID userId
    ) {
        return new EmployeeAccountInfoDto(employeeId, email, status, userId);
    }

    public static EmployeeAccountInfoDto activeEmployee() {
        return employee(EMPLOYEE_ID, EMAIL, EmploymentStatus.ACTIVE, null);
    }
}
