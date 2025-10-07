package com.sunil.finintel.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.sunil.finintel.messaging.OutboxEvent;
import com.sunil.finintel.messaging.OutboxRepository;
import com.sunil.finintel.messaging.Topics;
import com.sunil.finintel.portfolio.PortfolioResponse;
import com.sunil.finintel.portfolio.PortfolioService;
import com.sunil.finintel.user.User;
import com.sunil.finintel.user.UserRepository;

// Full flow on real PostgreSQL + Kafka:
// POST order -> outbox -> orders.created -> validation -> orders.validated -> execution + position
// Seeded market price for ACME is 100.0000 (V4 migration).
@SpringBootTest(properties = {
        "app.outbox.publisher.enabled=true",
        "app.outbox.publisher.interval-ms=200",
        "spring.kafka.listener.auto-startup=true",
        "spring.kafka.admin.auto-create=true"
})
@Testcontainers
// Close this Spring context after the class: otherwise the cached context keeps its scheduler,
// Kafka producer and consumers running against containers that are already stopped
@DirtiesContext
class KafkaOrderFlowIT {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer("apache/kafka:4.0.0");

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private PortfolioService portfolioService;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private JdbcClient jdbcClient;

    private Long userId;

    @BeforeEach
    void setUp() {
        userId = userRepository.save(new User("Sunil", "sunil-" + UUID.randomUUID() + "@example.com")).getId();
    }

    @AfterEach
    void cleanUp() {
        // Children before parents (foreign keys)
        jdbcClient.sql("DELETE FROM executions").update();
        jdbcClient.sql("DELETE FROM positions").update();
        jdbcClient.sql("DELETE FROM outbox_events").update();
        jdbcClient.sql("DELETE FROM processed_events").update();
        jdbcClient.sql("DELETE FROM orders").update();
        jdbcClient.sql("DELETE FROM users").update();
    }

    private Long placeOrder(String key, String symbol, OrderSide side, long qty, String limit) {
        return orderService.place(key, new PlaceOrderRequest(userId, symbol, side, qty, new BigDecimal(limit)))
                .order().id();
    }

    private void awaitStatus(Long orderId, OrderStatus expected) throws InterruptedException {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        OrderStatus current = null;
        while (System.nanoTime() < deadline) {
            current = orderRepository.findById(orderId).orElseThrow().getStatus();
            if (current == expected) {
                return;
            }
            Thread.sleep(200);
        }
        fail("order " + orderId + " expected " + expected + " but was " + current);
    }

    private long heldQuantity(String symbol) {
        PortfolioResponse portfolio = portfolioService.getPortfolio(userId);
        return portfolio.positions().stream()
                .filter(p -> p.symbol().equals(symbol))
                .mapToLong(p -> p.quantity())
                .sum();
    }

    @Test
    void buyIsValidatedExecutedAndOpensPosition() throws Exception {
        Long orderId = placeOrder("k1", "ACME", OrderSide.BUY, 10, "101.50");

        awaitStatus(orderId, OrderStatus.EXECUTED);

        assertThat(heldQuantity("ACME")).isEqualTo(10);
        assertThat(portfolioService.getPortfolio(userId).positions().get(0).avgCost()).isEqualByComparingTo("100");
        assertThat(outboxRepository.countByEventTypeAndAggregateId("OrderValidated", orderId.toString())).isEqualTo(1);
        assertThat(outboxRepository.countByEventTypeAndAggregateId("OrderExecuted", orderId.toString())).isEqualTo(1);
        OutboxEvent created = outboxRepository.findByEventTypeAndAggregateId("OrderCreated", orderId.toString())
                .orElseThrow();
        assertThat(created.getPublishedAt()).isNotNull();
    }

    @Test
    void unsupportedSymbolIsRejectedAtValidation() throws Exception {
        Long orderId = placeOrder("k1", "ZZZ", OrderSide.BUY, 10, "101.50");

        awaitStatus(orderId, OrderStatus.REJECTED);
        assertThat(outboxRepository.countByEventTypeAndAggregateId("OrderValidated", orderId.toString())).isZero();
    }

    @Test
    void buyBelowMarketIsRejectedAtExecution() throws Exception {
        Long orderId = placeOrder("k1", "ACME", OrderSide.BUY, 10, "99");

        awaitStatus(orderId, OrderStatus.REJECTED);
        assertThat(heldQuantity("ACME")).isZero();
    }

    @Test
    void sellWithoutSharesIsRejected() throws Exception {
        Long orderId = placeOrder("k1", "ACME", OrderSide.SELL, 5, "90");

        awaitStatus(orderId, OrderStatus.REJECTED);
    }

    @Test
    void sellAfterBuyReducesPosition() throws Exception {
        Long buy = placeOrder("k1", "ACME", OrderSide.BUY, 10, "101.50");
        awaitStatus(buy, OrderStatus.EXECUTED);

        Long sell = placeOrder("k2", "ACME", OrderSide.SELL, 4, "95");
        awaitStatus(sell, OrderStatus.EXECUTED);

        assertThat(heldQuantity("ACME")).isEqualTo(6);
    }

    @Test
    void duplicateEventIsProcessedOnlyOnce() throws Exception {
        Long first = placeOrder("k1", "ACME", OrderSide.BUY, 10, "101.50");
        awaitStatus(first, OrderStatus.EXECUTED);

        // Deliver the same OrderCreated event a second time (what a relay crash or a retry would do)
        OutboxEvent created = outboxRepository.findByEventTypeAndAggregateId("OrderCreated", first.toString())
                .orElseThrow();
        kafkaTemplate.send(Topics.ORDERS_CREATED, created.getMessageKey(), created.getPayload())
                .get(10, TimeUnit.SECONDS);

        // Same user = same key = same partition, so this order is consumed after the duplicate
        Long second = placeOrder("k2", "ACME", OrderSide.BUY, 1, "101.50");
        awaitStatus(second, OrderStatus.EXECUTED);

        assertThat(outboxRepository.countByEventTypeAndAggregateId("OrderValidated", first.toString()))
                .isEqualTo(1);
        Long processed = jdbcClient.sql("SELECT count(*) FROM processed_events WHERE event_id = :id")
                .param("id", created.getEventId())
                .query(Long.class)
                .single();
        assertThat(processed).isEqualTo(1L);
        // 10 + 1, not 21: the duplicate did not buy again
        assertThat(heldQuantity("ACME")).isEqualTo(11);
    }

    @Test
    void malformedMessageGoesToDeadLetterTopic() throws Exception {
        String bad = "this is not json " + UUID.randomUUID();
        Map<String, Object> props = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "dlt-check-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(Topics.ORDERS_CREATED_DLT));
            kafkaTemplate.send(Topics.ORDERS_CREATED, "bad-key", bad).get(10, TimeUnit.SECONDS);

            long deadline = System.nanoTime() + TIMEOUT.toNanos();
            while (System.nanoTime() < deadline) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    if (bad.equals(record.value())) {
                        return;
                    }
                }
            }
        }
        fail("malformed message did not reach " + Topics.ORDERS_CREATED_DLT);
    }
}
