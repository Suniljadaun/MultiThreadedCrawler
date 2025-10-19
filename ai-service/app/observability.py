import json
import logging
import time
from datetime import datetime, timezone

from prometheus_client import CONTENT_TYPE_LATEST, Counter, Histogram, generate_latest
from starlette.middleware.base import BaseHTTPMiddleware
from starlette.requests import Request
from starlette.responses import Response

from app.api.request_id import RequestIdLogFilter

SERVICE = "finintel-ai-service"
log = logging.getLogger("app.access")

HTTP_REQUESTS = Counter("http_requests", "HTTP requests", ["method", "route", "status"])
HTTP_LATENCY = Histogram("http_request_duration_seconds", "HTTP request latency", ["method", "route"])
RAG_QUERIES = Counter("rag_queries", "Research questions answered", ["answer_type"])
RAG_RETRIEVAL = Histogram("rag_retrieval_duration_seconds", "Embedding + vector search time",
                          buckets=(0.001, 0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1, 2.5))
RAG_GENERATION = Histogram("rag_generation_duration_seconds", "Answer generation time", ["provider"],
                           buckets=(0.001, 0.01, 0.1, 0.5, 1, 2.5, 5, 10, 30, 60))
LLM_ERRORS = Counter("rag_llm_errors", "LLM calls that failed or timed out", ["provider"])
INGESTED = Counter("rag_documents_ingested", "Ingest requests", ["result"])


def metrics_response() -> Response:
    return Response(generate_latest(), media_type=CONTENT_TYPE_LATEST)


class AccessLogMiddleware(BaseHTTPMiddleware):
    """One log line and HTTP metrics per request. Runs inside RequestIdMiddleware, so logs carry the id."""

    async def dispatch(self, request: Request, call_next):
        started = time.perf_counter()
        status = 500
        try:
            response = await call_next(request)
            status = response.status_code
            return response
        finally:
            route = request.scope.get("route")
            # Route template, not the raw path, so ids in URLs don't create new label values
            template = route.path if route is not None else "unmatched"
            elapsed = time.perf_counter() - started
            if template != "/metrics":
                HTTP_REQUESTS.labels(request.method, template, str(status)).inc()
                HTTP_LATENCY.labels(request.method, template).observe(elapsed)
                log.info("%s %s -> %s in %.1f ms", request.method, request.url.path, status, elapsed * 1000)


class JsonFormatter(logging.Formatter):
    def format(self, record: logging.LogRecord) -> str:
        request_id = getattr(record, "request_id", None)
        entry = {
            "timestamp": datetime.fromtimestamp(record.created, timezone.utc).isoformat(),
            "level": record.levelname,
            "service": SERVICE,
            "logger": record.name,
            "requestId": None if request_id in (None, "-") else request_id,
            "message": record.getMessage(),
        }
        if record.exc_info:
            entry["error"] = self.formatException(record.exc_info)
        return json.dumps(entry)


def configure_logging(log_format: str = "text") -> None:
    handler = logging.StreamHandler()
    handler.addFilter(RequestIdLogFilter())
    if log_format == "json":
        handler.setFormatter(JsonFormatter())
    else:
        handler.setFormatter(logging.Formatter("%(asctime)s %(levelname)s [%(request_id)s] %(name)s: %(message)s"))
    root = logging.getLogger()
    root.handlers[:] = [handler]
    root.setLevel(logging.INFO)
