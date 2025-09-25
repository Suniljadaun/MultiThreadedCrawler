package com.sunil.finintel.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.sunil.finintel.common.UnprocessableException;
import com.sunil.finintel.user.User;
import com.sunil.finintel.user.UserRepository;

// Idempotency against a real PostgreSQL, including concurrent duplicate requests
@SpringBootTest
@Testcontainers
class OrderIdempotencyIT {

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

    private Long userId;

    @BeforeEach
    void setUp() {
        userId = userRepository.save(new User("Sunil", "sunil@example.com")).getId();
    }

    @AfterEach
    void cleanUp() {
        orderRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void concurrentRequestsWithSameKeyCreateExactlyOneOrder() throws Exception {
        PlaceOrderRequest request = new PlaceOrderRequest(userId, "ACME", OrderSide.BUY, 10, new BigDecimal("101.50"));
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);

        List<Future<PlaceOrderResult>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await();   // release all threads at the same moment
                return orderService.place("same-key", request);
            }));
        }
        start.countDown();

        List<PlaceOrderResult> results = new ArrayList<>();
        for (Future<PlaceOrderResult> future : futures) {
            results.add(future.get(30, TimeUnit.SECONDS));
        }
        pool.shutdown();

        Long firstId = results.get(0).order().id();
        assertThat(results).allMatch(r -> r.order().id().equals(firstId));
        assertThat(results).filteredOn(PlaceOrderResult::created).hasSize(1);
        assertThat(orderRepository.count()).isEqualTo(1);
    }

    @Test
    void sameKeyWithDifferentBodyIsRejected() {
        orderService.place("k1", new PlaceOrderRequest(userId, "ACME", OrderSide.BUY, 10, new BigDecimal("101.50")));

        assertThatThrownBy(() -> orderService.place("k1",
                new PlaceOrderRequest(userId, "ACME", OrderSide.SELL, 10, new BigDecimal("101.50"))))
                .isInstanceOf(UnprocessableException.class);
        assertThat(orderRepository.count()).isEqualTo(1);
    }

    @Test
    void keysAreScopedPerUser() {
        Long otherUser = userRepository.save(new User("Other", "other@example.com")).getId();

        orderService.place("shared-key", new PlaceOrderRequest(userId, "ACME", OrderSide.BUY, 1, BigDecimal.TEN));
        orderService.place("shared-key", new PlaceOrderRequest(otherUser, "ACME", OrderSide.BUY, 1, BigDecimal.TEN));

        assertThat(orderRepository.count()).isEqualTo(2);
    }
}
