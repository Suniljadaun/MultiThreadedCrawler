package com.sunil.finintel.order;

import java.math.BigDecimal;
import java.time.Instant;

// One row of a user's trade history
public record ExecutionView(
        Long id,
        Long orderId,
        String symbol,
        OrderSide side,
        long quantity,
        BigDecimal price,
        Instant executedAt) {

    static ExecutionView from(Execution e) {
        return new ExecutionView(e.getId(), e.getOrderId(), e.getSymbol(), e.getSide(), e.getQuantity(),
                e.getPrice(), e.getExecutedAt());
    }
}
