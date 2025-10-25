-- Removes everything the benchmarks created (users, their orders, events, positions).
-- Best run with the backend stopped. The table locks also keep running consumers from
-- inserting executions for these orders while the delete is in progress.
BEGIN;
LOCK TABLE orders, executions, positions, outbox_events IN SHARE ROW EXCLUSIVE MODE;
CREATE TEMP TABLE bench_users AS SELECT id FROM users WHERE email LIKE 'bench-%@bench.test';
CREATE TEMP TABLE bench_orders AS SELECT id FROM orders WHERE user_id IN (SELECT id FROM bench_users);
DELETE FROM outbox_events WHERE event_type IN ('OrderCreated', 'OrderValidated', 'OrderRejected', 'OrderExecuted')
    AND aggregate_id IN (SELECT id::text FROM bench_orders);
DELETE FROM outbox_events WHERE event_type = 'PositionUpdated'
    AND split_part(aggregate_id, ':', 1) IN (SELECT id::text FROM bench_users);
DELETE FROM executions WHERE order_id IN (SELECT id FROM bench_orders) OR user_id IN (SELECT id FROM bench_users);
DELETE FROM orders WHERE id IN (SELECT id FROM bench_orders);
DELETE FROM positions WHERE user_id IN (SELECT id FROM bench_users);
DELETE FROM users WHERE id IN (SELECT id FROM bench_users);
COMMIT;
SELECT count(*) AS unpublished_events_left FROM outbox_events WHERE published_at IS NULL;
