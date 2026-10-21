package com.zodiakk.authservice.auth.security.utils;

import com.zodiakk.authservice.auth.entity.Role;
import com.zodiakk.authservice.auth.security.jwt.principal.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityContextUtilsTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("returns the authenticated user from the security context")
    void shouldReturnCurrentUser() {
        authenticate();

        AuthenticatedUser user = SecurityContextUtils.getCurrentUser();

        assertThat(user.getUserId()).isEqualTo(USER_ID);
        assertThat(user.getRole()).isEqualTo(Role.HR);
        assertThat(SecurityContextUtils.getCurrentUserId()).isEqualTo(USER_ID);
        assertThat(SecurityContextUtils.getCurrentUserRole()).isEqualTo(Role.HR);
    }

    @Test
    @DisplayName("fails when there is no authentication")
    void shouldFailWithoutAuthentication() {
        assertThatThrownBy(SecurityContextUtils::getCurrentUser)
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);

        assertThatThrownBy(SecurityContextUtils::getCurrentUserId)
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);

        assertThatThrownBy(SecurityContextUtils::getCurrentUserRole)
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    @DisplayName("fails when the principal is not an authenticated user")
    void shouldFailForForeignPrincipal() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("anonymous", null, List.of())
        );

        assertThatThrownBy(SecurityContextUtils::getCurrentUser)
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    @DisplayName("fails when authentication is not authenticated")
    void shouldFailForUnauthenticatedToken() {
        AuthenticatedUser principal = principal();
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, List.of());
        authentication.setAuthenticated(false);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        assertThatThrownBy(SecurityContextUtils::getCurrentUser)
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    private void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal(), null, List.of())
        );
    }

    private AuthenticatedUser principal() {
        return AuthenticatedUser.builder()
                .userId(USER_ID)
                .email("hr@company.com")
                .role(Role.HR)
                .build();
    }
}
