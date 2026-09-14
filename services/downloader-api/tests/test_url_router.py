from __future__ import annotations

import pytest
from video_get.domain.enums import PlatformId
from video_get.domain.errors import AppError
from video_get.providers.url_router import UrlRouter


@pytest.mark.parametrize(
    ("url", "platform", "canonical"),
    [
        ("https://twitter.com/user/status/123?s=20", PlatformId.X, "https://x.com/user/status/123"),
        (
            "https://instagram.com/reel/ABC/?igsh=test",
            PlatformId.INSTAGRAM,
            "https://www.instagram.com/reel/ABC",
        ),
        (
            "https://threads.net/@user/post/ABC/",
            PlatformId.THREADS,
            "https://www.threads.com/@user/post/ABC",
        ),
        (
            "https://www.threads.com/share/xyz/",
            PlatformId.THREADS,
            "https://www.threads.com/share/xyz",
        ),
    ],
)
def test_routes_known_urls(url: str, platform: PlatformId, canonical: str) -> None:
    result = UrlRouter().match(url)
    assert result.platform is platform
    assert result.canonical_url == canonical


@pytest.mark.parametrize(
    "url",
    [
        "file:///etc/passwd",
        "http://127.0.0.1/video",
        "https://example.com/video",
        "https://x.com/home",
        "https://instagram.com/accounts/login",
    ],
)
def test_rejects_invalid_or_unsupported_urls(url: str) -> None:
    with pytest.raises(AppError):
        UrlRouter().match(url)
