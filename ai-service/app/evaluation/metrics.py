import math
from collections.abc import Hashable, Sequence


def recall_at_k(retrieved: Sequence[Hashable], relevant: set, k: int) -> float:
    """Share of relevant items that appear in the top k."""
    if not relevant:
        raise ValueError("recall is undefined without relevant items")
    return len(set(retrieved[:k]) & relevant) / len(relevant)


def precision_at_k(retrieved: Sequence[Hashable], relevant: set, k: int) -> float:
    """Share of the top k results that are relevant (divided by k, even if fewer came back)."""
    if k <= 0:
        raise ValueError("k must be positive")
    return sum(1 for item in retrieved[:k] if item in relevant) / k


def reciprocal_rank(retrieved: Sequence[Hashable], relevant: set) -> float:
    for rank, item in enumerate(retrieved, start=1):
        if item in relevant:
            return 1.0 / rank
    return 0.0


def percentile(values: Sequence[float], p: float) -> float:
    """Nearest-rank percentile, no interpolation."""
    if not values:
        return 0.0
    ordered = sorted(values)
    index = max(0, math.ceil(p / 100 * len(ordered)) - 1)
    return ordered[index]


def mean(values: Sequence[float]) -> float:
    return sum(values) / len(values) if values else 0.0
