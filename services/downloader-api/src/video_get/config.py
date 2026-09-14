from __future__ import annotations

import os
import secrets
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True, slots=True)
class Settings:
    data_dir: Path
    download_dir: Path
    database_path: Path
    api_token: str
    max_workers: int = 2

    @classmethod
    def load(cls) -> Settings:
        data_dir = Path(os.getenv("VIDEO_GET_DATA_DIR", ".video-get")).resolve()
        download_dir = Path(
            os.getenv("VIDEO_GET_DOWNLOAD_DIR", str(data_dir / "downloads"))
        ).resolve()
        data_dir.mkdir(parents=True, exist_ok=True)
        download_dir.mkdir(parents=True, exist_ok=True)
        token = os.getenv("VIDEO_GET_API_TOKEN") or _load_or_create_token(data_dir)
        return cls(
            data_dir=data_dir,
            download_dir=download_dir,
            database_path=data_dir / "video-get.db",
            api_token=token,
        )


def _load_or_create_token(data_dir: Path) -> str:
    token_path = data_dir / "api-token"
    if token_path.exists():
        token = token_path.read_text(encoding="utf-8").strip()
        if token:
            return token
    token = secrets.token_urlsafe(32)
    token_path.write_text(token, encoding="utf-8")
    try:
        token_path.chmod(0o600)
    except OSError:
        pass
    return token
