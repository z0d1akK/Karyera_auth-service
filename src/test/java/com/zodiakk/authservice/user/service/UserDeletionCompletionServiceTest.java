package com.zodiakk.authservice.user.service;

import com.zodiakk.authservice.auth.repository.AuthCredentialRepository;
import com.zodiakk.authservice.auth.repository.RefreshTokenRepository;
import com.zodiakk.authservice.integration.employee.dto.EmployeeAccountUnlinkedEvent;
import com.zodiakk.authservice.integration.employee.repository.ProcessedEventRepository;
import com.zodiakk.authservice.support.AuthTestDataFactory;
import com.zodiakk.authservice.user.entity.AccountDeletionStatus;
import com.zodiakk.authservice.user.entity.User;
import com.zodiakk.authservice.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserDeletionCompletionServiceTest {

    private static final UUID EVENT_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuthCredentialRepository credentialRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    private UserDeletionCompletionService service;

    @BeforeEach
    void setUp() {
        service = new UserDeletionCompletionService(
                userRepository,
                credentialRepository,
                refreshTokenRepository,
                processedEventRepository
        );
    }

    @Nested
    @DisplayName("completeDeletion")
    class CompleteDeletion {

        @Test
        @DisplayName("finishes deletion of a user in DELETING state")
        void shouldCompleteDeletion() {
            User user = deletingUser();
            EmployeeAccountUnlinkedEvent event = event();

            when(userRepository.findByIdForUpdate(AuthTestDataFactory.USER_ID)).thenReturn(Optional.of(user));
            when(processedEventRepository.registerIfAbsent(EVENT_ID)).thenReturn(1);

            service.completeDeletion(EVENT_ID, event);

            assertThat(user.getActive()).isFalse();
            assertThat(user.getDeletionStatus()).isEqualTo(AccountDeletionStatus.DELETED);
            verify(refreshTokenRepository).deleteAllByUserId(AuthTestDataFactory.USER_ID);
            verify(credentialRepository).deleteByUserId(AuthTestDataFactory.USER_ID);
            verify(userRepository).flush();
        }

        @Test
        @DisplayName("is idempotent when the event has already been processed")
        void shouldSkipAlreadyProcessedEvent() {
            User user = deletingUser();

            when(userRepository.findByIdForUpdate(AuthTestDataFactory.USER_ID)).thenReturn(Optional.of(user));
            when(processedEventRepository.registerIfAbsent(EVENT_ID)).thenReturn(0);

            service.completeDeletion(EVENT_ID, event());

            assertThat(user.getDeletionStatus()).isEqualTo(AccountDeletionStatus.DELETING);
            verifyNoInteractions(credentialRepository, refreshTokenRepository);
            verify(userRepository, never()).flush();
        }

        @Test
        @DisplayName("does nothing when the account has already been deleted")
        void shouldSkipAlreadyDeletedAccount() {
            User user = AuthTestDataFactory.user(
                    AuthTestDataFactory.USER_ID,
                    AuthTestDataFactory.EMPLOYEE_ID,
                    AuthTestDataFactory.EMAIL,
                    false,
                    AccountDeletionStatus.DELETED
            );

            when(userRepository.findByIdForUpdate(AuthTestDataFactory.USER_ID)).thenReturn(Optional.of(user));
            when(processedEventRepository.registerIfAbsent(EVENT_ID)).thenReturn(1);

            service.completeDeletion(EVENT_ID, event());

            verifyNoInteractions(credentialRepository, refreshTokenRepository);
            verify(userRepository, never()).flush();
        }

        @Test
        @DisplayName("rejects users that are not in DELETING state")
        void shouldRejectUserNotInDeletingState() {
            User user = AuthTestDataFactory.user();

            when(userRepository.findByIdForUpdate(AuthTestDataFactory.USER_ID)).thenReturn(Optional.of(user));
            when(processedEventRepository.registerIfAbsent(EVENT_ID)).thenReturn(1);

            assertThatThrownBy(() -> service.completeDeletion(EVENT_ID, event()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("DELETING");

            verifyNoInteractions(credentialRepository, refreshTokenRepository);
        }

        @Test
        @DisplayName("fails when the user does not exist")
        void shouldFailWhenUserIsMissing() {
            when(userRepository.findByIdForUpdate(AuthTestDataFactory.USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.completeDeletion(EVENT_ID, event()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(AuthTestDataFactory.USER_ID.toString());

            verifyNoInteractions(processedEventRepository);
        }

        @Test
        @DisplayName("rejects events for a different employee")
        void shouldRejectEmployeeMismatch() {
            User user = deletingUser();

            when(userRepository.findByIdForUpdate(AuthTestDataFactory.USER_ID)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> service.completeDeletion(
                    EVENT_ID,
                    new EmployeeAccountUnlinkedEvent(AuthTestDataFactory.USER_ID, AuthTestDataFactory.OTHER_EMPLOYEE_ID)
            ))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("не совпадает");

            verifyNoInteractions(processedEventRepository, credentialRepository, refreshTokenRepository);
        }

        @Test
        @DisplayName("requires an event identifier")
        void shouldRequireEventId() {
            assertThatThrownBy(() -> service.completeDeletion(null, event()))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("eventId is required");

            verifyNoInteractions(userRepository);
        }

        @Test
        @DisplayName("requires an event")
        void shouldRequireEvent() {
            assertThatThrownBy(() -> service.completeDeletion(EVENT_ID, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("event is required");

            verifyNoInteractions(userRepository);
        }

        @Test
        @DisplayName("requires a user identifier inside the event")
        void shouldRequireEventUserId() {
            assertThatThrownBy(() -> service.completeDeletion(
                    EVENT_ID,
                    new EmployeeAccountUnlinkedEvent(null, AuthTestDataFactory.EMPLOYEE_ID)
            ))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("userId is required");

            verifyNoInteractions(userRepository);
        }

        @Test
        @DisplayName("requires an employee identifier inside the event")
        void shouldRequireEventEmployeeId() {
            assertThatThrownBy(() -> service.completeDeletion(
                    EVENT_ID,
                    new EmployeeAccountUnlinkedEvent(AuthTestDataFactory.USER_ID, null)
            ))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("employeeId is required");

            verifyNoInteractions(userRepository);
        }

        @Test
        @DisplayName("propagates repository failures")
        void shouldPropagateRepositoryFailure() {
            when(userRepository.findByIdForUpdate(any())).thenThrow(new IllegalStateException("db is down"));

            assertThatThrownBy(() -> service.completeDeletion(EVENT_ID, event()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("db is down");
        }
    }

    private User deletingUser() {
        return AuthTestDataFactory.user(
                AuthTestDataFactory.USER_ID,
                AuthTestDataFactory.EMPLOYEE_ID,
                AuthTestDataFactory.EMAIL,
                false,
                AccountDeletionStatus.DELETING
        );
    }

    private EmployeeAccountUnlinkedEvent event() {
        return new EmployeeAccountUnlinkedEvent(AuthTestDataFactory.USER_ID, AuthTestDataFactory.EMPLOYEE_ID);
    }
}
