#!/usr/bin/env bash
# End-to-end check against a running stack (docker compose --profile app up -d --build):
# user -> order -> Kafka -> execution -> portfolio, then RAG ingest + query.
# Needs curl and jq. Used by CI; also runs locally in Git Bash / WSL.
set -euo pipefail

API=${API:-http://localhost:8080/api/v1}
AI=${AI:-http://localhost:8000/api/v1}

wait_for() {
    for _ in $(seq 1 90); do
        if curl -fs "$1" > /dev/null; then return 0; fi
        sleep 2
    done
    echo "Timed out waiting for $1" >&2
    return 1
}

echo "Waiting for services..."
wait_for "http://localhost:8080/actuator/health"
wait_for "$AI/health"

run_id=$(date +%s)
user_id=$(curl -fs -X POST "$API/users" -H "Content-Type: application/json" \
    -d "{\"name\":\"Smoke Test\",\"email\":\"smoke-$run_id@example.com\"}" | jq -r .id)
echo "user $user_id"

# Limit above the synthetic ACME price (100), so the order fills
order_id=$(curl -fs -X POST "$API/orders" -H "Content-Type: application/json" \
    -H "Idempotency-Key: smoke-$run_id" \
    -d "{\"userId\":$user_id,\"symbol\":\"ACME\",\"side\":\"BUY\",\"quantity\":10,\"price\":200}" | jq -r .id)
echo "order $order_id"

status=""
for _ in $(seq 1 60); do
    status=$(curl -fs "$API/orders/$order_id" | jq -r .status)
    if [ "$status" = "EXECUTED" ] || [ "$status" = "REJECTED" ]; then break; fi
    sleep 1
done
echo "order status $status"
[ "$status" = "EXECUTED" ]

quantity=$(curl -fs "$API/portfolio/$user_id" | jq -r '.positions[] | select(.symbol == "ACME") | .quantity')
echo "portfolio ACME quantity $quantity"
[ "$quantity" = "10" ]

docker compose exec -T ai-service python -m app.ingestion.cli /data/documents
answer=$(curl -fs -X POST "$AI/research/query" -H "Content-Type: application/json" \
    -d '{"question":"What were the major revenue drivers for ACME in 2025?"}')
echo "$answer" | jq '{answerType, sources: [.sources[].documentId]}'
[ "$(echo "$answer" | jq -r .answerType)" = "extractive" ]
[ "$(echo "$answer" | jq '.sources | length')" -gt 0 ]

echo "Smoke test passed"
