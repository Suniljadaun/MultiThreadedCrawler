package com.sunil.finintel.order;

import java.util.EnumSet;
import java.util.Set;

// Order state machine:
//   CREATED   -> VALIDATED | REJECTED | CANCELLED
//   VALIDATED -> EXECUTED  | REJECTED | CANCELLED
//   EXECUTED, REJECTED, CANCELLED are final
public enum OrderStatus {
    CREATED,
    VALIDATED,
    EXECUTED,
    REJECTED,
    CANCELLED;

    public Set<OrderStatus> allowedNext() {
        return switch (this) {
            case CREATED -> EnumSet.of(VALIDATED, REJECTED, CANCELLED);
            case VALIDATED -> EnumSet.of(EXECUTED, REJECTED, CANCELLED);
            case EXECUTED, REJECTED, CANCELLED -> EnumSet.noneOf(OrderStatus.class);
        };
    }

    public boolean canMoveTo(OrderStatus next) {
        return allowedNext().contains(next);
    }

    public boolean isFinal() {
        return allowedNext().isEmpty();
    }
}
