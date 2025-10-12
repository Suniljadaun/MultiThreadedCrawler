from functools import lru_cache
from typing import Literal

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """All config comes from environment variables (or ai-service/.env). No secrets in code."""

    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    # Vector store: pgvector for real runs, memory for quick local tries and tests
    vector_store: Literal["pgvector", "memory"] = "pgvector"
    postgres_host: str = "localhost"
    postgres_port: int = 5433
    postgres_db: str = "finintel"
    postgres_user: str = "finintel"
    postgres_password: str = "finintel"

    # Embeddings: "hash" works offline with no model download (lexical baseline, see ADR-007)
    embedding_provider: Literal["hash", "openai"] = "hash"
    embedding_model: str = "hash-v1"
    embedding_dim: int = Field(default=512, ge=8, le=2000)  # pgvector HNSW index limit

    # Generation: "extractive" needs no LLM; "openai" is any OpenAI-compatible API (OpenAI, Ollama, ...)
    llm_provider: Literal["extractive", "openai"] = "extractive"
    llm_model: str = "gpt-4o-mini"
    llm_timeout_seconds: float = 30.0

    # Shared by both OpenAI-compatible clients
    openai_base_url: str = "https://api.openai.com/v1"
    openai_api_key: str = ""

    # Retrieval
    top_k: int = Field(default=5, ge=1, le=20)
    min_score: float = Field(default=0.15, ge=-1.0, le=1.0)

    # Chunking
    chunk_max_chars: int = Field(default=800, ge=200)

    def postgres_dsn(self) -> str:
        return (
            f"host={self.postgres_host} port={self.postgres_port} dbname={self.postgres_db} "
            f"user={self.postgres_user} password={self.postgres_password}"
        )


@lru_cache
def get_settings() -> Settings:
    return Settings()
