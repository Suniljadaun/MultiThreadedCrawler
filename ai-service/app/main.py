import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI

from app.api.errors import register_error_handlers
from app.api.request_id import RequestIdMiddleware
from app.api.routes import router
from app.config.settings import get_settings
from app.observability import AccessLogMiddleware, configure_logging, metrics_response
from app.services import Services, build_services


def create_app(services: Services | None = None) -> FastAPI:
    """Tests pass ready-made services; normal startup builds them from environment settings."""

    @asynccontextmanager
    async def lifespan(app: FastAPI):
        app.state.services = services or build_services(get_settings())
        yield
        app.state.services.close()

    app = FastAPI(title="FinIntel research assistant", version="0.1.0", lifespan=lifespan)
    # Last added runs first: RequestId wraps AccessLog, so access logs carry the request id
    app.add_middleware(AccessLogMiddleware)
    app.add_middleware(RequestIdMiddleware)
    register_error_handlers(app)
    app.include_router(router)
    app.add_api_route("/metrics", metrics_response, include_in_schema=False)
    return app


configure_logging(get_settings().log_format)
logging.getLogger("uvicorn.access").disabled = True  # replaced by app.access, which has the request id
app = create_app()
