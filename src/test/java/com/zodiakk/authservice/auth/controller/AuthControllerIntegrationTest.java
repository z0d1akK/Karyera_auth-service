package com.zodiakk.authservice.auth.controller;

import com.zodiakk.authservice.auth.entity.AuthCredential;
import com.zodiakk.authservice.auth.entity.RefreshToken;
import com.zodiakk.authservice.auth.entity.Role;
import com.zodiakk.authservice.auth.security.jwt.JwtTokenType;
import com.zodiakk.authservice.common.AbstractIntegrationTest;
import com.zodiakk.authservice.support.AuthTestDataFactory;
import com.zodiakk.authservice.user.entity.AccountDeletionStatus;
import com.zodiakk.authservice.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Auth API")
class AuthControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String LOGIN = "/api/auth/login";

    private static final String REFRESH = "/api/auth/refresh";

    private static final String LOGOUT = "/api/auth/logout";

    private static final String CHANGE_INITIAL_PASSWORD = "/api/auth/change-initial-password";

    @Nested
    @DisplayName("POST /api/auth/login")
    class Login {

        @Test
        @DisplayName("returns a token pair for valid credentials")
        void shouldLogin() throws Exception {
            User user = persistUser(AuthTestDataFactory.EMAIL, Role.EMPLOYEE,
                    AuthTestDataFactory.RAW_PASSWORD, false);

            MvcResult result = mockMvc.perform(post(LOGIN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(AuthTestDataFactory.loginRequest())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").isNotEmpty())
                    .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                    .andReturn();

            JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
            assertThat(jwtService.parseToken(body.get("accessToken").asString(), JwtTokenType.ACCESS).userId())
                    .isEqualTo(user.getId());
            assertThat(jwtService.parseToken(body.get("refreshToken").asString(), JwtTokenType.REFRESH).userId())
                    .isEqualTo(user.getId());
            assertThat(refreshTokenRepository.findAll())
                    .extracting(RefreshToken::getToken)
                    .containsExactly(body.get("refreshToken").asString());
        }

        @Test
        @DisplayName("normalizes the login")
        void shouldNormalizeLogin() throws Exception {
            persistUser(AuthTestDataFactory.EMAIL, Role.EMPLOYEE, AuthTestDataFactory.RAW_PASSWORD, false);

            mockMvc.perform(post(LOGIN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(Map.of(
                                    "login", "  USER@COMPANY.COM  ",
                                    "password", AuthTestDataFactory.RAW_PASSWORD
                            ))))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("rejects an unknown login")
        void shouldRejectUnknownLogin() throws Exception {
            mockMvc.perform(post(LOGIN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(AuthTestDataFactory.loginRequest())))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        }

        @Test
        @DisplayName("rejects a wrong password")
        void shouldRejectWrongPassword() throws Exception {
            persistUser(AuthTestDataFactory.EMAIL, Role.EMPLOYEE, AuthTestDataFactory.RAW_PASSWORD, false);

            mockMvc.perform(post(LOGIN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(Map.of(
                                    "login", AuthTestDataFactory.EMAIL,
                                    "password", "WrongPassword12345"
                            ))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        }

        @Test
        @DisplayName("requires an initial password change")
        void shouldRequirePasswordChange() throws Exception {
            persistUser(AuthTestDataFactory.EMAIL, Role.EMPLOYEE, AuthTestDataFactory.RAW_PASSWORD, true);

            mockMvc.perform(post(LOGIN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(AuthTestDataFactory.loginRequest())))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
        }

        @Test
        @DisplayName("rejects inactive accounts")
        void shouldRejectInactiveAccount() throws Exception {
            persistUser(AuthTestDataFactory.EMAIL, Role.EMPLOYEE, AuthTestDataFactory.RAW_PASSWORD, false,
                    AuthTestDataFactory.EMPLOYEE_ID, false, AccountDeletionStatus.NONE);

            mockMvc.perform(post(LOGIN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(AuthTestDataFactory.loginRequest())))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("rejects accounts that are being deleted")
        void shouldRejectDeletingAccount() throws Exception {
            persistUser(AuthTestDataFactory.EMAIL, Role.EMPLOYEE, AuthTestDataFactory.RAW_PASSWORD, false,
                    AuthTestDataFactory.EMPLOYEE_ID, false, AccountDeletionStatus.DELETING);

            mockMvc.perform(post(LOGIN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(AuthTestDataFactory.loginRequest())))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("rejects a request without credentials")
        void shouldValidateRequest() throws Exception {
            mockMvc.perform(post(LOGIN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(Map.of("login", "  ", "password", ""))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors").isNotEmpty());
        }
    }

    @Nested
    @DisplayName("POST /api/auth/refresh")
    class Refresh {

        @Test
        @DisplayName("rotates a valid refresh token")
        void shouldRefreshTokens() throws Exception {
            User user = persistUser(AuthTestDataFactory.EMAIL, Role.EMPLOYEE,
                    AuthTestDataFactory.RAW_PASSWORD, false);
            String refreshToken = persistRefreshToken(user);

            MvcResult result = mockMvc.perform(post(REFRESH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(Map.of("refreshToken", refreshToken))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").isNotEmpty())
                    .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                    .andReturn();

            String rotated = objectMapper.readTree(result.getResponse().getContentAsString())
                    .get("refreshToken").asString();
            assertThat(rotated).isNotEqualTo(refreshToken);

            RefreshToken original = tokenByValue(refreshToken);
            assertThat(original.getRevoked()).isTrue();

            RefreshToken storedRotated = tokenByValue(rotated);
            assertThat(storedRotated.getUserId()).isEqualTo(user.getId());
            assertThat(storedRotated.getRevoked()).isFalse();
        }

        @Test
        @DisplayName("rejects an unknown refresh token")
        void shouldRejectUnknownToken() throws Exception {
            mockMvc.perform(post(REFRESH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(Map.of("refreshToken", "not-a-token"))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
        }

        @Test
        @DisplayName("rejects a revoked refresh token")
        void shouldRejectRevokedToken() throws Exception {
            User user = persistUser(AuthTestDataFactory.EMAIL, Role.EMPLOYEE,
                    AuthTestDataFactory.RAW_PASSWORD, false);
            String token = jwtService.generateRefreshToken(user.getId(), Role.EMPLOYEE, user.getEmail());
            refreshTokenRepository.saveAndFlush(AuthTestDataFactory.refreshToken(
                    token,
                    user.getId(),
                    OffsetDateTime.now(ZoneOffset.UTC).plusDays(1),
                    true
            ));

            mockMvc.perform(post(REFRESH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(Map.of("refreshToken", token))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
        }

        @Test
        @DisplayName("rejects an access token passed as a refresh token")
        void shouldRejectWrongTokenType() throws Exception {
            User user = persistUser(AuthTestDataFactory.EMAIL, Role.EMPLOYEE,
                    AuthTestDataFactory.RAW_PASSWORD, false);
            String accessToken = accessToken(user);
            refreshTokenRepository.saveAndFlush(AuthTestDataFactory.refreshToken(
                    accessToken,
                    user.getId(),
                    OffsetDateTime.now(ZoneOffset.UTC).plusDays(1),
                    false
            ));

            mockMvc.perform(post(REFRESH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(Map.of("refreshToken", accessToken))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
        }

        @Test
        @DisplayName("rejects refresh for an inactive account")
        void shouldRejectInactiveAccount() throws Exception {
            User user = persistUser(AuthTestDataFactory.EMAIL, Role.EMPLOYEE,
                    AuthTestDataFactory.RAW_PASSWORD, false,
                    AuthTestDataFactory.EMPLOYEE_ID, false, AccountDeletionStatus.NONE);
            String token = persistRefreshToken(user);

            mockMvc.perform(post(REFRESH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(Map.of("refreshToken", token))))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("rejects refresh when a password change is required")
        void shouldRejectWhenPasswordChangeRequired() throws Exception {
            User user = persistUser(AuthTestDataFactory.EMAIL, Role.EMPLOYEE,
                    AuthTestDataFactory.RAW_PASSWORD, true);
            String token = persistRefreshToken(user);

            mockMvc.perform(post(REFRESH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(Map.of("refreshToken", token))))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("rejects a request without a refresh token")
        void shouldValidateRequest() throws Exception {
            mockMvc.perform(post(REFRESH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("POST /api/auth/change-initial-password")
    class ChangeInitialPassword {

        @Test
        @DisplayName("changes the password and revokes sessions")
        void shouldChangePassword() throws Exception {
            User user = persistUser(AuthTestDataFactory.EMAIL, Role.EMPLOYEE,
                    AuthTestDataFactory.RAW_PASSWORD, true);
            persistRefreshToken(user);

            mockMvc.perform(post(CHANGE_INITIAL_PASSWORD)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(AuthTestDataFactory.changeInitialPasswordRequest())))
                    .andExpect(status().isNoContent());

            AuthCredential credential = credentialOf(user);
            assertThat(credential.getPasswordChangeRequired()).isFalse();
            assertThat(passwordEncoder.matches(AuthTestDataFactory.NEW_PASSWORD, credential.getPasswordHash()))
                    .isTrue();
            assertThat(refreshTokenRepository.findAll())
                    .allMatch(RefreshToken::getRevoked);
        }

        @Test
        @DisplayName("rejects a wrong initial password")
        void shouldRejectWrongInitialPassword() throws Exception {
            persistUser(AuthTestDataFactory.EMAIL, Role.EMPLOYEE, AuthTestDataFactory.RAW_PASSWORD, true);

            mockMvc.perform(post(CHANGE_INITIAL_PASSWORD)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(Map.of(
                                    "login", AuthTestDataFactory.EMAIL,
                                    "initialPassword", "WrongPassword12345",
                                    "newPassword", AuthTestDataFactory.NEW_PASSWORD
                            ))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        }

        @Test
        @DisplayName("rejects accounts that do not require a password change")
        void shouldRejectWhenChangeNotRequired() throws Exception {
            persistUser(AuthTestDataFactory.EMAIL, Role.EMPLOYEE, AuthTestDataFactory.RAW_PASSWORD, false);

            mockMvc.perform(post(CHANGE_INITIAL_PASSWORD)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(AuthTestDataFactory.changeInitialPasswordRequest())))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("rejects a new password equal to the initial one")
        void shouldRejectSamePassword() throws Exception {
            persistUser(AuthTestDataFactory.EMAIL, Role.EMPLOYEE, AuthTestDataFactory.RAW_PASSWORD, true);

            mockMvc.perform(post(CHANGE_INITIAL_PASSWORD)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(Map.of(
                                    "login", AuthTestDataFactory.EMAIL,
                                    "initialPassword", AuthTestDataFactory.RAW_PASSWORD,
                                    "newPassword", AuthTestDataFactory.RAW_PASSWORD
                            ))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("BUSINESS_ERROR"));
        }

        @Test
        @DisplayName("rejects a too short new password")
        void shouldValidateNewPassword() throws Exception {
            persistUser(AuthTestDataFactory.EMAIL, Role.EMPLOYEE, AuthTestDataFactory.RAW_PASSWORD, true);

            mockMvc.perform(post(CHANGE_INITIAL_PASSWORD)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(Map.of(
                                    "login", AuthTestDataFactory.EMAIL,
                                    "initialPassword", AuthTestDataFactory.RAW_PASSWORD,
                                    "newPassword", "short"
                            ))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("newPassword"));
        }

        @Test
        @DisplayName("rejects an unknown login")
        void shouldRejectUnknownLogin() throws Exception {
            mockMvc.perform(post(CHANGE_INITIAL_PASSWORD)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(AuthTestDataFactory.changeInitialPasswordRequest())))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("POST /api/auth/logout")
    class Logout {

        @Test
        @DisplayName("revokes the refresh token of the authenticated user")
        void shouldLogout() throws Exception {
            User user = persistUser(AuthTestDataFactory.EMAIL, Role.EMPLOYEE,
                    AuthTestDataFactory.RAW_PASSWORD, false);
            String token = persistRefreshToken(user);

            mockMvc.perform(post(LOGOUT)
                            .header("Authorization", bearer(user))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(Map.of("refreshToken", token))))
                    .andExpect(status().isNoContent());

            assertThat(tokenByValue(token).getRevoked()).isTrue();
        }

        @Test
        @DisplayName("requires authentication")
        void shouldRequireAuthentication() throws Exception {
            mockMvc.perform(post(LOGOUT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(AuthTestDataFactory.refreshTokenRequest())))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("rejects a refresh token that belongs to another user")
        void shouldRejectForeignToken() throws Exception {
            User user = persistUser(AuthTestDataFactory.EMAIL, Role.EMPLOYEE,
                    AuthTestDataFactory.RAW_PASSWORD, false);
            User other = persistUser(AuthTestDataFactory.OTHER_EMAIL, Role.EMPLOYEE,
                    AuthTestDataFactory.RAW_PASSWORD, false);
            String foreignToken = persistRefreshToken(other);

            mockMvc.perform(post(LOGOUT)
                            .header("Authorization", bearer(user))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(Map.of("refreshToken", foreignToken))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
        }

        @Test
        @DisplayName("rejects an unknown refresh token")
        void shouldRejectUnknownToken() throws Exception {
            User user = persistUser(AuthTestDataFactory.EMAIL, Role.EMPLOYEE,
                    AuthTestDataFactory.RAW_PASSWORD, false);

            mockMvc.perform(post(LOGOUT)
                            .header("Authorization", bearer(user))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(Map.of("refreshToken", "unknown-token"))))
                    .andExpect(status().isUnauthorized());
        }
    }

    private String persistRefreshToken(User user) {
        String token = jwtService.generateRefreshToken(user.getId(), credentialOf(user).getRole(), user.getEmail());
        refreshTokenRepository.saveAndFlush(AuthTestDataFactory.refreshToken(
                token,
                user.getId(),
                OffsetDateTime.now(ZoneOffset.UTC).plusDays(1),
                false
        ));
        return token;
    }

    private RefreshToken tokenByValue(String token) {
        return refreshTokenRepository.findAll().stream()
                .filter(candidate -> candidate.getToken().equals(token))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Refresh token not found: " + token));
    }
}
