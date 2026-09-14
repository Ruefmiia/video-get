from __future__ import annotations

from pathlib import Path

import pytest
from fastapi.testclient import TestClient
from video_get.config import Settings
from video_get.main import create_app


@pytest.fixture
def client(tmp_path: Path) -> TestClient:
    settings = Settings(
        data_dir=tmp_path,
        download_dir=tmp_path / "downloads",
        database_path=tmp_path / "test.db",
        api_token="test-token",
    )
    settings.download_dir.mkdir()
    with TestClient(create_app(settings)) as test_client:
        yield test_client


@pytest.fixture
def auth_headers() -> dict[str, str]:
    return {"Authorization": "Bearer test-token"}
