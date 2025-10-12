from dataclasses import dataclass

from app.config.settings import Settings
from app.embeddings.base import EmbeddingClient
from app.embeddings.hash_embedding import HashEmbeddingClient
from app.embeddings.openai_embedding import OpenAIEmbeddingClient
from app.generation.answer_service import AnswerService
from app.generation.base import LLMClient
from app.generation.openai_llm import OpenAIChatClient
from app.ingestion.pipeline import IngestionService
from app.retrieval.memory_store import InMemoryVectorStore
from app.retrieval.retriever import Retriever
from app.retrieval.store import VectorStore


@dataclass
class Services:
    settings: Settings
    store: VectorStore
    embedder: EmbeddingClient
    retriever: Retriever
    ingestion: IngestionService
    answers: AnswerService

    def close(self) -> None:
        self.store.close()


def build_embedder(s: Settings) -> EmbeddingClient:
    if s.embedding_provider == "openai":
        return OpenAIEmbeddingClient(s.openai_base_url, s.openai_api_key, s.embedding_model, s.embedding_dim)
    return HashEmbeddingClient(dim=s.embedding_dim, model=f"{s.embedding_model}-{s.embedding_dim}")


def build_llm(s: Settings) -> LLMClient | None:
    if s.llm_provider == "openai":
        return OpenAIChatClient(s.openai_base_url, s.openai_api_key, s.llm_model, s.llm_timeout_seconds)
    return None


def build_store(s: Settings) -> VectorStore:
    if s.vector_store == "memory":
        return InMemoryVectorStore()
    from app.retrieval.pgvector_store import PgVectorStore  # only import the DB driver when used
    return PgVectorStore(s.postgres_dsn(), s.embedding_dim)


def build_services(s: Settings, store: VectorStore | None = None, embedder: EmbeddingClient | None = None,
                   llm: LLMClient | None = None) -> Services:
    """Wire everything from settings. Tests pass their own store/embedder/llm."""
    store = store or build_store(s)
    embedder = embedder or build_embedder(s)
    llm = llm if llm is not None else build_llm(s)
    retriever = Retriever(store, embedder, s.min_score)
    return Services(
        settings=s,
        store=store,
        embedder=embedder,
        retriever=retriever,
        ingestion=IngestionService(store, embedder, s.chunk_max_chars),
        answers=AnswerService(retriever, llm, s.top_k),
    )
