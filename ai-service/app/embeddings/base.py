from typing import Protocol


class EmbeddingError(RuntimeError):
    pass


class EmbeddingClient(Protocol):
    """Turns text into unit-length vectors. Business code only sees this interface."""

    model: str
    dim: int

    def embed(self, texts: list[str]) -> list[list[float]]: ...
