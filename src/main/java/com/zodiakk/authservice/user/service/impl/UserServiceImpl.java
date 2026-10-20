package com.zodiakk.authservice.user.service.impl;

import com.zodiakk.authservice.auth.entity.AuthCredential;
import com.zodiakk.authservice.auth.entity.Role;
import com.zodiakk.authservice.auth.exception.AuthCredentialNotFoundByUserIdException;
import com.zodiakk.authservice.auth.exception.PasswordMustDifferException;
import com.zodiakk.authservice.auth.repository.AuthCredentialRepository;
import com.zodiakk.authservice.auth.repository.RefreshTokenRepository;
import com.zodiakk.authservice.auth.security.utils.SecurityContextUtils;
import com.zodiakk.authservice.integration.employee.dto.EmployeeAccountInfoDto;
import com.zodiakk.authservice.integration.employee.dto.EmploymentStatus;
import com.zodiakk.authservice.integration.employee.service.EmployeeIntegrationService;
import com.zodiakk.authservice.user.dto.CreateUserRequestDto;
import com.zodiakk.authservice.user.dto.ResetUserPasswordRequestDto;
import com.zodiakk.authservice.user.dto.UpdateUserRequestDto;
import com.zodiakk.authservice.user.dto.UserResponseDto;
import com.zodiakk.authservice.user.entity.AccountDeletionStatus;
import com.zodiakk.authservice.user.entity.User;
import com.zodiakk.authservice.user.event.UserEventService;
import com.zodiakk.authservice.user.exception.*;
import com.zodiakk.authservice.user.mapper.UserMapper;
import com.zodiakk.authservice.user.repository.AdministrationLockRepository;
import com.zodiakk.authservice.user.repository.UserRepository;
import com.zodiakk.authservice.user.security.UserAdministrationPolicy;
import com.zodiakk.authservice.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    private final AuthCredentialRepository authCredentialRepository;

    private final RefreshTokenRepository refreshTokenRepository;

    private final EmployeeIntegrationService employeeIntegrationService;

    private final UserAdministrationPolicy administrationPolicy;

    private final UserMapper userMapper;

    private final PasswordEncoder passwordEncoder;

    private final AdministrationLockRepository administrationLockRepository;

    private final UserEventService userEventService;

    @Override
    @Transactional
    public UserResponseDto create(CreateUserRequestDto request) {
        Role operatorRole = getCurrentOperatorRole();
        administrationPolicy.checkCreatePermission(operatorRole, request.getRole());

        EmployeeAccountInfoDto employee =
                employeeIntegrationService.getAccountInfo(request.getEmployeeId());

        if (employee.employmentStatus() != EmploymentStatus.ACTIVE
                && employee.employmentStatus() != EmploymentStatus.ON_LEAVE) {
            throw new EmployeeAccountCreationException("Employee employment status does not allow account creation");
        }

        if (employee.userId() != null || userRepository.existsByEmployeeIdAndDeletionStatusNot(employee.employeeId(), AccountDeletionStatus.DELETED)) {
            throw new UserAlreadyExistsException(employee.employeeId().toString());
        }

        String email = normalizeEmail(employee.email());

        if (email == null || email.isBlank()) {
            throw new EmployeeAccountCreationException("Employee corporate email is missing");
        }

        if (userRepository.existsByEmailIgnoreCaseAndDeletionStatusNot(email, AccountDeletionStatus.DELETED)) {
            throw new UserAlreadyExistsException(email);
        }

        if (request.getRole() == Role.ADMIN) {
            administrationLockRepository.lock();
        }

        User user = userRepository.save(
                User.builder()
                        .employeeId(employee.employeeId())
                        .email(email)
                        .active(true)
                        .deletionStatus(AccountDeletionStatus.NONE)
                        .build()
        );

        AuthCredential credential = authCredentialRepository.save(
                AuthCredential.builder()
                        .userId(user.getId())
                        .login(email)
                        .passwordHash(passwordEncoder.encode(request.getInitialPassword()))
                        .role(request.getRole())
                        .passwordChangeRequired(true)
                        .build()
        );

        userEventService.publishUserCreated(
                user.getId(),
                user.getEmployeeId()
        );

        return userMapper.toResponseDto(user, credential);
    }

    @Override
    public UserResponseDto getById(UUID id) {
        return toResponse(findUser(id));
    }

    @Override
    public UserResponseDto getByEmployeeId(UUID employeeId) {
        User user = userRepository.findByEmployeeIdAndDeletionStatusNot(employeeId, AccountDeletionStatus.DELETED)
                .orElseThrow(() -> new UserNotFoundException(employeeId));

        return toResponse(user);
    }

    @Override
    public UserResponseDto getByEmail(String email) {
        String normalized = normalizeEmail(email);

        User user = userRepository.findByEmailAndDeletionStatusNot(normalized, AccountDeletionStatus.DELETED)
                .orElseThrow(() -> new UserNotFoundByEmailException(email));

        return toResponse(user);
    }


    @Override
    public Page<UserResponseDto> getAll(Pageable pageable) {
        Page<User> usersPage = userRepository.findAllByDeletionStatusNot(
                AccountDeletionStatus.DELETED,
                pageable
        );

        if (usersPage.isEmpty()) {
            return new PageImpl<>(
                    List.of(),
                    pageable,
                    usersPage.getTotalElements()
            );
        }

        List<UUID> userIds = usersPage.getContent()
                .stream()
                .map(User::getId)
                .toList();

        Map<UUID, AuthCredential> credentialsByUserId =
                authCredentialRepository.findAllByUserIdIn(userIds)
                        .stream()
                        .collect(Collectors.toMap(AuthCredential::getUserId, Function.identity()));

        return usersPage.map(user -> {
            AuthCredential credential =
                    credentialsByUserId.get(user.getId());

            if (credential == null) {
                throw new AuthCredentialNotFoundByUserIdException(
                        user.getId()
                );
            }

            return userMapper.toResponseDto(user, credential);
        });
    }



    @Override
    @Transactional
    public UserResponseDto update(UUID id, UpdateUserRequestDto request) {
        administrationPolicy.checkUpdatePermission(getCurrentOperatorRole());

        administrationLockRepository.lock();

        User user = findUser(id);

        assertNotBeingDeleted(user);

        AuthCredential credential = findCredential(id);

        Role oldRole = credential.getRole();
        Role newRole = request.getRole();

        boolean wasActive = Boolean.TRUE.equals(user.getActive());
        boolean willBeActive = Boolean.TRUE.equals(request.getActive());

        boolean roleChanged = oldRole != newRole;
        boolean activeChanged = wasActive != willBeActive;

        if (!roleChanged && !activeChanged) {
            return userMapper.toResponseDto(user, credential);
        }

        boolean removesActiveAdmin =
                oldRole == Role.ADMIN
                        && wasActive
                        && (newRole != Role.ADMIN || !willBeActive);

        if (removesActiveAdmin) {
            assertNotLastActiveAdmin();
        }

        credential.setRole(newRole);
        user.setActive(willBeActive);

        refreshTokenRepository.revokeAllByUserId(id);

        return userMapper.toResponseDto(user, credential);
    }

    @Override
    @Transactional
    public void resetPassword(UUID id, ResetUserPasswordRequestDto request) {
        administrationPolicy.checkUpdatePermission(getCurrentOperatorRole());

        User user = findUser(id);

        assertNotBeingDeleted(user);

        AuthCredential credential = authCredentialRepository
                .findByUserIdForUpdate(user.getId())
                .orElseThrow(() -> new AuthCredentialNotFoundByUserIdException(user.getId()));

        if (passwordEncoder.matches(
                request.getTemporaryPassword(),
                credential.getPasswordHash()
        )) {
            throw new PasswordMustDifferException();
        }

        credential.setPasswordHash(passwordEncoder.encode(request.getTemporaryPassword()));
        credential.setPasswordChangeRequired(true);

        refreshTokenRepository.revokeAllByUserId(user.getId());
    }

    @Override
    @Transactional
    public void activate(UUID id) {
        administrationPolicy.checkUpdatePermission(getCurrentOperatorRole());

        administrationLockRepository.lock();

        User user = findUser(id);

        assertNotBeingDeleted(user);

        if (Boolean.TRUE.equals(user.getActive())) {
            return;
        }

        user.setActive(true);
    }

    @Override
    @Transactional
    public void deactivate(UUID id) {
        administrationPolicy.checkUpdatePermission(getCurrentOperatorRole());

        administrationLockRepository.lock();

        User user = findUser(id);

        assertNotBeingDeleted(user);

        if (!Boolean.TRUE.equals(user.getActive())) {
            return;
        }

        AuthCredential credential = findCredential(id);

        if (credential.getRole() == Role.ADMIN) {
            assertNotLastActiveAdmin();
        }

        user.setActive(false);

        refreshTokenRepository.revokeAllByUserId(id);
    }


    @Override
    @Transactional
    public void delete(UUID id) {
        administrationPolicy.checkUpdatePermission(getCurrentOperatorRole());

        administrationLockRepository.lock();

        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));

        if (user.getDeletionStatus() != AccountDeletionStatus.NONE) {
            return;
        }

        AuthCredential credential = authCredentialRepository
                .findByUserId(id)
                .orElseThrow(() -> new AuthCredentialNotFoundByUserIdException(id));

        if (Boolean.TRUE.equals(user.getActive())
                && credential.getRole() == Role.ADMIN) {
            assertNotLastActiveAdmin();
        }

        user.setActive(false);
        user.setDeletionStatus(AccountDeletionStatus.DELETING);

        refreshTokenRepository.revokeAllByUserId(id);

        userEventService.recordUserDeleted(
                user.getId(),
                user.getEmployeeId()
        );
    }


    private User findUser(UUID id) {
        return userRepository.findByIdAndDeletionStatusNot(id, AccountDeletionStatus.DELETED)
                .orElseThrow(() -> new UserNotFoundException(id));
    }

    private AuthCredential findCredential(UUID userId) {
        return authCredentialRepository.findByUserId(userId)
                .orElseThrow(() -> new AuthCredentialNotFoundByUserIdException(userId));
    }

    private UserResponseDto toResponse(User user) {
        return userMapper.toResponseDto(user, findCredential(user.getId()));
    }

    private Role getCurrentOperatorRole() {
        return SecurityContextUtils.getCurrentUserRole();
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    private void assertNotLastActiveAdmin() {
        long activeAdmins =
                authCredentialRepository.countActiveUsersByRole(Role.ADMIN);

        if (activeAdmins <= 1) {
            throw new LastActiveAdminException();
        }
    }

    private void assertNotBeingDeleted(User user) {
        if (user.getDeletionStatus() != AccountDeletionStatus.NONE) {
            throw new UserAccountBeingDeletedException(user.getId());
        }
    }
}