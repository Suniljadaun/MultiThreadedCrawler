-- End-to-end order pipeline for one benchmark run: HTTP insert -> outbox -> Kafka -> validation -> execution.
-- Usage: psql -v prefix='bench-<runId>-%' -f order-latency.sql
SELECT status, count(*) AS orders
FROM orders WHERE idempotency_key LIKE :'prefix'
GROUP BY status ORDER BY status;

SELECT count(*) AS executed,
       round(extract(epoch FROM max(e.executed_at) - min(o.created_at))::numeric, 2) AS span_s,
       round((count(*) / nullif(extract(epoch FROM max(e.executed_at) - min(o.created_at)), 0))::numeric, 1) AS executed_per_s,
       round((1000 * percentile_cont(0.50) WITHIN GROUP (ORDER BY extract(epoch FROM e.executed_at - o.created_at)))::numeric) AS p50_ms,
       round((1000 * percentile_cont(0.95) WITHIN GROUP (ORDER BY extract(epoch FROM e.executed_at - o.created_at)))::numeric) AS p95_ms,
       round((1000 * percentile_cont(0.99) WITHIN GROUP (ORDER BY extract(epoch FROM e.executed_at - o.created_at)))::numeric) AS p99_ms,
       round((1000 * max(extract(epoch FROM e.executed_at - o.created_at)))::numeric) AS max_ms
FROM orders o JOIN executions e ON e.order_id = o.id
WHERE o.idempotency_key LIKE :'prefix';

-- Idempotency under load: exactly one OrderCreated event per stored order
SELECT (SELECT count(*) FROM orders WHERE idempotency_key LIKE :'prefix') AS orders,
       (SELECT count(*) FROM outbox_events
        WHERE event_type = 'OrderCreated'
          AND aggregate_id IN (SELECT id::text FROM orders WHERE idempotency_key LIKE :'prefix')) AS order_created_events;
