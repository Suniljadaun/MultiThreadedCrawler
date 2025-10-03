package com.sunil.finintel.messaging;

import org.springframework.stereotype.Component;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Component
public class EventParser {

    private final JsonMapper jsonMapper;

    public EventParser(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public EventEnvelope parse(String json) {
        if (json == null || json.isBlank()) {
            throw new MalformedEventException("empty message");
        }
        EventEnvelope event;
        try {
            event = jsonMapper.readValue(json, EventEnvelope.class);
        } catch (JacksonException e) {
            throw new MalformedEventException("message is not a valid event envelope", e);
        }
        if (event.eventId() == null || event.eventType() == null || event.aggregateId() == null) {
            throw new MalformedEventException("event envelope is missing eventId, eventType or aggregateId");
        }
        return event;
    }
}
