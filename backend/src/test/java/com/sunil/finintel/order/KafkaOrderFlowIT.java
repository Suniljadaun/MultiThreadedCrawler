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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.sunil.finintel.messaging.OutboxEvent;
import com.sunil.finintel.messaging.OutboxRepository;
import com.sunil.finintel.messaging.Topics;
import com.sunil.finintel.user.User;
import com.sunil.finintel.user.UserRepository;

// Full flow on real PostgreSQL + Kafka:
// POST order -> outbox -> relay -> orders.created -> validation consumer -> VALIDATED / REJECTED
@SpringBootTest(properties = {
        "app.outbox.publisher.enabled=true",
        "app.outbox.publisher.interval-ms=200",
        "spring.kafka.listener.auto-startup=true",
        "spring.kafka.admin.auto-create=true"
})
@Testcontainers
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
        outboxRepository.deleteAll();
        jdbcClient.sql("DELETE FROM processed_events").update();
        orderRepository.deleteAll();
        userRepository.deleteAll();
    }

    private Long placeOrder(String key, String symbol) {
        return orderService.place(key, new PlaceOrderRequest(userId, symbol, OrderSide.BUY, 10,
                new BigDecimal("101.50"))).order().id();
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

    @Test
    void supportedOrderIsValidatedThroughKafka() throws Exception {
        Long orderId = placeOrder("k1", "ACME");

        awaitStatus(orderId, OrderStatus.VALIDATED);

        OutboxEvent created = outboxRepository.findByEventTypeAndAggregateId("OrderCreated", orderId.toString())
                .orElseThrow();
        assertThat(created.getPublishedAt()).isNotNull();
        assertThat(outboxRepository.countByEventTypeAndAggregateId("OrderValidated", orderId.toString()))
                .isEqualTo(1);
    }

    @Test
    void unsupportedSymbolIsRejectedThroughKafka() throws Exception {
        Long orderId = placeOrder("k1", "ZZZ");

        awaitStatus(orderId, OrderStatus.REJECTED);
    }

    @Test
    void duplicateEventIsProcessedOnlyOnce() throws Exception {
        Long first = placeOrder("k1", "ACME");
        awaitStatus(first, OrderStatus.VALIDATED);

        // Deliver the same OrderCreated event a second time (what a relay crash or a retry would do)
        OutboxEvent created = outboxRepository.findByEventTypeAndAggregateId("OrderCreated", first.toString())
                .orElseThrow();
        kafkaTemplate.send(Topics.ORDERS_CREATED, created.getMessageKey(), created.getPayload())
                .get(10, TimeUnit.SECONDS);

        // Same user = same key = same partition, so this order is consumed after the duplicate
        Long second = placeOrder("k2", "ACME");
        awaitStatus(second, OrderStatus.VALIDATED);

        assertThat(outboxRepository.countByEventTypeAndAggregateId("OrderValidated", first.toString()))
                .isEqualTo(1);
        Long processed = jdbcClient.sql("SELECT count(*) FROM processed_events WHERE event_id = :id")
                .param("id", created.getEventId())
                .query(Long.class)
                .single();
        assertThat(processed).isEqualTo(1L);
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
