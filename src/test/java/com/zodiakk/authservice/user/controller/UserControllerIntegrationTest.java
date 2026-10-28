package com.zodiakk.authservice.user.controller;

import com.zodiakk.authservice.auth.entity.AuthCredential;
import com.zodiakk.authservice.auth.entity.Role;
import com.zodiakk.authservice.common.AbstractIntegrationTest;
import com.zodiakk.authservice.integration.employee.dto.EmployeeAccountInfoDto;
import com.zodiakk.authservice.integration.employee.dto.EmploymentStatus;
import com.zodiakk.authservice.integration.employee.exception.EmployeeNotFoundException;
import com.zodiakk.authservice.integration.employee.exception.EmployeeServiceUnavailableException;
import com.zodiakk.authservice.outbox.entity.OutboxEvent;
import com.zodiakk.authservice.support.AuthTestDataFactory;
import com.zodiakk.authservice.user.entity.AccountDeletionStatus;
import com.zodiakk.authservice.user.entity.User;
import feign.FeignException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("User administration API")
class UserControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String USERS = "/api/v1/users";

    @Nested
    @DisplayName("POST /api/v1/users")
    class Create {

        @Test
        @DisplayName("HR creates an EMPLOYEE account and records an outbox event")
        void shouldCreateEmployeeAsHr() throws Exception {
            User hr = persistUser("hr@company.com", Role.HR, AuthTestDataFactory.RAW_PASSWORD, false);
            stubEmployee(AuthTestDataFactory.EMAIL, EmploymentStatus.ACTIVE, null);

            mockMvc.perform(post(USERS)
                            .header("Authorization", bearer(hr))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(AuthTestDataFactory.createUserRequest(Role.EMPLOYEE))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.employeeId").value(AuthTestDataFactory.EMPLOYEE_ID.toString()))
                    .andExpect(jsonPath("$.email").value(AuthTestDataFactory.EMAIL))
                    .andExpect(jsonPath("$.role").value("EMPLOYEE"))
                    .andExpect(jsonPath("$.active").value(true))
                    .andExpect(jsonPath("$.passwordChangeRequired").value(true));

            User created = userRepository.findByEmployeeIdAndDeletionStatusNot(
                    AuthTestDataFactory.EMPLOYEE_ID, AccountDeletionStatus.DELETED).orElseThrow();

            AuthCredential credential = credentialOf(created);
            assertThat(credential.getLogin()).isEqualTo(AuthTestDataFactory.EMAIL);
            assertThat(credential.getRole()).isEqualTo(Role.EMPLOYEE);

            assertThat(outboxEventRepository.findAll())
                    .extracting(OutboxEvent::getEventType)
                    .containsExactly("USER_CREATED");
            assertThat(outboxEventRepository.findAll().getFirst().getAggregateId())
                    .isEqualTo(AuthTestDataFactory.EMPLOYEE_ID);
        }

        @Test
        @DisplayName("ADMIN creates an ADMIN account")
        void shouldCreateAdmin() throws Exception {
            User admin = persistUser("admin@company.com", Role.ADMIN, AuthTestDataFactory.RAW_PASSWORD, false);
            stubEmployee(AuthTestDataFactory.EMAIL, EmploymentStatus.ACTIVE, null);

            mockMvc.perform(post(USERS)
                            .header("Authorization", bearer(admin))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(AuthTestDataFactory.createUserRequest(Role.ADMIN))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.role").value("ADMIN"));
        }

        @Test
        @DisplayName("allows an employee on leave")
        void shouldAllowEmployeeOnLeave() throws Exception {
            User hr = persistUser("hr@company.com", Role.HR, AuthTestDataFactory.RAW_PASSWORD, false);
            stubEmployee(AuthTestDataFactory.EMAIL, EmploymentStatus.ON_LEAVE, null);

            mockMvc.perform(post(USERS)
                            .header("Authorization", bearer(hr))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(AuthTestDataFactory.createUserRequest(Role.EMPLOYEE))))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("rejects an employee whose status forbids account creation")
        void shouldRejectIneligibleEmployee() throws Exception {
            User hr = persistUser("hr@company.com", Role.HR, AuthTestDataFactory.RAW_PASSWORD, false);
            stubEmployee(AuthTestDataFactory.EMAIL, EmploymentStatus.SUSPENDED, null);

            mockMvc.perform(post(USERS)
                            .header("Authorization", bearer(hr))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(AuthTestDataFactory.createUserRequest(Role.EMPLOYEE))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("BUSINESS_ERROR"));

            assertThat(userRepository.findByEmployeeIdAndDeletionStatusNot(
                    AuthTestDataFactory.EMPLOYEE_ID, AccountDeletionStatus.DELETED)).isEmpty();
        }

        @Test
        @DisplayName("rejects an employee without a corporate email")
        void shouldRejectEmployeeWithoutEmail() throws Exception {
            User hr = persistUser("hr@company.com", Role.HR, AuthTestDataFactory.RAW_PASSWORD, false);
            stubEmployee("   ", EmploymentStatus.ACTIVE, null);

            mockMvc.perform(post(USERS)
                            .header("Authorization", bearer(hr))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(AuthTestDataFactory.createUserRequest(Role.EMPLOYEE))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("rejects an employee that is already linked")
        void shouldRejectLinkedEmployee() throws Exception {
            User hr = persistUser("hr@company.com", Role.HR, AuthTestDataFactory.RAW_PASSWORD, false);
            stubEmployee(AuthTestDataFactory.EMAIL, EmploymentStatus.ACTIVE, UUID.randomUUID());

            mockMvc.perform(post(USERS)
                            .header("Authorization", bearer(hr))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(AuthTestDataFactory.createUserRequest(Role.EMPLOYEE))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("USER_ALREADY_EXISTS"));
        }

        @Test
        @DisplayName("rejects an employee that already has an account")
        void shouldRejectDuplicateEmployee() throws Exception {
            User hr = persistUser("hr@company.com", Role.HR, AuthTestDataFactory.RAW_PASSWORD, false);
            persistUser(AuthTestDataFactory.EMAIL, Role.EMPLOYEE, AuthTestDataFactory.RAW_PASSWORD, false,
                    AuthTestDataFactory.EMPLOYEE_ID, true, AccountDeletionStatus.NONE);
            stubEmployee(AuthTestDataFactory.EMAIL, EmploymentStatus.ACTIVE, null);

            mockMvc.perform(post(USERS)
                            .header("Authorization", bearer(hr))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(AuthTestDataFactory.createUserRequest(Role.MANAGER))))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("rejects an email that is already used")
        void shouldRejectDuplicateEmail() throws Exception {
            User hr = persistUser("hr@company.com", Role.HR, AuthTestDataFactory.RAW_PASSWORD, false);
            persistUser(AuthTestDataFactory.EMAIL, Role.EMPLOYEE, AuthTestDataFactory.RAW_PASSWORD, false,
                    AuthTestDataFactory.OTHER_EMPLOYEE_ID, true, AccountDeletionStatus.NONE);
            stubEmployee(AuthTestDataFactory.EMAIL, EmploymentStatus.ACTIVE, null);

            mockMvc.perform(post(USERS)
                            .header("Authorization", bearer(hr))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(AuthTestDataFactory.createUserRequest(Role.EMPLOYEE))))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("HR cannot create an administrator")
        void shouldRejectAdminCreationByHr() throws Exception {
            User hr = persistUser("hr@company.com", Role.HR, AuthTestDataFactory.RAW_PASSWORD, false);

            mockMvc.perform(post(USERS)
                            .header("Authorization", bearer(hr))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(AuthTestDataFactory.createUserRequest(Role.ADMIN))))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }

        @Test
        @DisplayName("employees cannot create accounts")
        void shouldRejectEmployeeCreation() throws Exception {
            User employee = persistUser(AuthTestDataFactory.EMAIL, Role.EMPLOYEE,
                    AuthTestDataFactory.RAW_PASSWORD, false);

            mockMvc.perform(post(USERS)
                            .header("Authorization", bearer(employee))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(AuthTestDataFactory.createUserRequest(Role.EMPLOYEE))))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("requires authentication")
        void shouldRequireAuthentication() throws Exception {
            mockMvc.perform(post(USERS)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(AuthTestDataFactory.createUserRequest(Role.EMPLOYEE))))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("validates the request body")
        void shouldValidateRequestBody() throws Exception {
            User hr = persistUser("hr@company.com", Role.HR, AuthTestDataFactory.RAW_PASSWORD, false);

            mockMvc.perform(post(USERS)
                            .header("Authorization", bearer(hr))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(Map.of("initialPassword", "short"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors").isNotEmpty());
        }

        @Test
        @DisplayName("returns 404 when the employee does not exist")
        void shouldReturnNotFoundForMissingEmployee() throws Exception {
            User hr = persistUser("hr@company.com", Role.HR, AuthTestDataFactory.RAW_PASSWORD, false);
            when(employeeClient.getAccountInfo(AuthTestDataFactory.EMPLOYEE_ID))
                    .thenThrow(new EmployeeNotFoundException(AuthTestDataFactory.EMPLOYEE_ID));

            mockMvc.perform(post(USERS)
                            .header("Authorization", bearer(hr))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(AuthTestDataFactory.createUserRequest(Role.EMPLOYEE))))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("returns 503 when the employee service is unavailable")
        void shouldReturnServiceUnavailable() throws Exception {
            User hr = persistUser("hr@company.com", Role.HR, AuthTestDataFactory.RAW_PASSWORD, false);
            when(employeeClient.getAccountInfo(AuthTestDataFactory.EMPLOYEE_ID))
                    .thenThrow(new EmployeeServiceUnavailableException());

            mockMvc.perform(post(USERS)
                            .header("Authorization", bearer(hr))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(AuthTestDataFactory.createUserRequest(Role.EMPLOYEE))))
                    .andExpect(status().isServiceUnavailable());
        }

        @Test
        @DisplayName("returns 502 when the employee service rejects the request")
        void shouldReturnBadGateway() throws Exception {
            User hr = persistUser("hr@company.com", Role.HR, AuthTestDataFactory.RAW_PASSWORD, false);
            FeignException badRequest = mock(FeignException.BadRequest.class);
            when(badRequest.status()).thenReturn(400);
            when(employeeClient.getAccountInfo(AuthTestDataFactory.EMPLOYEE_ID)).thenThrow(badRequest);

            mockMvc.perform(post(USERS)
                            .header("Authorization", bearer(hr))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(AuthTestDataFactory.createUserRequest(Role.EMPLOYEE))))
                    .andExpect(status().isBadGateway());
        }
    }

    @Nested
    @DisplayName("read endpoints")
    class Read {

        @Test
        @DisplayName("returns an account by id")
        void shouldReturnById() throws Exception {
            User admin = persistUser("admin@company.com", Role.ADMIN, AuthTestDataFactory.RAW_PASSWORD, false);
            User user = persistUser(AuthTestDataFactory.EMAIL, Role.EMPLOYEE,
                    AuthTestDataFactory.RAW_PASSWORD, false);

            mockMvc.perform(get(USERS + "/" + user.getId())
                            .header("Authorization", bearer(admin)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(user.getId().toString()))
                    .andExpect(jsonPath("$.email").value(AuthTestDataFactory.EMAIL));
        }

        @Test
        @DisplayName("returns 404 for an unknown account")
        void shouldReturnNotFoundById() throws Exception {
            User admin = persistUser("admin@company.com", Role.ADMIN, AuthTestDataFactory.RAW_PASSWORD, false);

            mockMvc.perform(get(USERS + "/" + UUID.randomUUID())
                            .header("Authorization", bearer(admin)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("does not expose deleted accounts")
        void shouldHideDeletedAccounts() throws Exception {
            User admin = persistUser("admin@company.com", Role.ADMIN, AuthTestDataFactory.RAW_PASSWORD, false);
            User deleted = persistUser(AuthTestDataFactory.EMAIL, Role.EMPLOYEE,
                    AuthTestDataFactory.RAW_PASSWORD, false,
                    AuthTestDataFactory.EMPLOYEE_ID, false, AccountDeletionStatus.DELETED);

            mockMvc.perform(get(USERS + "/" + deleted.getId())
                            .header("Authorization", bearer(admin)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("returns a paginated list")
        void shouldReturnPage() throws Exception {
            User admin = persistUser("admin@company.com", Role.ADMIN, AuthTestDataFactory.RAW_PASSWORD, false);
            persistUser(AuthTestDataFactory.EMAIL, Role.EMPLOYEE, AuthTestDataFactory.RAW_PASSWORD, false);

            mockMvc.perform(get(USERS)
                            .param("page", "0")
                            .param("size", "10")
                            .header("Authorization", bearer(admin)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(2))
                    .andExpect(jsonPath("$.content").isNotEmpty());
        }

        @Test
        @DisplayName("returns an account by employee id")
        void shouldReturnByEmployeeId() throws Exception {
            User admin = persistUser("admin@company.com", Role.ADMIN, AuthTestDataFactory.RAW_PASSWORD, false);
            User user = persistUser(AuthTestDataFactory.EMAIL, Role.EMPLOYEE,
                    AuthTestDataFactory.RAW_PASSWORD, false);

            mockMvc.perform(get(USERS + "/employee/" + user.getEmployeeId())
                            .header("Authorization", bearer(admin)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(user.getId().toString()));
        }

        @Test
        @DisplayName("returns 404 for an unknown employee id")
        void shouldReturnNotFoundByEmployeeId() throws Exception {
            User admin = persistUser("admin@company.com", Role.ADMIN, AuthTestDataFactory.RAW_PASSWORD, false);

            mockMvc.perform(get(USERS + "/employee/" + UUID.randomUUID())
                            .header("Authorization", bearer(admin)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("requires an HR or ADMIN role")
        void shouldRequireHrOrAdmin() throws Exception {
            User employee = persistUser(AuthTestDataFactory.EMAIL, Role.EMPLOYEE,
                    AuthTestDataFactory.RAW_PASSWORD, false);

            mockMvc.perform(get(USERS)
                            .header("Authorization", bearer(employee)))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/users/{id}")
    class Update {

        @Test
        @DisplayName("changes the role and revokes refresh tokens")
        void shouldUpdateRole() throws Exception {
            User admin = persistUser("admin@company.com", Role.ADMIN, AuthTestDataFactory.RAW_PASSWORD, false);
            User target = persistUser(AuthTestDataFactory.EMAIL, Role.EMPLOYEE,
                    AuthTestDataFactory.RAW_PASSWORD, false);

            mockMvc.perform(put(USERS + "/" + target.getId())
                            .header("Authorization", bearer(admin))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(AuthTestDataFactory.updateUserRequest(Role.MANAGER, true))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.role").value("MANAGER"));

            assertThat(credentialOf(target).getRole()).isEqualTo(Role.MANAGER);
        }

        @Test
        @DisplayName("deactivates an account through the update endpoint")
        void shouldDeactivateThroughUpdate() throws Exception {
            User admin = persistUser("admin@company.com", Role.ADMIN, AuthTestDataFactory.RAW_PASSWORD, false);
            User target = persistUser(AuthTestDataFactory.EMAIL, Role.EMPLOYEE,
                    AuthTestDataFactory.RAW_PASSWORD, false);

            mockMvc.perform(put(USERS + "/" + target.getId())
                            .header("Authorization", bearer(admin))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(AuthTestDataFactory.updateUserRequest(Role.EMPLOYEE, false))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.active").value(false));
        }

        @Test
        @DisplayName("HR cannot update accounts")
        void shouldRejectHrUpdate() throws Exception {
            User hr = persistUser("hr@company.com", Role.HR, AuthTestDataFactory.RAW_PASSWORD, false);
            User target = persistUser(AuthTestDataFactory.EMAIL, Role.EMPLOYEE,
                    AuthTestDataFactory.RAW_PASSWORD, false);

            mockMvc.perform(put(USERS + "/" + target.getId())
                            .header("Authorization", bearer(hr))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(AuthTestDataFactory.updateUserRequest(Role.MANAGER, true))))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("protects the last active administrator from demotion")
        void shouldProtectLastActiveAdmin() throws Exception {
            User admin = persistUser("admin@company.com", Role.ADMIN, AuthTestDataFactory.RAW_PASSWORD, false);

            mockMvc.perform(put(USERS + "/" + admin.getId())
                            .header("Authorization", bearer(admin))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(AuthTestDataFactory.updateUserRequest(Role.EMPLOYEE, true))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("LAST_ACTIVE_ADMIN"));
        }

        @Test
        @DisplayName("returns 404 for an unknown account")
        void shouldReturnNotFound() throws Exception {
            User admin = persistUser("admin@company.com", Role.ADMIN, AuthTestDataFactory.RAW_PASSWORD, false);

            mockMvc.perform(put(USERS + "/" + UUID.randomUUID())
                            .header("Authorization", bearer(admin))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(AuthTestDataFactory.updateUserRequest(Role.EMPLOYEE, true))))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("validates the request body")
        void shouldValidateRequestBody() throws Exception {
            User admin = persistUser("admin@company.com", Role.ADMIN, AuthTestDataFactory.RAW_PASSWORD, false);
            User target = persistUser(AuthTestDataFactory.EMAIL, Role.EMPLOYEE,
                    AuthTestDataFactory.RAW_PASSWORD, false);

            mockMvc.perform(put(USERS + "/" + target.getId())
                            .header("Authorization", bearer(admin))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("rejects updates of accounts that are being deleted")
        void shouldRejectDeletingAccount() throws Exception {
            User admin = persistUser("admin@company.com", Role.ADMIN, AuthTestDataFactory.RAW_PASSWORD, false);
            User target = persistUser(AuthTestDataFactory.EMAIL, Role.EMPLOYEE,
                    AuthTestDataFactory.RAW_PASSWORD, false,
                    AuthTestDataFactory.EMPLOYEE_ID, false, AccountDeletionStatus.DELETING);

            mockMvc.perform(put(USERS + "/" + target.getId())
                            .header("Authorization", bearer(admin))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(AuthTestDataFactory.updateUserRequest(Role.MANAGER, true))))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("PATCH activation endpoints")
    class Activation {

        @Test
        @DisplayName("activates an account")
        void shouldActivate() throws Exception {
            User admin = persistUser("admin@company.com", Role.ADMIN, AuthTestDataFactory.RAW_PASSWORD, false);
            User target = persistUser(AuthTestDataFactory.EMAIL, Role.EMPLOYEE,
                    AuthTestDataFactory.RAW_PASSWORD, false,
                    AuthTestDataFactory.EMPLOYEE_ID, false, AccountDeletionStatus.NONE);

            mockMvc.perform(patch(USERS + "/" + target.getId() + "/activate")
                            .header("Authorization", bearer(admin)))
                    .andExpect(status().isNoContent());

            assertThat(userRepository.findById(target.getId()).orElseThrow().getActive()).isTrue();
        }

        @Test
        @DisplayName("deactivates an account")
        void shouldDeactivate() throws Exception {
            User admin = persistUser("admin@company.com", Role.ADMIN, AuthTestDataFactory.RAW_PASSWORD, false);
            User target = persistUser(AuthTestDataFactory.EMAIL, Role.EMPLOYEE,
                    AuthTestDataFactory.RAW_PASSWORD, false);

            mockMvc.perform(patch(USERS + "/" + target.getId() + "/deactivate")
                            .header("Authorization", bearer(admin)))
                    .andExpect(status().isNoContent());

            assertThat(userRepository.findById(target.getId()).orElseThrow().getActive()).isFalse();
        }

        @Test
        @DisplayName("protects the last active administrator from deactivation")
        void shouldProtectLastActiveAdmin() throws Exception {
            User admin = persistUser("admin@company.com", Role.ADMIN, AuthTestDataFactory.RAW_PASSWORD, false);

            mockMvc.perform(patch(USERS + "/" + admin.getId() + "/deactivate")
                            .header("Authorization", bearer(admin)))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("returns 404 for an unknown account")
        void shouldReturnNotFound() throws Exception {
            User admin = persistUser("admin@company.com", Role.ADMIN, AuthTestDataFactory.RAW_PASSWORD, false);

            mockMvc.perform(patch(USERS + "/" + UUID.randomUUID() + "/activate")
                            .header("Authorization", bearer(admin)))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/users/{id}/reset-password")
    class ResetPassword {

        @Test
        @DisplayName("assigns a temporary password")
        void shouldResetPassword() throws Exception {
            User admin = persistUser("admin@company.com", Role.ADMIN, AuthTestDataFactory.RAW_PASSWORD, false);
            User target = persistUser(AuthTestDataFactory.EMAIL, Role.EMPLOYEE,
                    AuthTestDataFactory.RAW_PASSWORD, false);

            mockMvc.perform(post(USERS + "/" + target.getId() + "/reset-password")
                            .header("Authorization", bearer(admin))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(AuthTestDataFactory.resetPasswordRequest())))
                    .andExpect(status().isNoContent());

            AuthCredential credential = credentialOf(target);
            assertThat(credential.getPasswordChangeRequired()).isTrue();
            assertThat(passwordEncoder.matches(
                    AuthTestDataFactory.TEMPORARY_PASSWORD,
                    credential.getPasswordHash()
            )).isTrue();
        }

        @Test
        @DisplayName("rejects a temporary password equal to the current one")
        void shouldRejectSamePassword() throws Exception {
            User admin = persistUser("admin@company.com", Role.ADMIN, AuthTestDataFactory.RAW_PASSWORD, false);
            User target = persistUser(AuthTestDataFactory.EMAIL, Role.EMPLOYEE,
                    AuthTestDataFactory.RAW_PASSWORD, false);

            mockMvc.perform(post(USERS + "/" + target.getId() + "/reset-password")
                            .header("Authorization", bearer(admin))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(Map.of("temporaryPassword", AuthTestDataFactory.RAW_PASSWORD))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("validates the temporary password")
        void shouldValidatePassword() throws Exception {
            User admin = persistUser("admin@company.com", Role.ADMIN, AuthTestDataFactory.RAW_PASSWORD, false);
            User target = persistUser(AuthTestDataFactory.EMAIL, Role.EMPLOYEE,
                    AuthTestDataFactory.RAW_PASSWORD, false);

            mockMvc.perform(post(USERS + "/" + target.getId() + "/reset-password")
                            .header("Authorization", bearer(admin))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(Map.of("temporaryPassword", "short"))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("HR cannot reset passwords")
        void shouldRejectHr() throws Exception {
            User hr = persistUser("hr@company.com", Role.HR, AuthTestDataFactory.RAW_PASSWORD, false);
            User target = persistUser(AuthTestDataFactory.EMAIL, Role.EMPLOYEE,
                    AuthTestDataFactory.RAW_PASSWORD, false);

            mockMvc.perform(post(USERS + "/" + target.getId() + "/reset-password")
                            .header("Authorization", bearer(hr))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(AuthTestDataFactory.resetPasswordRequest())))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/users/{id}")
    class Delete {

        @Test
        @DisplayName("starts logical deletion and records an outbox event")
        void shouldStartDeletion() throws Exception {
            User admin = persistUser("admin@company.com", Role.ADMIN, AuthTestDataFactory.RAW_PASSWORD, false);
            User target = persistUser(AuthTestDataFactory.EMAIL, Role.EMPLOYEE,
                    AuthTestDataFactory.RAW_PASSWORD, false);

            mockMvc.perform(delete(USERS + "/" + target.getId())
                            .header("Authorization", bearer(admin)))
                    .andExpect(status().isAccepted());

            User deleted = userRepository.findById(target.getId()).orElseThrow();
            assertThat(deleted.getDeletionStatus()).isEqualTo(AccountDeletionStatus.DELETING);
            assertThat(deleted.getActive()).isFalse();

            assertThat(outboxEventRepository.findAll())
                    .extracting(OutboxEvent::getEventType)
                    .containsExactly("USER_DELETED");
        }

        @Test
        @DisplayName("is idempotent for an account that is already being deleted")
        void shouldBeIdempotent() throws Exception {
            User admin = persistUser("admin@company.com", Role.ADMIN, AuthTestDataFactory.RAW_PASSWORD, false);
            User target = persistUser(AuthTestDataFactory.EMAIL, Role.EMPLOYEE,
                    AuthTestDataFactory.RAW_PASSWORD, false,
                    AuthTestDataFactory.EMPLOYEE_ID, false, AccountDeletionStatus.DELETING);

            mockMvc.perform(delete(USERS + "/" + target.getId())
                            .header("Authorization", bearer(admin)))
                    .andExpect(status().isAccepted());

            assertThat(outboxEventRepository.findAll()).isEmpty();
        }

        @Test
        @DisplayName("returns 404 for an unknown account")
        void shouldReturnNotFound() throws Exception {
            User admin = persistUser("admin@company.com", Role.ADMIN, AuthTestDataFactory.RAW_PASSWORD, false);

            mockMvc.perform(delete(USERS + "/" + UUID.randomUUID())
                            .header("Authorization", bearer(admin)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("protects the last active administrator from deletion")
        void shouldProtectLastActiveAdmin() throws Exception {
            User admin = persistUser("admin@company.com", Role.ADMIN, AuthTestDataFactory.RAW_PASSWORD, false);

            mockMvc.perform(delete(USERS + "/" + admin.getId())
                            .header("Authorization", bearer(admin)))
                    .andExpect(status().isConflict());

            assertThat(userRepository.findById(admin.getId()).orElseThrow().getDeletionStatus())
                    .isEqualTo(AccountDeletionStatus.NONE);
        }

        @Test
        @DisplayName("HR cannot delete accounts")
        void shouldRejectHr() throws Exception {
            User hr = persistUser("hr@company.com", Role.HR, AuthTestDataFactory.RAW_PASSWORD, false);
            User target = persistUser(AuthTestDataFactory.EMAIL, Role.EMPLOYEE,
                    AuthTestDataFactory.RAW_PASSWORD, false);

            mockMvc.perform(delete(USERS + "/" + target.getId())
                            .header("Authorization", bearer(hr)))
                    .andExpect(status().isForbidden());
        }
    }

    private void stubEmployee(String email, EmploymentStatus status, UUID linkedUserId) {
        EmployeeAccountInfoDto employee = new EmployeeAccountInfoDto(
                AuthTestDataFactory.EMPLOYEE_ID,
                email,
                status,
                linkedUserId
        );
        when(employeeClient.getAccountInfo(AuthTestDataFactory.EMPLOYEE_ID)).thenReturn(employee);
    }
}
