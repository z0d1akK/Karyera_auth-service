package com.zodiakk.authservice.outbox.entity;

public enum OutboxEventStatus {
    PENDING,
    PROCESSING,
    PUBLISHED
}