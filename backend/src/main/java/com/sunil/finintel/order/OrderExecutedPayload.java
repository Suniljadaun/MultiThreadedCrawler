package com.sunil.finintel.order;

import java.math.BigDecimal;
import java.time.Instant;

// Payload of the OrderExecuted event
public record OrderExecutedPayload(
        Long orderId,
        Long executionId,
        Long userId,
        String symbol,
        OrderSide side,
        long quantity,
        BigDecimal price,
        Instant executedAt) {

    static OrderExecutedPayload from(Execution e) {
        return new OrderExecutedPayload(e.getOrderId(), e.getId(), e.getUserId(), e.getSymbol(), e.getSide(),
                e.getQuantity(), e.getPrice(), e.getExecutedAt());
    }
}
