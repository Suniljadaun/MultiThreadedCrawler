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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

// Named "orders" in the DB because ORDER is an SQL keyword
@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(nullable = false, length = 10, updatable = false)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 4, updatable = false)
    private OrderSide side;

    @Column(nullable = false, updatable = false)
    private long quantity;

    @Column(name = "requested_price", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal requestedPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(name = "idempotency_key", nullable = false, length = 100, updatable = false)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64, updatable = false, columnDefinition = "bpchar(64)")
    private String requestHash;

    // Incremented on every update; a stale write fails instead of overwriting
    @Version
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Required by JPA
    protected Order() {
    }

    public Order(Long userId, String symbol, OrderSide side, long quantity, BigDecimal requestedPrice,
                 String idempotencyKey, String requestHash) {
        this.userId = userId;
        this.symbol = symbol;
        this.side = side;
        this.quantity = quantity;
        this.requestedPrice = requestedPrice;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.status = OrderStatus.CREATED;
    }

    // The only way to change status; illegal moves are rejected
    public void moveTo(OrderStatus next) {
        if (!status.canMoveTo(next)) {
            throw new IllegalStateException("order " + id + " cannot move from " + status + " to " + next);
        }
        this.status = next;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getSymbol() { return symbol; }
    public OrderSide getSide() { return side; }
    public long getQuantity() { return quantity; }
    public BigDecimal getRequestedPrice() { return requestedPrice; }
    public OrderStatus getStatus() { return status; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getRequestHash() { return requestHash; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
