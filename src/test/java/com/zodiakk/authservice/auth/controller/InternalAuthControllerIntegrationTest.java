package com.zodiakk.authservice.auth.controller;

import com.zodiakk.authservice.auth.entity.Role;
import com.zodiakk.authservice.common.AbstractIntegrationTest;
import com.zodiakk.authservice.common.constants.SecurityConstants;
import com.zodiakk.authservice.support.AuthTestDataFactory;
import com.zodiakk.authservice.user.entity.AccountDeletionStatus;
import com.zodiakk.authservice.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Internal auth API")
class InternalAuthControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String VALIDATE = "/internal/v1/auth/validate";

    private static final String INTERNAL_KEY = "test-internal-api-key";

    @Nested
    @DisplayName("POST /internal/v1/auth/validate")
    class ValidateToken {

        @Test
        @DisplayName("returns claims for a valid access token")
        void shouldValidateToken() throws Exception {
            User user = persistUser(AuthTestDataFactory.EMAIL, Role.MANAGER,
                    AuthTestDataFactory.RAW_PASSWORD, false);

            mockMvc.perform(post(VALIDATE)
                            .header(SecurityConstants.INTERNAL_API_KEY_HEADER, INTERNAL_KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(Map.of("token", accessToken(user)))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.valid").value(true))
                    .andExpect(jsonPath("$.userId").value(user.getId().toString()))
                    .andExpect(jsonPath("$.role").value("MANAGER"))
                    .andExpect(jsonPath("$.email").value(AuthTestDataFactory.EMAIL));
        }

        @Test
        @DisplayName("requires the internal API key")
        void shouldRequireInternalKey() throws Exception {
            mockMvc.perform(post(VALIDATE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(Map.of("token", "anything"))))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("rejects an invalid internal API key")
        void shouldRejectInvalidInternalKey() throws Exception {
            mockMvc.perform(post(VALIDATE)
                            .header(SecurityConstants.INTERNAL_API_KEY_HEADER, "wrong-key")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(Map.of("token", "anything"))))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("reports malformed tokens as invalid")
        void shouldReportMalformedToken() throws Exception {
            mockMvc.perform(post(VALIDATE)
                            .header(SecurityConstants.INTERNAL_API_KEY_HEADER, INTERNAL_KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(Map.of("token", "not-a-jwt"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.valid").value(false));
        }

        @Test
        @DisplayName("reports tokens of unknown users as invalid")
        void shouldReportUnknownUser() throws Exception {
            String token = jwtService.generateAccessToken(UUID.randomUUID(), Role.EMPLOYEE, AuthTestDataFactory.EMAIL);

            mockMvc.perform(post(VALIDATE)
                            .header(SecurityConstants.INTERNAL_API_KEY_HEADER, INTERNAL_KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(Map.of("token", token))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.valid").value(false));
        }

        @Test
        @DisplayName("reports tokens of inactive users as invalid")
        void shouldReportInactiveUser() throws Exception {
            User user = persistUser(AuthTestDataFactory.EMAIL, Role.EMPLOYEE,
                    AuthTestDataFactory.RAW_PASSWORD, false,
                    AuthTestDataFactory.EMPLOYEE_ID, false, AccountDeletionStatus.NONE);
            String token = jwtService.generateAccessToken(user.getId(), Role.EMPLOYEE, user.getEmail());

            mockMvc.perform(post(VALIDATE)
                            .header(SecurityConstants.INTERNAL_API_KEY_HEADER, INTERNAL_KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(Map.of("token", token))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.valid").value(false));
        }

        @Test
        @DisplayName("reports tokens of accounts being deleted as invalid")
        void shouldReportDeletingUser() throws Exception {
            User user = persistUser(AuthTestDataFactory.EMAIL, Role.EMPLOYEE,
                    AuthTestDataFactory.RAW_PASSWORD, false,
                    AuthTestDataFactory.EMPLOYEE_ID, false, AccountDeletionStatus.DELETING);
            String token = jwtService.generateAccessToken(user.getId(), Role.EMPLOYEE, user.getEmail());

            mockMvc.perform(post(VALIDATE)
                            .header(SecurityConstants.INTERNAL_API_KEY_HEADER, INTERNAL_KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(Map.of("token", token))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.valid").value(false));
        }

        @Test
        @DisplayName("reports tokens with an outdated role as invalid")
        void shouldReportOutdatedRole() throws Exception {
            User user = persistUser(AuthTestDataFactory.EMAIL, Role.EMPLOYEE,
                    AuthTestDataFactory.RAW_PASSWORD, false);
            String token = jwtService.generateAccessToken(user.getId(), Role.ADMIN, user.getEmail());

            mockMvc.perform(post(VALIDATE)
                            .header(SecurityConstants.INTERNAL_API_KEY_HEADER, INTERNAL_KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(Map.of("token", token))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.valid").value(false));
        }

        @Test
        @DisplayName("reports records whose password change is pending as invalid")
        void shouldReportPendingPasswordChange() throws Exception {
            User user = persistUser(AuthTestDataFactory.EMAIL, Role.EMPLOYEE,
                    AuthTestDataFactory.RAW_PASSWORD, true);
            String token = jwtService.generateAccessToken(user.getId(), Role.EMPLOYEE, user.getEmail());

            mockMvc.perform(post(VALIDATE)
                            .header(SecurityConstants.INTERNAL_API_KEY_HEADER, INTERNAL_KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(Map.of("token", token))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.valid").value(false));
        }

        @Test
        @DisplayName("reports users without credentials as invalid")
        void shouldReportMissingCredential() throws Exception {
            User user = persistUser(AuthTestDataFactory.EMAIL, Role.EMPLOYEE,
                    AuthTestDataFactory.RAW_PASSWORD, false);
            authCredentialRepository.deleteAll();
            String token = jwtService.generateAccessToken(user.getId(), Role.EMPLOYEE, user.getEmail());

            mockMvc.perform(post(VALIDATE)
                            .header(SecurityConstants.INTERNAL_API_KEY_HEADER, INTERNAL_KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(Map.of("token", token))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.valid").value(false));
        }

        @Test
        @DisplayName("reports tokens with a changed email as invalid")
        void shouldReportOutdatedEmail() throws Exception {
            User user = persistUser(AuthTestDataFactory.EMAIL, Role.EMPLOYEE,
                    AuthTestDataFactory.RAW_PASSWORD, false);
            String token = jwtService.generateAccessToken(user.getId(), Role.EMPLOYEE, "old@company.com");

            mockMvc.perform(post(VALIDATE)
                            .header(SecurityConstants.INTERNAL_API_KEY_HEADER, INTERNAL_KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(Map.of("token", token))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.valid").value(false));
        }

        @Test
        @DisplayName("rejects a request without a token")
        void shouldValidateRequest() throws Exception {
            mockMvc.perform(post(VALIDATE)
                            .header(SecurityConstants.INTERNAL_API_KEY_HEADER, INTERNAL_KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"token\":\"\"}"))
                    .andExpect(status().isBadRequest());
        }
    }
}
