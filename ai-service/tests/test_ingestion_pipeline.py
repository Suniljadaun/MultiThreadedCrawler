import pytest

from app.ingestion.pipeline import EmptyDocumentError, document_id_from_source
from tests.conftest import SAMPLE


def test_document_id_is_a_slug_of_the_file_name():
    assert document_id_from_source("docs\\ACME Annual_Report 2025.md") == "acme-annual-report-2025"


def test_ingest_stores_chunks_with_ids_and_locations(services):
    result = services.ingestion.ingest(source="example.md", content=SAMPLE)

    assert (result.document_id, result.title, result.chunks, result.unchanged) == ("example", "Example Co Report", 3, False)
    doc = services.store.get_document("example")
    assert doc.chunks == 3
    hits = services.store.search(services.embedder.embed(["supplier currency"])[0], 3, services.embedder.model)
    top = hits[0].chunk
    assert (top.chunk_id, top.section, top.location) == ("example:2", "Risks", "section: Risks")


def test_same_input_twice_is_not_re_embedded(services):
    services.ingestion.ingest(source="example.md", content=SAMPLE)
    again = services.ingestion.ingest(source="example.md", content=SAMPLE)
    assert again.unchanged
    assert again.chunks == 3


def test_changed_content_replaces_all_old_chunks(services):
    services.ingestion.ingest(source="example.md", content=SAMPLE)
    result = services.ingestion.ingest(source="example.md", content="# Example Co Report\n\nOnly one line now.")
    assert not result.unchanged
    assert services.store.get_document("example").chunks == 1
    hits = services.store.search(services.embedder.embed(["supplier"])[0], 10, services.embedder.model)
    assert [h.chunk.chunk_id for h in hits] == ["example:0"]


def test_explicit_id_and_title_win(services):
    result = services.ingestion.ingest(source="x.md", content=SAMPLE, document_id="custom-id", title="Custom")
    assert (result.document_id, result.title) == ("custom-id", "Custom")


def test_document_with_only_headings_is_rejected(services):
    with pytest.raises(EmptyDocumentError):
        services.ingestion.ingest(source="empty.md", content="# Title\n\n## Heading\n<!-- nothing -->")


def test_search_ignores_chunks_from_another_embedding_model(services):
    services.ingestion.ingest(source="example.md", content=SAMPLE)
    assert services.store.search(services.embedder.embed(["revenue"])[0], 5, "other-model") == []
