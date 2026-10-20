package com.zodiakk.authservice.outbox.publisher;

import com.zodiakk.authservice.outbox.config.OutboxProperties;
import com.zodiakk.authservice.outbox.entity.OutboxEvent;
import com.zodiakk.authservice.outbox.service.OutboxClaimService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisher {

    private final OutboxClaimService claimService;

    private final KafkaTemplate<String, String> kafkaTemplate;

    private final OutboxProperties properties;

    @Value("${app.kafka.topics.user-events}")
    private String userEventsTopic;

    @Scheduled(fixedDelayString = "${app.outbox.poll-delay-ms}")
    public void publishPendingEvents() {
        var events = claimService.claimBatch();

        for (OutboxEvent event : events) {
            publish(event);
        }
    }

    private void publish(OutboxEvent event) {
        try {
            kafkaTemplate.send(
                    userEventsTopic,
                    event.getAggregateId().toString(),
                    event.getPayload()
            ).get(
                    properties.sendTimeoutSeconds(),
                    TimeUnit.SECONDS
            );

            boolean updated = claimService.markPublished(
                    event.getId(),
                    event.getAttempts()
            );

            if (!updated) {
                log.warn(
                        "Outbox event {} was published but its claim expired",
                        event.getId()
                );
            } else {
                log.info(
                        "Published outbox event: id={}, type={}",
                        event.getId(),
                        event.getEventType()
                );
            }

        } catch (Exception ex) {
            log.error(
                    "Failed to publish outbox event: id={}, attempt={}",
                    event.getId(),
                    event.getAttempts(),
                    ex
            );

            claimService.markFailed(
                    event.getId(),
                    event.getAttempts()
            );

            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
