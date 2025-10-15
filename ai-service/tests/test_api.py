import pytest
from fastapi.testclient import TestClient

from app.main import create_app
from app.retrieval.memory_store import InMemoryVectorStore
from app.services import build_services
from tests.conftest import SAMPLE, FakeLLM, memory_settings


def _client(llm=None) -> TestClient:
    services = build_services(memory_settings(), store=InMemoryVectorStore(), llm=llm)
    return TestClient(create_app(services))


@pytest.fixture
def client():
    with _client() as c:
        yield c


def _ingest(client, **overrides):
    return client.post("/api/v1/research/documents", json={"source": "example.md", "content": SAMPLE, **overrides})


def test_health(client):
    body = client.get("/api/v1/health").json()
    assert body == {"status": "UP", "vectorStore": "memory", "embeddingModel": "hash-v1-512",
                    "llmProvider": "extractive"}


def test_ingest_returns_201_then_200_for_same_content(client):
    first = _ingest(client)
    assert first.status_code == 201
    assert first.json() == {"documentId": "example", "title": "Example Co Report", "chunks": 3}
    assert _ingest(client).status_code == 200


def test_list_documents(client):
    _ingest(client)
    assert client.get("/api/v1/research/documents").json() == [
        {"documentId": "example", "title": "Example Co Report", "source": "example.md", "chunks": 3}
    ]


def test_query_returns_answer_sources_and_metadata(client):
    _ingest(client)
    body = client.post("/api/v1/research/query", json={"question": "What was the revenue growth?"}).json()

    assert body["answerType"] == "extractive"
    assert body["sources"][0]["documentId"] == "example"
    assert body["sources"][0]["chunkId"] == "example:1"
    assert body["retrieval"]["topK"] == 5
    assert "Not investment advice" in body["disclaimer"]


def test_validation_error_uses_standard_error_shape(client):
    response = client.post("/api/v1/research/query", json={"question": "x"}, headers={"X-Request-ID": "req-1"})

    assert response.status_code == 400
    body = response.json()
    assert body["error"] == "VALIDATION_ERROR"
    assert body["path"] == "/api/v1/research/query"
    assert body["requestId"] == "req-1"
    assert "question" in body["message"]


def test_bad_document_id_is_rejected(client):
    assert _ingest(client, documentId="Bad Id!").status_code == 400


def test_empty_document_is_422(client):
    response = _ingest(client, content="# Only a title")
    assert response.status_code == 422
    assert response.json()["error"] == "EMPTY_DOCUMENT"


def test_request_id_is_generated_and_echoed(client):
    generated = client.get("/api/v1/health").headers["X-Request-ID"]
    assert len(generated) == 36
    assert client.get("/api/v1/health", headers={"X-Request-ID": "abc-123"}).headers["X-Request-ID"] == "abc-123"


def test_llm_failure_is_503(llm_timeout):
    with _client(llm_timeout) as client:
        _ingest(client)
        response = client.post("/api/v1/research/query", json={"question": "What was the revenue growth?"})
    assert response.status_code == 503
    assert response.json()["error"] == "LLM_UNAVAILABLE"


def test_unknown_route_is_404_in_standard_shape(client):
    response = client.get("/api/v1/nope")
    assert response.status_code == 404
    assert response.json()["error"] == "NOT_FOUND"


def test_unexpected_error_is_500_in_standard_shape():
    class Broken(FakeLLM):
        def complete(self, system, user):
            raise ZeroDivisionError

    with _client(Broken()) as client:
        _ingest(client)
        response = client.post("/api/v1/research/query", json={"question": "What was the revenue growth?"},
                               headers={"X-Request-ID": "boom-1"})
    assert response.status_code == 500
    assert response.json()["error"] == "INTERNAL_ERROR"
    assert response.json()["requestId"] == "boom-1"
