package com.zodiakk.authservice.integration.employee.consumer;

import com.zodiakk.authservice.common.constants.KafkaEventConstants;
import com.zodiakk.authservice.integration.employee.dto.EmployeeAccountUnlinkedEvent;
import com.zodiakk.authservice.outbox.dto.OutboxEventEnvelope;
import com.zodiakk.authservice.support.AuthTestDataFactory;
import com.zodiakk.authservice.user.service.UserDeletionCompletionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.core.exc.StreamConstraintsException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeAccountEventConsumerTest {

    private static final UUID EVENT_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

    private static final String MESSAGE = "{\"eventId\":\"cccccccc-cccc-cccc-cccc-cccccccccccc\"}";

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private UserDeletionCompletionService deletionService;

    @Test
    @DisplayName("completes deletion for a supported event")
    void shouldConsumeSupportedEvent() {
        JsonNode payload = mock(JsonNode.class);
        OutboxEventEnvelope envelope = new OutboxEventEnvelope(
                EVENT_ID,
                KafkaEventConstants.EMPLOYEE_ACCOUNT_UNLINKED,
                KafkaEventConstants.EVENT_VERSION_V1,
                OffsetDateTime.now(ZoneOffset.UTC),
                payload
        );
        EmployeeAccountUnlinkedEvent event = new EmployeeAccountUnlinkedEvent(
                AuthTestDataFactory.USER_ID,
                AuthTestDataFactory.EMPLOYEE_ID
        );

        when(objectMapper.readValue(MESSAGE, OutboxEventEnvelope.class)).thenReturn(envelope);
        when(objectMapper.treeToValue(payload, EmployeeAccountUnlinkedEvent.class)).thenReturn(event);

        EmployeeAccountEventConsumer consumer = new EmployeeAccountEventConsumer(objectMapper, deletionService);
        consumer.consume(MESSAGE);

        verify(deletionService).completeDeletion(EVENT_ID, event);
    }

    @Test
    @DisplayName("rejects unsupported event types")
    void shouldRejectUnsupportedEventType() {
        OutboxEventEnvelope envelope = new OutboxEventEnvelope(
                EVENT_ID,
                "USER_CREATED",
                KafkaEventConstants.EVENT_VERSION_V1,
                OffsetDateTime.now(ZoneOffset.UTC),
                mock(JsonNode.class)
        );

        when(objectMapper.readValue(MESSAGE, OutboxEventEnvelope.class)).thenReturn(envelope);

        EmployeeAccountEventConsumer consumer = new EmployeeAccountEventConsumer(objectMapper, deletionService);

        assertThatThrownBy(() -> consumer.consume(MESSAGE))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(deletionService);
    }

    @Test
    @DisplayName("rejects unsupported event versions")
    void shouldRejectUnsupportedVersion() {
        OutboxEventEnvelope envelope = new OutboxEventEnvelope(
                EVENT_ID,
                KafkaEventConstants.EMPLOYEE_ACCOUNT_UNLINKED,
                2,
                OffsetDateTime.now(ZoneOffset.UTC),
                mock(JsonNode.class)
        );

        when(objectMapper.readValue(MESSAGE, OutboxEventEnvelope.class)).thenReturn(envelope);

        EmployeeAccountEventConsumer consumer = new EmployeeAccountEventConsumer(objectMapper, deletionService);

        assertThatThrownBy(() -> consumer.consume(MESSAGE))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(deletionService);
    }

    @Test
    @DisplayName("propagates malformed messages so they can be sent to the DLT")
    void shouldPropagateMalformedMessage() {
        when(objectMapper.readValue(MESSAGE, OutboxEventEnvelope.class))
                .thenThrow(new StreamConstraintsException("malformed"));

        EmployeeAccountEventConsumer consumer = new EmployeeAccountEventConsumer(objectMapper, deletionService);

        assertThatThrownBy(() -> consumer.consume(MESSAGE))
                .isInstanceOf(StreamConstraintsException.class);

        verify(deletionService, never()).completeDeletion(any(), any());
    }

    @Test
    @DisplayName("propagates payload conversion failures")
    void shouldPropagatePayloadConversionFailure() {
        JsonNode payload = mock(JsonNode.class);
        OutboxEventEnvelope envelope = new OutboxEventEnvelope(
                EVENT_ID,
                KafkaEventConstants.EMPLOYEE_ACCOUNT_UNLINKED,
                KafkaEventConstants.EVENT_VERSION_V1,
                OffsetDateTime.now(ZoneOffset.UTC),
                payload
        );

        when(objectMapper.readValue(MESSAGE, OutboxEventEnvelope.class)).thenReturn(envelope);
        when(objectMapper.treeToValue(payload, EmployeeAccountUnlinkedEvent.class))
                .thenThrow(new StreamConstraintsException("bad payload"));

        EmployeeAccountEventConsumer consumer = new EmployeeAccountEventConsumer(objectMapper, deletionService);

        assertThatThrownBy(() -> consumer.consume(MESSAGE))
                .isInstanceOf(StreamConstraintsException.class);

        verify(deletionService, never()).completeDeletion(any(), any());
    }

    @Test
    @DisplayName("propagates deletion failures so the message is retried")
    void shouldPropagateDeletionFailure() {
        JsonNode payload = mock(JsonNode.class);
        OutboxEventEnvelope envelope = new OutboxEventEnvelope(
                EVENT_ID,
                KafkaEventConstants.EMPLOYEE_ACCOUNT_UNLINKED,
                KafkaEventConstants.EVENT_VERSION_V1,
                OffsetDateTime.now(ZoneOffset.UTC),
                payload
        );
        EmployeeAccountUnlinkedEvent event = new EmployeeAccountUnlinkedEvent(
                AuthTestDataFactory.USER_ID,
                AuthTestDataFactory.EMPLOYEE_ID
        );

        when(objectMapper.readValue(MESSAGE, OutboxEventEnvelope.class)).thenReturn(envelope);
        when(objectMapper.treeToValue(payload, EmployeeAccountUnlinkedEvent.class)).thenReturn(event);
        org.mockito.Mockito.doThrow(new IllegalStateException("user missing"))
                .when(deletionService)
                .completeDeletion(EVENT_ID, event);

        EmployeeAccountEventConsumer consumer = new EmployeeAccountEventConsumer(objectMapper, deletionService);

        assertThatThrownBy(() -> consumer.consume(MESSAGE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("user missing");
    }
}
