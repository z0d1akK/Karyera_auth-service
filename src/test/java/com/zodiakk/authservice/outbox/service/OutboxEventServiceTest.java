package com.zodiakk.authservice.outbox.service;

import com.zodiakk.authservice.outbox.entity.OutboxEvent;
import com.zodiakk.authservice.outbox.entity.OutboxEventStatus;
import com.zodiakk.authservice.outbox.repository.OutboxEventRepository;
import com.zodiakk.authservice.user.event.UserCreatedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.core.exc.StreamConstraintsException;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxEventServiceTest {

    private static final UUID AGGREGATE_ID = UUID.fromString("77777777-7777-7777-7777-777777777777");

    private static final UUID USER_ID = UUID.fromString("88888888-8888-8888-8888-888888888888");

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("stores a serialized envelope as a pending event")
    void shouldStorePendingEvent() {
        ObjectMapper realMapper = new ObjectMapper();
        OutboxEventService service = new OutboxEventService(outboxEventRepository, realMapper);
        UserCreatedEvent payload = new UserCreatedEvent(USER_ID, AGGREGATE_ID);

        service.saveEvent(AGGREGATE_ID, "EMPLOYEE_ACCOUNT", "USER_CREATED", 1, payload);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());

        OutboxEvent event = captor.getValue();
        assertThat(event.getId()).isNotNull();
        assertThat(event.getAggregateId()).isEqualTo(AGGREGATE_ID);
        assertThat(event.getAggregateType()).isEqualTo("EMPLOYEE_ACCOUNT");
        assertThat(event.getEventType()).isEqualTo("USER_CREATED");
        assertThat(event.getEventVersion()).isEqualTo(1);
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(event.getAttempts()).isZero();
        assertThat(event.getCreatedAt()).isNotNull();
        assertThat(event.getNextAttemptAt()).isNotNull();
        assertThat(event.getPayload())
                .contains("\"eventType\":\"USER_CREATED\"")
                .contains("\"eventVersion\":1")
                .contains(AGGREGATE_ID.toString());
    }

    @Test
    @DisplayName("wraps serialization failures into an illegal state exception")
    void shouldWrapSerializationFailure() {
        OutboxEventService service = new OutboxEventService(outboxEventRepository, objectMapper);

        when(objectMapper.valueToTree(any()))
                .thenThrow(new StreamConstraintsException("too large"));

        assertThatThrownBy(() -> service.saveEvent(
                AGGREGATE_ID,
                "EMPLOYEE_ACCOUNT",
                "USER_CREATED",
                1,
                new UserCreatedEvent(USER_ID, AGGREGATE_ID)
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("USER_CREATED")
                .hasCauseInstanceOf(StreamConstraintsException.class);

        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("wraps failures raised while writing the envelope")
    void shouldWrapWriteFailure() {
        OutboxEventService service = new OutboxEventService(outboxEventRepository, objectMapper);

        when(objectMapper.valueToTree(any())).thenReturn(new ObjectMapper().createObjectNode());
        when(objectMapper.writeValueAsString(any()))
                .thenThrow(new StreamConstraintsException("cannot write"));

        assertThatThrownBy(() -> service.saveEvent(
                AGGREGATE_ID,
                "EMPLOYEE_ACCOUNT",
                "USER_CREATED",
                1,
                new UserCreatedEvent(USER_ID, AGGREGATE_ID)
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasCauseInstanceOf(StreamConstraintsException.class);

        verify(outboxEventRepository, never()).save(any());
    }
}
