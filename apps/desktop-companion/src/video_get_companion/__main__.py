from __future__ import annotations

import os
import sys
from pathlib import Path
from typing import TextIO


def _configure_bundled_ffmpeg() -> Path | None:
    if not getattr(sys, "frozen", False):
        return None
    ffmpeg_bin = Path(sys.executable).resolve().parent / "ffmpeg" / "bin"
    if not (ffmpeg_bin / "ffmpeg.exe").is_file() or not (ffmpeg_bin / "ffprobe.exe").is_file():
        return None
    os.environ["PATH"] = os.pathsep.join([str(ffmpeg_bin), os.environ.get("PATH", "")])
    return ffmpeg_bin


def _prepare_service_streams() -> list[TextIO]:
    """Provide real streams when a windowed PyInstaller executable has none."""
    opened: list[TextIO] = []
    data_dir = Path(os.getenv("VIDEO_GET_DATA_DIR", ".video-get")).resolve()
    data_dir.mkdir(parents=True, exist_ok=True)
    if sys.stdout is None:
        stdout = (data_dir / "service.stdout.log").open("a", encoding="utf-8", buffering=1)
        sys.stdout = stdout
        opened.append(stdout)
    if sys.stderr is None:
        stderr = (data_dir / "service.stderr.log").open("a", encoding="utf-8", buffering=1)
        sys.stderr = stderr
        opened.append(stderr)
    return opened


def main() -> int:
    _configure_bundled_ffmpeg()
    executable_name = Path(sys.executable).stem.lower()
    if "nativehost" in executable_name or "--native-messaging" in sys.argv:
        from video_get_companion.native_host import run_native_host

        return run_native_host()
    if "--service" in sys.argv:
        import uvicorn

        streams = _prepare_service_streams()
        port = int(os.getenv("VIDEO_GET_PORT", "17382"))
        try:
            uvicorn.run(
                "video_get.main:app",
                host="127.0.0.1",
                port=port,
                reload=False,
                use_colors=False,
            )
        finally:
            for stream in streams:
                stream.close()
        return 0
    from video_get_companion.app import CompanionApp

    return CompanionApp().run()


if __name__ == "__main__":
    raise SystemExit(main())
