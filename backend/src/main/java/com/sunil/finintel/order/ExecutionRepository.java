package com.sunil.finintel.order;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExecutionRepository extends JpaRepository<Execution, Long> {

    Optional<Execution> findByOrderId(Long orderId);

    // Backed by index ix_executions_user_executed
    Page<Execution> findByUserId(Long userId, Pageable pageable);
}
