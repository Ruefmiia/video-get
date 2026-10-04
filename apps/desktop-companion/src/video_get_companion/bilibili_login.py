"""Isolated official-site login. No JS bridge or credential persistence."""

from __future__ import annotations

import logging
import threading
import time
from email.utils import parsedate_to_datetime
from pathlib import Path
from urllib.parse import urlsplit

from .client import DesktopApi

ALLOWED = {"SESSDATA", "DedeUserID", "DedeUserID__ckMd5", "bili_jct"}


def check_runtime() -> int:
    """Hidden local-only packaging smoke test; no external login or credentials."""
    logging.disable(logging.CRITICAL)
    import webview

    result = [3]
    window = webview.create_window(
        "Video Get runtime check", html="<p>Local check</p>", hidden=True
    )

    def probe():
        try:
            window.events.loaded.wait(15)
            window.get_cookies()
            result[0] = 0
        finally:
            window.destroy()

    try:
        webview.start(probe, gui="edgechromium", private_mode=True)
    except Exception:
        return 3
    return result[0]


def session_cookies(jars: list) -> list[dict]:
    selected = {}
    for jar in jars:
        for name, item in jar.items():
            if name not in ALLOWED or item["domain"].lstrip(".").lower() != "bilibili.com":
                continue
            if item["path"] != "/":
                continue
            expires = None
            if item["expires"]:
                try:
                    expires = int(parsedate_to_datetime(item["expires"]).timestamp())
                except (ValueError, TypeError, OverflowError):
                    continue
                if expires <= time.time():
                    continue
            selected[name] = {"name": name, "value": item.value, "expires": expires}
    return list(selected.values())


def run_login(data_dir: Path, port: int = 17382) -> int:
    # Never log browser exceptions or credential-bearing Cookie objects.
    logging.disable(logging.CRITICAL)
    import webview

    result = [2]
    closed = threading.Event()
    window = webview.create_window(
        "B站登录 · 登录成功后自动返回 Video Get",
        "https://www.bilibili.com/",
        width=1000,
        height=740,
    )
    window.events.closed += closed.set

    def watch():
        deadline = time.monotonic() + 600
        while not closed.wait(2):
            if time.monotonic() >= deadline:
                result[0] = 4
                window.destroy()
                return
            try:
                url = urlsplit(window.get_current_url() or "")
                if url.scheme != "https" or url.hostname not in {
                    "www.bilibili.com",
                    "passport.bilibili.com",
                }:
                    continue
                cookies = session_cookies(window.get_cookies())
                if not any(item["name"] == "SESSDATA" for item in cookies):
                    continue
                try:
                    DesktopApi(data_dir, port).request(
                        "/sessions/bilibili", "POST", {"cookies": cookies}
                    )
                except RuntimeError:
                    result[0] = 4
                    window.destroy()
                    return
                result[0] = 0
                window.destroy()
                return
            except Exception:
                # Do not print exception details: browser/API errors may contain cookies.
                continue

    try:
        webview.start(watch, gui="edgechromium", private_mode=True, debug=False)
    except Exception:
        return 3
    return result[0]
