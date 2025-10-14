from typing import Protocol


class LLMError(RuntimeError):
    """Provider failed or timed out. The API turns this into a 503, never into a made-up answer."""


class LLMClient(Protocol):
    provider: str
    model: str

    def complete(self, system: str, user: str) -> str: ...
