package com.zodiakk.authservice.user.service;

import com.zodiakk.authservice.common.constants.KafkaEventConstants;
import com.zodiakk.authservice.outbox.entity.OutboxEventStatus;
import com.zodiakk.authservice.outbox.repository.OutboxEventRepository;
import com.zodiakk.authservice.support.AuthTestDataFactory;
import com.zodiakk.authservice.user.entity.AccountDeletionStatus;
import com.zodiakk.authservice.user.entity.User;
import com.zodiakk.authservice.user.event.UserEventService;
import com.zodiakk.authservice.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserDeletionReconciliationServiceTest {

    private static final long STUCK_AFTER_SECONDS = 300L;

    private static final int BATCH_SIZE = 50;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private UserEventService userEventService;

    private UserDeletionReconciliationService service;

    @BeforeEach
    void setUp() {
        service = new UserDeletionReconciliationService(
                userRepository,
                outboxEventRepository,
                userEventService
        );
        ReflectionTestUtils.setField(service, "stuckAfterSeconds", STUCK_AFTER_SECONDS);
        ReflectionTestUtils.setField(service, "batchSize", BATCH_SIZE);
    }

    @Test
    @DisplayName("re-publishes USER_DELETED for a stuck account without an in-flight event")
    void shouldRepublishStuckDeletion() {
        User user = stuckUser();

        when(userRepository.findStuckDeletions(
                eq(AccountDeletionStatus.DELETING),
                any(OffsetDateTime.class),
                eq(PageRequest.of(0, BATCH_SIZE))
        )).thenReturn(List.of(user));
        when(outboxEventRepository.existsByAggregateIdAndEventTypeAndStatusIn(
                AuthTestDataFactory.EMPLOYEE_ID,
                KafkaEventConstants.USER_DELETED,
                List.of(OutboxEventStatus.PENDING, OutboxEventStatus.PROCESSING)
        )).thenReturn(false);

        service.republishStuckDeletions();

        verify(userEventService).recordUserDeleted(AuthTestDataFactory.USER_ID, AuthTestDataFactory.EMPLOYEE_ID);
    }

    @Test
    @DisplayName("skips accounts whose deletion event is still in flight")
    void shouldSkipAccountWithInFlightEvent() {
        User user = stuckUser();

        when(userRepository.findStuckDeletions(
                eq(AccountDeletionStatus.DELETING),
                any(OffsetDateTime.class),
                any(Pageable.class)
        )).thenReturn(List.of(user));
        when(outboxEventRepository.existsByAggregateIdAndEventTypeAndStatusIn(
                AuthTestDataFactory.EMPLOYEE_ID,
                KafkaEventConstants.USER_DELETED,
                List.of(OutboxEventStatus.PENDING, OutboxEventStatus.PROCESSING)
        )).thenReturn(true);

        service.republishStuckDeletions();

        verify(userEventService, never()).recordUserDeleted(any(), any());
    }

    @Test
    @DisplayName("does nothing when there are no stuck deletions")
    void shouldDoNothingWithoutStuckDeletions() {
        when(userRepository.findStuckDeletions(
                eq(AccountDeletionStatus.DELETING),
                any(OffsetDateTime.class),
                any(Pageable.class)
        )).thenReturn(List.of());

        service.republishStuckDeletions();

        verifyNoInteractions(outboxEventRepository, userEventService);
    }

    @Test
    @DisplayName("processes every stuck account independently")
    void shouldProcessEveryStuckAccount() {
        User first = stuckUser();
        User second = AuthTestDataFactory.user(
                AuthTestDataFactory.OTHER_USER_ID,
                AuthTestDataFactory.OTHER_EMPLOYEE_ID,
                AuthTestDataFactory.OTHER_EMAIL,
                false,
                AccountDeletionStatus.DELETING
        );

        when(userRepository.findStuckDeletions(
                eq(AccountDeletionStatus.DELETING),
                any(OffsetDateTime.class),
                any(Pageable.class)
        )).thenReturn(List.of(first, second));
        when(outboxEventRepository.existsByAggregateIdAndEventTypeAndStatusIn(
                eq(AuthTestDataFactory.EMPLOYEE_ID),
                eq(KafkaEventConstants.USER_DELETED),
                any()
        )).thenReturn(true);
        when(outboxEventRepository.existsByAggregateIdAndEventTypeAndStatusIn(
                eq(AuthTestDataFactory.OTHER_EMPLOYEE_ID),
                eq(KafkaEventConstants.USER_DELETED),
                any()
        )).thenReturn(false);

        service.republishStuckDeletions();

        verify(userEventService).recordUserDeleted(
                AuthTestDataFactory.OTHER_USER_ID,
                AuthTestDataFactory.OTHER_EMPLOYEE_ID
        );
    }

    @Test
    @DisplayName("queries deletions older than the configured threshold")
    void shouldUseConfiguredThresholdAndBatch() {
        when(userRepository.findStuckDeletions(
                eq(AccountDeletionStatus.DELETING),
                any(OffsetDateTime.class),
                any(Pageable.class)
        )).thenReturn(List.of());

        OffsetDateTime before = OffsetDateTime.now(ZoneOffset.UTC);
        service.republishStuckDeletions();

        ArgumentCaptor<OffsetDateTime> thresholdCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);

        verify(userRepository).findStuckDeletions(
                eq(AccountDeletionStatus.DELETING),
                thresholdCaptor.capture(),
                pageableCaptor.capture()
        );

        OffsetDateTime expected = before.minusSeconds(STUCK_AFTER_SECONDS);
        assertThat(Math.abs(thresholdCaptor.getValue().toInstant().toEpochMilli()
                - expected.toInstant().toEpochMilli())).isLessThan(5_000L);
        assertThat(pageableCaptor.getValue()).isEqualTo(PageRequest.of(0, BATCH_SIZE));
    }

    private User stuckUser() {
        return AuthTestDataFactory.user(
                AuthTestDataFactory.USER_ID,
                AuthTestDataFactory.EMPLOYEE_ID,
                AuthTestDataFactory.EMAIL,
                false,
                AccountDeletionStatus.DELETING
        );
    }
}
