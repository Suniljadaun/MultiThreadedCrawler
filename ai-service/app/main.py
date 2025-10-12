import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI

from app.api.errors import register_error_handlers
from app.api.request_id import RequestIdLogFilter, RequestIdMiddleware
from app.api.routes import router
from app.config.settings import get_settings
from app.services import Services, build_services


def configure_logging() -> None:
    handler = logging.StreamHandler()
    handler.addFilter(RequestIdLogFilter())
    handler.setFormatter(logging.Formatter("%(asctime)s %(levelname)s [%(request_id)s] %(name)s: %(message)s"))
    root = logging.getLogger()
    root.handlers[:] = [handler]
    root.setLevel(logging.INFO)


def create_app(services: Services | None = None) -> FastAPI:
    """Tests pass ready-made services; normal startup builds them from environment settings."""

    @asynccontextmanager
    async def lifespan(app: FastAPI):
        app.state.services = services or build_services(get_settings())
        yield
        app.state.services.close()

    app = FastAPI(title="FinIntel research assistant", version="0.1.0", lifespan=lifespan)
    app.add_middleware(RequestIdMiddleware)
    register_error_handlers(app)
    app.include_router(router)
    return app


configure_logging()
app = create_app()
