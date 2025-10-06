package com.sunil.finintel.portfolio;

import java.math.BigDecimal;
import java.time.Instant;

public record MarketPriceResponse(String symbol, BigDecimal price, Instant updatedAt) {

    static MarketPriceResponse from(MarketPrice p) {
        return new MarketPriceResponse(p.getSymbol(), p.getPrice(), p.getUpdatedAt());
    }
}
