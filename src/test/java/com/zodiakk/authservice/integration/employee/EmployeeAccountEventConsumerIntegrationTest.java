package com.zodiakk.authservice.integration.employee;

import com.zodiakk.authservice.auth.entity.Role;
import com.zodiakk.authservice.common.AbstractIntegrationTest;
import com.zodiakk.authservice.common.constants.KafkaEventConstants;
import com.zodiakk.authservice.integration.employee.consumer.EmployeeAccountEventConsumer;
import com.zodiakk.authservice.integration.employee.dto.EmployeeAccountUnlinkedEvent;
import com.zodiakk.authservice.outbox.dto.OutboxEventEnvelope;
import com.zodiakk.authservice.support.AuthTestDataFactory;
import com.zodiakk.authservice.user.entity.AccountDeletionStatus;
import com.zodiakk.authservice.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Employee account event consumer")
class EmployeeAccountEventConsumerIntegrationTest extends AbstractIntegrationTest {

    private static final String EMPLOYEE_EVENTS_TOPIC = "employee.account.events.v1";

    @Autowired
    private EmployeeAccountEventConsumer consumer;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Nested
    @DisplayName("completeDeletion")
    class CompleteDeletion {

        @Test
        @DisplayName("deletes credentials, tokens and marks the account as deleted")
        void shouldCompleteDeletion() {
            UUID employeeId = UUID.randomUUID();
            User user = persistDeletingUser(employeeId);
            persistRefreshToken(user);
            UUID eventId = UUID.randomUUID();

            consumer.consume(message(eventId, user, employeeId));

            User stored = userRepository.findById(user.getId()).orElseThrow();
            assertThat(stored.getDeletionStatus()).isEqualTo(AccountDeletionStatus.DELETED);
            assertThat(stored.getActive()).isFalse();
            assertThat(authCredentialRepository.findByUserId(user.getId())).isEmpty();
            assertThat(refreshTokenRepository.findAll()).isEmpty();
            assertThat(processedEventRepository.findById(eventId)).isPresent();
        }

        @Test
        @DisplayName("processes the same event only once")
        void shouldBeIdempotent() {
            UUID employeeId = UUID.randomUUID();
            User user = persistDeletingUser(employeeId);
            UUID eventId = UUID.randomUUID();
            String message = message(eventId, user, employeeId);

            consumer.consume(message);
            consumer.consume(message);

            assertThat(processedEventRepository.findAll()).hasSize(1);
            assertThat(userRepository.findById(user.getId()).orElseThrow().getDeletionStatus())
                    .isEqualTo(AccountDeletionStatus.DELETED);
        }

        @Test
        @DisplayName("rejects unsupported event types")
        void shouldRejectUnsupportedEventType() {
            UUID employeeId = UUID.randomUUID();
            User user = persistDeletingUser(employeeId);
            String message = objectMapper.writeValueAsString(new OutboxEventEnvelope(
                    UUID.randomUUID(),
                    KafkaEventConstants.USER_CREATED,
                    KafkaEventConstants.EVENT_VERSION_V1,
                    OffsetDateTime.now(ZoneOffset.UTC),
                    objectMapper.valueToTree(new EmployeeAccountUnlinkedEvent(user.getId(), employeeId))
            ));

            assertThatThrownBy(() -> consumer.consume(message))
                    .isInstanceOf(IllegalArgumentException.class);

            assertThat(userRepository.findById(user.getId()).orElseThrow().getDeletionStatus())
                    .isEqualTo(AccountDeletionStatus.DELETING);
        }

        @Test
        @DisplayName("rejects unsupported event versions")
        void shouldRejectUnsupportedVersion() {
            UUID employeeId = UUID.randomUUID();
            User user = persistDeletingUser(employeeId);
            String message = objectMapper.writeValueAsString(new OutboxEventEnvelope(
                    UUID.randomUUID(),
                    KafkaEventConstants.EMPLOYEE_ACCOUNT_UNLINKED,
                    2,
                    OffsetDateTime.now(ZoneOffset.UTC),
                    objectMapper.valueToTree(new EmployeeAccountUnlinkedEvent(user.getId(), employeeId))
            ));

            assertThatThrownBy(() -> consumer.consume(message))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("rejects a malformed message")
        void shouldRejectMalformedMessage() {
            assertThatThrownBy(() -> consumer.consume("not-json"))
                    .isInstanceOf(tools.jackson.core.JacksonException.class);
        }

        @Test
        @DisplayName("fails when the user does not exist")
        void shouldFailForUnknownUser() {
            UUID employeeId = UUID.randomUUID();
            String message = objectMapper.writeValueAsString(new OutboxEventEnvelope(
                    UUID.randomUUID(),
                    KafkaEventConstants.EMPLOYEE_ACCOUNT_UNLINKED,
                    KafkaEventConstants.EVENT_VERSION_V1,
                    OffsetDateTime.now(ZoneOffset.UTC),
                    objectMapper.valueToTree(new EmployeeAccountUnlinkedEvent(UUID.randomUUID(), employeeId))
            ));

            assertThatThrownBy(() -> consumer.consume(message))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("fails when the employee id does not match")
        void shouldFailOnEmployeeMismatch() {
            UUID employeeId = UUID.randomUUID();
            User user = persistDeletingUser(employeeId);

            assertThatThrownBy(() -> consumer.consume(message(UUID.randomUUID(), user, UUID.randomUUID())))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    @DisplayName("completes a deletion driven by a real Kafka message")
    void shouldConsumeFromKafka() {
        UUID employeeId = UUID.randomUUID();
        User user = persistDeletingUser(employeeId);
        persistRefreshToken(user);
        UUID eventId = UUID.randomUUID();
        String message = message(eventId, user, employeeId);

        kafkaTemplate.send(EMPLOYEE_EVENTS_TOPIC, user.getId().toString(), message);

        awaitDeletion(user.getId(), Duration.ofSeconds(30));

        assertThat(processedEventRepository.findById(eventId)).isPresent();
        assertThat(authCredentialRepository.findByUserId(user.getId())).isEmpty();
    }

    private User persistDeletingUser(UUID employeeId) {
        return persistUser(
                "employee-" + employeeId + "@company.com",
                Role.EMPLOYEE,
                AuthTestDataFactory.RAW_PASSWORD,
                false,
                employeeId,
                false,
                AccountDeletionStatus.DELETING
        );
    }

    private void persistRefreshToken(User user) {
        String token = jwtService.generateRefreshToken(user.getId(), Role.EMPLOYEE, user.getEmail());
        refreshTokenRepository.saveAndFlush(AuthTestDataFactory.refreshToken(
                token,
                user.getId(),
                OffsetDateTime.now(ZoneOffset.UTC).plusDays(1),
                false
        ));
    }

    private String message(UUID eventId, User user, UUID employeeId) {
        return objectMapper.writeValueAsString(new OutboxEventEnvelope(
                eventId,
                KafkaEventConstants.EMPLOYEE_ACCOUNT_UNLINKED,
                KafkaEventConstants.EVENT_VERSION_V1,
                OffsetDateTime.now(ZoneOffset.UTC),
                objectMapper.valueToTree(new EmployeeAccountUnlinkedEvent(user.getId(), employeeId))
        ));
    }

    private void awaitDeletion(UUID userId, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();

        while (System.nanoTime() < deadline) {
            AccountDeletionStatus status = userRepository.findById(userId)
                    .map(User::getDeletionStatus)
                    .orElse(null);

            if (status == AccountDeletionStatus.DELETED) {
                return;
            }

            try {
                Thread.sleep(200);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(ex);
            }
        }

        throw new AssertionError("Account was not deleted in time for user " + userId);
    }
}
