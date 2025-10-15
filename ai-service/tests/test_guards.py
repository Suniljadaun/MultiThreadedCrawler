import pytest

from app.generation.citations import check_answer
from app.generation.scope import is_personal_advice

SOURCES = ["Revenue was 1,200 million USD, up 12%.", "Margin was 18.5%."]


def test_valid_citations_are_kept():
    checked = check_answer("Revenue rose 12% [1]. Margin was 18.5% [2].", SOURCES, "q")
    assert checked.cited == [1, 2]
    assert checked.invalid == []
    assert checked.unsupported_numbers == []


def test_citation_to_missing_source_is_removed():
    checked = check_answer("Revenue rose 12% [1][7].", SOURCES, "q")
    assert checked.cited == [1]
    assert checked.invalid == [7]
    assert "[7]" not in checked.text


def test_number_not_in_cited_sources_is_flagged():
    # 18.5 exists, but only in source 2, which this sentence does not cite
    checked = check_answer("Revenue was 1,200 million and margin 18.5% [1].", SOURCES, "q")
    assert checked.unsupported_numbers == ["18.5"]


def test_numbers_from_the_question_are_allowed():
    checked = check_answer("In 2025 revenue was 1,200 million [1].", SOURCES, "What was revenue in 2025?")
    assert checked.unsupported_numbers == []


def test_answer_without_citations_has_none():
    assert check_answer("Revenue went up.", SOURCES, "q").cited == []


@pytest.mark.parametrize("question", [
    "Should I buy ACME shares now?",
    "should i sell globex",
    "Is it a good time to invest in Initech?",
    "Which stock should I pick?",
    "Can you recommend what to buy?",
    "How much should I invest in ACME?",
])
def test_personal_advice_is_detected(question):
    assert is_personal_advice(question)


@pytest.mark.parametrize("question", [
    "What were ACME's revenue drivers?",
    "Did Globex buy back shares?",
    "What does Initech say about selling its old product?",
])
def test_research_questions_are_not_advice(question):
    assert not is_personal_advice(question)
