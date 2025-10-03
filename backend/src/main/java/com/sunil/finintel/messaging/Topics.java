package com.sunil.finintel.messaging;

// Kafka topic names (see docs/kafka.md)
public final class Topics {

    public static final String ORDERS_CREATED = "orders.created";
    public static final String ORDERS_CREATED_DLT = "orders.created.DLT";
    public static final String ORDERS_VALIDATED = "orders.validated";
    public static final String ORDERS_REJECTED = "orders.rejected";

    private Topics() {
    }
}
