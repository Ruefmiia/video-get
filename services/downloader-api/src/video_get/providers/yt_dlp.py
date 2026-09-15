from __future__ import annotations

import hashlib
import re
import threading
import time
from collections import OrderedDict
from dataclasses import dataclass
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


@dataclass(frozen=True, slots=True)
class _FormatSelection:
    canonical_url: str
    asset_id: str
    playlist_index: int
    expression: str
    merge_output_format: str | None
    expires_at: float


class YtDlpProvider:
    id = "yt-dlp"
    _platforms = {PlatformId.X, PlatformId.INSTAGRAM}

    def __init__(
        self,
        ffmpeg_location: str | None = None,
        *,
        format_token_ttl_seconds: float = 15 * 60,
        max_format_tokens: int = 2048,
    ) -> None:
        self._format_tokens: OrderedDict[str, _FormatSelection] = OrderedDict()
        self._format_lock = threading.Lock()
        self._ffmpeg_location = ffmpeg_location
        self._format_token_ttl_seconds = format_token_ttl_seconds
        self._max_format_tokens = max_format_tokens

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
        assets = [self._asset(item, index, match.canonical_url) for index, item in enumerate(items)]
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
        asset_ids: list[str],
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
            "outtmpl": str(
                output_dir
                / (
                    f"{match.platform.value}_"
                    "%(uploader_id,uploader,channel_id,channel|unknown)s_"
                    "%(upload_date|unknown)s_%(id)s_%(title).80B.%(ext)s"
                )
            ),
            "progress_hooks": [hook],
            "restrictfilenames": True,
            "windowsfilenames": True,
        }
        if self._ffmpeg_location:
            options["ffmpeg_location"] = self._ffmpeg_location
        if format_id:
            with self._format_lock:
                self._prune_format_tokens(time.monotonic())
                selection = self._format_tokens.get(format_id)
            if selection is None or selection.canonical_url != match.canonical_url:
                raise AppError(
                    "FORMAT_NOT_AVAILABLE",
                    "The selected format is unavailable or has expired.",
                    422,
                )
            if asset_ids and asset_ids != [selection.asset_id]:
                raise AppError(
                    "ASSET_NOT_AVAILABLE",
                    "The selected media item is unavailable or has expired.",
                    422,
                )
            options["format"] = selection.expression
            options["playlist_items"] = str(selection.playlist_index + 1)
            if selection.merge_output_format:
                options["merge_output_format"] = selection.merge_output_format
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

    def _asset(self, raw: dict[str, Any], index: int, canonical_url: str) -> MediaAsset:
        formats: list[MediaFormat] = []
        for item in raw.get("formats") or []:
            vcodec = item.get("vcodec")
            acodec = item.get("acodec")
            has_video = bool(vcodec and vcodec != "none")
            has_audio = bool(acodec and acodec != "none")
            if not has_video:
                continue
            raw_format_id = str(item.get("format_id") or "")
            if not raw_format_id:
                continue
            raw_asset_id = str(raw.get("id") or index)
            token_source = f"{canonical_url}\0{raw_asset_id}\0{raw_format_id}"
            format_id = f"fmt_{hashlib.sha256(token_source.encode()).hexdigest()[:24]}"
            expression = raw_format_id
            merge_output_format = None
            if has_video and not has_audio:
                expression = (
                    f"{raw_format_id}+bestaudio[ext=m4a]/{raw_format_id}+bestaudio/{raw_format_id}"
                )
                if item.get("ext") == "mp4":
                    merge_output_format = "mp4"
            asset_id = self._asset_id(raw, index)
            with self._format_lock:
                now = time.monotonic()
                self._prune_format_tokens(now)
                self._format_tokens[format_id] = _FormatSelection(
                    canonical_url=canonical_url,
                    asset_id=asset_id,
                    playlist_index=index,
                    expression=expression,
                    merge_output_format=merge_output_format,
                    expires_at=now + self._format_token_ttl_seconds,
                )
                self._format_tokens.move_to_end(format_id)
                while len(self._format_tokens) > self._max_format_tokens:
                    self._format_tokens.popitem(last=False)
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
        return MediaAsset(
            id=self._asset_id(raw, index),
            type="video",
            thumbnail_url=_string_or_none(raw.get("thumbnail")),
            formats=formats,
        )

    @staticmethod
    def _map_error(exc: DownloadError) -> AppError:
        message = re.sub(r"\x1b\[[0-9;]*m", "", str(exc))
        lowered = message.lower()
        if "timed out" in lowered or "timeout" in lowered:
            return AppError(
                "SOURCE_TIMEOUT",
                "The source did not respond in time.",
                504,
                True,
            )
        if any(
            marker in lowered
            for marker in (
                "connection failed",
                "connection refused",
                "name resolution",
                "network is unreachable",
            )
        ):
            return AppError(
                "SOURCE_UNREACHABLE",
                "The source could not be reached from this network.",
                502,
                True,
            )
        if "login" in lowered or "cookie" in lowered or "private" in lowered:
            return AppError("AUTHENTICATION_REQUIRED", "The source requires authentication.", 401)
        if any(
            marker in lowered
            for marker in ("not found", "does not exist", "no video", "unavailable", "removed")
        ):
            return media_not_found()
        if "429" in lowered or "rate limit" in lowered:
            return AppError("RATE_LIMITED", "The source is rate limiting requests.", 429, True)
        if "403" in lowered or "forbidden" in lowered:
            return AppError(
                "SOURCE_FORBIDDEN",
                "The source refused access to this public media.",
                403,
            )
        return download_failed("The source could not be downloaded.")

    @staticmethod
    def _asset_id(raw: dict[str, Any], index: int) -> str:
        raw_id = str(raw.get("id") or index)
        digest = hashlib.sha256(f"{index}\0{raw_id}".encode()).hexdigest()[:16]
        return f"asset-{digest}"

    def _prune_format_tokens(self, now: float) -> None:
        expired = [
            token for token, selection in self._format_tokens.items() if selection.expires_at <= now
        ]
        for token in expired:
            self._format_tokens.pop(token, None)


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
