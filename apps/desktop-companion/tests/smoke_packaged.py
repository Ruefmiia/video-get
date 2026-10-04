"""Explicit packaging smoke test (not collected by pytest).

Run from repository root: python apps/desktop-companion/tests/smoke_packaged.py --download
All service state is isolated under .video-get; no existing user service is stopped.
"""

from __future__ import annotations

import os
import subprocess
import sys
import time
import uuid
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))
from video_get_companion.client import DesktopApi, download_payload


def main():
    root = Path(__file__).resolve().parents[3]
    executable = root / "apps/desktop-companion/dist/VideoGet.exe"
    data = root / ".video-get" / ("standalone-smoke-" + uuid.uuid4().hex)
    data.mkdir(parents=True)
    env = os.environ.copy()
    env.update(
        VIDEO_GET_DATA_DIR=str(data),
        VIDEO_GET_DOWNLOAD_DIR=str(data / "downloads"),
        VIDEO_GET_PORT="17401",
    )
    for flag in ["--check-desktop-runtime", "--check-login-runtime"]:
        result = subprocess.run(
            [str(executable), flag], env=env, timeout=60, creationflags=subprocess.CREATE_NO_WINDOW
        )
        if result.returncode:
            raise RuntimeError(f"Runtime check failed: {flag} ({result.returncode})")
        print(f"Passed: {flag}")
    process = subprocess.Popen(
        [str(executable), "--service"], env=env, creationflags=subprocess.CREATE_NO_WINDOW
    )
    api = DesktopApi(data, 17401)
    try:
        deadline = time.monotonic() + 60
        while True:
            try:
                version = api.request("/version")
                break
            except RuntimeError:
                if process.poll() is not None or time.monotonic() >= deadline:
                    raise RuntimeError("Isolated service did not start") from None
                time.sleep(1)
        assert version["ffmpeg_available"]
        assert not api.request("/sessions/bilibili")["configured"]
        media = api.request(
            "/analyze", "POST", {"url": "https://www.bilibili.com/video/BV1xx411c7mD"}
        )
        assert media["assets"][0]["formats"]
        print("Passed: packaged service, FFmpeg, authenticated API and Bilibili analysis")
        if "--download" in sys.argv:
            job = api.request("/downloads", "POST", download_payload(media, 0, 0))
            deadline = time.monotonic() + 300
            while job["state"] not in {"completed", "failed", "cancelled"}:
                if time.monotonic() >= deadline:
                    raise RuntimeError("Download smoke test timed out")
                time.sleep(2)
                job = api.request("/downloads/" + job["id"])
            assert job["state"] == "completed", job.get("error_code")
            output = Path(job["output_path"]).resolve()
            assert (
                output.is_relative_to((data / "downloads").resolve()) and output.stat().st_size > 0
            )
            probe = subprocess.run(
                [
                    str(executable.parent / "ffmpeg/bin/ffprobe.exe"),
                    "-v",
                    "error",
                    "-show_entries",
                    "stream=codec_type",
                    "-of",
                    "csv=p=0",
                    str(output),
                ],
                capture_output=True,
                text=True,
                check=True,
            )
            assert "video" in probe.stdout and "audio" in probe.stdout
            print(
                f"Passed: public video download + audio/video merge ({output.stat().st_size} bytes)"
            )
            output.unlink()  # Only our exact verified test output, never user media.
    finally:
        if process.poll() is None:
            subprocess.run(
                ["taskkill.exe", "/PID", str(process.pid), "/T", "/F"],
                capture_output=True,
                creationflags=subprocess.CREATE_NO_WINDOW,
            )
            process.wait(timeout=10)
    print("Smoke test complete; isolated service stopped.")


if __name__ == "__main__":
    main()
