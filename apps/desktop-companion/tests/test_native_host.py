from __future__ import annotations

import io
import json
import struct
from pathlib import Path

import pytest
from video_get_companion.native_host import (
    MAX_MESSAGE_BYTES,
    configuration,
    read_message,
    run_native_host,
    write_message,
)


def encode_message(value: dict[str, object]) -> bytes:
    payload = json.dumps(value).encode()
    return struct.pack("<I", len(payload)) + payload


def test_native_message_round_trip(tmp_path: Path, monkeypatch: pytest.MonkeyPatch) -> None:
    (tmp_path / "api-token").write_text("test-token", encoding="utf-8")
    monkeypatch.setenv("LOCALAPPDATA", str(tmp_path.parent))
    monkeypatch.setattr(
        "video_get_companion.native_host.configuration",
        lambda: configuration(tmp_path),
    )
    output = io.BytesIO()
    assert run_native_host(io.BytesIO(encode_message({"type": "get_configuration"})), output) == 0
    output.seek(0)
    response = read_message(output)
    assert response == {
        "ok": True,
        "apiBaseUrl": "http://127.0.0.1:17382",
        "apiToken": "test-token",
        "apiVersion": "1",
    }


def test_configuration_requires_initialized_service(tmp_path: Path) -> None:
    assert configuration(tmp_path)["error"] == "service_not_initialized"


def test_rejects_oversized_native_message() -> None:
    with pytest.raises(ValueError, match="too large"):
        read_message(io.BytesIO(struct.pack("<I", MAX_MESSAGE_BYTES + 1)))


def test_write_message_uses_little_endian_length_prefix() -> None:
    output = io.BytesIO()
    write_message(output, {"ok": True})
    raw = output.getvalue()
    assert struct.unpack("<I", raw[:4])[0] == len(raw[4:])
