package com.sunil.finintel.order;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sunil.finintel.messaging.EventEnvelope;
import com.sunil.finintel.messaging.OutboxWriter;
import com.sunil.finintel.messaging.ProcessedEvents;
import com.sunil.finintel.messaging.Topics;

// Handles OrderCreated: CREATED -> VALIDATED or REJECTED.
// One transaction covers: dedupe row + status change + outgoing event.
@Service
public class OrderValidationService {

    static final String CONSUMER_NAME = "order-validation";

    private static final Logger log = LoggerFactory.getLogger(OrderValidationService.class);

    private final OrderRepository orderRepository;
    private final ProcessedEvents processedEvents;
    private final OutboxWriter outboxWriter;
    private final Set<String> supportedSymbols;

    public OrderValidationService(OrderRepository orderRepository,
                                  ProcessedEvents processedEvents,
                                  OutboxWriter outboxWriter,
                                  @Value("${app.orders.supported-symbols}") Set<String> supportedSymbols) {
        this.orderRepository = orderRepository;
        this.processedEvents = processedEvents;
        this.outboxWriter = outboxWriter;
        this.supportedSymbols = supportedSymbols.stream()
                .map(s -> s.trim().toUpperCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    @Transactional
    public void handleOrderCreated(EventEnvelope event) {
        if (!processedEvents.markProcessed(CONSUMER_NAME, event.eventId())) {
            log.info("Skipping duplicate event {}", event.eventId());
            return;
        }
        Long orderId = Long.valueOf(event.aggregateId());
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            log.warn("Order {} from event {} not found, ignoring", orderId, event.eventId());
            return;
        }
        if (order.getStatus() != OrderStatus.CREATED) {
            // e.g. cancelled before validation ran
            log.info("Order {} is {}, nothing to validate", orderId, order.getStatus());
            return;
        }

        String reason = rejectReason(order);
        if (reason == null) {
            order.moveTo(OrderStatus.VALIDATED);
            outboxWriter.append(Topics.ORDERS_VALIDATED, "OrderValidated", String.valueOf(orderId),
                    order.getUserId(), new OrderStatusChangedPayload(orderId, OrderStatus.VALIDATED, null));
        } else {
            order.moveTo(OrderStatus.REJECTED);
            outboxWriter.append(Topics.ORDERS_REJECTED, "OrderRejected", String.valueOf(orderId),
                    order.getUserId(), new OrderStatusChangedPayload(orderId, OrderStatus.REJECTED, reason));
            log.info("Order {} rejected: {}", orderId, reason);
        }
    }

    // null = valid. SELL position checks come with the portfolio (Phase 4, see A-012).
    private String rejectReason(Order order) {
        if (!supportedSymbols.contains(order.getSymbol())) {
            return "unsupported symbol " + order.getSymbol();
        }
        return null;
    }
}
