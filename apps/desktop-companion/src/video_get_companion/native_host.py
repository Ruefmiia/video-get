from __future__ import annotations

import json
import os
import struct
import sys
from pathlib import Path
from typing import BinaryIO

HOST_NAME = "com.videoget.companion"
MAX_MESSAGE_BYTES = 1024 * 1024


def configuration(data_dir: Path | None = None) -> dict[str, object]:
    root = data_dir or Path(os.getenv("LOCALAPPDATA", ".")) / "VideoGet"
    token_path = root / "api-token"
    if not token_path.exists():
        return {
            "ok": False,
            "error": "service_not_initialized",
            "message": "请先启动 Video Get 桌面程序。",
        }
    token = token_path.read_text(encoding="utf-8").strip()
    if not token:
        return {
            "ok": False,
            "error": "token_unavailable",
            "message": "本地服务令牌不可用。",
        }
    return {
        "ok": True,
        "apiBaseUrl": "http://127.0.0.1:17382",
        "apiToken": token,
        "apiVersion": "1",
    }


def read_message(stream: BinaryIO) -> dict[str, object] | None:
    header = stream.read(4)
    if not header:
        return None
    if len(header) != 4:
        raise ValueError("Incomplete native messaging header")
    (length,) = struct.unpack("<I", header)
    if length > MAX_MESSAGE_BYTES:
        raise ValueError("Native messaging request is too large")
    payload = stream.read(length)
    if len(payload) != length:
        raise ValueError("Incomplete native messaging payload")
    value = json.loads(payload.decode("utf-8"))
    if not isinstance(value, dict):
        raise ValueError("Native messaging request must be an object")
    return value


def write_message(stream: BinaryIO, value: dict[str, object]) -> None:
    payload = json.dumps(value, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    stream.write(struct.pack("<I", len(payload)))
    stream.write(payload)
    stream.flush()


def run_native_host(
    input_stream: BinaryIO | None = None, output_stream: BinaryIO | None = None
) -> int:
    stdin, stdout = input_stream, output_stream
    if stdin is None or stdout is None:
        stdin, stdout = _standard_binary_streams()
    while request := read_message(stdin):
        if request.get("type") == "get_configuration":
            write_message(stdout, configuration())
        else:
            write_message(
                stdout,
                {"ok": False, "error": "unsupported_request", "message": "不支持的请求。"},
            )
    return 0


def _standard_binary_streams() -> tuple[BinaryIO, BinaryIO]:
    if sys.stdin is not None and sys.stdout is not None:
        return sys.stdin.buffer, sys.stdout.buffer
    if sys.platform != "win32":
        raise RuntimeError("Native messaging streams are unavailable")
    import ctypes
    import msvcrt

    kernel32 = ctypes.windll.kernel32
    input_handle = kernel32.GetStdHandle(-10)
    output_handle = kernel32.GetStdHandle(-11)
    if input_handle in (0, -1) or output_handle in (0, -1):
        raise RuntimeError("Native messaging pipe handles are unavailable")
    stdin = os.fdopen(msvcrt.open_osfhandle(input_handle, os.O_RDONLY), "rb", closefd=False)
    stdout = os.fdopen(msvcrt.open_osfhandle(output_handle, 0), "wb", closefd=False)
    return stdin, stdout
