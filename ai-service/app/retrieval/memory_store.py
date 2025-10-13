import threading

import numpy as np

from app.ingestion.models import Chunk
from app.retrieval.store import ScoredChunk, StoredDocument


class InMemoryVectorStore:
    """Exact search in memory. Used by tests and the evaluation script; data is lost on restart."""

    def __init__(self) -> None:
        self._lock = threading.Lock()
        self._docs: dict[str, StoredDocument] = {}
        self._chunks: dict[str, list[tuple[Chunk, np.ndarray]]] = {}

    def get_document(self, document_id: str) -> StoredDocument | None:
        return self._docs.get(document_id)

    def replace_document(self, doc: StoredDocument, chunks: list[Chunk], vectors: list[list[float]]) -> None:
        with self._lock:
            self._docs[doc.document_id] = doc
            self._chunks[doc.document_id] = [(c, np.asarray(v, dtype=np.float32)) for c, v in zip(chunks, vectors)]

    def search(self, vector: list[float], k: int, embedding_model: str) -> list[ScoredChunk]:
        query = np.asarray(vector, dtype=np.float32)
        with self._lock:
            candidates = [
                (chunk, v)
                for doc_id, items in self._chunks.items()
                if self._docs[doc_id].embedding_model == embedding_model
                for chunk, v in items
            ]
        scored = [ScoredChunk(chunk, float(np.dot(query, v))) for chunk, v in candidates]
        # Tie-break on chunk_id so results are deterministic
        scored.sort(key=lambda s: (-s.score, s.chunk.chunk_id))
        return scored[:k]

    def list_documents(self) -> list[StoredDocument]:
        return sorted(self._docs.values(), key=lambda d: d.document_id)

    def close(self) -> None:
        pass
