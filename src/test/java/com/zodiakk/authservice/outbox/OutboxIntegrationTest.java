package com.zodiakk.authservice.outbox;

import com.zodiakk.authservice.common.AbstractIntegrationTest;
import com.zodiakk.authservice.housekeeping.HousekeepingService;
import com.zodiakk.authservice.outbox.entity.OutboxEvent;
import com.zodiakk.authservice.outbox.entity.OutboxEventStatus;
import com.zodiakk.authservice.outbox.publisher.OutboxPublisher;
import com.zodiakk.authservice.outbox.service.OutboxClaimService;
import com.zodiakk.authservice.outbox.service.OutboxEventService;
import com.zodiakk.authservice.support.AuthTestDataFactory;
import com.zodiakk.authservice.user.event.UserCreatedEvent;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Transactional outbox")
class OutboxIntegrationTest extends AbstractIntegrationTest {

    private static final String USER_EVENTS_TOPIC = "auth.user.events.v1";

    @Autowired
    private OutboxEventService outboxEventService;

    @Autowired
    private OutboxClaimService outboxClaimService;

    @Autowired
    private OutboxPublisher outboxPublisher;

    @Autowired
    private HousekeepingService housekeepingService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private ConsumerFactory<String, String> consumerFactory;

    @Nested
    @DisplayName("claiming")
    class Claiming {

        @Test
        @DisplayName("claims a pending event and leases it")
        void shouldClaimPendingEvent() {
            saveUserCreatedEvent();

            List<OutboxEvent> claimed = outboxClaimService.claimBatch();

            assertThat(claimed).hasSize(1);
            assertThat(claimed.getFirst().getStatus()).isEqualTo(OutboxEventStatus.PROCESSING);
            assertThat(claimed.getFirst().getAttempts()).isEqualTo(1);
            assertThat(claimed.getFirst().getLockedUntil()).isAfter(OffsetDateTime.now(ZoneOffset.UTC));

            OutboxEvent stored = outboxEventRepository.findAll().getFirst();
            assertThat(stored.getStatus()).isEqualTo(OutboxEventStatus.PROCESSING);
            assertThat(stored.getAttempts()).isEqualTo(1);
        }

        @Test
        @DisplayName("does not claim an event before its next attempt")
        void shouldNotClaimEventThatIsNotDue() {
            saveUserCreatedEvent();
            OutboxEvent claimed = outboxClaimService.claimBatch().getFirst();
            outboxClaimService.markFailed(claimed.getId(), claimed.getAttempts());

            assertThat(outboxClaimService.claimBatch()).isEmpty();
        }
    }

    @Nested
    @DisplayName("acknowledgement")
    class Acknowledgement {

        @Test
        @DisplayName("marks a claimed event as published")
        void shouldMarkPublished() {
            saveUserCreatedEvent();
            OutboxEvent claimed = outboxClaimService.claimBatch().getFirst();

            assertThat(outboxClaimService.markPublished(claimed.getId(), claimed.getAttempts())).isTrue();

            OutboxEvent stored = outboxEventRepository.findById(claimed.getId()).orElseThrow();
            assertThat(stored.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
            assertThat(stored.getPublishedAt()).isNotNull();
            assertThat(stored.getLockedUntil()).isNull();
        }

        @Test
        @DisplayName("rejects a stale publication acknowledgement")
        void shouldRejectStaleAcknowledgement() {
            saveUserCreatedEvent();
            OutboxEvent claimed = outboxClaimService.claimBatch().getFirst();

            assertThat(outboxClaimService.markPublished(claimed.getId(), claimed.getAttempts() + 1)).isFalse();
        }

        @Test
        @DisplayName("schedules a retry after a failure")
        void shouldMarkFailed() {
            saveUserCreatedEvent();
            OutboxEvent claimed = outboxClaimService.claimBatch().getFirst();

            assertThat(outboxClaimService.markFailed(claimed.getId(), claimed.getAttempts())).isTrue();

            OutboxEvent stored = outboxEventRepository.findById(claimed.getId()).orElseThrow();
            assertThat(stored.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
            assertThat(stored.getLockedUntil()).isNull();
            assertThat(stored.getNextAttemptAt()).isAfter(OffsetDateTime.now(ZoneOffset.UTC));
        }
    }

    @Nested
    @DisplayName("publishing")
    class Publishing {

        @Test
        @DisplayName("publishes a pending event to Kafka and marks it as published")
        void shouldPublishToKafka() {
            saveUserCreatedEvent();

            outboxPublisher.publishPendingEvents();

            awaitStatus(OutboxEventStatus.PUBLISHED, Duration.ofSeconds(20));

            OutboxEvent stored = outboxEventRepository.findAll().getFirst();
            assertThat(stored.getAttempts()).isEqualTo(1);
            assertThat(stored.getPublishedAt()).isNotNull();

            List<ConsumerRecord<String, String>> records = pollRecords(
                    USER_EVENTS_TOPIC,
                    record -> record.key().equals(AuthTestDataFactory.EMPLOYEE_ID.toString()),
                    Duration.ofSeconds(20)
            );

            assertThat(records).hasSize(1);
            assertThat(records.getFirst().value())
                    .contains("\"eventType\"", "USER_CREATED")
                    .contains(AuthTestDataFactory.USER_ID.toString());
        }

        @Test
        @DisplayName("does not duplicate a published event")
        void shouldNotRepublishPublishedEvents() {
            saveUserCreatedEvent();
            outboxPublisher.publishPendingEvents();
            awaitStatus(OutboxEventStatus.PUBLISHED, Duration.ofSeconds(20));

            outboxPublisher.publishPendingEvents();

            assertThat(outboxEventRepository.findAll()).hasSize(1);
            assertThat(outboxEventRepository.findAll().getFirst().getAttempts()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("housekeeping")
    class Housekeeping {

        @Test
        @DisplayName("removes published events and processed events past retention")
        void shouldRemoveExpiredRecords() {
            OutboxEvent expired = OutboxEvent.builder()
                    .id(java.util.UUID.randomUUID())
                    .aggregateId(AuthTestDataFactory.EMPLOYEE_ID)
                    .aggregateType("EMPLOYEE_ACCOUNT")
                    .eventType("USER_CREATED")
                    .eventVersion(1)
                    .payload("{}")
                    .status(OutboxEventStatus.PUBLISHED)
                    .attempts(1)
                    .createdAt(OffsetDateTime.now(ZoneOffset.UTC).minusDays(40))
                    .publishedAt(OffsetDateTime.now(ZoneOffset.UTC).minusDays(40))
                    .nextAttemptAt(OffsetDateTime.now(ZoneOffset.UTC).minusDays(40))
                    .build();
            outboxEventRepository.saveAndFlush(expired);

            jdbcTemplate.update(
                    "INSERT INTO processed_events (event_id, processed_at) VALUES (?, ?)",
                    java.util.UUID.randomUUID(),
                    OffsetDateTime.now(ZoneOffset.UTC).minusDays(60)
            );

            housekeepingService.cleanup();

            assertThat(outboxEventRepository.findAll()).isEmpty();
            assertThat(processedEventRepository.findAll()).isEmpty();
        }

        @Test
        @DisplayName("keeps records inside the retention window")
        void shouldKeepRecentRecords() {
            saveUserCreatedEvent();
            OutboxEvent claimed = outboxClaimService.claimBatch().getFirst();
            outboxClaimService.markPublished(claimed.getId(), claimed.getAttempts());

            housekeepingService.cleanup();

            assertThat(outboxEventRepository.findAll()).hasSize(1);
        }
    }

    @Test
    @DisplayName("refuses to record an event outside a transaction")
    void shouldRequireTransaction() {
        assertThatThrownBy(() -> outboxEventService.saveEvent(
                AuthTestDataFactory.EMPLOYEE_ID,
                "EMPLOYEE_ACCOUNT",
                "USER_CREATED",
                1,
                new UserCreatedEvent(AuthTestDataFactory.USER_ID, AuthTestDataFactory.EMPLOYEE_ID)
        )).isInstanceOf(IllegalTransactionStateException.class);
    }

    private void saveUserCreatedEvent() {
        transactionTemplate.executeWithoutResult(status -> outboxEventService.saveEvent(
                AuthTestDataFactory.EMPLOYEE_ID,
                "EMPLOYEE_ACCOUNT",
                "USER_CREATED",
                1,
                new UserCreatedEvent(AuthTestDataFactory.USER_ID, AuthTestDataFactory.EMPLOYEE_ID)
        ));
    }

    private void awaitStatus(OutboxEventStatus expected, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();

        while (System.nanoTime() < deadline) {
            List<OutboxEvent> events = outboxEventRepository.findAll();

            if (!events.isEmpty() && events.stream().allMatch(event -> event.getStatus() == expected)) {
                return;
            }

            sleep();
        }

        throw new AssertionError("Outbox events did not reach status " + expected
                + ": " + outboxEventRepository.findAll().stream()
                .map(event -> event.getStatus() + " attempts=" + event.getAttempts())
                .toList());
    }

    private List<ConsumerRecord<String, String>> pollRecords(
            String topic,
            java.util.function.Predicate<ConsumerRecord<String, String>> filter,
            Duration timeout
    ) {
        List<ConsumerRecord<String, String>> matching = new ArrayList<>();

        try (Consumer<String, String> consumer = consumerFactory.createConsumer(
                "outbox-test-" + java.util.UUID.randomUUID())) {
            consumer.subscribe(List.of(topic));

            long deadline = System.nanoTime() + timeout.toNanos();

            while (System.nanoTime() < deadline && matching.isEmpty()) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                records.forEach(record -> {
                    if (filter.test(record)) {
                        matching.add(record);
                    }
                });
            }
        }

        return matching;
    }

    private void sleep() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }
}
