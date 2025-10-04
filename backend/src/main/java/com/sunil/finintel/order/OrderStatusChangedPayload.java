package com.sunil.finintel.order;

// Payload of OrderValidated / OrderRejected events. reason is null when validated.
public record OrderStatusChangedPayload(Long orderId, OrderStatus status, String reason) {
}
