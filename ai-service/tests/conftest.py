from pathlib import Path

import pytest

from app.config.settings import Settings
from app.generation.base import LLMError
from app.retrieval.memory_store import InMemoryVectorStore
from app.services import build_services

REPO_ROOT = Path(__file__).resolve().parents[2]
DOCS = REPO_ROOT / "test-data" / "documents"

SAMPLE = """# Example Co Report

Intro line about Example Co.

## Revenue

Revenue was 120 million USD, up 5%. Growth came from cloud products.

## Risks

A key supplier could fail. Currency swings affect 40% of sales.
"""


class FakeLLM:
    """Returns a fixed reply (or raises) and records the prompts it got."""

    provider = "fake"
    model = "fake-1"

    def __init__(self, reply: str = "", error: Exception | None = None):
        self.reply = reply
        self.error = error
        self.calls: list[tuple[str, str]] = []

    def complete(self, system: str, user: str) -> str:
        self.calls.append((system, user))
        if self.error:
            raise self.error
        return self.reply


def memory_settings(**overrides) -> Settings:
    # _env_file=None: tests never read a developer's local .env
    return Settings(_env_file=None, vector_store="memory", llm_provider="extractive", **overrides)


@pytest.fixture
def settings() -> Settings:
    return memory_settings()


@pytest.fixture
def services(settings):
    return build_services(settings, store=InMemoryVectorStore())


@pytest.fixture
def llm_timeout():
    return FakeLLM(error=LLMError("LLM timed out"))
