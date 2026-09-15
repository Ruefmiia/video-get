from __future__ import annotations

from datetime import datetime
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, HttpUrl, field_validator

from .enums import JobState, PlatformId, PlatformStatus


class StrictModel(BaseModel):
    model_config = ConfigDict(extra="forbid")


class PlatformCapability(StrictModel):
    id: PlatformId
    display_name: str
    status: PlatformStatus
    available: bool
    url_patterns: list[str]
    capabilities: list[str]


class Author(StrictModel):
    id: str | None = None
    name: str | None = None
    url: str | None = None


class MediaFormat(StrictModel):
    id: str
    label: str
    container: str | None = None
    width: int | None = None
    height: int | None = None
    fps: float | None = None
    video_codec: str | None = None
    audio_codec: str | None = None
    has_video: bool
    has_audio: bool
    estimated_bytes: int | None = None
    requires_merge: bool = False


class MediaAsset(StrictModel):
    id: str
    type: Literal["video", "audio", "image"]
    thumbnail_url: str | None = None
    formats: list[MediaFormat] = Field(default_factory=list)


class AuthenticationInfo(StrictModel):
    required: bool = False
    mode: Literal["none", "browser_cookies", "cookie_file"] = "none"


class MediaInfo(StrictModel):
    source_url: str
    canonical_url: str
    platform: PlatformId
    title: str | None = None
    author: Author = Field(default_factory=Author)
    thumbnail_url: str | None = None
    duration_seconds: float | None = None
    assets: list[MediaAsset]
    authentication: AuthenticationInfo = Field(default_factory=AuthenticationInfo)


class AnalyzeRequest(StrictModel):
    url: HttpUrl


class DownloadOutput(StrictModel):
    mode: Literal["default_downloads_directory"] = "default_downloads_directory"


class DownloadRequest(StrictModel):
    url: HttpUrl
    asset_ids: list[str] = Field(default_factory=list, max_length=1)
    format_id: str | None = None
    output: DownloadOutput = Field(default_factory=DownloadOutput)

    @field_validator("format_id")
    @classmethod
    def validate_format_id(cls, value: str | None) -> str | None:
        if value is None:
            return None
        if (
            not value
            or len(value) > 128
            or not all(character.isalnum() or character in "._-" for character in value)
        ):
            raise ValueError("format_id must be an opaque format identifier")
        return value


class JobProgress(StrictModel):
    downloaded_bytes: int = 0
    total_bytes: int | None = None
    estimated_total_bytes: int | None = None
    speed_bytes_per_second: float | None = None
    eta_seconds: float | None = None
    progress: float | None = None


class DownloadJob(StrictModel):
    id: str
    source_url: str
    canonical_url: str
    platform: PlatformId
    state: JobState
    selected_format_id: str | None = None
    title: str | None = None
    output_path: str | None = None
    progress: JobProgress = Field(default_factory=JobProgress)
    error_code: str | None = None
    error_message: str | None = None
    retry_of_job_id: str | None = None
    created_at: datetime
    started_at: datetime | None = None
    finished_at: datetime | None = None
