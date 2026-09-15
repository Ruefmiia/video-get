from __future__ import annotations

import time
from datetime import UTC, datetime
from pathlib import Path

import pytest
from video_get.domain.enums import TERMINAL_JOB_STATES, JobState, PlatformId
from video_get.domain.errors import AppError
from video_get.domain.models import Author, DownloadJob, DownloadRequest, MediaAsset, MediaInfo
from video_get.jobs.manager import JobManager
from video_get.jobs.state_machine import InvalidJobTransition, validate_transition
from video_get.persistence.database import JobRepository
from video_get.providers.base import CancellationToken, MatchResult, ProgressCallback
from video_get.providers.registry import ProviderRegistry
from video_get.providers.yt_dlp import DownloadCancelled


class FakeProvider:
    id = "fake"

    def __init__(self, *, fail: bool = False, block: bool = False) -> None:
        self.fail = fail
        self.block = block

    def supports(self, platform: PlatformId) -> bool:
        return platform is PlatformId.X

    def analyze(self, match: MatchResult) -> MediaInfo:
        if self.fail:
            raise AppError("DOWNLOAD_FAILED", "expected failure", 502)
        return MediaInfo(
            source_url=match.canonical_url,
            canonical_url=match.canonical_url,
            platform=match.platform,
            title="A clip",
            author=Author(name="owner"),
            assets=[MediaAsset(id="asset-1", type="video")],
        )

    def download(
        self,
        match: MatchResult,
        asset_ids: list[str],
        format_id: str | None,
        output_dir: Path,
        progress: ProgressCallback,
        cancel: CancellationToken,
    ) -> Path:
        del match, asset_ids, format_id
        output_dir.mkdir(parents=True, exist_ok=True)
        progress({"downloaded_bytes": 5, "total_bytes": 10})
        while self.block and not cancel.cancelled:
            time.sleep(0.005)
        if cancel.cancelled:
            raise DownloadCancelled()
        output = output_dir / "clip.mp4"
        output.write_bytes(b"video")
        return output


def make_manager(tmp_path: Path, provider: FakeProvider) -> tuple[JobManager, JobRepository]:
    repository = JobRepository(tmp_path / "jobs.db")
    manager = JobManager(
        ProviderRegistry([provider]), repository, tmp_path / "downloads", tmp_path, max_workers=2
    )
    (tmp_path / "downloads").mkdir(exist_ok=True)
    return manager, repository


def wait_for_terminal(repository: JobRepository, job_id: str) -> DownloadJob:
    deadline = time.monotonic() + 3
    while time.monotonic() < deadline:
        job = repository.get(job_id)
        assert job is not None
        if job.state in TERMINAL_JOB_STATES:
            return job
        time.sleep(0.01)
    raise AssertionError("job did not finish")


def test_state_machine_allows_only_forward_transitions() -> None:
    validate_transition(JobState.QUEUED, JobState.ANALYZING)
    validate_transition(JobState.PROCESSING, JobState.COMPLETED)
    with pytest.raises(InvalidJobTransition):
        validate_transition(JobState.COMPLETED, JobState.DOWNLOADING)
    with pytest.raises(InvalidJobTransition):
        validate_transition(JobState.DOWNLOADING, JobState.ANALYZING)


def test_repository_rejects_direct_and_unknown_updates(tmp_path: Path) -> None:
    repository = JobRepository(tmp_path / "jobs.db")
    repository.create(
        DownloadJob(
            id="job",
            source_url="https://x.com/user/status/123",
            canonical_url="https://x.com/user/status/123",
            platform=PlatformId.X,
            state=JobState.QUEUED,
            created_at=datetime.now(UTC),
        )
    )
    with pytest.raises(ValueError, match="transition"):
        repository.update("job", state=JobState.FAILED)
    with pytest.raises(ValueError, match="Unsupported"):
        repository.update("job", made_up=True)
    with pytest.raises(KeyError):
        repository.get("missing") or repository.update("missing", title="x")


def test_job_manager_completes_and_avoids_overwrite(tmp_path: Path) -> None:
    manager, repository = make_manager(tmp_path, FakeProvider())
    existing = tmp_path / "downloads" / "clip.mp4"
    existing.write_bytes(b"existing")
    request = DownloadRequest.model_validate({"url": "https://x.com/user/status/123"})
    created = manager.create(request)
    completed = wait_for_terminal(repository, created.id)
    manager.shutdown()
    assert completed.state is JobState.COMPLETED
    assert completed.progress.progress == 1.0
    assert completed.output_path is not None
    assert Path(completed.output_path).name != "clip.mp4"
    assert existing.read_bytes() == b"existing"


def test_job_manager_cancel_wins_worker_race(tmp_path: Path) -> None:
    manager, repository = make_manager(tmp_path, FakeProvider(block=True))
    created = manager.create(
        DownloadRequest.model_validate({"url": "https://x.com/user/status/123"})
    )
    deadline = time.monotonic() + 2
    while time.monotonic() < deadline:
        current = repository.get(created.id)
        if current and current.state is JobState.DOWNLOADING:
            break
        time.sleep(0.005)
    cancelled = manager.cancel(created.id)
    assert cancelled is not None
    final = wait_for_terminal(repository, created.id)
    manager.shutdown()
    assert final.state is JobState.CANCELLED
    assert manager.cancel(created.id).state is JobState.CANCELLED  # type: ignore[union-attr]


def test_job_manager_records_failure_and_retry(tmp_path: Path) -> None:
    manager, repository = make_manager(tmp_path, FakeProvider(fail=True))
    original = manager.create(
        DownloadRequest.model_validate({"url": "https://x.com/user/status/123"})
    )
    failed = wait_for_terminal(repository, original.id)
    assert failed.state is JobState.FAILED
    assert failed.error_code == "DOWNLOAD_FAILED"
    retried = manager.retry(original.id)
    assert retried is not None
    assert retried.retry_of_job_id == original.id
    wait_for_terminal(repository, retried.id)
    assert manager.retry("missing") is None
    assert manager.cancel("missing") is None
    manager.shutdown()
