package com.zodiakk.authservice.outbox.publisher;

import com.zodiakk.authservice.outbox.config.OutboxProperties;
import com.zodiakk.authservice.outbox.entity.OutboxEvent;
import com.zodiakk.authservice.outbox.entity.OutboxEventStatus;
import com.zodiakk.authservice.outbox.service.OutboxClaimService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    private static final String TOPIC = "auth.user.events.v1";

    private static final UUID EVENT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    private static final UUID AGGREGATE_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @Mock
    private OutboxClaimService claimService;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private OutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        OutboxProperties properties = new OutboxProperties(10, 2_000L, 180L, 15L);
        publisher = new OutboxPublisher(claimService, kafkaTemplate, properties);
        ReflectionTestUtils.setField(publisher, "userEventsTopic", TOPIC);
    }

    @Nested
    @DisplayName("publishPendingEvents")
    class PublishPendingEvents {

        @Test
        @DisplayName("does nothing when there are no claimable events")
        void shouldDoNothingWithoutEvents() {
            when(claimService.claimBatch()).thenReturn(List.of());

            publisher.publishPendingEvents();

            verifyNoInteractions(kafkaTemplate);
        }

        @Test
        @DisplayName("sends the payload and marks the event as published")
        void shouldPublishEvent() {
            OutboxEvent event = event(1);
            when(claimService.claimBatch()).thenReturn(List.of(event));
            when(kafkaTemplate.send(TOPIC, AGGREGATE_ID.toString(), event.getPayload()))
                    .thenReturn(completed());
            when(claimService.markPublished(EVENT_ID, 1)).thenReturn(true);

            publisher.publishPendingEvents();

            verify(kafkaTemplate).send(TOPIC, AGGREGATE_ID.toString(), event.getPayload());
            verify(claimService).markPublished(EVENT_ID, 1);
            verify(claimService, never()).markFailed(any(), anyInt());
        }

        @Test
        @DisplayName("marks a failed send as failed so it can be retried")
        void shouldMarkFailedWhenSendFails() {
            OutboxEvent event = event(2);
            when(claimService.claimBatch()).thenReturn(List.of(event));
            when(kafkaTemplate.send(TOPIC, AGGREGATE_ID.toString(), event.getPayload()))
                    .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));
            when(claimService.markFailed(EVENT_ID, 2)).thenReturn(true);

            publisher.publishPendingEvents();

            verify(claimService).markFailed(EVENT_ID, 2);
            verify(claimService, never()).markPublished(any(), anyInt());
        }

        @Test
        @DisplayName("marks a synchronously rejected send as failed")
        void shouldMarkFailedWhenSendThrows() {
            OutboxEvent event = event(3);
            when(claimService.claimBatch()).thenReturn(List.of(event));
            when(kafkaTemplate.send(TOPIC, AGGREGATE_ID.toString(), event.getPayload()))
                    .thenThrow(new IllegalStateException("producer closed"));
            when(claimService.markFailed(EVENT_ID, 3)).thenReturn(true);

            publisher.publishPendingEvents();

            verify(claimService).markFailed(EVENT_ID, 3);
        }

        @Test
        @DisplayName("keeps going when marking the publication fails")
        void shouldSwallowMarkPublishedFailure() {
            OutboxEvent event = event(4);
            when(claimService.claimBatch()).thenReturn(List.of(event));
            when(kafkaTemplate.send(TOPIC, AGGREGATE_ID.toString(), event.getPayload()))
                    .thenReturn(completed());
            when(claimService.markPublished(EVENT_ID, 4)).thenThrow(new IllegalStateException("db down"));

            assertThatCode(() -> publisher.publishPendingEvents()).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("keeps going when the claim expired before the acknowledgement")
        void shouldHandleExpiredClaim() {
            OutboxEvent event = event(5);
            when(claimService.claimBatch()).thenReturn(List.of(event));
            when(kafkaTemplate.send(TOPIC, AGGREGATE_ID.toString(), event.getPayload()))
                    .thenReturn(completed());
            when(claimService.markPublished(EVENT_ID, 5)).thenReturn(false);

            assertThatCode(() -> publisher.publishPendingEvents()).doesNotThrowAnyException();
            verify(claimService).markPublished(EVENT_ID, 5);
        }

        @Test
        @DisplayName("keeps going when marking the failure fails")
        void shouldSwallowMarkFailedFailure() {
            OutboxEvent event = event(6);
            when(claimService.claimBatch()).thenReturn(List.of(event));
            when(kafkaTemplate.send(TOPIC, AGGREGATE_ID.toString(), event.getPayload()))
                    .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));
            when(claimService.markFailed(EVENT_ID, 6)).thenThrow(new IllegalStateException("db down"));

            assertThatCode(() -> publisher.publishPendingEvents()).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("publishes every claimed event")
        void shouldPublishEveryEvent() {
            OutboxEvent first = event(7);
            OutboxEvent second = OutboxEvent.builder()
                    .id(UUID.randomUUID())
                    .aggregateId(UUID.randomUUID())
                    .aggregateType("EMPLOYEE_ACCOUNT")
                    .eventType("USER_DELETED")
                    .eventVersion(1)
                    .payload("{}")
                    .status(OutboxEventStatus.PROCESSING)
                    .attempts(1)
                    .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                    .nextAttemptAt(OffsetDateTime.now(ZoneOffset.UTC))
                    .build();

            when(claimService.claimBatch()).thenReturn(List.of(first, second));
            when(kafkaTemplate.send(eq(TOPIC), anyString(), anyString())).thenReturn(completed());
            when(claimService.markPublished(any(), anyInt())).thenReturn(true);

            publisher.publishPendingEvents();

            verify(kafkaTemplate).send(TOPIC, first.getAggregateId().toString(), first.getPayload());
            verify(kafkaTemplate).send(TOPIC, second.getAggregateId().toString(), second.getPayload());
        }
    }

    private OutboxEvent event(int attempts) {
        return OutboxEvent.builder()
                .id(EVENT_ID)
                .aggregateId(AGGREGATE_ID)
                .aggregateType("EMPLOYEE_ACCOUNT")
                .eventType("USER_CREATED")
                .eventVersion(1)
                .payload("{\"eventType\":\"USER_CREATED\"}")
                .status(OutboxEventStatus.PROCESSING)
                .attempts(attempts)
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                .nextAttemptAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();
    }

    private CompletableFuture<SendResult<String, String>> completed() {
        return CompletableFuture.completedFuture(null);
    }
}

