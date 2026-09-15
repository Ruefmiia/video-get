from __future__ import annotations

import sys
from pathlib import Path

from video_get_companion.__main__ import _configure_bundled_ffmpeg, _prepare_service_streams


def test_prepare_service_streams_for_windowed_executable(tmp_path: Path, monkeypatch) -> None:
    original_stdout = sys.stdout
    original_stderr = sys.stderr
    monkeypatch.setenv("VIDEO_GET_DATA_DIR", str(tmp_path))
    monkeypatch.setattr(sys, "stdout", None)
    monkeypatch.setattr(sys, "stderr", None)
    streams = _prepare_service_streams()
    try:
        assert len(streams) == 2
        assert sys.stdout is not None
        assert sys.stderr is not None
        print("stdout ready", file=sys.stdout)
        print("stderr ready", file=sys.stderr)
    finally:
        for stream in streams:
            stream.close()
        monkeypatch.setattr(sys, "stdout", original_stdout)
        monkeypatch.setattr(sys, "stderr", original_stderr)
    assert "stdout ready" in (tmp_path / "service.stdout.log").read_text("utf-8")
    assert "stderr ready" in (tmp_path / "service.stderr.log").read_text("utf-8")


def test_bundled_ffmpeg_is_skipped_outside_frozen_build(monkeypatch) -> None:
    monkeypatch.delattr(sys, "frozen", raising=False)
    assert _configure_bundled_ffmpeg() is None


def test_entrypoint_uses_package_safe_absolute_import() -> None:
    source = Path(__file__).parents[1] / "src" / "video_get_companion" / "__main__.py"
    content = source.read_text(encoding="utf-8")
    assert "from video_get_companion.app import CompanionApp" in content
    assert "from .app import CompanionApp" not in content
