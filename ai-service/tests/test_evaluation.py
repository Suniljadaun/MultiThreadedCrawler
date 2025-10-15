"""Locks the retrieval quality of the offline baseline (hash embeddings + extractive answers).

These numbers are deterministic. If a change moves them, the test fails on purpose:
update docs/ai-evaluation.md and the expected values together, with the reason.
"""
from app.evaluation.run import evaluate
from tests.conftest import DOCS, REPO_ROOT, memory_settings

EVAL_FILE = REPO_ROOT / "test-data" / "eval" / "research-eval.json"


def test_baseline_evaluation_is_stable():
    report = evaluate(memory_settings(), EVAL_FILE, DOCS, k=5)
    summary = report["summary"]

    assert summary["recall@5"] == 1.0
    assert summary["precision@5"] == 0.2
    assert summary["mrr"] == 0.9
    assert summary["answeredWhenAnswerable"] == "13/13"
    assert summary["answerContainsExpectedFacts"] == "10/13"
    assert summary["abstainedWhenUnanswerable"] == "2/3"
    assert summary["declinedAdvice"] == "1/1"


def test_known_weak_spots_stay_visible():
    rows = {r["id"]: r for r in evaluate(memory_settings(), EVAL_FILE, DOCS, k=5)["examples"]}

    # "operating margin" matches the segment sections before the company-wide figure
    assert rows["q02"]["rr"] == 0.2
    # Lexical match on "Globex revenue" answers a question the documents cannot answer
    assert rows["u01"]["answerType"] == "extractive"
