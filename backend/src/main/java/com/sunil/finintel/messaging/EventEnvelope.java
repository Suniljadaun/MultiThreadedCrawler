package com.sunil.finintel.messaging;

import java.time.Instant;
import java.util.UUID;

import tools.jackson.databind.JsonNode;

// Common wrapper for every event (see docs/kafka.md)
public record EventEnvelope(
        UUID eventId,
        String eventType,
        String aggregateId,
        Long userId,
        Instant occurredAt,
        int version,
        JsonNode payload) {
}
