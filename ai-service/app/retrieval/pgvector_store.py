import numpy as np
import psycopg
from pgvector.psycopg import register_vector
from psycopg_pool import ConnectionPool

from app.ingestion.models import Chunk
from app.retrieval.store import ScoredChunk, StoredDocument

# The AI service owns the "research" schema (ADR-005). The backend's Flyway only manages "public".
_SCHEMA = """
CREATE EXTENSION IF NOT EXISTS vector;
CREATE SCHEMA IF NOT EXISTS research;

CREATE TABLE IF NOT EXISTS research.documents (
    document_id     TEXT PRIMARY KEY,
    title           TEXT NOT NULL,
    source          TEXT NOT NULL,
    fingerprint     CHAR(64) NOT NULL,
    embedding_model TEXT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS research.document_chunks (
    chunk_id    TEXT PRIMARY KEY,
    document_id TEXT NOT NULL REFERENCES research.documents (document_id) ON DELETE CASCADE,
    chunk_index INT NOT NULL,
    section     TEXT NOT NULL,
    content     TEXT NOT NULL,
    embedding   vector({dim}) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_chunks_document_index UNIQUE (document_id, chunk_index)
);

CREATE INDEX IF NOT EXISTS idx_chunks_embedding
    ON research.document_chunks USING hnsw (embedding vector_cosine_ops);
"""


class SchemaMismatchError(RuntimeError):
    pass


class PgVectorStore:
    def __init__(self, dsn: str, dim: int):
        self._dim = dim
        with psycopg.connect(dsn, autocommit=True) as conn:
            conn.execute(_SCHEMA.format(dim=int(dim)))
            existing = conn.execute(
                "SELECT atttypmod FROM pg_attribute "
                "WHERE attrelid = 'research.document_chunks'::regclass AND attname = 'embedding'"
            ).fetchone()[0]
        if existing != dim:
            raise SchemaMismatchError(
                f"research.document_chunks.embedding is vector({existing}) but EMBEDDING_DIM={dim}. "
                "Drop the research schema and re-ingest, or set EMBEDDING_DIM back."
            )
        # check: drop dead connections (e.g. after a DB restart) before use; timeout: fail fast when DB is down
        self._pool = ConnectionPool(dsn, min_size=1, max_size=5, configure=register_vector,
                                    check=ConnectionPool.check_connection, timeout=5, open=True)

    def get_document(self, document_id: str) -> StoredDocument | None:
        with self._pool.connection() as conn:
            row = conn.execute(
                "SELECT d.document_id, d.title, d.source, d.fingerprint, d.embedding_model, "
                "(SELECT count(*) FROM research.document_chunks c WHERE c.document_id = d.document_id) "
                "FROM research.documents d WHERE d.document_id = %s",
                (document_id,),
            ).fetchone()
        return StoredDocument(*row) if row else None

    def replace_document(self, doc: StoredDocument, chunks: list[Chunk], vectors: list[list[float]]) -> None:
        with self._pool.connection() as conn, conn.transaction():
            # The upsert locks the document row, so two ingests of the same document run one after the other
            conn.execute(
                "INSERT INTO research.documents (document_id, title, source, fingerprint, embedding_model) "
                "VALUES (%s, %s, %s, %s, %s) "
                "ON CONFLICT (document_id) DO UPDATE SET title = EXCLUDED.title, source = EXCLUDED.source, "
                "fingerprint = EXCLUDED.fingerprint, embedding_model = EXCLUDED.embedding_model, "
                "updated_at = now()",
                (doc.document_id, doc.title, doc.source, doc.fingerprint, doc.embedding_model),
            )
            conn.execute("DELETE FROM research.document_chunks WHERE document_id = %s", (doc.document_id,))
            with conn.cursor() as cur:
                cur.executemany(
                    "INSERT INTO research.document_chunks "
                    "(chunk_id, document_id, chunk_index, section, content, embedding, created_at) "
                    "VALUES (%s, %s, %s, %s, %s, %s, %s)",
                    [
                        (c.chunk_id, c.document_id, c.index, c.section, c.text,
                         np.asarray(v, dtype=np.float32), c.created_at)
                        for c, v in zip(chunks, vectors)
                    ],
                )

    def search(self, vector: list[float], k: int, embedding_model: str) -> list[ScoredChunk]:
        query = np.asarray(vector, dtype=np.float32)
        with self._pool.connection() as conn:
            rows = conn.execute(
                "SELECT c.chunk_id, c.document_id, c.chunk_index, d.title, d.source, c.section, c.content, "
                "c.created_at, 1 - (c.embedding <=> %s) AS score "
                "FROM research.document_chunks c JOIN research.documents d ON d.document_id = c.document_id "
                "WHERE d.embedding_model = %s "
                "ORDER BY c.embedding <=> %s LIMIT %s",
                (query, embedding_model, query, k),
            ).fetchall()
        return [
            ScoredChunk(
                Chunk(document_id=r[1], chunk_id=r[0], index=r[2], title=r[3], source=r[4], section=r[5],
                      text=r[6], created_at=r[7]),
                float(r[8]),
            )
            for r in rows
        ]

    def list_documents(self) -> list[StoredDocument]:
        with self._pool.connection() as conn:
            rows = conn.execute(
                "SELECT d.document_id, d.title, d.source, d.fingerprint, d.embedding_model, count(c.chunk_id) "
                "FROM research.documents d LEFT JOIN research.document_chunks c ON c.document_id = d.document_id "
                "GROUP BY d.document_id ORDER BY d.document_id"
            ).fetchall()
        return [StoredDocument(*r) for r in rows]

    def close(self) -> None:
        self._pool.close()
