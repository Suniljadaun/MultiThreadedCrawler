package com.sunil.finintel.order;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {

    // Backed by the unique constraint uq_orders_user_idempotency
    Optional<Order> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);

    // Backed by index ix_orders_user_created
    Page<Order> findByUserId(Long userId, Pageable pageable);
}
