from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any


@dataclass(slots=True)
class AppError(Exception):
    code: str
    message: str
    status_code: int
    retryable: bool = False
    details: dict[str, Any] = field(default_factory=dict)

    def __str__(self) -> str:
        return self.message


def invalid_url(message: str = "The URL is invalid.") -> AppError:
    return AppError("INVALID_URL", message, 400)


def unsupported_platform() -> AppError:
    return AppError("UNSUPPORTED_PLATFORM", "This platform is not supported.", 422)


def platform_not_implemented(platform: str) -> AppError:
    return AppError(
        "PLATFORM_NOT_IMPLEMENTED",
        f"{platform.title()} support is planned but not available yet.",
        501,
        details={"platform": platform},
    )


def media_not_found() -> AppError:
    return AppError("MEDIA_NOT_FOUND", "No downloadable media was found.", 404)


def download_failed(message: str, *, retryable: bool = True) -> AppError:
    return AppError("DOWNLOAD_FAILED", message, 502, retryable=retryable)
