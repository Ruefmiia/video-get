from __future__ import annotations

import json
import subprocess
from pathlib import Path
from types import SimpleNamespace

import pytest
from video_get.domain.errors import AppError
from video_get.media.ffmpeg import FFmpegToolchain
from video_get.media.filenames import sanitize_filename, unique_destination
from video_get.security.redaction import redact_mapping, redact_text, redact_url


@pytest.mark.parametrize(
    ("unsafe", "expected"),
    [
        ("../hello?.mp4", "hello_.mp4"),
        ("CON.txt", "_CON.txt"),
        ("  .  ", "download"),
        ("a   b.mp4", "a b.mp4"),
    ],
)
def test_sanitize_filename(unsafe: str, expected: str) -> None:
    assert sanitize_filename(unsafe) == expected


def test_filename_is_limited_and_destination_never_overwrites(tmp_path: Path) -> None:
    assert len(sanitize_filename(f"{'a' * 300}.mp4")) == 180
    existing = tmp_path / "clip.mp4"
    existing.write_bytes(b"existing")
    destination = unique_destination(tmp_path, "../../clip.mp4")
    assert destination.parent == tmp_path.resolve()
    assert destination.name.startswith("clip-")
    assert destination.suffix == ".mp4"


def test_redaction_hides_credentials_and_preserves_safe_values() -> None:
    url = redact_url("https://cdn.example/video?signature=secret&quality=hd#fragment")
    assert "secret" not in url
    assert "quality=hd" in url
    assert "fragment" not in url
    assert redact_text("Authorization: Bearer abc.def-123") == ("Authorization: Bearer [REDACTED]")
    result = redact_mapping(
        {
            "Cookie": "session=secret",
            "nested": {"token": "secret"},
            "url": "https://example.test/a?sig=secret",
            "message": "Bearer another-secret",
            "count": 2,
        }
    )
    assert result["Cookie"] == "[REDACTED]"
    assert result["nested"] == {"token": "[REDACTED]"}
    assert "secret" not in str(result)
    assert result["count"] == 2
    assert redact_url("http://[") == "[REDACTED_URL]"


def test_ffmpeg_diagnostics_reports_missing_binary() -> None:
    diagnostics = FFmpegToolchain("missing-ffmpeg", "missing-ffprobe").diagnostics()
    assert diagnostics.available is False
    assert diagnostics.error == "FFmpeg failed to start."
    missing = FFmpegToolchain()
    missing.ffmpeg_path = None
    missing.ffprobe_path = None
    assert missing.diagnostics().available is False


def test_ffprobe_parses_json(monkeypatch: pytest.MonkeyPatch, tmp_path: Path) -> None:
    payload = {"streams": [{"codec_type": "video"}], "format": {"duration": "1"}}

    def fake_run(*_: object, **__: object) -> SimpleNamespace:
        return SimpleNamespace(stdout=json.dumps(payload))

    monkeypatch.setattr(subprocess, "run", fake_run)
    toolchain = FFmpegToolchain("ffmpeg", "ffprobe")
    assert toolchain.probe(tmp_path / "clip.mp4") == payload


def test_ffprobe_has_clear_missing_and_invalid_errors(
    monkeypatch: pytest.MonkeyPatch, tmp_path: Path
) -> None:
    toolchain = FFmpegToolchain("ffmpeg", "ffprobe")
    toolchain.ffprobe_path = None
    with pytest.raises(AppError, match="ffprobe") as missing:
        toolchain.probe(tmp_path / "clip.mp4")
    assert missing.value.code == "FFMPEG_UNAVAILABLE"

    toolchain.ffprobe_path = "ffprobe"
    monkeypatch.setattr(
        subprocess,
        "run",
        lambda *_args, **_kwargs: SimpleNamespace(stdout="not json"),
    )
    with pytest.raises(AppError) as invalid:
        toolchain.probe(tmp_path / "clip.mp4")
    assert invalid.value.code == "MEDIA_VALIDATION_FAILED"


def test_ffmpeg_merge_success_and_failure(monkeypatch: pytest.MonkeyPatch, tmp_path: Path) -> None:
    output = tmp_path / "output.mp4"

    class FakeProcess:
        returncode = 0

        def __init__(self, command: list[str], **_: object) -> None:
            Path(command[-1]).write_bytes(b"merged")

        def poll(self) -> int:
            return 0

    monkeypatch.setattr(subprocess, "Popen", FakeProcess)
    token = SimpleNamespace(cancelled=False)
    result = FFmpegToolchain("ffmpeg", "ffprobe").merge(
        tmp_path / "video.mp4", tmp_path / "audio.m4a", output, token
    )
    assert result.read_bytes() == b"merged"

    class FailedProcess(FakeProcess):
        returncode = 1

        def __init__(self, command: list[str], **_: object) -> None:
            pass

    monkeypatch.setattr(subprocess, "Popen", FailedProcess)
    with pytest.raises(AppError) as failed:
        FFmpegToolchain("ffmpeg", "ffprobe").merge(
            tmp_path / "video.mp4", tmp_path / "audio.m4a", output, token
        )
    assert failed.value.code == "POST_PROCESSING_FAILED"


def test_ffmpeg_merge_requires_binary(tmp_path: Path) -> None:
    toolchain = FFmpegToolchain()
    toolchain.ffmpeg_path = None
    with pytest.raises(AppError) as error:
        toolchain.merge(
            tmp_path / "video.mp4",
            tmp_path / "audio.m4a",
            tmp_path / "out.mp4",
            SimpleNamespace(cancelled=False),
        )
    assert error.value.code == "FFMPEG_UNAVAILABLE"
