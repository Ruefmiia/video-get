from __future__ import annotations

import hashlib
import re
from pathlib import Path
from typing import Any

import yt_dlp
from yt_dlp.utils import DownloadError

from video_get.domain.enums import PlatformId
from video_get.domain.errors import AppError, download_failed, media_not_found
from video_get.domain.models import Author, MediaAsset, MediaFormat, MediaInfo

from .base import CancellationToken, MatchResult, ProgressCallback


class DownloadCancelled(Exception):
    pass


class YtDlpProvider:
    id = "yt-dlp"
    _platforms = {PlatformId.X, PlatformId.INSTAGRAM}

    def supports(self, platform: PlatformId) -> bool:
        return platform in self._platforms

    def analyze(self, match: MatchResult) -> MediaInfo:
        options: dict[str, Any] = {"quiet": True, "no_warnings": True, "skip_download": True}
        try:
            with yt_dlp.YoutubeDL(options) as ydl:
                raw = ydl.extract_info(match.canonical_url, download=False)
        except DownloadError as exc:
            raise self._map_error(exc) from exc
        if not raw:
            raise media_not_found()
        entries = [entry for entry in raw.get("entries") or [] if entry]
        items = entries or [raw]
        assets = [self._asset(item, index) for index, item in enumerate(items)]
        assets = [asset for asset in assets if asset.formats]
        if not assets:
            raise media_not_found()
        uploader = raw.get("uploader") or raw.get("channel")
        return MediaInfo(
            source_url=match.canonical_url,
            canonical_url=raw.get("webpage_url") or match.canonical_url,
            platform=match.platform,
            title=raw.get("title") or raw.get("description"),
            author=Author(
                id=_string_or_none(raw.get("uploader_id")),
                name=_string_or_none(uploader),
                url=_string_or_none(raw.get("uploader_url")),
            ),
            thumbnail_url=_string_or_none(raw.get("thumbnail")),
            duration_seconds=_float_or_none(raw.get("duration")),
            assets=assets,
        )

    def download(
        self,
        match: MatchResult,
        format_id: str | None,
        output_dir: Path,
        progress: ProgressCallback,
        cancel: CancellationToken,
    ) -> Path:
        output_dir.mkdir(parents=True, exist_ok=True)

        def hook(update: dict[str, Any]) -> None:
            if cancel.cancelled:
                raise DownloadCancelled()
            progress(
                {
                    "downloaded_bytes": int(update.get("downloaded_bytes") or 0),
                    "total_bytes": _int_or_none(update.get("total_bytes")),
                    "estimated_total_bytes": _int_or_none(update.get("total_bytes_estimate")),
                    "speed_bytes_per_second": _float_or_none(update.get("speed")),
                    "eta_seconds": _float_or_none(update.get("eta")),
                }
            )

        options: dict[str, Any] = {
            "quiet": True,
            "no_warnings": True,
            "noplaylist": False,
            "outtmpl": str(output_dir / "%(title).120B [%(id)s].%(ext)s"),
            "progress_hooks": [hook],
            "restrictfilenames": True,
            "windowsfilenames": True,
        }
        if format_id:
            options["format"] = format_id
        try:
            before = set(output_dir.iterdir())
            with yt_dlp.YoutubeDL(options) as ydl:
                result = ydl.extract_info(match.canonical_url, download=True)
                prepared = Path(ydl.prepare_filename(result)) if result else None
            if prepared and prepared.exists():
                return prepared
            created = [
                path for path in output_dir.iterdir() if path not in before and path.is_file()
            ]
            if not created:
                raise download_failed("The downloader did not produce an output file.")
            return max(created, key=lambda path: path.stat().st_mtime)
        except DownloadCancelled:
            raise
        except DownloadError as exc:
            raise self._map_error(exc) from exc

    def _asset(self, raw: dict[str, Any], index: int) -> MediaAsset:
        formats: list[MediaFormat] = []
        for item in raw.get("formats") or []:
            vcodec = item.get("vcodec")
            acodec = item.get("acodec")
            has_video = bool(vcodec and vcodec != "none")
            has_audio = bool(acodec and acodec != "none")
            if not has_video:
                continue
            format_id = str(item.get("format_id") or "")
            if not format_id:
                continue
            height = _int_or_none(item.get("height"))
            ext = _string_or_none(item.get("ext"))
            label = f"{height}p {ext.upper() if ext else ''}".strip() if height else ext or "Video"
            formats.append(
                MediaFormat(
                    id=format_id,
                    label=label,
                    container=ext,
                    width=_int_or_none(item.get("width")),
                    height=height,
                    fps=_float_or_none(item.get("fps")),
                    video_codec=_string_or_none(vcodec),
                    audio_codec=_string_or_none(acodec),
                    has_video=has_video,
                    has_audio=has_audio,
                    estimated_bytes=_int_or_none(
                        item.get("filesize") or item.get("filesize_approx")
                    ),
                    requires_merge=has_video and not has_audio,
                )
            )
        raw_id = str(raw.get("id") or index)
        asset_id = hashlib.sha256(raw_id.encode()).hexdigest()[:16]
        return MediaAsset(
            id=f"asset-{asset_id}",
            type="video",
            thumbnail_url=_string_or_none(raw.get("thumbnail")),
            formats=formats,
        )

    @staticmethod
    def _map_error(exc: DownloadError) -> AppError:
        message = re.sub(r"\x1b\[[0-9;]*m", "", str(exc))
        lowered = message.lower()
        if "login" in lowered or "cookie" in lowered or "private" in lowered:
            return AppError("AUTHENTICATION_REQUIRED", "The source requires authentication.", 401)
        if "not found" in lowered or "does not exist" in lowered or "no video" in lowered:
            return media_not_found()
        if "429" in lowered or "rate limit" in lowered:
            return AppError("RATE_LIMITED", "The source is rate limiting requests.", 429, True)
        return download_failed("The source could not be downloaded.")


def _string_or_none(value: object) -> str | None:
    return str(value) if value is not None else None


def _int_or_none(value: object) -> int | None:
    if not isinstance(value, (str, bytes, bytearray, int, float)):
        return None
    try:
        return int(value)
    except (TypeError, ValueError):
        return None


def _float_or_none(value: object) -> float | None:
    if not isinstance(value, (str, bytes, bytearray, int, float)):
        return None
    try:
        return float(value)
    except (TypeError, ValueError):
        return None
