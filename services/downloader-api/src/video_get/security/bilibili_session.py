from __future__ import annotations

import threading
import time
from http.cookiejar import Cookie
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, SecretStr, field_validator

from video_get.domain.errors import AppError


class BilibiliCookie(BaseModel):
    model_config = ConfigDict(extra="forbid", frozen=True)
    name: Literal["SESSDATA", "DedeUserID", "DedeUserID__ckMd5", "bili_jct"]
    value: SecretStr = Field(repr=False)
    expires: int | None = Field(default=None, ge=0)

    @field_validator("value")
    @classmethod
    def validate_value(cls, value: SecretStr) -> SecretStr:
        raw = value.get_secret_value()
        if not raw or len(raw) > 4096 or any(ord(c) < 33 or ord(c) > 126 or c == ";" for c in raw):
            raise ValueError("Invalid cookie value")
        return value

    def active(self) -> bool:
        return self.expires is None or self.expires > time.time()

    def to_cookie(self) -> Cookie:
        # Fixed domain/path: login cookies must never reach X/Instagram or media CDNs.
        return Cookie(
            0,
            self.name,
            self.value.get_secret_value(),
            None,
            False,
            ".bilibili.com",
            True,
            True,
            "/",
            True,
            True,
            self.expires,
            self.expires is None,
            None,
            None,
            {"HttpOnly": ""},
            False,
        )


class BilibiliSessionRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")
    cookies: list[BilibiliCookie] = Field(min_length=1, max_length=4, repr=False)


class BilibiliSession:
    """Explicitly authorized browser login, in memory only. No credentials in diagnostics."""

    def __init__(self) -> None:
        self._lock = threading.Lock()
        self._cookies: tuple[BilibiliCookie, ...] = ()
        self._revision = 0

    def set(self, cookies: list[BilibiliCookie]) -> None:
        if len({c.name for c in cookies}) != len(cookies):
            raise AppError("INVALID_SESSION", "Invalid Bilibili login data.", 422)
        if not any(c.name == "SESSDATA" and c.active() for c in cookies):
            raise AppError("AUTHENTICATION_REQUIRED", "Please log into Bilibili again.", 401)
        with self._lock:
            self._cookies = tuple(cookies)
            self._revision += 1

    def clear(self) -> None:
        with self._lock:
            self._cookies = ()
            self._revision += 1

    def snapshot(self) -> tuple[tuple[BilibiliCookie, ...], int]:
        with self._lock:
            active = tuple(c for c in self._cookies if c.active())
            if not any(c.name == "SESSDATA" for c in active):
                active = ()
            return active, self._revision

    def status(self) -> dict[str, bool]:
        return {"configured": bool(self.snapshot()[0])}
