from __future__ import annotations

import json
from pathlib import Path
from unittest.mock import MagicMock, patch

from video_get_companion.service import ServiceController, ServiceStatus, _directory_writable


def test_port_available_uses_loopback(tmp_path: Path) -> None:
    controller = ServiceController(tmp_path, port=0)
    assert controller.port_available()


def test_diagnostics_contains_no_token_or_media_urls(tmp_path: Path) -> None:
    controller = ServiceController(tmp_path)
    with patch.object(controller, "status") as status:
        status.return_value = ServiceStatus(False, "unavailable", None, 17382)
        payload = controller.diagnostics()
    assert "token" not in payload.lower()
    assert "authorization" not in payload.lower()


def test_status_accepts_expected_health_payload(tmp_path: Path) -> None:
    response = MagicMock(status=200)
    response.read.return_value = json.dumps({"status": "ok"}).encode()
    response.__enter__.return_value = response
    with patch("urllib.request.urlopen", return_value=response):
        status = ServiceController(tmp_path).status()
    assert status.running is True
    assert status.health == "ok"


def test_directory_writable(tmp_path: Path) -> None:
    target = tmp_path / "new"
    assert _directory_writable(target)
    assert not (target / ".write-test").exists()
