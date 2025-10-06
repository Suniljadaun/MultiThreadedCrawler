package com.sunil.finintel.portfolio;

import java.math.BigDecimal;

// Payload of the PositionUpdated event
public record PositionUpdatedPayload(Long userId, String symbol, long quantity, BigDecimal avgCost) {

    public static PositionUpdatedPayload from(Position p) {
        return new PositionUpdatedPayload(p.getUserId(), p.getSymbol(), p.getQuantity(), p.getAvgCost());
    }
}
