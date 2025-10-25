-- Query run on every price change to find whose cached portfolio to evict (PositionRepository.findHolderIds)
EXPLAIN (ANALYZE, BUFFERS)
SELECT DISTINCT user_id FROM positions WHERE symbol = 'ACME' AND quantity > 0;
