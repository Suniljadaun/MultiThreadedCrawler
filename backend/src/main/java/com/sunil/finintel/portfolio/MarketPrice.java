package com.sunil.finintel.portfolio;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "market_prices")
public class MarketPrice {

    @Id
    @Column(length = 10)
    private String symbol;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal price;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Required by JPA
    protected MarketPrice() {
    }

    public MarketPrice(String symbol, BigDecimal price) {
        this.symbol = symbol;
        this.price = price;
        this.updatedAt = Instant.now();
    }

    public void changePrice(BigDecimal newPrice) {
        this.price = newPrice;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public String getSymbol() { return symbol; }
    public BigDecimal getPrice() { return price; }
    public Instant getUpdatedAt() { return updatedAt; }
}
