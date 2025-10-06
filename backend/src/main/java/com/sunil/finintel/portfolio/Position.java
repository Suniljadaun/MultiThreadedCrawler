package com.sunil.finintel.portfolio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "positions")
public class Position {

    private static final int MONEY_SCALE = 4;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(nullable = false, length = 10, updatable = false)
    private String symbol;

    @Column(nullable = false)
    private long quantity;

    @Column(name = "avg_cost", nullable = false, precision = 19, scale = 4)
    private BigDecimal avgCost;

    @Version
    private long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Required by JPA
    protected Position() {
    }

    public Position(Long userId, String symbol) {
        this.userId = userId;
        this.symbol = symbol;
        this.quantity = 0;
        this.avgCost = BigDecimal.ZERO.setScale(MONEY_SCALE);
    }

    // New average cost = (old cost + new cost) / new quantity
    public void buy(long qty, BigDecimal price) {
        BigDecimal totalCost = avgCost.multiply(BigDecimal.valueOf(quantity))
                .add(price.multiply(BigDecimal.valueOf(qty)));
        quantity += qty;
        avgCost = totalCost.divide(BigDecimal.valueOf(quantity), MONEY_SCALE, RoundingMode.HALF_EVEN);
    }

    // Selling does not change the average cost of the shares that remain
    public void sell(long qty) {
        if (qty > quantity) {
            throw new IllegalStateException("cannot sell " + qty + " " + symbol + ", holding " + quantity);
        }
        quantity -= qty;
        if (quantity == 0) {
            avgCost = BigDecimal.ZERO.setScale(MONEY_SCALE);
        }
    }

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getSymbol() { return symbol; }
    public long getQuantity() { return quantity; }
    public BigDecimal getAvgCost() { return avgCost; }
    public long getVersion() { return version; }
    public Instant getUpdatedAt() { return updatedAt; }
}
