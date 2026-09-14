from __future__ import annotations

from datetime import UTC, datetime
from pathlib import Path

from video_get.domain.enums import JobState, PlatformId
from video_get.domain.models import DownloadJob
from video_get.persistence.database import JobRepository


def test_repository_persists_and_recovers_interrupted_job(tmp_path: Path) -> None:
    database = tmp_path / "jobs.db"
    repository = JobRepository(database)
    repository.create(
        DownloadJob(
            id="job-1",
            source_url="https://x.com/user/status/123",
            canonical_url="https://x.com/user/status/123",
            platform=PlatformId.X,
            state=JobState.DOWNLOADING,
            created_at=datetime.now(UTC),
        )
    )

    reopened = JobRepository(database)
    job = reopened.get("job-1")

    assert job is not None
    assert job.state is JobState.FAILED
    assert job.error_code == "SERVICE_INTERRUPTED"
