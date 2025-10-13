import hashlib
import math
import re

_TOKEN = re.compile(r"[a-z0-9]+(?:[.,][0-9]+)*")
_STOPWORDS = frozenset(
    "a an and are as at be by for from has have in is it its of on or that the this to was were what which "
    "who will with how did does do why when where".split()
)


def tokenize(text: str) -> list[str]:
    return [t for t in _TOKEN.findall(text.lower()) if t not in _STOPWORDS]


class HashEmbeddingClient:
    """Deterministic feature-hashing embedding (unigrams + bigrams).

    It measures word overlap, not meaning, so "profit" and "earnings" do not match.
    It exists so the service and its tests run offline with no model download (ADR-007).
    """

    def __init__(self, dim: int = 512, model: str = "hash-v1"):
        self.dim = dim
        self.model = model

    def embed(self, texts: list[str]) -> list[list[float]]:
        return [self._embed_one(t) for t in texts]

    def _embed_one(self, text: str) -> list[float]:
        tokens = tokenize(text)
        features = tokens + [f"{a}_{b}" for a, b in zip(tokens, tokens[1:])]
        vector = [0.0] * self.dim
        for feature in features:
            # blake2b, not hash(): Python's hash() changes between processes
            digest = hashlib.blake2b(feature.encode(), digest_size=8).digest()
            value = int.from_bytes(digest, "big")
            sign = 1.0 if value & 1 else -1.0
            vector[(value >> 1) % self.dim] += sign
        norm = math.sqrt(sum(v * v for v in vector))
        return [v / norm for v in vector] if norm else vector
