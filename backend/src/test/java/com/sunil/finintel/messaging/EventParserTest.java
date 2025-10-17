package com.sunil.finintel.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

class EventParserTest {

    private final EventParser parser = new EventParser(JsonMapper.builder().build());

    @Test
    void parsesValidEnvelope() {
        String json = """
                {"eventId":"8a2d0c1e-1111-4222-8333-444455556666","eventType":"OrderCreated",
                 "aggregateId":"7","userId":1,"occurredAt":"2026-01-01T10:00:00Z","version":1,
                 "payload":{"orderId":7}}
                """;

        EventEnvelope event = parser.parse(json);

        assertThat(event.eventType()).isEqualTo("OrderCreated");
        assertThat(event.aggregateId()).isEqualTo("7");
        assertThat(event.payload().get("orderId").asLong()).isEqualTo(7L);
        // Events written before request ids existed still parse
        assertThat(event.requestId()).isNull();
    }

    @Test
    void readsRequestId() {
        String json = """
                {"eventId":"8a2d0c1e-1111-4222-8333-444455556666","eventType":"OrderCreated",
                 "aggregateId":"7","userId":1,"occurredAt":"2026-01-01T10:00:00Z","version":1,
                 "payload":{"orderId":7},"requestId":"req-9"}
                """;

        assertThat(parser.parse(json).requestId()).isEqualTo("req-9");
    }

    @Test
    void rejectsNonJson() {
        assertThatThrownBy(() -> parser.parse("this is not json"))
                .isInstanceOf(MalformedEventException.class);
    }

    @Test
    void rejectsEnvelopeWithoutEventId() {
        assertThatThrownBy(() -> parser.parse("{\"eventType\":\"OrderCreated\",\"aggregateId\":\"7\"}"))
                .isInstanceOf(MalformedEventException.class);
    }

    @Test
    void rejectsEmptyMessage() {
        assertThatThrownBy(() -> parser.parse("  ")).isInstanceOf(MalformedEventException.class);
    }
}
