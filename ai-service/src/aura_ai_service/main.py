import logging
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from typing import Literal

from fastapi import FastAPI
from pydantic import BaseModel

from aura_ai_service.config import Settings, get_settings

logger = logging.getLogger("aura.ai_service")


class HealthResponse(BaseModel):
    status: Literal["UP"]
    service: str
    version: str


def create_app(settings: Settings | None = None) -> FastAPI:
    resolved_settings = settings or get_settings()

    @asynccontextmanager
    async def lifespan(_: FastAPI) -> AsyncIterator[None]:
        logging.basicConfig(
            level=resolved_settings.log_level,
            format="%(asctime)s %(levelname)s %(name)s %(message)s",
        )
        logger.info("service_started", extra={"service": resolved_settings.service_name})
        yield
        logger.info("service_stopped", extra={"service": resolved_settings.service_name})

    app = FastAPI(
        title=resolved_settings.service_name,
        version=resolved_settings.service_version,
        docs_url=None,
        redoc_url=None,
        openapi_url=None,
        lifespan=lifespan,
    )

    @app.get("/api/v1/health", response_model=HealthResponse, tags=["operations"])
    async def health() -> HealthResponse:
        return HealthResponse(
            status="UP",
            service=resolved_settings.service_name,
            version=resolved_settings.service_version,
        )

    return app


app = create_app()


