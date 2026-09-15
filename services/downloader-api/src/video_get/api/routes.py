from __future__ import annotations

from typing import cast

from fastapi import APIRouter, Depends, HTTPException, Query, Request, status

from video_get import __version__
from video_get.api.dependencies import require_local_token
from video_get.domain.models import (
    AnalyzeRequest,
    DownloadJob,
    DownloadRequest,
    MediaInfo,
    PlatformCapability,
)
from video_get.jobs.manager import JobManager
from video_get.media.ffmpeg import FFmpegDiagnostics
from video_get.persistence.database import JobRepository
from video_get.providers.registry import ProviderRegistry

public_router = APIRouter()
api_router = APIRouter(prefix="/api/v1", dependencies=[Depends(require_local_token)])


@public_router.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@api_router.get("/version")
def version(request: Request) -> dict[str, object]:
    import yt_dlp.version

    diagnostics = cast(FFmpegDiagnostics, request.app.state.ffmpeg_diagnostics)
    return {
        "service_version": __version__,
        "api_version": "1",
        "yt_dlp_version": yt_dlp.version.__version__,
        "ffmpeg_available": diagnostics.available,
        "ffmpeg_version": diagnostics.version,
        "ffmpeg_error": diagnostics.error,
    }


@api_router.get("/platforms", response_model=list[PlatformCapability])
def platforms(request: Request) -> list[PlatformCapability]:
    registry = cast(ProviderRegistry, request.app.state.registry)
    return registry.capabilities()


@api_router.post("/analyze", response_model=MediaInfo)
def analyze(payload: AnalyzeRequest, request: Request) -> MediaInfo:
    registry = cast(ProviderRegistry, request.app.state.registry)
    provider, match = registry.resolve(str(payload.url))
    return provider.analyze(match)


@api_router.post("/downloads", response_model=DownloadJob, status_code=status.HTTP_202_ACCEPTED)
def create_download(payload: DownloadRequest, request: Request) -> DownloadJob:
    manager = cast(JobManager, request.app.state.job_manager)
    return manager.create(payload)


@api_router.get("/downloads", response_model=list[DownloadJob])
def list_downloads(
    request: Request, limit: int = Query(default=50, ge=1, le=200)
) -> list[DownloadJob]:
    repository = cast(JobRepository, request.app.state.repository)
    return repository.list(limit=limit)


@api_router.get("/downloads/{job_id}", response_model=DownloadJob)
def get_download(job_id: str, request: Request) -> DownloadJob:
    repository = cast(JobRepository, request.app.state.repository)
    job = repository.get(job_id)
    if job is None:
        raise HTTPException(status_code=404, detail="Download job not found")
    return job


@api_router.delete("/downloads/{job_id}", response_model=DownloadJob)
def cancel_download(job_id: str, request: Request) -> DownloadJob:
    manager = cast(JobManager, request.app.state.job_manager)
    job = manager.cancel(job_id)
    if job is None:
        raise HTTPException(status_code=404, detail="Download job not found")
    return job


@api_router.post("/downloads/{job_id}/retry", response_model=DownloadJob, status_code=202)
def retry_download(job_id: str, request: Request) -> DownloadJob:
    manager = cast(JobManager, request.app.state.job_manager)
    job = manager.retry(job_id)
    if job is None:
        raise HTTPException(status_code=404, detail="Download job not found")
    return job
