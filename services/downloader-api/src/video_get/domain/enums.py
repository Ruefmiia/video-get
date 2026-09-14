from __future__ import annotations

from enum import StrEnum


class PlatformId(StrEnum):
    X = "x"
    INSTAGRAM = "instagram"
    THREADS = "threads"


class PlatformStatus(StrEnum):
    AVAILABLE = "available"
    PLANNED = "planned"
    DISABLED = "disabled"


class JobState(StrEnum):
    QUEUED = "queued"
    ANALYZING = "analyzing"
    DOWNLOADING = "downloading"
    PROCESSING = "processing"
    COMPLETED = "completed"
    FAILED = "failed"
    CANCELLED = "cancelled"


TERMINAL_JOB_STATES = {JobState.COMPLETED, JobState.FAILED, JobState.CANCELLED}
