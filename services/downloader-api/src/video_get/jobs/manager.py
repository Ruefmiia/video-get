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
from video_get.jobs.state_machine import InvalidJobTransition
from video_get.media.ffmpeg import FFmpegToolchain
from video_get.media.filenames import unique_destination
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
        media_toolchain: FFmpegToolchain | None = None,
    ) -> None:
        self.registry = registry
        self.repository = repository
        self.download_dir = download_dir
        self.temp_root = data_dir / "tmp"
        self.temp_root.mkdir(parents=True, exist_ok=True)
        self.media_toolchain = media_toolchain
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
                self._run,
                job.id,
                provider,
                match,
                request.asset_ids,
                request.format_id,
                token,
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
        try:
            return self.repository.transition(
                job_id, JobState.CANCELLED, finished_at=datetime.now(UTC)
            )
        except InvalidJobTransition:
            return self.repository.get(job_id)

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
        asset_ids: list[str],
        format_id: str | None,
        token: ThreadCancellationToken,
    ) -> None:
        job_temp = self.temp_root / job_id
        try:
            self.repository.transition(job_id, JobState.ANALYZING, started_at=datetime.now(UTC))
            info = provider.analyze(match)
            if token.cancelled:
                raise DownloadCancelled()
            self.repository.transition(job_id, JobState.DOWNLOADING, title=info.title)

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

            output = provider.download(match, asset_ids, format_id, job_temp, on_progress, token)
            if token.cancelled:
                raise DownloadCancelled()
            self.repository.transition(job_id, JobState.PROCESSING)
            if self.media_toolchain is not None:
                self.media_toolchain.probe(output)
            destination = unique_destination(self.download_dir, output.name)
            shutil.move(str(output), destination)
            self.repository.transition(
                job_id,
                JobState.COMPLETED,
                output_path=str(destination),
                finished_at=datetime.now(UTC),
                progress=1.0,
            )
        except DownloadCancelled:
            current = self.repository.get(job_id)
            if current and current.state not in TERMINAL_JOB_STATES:
                self.repository.transition(
                    job_id, JobState.CANCELLED, finished_at=datetime.now(UTC)
                )
        except AppError as exc:
            self._fail(job_id, exc.code, exc.message)
        except InvalidJobTransition:
            # Cancellation may win a race with a worker transition. A terminal
            # state is authoritative and must never be overwritten.
            pass
        except Exception:
            self._fail(job_id, "INTERNAL_ERROR", "The download task failed unexpectedly.")
        finally:
            shutil.rmtree(job_temp, ignore_errors=True)
            with self._lock:
                self._tokens.pop(job_id, None)
                self._futures.pop(job_id, None)

    def shutdown(self, wait: bool = True) -> None:
        with self._lock:
            for token in self._tokens.values():
                token.cancel()
        self._executor.shutdown(wait=wait, cancel_futures=True)

    def _fail(self, job_id: str, code: str, message: str) -> None:
        current = self.repository.get(job_id)
        if current is None or current.state in TERMINAL_JOB_STATES:
            return
        try:
            self.repository.transition(
                job_id,
                JobState.FAILED,
                error_code=code,
                error_message=message,
                finished_at=datetime.now(UTC),
            )
        except InvalidJobTransition:
            pass


def _number_or_none(value: object) -> int | float | None:
    if isinstance(value, (int, float)):
        return value
    if isinstance(value, str):
        try:
            return float(value)
        except ValueError:
            return None
    return None
