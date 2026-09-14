from __future__ import annotations

import shutil
import threading
import uuid
from concurrent.futures import Future, ThreadPoolExecutor
from datetime import UTC, datetime
from pathlib import Path

from video_get.domain.enums import TERMINAL_JOB_STATES, JobState
from video_get.domain.errors import AppError
from video_get.domain.models import DownloadJob, DownloadRequest
from video_get.persistence.database import JobRepository
from video_get.providers.base import MatchResult, Provider
from video_get.providers.registry import ProviderRegistry
from video_get.providers.yt_dlp import DownloadCancelled


class ThreadCancellationToken:
    def __init__(self) -> None:
        self._event = threading.Event()

    @property
    def cancelled(self) -> bool:
        return self._event.is_set()

    def cancel(self) -> None:
        self._event.set()


class JobManager:
    def __init__(
        self,
        registry: ProviderRegistry,
        repository: JobRepository,
        download_dir: Path,
        data_dir: Path,
        max_workers: int = 2,
    ) -> None:
        self.registry = registry
        self.repository = repository
        self.download_dir = download_dir
        self.temp_root = data_dir / "tmp"
        self.temp_root.mkdir(parents=True, exist_ok=True)
        self._executor = ThreadPoolExecutor(max_workers=max_workers, thread_name_prefix="video-get")
        self._tokens: dict[str, ThreadCancellationToken] = {}
        self._futures: dict[str, Future[None]] = {}
        self._lock = threading.Lock()

    def create(self, request: DownloadRequest, retry_of: str | None = None) -> DownloadJob:
        provider, match = self.registry.resolve(str(request.url))
        job = DownloadJob(
            id=uuid.uuid4().hex,
            source_url=str(request.url),
            canonical_url=match.canonical_url,
            platform=match.platform,
            state=JobState.QUEUED,
            selected_format_id=request.format_id,
            retry_of_job_id=retry_of,
            created_at=datetime.now(UTC),
        )
        self.repository.create(job)
        token = ThreadCancellationToken()
        with self._lock:
            self._tokens[job.id] = token
            self._futures[job.id] = self._executor.submit(
                self._run, job.id, provider, match, request.format_id, token
            )
        return job

    def cancel(self, job_id: str) -> DownloadJob | None:
        job = self.repository.get(job_id)
        if job is None:
            return None
        if job.state in TERMINAL_JOB_STATES:
            return job
        with self._lock:
            token = self._tokens.get(job_id)
            if token:
                token.cancel()
        return self.repository.update(
            job_id, state=JobState.CANCELLED, finished_at=datetime.now(UTC)
        )

    def retry(self, job_id: str) -> DownloadJob | None:
        previous = self.repository.get(job_id)
        if previous is None:
            return None
        request = DownloadRequest.model_validate(
            {"url": previous.source_url, "format_id": previous.selected_format_id}
        )
        return self.create(request, retry_of=previous.id)

    def _run(
        self,
        job_id: str,
        provider: Provider,
        match: MatchResult,
        format_id: str | None,
        token: ThreadCancellationToken,
    ) -> None:
        job_temp = self.temp_root / job_id
        try:
            self.repository.update(job_id, state=JobState.ANALYZING, started_at=datetime.now(UTC))
            info = provider.analyze(match)
            if token.cancelled:
                raise DownloadCancelled()
            self.repository.update(job_id, state=JobState.DOWNLOADING, title=info.title)

            def on_progress(update: dict[str, object]) -> None:
                total = _number_or_none(
                    update.get("total_bytes") or update.get("estimated_total_bytes")
                )
                downloaded = int(_number_or_none(update.get("downloaded_bytes")) or 0)
                ratio = min(downloaded / total, 1.0) if total else None
                self.repository.update(
                    job_id,
                    downloaded_bytes=downloaded,
                    total_bytes=update.get("total_bytes"),
                    estimated_total_bytes=update.get("estimated_total_bytes"),
                    speed_bytes_per_second=update.get("speed_bytes_per_second"),
                    eta_seconds=update.get("eta_seconds"),
                    progress=ratio,
                )

            output = provider.download(match, format_id, job_temp, on_progress, token)
            if token.cancelled:
                raise DownloadCancelled()
            self.repository.update(job_id, state=JobState.PROCESSING)
            destination = self._unique_destination(output.name)
            shutil.move(str(output), destination)
            self.repository.update(
                job_id,
                state=JobState.COMPLETED,
                output_path=str(destination),
                finished_at=datetime.now(UTC),
                progress=1.0,
            )
        except DownloadCancelled:
            current = self.repository.get(job_id)
            if current and current.state not in TERMINAL_JOB_STATES:
                self.repository.update(
                    job_id, state=JobState.CANCELLED, finished_at=datetime.now(UTC)
                )
        except AppError as exc:
            self.repository.update(
                job_id,
                state=JobState.FAILED,
                error_code=exc.code,
                error_message=exc.message,
                finished_at=datetime.now(UTC),
            )
        except Exception:
            self.repository.update(
                job_id,
                state=JobState.FAILED,
                error_code="INTERNAL_ERROR",
                error_message="The download task failed unexpectedly.",
                finished_at=datetime.now(UTC),
            )
        finally:
            shutil.rmtree(job_temp, ignore_errors=True)
            with self._lock:
                self._tokens.pop(job_id, None)
                self._futures.pop(job_id, None)

    def _unique_destination(self, name: str) -> Path:
        candidate = self.download_dir / name
        if not candidate.exists():
            return candidate
        suffix = uuid.uuid4().hex[:8]
        return candidate.with_name(f"{candidate.stem}-{suffix}{candidate.suffix}")


def _number_or_none(value: object) -> int | float | None:
    if isinstance(value, (int, float)):
        return value
    if isinstance(value, str):
        try:
            return float(value)
        except ValueError:
            return None
    return None
