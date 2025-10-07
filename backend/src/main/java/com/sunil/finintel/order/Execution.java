package com.sunil.finintel.order;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

// A fill of an order. Immutable once written.
@Entity
@Table(name = "executions")
public class Execution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, updatable = false)
    private Long orderId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(nullable = false, length = 10, updatable = false)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 4, updatable = false)
    private OrderSide side;

    @Column(nullable = false, updatable = false)
    private long quantity;

    @Column(nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal price;

    @Column(name = "executed_at", nullable = false, updatable = false)
    private Instant executedAt;

    // Required by JPA
    protected Execution() {
    }

    public Execution(Order order, BigDecimal price) {
        this.orderId = order.getId();
        this.userId = order.getUserId();
        this.symbol = order.getSymbol();
        this.side = order.getSide();
        this.quantity = order.getQuantity();
        this.price = price;
    }

    @PrePersist
    void onCreate() {
        executedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getOrderId() { return orderId; }
    public Long getUserId() { return userId; }
    public String getSymbol() { return symbol; }
    public OrderSide getSide() { return side; }
    public long getQuantity() { return quantity; }
    public BigDecimal getPrice() { return price; }
    public Instant getExecutedAt() { return executedAt; }
}
