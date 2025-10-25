-- Benchmark data: 20,000 users, each holding all 5 synthetic symbols (100,000 positions).
-- Safe to run again: existing rows are kept. Remove with cleanup.sql.
INSERT INTO users (name, email)
SELECT 'Bench User ' || n, 'bench-' || n || '@bench.test'
FROM generate_series(1, 20000) AS n
ON CONFLICT (email) DO NOTHING;

INSERT INTO positions (user_id, symbol, quantity, avg_cost)
SELECT u.id, p.symbol, 1 + (u.id % 50), p.price
FROM users u
CROSS JOIN market_prices p
WHERE u.email LIKE 'bench-%@bench.test'
ON CONFLICT (user_id, symbol) DO NOTHING;

ANALYZE users;
ANALYZE positions;

SELECT count(*) AS bench_users, min(id) AS first_id, max(id) AS last_id
FROM users WHERE email LIKE 'bench-%@bench.test';
SELECT count(*) AS positions FROM positions;
