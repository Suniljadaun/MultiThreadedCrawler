# Security

This is a local learning project with synthetic data. It is **not** safe to expose to the internet as is.
This page lists what is protected, what is not, and what production would need.

## In place

| Area | What is done | Where |
|---|---|---|
| Secrets | none in git; config comes from environment variables; `.env` files are ignored, only `.env.example` is committed | `.gitignore`, `application.yml`, `ai-service/app/config` |
| Default credentials | `finintel/finintel` (Postgres) and `admin/admin` (Grafana) are local-dev defaults, overridable by env vars | `docker-compose.yml` |
| Input validation | Bean Validation on every request body and path/header value; invalid input never reaches the database | `*Request.java`, `GlobalExceptionHandler` |
| AI input limits | question 3-1000 chars, document up to 2 MB, `document_id` pattern, `top_k` 1-20 | `ai-service/app/schemas/research.py` |
| SQL injection | JPA/JDBC parameters and psycopg parameters only; no string-built SQL with user input | repositories, `pgvector_store.py` |
| Error bodies | no stack traces or SQL in responses; details only in the server log with the request id | both error handlers |
| Log injection | client `X-Request-ID` is reused only if it matches `[A-Za-z0-9._-]{1,100}` (A-021) | `RequestIds.java`, `request_id.py` |
| Replay of order requests | `Idempotency-Key` + request hash: a replay cannot create a second order or change the first | `OrderService` |
| Containers | both app images run as a non-root user (uid 10001) | `backend/Dockerfile`, `ai-service/Dockerfile` |
| CI token | workflow runs with `contents: read` only | `.github/workflows/ci.yml` |
| LLM output | answers must cite retrieved sources and may not contain numbers absent from them; personal advice is declined before retrieval | `citations.py`, `scope.py` |

## Not in place (known risks)

| Risk | Why it is accepted here | Production answer |
|---|---|---|
| No authentication or authorization (A-006) | the plan defers auth; any caller can read any user's orders and portfolio | OAuth2/OIDC resource server (Spring Security), user id taken from the token, not the URL |
| `PUT /market-prices/{symbol}` is open | simulation helper for tests and benchmarks | remove or restrict to an admin role |
| Actuator (`/actuator/prometheus`, `/metrics`) open | local Prometheus scrapes it | separate management port, network-only access |
| Grafana anonymous viewer | local dashboards without login | disable anonymous access |
| No TLS | everything runs on localhost | TLS at the ingress / load balancer |
| No rate limiting | single local user | rate limits per client at the gateway |
| Prompt injection through documents | documents are synthetic and ingested by the developer | ingest only trusted sources, keep output checks, limit what the LLM can do (it has no tools) |
| Dependency vulnerabilities | not scanned yet | Dependabot or `mvn dependency-check` / `pip-audit` in CI |

## Handling a leaked secret

1. Revoke the key at the provider first (e.g. OpenAI dashboard).
2. Remove it from the repository and from history; assume it is compromised either way.
3. Put the new value only in a local `.env` or the CI secret store.
