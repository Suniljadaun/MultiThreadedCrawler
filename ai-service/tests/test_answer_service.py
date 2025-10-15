import pytest

from app.generation.answer_service import INSUFFICIENT_TEXT, OUT_OF_SCOPE_TEXT
from app.generation.base import LLMError
from app.retrieval.memory_store import InMemoryVectorStore
from app.services import build_services
from tests.conftest import SAMPLE, FakeLLM, memory_settings


def _services(llm=None, **settings):
    services = build_services(memory_settings(**settings), store=InMemoryVectorStore(), llm=llm)
    services.ingestion.ingest(source="example.md", content=SAMPLE)
    return services


def test_extractive_answer_copies_source_sentences_with_citations():
    response = _services().answers.answer("What was the revenue growth?")

    assert response.answer_type == "extractive"
    assert "Revenue was 120 million USD, up 5%. [1]" in response.answer
    assert response.sources[0].chunk_id == "example:1"
    assert response.sources[0].location == "section: Revenue"
    assert response.generation.provider == "extractive"


def test_no_evidence_above_min_score_gives_insufficient_evidence():
    response = _services().answers.answer("Weather forecast in Mumbai tomorrow?")
    assert response.answer_type == "insufficient_evidence"
    assert response.answer == INSUFFICIENT_TEXT
    assert response.sources == []


def test_personal_advice_is_declined_without_retrieval():
    llm = FakeLLM("should not be called")
    response = _services(llm).answers.answer("Should I buy Example Co shares?")
    assert response.answer_type == "out_of_scope"
    assert response.answer == OUT_OF_SCOPE_TEXT
    assert response.retrieval is None
    assert llm.calls == []


def test_llm_answer_with_valid_citations_is_returned_with_only_cited_sources():
    llm = FakeLLM("Revenue was 120 million USD, up 5% [1].")
    response = _services(llm, min_score=-1.0).answers.answer("What was the revenue?")

    assert response.answer_type == "generated"
    assert response.answer == "Revenue was 120 million USD, up 5% [1]."
    assert [s.ref for s in response.sources] == [1]
    assert response.generation.model == "fake-1"
    # The prompt holds the numbered sources
    assert "[1] Example Co Report (section: Revenue)" in llm.calls[0][1]


def test_llm_insufficient_marker_is_respected():
    response = _services(FakeLLM("INSUFFICIENT_EVIDENCE")).answers.answer("What was the revenue?")
    assert response.answer_type == "insufficient_evidence"


def test_llm_answer_without_citations_is_rejected():
    response = _services(FakeLLM("Revenue went up nicely.")).answers.answer("What was the revenue?")
    assert response.answer_type == "insufficient_evidence"


def test_llm_answer_citing_only_missing_sources_is_rejected():
    response = _services(FakeLLM("Revenue was 120 million [9].")).answers.answer("What was the revenue?")
    assert response.answer_type == "insufficient_evidence"


def test_llm_answer_with_invented_number_is_rejected():
    response = _services(FakeLLM("Revenue was 150 million USD [1].")).answers.answer("What was the revenue?")
    assert response.answer_type == "insufficient_evidence"


def test_llm_failure_is_raised_not_hidden():
    with pytest.raises(LLMError):
        _services(FakeLLM(error=LLMError("timeout"))).answers.answer("What was the revenue?")


def test_top_k_override_is_reported():
    response = _services(min_score=-1.0).answers.answer("revenue", top_k=2)
    assert response.retrieval.top_k == 2
    assert response.retrieval.candidates == 2
