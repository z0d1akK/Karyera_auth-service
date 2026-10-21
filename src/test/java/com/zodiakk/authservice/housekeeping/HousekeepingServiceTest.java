package com.zodiakk.authservice.housekeeping;

import com.zodiakk.authservice.integration.employee.repository.ProcessedEventRepository;
import com.zodiakk.authservice.outbox.entity.OutboxEventStatus;
import com.zodiakk.authservice.outbox.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HousekeepingServiceTest {

    private static final long OUTBOX_RETENTION_DAYS = 7L;

    private static final long PROCESSED_EVENTS_RETENTION_DAYS = 30L;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    private HousekeepingService service;

    @BeforeEach
    void setUp() {
        service = new HousekeepingService(outboxEventRepository, processedEventRepository);
        ReflectionTestUtils.setField(service, "outboxRetentionDays", OUTBOX_RETENTION_DAYS);
        ReflectionTestUtils.setField(service, "processedEventsRetentionDays", PROCESSED_EVENTS_RETENTION_DAYS);
    }

    @Test
    @DisplayName("removes published outbox events and old processed events")
    void shouldCleanupExpiredRecords() {
        when(outboxEventRepository.deletePublishedBefore(eq(OutboxEventStatus.PUBLISHED), any()))
                .thenReturn(3);
        when(processedEventRepository.deleteProcessedBefore(any())).thenReturn(5);

        OffsetDateTime before = OffsetDateTime.now(ZoneOffset.UTC);
        service.cleanup();

        ArgumentCaptor<OffsetDateTime> outboxCutoff = ArgumentCaptor.forClass(OffsetDateTime.class);
        ArgumentCaptor<OffsetDateTime> processedCutoff = ArgumentCaptor.forClass(OffsetDateTime.class);

        verify(outboxEventRepository).deletePublishedBefore(
                eq(OutboxEventStatus.PUBLISHED),
                outboxCutoff.capture()
        );
        verify(processedEventRepository).deleteProcessedBefore(processedCutoff.capture());

        assertThatCutoffIsCloseTo(outboxCutoff.getValue(), before.minusDays(OUTBOX_RETENTION_DAYS));
        assertThatCutoffIsCloseTo(processedCutoff.getValue(), before.minusDays(PROCESSED_EVENTS_RETENTION_DAYS));
    }

    @Test
    @DisplayName("tolerates an empty cleanup")
    void shouldTolerateEmptyCleanup() {
        when(outboxEventRepository.deletePublishedBefore(any(), any())).thenReturn(0);
        when(processedEventRepository.deleteProcessedBefore(any())).thenReturn(0);

        assertThatCode(() -> service.cleanup()).doesNotThrowAnyException();
    }

    private void assertThatCutoffIsCloseTo(OffsetDateTime actual, OffsetDateTime expected) {
        long difference = Math.abs(
                actual.toInstant().toEpochMilli() - expected.toInstant().toEpochMilli()
        );

        assertThat(difference).isLessThan(5_000L);
    }
}
