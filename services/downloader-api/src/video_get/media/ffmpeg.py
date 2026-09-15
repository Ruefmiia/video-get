from __future__ import annotations

import json
import shutil
import subprocess
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Protocol

from video_get.domain.errors import AppError


class CancellationView(Protocol):
    @property
    def cancelled(self) -> bool: ...


@dataclass(frozen=True, slots=True)
class FFmpegDiagnostics:
    available: bool
    ffmpeg_path: str | None
    ffprobe_path: str | None
    version: str | None
    error: str | None = None


class FFmpegToolchain:
    def __init__(self, ffmpeg_path: str | None = None, ffprobe_path: str | None = None) -> None:
        self.ffmpeg_path = ffmpeg_path or shutil.which("ffmpeg")
        self.ffprobe_path = ffprobe_path or shutil.which("ffprobe")

    def diagnostics(self) -> FFmpegDiagnostics:
        if not self.ffmpeg_path or not self.ffprobe_path:
            return FFmpegDiagnostics(
                available=False,
                ffmpeg_path=self.ffmpeg_path,
                ffprobe_path=self.ffprobe_path,
                version=None,
                error="FFmpeg and ffprobe are required for media post-processing.",
            )
        try:
            result = subprocess.run(
                [self.ffmpeg_path, "-version"],
                capture_output=True,
                text=True,
                timeout=5,
                check=True,
            )
        except (OSError, subprocess.SubprocessError):
            return FFmpegDiagnostics(
                False,
                self.ffmpeg_path,
                self.ffprobe_path,
                None,
                "FFmpeg failed to start.",
            )
        first_line = result.stdout.splitlines()[0] if result.stdout else "unknown"
        return FFmpegDiagnostics(True, self.ffmpeg_path, self.ffprobe_path, first_line)

    def probe(self, media_path: Path) -> dict[str, object]:
        if not self.ffprobe_path:
            raise AppError(
                "FFMPEG_UNAVAILABLE",
                "ffprobe is not installed or could not be located.",
                503,
            )
        try:
            result = subprocess.run(
                [
                    self.ffprobe_path,
                    "-v",
                    "error",
                    "-show_entries",
                    "format=duration,size:stream=codec_type,codec_name",
                    "-of",
                    "json",
                    str(media_path),
                ],
                capture_output=True,
                text=True,
                timeout=15,
                check=True,
            )
            parsed = json.loads(result.stdout)
        except (OSError, subprocess.SubprocessError, json.JSONDecodeError) as exc:
            raise AppError(
                "MEDIA_VALIDATION_FAILED", "The downloaded media is invalid.", 500
            ) from exc
        if not isinstance(parsed, dict):
            raise AppError("MEDIA_VALIDATION_FAILED", "The downloaded media is invalid.", 500)
        return parsed

    def merge(self, video: Path, audio: Path, output: Path, cancel: CancellationView) -> Path:
        if not self.ffmpeg_path:
            raise AppError(
                "FFMPEG_UNAVAILABLE",
                "FFmpeg is required to merge separate video and audio streams.",
                503,
            )
        process = subprocess.Popen(
            [
                self.ffmpeg_path,
                "-nostdin",
                "-y",
                "-i",
                str(video),
                "-i",
                str(audio),
                "-map",
                "0:v:0",
                "-map",
                "1:a:0",
                "-c",
                "copy",
                str(output),
            ],
            stdin=subprocess.DEVNULL,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.PIPE,
        )
        while process.poll() is None:
            if cancel.cancelled:
                process.terminate()
                try:
                    process.wait(timeout=3)
                except subprocess.TimeoutExpired:
                    process.kill()
                output.unlink(missing_ok=True)
                raise AppError("DOWNLOAD_CANCELLED", "Media processing was cancelled.", 409)
            time.sleep(0.05)
        if process.returncode != 0 or not output.exists():
            output.unlink(missing_ok=True)
            raise AppError("POST_PROCESSING_FAILED", "FFmpeg could not merge the media.", 500)
        return output
