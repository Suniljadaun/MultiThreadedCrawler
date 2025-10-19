from fastapi import APIRouter, Request, Response, status

from app.observability import INGESTED
from app.schemas.research import DocumentSummary, IngestRequest, IngestResponse, QueryRequest, QueryResponse
from app.services import Services

router = APIRouter(prefix="/api/v1")


def _services(request: Request) -> Services:
    return request.app.state.services


@router.get("/health")
def health(request: Request) -> dict:
    s = _services(request)
    return {
        "status": "UP",
        "vectorStore": s.settings.vector_store,
        "embeddingModel": s.embedder.model,
        "llmProvider": s.settings.llm_provider,
    }


@router.post("/research/documents", response_model=IngestResponse, status_code=status.HTTP_201_CREATED)
def ingest(body: IngestRequest, request: Request, response: Response) -> IngestResponse:
    result = _services(request).ingestion.ingest(
        source=body.source, content=body.content, document_id=body.document_id, title=body.title
    )
    INGESTED.labels("unchanged" if result.unchanged else "stored").inc()
    if result.unchanged:
        response.status_code = status.HTTP_200_OK  # same input as last time, nothing re-embedded
    return IngestResponse(document_id=result.document_id, title=result.title, chunks=result.chunks)


@router.get("/research/documents", response_model=list[DocumentSummary])
def list_documents(request: Request) -> list[DocumentSummary]:
    return [
        DocumentSummary(document_id=d.document_id, title=d.title, source=d.source, chunks=d.chunks)
        for d in _services(request).store.list_documents()
    ]


@router.post("/research/query", response_model=QueryResponse)
def query(body: QueryRequest, request: Request) -> QueryResponse:
    return _services(request).answers.answer(body.question, body.top_k)
