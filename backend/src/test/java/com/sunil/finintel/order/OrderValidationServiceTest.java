package com.sunil.finintel.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sunil.finintel.messaging.EventEnvelope;
import com.sunil.finintel.messaging.OutboxWriter;
import com.sunil.finintel.messaging.ProcessedEvents;
import com.sunil.finintel.messaging.Topics;

@ExtendWith(MockitoExtension.class)
class OrderValidationServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProcessedEvents processedEvents;

    @Mock
    private OutboxWriter outboxWriter;

    private OrderValidationService service;

    private final UUID eventId = UUID.randomUUID();
    private final EventEnvelope event =
            new EventEnvelope(eventId, "OrderCreated", "1", 1L, Instant.now(), 1, null, null);

    @BeforeEach
    void setUp() {
        service = new OrderValidationService(orderRepository, processedEvents, outboxWriter, Set.of("acme", " GLOBEX "));
    }

    private Order order(String symbol) {
        return new Order(1L, symbol, OrderSide.BUY, 10, BigDecimal.TEN, "k", "h");
    }

    @Test
    void supportedSymbolIsValidated() {
        Order order = order("ACME");
        when(processedEvents.markProcessed(OrderValidationService.CONSUMER_NAME, eventId)).thenReturn(true);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        service.handleOrderCreated(event);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.VALIDATED);
        verify(outboxWriter).append(eq(Topics.ORDERS_VALIDATED), eq("OrderValidated"), eq("1"), eq(1L), any());
    }

    @Test
    void unsupportedSymbolIsRejectedWithReason() {
        Order order = order("ZZZ");
        when(processedEvents.markProcessed(OrderValidationService.CONSUMER_NAME, eventId)).thenReturn(true);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        service.handleOrderCreated(event);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.REJECTED);
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(outboxWriter).append(eq(Topics.ORDERS_REJECTED), eq("OrderRejected"), anyString(), eq(1L),
                payload.capture());
        assertThat(((OrderStatusChangedPayload) payload.getValue()).reason()).contains("ZZZ");
    }

    @Test
    void duplicateEventChangesNothing() {
        when(processedEvents.markProcessed(OrderValidationService.CONSUMER_NAME, eventId)).thenReturn(false);

        service.handleOrderCreated(event);

        verifyNoInteractions(orderRepository, outboxWriter);
    }

    @Test
    void cancelledOrderIsLeftAlone() {
        Order order = order("ACME");
        order.moveTo(OrderStatus.CANCELLED);
        when(processedEvents.markProcessed(OrderValidationService.CONSUMER_NAME, eventId)).thenReturn(true);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        service.handleOrderCreated(event);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verifyNoInteractions(outboxWriter);
    }
}
