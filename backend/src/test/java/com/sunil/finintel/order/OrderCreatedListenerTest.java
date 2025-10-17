package com.sunil.finintel.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import java.util.concurrent.atomic.AtomicReference;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import com.sunil.finintel.common.RequestIds;
import com.sunil.finintel.messaging.EventParser;

import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class OrderCreatedListenerTest {

    @Mock
    private OrderValidationService validationService;

    private OrderCreatedListener listener() {
        return new OrderCreatedListener(new EventParser(JsonMapper.builder().build()), validationService);
    }

    private static ConsumerRecord<String, String> record(String requestIdJson) {
        String json = """
                {"eventId":"8a2d0c1e-1111-4222-8333-444455556666","eventType":"OrderCreated",
                 "aggregateId":"7","userId":1,"occurredAt":"2026-01-01T10:00:00Z","version":1,
                 "payload":{"orderId":7},"requestId":%s}
                """.formatted(requestIdJson);
        return new ConsumerRecord<>("orders.created", 0, 0L, "1", json);
    }

    @Test
    void handlesEventWithRequestIdOfOriginalRequest() {
        AtomicReference<String> seen = new AtomicReference<>();
        doAnswer(inv -> {
            seen.set(MDC.get(RequestIds.MDC_KEY));
            return null;
        }).when(validationService).handleOrderCreated(any());

        listener().onMessage(record("\"req-1\""));

        assertThat(seen.get()).isEqualTo("req-1");
        assertThat(MDC.get(RequestIds.MDC_KEY)).isNull();
    }

    @Test
    void eventWithoutRequestIdIsStillHandled() {
        AtomicReference<String> seen = new AtomicReference<>("unset");
        doAnswer(inv -> {
            seen.set(MDC.get(RequestIds.MDC_KEY));
            return null;
        }).when(validationService).handleOrderCreated(any());

        listener().onMessage(record("null"));

        assertThat(seen.get()).isNull();
    }
}
