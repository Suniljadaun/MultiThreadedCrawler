import logging
import re
import uuid
from contextvars import ContextVar
from datetime import datetime, timezone

from starlette.middleware.base import BaseHTTPMiddleware
from starlette.requests import Request
from starlette.responses import JSONResponse

log = logging.getLogger(__name__)

HEADER = "X-Request-ID"
_VALID = re.compile(r"^[A-Za-z0-9._-]{1,100}$")
_request_id: ContextVar[str | None] = ContextVar("request_id", default=None)


def current_request_id() -> str | None:
    return _request_id.get()


class RequestIdMiddleware(BaseHTTPMiddleware):
    """Reuse the caller's X-Request-ID (if sane) or create one, and echo it back.
    Also turns any unhandled error into a 500 in the standard error shape."""

    async def dispatch(self, request: Request, call_next):
        incoming = request.headers.get(HEADER, "")
        request_id = incoming if _VALID.match(incoming) else str(uuid.uuid4())
        token = _request_id.set(request_id)
        try:
            response = await call_next(request)
        except Exception:
            log.exception("Unexpected error")
            response = JSONResponse(status_code=500, content={
                "timestamp": datetime.now(timezone.utc).isoformat(), "status": 500, "error": "INTERNAL_ERROR",
                "message": "Unexpected error", "path": request.url.path, "requestId": request_id,
            })
        finally:
            _request_id.reset(token)
        response.headers[HEADER] = request_id
        return response


class RequestIdLogFilter(logging.Filter):
    def filter(self, record: logging.LogRecord) -> bool:
        record.request_id = current_request_id() or "-"
        return True
