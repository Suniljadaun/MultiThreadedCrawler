import hashlib
import re
from dataclasses import dataclass
from pathlib import PurePath

from app.embeddings.base import EmbeddingClient
from app.ingestion.chunker import chunk_document
from app.ingestion.cleaner import clean
from app.ingestion.models import Chunk
from app.ingestion.parser import parse_markdown
from app.retrieval.store import StoredDocument, VectorStore

EMBED_BATCH = 64


class EmptyDocumentError(ValueError):
    pass


@dataclass(frozen=True)
class IngestResult:
    document_id: str
    title: str
    chunks: int
    unchanged: bool


def document_id_from_source(source: str) -> str:
    stem = PurePath(source.replace("\\", "/")).stem.lower()
    slug = re.sub(r"[^a-z0-9]+", "-", stem).strip("-")[:100]
    if not slug:
        raise ValueError(f"cannot derive a document id from source {source!r}")
    return slug


class IngestionService:
    """clean -> parse -> chunk -> embed -> store. Re-ingesting identical input is a no-op."""

    def __init__(self, store: VectorStore, embedder: EmbeddingClient, max_chars: int):
        self._store = store
        self._embedder = embedder
        self._max_chars = max_chars

    def ingest(self, source: str, content: str, document_id: str | None = None,
               title: str | None = None) -> IngestResult:
        document_id = document_id or document_id_from_source(source)
        text = clean(content)
        parsed = parse_markdown(text, fallback_title=title or document_id)
        final_title = title or parsed.title

        # Anything that changes the stored chunks is part of the fingerprint
        fingerprint = hashlib.sha256(
            "\n".join([final_title, source, str(self._max_chars), self._embedder.model, text]).encode()
        ).hexdigest()
        existing = self._store.get_document(document_id)
        if existing and existing.fingerprint == fingerprint:
            return IngestResult(document_id, final_title, existing.chunks, unchanged=True)

        pieces = chunk_document(parsed, self._max_chars)
        if not pieces:
            raise EmptyDocumentError(f"document {document_id} has no text after cleaning")

        chunks = [
            Chunk(document_id=document_id, chunk_id=f"{document_id}:{i}", index=i, title=final_title,
                  source=source, section=section, text=body)
            for i, (section, body) in enumerate(pieces)
        ]
        vectors: list[list[float]] = []
        for start in range(0, len(chunks), EMBED_BATCH):
            # Title and section are embedded with the text so "Initech risks" can match a "Risk factors" chunk
            batch = chunks[start:start + EMBED_BATCH]
            vectors.extend(self._embedder.embed([f"{c.title}\n{c.section}\n{c.text}" for c in batch]))

        doc = StoredDocument(document_id, final_title, source, fingerprint, self._embedder.model, len(chunks))
        self._store.replace_document(doc, chunks, vectors)
        return IngestResult(document_id, final_title, len(chunks), unchanged=False)
