import math

import httpx
import pytest

from app.embeddings.base import EmbeddingError
from app.embeddings.hash_embedding import HashEmbeddingClient, tokenize
from app.embeddings.openai_embedding import OpenAIEmbeddingClient


def _cos(a, b):
    return sum(x * y for x, y in zip(a, b))


def test_tokenize_drops_stopwords_and_keeps_numbers():
    assert tokenize("What was the margin in 2025? It was 18.5%") == ["margin", "2025", "18.5"]


def test_hash_embedding_is_deterministic_and_unit_length():
    client = HashEmbeddingClient(dim=64)
    first, again = client.embed(["revenue grew 12%"])[0], client.embed(["revenue grew 12%"])[0]
    assert first == again
    assert len(first) == 64
    assert math.isclose(math.sqrt(sum(v * v for v in first)), 1.0, rel_tol=1e-9)


def test_hash_embedding_scores_overlap_higher_than_unrelated_text():
    client = HashEmbeddingClient(dim=512)
    query, related, unrelated = client.embed(
        ["operating margin", "operating margin improved to 18%", "new plant in Poland"]
    )
    assert _cos(query, related) > _cos(query, unrelated)


def test_empty_text_gives_zero_vector():
    assert set(HashEmbeddingClient(dim=16).embed([""])[0]) == {0.0}


def _client(handler, dim=3):
    return OpenAIEmbeddingClient("http://llm.test/v1", "key", "m", dim,
                                 http=httpx.Client(transport=httpx.MockTransport(handler)))


def test_openai_embedding_orders_by_index_and_normalises():
    def handler(request):
        assert request.url.path == "/v1/embeddings"
        assert request.headers["Authorization"] == "Bearer key"
        return httpx.Response(200, json={"data": [
            {"index": 1, "embedding": [0, 2, 0]},
            {"index": 0, "embedding": [3, 0, 4]},
        ]})

    assert _client(handler).embed(["a", "b"]) == [[0.6, 0.0, 0.8], [0.0, 1.0, 0.0]]


def test_openai_embedding_rejects_wrong_dimension():
    handler = lambda request: httpx.Response(200, json={"data": [{"index": 0, "embedding": [1, 2]}]})
    with pytest.raises(EmbeddingError):
        _client(handler).embed(["a"])


def test_openai_embedding_wraps_http_errors():
    with pytest.raises(EmbeddingError):
        _client(lambda request: httpx.Response(500)).embed(["a"])
