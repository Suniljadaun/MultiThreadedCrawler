package com.sunil.finintel.order;

import java.math.BigDecimal;
import java.time.Instant;

// API view of an order. Keeps the JPA entity out of the public contract.
public record OrderResponse(
        Long id,
        Long userId,
        String symbol,
        OrderSide side,
        long quantity,
        BigDecimal requestedPrice,
        OrderStatus status,
        Instant createdAt,
        Instant updatedAt) {

    static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getUserId(),
                order.getSymbol(),
                order.getSide(),
                order.getQuantity(),
                order.getRequestedPrice(),
                order.getStatus(),
                order.getCreatedAt(),
                order.getUpdatedAt());
    }
}
