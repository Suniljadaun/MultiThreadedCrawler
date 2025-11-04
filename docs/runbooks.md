# Runbooks

Short procedures for the failures this system can have. Commands are for the Docker Compose setup and
work in PowerShell and bash. Dashboard: http://localhost:3000/d/finintel-overview.

Shortcut used below:

```bash
docker exec -it finintel-postgres psql -U finintel -d finintel
```

## Orders stay in CREATED or VALIDATED

1. Check the outbox backlog: panel "Outbox backlog", or
   `SELECT count(*), min(created_at) FROM outbox_events WHERE published_at IS NULL;`
2. Backlog growing and `outbox_publish_total{result="failure"}` rising (panel "Outbox publish / s"): Kafka is the problem, go to "Kafka down".
3. Backlog near zero but orders not moving: check consumer lag:
   `docker exec finintel-kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --describe --group order-validation`
   (same for `order-execution`). Growing lag means the consumer is slow or failing; check the backend log for its errors.
4. After a load test a large backlog is normal; it drains at the relay/consumer rate (docs/performance.md).

## Kafka down

Symptoms: `outbox_publish_total{result="failure"}` rising, log line `Outbox publish failed ... will retry`.
The API keeps accepting orders; nothing is lost.

1. `docker compose ps kafka` and `docker compose logs --tail 100 kafka`.
2. `docker compose up -d kafka` and wait for `healthy`.
3. The relay resumes on its next run. Confirm the backlog falls to 0.
4. Topic data is not kept across container re-creation; the backend re-creates topics on start.
   Restart the backend if it started while Kafka was down.

## Records in a dead-letter topic

Symptoms: `kafka_dead_letter_total` rising, panel "Consumer lag and dead letters".

1. Read them with their error headers:
   `docker exec finintel-kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic orders.created.DLT --from-beginning --property print.headers=true --timeout-ms 5000`
2. Find the cause in the backend log (search for the request id in the event's `requestId`).
3. Malformed record: fix the producer; the record itself cannot be replayed.
4. Transient cause (e.g. DB was down): fix it, then re-publish the value to the original topic with
   `kafka-console-producer.sh`. Consumers are idempotent, so a record that was partly handled is safe to replay.
There is no automatic replay (docs/reliability.md).

## Redis down

Symptoms: `/actuator/health` shows `redis: DOWN`, `portfolio_cache_total{result="error"}` rising, portfolio p95 up.
Portfolio reads keep working from PostgreSQL.

1. `docker compose up -d redis`.
2. Nothing to clean up: stale entries were never written while Redis was down, and TTL is 60 s.

## PostgreSQL down

Symptoms: backend `500` responses after about 30 s (pool timeout), ai-service `503 DATABASE_UNAVAILABLE`,
Kafka records going to the DLT.

1. `docker compose ps postgres`, `docker compose logs --tail 100 postgres`.
2. `docker compose up -d postgres` and wait for `healthy`. Data is in the `pgdata` volume.
3. The backend and ai-service reconnect on their own (Hikari / psycopg pool check).
4. Replay any DLT records from the outage (section above).

## ai-service returns 503

| Error | Meaning | Action |
|---|---|---|
| `LLM_UNAVAILABLE` | LLM provider timed out or failed | check `OPENAI_BASE_URL` / Ollama is running; or set `LLM_PROVIDER=extractive` |
| `EMBEDDING_UNAVAILABLE` | embedding provider failed | same as above for the embedding settings |
| `DATABASE_UNAVAILABLE` | pgvector store unreachable | see "PostgreSQL down" |

Changing the embedding model or dimension needs a re-ingest (docs/rag.md).

## Prometheus target DOWN

1. Open http://localhost:9090/targets.
2. The app is not running, or the ai-service listens only on 127.0.0.1: start it with `--host 0.0.0.0`.

## Reset benchmark data

Stop the backend first, so consumers do not write while rows are deleted:

```powershell
Get-Content scripts\benchmark\sql\cleanup.sql | docker exec -i finintel-postgres psql -U finintel -d finintel
```

The last line must show `unpublished_events_left = 0`.

## Start from scratch

```bash
docker compose --profile app down -v     # deletes the database volume
docker compose --profile app up -d --build
```
