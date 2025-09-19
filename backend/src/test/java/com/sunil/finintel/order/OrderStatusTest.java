package com.sunil.finintel.order;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class OrderStatusTest {

    @ParameterizedTest
    @CsvSource({
            "CREATED, VALIDATED",
            "CREATED, REJECTED",
            "CREATED, CANCELLED",
            "VALIDATED, EXECUTED",
            "VALIDATED, REJECTED",
            "VALIDATED, CANCELLED"
    })
    void allowedTransitions(OrderStatus from, OrderStatus to) {
        assertThat(from.canMoveTo(to)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
            "CREATED, EXECUTED",    // must be validated first
            "CREATED, CREATED",
            "VALIDATED, CREATED",   // no going back
            "VALIDATED, VALIDATED"
    })
    void forbiddenTransitions(OrderStatus from, OrderStatus to) {
        assertThat(from.canMoveTo(to)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"EXECUTED", "REJECTED", "CANCELLED"})
    void finalStatesAllowNothing(OrderStatus status) {
        assertThat(status.isFinal()).isTrue();
        for (OrderStatus next : OrderStatus.values()) {
            assertThat(status.canMoveTo(next)).isFalse();
        }
    }

    @Test
    void openStatesAreNotFinal() {
        assertThat(OrderStatus.CREATED.isFinal()).isFalse();
        assertThat(OrderStatus.VALIDATED.isFinal()).isFalse();
    }
}
