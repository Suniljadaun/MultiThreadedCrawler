package com.sunil.finintel.messaging;

import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// Consumer-side deduplication. Joins the consumer's transaction, so "mark processed"
// and the state change commit together or not at all.
@Component
public class ProcessedEvents {

    private final JdbcClient jdbcClient;

    public ProcessedEvents(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    // true = first time this consumer sees the event; false = duplicate, skip it
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean markProcessed(String consumerName, UUID eventId) {
        int inserted = jdbcClient.sql("""
                        INSERT INTO processed_events (consumer_name, event_id)
                        VALUES (:consumer, :eventId)
                        ON CONFLICT DO NOTHING
                        """)
                .param("consumer", consumerName)
                .param("eventId", eventId)
                .update();
        return inserted == 1;
    }
}
