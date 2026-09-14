from __future__ import annotations

import secrets

from fastapi import Header, Request

from video_get.domain.errors import AppError


def require_local_token(request: Request, authorization: str | None = Header(default=None)) -> None:
    expected = f"Bearer {request.app.state.settings.api_token}"
    if authorization is None or not secrets.compare_digest(authorization, expected):
        raise AppError("UNAUTHORIZED", "A valid local API token is required.", 401)
