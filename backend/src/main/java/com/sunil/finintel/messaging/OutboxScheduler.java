package com.sunil.finintel.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Runs the relay on a fixed delay. Kept separate from OutboxRelay so the
// @Transactional call goes through the Spring proxy.
@Component
public class OutboxScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxScheduler.class);

    private final OutboxRelay relay;
    private final boolean enabled;

    public OutboxScheduler(OutboxRelay relay, @Value("${app.outbox.publisher.enabled:true}") boolean enabled) {
        this.relay = relay;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${app.outbox.publisher.interval-ms:500}")
    public void run() {
        if (!enabled) {
            return;
        }
        try {
            int published = relay.publishBatch();
            if (published > 0) {
                log.debug("Published {} outbox events", published);
            }
        } catch (RuntimeException e) {
            // e.g. database down; the next run tries again
            log.error("Outbox relay run failed", e);
        }
    }
}
