package com.sunil.finintel.order;

import java.math.BigDecimal;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

// Request body for POST /api/v1/orders
public record PlaceOrderRequest(
        @NotNull @Positive Long userId,
        @NotBlank @Pattern(regexp = "\\s*[A-Za-z]{1,10}\\s*", message = "must be 1-10 letters") String symbol,
        @NotNull OrderSide side,
        @Positive @Max(1_000_000) long quantity,
        @NotNull @Positive @Digits(integer = 15, fraction = 4) BigDecimal price) {
}
