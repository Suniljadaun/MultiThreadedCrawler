from typing import Literal

from pydantic import BaseModel, ConfigDict, Field
from pydantic.alias_generators import to_camel


class CamelModel(BaseModel):
    # JSON uses camelCase like the backend; Python code uses snake_case
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)


class IngestRequest(CamelModel):
    document_id: str | None = Field(default=None, pattern=r"^[a-z0-9][a-z0-9-]{0,99}$")
    title: str | None = Field(default=None, max_length=300)
    source: str = Field(min_length=1, max_length=500)
    content: str = Field(min_length=1, max_length=2_000_000)


class IngestResponse(CamelModel):
    document_id: str
    title: str
    chunks: int


class DocumentSummary(CamelModel):
    document_id: str
    title: str
    source: str
    chunks: int


class QueryRequest(CamelModel):
    question: str = Field(min_length=3, max_length=1000)
    top_k: int | None = Field(default=None, ge=1, le=20)


class Source(CamelModel):
    # Everything here is copied from the stored chunk, never generated
    ref: int
    document_id: str
    chunk_id: str
    title: str
    location: str
    score: float
    snippet: str


class RetrievalInfo(CamelModel):
    top_k: int
    candidates: int
    min_score: float
    latency_ms: float


class GenerationInfo(CamelModel):
    provider: str
    model: str
    latency_ms: float


class QueryResponse(CamelModel):
    # generated = LLM text; extractive = sentences copied from sources;
    # insufficient_evidence / out_of_scope = no answer given
    answer_type: Literal["generated", "extractive", "insufficient_evidence", "out_of_scope"]
    answer: str
    sources: list[Source]
    retrieval: RetrievalInfo | None
    generation: GenerationInfo | None
    disclaimer: str
