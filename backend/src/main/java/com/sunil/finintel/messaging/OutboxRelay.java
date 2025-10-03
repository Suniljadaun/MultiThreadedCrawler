package com.sunil.finintel.messaging;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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

    public OutboxRelay(OutboxRepository outboxRepository,
                       KafkaTemplate<String, String> kafkaTemplate,
                       @Value("${app.outbox.publisher.batch-size:100}") int batchSize,
                       @Value("${app.outbox.publisher.send-timeout-ms:10000}") long sendTimeoutMs) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.batchSize = batchSize;
        this.sendTimeoutMs = sendTimeoutMs;
    }

    // Returns how many events were published
    @Transactional
    public int publishBatch() {
        List<OutboxEvent> batch = outboxRepository.lockUnpublished(batchSize);
        int published = 0;
        for (OutboxEvent event : batch) {
            try {
                kafkaTemplate.send(event.getTopic(), event.getMessageKey(), event.getPayload())
                        .get(sendTimeoutMs, TimeUnit.MILLISECONDS);
                event.markPublished();
                published++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                event.markFailed("interrupted");
                break;
            } catch (ExecutionException | TimeoutException | RuntimeException e) {
                event.markFailed(e.toString());
                log.warn("Outbox publish failed for event {} ({}), will retry: {}",
                        event.getEventId(), event.getEventType(), e.toString());
                // Stop here so later events are not published ahead of this one
                break;
            }
        }
        return published;
    }
}
