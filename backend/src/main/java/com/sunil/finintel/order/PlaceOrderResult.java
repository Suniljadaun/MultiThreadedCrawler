package com.sunil.finintel.order;

// created = true for a new order (HTTP 201), false for an idempotent replay (HTTP 200)
public record PlaceOrderResult(OrderResponse order, boolean created) {
}
