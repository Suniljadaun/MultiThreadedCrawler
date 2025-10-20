# ADR-008: Request id correlation before distributed tracing

Status: Accepted

## Context
Phase 7 requires following one request across components. An order goes HTTP -> database -> outbox -> scheduler thread
-> Kafka -> validation consumer -> outbox -> Kafka -> execution consumer. The plan asks for traces "where practical".

## Decision
- Use a request id (correlation id) as the thread through everything: HTTP header, MDC, and a `requestId`
  field in the event envelope that consumers restore.
- Do not add OpenTelemetry tracing yet.

## Alternatives
- Micrometer Tracing + OpenTelemetry: gives spans and timings per hop. But the outbox relay sends events later from
  a scheduler thread, so the trace context would also have to be stored in the outbox row and restored by hand.
  It also needs a tracing backend (Jaeger or Tempo), one more container.
- Only log timestamps and order ids: works for orders, but not for requests that fail before an order exists.

## Consequences
- `grep <requestId>` over the logs shows the whole path of one request, including the Kafka consumers.
- No per-hop timing view; latency is measured by metrics per component instead.
- Tracing can be added later: the envelope already has the place to carry the context (a `traceparent` field).
