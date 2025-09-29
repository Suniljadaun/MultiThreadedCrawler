package com.sunil.finintel.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.OptimisticLockingFailureException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.sunil.finintel.common.ConflictException;
import com.sunil.finintel.user.User;
import com.sunil.finintel.user.UserRepository;

// Status changes against a real PostgreSQL: cancel rules and optimistic locking
@SpringBootTest
@Testcontainers
class OrderLifecycleIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private UserRepository userRepository;

    private Long orderId;

    @BeforeEach
    void setUp() {
        Long userId = userRepository.save(new User("Sunil", "sunil@example.com")).getId();
        orderId = orderService.place("k1",
                new PlaceOrderRequest(userId, "ACME", OrderSide.BUY, 10, new BigDecimal("101.50"))).order().id();
    }

    @AfterEach
    void cleanUp() {
        orderRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void cancelPersistsCancelledStatus() {
        orderService.cancel(orderId);

        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void executedOrderCannotBeCancelled() {
        Order order = orderRepository.findById(orderId).orElseThrow();
        order.moveTo(OrderStatus.VALIDATED);
        order.moveTo(OrderStatus.EXECUTED);
        orderRepository.saveAndFlush(order);

        assertThatThrownBy(() -> orderService.cancel(orderId)).isInstanceOf(ConflictException.class);
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.EXECUTED);
    }

    @Test
    void staleUpdateIsRejectedByVersionCheck() {
        // Two readers load the same row (version 0)
        Order first = orderRepository.findById(orderId).orElseThrow();
        Order second = orderRepository.findById(orderId).orElseThrow();

        // First writer wins and bumps the version to 1
        first.moveTo(OrderStatus.CANCELLED);
        orderRepository.saveAndFlush(first);

        // Second writer still holds version 0, so its update must fail instead of overwriting
        second.moveTo(OrderStatus.VALIDATED);
        assertThatThrownBy(() -> orderRepository.saveAndFlush(second))
                .isInstanceOf(OptimisticLockingFailureException.class);
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }
}
