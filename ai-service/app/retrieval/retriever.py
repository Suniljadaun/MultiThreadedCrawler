import time
from dataclasses import dataclass

from app.embeddings.base import EmbeddingClient
from app.ingestion.cleaner import clean
from app.retrieval.store import ScoredChunk, VectorStore


@dataclass(frozen=True)
class RetrievalResult:
    candidates: list[ScoredChunk]  # everything the vector search returned, best first
    chunks: list[ScoredChunk]  # the ones at or above min_score
    latency_ms: float


class Retriever:
    def __init__(self, store: VectorStore, embedder: EmbeddingClient, min_score: float):
        self._store = store
        self._embedder = embedder
        self.min_score = min_score

    def retrieve(self, question: str, k: int) -> RetrievalResult:
        started = time.perf_counter()
        query = " ".join(clean(question).split())
        vector = self._embedder.embed([query])[0]
        candidates = self._store.search(vector, k, self._embedder.model)
        # Weak matches are dropped rather than handed to the LLM as "evidence"
        kept = [c for c in candidates if c.score >= self.min_score]
        return RetrievalResult(candidates, kept, (time.perf_counter() - started) * 1000)
