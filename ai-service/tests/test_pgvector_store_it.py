"""Real PostgreSQL + pgvector in Docker. Run with: pytest -m integration"""
import threading

import psycopg
import pytest

from app.config.settings import Settings
from app.retrieval.pgvector_store import PgVectorStore, SchemaMismatchError
from app.services import build_services
from tests.conftest import SAMPLE

pytestmark = pytest.mark.integration


@pytest.fixture(scope="module")
def postgres():
    from testcontainers.community.postgres import PostgresContainer

    with PostgresContainer("pgvector/pgvector:pg17", driver=None) as pg:
        yield pg


def _dsn(pg) -> str:
    return (f"host={pg.get_container_host_ip()} port={pg.get_exposed_port(5432)} dbname={pg.dbname} "
            f"user={pg.username} password={pg.password}")


@pytest.fixture
def services(postgres):
    dsn = _dsn(postgres)
    with psycopg.connect(dsn, autocommit=True) as conn:
        conn.execute("DROP SCHEMA IF EXISTS research CASCADE")
    settings = Settings(_env_file=None, llm_provider="extractive", embedding_dim=512)
    s = build_services(settings, store=PgVectorStore(dsn, 512))
    yield s
    s.close()


def test_ingest_and_search_round_trip(services):
    services.ingestion.ingest(source="example.md", content=SAMPLE)

    hits = services.store.search(services.embedder.embed(["supplier currency risk"])[0], 2, services.embedder.model)
    assert hits[0].chunk.chunk_id == "example:2"
    assert hits[0].chunk.section == "Risks"
    assert hits[0].chunk.title == "Example Co Report"
    assert 0 < hits[0].score <= 1
    assert services.store.list_documents()[0].chunks == 3


def test_same_ranking_as_in_memory_store(services):
    # Exact cosine in memory vs HNSW in Postgres should agree on this tiny data set
    from app.retrieval.memory_store import InMemoryVectorStore
    from app.services import build_services as build

    memory = build(services.settings, store=InMemoryVectorStore())
    for s in (services, memory):
        s.ingestion.ingest(source="example.md", content=SAMPLE)
    query = services.embedder.embed(["revenue growth cloud"])[0]
    pg_ids = [h.chunk.chunk_id for h in services.store.search(query, 3, services.embedder.model)]
    mem_ids = [h.chunk.chunk_id for h in memory.store.search(query, 3, memory.embedder.model)]
    assert pg_ids == mem_ids


def test_reingest_replaces_chunks(services):
    services.ingestion.ingest(source="example.md", content=SAMPLE)
    services.ingestion.ingest(source="example.md", content="# Example Co Report\n\nShort now.")
    assert services.store.get_document("example").chunks == 1


def test_concurrent_ingest_of_same_document_leaves_one_consistent_copy(services):
    errors = []

    def run(i):
        try:
            services.ingestion.ingest(source="example.md", content=SAMPLE + f"\n\nVersion {i}.")
        except Exception as e:  # noqa: BLE001
            errors.append(e)

    threads = [threading.Thread(target=run, args=(i,)) for i in range(4)]
    for t in threads:
        t.start()
    for t in threads:
        t.join()

    assert errors == []
    doc = services.store.get_document("example")
    assert doc.chunks == 3  # one full copy, never a mix of versions


def test_dimension_mismatch_fails_fast(postgres, services):
    with pytest.raises(SchemaMismatchError):
        PgVectorStore(_dsn(postgres), 256)
