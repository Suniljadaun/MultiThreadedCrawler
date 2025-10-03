package com.sunil.finintel.messaging;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {

    // Oldest unpublished events first. SKIP LOCKED: rows locked by another relay are skipped, not waited on.
    // Must run inside a transaction; the locks are held until it commits.
    @Query(value = "SELECT * FROM outbox_events WHERE published_at IS NULL ORDER BY id LIMIT :limit FOR UPDATE SKIP LOCKED",
            nativeQuery = true)
    List<OutboxEvent> lockUnpublished(@Param("limit") int limit);

    Optional<OutboxEvent> findByEventTypeAndAggregateId(String eventType, String aggregateId);

    long countByEventTypeAndAggregateId(String eventType, String aggregateId);
}
