"""Desktop client for the same loopback API used by the extension."""

from __future__ import annotations

import json
import urllib.error
import urllib.request
from pathlib import Path


class DesktopApi:
    def __init__(self, data_dir: Path, port: int = 17382) -> None:
        self.data_dir = data_dir
        self.base = f"http://127.0.0.1:{port}/api/v1"
        self.opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))

    def request(self, path: str, method: str = "GET", payload=None):
        if not path.startswith("/") or "://" in path:
            raise ValueError("Invalid API path")
        try:
            token = (self.data_dir / "api-token").read_text(encoding="utf-8").strip()
            body = json.dumps(payload).encode() if payload is not None else None
            request = urllib.request.Request(
                self.base + path,
                data=body,
                method=method,
                headers={"Authorization": f"Bearer {token}", "Content-Type": "application/json"},
            )
            with self.opener.open(request, timeout=120 if path == "/analyze" else 15) as response:
                return json.loads(response.read())
        except urllib.error.HTTPError as error:
            if error.code == 401:
                raise RuntimeError("访问令牌不匹配，请退出后重新启动 Video Get。") from None
            try:
                detail = json.loads(error.read()).get("error", {})
                message = detail.get("message") if isinstance(detail, dict) else None
            except (ValueError, OSError):
                message = None
            # Session responses must never become a credential-bearing UI/log message.
            if path.startswith("/sessions/"):
                message = "登录状态同步失败，请重新登录。"
            raise RuntimeError(message or f"请求失败（HTTP {error.code}）") from None
        except (OSError, ValueError, urllib.error.URLError):
            raise RuntimeError("无法连接本地服务，请检查服务是否已启动。") from None


def download_payload(media: dict, asset_index: int, format_index: int) -> dict:
    asset = media["assets"][asset_index]
    return {
        "url": media["source_url"],
        "asset_ids": [asset["id"]],
        "format_id": asset["formats"][format_index]["id"],
        "output": {"mode": "default_downloads_directory"},
    }
