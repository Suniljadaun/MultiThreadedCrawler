package com.sunil.finintel.order;

import java.math.BigDecimal;

// Payload of the OrderCreated event
public record OrderCreatedPayload(
        Long orderId,
        Long userId,
        String symbol,
        OrderSide side,
        long quantity,
        BigDecimal requestedPrice) {

    static OrderCreatedPayload from(Order order) {
        return new OrderCreatedPayload(order.getId(), order.getUserId(), order.getSymbol(), order.getSide(),
                order.getQuantity(), order.getRequestedPrice());
    }
}
