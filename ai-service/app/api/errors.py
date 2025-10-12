import logging
from datetime import datetime, timezone

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from starlette.exceptions import HTTPException as StarletteHTTPException

from app.api.request_id import current_request_id
from app.embeddings.base import EmbeddingError
from app.generation.base import LLMError
from app.ingestion.pipeline import EmptyDocumentError

log = logging.getLogger(__name__)


def error_body(request: Request, status: int, error: str, message: str) -> dict:
    # Same shape as the backend's ApiError
    return {
        "timestamp": datetime.now(timezone.utc).isoformat(),
        "status": status,
        "error": error,
        "message": message,
        "path": request.url.path,
        "requestId": current_request_id(),
    }


def _json(request: Request, status: int, error: str, message: str) -> JSONResponse:
    return JSONResponse(status_code=status, content=error_body(request, status, error, message))


def register_error_handlers(app: FastAPI) -> None:
    @app.exception_handler(RequestValidationError)
    async def validation(request: Request, exc: RequestValidationError):
        first = exc.errors()[0] if exc.errors() else {}
        field = ".".join(str(p) for p in first.get("loc", []) if p != "body")
        return _json(request, 400, "VALIDATION_ERROR", f"{field}: {first.get('msg', 'invalid request')}")

    @app.exception_handler(StarletteHTTPException)
    async def http_error(request: Request, exc: StarletteHTTPException):
        return _json(request, exc.status_code, "NOT_FOUND" if exc.status_code == 404 else "HTTP_ERROR",
                     str(exc.detail))

    @app.exception_handler(EmptyDocumentError)
    async def empty_document(request: Request, exc: EmptyDocumentError):
        return _json(request, 422, "EMPTY_DOCUMENT", str(exc))

    @app.exception_handler(LLMError)
    async def llm_error(request: Request, exc: LLMError):
        log.warning("LLM unavailable: %s", exc)
        return _json(request, 503, "LLM_UNAVAILABLE", "The answer model is unavailable, try again later")

    @app.exception_handler(EmbeddingError)
    async def embedding_error(request: Request, exc: EmbeddingError):
        log.warning("Embedding provider unavailable: %s", exc)
        return _json(request, 503, "EMBEDDING_UNAVAILABLE", "The embedding model is unavailable, try again later")

    try:
        import psycopg
        from psycopg_pool import PoolTimeout
    except ImportError:
        return

    async def database_error(request: Request, exc: Exception):
        log.warning("Database unavailable: %s", exc)
        return _json(request, 503, "DATABASE_UNAVAILABLE", "The document store is unavailable, try again later")

    app.add_exception_handler(psycopg.OperationalError, database_error)
    app.add_exception_handler(PoolTimeout, database_error)
