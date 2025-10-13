from dataclasses import dataclass
from typing import Protocol

from app.ingestion.models import Chunk


@dataclass(frozen=True)
class ScoredChunk:
    chunk: Chunk
    score: float  # cosine similarity, 1.0 = identical direction


@dataclass(frozen=True)
class StoredDocument:
    document_id: str
    title: str
    source: str
    fingerprint: str
    embedding_model: str
    chunks: int


class VectorStore(Protocol):
    def get_document(self, document_id: str) -> StoredDocument | None: ...

    def replace_document(self, doc: StoredDocument, chunks: list[Chunk], vectors: list[list[float]]) -> None:
        """Atomically replace a document and all its chunks."""
        ...

    def search(self, vector: list[float], k: int, embedding_model: str) -> list[ScoredChunk]:
        """Top-k chunks by cosine similarity, only among chunks embedded with embedding_model."""
        ...

    def list_documents(self) -> list[StoredDocument]: ...

    def close(self) -> None: ...
