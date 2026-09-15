from __future__ import annotations

import json
import os
import platform
import socket
import subprocess
import sys
import time
import urllib.error
import urllib.request
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import IO


@dataclass(frozen=True, slots=True)
class ServiceStatus:
    running: bool
    health: str
    pid: int | None
    port: int


class ServiceController:
    def __init__(self, data_dir: Path, port: int = 17382) -> None:
        self.data_dir = data_dir.resolve()
        self.port = port
        self.process: subprocess.Popen[bytes] | None = None
        self._stdout: IO[bytes] | None = None
        self._stderr: IO[bytes] | None = None

    @property
    def download_dir(self) -> Path:
        return self.data_dir / "downloads"

    def status(self) -> ServiceStatus:
        try:
            with urllib.request.urlopen(
                f"http://127.0.0.1:{self.port}/health", timeout=1
            ) as response:
                payload = json.loads(response.read().decode("utf-8"))
            healthy = response.status == 200 and payload.get("status") == "ok"
        except (OSError, ValueError, urllib.error.URLError):
            healthy = False
        pid = self.process.pid if self.process and self.process.poll() is None else None
        return ServiceStatus(healthy, "ok" if healthy else "unavailable", pid, self.port)

    def port_available(self) -> bool:
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as probe:
            probe.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            try:
                probe.bind(("127.0.0.1", self.port))
            except OSError:
                return False
        return True

    def start(self, timeout: float = 12) -> ServiceStatus:
        current = self.status()
        if current.running:
            return current
        if not self.port_available():
            raise RuntimeError(f"端口 {self.port} 已被其他程序占用。")
        self.data_dir.mkdir(parents=True, exist_ok=True)
        self.download_dir.mkdir(parents=True, exist_ok=True)
        self._stdout = (self.data_dir / "service.stdout.log").open("ab")
        self._stderr = (self.data_dir / "service.stderr.log").open("ab")
        env = os.environ.copy()
        env.update(
            {
                "VIDEO_GET_DATA_DIR": str(self.data_dir),
                "VIDEO_GET_DOWNLOAD_DIR": str(self.download_dir),
                "VIDEO_GET_PORT": str(self.port),
            }
        )
        bundled_ffmpeg = Path(sys.executable).resolve().parent / "ffmpeg" / "bin"
        if getattr(sys, "frozen", False) and bundled_ffmpeg.is_dir():
            env["PATH"] = os.pathsep.join([str(bundled_ffmpeg), env.get("PATH", "")])
        if getattr(sys, "frozen", False):
            command = [sys.executable, "--service"]
        else:
            command = [sys.executable, "-m", "video_get"]
            sources = str(Path(__file__).parents[4] / "services" / "downloader-api" / "src")
            env["PYTHONPATH"] = os.pathsep.join(filter(None, [sources, env.get("PYTHONPATH")]))
        flags = subprocess.CREATE_NO_WINDOW if sys.platform == "win32" else 0
        self.process = subprocess.Popen(
            command,
            cwd=self.data_dir.parent,
            env=env,
            stdout=self._stdout,
            stderr=self._stderr,
            creationflags=flags,
        )
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            result = self.status()
            if result.running:
                return result
            if self.process.poll() is not None:
                break
            time.sleep(0.2)
        self.stop()
        raise RuntimeError("本地下载服务启动失败，请查看诊断日志。")

    def stop(self, timeout: float = 5) -> None:
        if self.process and self.process.poll() is None:
            self.process.terminate()
            try:
                self.process.wait(timeout=timeout)
            except subprocess.TimeoutExpired:
                self.process.kill()
                self.process.wait(timeout=2)
        self.process = None
        for stream in (self._stdout, self._stderr):
            if stream:
                stream.close()
        self._stdout = self._stderr = None

    def diagnostics(self) -> str:
        status = self.status()
        data = {
            "companion_version": "0.1.0",
            "operating_system": platform.platform(),
            "architecture": platform.machine(),
            "python": platform.python_version(),
            "service": asdict(status),
            "data_directory_writable": _directory_writable(self.data_dir),
            "download_directory_writable": _directory_writable(self.download_dir),
        }
        data["service_error_log_present"] = (self.data_dir / "service.stderr.log").exists()
        return json.dumps(data, ensure_ascii=False, indent=2)


def _directory_writable(path: Path) -> bool:
    try:
        path.mkdir(parents=True, exist_ok=True)
        probe = path / ".write-test"
        probe.touch(exist_ok=True)
        probe.unlink()
        return True
    except OSError:
        return False
