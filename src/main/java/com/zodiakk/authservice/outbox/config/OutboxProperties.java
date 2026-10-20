package com.zodiakk.authservice.outbox.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.outbox")
public record OutboxProperties(
        int batchSize,
        long pollDelayMs,
        long leaseSeconds,
        long sendTimeoutSeconds
) {
}