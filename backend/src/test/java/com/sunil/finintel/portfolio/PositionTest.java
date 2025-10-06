package com.sunil.finintel.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class PositionTest {

    @Test
    void buyingTwiceAveragesTheCost() {
        Position p = new Position(1L, "ACME");
        p.buy(10, new BigDecimal("100"));
        p.buy(10, new BigDecimal("110"));

        assertThat(p.getQuantity()).isEqualTo(20);
        assertThat(p.getAvgCost()).isEqualByComparingTo("105.0000");
    }

    @Test
    void averageIsRoundedToFourDecimals() {
        Position p = new Position(1L, "ACME");
        p.buy(3, new BigDecimal("10"));
        p.buy(1, new BigDecimal("11"));      // (30 + 11) / 4 = 10.25
        p.buy(2, new BigDecimal("10.01"));   // (41 + 20.02) / 6 = 10.17

        assertThat(p.getAvgCost()).isEqualByComparingTo("10.1700");
        assertThat(p.getAvgCost().scale()).isEqualTo(4);
    }

    @Test
    void sellingKeepsAverageCost() {
        Position p = new Position(1L, "ACME");
        p.buy(10, new BigDecimal("100"));
        p.sell(4);

        assertThat(p.getQuantity()).isEqualTo(6);
        assertThat(p.getAvgCost()).isEqualByComparingTo("100");
    }

    @Test
    void sellingEverythingResetsAverageCost() {
        Position p = new Position(1L, "ACME");
        p.buy(5, new BigDecimal("100"));
        p.sell(5);

        assertThat(p.getQuantity()).isZero();
        assertThat(p.getAvgCost()).isEqualByComparingTo("0");
    }

    @Test
    void cannotSellMoreThanHeld() {
        Position p = new Position(1L, "ACME");
        p.buy(2, new BigDecimal("100"));

        assertThatThrownBy(() -> p.sell(3)).isInstanceOf(IllegalStateException.class);
        assertThat(p.getQuantity()).isEqualTo(2);
    }
}
