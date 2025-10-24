package com.sunil.finintel.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    private OutboxRelay relay() {
        return new OutboxRelay(outboxRepository, kafkaTemplate, registry, 100, 1000);
    }

    private static OutboxEvent event(String topic) {
        return new OutboxEvent(UUID.randomUUID(), topic, "1", "OrderCreated", "1", "{}");
    }

    private double publishCount(String result) {
        return registry.get("outbox.publish").tag("result", result).counter().count();
    }

    @Test
    void countsPublishedEvents() {
        OutboxEvent first = event("t1");
        OutboxEvent second = event("t2");
        when(outboxRepository.lockUnpublished(100)).thenReturn(List.of(first, second));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture((SendResult<String, String>) null));

        assertThat(relay().publishBatch()).isEqualTo(2);

        assertThat(first.getPublishedAt()).isNotNull();
        assertThat(publishCount("success")).isEqualTo(2);
        assertThat(publishCount("failure")).isZero();
    }

    @Test
    void stopsAtFirstFailureAndCountsIt() {
        OutboxEvent first = event("t1");
        OutboxEvent second = event("t2");
        when(outboxRepository.lockUnpublished(100)).thenReturn(List.of(first, second));
        when(kafkaTemplate.send(eq("t1"), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")));
        when(kafkaTemplate.send(eq("t2"), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture((SendResult<String, String>) null));

        assertThat(relay().publishBatch()).isZero();

        // t2 may have reached Kafka, but stays unpublished so it is sent again after t1
        assertThat(first.getAttempts()).isEqualTo(1);
        assertThat(second.getPublishedAt()).isNull();
        assertThat(second.getAttempts()).isZero();
        assertThat(publishCount("failure")).isEqualTo(1);
        assertThat(publishCount("success")).isZero();
    }

    @Test
    void failureInTheMiddleKeepsEarlierEventsPublished() {
        OutboxEvent first = event("t1");
        OutboxEvent second = event("t2");
        OutboxEvent third = event("t3");
        when(outboxRepository.lockUnpublished(100)).thenReturn(List.of(first, second, third));
        when(kafkaTemplate.send(eq("t1"), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture((SendResult<String, String>) null));
        when(kafkaTemplate.send(eq("t2"), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")));
        when(kafkaTemplate.send(eq("t3"), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture((SendResult<String, String>) null));

        assertThat(relay().publishBatch()).isEqualTo(1);

        assertThat(first.getPublishedAt()).isNotNull();
        assertThat(second.getAttempts()).isEqualTo(1);
        assertThat(third.getPublishedAt()).isNull();
    }

    @Test
    void sendThrowingStopsTheRestOfTheBatch() {
        OutboxEvent first = event("t1");
        OutboxEvent second = event("t2");
        when(outboxRepository.lockUnpublished(100)).thenReturn(List.of(first, second));
        when(kafkaTemplate.send(eq("t1"), anyString(), anyString()))
                .thenThrow(new IllegalStateException("no metadata"));

        assertThat(relay().publishBatch()).isZero();

        assertThat(first.getAttempts()).isEqualTo(1);
        verify(kafkaTemplate, never()).send(eq("t2"), anyString(), anyString());
        assertThat(publishCount("failure")).isEqualTo(1);
    }

    @Test
    void sendThatNeverCompletesTimesOut() {
        OutboxEvent first = event("t1");
        when(outboxRepository.lockUnpublished(100)).thenReturn(List.of(first));
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(new CompletableFuture<>());

        OutboxRelay relay = new OutboxRelay(outboxRepository, kafkaTemplate, registry, 100, 50);

        assertThat(relay.publishBatch()).isZero();
        assertThat(first.getAttempts()).isEqualTo(1);
        assertThat(publishCount("failure")).isEqualTo(1);
    }

    @Test
    void pendingGaugeReadsBacklogFromDatabase() {
        when(outboxRepository.countUnpublished()).thenReturn(7L);
        relay();

        assertThat(registry.get("outbox.pending").gauge().value()).isEqualTo(7.0);
    }
}
