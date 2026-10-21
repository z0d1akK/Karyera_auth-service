package com.zodiakk.authservice.outbox.service;

import com.zodiakk.authservice.outbox.config.OutboxProperties;
import com.zodiakk.authservice.outbox.entity.OutboxEvent;
import com.zodiakk.authservice.outbox.entity.OutboxEventStatus;
import com.zodiakk.authservice.outbox.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxClaimServiceTest {

    private static final int BATCH_SIZE = 10;

    private static final long LEASE_SECONDS = 180L;

    private static final UUID EVENT_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");

    @Mock
    private OutboxEventRepository repository;

    private OutboxClaimService service;

    @BeforeEach
    void setUp() {
        OutboxProperties properties = new OutboxProperties(BATCH_SIZE, 2_000L, LEASE_SECONDS, 15L);
        service = new OutboxClaimService(repository, properties);
    }

    @Nested
    @DisplayName("claimBatch")
    class ClaimBatch {

        @Test
        @DisplayName("marks available events as PROCESSING and leases them")
        void shouldClaimBatch() {
            OutboxEvent event = event(2);

            when(repository.findAvailableForUpdate(any(), eq(BATCH_SIZE))).thenReturn(List.of(event));

            OffsetDateTime before = OffsetDateTime.now(ZoneOffset.UTC);
            List<OutboxEvent> claimed = service.claimBatch();

            assertThat(claimed).containsExactly(event);
            assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PROCESSING);
            assertThat(event.getAttempts()).isEqualTo(3);
            assertThat(event.getLockedUntil())
                    .isAfterOrEqualTo(before.plusSeconds(LEASE_SECONDS))
                    .isBeforeOrEqualTo(OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(LEASE_SECONDS));
            verify(repository).flush();
        }

        @Test
        @DisplayName("returns an immutable snapshot")
        void shouldReturnImmutableSnapshot() {
            OutboxEvent event = event(0);

            when(repository.findAvailableForUpdate(any(), eq(BATCH_SIZE))).thenReturn(List.of(event));

            List<OutboxEvent> claimed = service.claimBatch();

            assertThatThrownBy(() -> claimed.add(event(1)))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("flushes even when nothing can be claimed")
        void shouldFlushEmptyBatch() {
            when(repository.findAvailableForUpdate(any(), eq(BATCH_SIZE))).thenReturn(List.of());

            assertThat(service.claimBatch()).isEmpty();
            verify(repository).flush();
        }

        @Test
        @DisplayName("propagates repository failures")
        void shouldPropagateRepositoryFailure() {
            when(repository.findAvailableForUpdate(any(), eq(BATCH_SIZE)))
                    .thenThrow(new IllegalStateException("lock timeout"));

            assertThatThrownBy(() -> service.claimBatch())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("lock timeout");
        }
    }

    @Nested
    @DisplayName("markPublished")
    class MarkPublished {

        @Test
        @DisplayName("returns true when the claim is still valid")
        void shouldMarkPublished() {
            when(repository.markPublished(
                    eq(EVENT_ID),
                    eq(3),
                    eq(OutboxEventStatus.PROCESSING),
                    eq(OutboxEventStatus.PUBLISHED),
                    any(OffsetDateTime.class)
            )).thenReturn(1);

            assertThat(service.markPublished(EVENT_ID, 3)).isTrue();
        }

        @Test
        @DisplayName("returns false when the claim has expired")
        void shouldReportExpiredClaim() {
            when(repository.markPublished(any(), eq(3), any(), any(), any())).thenReturn(0);

            assertThat(service.markPublished(EVENT_ID, 3)).isFalse();
        }
    }

    @Nested
    @DisplayName("markFailed")
    class MarkFailed {

        @Test
        @DisplayName("schedules an exponential retry")
        void shouldScheduleRetry() {
            when(repository.markFailed(any(), eq(1), any(), any(), any(), any())).thenReturn(1);

            OffsetDateTime before = OffsetDateTime.now(ZoneOffset.UTC);
            boolean updated = service.markFailed(EVENT_ID, 1);

            assertThat(updated).isTrue();

            ArgumentCaptor<OffsetDateTime> nextAttemptCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
            verify(repository).markFailed(
                    eq(EVENT_ID),
                    eq(1),
                    eq(OutboxEventStatus.PROCESSING),
                    eq(OutboxEventStatus.PENDING),
                    any(OffsetDateTime.class),
                    nextAttemptCaptor.capture()
            );

            assertThat(nextAttemptCaptor.getValue())
                    .isAfterOrEqualTo(before.plusSeconds(2))
                    .isBeforeOrEqualTo(OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(2));
        }

        @Test
        @DisplayName("caps the retry delay at the maximum exponent")
        void shouldCapRetryDelay() {
            when(repository.markFailed(any(), eq(20), any(), any(), any(), any())).thenReturn(1);

            OffsetDateTime before = OffsetDateTime.now(ZoneOffset.UTC);
            service.markFailed(EVENT_ID, 20);

            ArgumentCaptor<OffsetDateTime> nextAttemptCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
            verify(repository).markFailed(
                    eq(EVENT_ID),
                    eq(20),
                    any(),
                    any(),
                    any(),
                    nextAttemptCaptor.capture()
            );

            OffsetDateTime expected = before.plusSeconds(256);
            assertThat(Math.abs(nextAttemptCaptor.getValue().toInstant().toEpochMilli()
                    - expected.toInstant().toEpochMilli())).isLessThan(5_000L);
        }

        @Test
        @DisplayName("returns false when the claim has been lost")
        void shouldReportLostClaim() {
            when(repository.markFailed(any(), eq(2), any(), any(), any(), any())).thenReturn(0);

            assertThat(service.markFailed(EVENT_ID, 2)).isFalse();
            verifyNoMoreInteractions(repository);
        }
    }

    private OutboxEvent event(int attempts) {
        return OutboxEvent.builder()
                .id(EVENT_ID)
                .aggregateId(UUID.randomUUID())
                .aggregateType("EMPLOYEE_ACCOUNT")
                .eventType("USER_CREATED")
                .eventVersion(1)
                .payload("{}")
                .status(OutboxEventStatus.PENDING)
                .attempts(attempts)
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                .nextAttemptAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();
    }
}
