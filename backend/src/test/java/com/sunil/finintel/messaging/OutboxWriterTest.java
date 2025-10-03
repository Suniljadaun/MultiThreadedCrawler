package com.sunil.finintel.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class OutboxWriterTest {

    @Mock
    private OutboxRepository outboxRepository;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void storesEnvelopeKeyedByUser() {
        OutboxWriter writer = new OutboxWriter(outboxRepository, jsonMapper);

        UUID eventId = writer.append("orders.created", "OrderCreated", "7", 42L, Map.of("orderId", 7));

        ArgumentCaptor<OutboxEvent> saved = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(saved.capture());
        OutboxEvent row = saved.getValue();
        assertThat(row.getEventId()).isEqualTo(eventId);
        assertThat(row.getTopic()).isEqualTo("orders.created");
        assertThat(row.getMessageKey()).isEqualTo("42");
        assertThat(row.getPublishedAt()).isNull();

        // The stored JSON must be readable by the consumer side
        EventEnvelope envelope = new EventParser(jsonMapper).parse(row.getPayload());
        assertThat(envelope.eventId()).isEqualTo(eventId);
        assertThat(envelope.eventType()).isEqualTo("OrderCreated");
        assertThat(envelope.userId()).isEqualTo(42L);
        assertThat(envelope.version()).isEqualTo(1);
        assertThat(envelope.payload().get("orderId").asInt()).isEqualTo(7);
    }
}
