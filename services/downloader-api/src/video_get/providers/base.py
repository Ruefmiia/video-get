from __future__ import annotations

from collections.abc import Callable
from dataclasses import dataclass
from pathlib import Path
from typing import Protocol

from video_get.domain.enums import PlatformId
from video_get.domain.models import MediaInfo

ProgressCallback = Callable[[dict[str, object]], None]


@dataclass(frozen=True, slots=True)
class MatchResult:
    platform: PlatformId
    canonical_url: str


class CancellationToken(Protocol):
    @property
    def cancelled(self) -> bool: ...


class Provider(Protocol):
    id: str

    def supports(self, platform: PlatformId) -> bool: ...

    def analyze(self, match: MatchResult) -> MediaInfo: ...

    def download(
        self,
        match: MatchResult,
        asset_ids: list[str],
        format_id: str | None,
        output_dir: Path,
        progress: ProgressCallback,
        cancel: CancellationToken,
    ) -> Path: ...
