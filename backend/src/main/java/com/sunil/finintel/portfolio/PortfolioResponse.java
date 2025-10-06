package com.sunil.finintel.portfolio;

import java.math.BigDecimal;
import java.util.List;

public record PortfolioResponse(
        Long userId,
        List<PositionView> positions,
        BigDecimal totalCost,
        BigDecimal totalMarketValue,
        BigDecimal totalUnrealizedPnl) {
}
