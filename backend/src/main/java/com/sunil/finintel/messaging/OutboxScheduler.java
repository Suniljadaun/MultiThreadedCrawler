package com.sunil.finintel.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Runs the relay on a fixed delay. Kept separate from OutboxRelay so the
// @Transactional call goes through the Spring proxy (one transaction per batch).
// While batches come back full there is a backlog, so it keeps going up to max-rounds.
@Component
public class OutboxScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxScheduler.class);

    private final OutboxRelay relay;
    private final boolean enabled;
    private final int maxRounds;

    public OutboxScheduler(OutboxRelay relay,
                           @Value("${app.outbox.publisher.enabled:true}") boolean enabled,
                           @Value("${app.outbox.publisher.max-rounds:20}") int maxRounds) {
        this.relay = relay;
        this.enabled = enabled;
        this.maxRounds = maxRounds;
    }

    @Scheduled(fixedDelayString = "${app.outbox.publisher.interval-ms:500}")
    public void run() {
        if (!enabled) {
            return;
        }
        try {
            int total = 0;
            int published;
            int rounds = 0;
            do {
                published = relay.publishBatch();
                total += published;
                rounds++;
            } while (published == relay.batchSize() && rounds < maxRounds);
            if (total > 0) {
                log.debug("Published {} outbox events in {} batches", total, rounds);
            }
        } catch (RuntimeException e) {
            // e.g. database down; the next run tries again
            log.error("Outbox relay run failed", e);
        }
    }
}
