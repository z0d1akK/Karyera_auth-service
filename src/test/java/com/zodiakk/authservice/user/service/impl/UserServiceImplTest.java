package com.zodiakk.authservice.user.service.impl;

import com.zodiakk.authservice.auth.entity.AuthCredential;
import com.zodiakk.authservice.auth.entity.Role;
import com.zodiakk.authservice.auth.exception.AuthCredentialNotFoundByUserIdException;
import com.zodiakk.authservice.auth.exception.PasswordMustDifferException;
import com.zodiakk.authservice.auth.repository.AuthCredentialRepository;
import com.zodiakk.authservice.auth.repository.RefreshTokenRepository;
import com.zodiakk.authservice.auth.security.jwt.principal.AuthenticatedUser;
import com.zodiakk.authservice.integration.employee.dto.EmployeeAccountInfoDto;
import com.zodiakk.authservice.integration.employee.dto.EmploymentStatus;
import com.zodiakk.authservice.integration.employee.service.EmployeeIntegrationService;
import com.zodiakk.authservice.support.AuthTestDataFactory;
import com.zodiakk.authservice.user.dto.CreateUserRequestDto;
import com.zodiakk.authservice.user.dto.ResetUserPasswordRequestDto;
import com.zodiakk.authservice.user.dto.UpdateUserRequestDto;
import com.zodiakk.authservice.user.dto.UserResponseDto;
import com.zodiakk.authservice.user.entity.AccountDeletionStatus;
import com.zodiakk.authservice.user.entity.User;
import com.zodiakk.authservice.user.event.UserEventService;
import com.zodiakk.authservice.user.exception.EmployeeAccountCreationException;
import com.zodiakk.authservice.user.exception.LastActiveAdminException;
import com.zodiakk.authservice.user.exception.UserAccountBeingDeletedException;
import com.zodiakk.authservice.user.exception.UserAlreadyExistsException;
import com.zodiakk.authservice.user.exception.UserNotFoundException;
import com.zodiakk.authservice.user.mapper.UserMapper;
import com.zodiakk.authservice.user.repository.AdministrationLockRepository;
import com.zodiakk.authservice.user.repository.UserRepository;
import com.zodiakk.authservice.user.security.UserAdministrationPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuthCredentialRepository authCredentialRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private EmployeeIntegrationService employeeIntegrationService;

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AdministrationLockRepository administrationLockRepository;

    @Mock
    private UserEventService userEventService;

    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        userService = new UserServiceImpl(
                userRepository,
                authCredentialRepository,
                refreshTokenRepository,
                employeeIntegrationService,
                new UserAdministrationPolicy(),
                userMapper,
                passwordEncoder,
                administrationLockRepository,
                userEventService
        );
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("ADMIN creates an ADMIN account and locks administration")
        void shouldCreateAdminAccount() {
            authenticate(Role.ADMIN);
            CreateUserRequestDto request = AuthTestDataFactory.createUserRequest(Role.ADMIN);
            EmployeeAccountInfoDto employee = AuthTestDataFactory.activeEmployee();

            when(employeeIntegrationService.getAccountInfo(AuthTestDataFactory.EMPLOYEE_ID)).thenReturn(employee);
            when(userRepository.existsByEmployeeIdAndDeletionStatusNot(
                    AuthTestDataFactory.EMPLOYEE_ID, AccountDeletionStatus.DELETED)).thenReturn(false);
            when(userRepository.existsByEmailIgnoreCaseAndDeletionStatusNot(
                    AuthTestDataFactory.EMAIL, AccountDeletionStatus.DELETED)).thenReturn(false);
            stubPersistence();
            when(passwordEncoder.encode(AuthTestDataFactory.RAW_PASSWORD)).thenReturn(AuthTestDataFactory.ENCODED_PASSWORD);
            when(userMapper.toResponseDto(any(User.class), any(AuthCredential.class)))
                    .thenReturn(AuthTestDataFactory.userResponse());

            UserResponseDto response = userService.create(request);

            assertThat(response).isNotNull();
            verify(administrationLockRepository).lock();
            verify(userEventService).recordUserCreated(AuthTestDataFactory.USER_ID, AuthTestDataFactory.EMPLOYEE_ID);

            ArgumentCaptor<AuthCredential> credentialCaptor = ArgumentCaptor.forClass(AuthCredential.class);
            verify(authCredentialRepository).saveAndFlush(credentialCaptor.capture());
            assertThat(credentialCaptor.getValue().getRole()).isEqualTo(Role.ADMIN);
            assertThat(credentialCaptor.getValue().getPasswordChangeRequired()).isTrue();
            assertThat(credentialCaptor.getValue().getLogin()).isEqualTo(AuthTestDataFactory.EMAIL);
        }

        @Test
        @DisplayName("HR creates an EMPLOYEE account without locking administration")
        void shouldCreateEmployeeAsHr() {
            authenticate(Role.HR);
            CreateUserRequestDto request = AuthTestDataFactory.createUserRequest(Role.EMPLOYEE);

            when(employeeIntegrationService.getAccountInfo(AuthTestDataFactory.EMPLOYEE_ID))
                    .thenReturn(AuthTestDataFactory.activeEmployee());
            when(userRepository.existsByEmployeeIdAndDeletionStatusNot(
                    AuthTestDataFactory.EMPLOYEE_ID, AccountDeletionStatus.DELETED)).thenReturn(false);
            when(userRepository.existsByEmailIgnoreCaseAndDeletionStatusNot(
                    AuthTestDataFactory.EMAIL, AccountDeletionStatus.DELETED)).thenReturn(false);
            stubPersistence();
            when(passwordEncoder.encode(AuthTestDataFactory.RAW_PASSWORD)).thenReturn(AuthTestDataFactory.ENCODED_PASSWORD);
            when(userMapper.toResponseDto(any(User.class), any(AuthCredential.class)))
                    .thenReturn(AuthTestDataFactory.userResponse());

            userService.create(request);

            verify(administrationLockRepository, never()).lock();
            verify(administrationLockRepository, never()).lock();
        }

        @Test
        @DisplayName("HR cannot create an ADMIN account")
        void shouldRejectAdminCreationByHr() {
            authenticate(Role.HR);

            assertThatThrownBy(() -> userService.create(AuthTestDataFactory.createUserRequest(Role.ADMIN)))
                    .isInstanceOf(AccessDeniedException.class);

            verifyNoInteractions(employeeIntegrationService);
        }

        @Test
        @DisplayName("Employee cannot create accounts")
        void shouldRejectCreationByEmployee() {
            authenticate(Role.EMPLOYEE);

            assertThatThrownBy(() -> userService.create(AuthTestDataFactory.createUserRequest(Role.EMPLOYEE)))
                    .isInstanceOf(AccessDeniedException.class);

            verifyNoInteractions(employeeIntegrationService);
        }

        @Test
        @DisplayName("Rejects employees whose status does not allow account creation")
        void shouldRejectIneligibleEmploymentStatus() {
            authenticate(Role.HR);
            EmployeeAccountInfoDto suspended = AuthTestDataFactory.employee(
                    AuthTestDataFactory.EMPLOYEE_ID,
                    AuthTestDataFactory.EMAIL,
                    EmploymentStatus.SUSPENDED,
                    null
            );

            when(employeeIntegrationService.getAccountInfo(AuthTestDataFactory.EMPLOYEE_ID)).thenReturn(suspended);

            assertThatThrownBy(() -> userService.create(AuthTestDataFactory.createUserRequest(Role.EMPLOYEE)))
                    .isInstanceOf(EmployeeAccountCreationException.class);

            verify(userRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("Allows employees on leave")
        void shouldAllowOnLeaveEmployee() {
            authenticate(Role.HR);
            EmployeeAccountInfoDto onLeave = AuthTestDataFactory.employee(
                    AuthTestDataFactory.EMPLOYEE_ID,
                    AuthTestDataFactory.EMAIL,
                    EmploymentStatus.ON_LEAVE,
                    null
            );

            when(employeeIntegrationService.getAccountInfo(AuthTestDataFactory.EMPLOYEE_ID)).thenReturn(onLeave);
            when(userRepository.existsByEmployeeIdAndDeletionStatusNot(
                    AuthTestDataFactory.EMPLOYEE_ID, AccountDeletionStatus.DELETED)).thenReturn(false);
            when(userRepository.existsByEmailIgnoreCaseAndDeletionStatusNot(
                    AuthTestDataFactory.EMAIL, AccountDeletionStatus.DELETED)).thenReturn(false);
            stubPersistence();
            when(passwordEncoder.encode(AuthTestDataFactory.RAW_PASSWORD)).thenReturn(AuthTestDataFactory.ENCODED_PASSWORD);
            when(userMapper.toResponseDto(any(User.class), any(AuthCredential.class)))
                    .thenReturn(AuthTestDataFactory.userResponse());

            assertThatCode(() -> userService.create(AuthTestDataFactory.createUserRequest(Role.EMPLOYEE)))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Rejects employee that is already linked to an account")
        void shouldRejectLinkedEmployee() {
            authenticate(Role.HR);
            EmployeeAccountInfoDto linked = AuthTestDataFactory.employee(
                    AuthTestDataFactory.EMPLOYEE_ID,
                    AuthTestDataFactory.EMAIL,
                    EmploymentStatus.ACTIVE,
                    UUID.randomUUID()
            );

            when(employeeIntegrationService.getAccountInfo(AuthTestDataFactory.EMPLOYEE_ID)).thenReturn(linked);

            assertThatThrownBy(() -> userService.create(AuthTestDataFactory.createUserRequest(Role.EMPLOYEE)))
                    .isInstanceOf(UserAlreadyExistsException.class);
        }

        @Test
        @DisplayName("Rejects employee that already has a non-deleted account")
        void shouldRejectDuplicateEmployee() {
            authenticate(Role.HR);

            when(employeeIntegrationService.getAccountInfo(AuthTestDataFactory.EMPLOYEE_ID))
                    .thenReturn(AuthTestDataFactory.activeEmployee());
            when(userRepository.existsByEmployeeIdAndDeletionStatusNot(
                    AuthTestDataFactory.EMPLOYEE_ID, AccountDeletionStatus.DELETED)).thenReturn(true);

            assertThatThrownBy(() -> userService.create(AuthTestDataFactory.createUserRequest(Role.EMPLOYEE)))
                    .isInstanceOf(UserAlreadyExistsException.class);
        }

        @Test
        @DisplayName("Rejects employee without corporate email")
        void shouldRejectMissingEmail() {
            authenticate(Role.HR);
            EmployeeAccountInfoDto withoutEmail = AuthTestDataFactory.employee(
                    AuthTestDataFactory.EMPLOYEE_ID,
                    "  ",
                    EmploymentStatus.ACTIVE,
                    null
            );

            when(employeeIntegrationService.getAccountInfo(AuthTestDataFactory.EMPLOYEE_ID)).thenReturn(withoutEmail);
            when(userRepository.existsByEmployeeIdAndDeletionStatusNot(
                    AuthTestDataFactory.EMPLOYEE_ID, AccountDeletionStatus.DELETED)).thenReturn(false);

            assertThatThrownBy(() -> userService.create(AuthTestDataFactory.createUserRequest(Role.EMPLOYEE)))
                    .isInstanceOf(EmployeeAccountCreationException.class);
        }

        @Test
        @DisplayName("Rejects email that is already used by another account")
        void shouldRejectDuplicateEmail() {
            authenticate(Role.HR);

            when(employeeIntegrationService.getAccountInfo(AuthTestDataFactory.EMPLOYEE_ID))
                    .thenReturn(AuthTestDataFactory.activeEmployee());
            when(userRepository.existsByEmployeeIdAndDeletionStatusNot(
                    AuthTestDataFactory.EMPLOYEE_ID, AccountDeletionStatus.DELETED)).thenReturn(false);
            when(userRepository.existsByEmailIgnoreCaseAndDeletionStatusNot(
                    AuthTestDataFactory.EMAIL, AccountDeletionStatus.DELETED)).thenReturn(true);

            assertThatThrownBy(() -> userService.create(AuthTestDataFactory.createUserRequest(Role.EMPLOYEE)))
                    .isInstanceOf(UserAlreadyExistsException.class);
        }

        @Test
        @DisplayName("Normalizes the employee email")
        void shouldNormalizeEmail() {
            authenticate(Role.HR);
            EmployeeAccountInfoDto mixedCaseEmail = AuthTestDataFactory.employee(
                    AuthTestDataFactory.EMPLOYEE_ID,
                    "  User@Company.COM ",
                    EmploymentStatus.ACTIVE,
                    null
            );

            when(employeeIntegrationService.getAccountInfo(AuthTestDataFactory.EMPLOYEE_ID)).thenReturn(mixedCaseEmail);
            when(userRepository.existsByEmployeeIdAndDeletionStatusNot(
                    AuthTestDataFactory.EMPLOYEE_ID, AccountDeletionStatus.DELETED)).thenReturn(false);
            when(userRepository.existsByEmailIgnoreCaseAndDeletionStatusNot(
                    AuthTestDataFactory.EMAIL, AccountDeletionStatus.DELETED)).thenReturn(false);
            stubPersistence();
            when(passwordEncoder.encode(AuthTestDataFactory.RAW_PASSWORD)).thenReturn(AuthTestDataFactory.ENCODED_PASSWORD);
            when(userMapper.toResponseDto(any(User.class), any(AuthCredential.class)))
                    .thenReturn(AuthTestDataFactory.userResponse());

            userService.create(AuthTestDataFactory.createUserRequest(Role.EMPLOYEE));

            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).saveAndFlush(userCaptor.capture());
            assertThat(userCaptor.getValue().getEmail()).isEqualTo(AuthTestDataFactory.EMAIL);
        }

        @Test
        @DisplayName("Translates database integrity violations into a conflict")
        void shouldTranslateDataIntegrityViolation() {
            authenticate(Role.HR);

            when(employeeIntegrationService.getAccountInfo(AuthTestDataFactory.EMPLOYEE_ID))
                    .thenReturn(AuthTestDataFactory.activeEmployee());
            when(userRepository.existsByEmployeeIdAndDeletionStatusNot(
                    AuthTestDataFactory.EMPLOYEE_ID, AccountDeletionStatus.DELETED)).thenReturn(false);
            when(userRepository.existsByEmailIgnoreCaseAndDeletionStatusNot(
                    AuthTestDataFactory.EMAIL, AccountDeletionStatus.DELETED)).thenReturn(false);
            when(userRepository.saveAndFlush(any(User.class)))
                    .thenThrow(new DataIntegrityViolationException("duplicate"));

            assertThatThrownBy(() -> userService.create(AuthTestDataFactory.createUserRequest(Role.EMPLOYEE)))
                    .isInstanceOf(UserAlreadyExistsException.class);

            verify(userEventService, never()).recordUserCreated(any(), any());
        }
    }

    @Nested
    @DisplayName("read operations")
    class ReadOperations {

        @Test
        @DisplayName("returns user by id")
        void shouldReturnUserById() {
            User user = AuthTestDataFactory.user();
            AuthCredential credential = AuthTestDataFactory.credential();

            when(userRepository.findByIdAndDeletionStatusNot(
                    AuthTestDataFactory.USER_ID, AccountDeletionStatus.DELETED)).thenReturn(Optional.of(user));
            when(authCredentialRepository.findByUserId(AuthTestDataFactory.USER_ID)).thenReturn(Optional.of(credential));
            when(userMapper.toResponseDto(user, credential)).thenReturn(AuthTestDataFactory.userResponse());

            assertThat(userService.getById(AuthTestDataFactory.USER_ID)).isNotNull();
        }

        @Test
        @DisplayName("fails when user is not found by id")
        void shouldFailWhenUserByIdIsMissing() {
            when(userRepository.findByIdAndDeletionStatusNot(
                    AuthTestDataFactory.USER_ID, AccountDeletionStatus.DELETED)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.getById(AuthTestDataFactory.USER_ID))
                    .isInstanceOf(UserNotFoundException.class);
        }

        @Test
        @DisplayName("fails when credentials are missing for an existing user")
        void shouldFailWhenCredentialIsMissing() {
            User user = AuthTestDataFactory.user();

            when(userRepository.findByIdAndDeletionStatusNot(
                    AuthTestDataFactory.USER_ID, AccountDeletionStatus.DELETED)).thenReturn(Optional.of(user));
            when(authCredentialRepository.findByUserId(AuthTestDataFactory.USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.getById(AuthTestDataFactory.USER_ID))
                    .isInstanceOf(AuthCredentialNotFoundByUserIdException.class);
        }

        @Test
        @DisplayName("returns user by employee id")
        void shouldReturnUserByEmployeeId() {
            User user = AuthTestDataFactory.user();
            AuthCredential credential = AuthTestDataFactory.credential();

            when(userRepository.findByEmployeeIdAndDeletionStatusNot(
                    AuthTestDataFactory.EMPLOYEE_ID, AccountDeletionStatus.DELETED)).thenReturn(Optional.of(user));
            when(authCredentialRepository.findByUserId(AuthTestDataFactory.USER_ID)).thenReturn(Optional.of(credential));
            when(userMapper.toResponseDto(user, credential)).thenReturn(AuthTestDataFactory.userResponse());

            assertThat(userService.getByEmployeeId(AuthTestDataFactory.EMPLOYEE_ID)).isNotNull();
        }

        @Test
        @DisplayName("fails when user is not found by employee id")
        void shouldFailWhenUserByEmployeeIdIsMissing() {
            when(userRepository.findByEmployeeIdAndDeletionStatusNot(
                    AuthTestDataFactory.EMPLOYEE_ID, AccountDeletionStatus.DELETED)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.getByEmployeeId(AuthTestDataFactory.EMPLOYEE_ID))
                    .isInstanceOf(UserNotFoundException.class);
        }

        @Test
        @DisplayName("returns a page of users with credentials")
        void shouldReturnUsersPage() {
            Pageable pageable = PageRequest.of(0, 10);
            User user = AuthTestDataFactory.user();
            AuthCredential credential = AuthTestDataFactory.credential();
            Page<User> users = new PageImpl<>(List.of(user), pageable, 1);

            when(userRepository.findAllByDeletionStatusNot(AccountDeletionStatus.DELETED, pageable)).thenReturn(users);
            when(authCredentialRepository.findAllByUserIdIn(List.of(AuthTestDataFactory.USER_ID)))
                    .thenReturn(List.of(credential));
            when(userMapper.toResponseDto(user, credential)).thenReturn(AuthTestDataFactory.userResponse());

            Page<UserResponseDto> result = userService.getAll(pageable);

            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().getFirst().getId()).isEqualTo(AuthTestDataFactory.USER_ID);
            assertThat(result.getContent().getFirst().getEmail()).isEqualTo(AuthTestDataFactory.EMAIL);
        }

        @Test
        @DisplayName("fails when a user in the page has no credentials")
        void shouldFailWhenPageContainsUserWithoutCredential() {
            Pageable pageable = PageRequest.of(0, 10);
            User user = AuthTestDataFactory.user();
            Page<User> users = new PageImpl<>(List.of(user), pageable, 1);

            when(userRepository.findAllByDeletionStatusNot(AccountDeletionStatus.DELETED, pageable)).thenReturn(users);
            when(authCredentialRepository.findAllByUserIdIn(List.of(AuthTestDataFactory.USER_ID)))
                    .thenReturn(List.of());

            assertThatThrownBy(() -> userService.getAll(pageable))
                    .isInstanceOf(AuthCredentialNotFoundByUserIdException.class);
        }

        @Test
        @DisplayName("returns an empty page")
        void shouldReturnEmptyPage() {
            Pageable pageable = PageRequest.of(0, 10);

            when(userRepository.findAllByDeletionStatusNot(AccountDeletionStatus.DELETED, pageable))
                    .thenReturn(Page.empty(pageable));
            when(authCredentialRepository.findAllByUserIdIn(List.of())).thenReturn(List.of());

            assertThat(userService.getAll(pageable).getContent()).isEmpty();
        }
    }

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        @DisplayName("changes the role and revokes sessions")
        void shouldChangeRole() {
            authenticate(Role.ADMIN);
            User user = AuthTestDataFactory.user();
            AuthCredential credential = AuthTestDataFactory.credential();
            UpdateUserRequestDto request = AuthTestDataFactory.updateUserRequest(Role.MANAGER, true);

            when(userRepository.findByIdAndDeletionStatusNot(
                    AuthTestDataFactory.USER_ID, AccountDeletionStatus.DELETED)).thenReturn(Optional.of(user));
            when(authCredentialRepository.findByUserId(AuthTestDataFactory.USER_ID)).thenReturn(Optional.of(credential));
            when(userMapper.toResponseDto(user, credential)).thenReturn(AuthTestDataFactory.userResponse());

            userService.update(AuthTestDataFactory.USER_ID, request);

            assertThat(credential.getRole()).isEqualTo(Role.MANAGER);
            verify(administrationLockRepository).lock();
            verify(refreshTokenRepository).revokeAllByUserId(AuthTestDataFactory.USER_ID);
        }

        @Test
        @DisplayName("does nothing when role and activity are unchanged")
        void shouldSkipUnchangedUpdate() {
            authenticate(Role.ADMIN);
            User user = AuthTestDataFactory.user();
            AuthCredential credential = AuthTestDataFactory.credential();
            UpdateUserRequestDto request = AuthTestDataFactory.updateUserRequest(Role.EMPLOYEE, true);

            when(userRepository.findByIdAndDeletionStatusNot(
                    AuthTestDataFactory.USER_ID, AccountDeletionStatus.DELETED)).thenReturn(Optional.of(user));
            when(authCredentialRepository.findByUserId(AuthTestDataFactory.USER_ID)).thenReturn(Optional.of(credential));
            when(userMapper.toResponseDto(user, credential)).thenReturn(AuthTestDataFactory.userResponse());

            userService.update(AuthTestDataFactory.USER_ID, request);

            verify(refreshTokenRepository, never()).revokeAllByUserId(any());
        }

        @Test
        @DisplayName("rejects operator without ADMIN role")
        void shouldRejectNonAdminOperator() {
            authenticate(Role.HR);

            assertThatThrownBy(() -> userService.update(
                    AuthTestDataFactory.USER_ID,
                    AuthTestDataFactory.updateUserRequest(Role.EMPLOYEE, true)
            )).isInstanceOf(AccessDeniedException.class);

            verifyNoInteractions(userRepository);
        }

        @Test
        @DisplayName("fails when user is not found")
        void shouldFailWhenUserIsMissing() {
            authenticate(Role.ADMIN);

            when(userRepository.findByIdAndDeletionStatusNot(
                    AuthTestDataFactory.USER_ID, AccountDeletionStatus.DELETED)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.update(
                    AuthTestDataFactory.USER_ID,
                    AuthTestDataFactory.updateUserRequest(Role.EMPLOYEE, true)
            )).isInstanceOf(UserNotFoundException.class);
        }

        @Test
        @DisplayName("rejects update of an account that is being deleted")
        void shouldRejectUserBeingDeleted() {
            authenticate(Role.ADMIN);
            User user = AuthTestDataFactory.user(
                    AuthTestDataFactory.USER_ID,
                    AuthTestDataFactory.EMPLOYEE_ID,
                    AuthTestDataFactory.EMAIL,
                    true,
                    AccountDeletionStatus.DELETING
            );

            when(userRepository.findByIdAndDeletionStatusNot(
                    AuthTestDataFactory.USER_ID, AccountDeletionStatus.DELETED)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> userService.update(
                    AuthTestDataFactory.USER_ID,
                    AuthTestDataFactory.updateUserRequest(Role.EMPLOYEE, true)
            )).isInstanceOf(UserAccountBeingDeletedException.class);
        }

        @Test
        @DisplayName("protects the last active administrator from demotion")
        void shouldProtectLastActiveAdmin() {
            authenticate(Role.ADMIN);
            User user = AuthTestDataFactory.user();
            AuthCredential credential = AuthTestDataFactory.credential(
                    AuthTestDataFactory.USER_ID,
                    AuthTestDataFactory.EMAIL,
                    Role.ADMIN,
                    false
            );

            when(userRepository.findByIdAndDeletionStatusNot(
                    AuthTestDataFactory.USER_ID, AccountDeletionStatus.DELETED)).thenReturn(Optional.of(user));
            when(authCredentialRepository.findByUserId(AuthTestDataFactory.USER_ID)).thenReturn(Optional.of(credential));
            when(authCredentialRepository.countActiveUsersByRole(Role.ADMIN)).thenReturn(1L);

            assertThatThrownBy(() -> userService.update(
                    AuthTestDataFactory.USER_ID,
                    AuthTestDataFactory.updateUserRequest(Role.EMPLOYEE, true)
            )).isInstanceOf(LastActiveAdminException.class);

            verify(refreshTokenRepository, never()).revokeAllByUserId(any());
        }

        @Test
        @DisplayName("allows demotion when another active administrator exists")
        void shouldAllowAdminDemotion() {
            authenticate(Role.ADMIN);
            User user = AuthTestDataFactory.user();
            AuthCredential credential = AuthTestDataFactory.credential(
                    AuthTestDataFactory.USER_ID,
                    AuthTestDataFactory.EMAIL,
                    Role.ADMIN,
                    false
            );

            when(userRepository.findByIdAndDeletionStatusNot(
                    AuthTestDataFactory.USER_ID, AccountDeletionStatus.DELETED)).thenReturn(Optional.of(user));
            when(authCredentialRepository.findByUserId(AuthTestDataFactory.USER_ID)).thenReturn(Optional.of(credential));
            when(authCredentialRepository.countActiveUsersByRole(Role.ADMIN)).thenReturn(2L);
            when(userMapper.toResponseDto(user, credential)).thenReturn(AuthTestDataFactory.userResponse());

            userService.update(AuthTestDataFactory.USER_ID, AuthTestDataFactory.updateUserRequest(Role.EMPLOYEE, true));

            assertThat(credential.getRole()).isEqualTo(Role.EMPLOYEE);
            verify(refreshTokenRepository).revokeAllByUserId(AuthTestDataFactory.USER_ID);
        }

        @Test
        @DisplayName("deactivates the account when active flag is cleared")
        void shouldDeactivateThroughUpdate() {
            authenticate(Role.ADMIN);
            User user = AuthTestDataFactory.user();
            AuthCredential credential = AuthTestDataFactory.credential();

            when(userRepository.findByIdAndDeletionStatusNot(
                    AuthTestDataFactory.USER_ID, AccountDeletionStatus.DELETED)).thenReturn(Optional.of(user));
            when(authCredentialRepository.findByUserId(AuthTestDataFactory.USER_ID)).thenReturn(Optional.of(credential));
            when(userMapper.toResponseDto(user, credential)).thenReturn(AuthTestDataFactory.userResponse());

            userService.update(AuthTestDataFactory.USER_ID, AuthTestDataFactory.updateUserRequest(Role.EMPLOYEE, false));

            assertThat(user.getActive()).isFalse();
            verify(refreshTokenRepository).revokeAllByUserId(AuthTestDataFactory.USER_ID);
        }
    }

    @Nested
    @DisplayName("resetPassword")
    class ResetPassword {

        @Test
        @DisplayName("sets a temporary password, requires a change and revokes sessions")
        void shouldResetPassword() {
            authenticate(Role.ADMIN);
            User user = AuthTestDataFactory.user();
            AuthCredential credential = AuthTestDataFactory.credential();
            ResetUserPasswordRequestDto request = AuthTestDataFactory.resetPasswordRequest();

            when(userRepository.findByIdAndDeletionStatusNot(
                    AuthTestDataFactory.USER_ID, AccountDeletionStatus.DELETED)).thenReturn(Optional.of(user));
            when(authCredentialRepository.findByUserIdForUpdate(AuthTestDataFactory.USER_ID))
                    .thenReturn(Optional.of(credential));
            when(passwordEncoder.matches(AuthTestDataFactory.TEMPORARY_PASSWORD, AuthTestDataFactory.ENCODED_PASSWORD))
                    .thenReturn(false);
            when(passwordEncoder.encode(AuthTestDataFactory.TEMPORARY_PASSWORD)).thenReturn("temporary-encoded");

            userService.resetPassword(AuthTestDataFactory.USER_ID, request);

            assertThat(credential.getPasswordHash()).isEqualTo("temporary-encoded");
            assertThat(credential.getPasswordChangeRequired()).isTrue();
            verify(refreshTokenRepository).revokeAllByUserId(AuthTestDataFactory.USER_ID);
        }

        @Test
        @DisplayName("rejects a temporary password equal to the current one")
        void shouldRejectSamePassword() {
            authenticate(Role.ADMIN);
            User user = AuthTestDataFactory.user();
            AuthCredential credential = AuthTestDataFactory.credential();

            when(userRepository.findByIdAndDeletionStatusNot(
                    AuthTestDataFactory.USER_ID, AccountDeletionStatus.DELETED)).thenReturn(Optional.of(user));
            when(authCredentialRepository.findByUserIdForUpdate(AuthTestDataFactory.USER_ID))
                    .thenReturn(Optional.of(credential));
            when(passwordEncoder.matches(AuthTestDataFactory.TEMPORARY_PASSWORD, AuthTestDataFactory.ENCODED_PASSWORD))
                    .thenReturn(true);

            assertThatThrownBy(() -> userService.resetPassword(
                    AuthTestDataFactory.USER_ID,
                    AuthTestDataFactory.resetPasswordRequest()
            )).isInstanceOf(PasswordMustDifferException.class);

            verify(refreshTokenRepository, never()).revokeAllByUserId(any());
        }

        @Test
        @DisplayName("fails when credentials are missing")
        void shouldFailWhenCredentialIsMissing() {
            authenticate(Role.ADMIN);
            User user = AuthTestDataFactory.user();

            when(userRepository.findByIdAndDeletionStatusNot(
                    AuthTestDataFactory.USER_ID, AccountDeletionStatus.DELETED)).thenReturn(Optional.of(user));
            when(authCredentialRepository.findByUserIdForUpdate(AuthTestDataFactory.USER_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.resetPassword(
                    AuthTestDataFactory.USER_ID,
                    AuthTestDataFactory.resetPasswordRequest()
            )).isInstanceOf(AuthCredentialNotFoundByUserIdException.class);
        }

        @Test
        @DisplayName("rejects operator without ADMIN role")
        void shouldRejectNonAdminOperator() {
            authenticate(Role.MANAGER);

            assertThatThrownBy(() -> userService.resetPassword(
                    AuthTestDataFactory.USER_ID,
                    AuthTestDataFactory.resetPasswordRequest()
            )).isInstanceOf(AccessDeniedException.class);

            verifyNoInteractions(userRepository);
        }
    }

    @Nested
    @DisplayName("activate / deactivate")
    class Activation {

        @Test
        @DisplayName("activates an inactive account")
        void shouldActivateAccount() {
            authenticate(Role.ADMIN);
            User user = AuthTestDataFactory.user(
                    AuthTestDataFactory.USER_ID,
                    AuthTestDataFactory.EMPLOYEE_ID,
                    AuthTestDataFactory.EMAIL,
                    false,
                    AccountDeletionStatus.NONE
            );

            when(userRepository.findByIdAndDeletionStatusNot(
                    AuthTestDataFactory.USER_ID, AccountDeletionStatus.DELETED)).thenReturn(Optional.of(user));

            userService.activate(AuthTestDataFactory.USER_ID);

            assertThat(user.getActive()).isTrue();
        }

        @Test
        @DisplayName("keeps an already active account unchanged")
        void shouldSkipActivationOfActiveAccount() {
            authenticate(Role.ADMIN);
            User user = AuthTestDataFactory.user();

            when(userRepository.findByIdAndDeletionStatusNot(
                    AuthTestDataFactory.USER_ID, AccountDeletionStatus.DELETED)).thenReturn(Optional.of(user));

            userService.activate(AuthTestDataFactory.USER_ID);

            assertThat(user.getActive()).isTrue();
            verify(refreshTokenRepository, never()).revokeAllByUserId(any());
        }

        @Test
        @DisplayName("deactivates an active account and revokes sessions")
        void shouldDeactivateAccount() {
            authenticate(Role.ADMIN);
            User user = AuthTestDataFactory.user();
            AuthCredential credential = AuthTestDataFactory.credential();

            when(userRepository.findByIdAndDeletionStatusNot(
                    AuthTestDataFactory.USER_ID, AccountDeletionStatus.DELETED)).thenReturn(Optional.of(user));
            when(authCredentialRepository.findByUserId(AuthTestDataFactory.USER_ID)).thenReturn(Optional.of(credential));

            userService.deactivate(AuthTestDataFactory.USER_ID);

            assertThat(user.getActive()).isFalse();
            verify(refreshTokenRepository).revokeAllByUserId(AuthTestDataFactory.USER_ID);
        }

        @Test
        @DisplayName("keeps an already inactive account unchanged")
        void shouldSkipDeactivationOfInactiveAccount() {
            authenticate(Role.ADMIN);
            User user = AuthTestDataFactory.user(
                    AuthTestDataFactory.USER_ID,
                    AuthTestDataFactory.EMPLOYEE_ID,
                    AuthTestDataFactory.EMAIL,
                    false,
                    AccountDeletionStatus.NONE
            );

            when(userRepository.findByIdAndDeletionStatusNot(
                    AuthTestDataFactory.USER_ID, AccountDeletionStatus.DELETED)).thenReturn(Optional.of(user));

            userService.deactivate(AuthTestDataFactory.USER_ID);

            assertThat(user.getActive()).isFalse();
            verifyNoInteractions(authCredentialRepository);
        }

        @Test
        @DisplayName("protects the last active administrator from deactivation")
        void shouldProtectLastActiveAdminFromDeactivation() {
            authenticate(Role.ADMIN);
            User user = AuthTestDataFactory.user();
            AuthCredential credential = AuthTestDataFactory.credential(
                    AuthTestDataFactory.USER_ID,
                    AuthTestDataFactory.EMAIL,
                    Role.ADMIN,
                    false
            );

            when(userRepository.findByIdAndDeletionStatusNot(
                    AuthTestDataFactory.USER_ID, AccountDeletionStatus.DELETED)).thenReturn(Optional.of(user));
            when(authCredentialRepository.findByUserId(AuthTestDataFactory.USER_ID)).thenReturn(Optional.of(credential));
            when(authCredentialRepository.countActiveUsersByRole(Role.ADMIN)).thenReturn(1L);

            assertThatThrownBy(() -> userService.deactivate(AuthTestDataFactory.USER_ID))
                    .isInstanceOf(LastActiveAdminException.class);

            assertThat(user.getActive()).isTrue();
        }

        @Test
        @DisplayName("deactivates an administrator when another active administrator exists")
        void shouldDeactivateAdmin() {
            authenticate(Role.ADMIN);
            User user = AuthTestDataFactory.user();
            AuthCredential credential = AuthTestDataFactory.credential(
                    AuthTestDataFactory.USER_ID,
                    AuthTestDataFactory.EMAIL,
                    Role.ADMIN,
                    false
            );

            when(userRepository.findByIdAndDeletionStatusNot(
                    AuthTestDataFactory.USER_ID, AccountDeletionStatus.DELETED)).thenReturn(Optional.of(user));
            when(authCredentialRepository.findByUserId(AuthTestDataFactory.USER_ID)).thenReturn(Optional.of(credential));
            when(authCredentialRepository.countActiveUsersByRole(Role.ADMIN)).thenReturn(2L);

            userService.deactivate(AuthTestDataFactory.USER_ID);

            assertThat(user.getActive()).isFalse();
            verify(refreshTokenRepository).revokeAllByUserId(AuthTestDataFactory.USER_ID);
        }

        @Test
        @DisplayName("rejects activation of an account that is being deleted")
        void shouldRejectActivationOfDeletingAccount() {
            authenticate(Role.ADMIN);
            User user = AuthTestDataFactory.user(
                    AuthTestDataFactory.USER_ID,
                    AuthTestDataFactory.EMPLOYEE_ID,
                    AuthTestDataFactory.EMAIL,
                    false,
                    AccountDeletionStatus.DELETING
            );

            when(userRepository.findByIdAndDeletionStatusNot(
                    AuthTestDataFactory.USER_ID, AccountDeletionStatus.DELETED)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> userService.activate(AuthTestDataFactory.USER_ID))
                    .isInstanceOf(UserAccountBeingDeletedException.class);
        }
    }

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("starts logical deletion and records the event")
        void shouldStartDeletion() {
            authenticate(Role.ADMIN);
            User user = AuthTestDataFactory.user();
            AuthCredential credential = AuthTestDataFactory.credential();

            when(userRepository.findById(AuthTestDataFactory.USER_ID)).thenReturn(Optional.of(user));
            when(authCredentialRepository.findByUserId(AuthTestDataFactory.USER_ID)).thenReturn(Optional.of(credential));

            userService.delete(AuthTestDataFactory.USER_ID);

            assertThat(user.getDeletionStatus()).isEqualTo(AccountDeletionStatus.DELETING);
            assertThat(user.getActive()).isFalse();
            verify(refreshTokenRepository).revokeAllByUserId(AuthTestDataFactory.USER_ID);
            verify(userEventService).recordUserDeleted(
                    AuthTestDataFactory.USER_ID,
                    AuthTestDataFactory.EMPLOYEE_ID
            );
        }

        @Test
        @DisplayName("fails when user does not exist")
        void shouldFailWhenUserIsMissing() {
            authenticate(Role.ADMIN);

            when(userRepository.findById(AuthTestDataFactory.USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.delete(AuthTestDataFactory.USER_ID))
                    .isInstanceOf(UserNotFoundException.class);
        }

        @Test
        @DisplayName("is idempotent for accounts that are already being deleted")
        void shouldSkipAlreadyStartedDeletion() {
            authenticate(Role.ADMIN);
            User user = AuthTestDataFactory.user(
                    AuthTestDataFactory.USER_ID,
                    AuthTestDataFactory.EMPLOYEE_ID,
                    AuthTestDataFactory.EMAIL,
                    true,
                    AccountDeletionStatus.DELETING
            );

            when(userRepository.findById(AuthTestDataFactory.USER_ID)).thenReturn(Optional.of(user));

            userService.delete(AuthTestDataFactory.USER_ID);

            verifyNoInteractions(authCredentialRepository);
            verify(userEventService, never()).recordUserDeleted(any(), any());
        }

        @Test
        @DisplayName("skips already deleted accounts")
        void shouldSkipDeletedAccount() {
            authenticate(Role.ADMIN);
            User user = AuthTestDataFactory.user(
                    AuthTestDataFactory.USER_ID,
                    AuthTestDataFactory.EMPLOYEE_ID,
                    AuthTestDataFactory.EMAIL,
                    false,
                    AccountDeletionStatus.DELETED
            );

            when(userRepository.findById(AuthTestDataFactory.USER_ID)).thenReturn(Optional.of(user));

            userService.delete(AuthTestDataFactory.USER_ID);

            verifyNoInteractions(authCredentialRepository);
        }

        @Test
        @DisplayName("fails when credentials are missing")
        void shouldFailWhenCredentialIsMissing() {
            authenticate(Role.ADMIN);
            User user = AuthTestDataFactory.user();

            when(userRepository.findById(AuthTestDataFactory.USER_ID)).thenReturn(Optional.of(user));
            when(authCredentialRepository.findByUserId(AuthTestDataFactory.USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.delete(AuthTestDataFactory.USER_ID))
                    .isInstanceOf(AuthCredentialNotFoundByUserIdException.class);
        }

        @Test
        @DisplayName("protects the last active administrator from deletion")
        void shouldProtectLastActiveAdmin() {
            authenticate(Role.ADMIN);
            User user = AuthTestDataFactory.user();
            AuthCredential credential = AuthTestDataFactory.credential(
                    AuthTestDataFactory.USER_ID,
                    AuthTestDataFactory.EMAIL,
                    Role.ADMIN,
                    false
            );

            when(userRepository.findById(AuthTestDataFactory.USER_ID)).thenReturn(Optional.of(user));
            when(authCredentialRepository.findByUserId(AuthTestDataFactory.USER_ID)).thenReturn(Optional.of(credential));
            when(authCredentialRepository.countActiveUsersByRole(Role.ADMIN)).thenReturn(1L);

            assertThatThrownBy(() -> userService.delete(AuthTestDataFactory.USER_ID))
                    .isInstanceOf(LastActiveAdminException.class);

            assertThat(user.getDeletionStatus()).isEqualTo(AccountDeletionStatus.NONE);
            verify(userEventService, never()).recordUserDeleted(any(), any());
        }
    }

    private void stubPersistence() {
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(AuthTestDataFactory.USER_ID);
            return user;
        });
        when(authCredentialRepository.saveAndFlush(any(AuthCredential.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void authenticate(Role role) {
        AuthenticatedUser principal = AuthenticatedUser.builder()
                .userId(UUID.randomUUID())
                .email("operator@company.com")
                .role(role)
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of())
        );
    }
}
