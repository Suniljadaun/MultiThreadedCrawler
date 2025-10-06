package com.sunil.finintel.portfolio;

// Outcome of applying a fill to a position. applied = false means not enough shares to sell.
public record FillResult(boolean applied, long heldQuantity, Position position) {

    static FillResult applied(Position position) {
        return new FillResult(true, position.getQuantity(), position);
    }

    static FillResult insufficient(long heldQuantity) {
        return new FillResult(false, heldQuantity, null);
    }
}
