package com.sunil.finintel.portfolio;

import java.math.BigDecimal;

public record PositionView(
        String symbol,
        long quantity,
        BigDecimal avgCost,
        BigDecimal marketPrice,
        BigDecimal marketValue,
        BigDecimal unrealizedPnl) {
}
