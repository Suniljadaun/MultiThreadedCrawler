package com.sunil.finintel.messaging;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

// Publishes outbox rows to Kafka.
// Delivery is at-least-once: if the app crashes after the send but before the commit,
// the event is sent again on the next run. Consumers deduplicate by eventId.
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final int batchSize;
    private final long sendTimeoutMs;
    private final Counter publishedCounter;
    private final Counter failedCounter;

    public OutboxRelay(OutboxRepository outboxRepository,
                       KafkaTemplate<String, String> kafkaTemplate,
                       MeterRegistry meterRegistry,
                       @Value("${app.outbox.publisher.batch-size:100}") int batchSize,
                       @Value("${app.outbox.publisher.send-timeout-ms:10000}") long sendTimeoutMs) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.batchSize = batchSize;
        this.sendTimeoutMs = sendTimeoutMs;
        this.publishedCounter = Counter.builder("outbox.publish").tag("result", "success")
                .description("Outbox events sent to Kafka").register(meterRegistry);
        this.failedCounter = Counter.builder("outbox.publish").tag("result", "failure")
                .description("Outbox events sent to Kafka").register(meterRegistry);
        // Growing backlog = Kafka down or relay stuck
        Gauge.builder("outbox.pending", outboxRepository, OutboxRepository::countUnpublished)
                .description("Outbox events not yet published").register(meterRegistry);
    }

    public int batchSize() {
        return batchSize;
    }

    // Sends the whole batch without waiting per event, then checks the results in order.
    // Returns how many events were published.
    @Transactional
    public int publishBatch() {
        List<OutboxEvent> batch = outboxRepository.lockUnpublished(batchSize);
        List<CompletableFuture<?>> sends = new ArrayList<>(batch.size());
        for (OutboxEvent event : batch) {
            try {
                sends.add(kafkaTemplate.send(event.getTopic(), event.getMessageKey(), event.getPayload()));
            } catch (RuntimeException e) {
                // e.g. no broker metadata; do not send the rest
                sends.add(CompletableFuture.failedFuture(e));
                break;
            }
        }
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(sendTimeoutMs);
        int published = 0;
        for (int i = 0; i < sends.size(); i++) {
            OutboxEvent event = batch.get(i);
            try {
                sends.get(i).get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
                event.markPublished();
                publishedCounter.increment();
                published++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                event.markFailed("interrupted");
                break;
            } catch (ExecutionException | TimeoutException | RuntimeException e) {
                event.markFailed(e.toString());
                failedCounter.increment();
                log.warn("Outbox publish failed for event {} ({}), will retry: {}",
                        event.getEventId(), event.getEventType(), e.toString());
                // Later events stay unpublished and are sent again next run, even if this
                // attempt reached Kafka (at-least-once, consumers deduplicate)
                break;
            }
        }
        return published;
    }
}
