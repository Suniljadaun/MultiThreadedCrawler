package com.sunil.finintel.messaging;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.json.JsonMapper;

// Stores an event in the outbox. MANDATORY: it must join the caller's transaction,
// so the event is saved if and only if the business change is saved.
@Component
public class OutboxWriter {

    private static final int ENVELOPE_VERSION = 1;

    private final OutboxRepository outboxRepository;
    private final JsonMapper jsonMapper;

    public OutboxWriter(OutboxRepository outboxRepository, JsonMapper jsonMapper) {
        this.outboxRepository = outboxRepository;
        this.jsonMapper = jsonMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public UUID append(String topic, String eventType, String aggregateId, Long userId, Object payload) {
        UUID eventId = UUID.randomUUID();
        EventEnvelope envelope = new EventEnvelope(eventId, eventType, aggregateId, userId, Instant.now(),
                ENVELOPE_VERSION, jsonMapper.valueToTree(payload));
        // Key = userId, so all events of one user land on the same partition in order
        outboxRepository.save(new OutboxEvent(eventId, topic, String.valueOf(userId), eventType, aggregateId,
                jsonMapper.writeValueAsString(envelope)));
        return eventId;
    }
}
