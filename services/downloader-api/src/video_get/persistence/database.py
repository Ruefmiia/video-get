from __future__ import annotations

from datetime import UTC, datetime
from pathlib import Path
from typing import Any

from sqlalchemy import DateTime, Float, Integer, String, create_engine, event, select
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column, sessionmaker

from video_get.domain.enums import JobState, PlatformId
from video_get.domain.models import DownloadJob, JobProgress


class Base(DeclarativeBase):
    pass


class DownloadJobEntity(Base):
    __tablename__ = "download_jobs"

    id: Mapped[str] = mapped_column(String(64), primary_key=True)
    source_url: Mapped[str] = mapped_column(String(2048))
    canonical_url: Mapped[str] = mapped_column(String(2048))
    platform: Mapped[str] = mapped_column(String(32), index=True)
    state: Mapped[str] = mapped_column(String(32), index=True)
    selected_format_id: Mapped[str | None] = mapped_column(String(256), nullable=True)
    title: Mapped[str | None] = mapped_column(String(512), nullable=True)
    output_path: Mapped[str | None] = mapped_column(String(2048), nullable=True)
    downloaded_bytes: Mapped[int] = mapped_column(Integer, default=0)
    total_bytes: Mapped[int | None] = mapped_column(Integer, nullable=True)
    estimated_total_bytes: Mapped[int | None] = mapped_column(Integer, nullable=True)
    speed_bytes_per_second: Mapped[float | None] = mapped_column(Float, nullable=True)
    eta_seconds: Mapped[float | None] = mapped_column(Float, nullable=True)
    progress: Mapped[float | None] = mapped_column(Float, nullable=True)
    error_code: Mapped[str | None] = mapped_column(String(64), nullable=True)
    error_message: Mapped[str | None] = mapped_column(String(1024), nullable=True)
    retry_of_job_id: Mapped[str | None] = mapped_column(String(64), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    started_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    finished_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)


class JobRepository:
    def __init__(self, database_path: Path) -> None:
        self.engine = create_engine(
            f"sqlite:///{database_path.as_posix()}", connect_args={"check_same_thread": False}
        )
        event.listen(self.engine, "connect", self._configure_sqlite)
        self._sessions = sessionmaker(self.engine, expire_on_commit=False)
        Base.metadata.create_all(self.engine)
        self.fail_interrupted_jobs()

    @staticmethod
    def _configure_sqlite(connection: Any, _: Any) -> None:
        cursor = connection.cursor()
        cursor.execute("PRAGMA journal_mode=WAL")
        cursor.execute("PRAGMA foreign_keys=ON")
        cursor.close()

    def create(self, job: DownloadJob) -> DownloadJob:
        with self._sessions.begin() as session:
            session.add(self._to_entity(job))
        return job

    def get(self, job_id: str) -> DownloadJob | None:
        with self._sessions() as session:
            entity = session.get(DownloadJobEntity, job_id)
            return self._to_model(entity) if entity else None

    def list(self, limit: int = 50) -> list[DownloadJob]:
        with self._sessions() as session:
            rows = session.scalars(
                select(DownloadJobEntity).order_by(DownloadJobEntity.created_at.desc()).limit(limit)
            )
            return [self._to_model(row) for row in rows]

    def update(self, job_id: str, **values: object) -> DownloadJob:
        with self._sessions.begin() as session:
            entity = session.get(DownloadJobEntity, job_id)
            if entity is None:
                raise KeyError(job_id)
            for key, value in values.items():
                setattr(entity, key, value.value if hasattr(value, "value") else value)
        model = self.get(job_id)
        if model is None:
            raise KeyError(job_id)
        return model

    def fail_interrupted_jobs(self) -> None:
        active = {
            JobState.QUEUED.value,
            JobState.ANALYZING.value,
            JobState.DOWNLOADING.value,
            JobState.PROCESSING.value,
        }
        with self._sessions.begin() as session:
            rows = session.scalars(
                select(DownloadJobEntity).where(DownloadJobEntity.state.in_(active))
            )
            for row in rows:
                row.state = JobState.FAILED.value
                row.error_code = "SERVICE_INTERRUPTED"
                row.error_message = "The local service stopped before the task completed."
                row.finished_at = datetime.now(UTC)

    @staticmethod
    def _to_entity(job: DownloadJob) -> DownloadJobEntity:
        return DownloadJobEntity(
            id=job.id,
            source_url=job.source_url,
            canonical_url=job.canonical_url,
            platform=job.platform.value,
            state=job.state.value,
            selected_format_id=job.selected_format_id,
            title=job.title,
            output_path=job.output_path,
            downloaded_bytes=job.progress.downloaded_bytes,
            total_bytes=job.progress.total_bytes,
            estimated_total_bytes=job.progress.estimated_total_bytes,
            speed_bytes_per_second=job.progress.speed_bytes_per_second,
            eta_seconds=job.progress.eta_seconds,
            progress=job.progress.progress,
            error_code=job.error_code,
            error_message=job.error_message,
            retry_of_job_id=job.retry_of_job_id,
            created_at=job.created_at,
            started_at=job.started_at,
            finished_at=job.finished_at,
        )

    @staticmethod
    def _to_model(row: DownloadJobEntity) -> DownloadJob:
        return DownloadJob(
            id=row.id,
            source_url=row.source_url,
            canonical_url=row.canonical_url,
            platform=PlatformId(row.platform),
            state=JobState(row.state),
            selected_format_id=row.selected_format_id,
            title=row.title,
            output_path=row.output_path,
            progress=JobProgress(
                downloaded_bytes=row.downloaded_bytes,
                total_bytes=row.total_bytes,
                estimated_total_bytes=row.estimated_total_bytes,
                speed_bytes_per_second=row.speed_bytes_per_second,
                eta_seconds=row.eta_seconds,
                progress=row.progress,
            ),
            error_code=row.error_code,
            error_message=row.error_message,
            retry_of_job_id=row.retry_of_job_id,
            created_at=row.created_at,
            started_at=row.started_at,
            finished_at=row.finished_at,
        )
