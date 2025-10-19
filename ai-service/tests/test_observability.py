import json
import logging

from fastapi.testclient import TestClient
from prometheus_client import REGISTRY

from app.generation.base import LLMError
from app.main import create_app
from app.observability import JsonFormatter
from app.retrieval.memory_store import InMemoryVectorStore
from app.services import build_services
from tests.conftest import SAMPLE, FakeLLM, memory_settings


def _value(name: str, **labels) -> float:
    return REGISTRY.get_sample_value(name, labels) or 0.0


def _client(llm=None) -> TestClient:
    return TestClient(create_app(build_services(memory_settings(), store=InMemoryVectorStore(), llm=llm)))


def test_metrics_endpoint_exposes_http_metrics_by_route_template():
    with _client() as client:
        before = _value("http_requests_total", method="GET", route="/api/v1/health", status="200")
        client.get("/api/v1/health")
        body = client.get("/metrics").text

    assert _value("http_requests_total", method="GET", route="/api/v1/health", status="200") == before + 1
    assert "http_request_duration_seconds_bucket" in body
    # /metrics itself is not counted
    assert 'route="/metrics"' not in body


def test_unknown_paths_share_one_label():
    with _client() as client:
        before = _value("http_requests_total", method="GET", route="unmatched", status="404")
        client.get("/api/v1/nope/123")
        client.get("/api/v1/nope/456")
    assert _value("http_requests_total", method="GET", route="unmatched", status="404") == before + 2


def test_rag_query_metrics():
    with _client() as client:
        client.post("/api/v1/research/documents", json={"source": "example.md", "content": SAMPLE})
        answered = _value("rag_queries_total", answer_type="extractive")
        declined = _value("rag_queries_total", answer_type="out_of_scope")
        retrievals = _value("rag_retrieval_duration_seconds_count")

        client.post("/api/v1/research/query", json={"question": "What was the revenue growth?"})
        client.post("/api/v1/research/query", json={"question": "Should I buy Example Co?"})

    assert _value("rag_queries_total", answer_type="extractive") == answered + 1
    assert _value("rag_queries_total", answer_type="out_of_scope") == declined + 1
    assert _value("rag_retrieval_duration_seconds_count") == retrievals + 1


def test_llm_errors_are_counted():
    before = _value("rag_llm_errors_total", provider="fake")
    with _client(FakeLLM(error=LLMError("timeout"))) as client:
        client.post("/api/v1/research/documents", json={"source": "example.md", "content": SAMPLE})
        response = client.post("/api/v1/research/query", json={"question": "What was the revenue growth?"})
    assert response.status_code == 503
    assert _value("rag_llm_errors_total", provider="fake") == before + 1


def test_json_log_line_has_service_and_request_id():
    record = logging.LogRecord("app.x", logging.INFO, __file__, 1, "hello %s", ("world",), None)
    record.request_id = "req-5"

    line = json.loads(JsonFormatter().format(record))

    assert line["message"] == "hello world"
    assert line["requestId"] == "req-5"
    assert line["service"] == "finintel-ai-service"
    assert line["level"] == "INFO"
