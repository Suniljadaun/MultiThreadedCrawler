import math

import httpx

from app.embeddings.base import EmbeddingError


class OpenAIEmbeddingClient:
    """Any OpenAI-compatible /embeddings endpoint (OpenAI, Ollama at http://localhost:11434/v1, ...)."""

    def __init__(self, base_url: str, api_key: str, model: str, dim: int, timeout: float = 30.0,
                 http: httpx.Client | None = None):
        self.model = model
        self.dim = dim
        self._url = base_url.rstrip("/") + "/embeddings"
        self._headers = {"Authorization": f"Bearer {api_key}"} if api_key else {}
        self._http = http or httpx.Client(timeout=timeout)

    def embed(self, texts: list[str]) -> list[list[float]]:
        try:
            response = self._http.post(self._url, headers=self._headers, json={"model": self.model, "input": texts})
            response.raise_for_status()
            data = sorted(response.json()["data"], key=lambda d: d["index"])
        except (httpx.HTTPError, KeyError, ValueError) as e:
            raise EmbeddingError(f"embedding request failed: {e}") from e

        vectors = [d["embedding"] for d in data]
        if len(vectors) != len(texts) or any(len(v) != self.dim for v in vectors):
            raise EmbeddingError(f"expected {len(texts)} vectors of dim {self.dim} from {self.model}")
        return [_normalise(v) for v in vectors]


def _normalise(vector: list[float]) -> list[float]:
    norm = math.sqrt(sum(v * v for v in vector))
    return [v / norm for v in vector] if norm else vector
