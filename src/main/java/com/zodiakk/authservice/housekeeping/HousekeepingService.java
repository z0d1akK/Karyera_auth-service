package com.zodiakk.authservice.housekeeping;

import com.zodiakk.authservice.integration.employee.repository.ProcessedEventRepository;
import com.zodiakk.authservice.outbox.entity.OutboxEventStatus;
import com.zodiakk.authservice.outbox.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Slf4j
@Service
@RequiredArgsConstructor
public class HousekeepingService {

    private final OutboxEventRepository outboxEventRepository;

    private final ProcessedEventRepository processedEventRepository;

    @Value("${app.housekeeping.outbox-retention-days}")
    private long outboxRetentionDays;

    @Value("${app.housekeeping.processed-events-retention-days}")
    private long processedEventsRetentionDays;

    @Scheduled(cron = "${app.housekeeping.cron}")
    @Transactional
    public void cleanup() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        int removedOutbox = outboxEventRepository.deletePublishedBefore(
                OutboxEventStatus.PUBLISHED,
                now.minusDays(outboxRetentionDays)
        );

        int removedProcessedEvents = processedEventRepository.deleteProcessedBefore(
                now.minusDays(processedEventsRetentionDays)
        );

        log.info(
                "Housekeeping finished: removedOutboxEvents={}, removedProcessedEvents={}",
                removedOutbox,
                removedProcessedEvents
        );
    }
}
