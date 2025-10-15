import pytest

from app.evaluation.metrics import mean, percentile, precision_at_k, recall_at_k, reciprocal_rank


def test_recall_counts_distinct_relevant_items_in_top_k():
    assert recall_at_k(["a", "x", "a", "b"], {"a", "b"}, k=3) == 0.5
    assert recall_at_k(["a", "x", "a", "b"], {"a", "b"}, k=4) == 1.0


def test_recall_needs_relevant_items():
    with pytest.raises(ValueError):
        recall_at_k(["a"], set(), k=1)


def test_precision_divides_by_k():
    assert precision_at_k(["a", "x"], {"a"}, k=5) == 0.2


def test_reciprocal_rank():
    assert reciprocal_rank(["x", "y", "a"], {"a"}) == pytest.approx(1 / 3)
    assert reciprocal_rank(["x"], {"a"}) == 0.0


def test_percentile_nearest_rank():
    values = [5, 1, 4, 2, 3]
    assert percentile(values, 50) == 3
    assert percentile(values, 95) == 5
    assert percentile([], 50) == 0.0


def test_mean():
    assert mean([1, 2, 3]) == 2
    assert mean([]) == 0.0
