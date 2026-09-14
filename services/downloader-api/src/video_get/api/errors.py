from __future__ import annotations

import uuid

from fastapi import Request
from fastapi.responses import JSONResponse

from video_get.domain.errors import AppError


async def app_error_handler(_: Request, exc: AppError) -> JSONResponse:
    return JSONResponse(
        status_code=exc.status_code,
        content={
            "error": {
                "code": exc.code,
                "message": exc.message,
                "retryable": exc.retryable,
                "details": exc.details,
                "request_id": f"req_{uuid.uuid4().hex}",
            }
        },
    )
