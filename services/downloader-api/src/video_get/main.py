from __future__ import annotations

import json
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from pathlib import Path

from fastapi import FastAPI

from video_get import __version__
from video_get.api.errors import app_error_handler
from video_get.api.routes import api_router, public_router
from video_get.config import Settings
from video_get.domain.errors import AppError
from video_get.jobs.manager import JobManager
from video_get.media.ffmpeg import FFmpegToolchain
from video_get.persistence.database import JobRepository
from video_get.providers.registry import ProviderRegistry
from video_get.providers.yt_dlp import YtDlpProvider


def create_app(settings: Settings | None = None) -> FastAPI:
    resolved = settings or Settings.load()
    toolchain = FFmpegToolchain()
    repository = JobRepository(resolved.database_path)
    registry = ProviderRegistry([YtDlpProvider(ffmpeg_location=toolchain.ffmpeg_path)])
    manager = JobManager(
        registry=registry,
        repository=repository,
        download_dir=resolved.download_dir,
        data_dir=resolved.data_dir,
        max_workers=resolved.max_workers,
        media_toolchain=toolchain,
    )

    @asynccontextmanager
    async def lifespan(_: FastAPI) -> AsyncIterator[None]:
        yield
        manager.shutdown()

    application = FastAPI(title="Video Get API", version=__version__, lifespan=lifespan)
    application.state.settings = resolved
    application.state.repository = repository
    application.state.registry = registry
    application.state.job_manager = manager
    application.state.ffmpeg = toolchain
    application.state.ffmpeg_diagnostics = toolchain.diagnostics()
    application.add_exception_handler(AppError, app_error_handler)  # type: ignore[arg-type]
    application.include_router(public_router)
    application.include_router(api_router)
    return application


app = create_app()


def export_openapi(path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(app.openapi(), ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
