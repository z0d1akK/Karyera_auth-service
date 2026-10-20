package com.zodiakk.authservice.user.service;

import com.zodiakk.authservice.auth.repository.AuthCredentialRepository;
import com.zodiakk.authservice.auth.repository.RefreshTokenRepository;
import com.zodiakk.authservice.integration.employee.dto.EmployeeAccountUnlinkedEvent;
import com.zodiakk.authservice.integration.employee.repository.ProcessedEventRepository;
import com.zodiakk.authservice.user.entity.AccountDeletionStatus;
import com.zodiakk.authservice.user.entity.User;
import com.zodiakk.authservice.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserDeletionCompletionService {

    private final UserRepository userRepository;

    private final AuthCredentialRepository credentialRepository;

    private final RefreshTokenRepository refreshTokenRepository;

    private final ProcessedEventRepository processedEventRepository;

    @Transactional
    public void completeDeletion(
            UUID eventId,
            EmployeeAccountUnlinkedEvent event
    ) {
        Objects.requireNonNull(eventId, "eventId is required");
        Objects.requireNonNull(event, "event is required");
        Objects.requireNonNull(event.userId(), "userId is required");
        Objects.requireNonNull(event.employeeId(), "employeeId is required");

        User user = userRepository.findByIdForUpdate(event.userId())
                .orElseThrow(() -> new IllegalStateException(
                        "User not found: " + event.userId()
                ));

        if (!user.getEmployeeId().equals(event.employeeId())) {
            throw new IllegalStateException(
                    "Employee ID mismatch for user " + event.userId()
            );
        }

        int registered = processedEventRepository.registerIfAbsent(eventId);

        if (registered == 0) {
            return;
        }

        if (user.getDeletionStatus() == AccountDeletionStatus.DELETED) {
            return;
        }

        if (user.getDeletionStatus() != AccountDeletionStatus.DELETING) {
            throw new IllegalStateException(
                    "User is not in DELETING state: " + user.getId()
            );
        }

        refreshTokenRepository.deleteAllByUserId(user.getId());
        credentialRepository.deleteByUserId(user.getId());

        user.setActive(false);
        user.setDeletionStatus(AccountDeletionStatus.DELETED);

        userRepository.flush();
    }
}