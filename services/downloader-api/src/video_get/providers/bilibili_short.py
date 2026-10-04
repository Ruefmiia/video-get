from __future__ import annotations

from urllib.error import HTTPError, URLError
from urllib.parse import urljoin, urlsplit
from urllib.request import HTTPRedirectHandler, Request, build_opener

from video_get.domain.errors import AppError
from video_get.providers.base import MatchResult
from video_get.providers.url_router import UrlRouter


class _NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):  # type: ignore[no-untyped-def]
        return None


def resolve_short(match: MatchResult) -> MatchResult:
    """Never let b23 redirect the downloader to arbitrary hosts or non-video extractors."""
    if urlsplit(match.canonical_url).hostname != "b23.tv":
        return match
    opener = build_opener(_NoRedirect())
    current = match
    for _ in range(4):
        try:
            with opener.open(
                Request(current.canonical_url, headers={"User-Agent": "Mozilla/5.0"}), timeout=10
            ):
                raise AppError(
                    "INVALID_URL", "Bilibili share link did not resolve to a video.", 422
                )
        except HTTPError as exc:
            if exc.code not in {301, 302, 303, 307, 308}:
                raise AppError(
                    "SOURCE_FORBIDDEN", "Bilibili share link could not be resolved.", 403
                ) from exc
            target = urljoin(current.canonical_url, exc.headers.get("Location", ""))
            exc.close()
            parsed = urlsplit(target)
            if parsed.scheme != "https" or parsed.hostname not in {
                "b23.tv",
                "bilibili.com",
                "www.bilibili.com",
                "m.bilibili.com",
            }:
                raise AppError(
                    "INVALID_URL", "Bilibili share link has an unsupported redirect.", 422
                ) from None
            current = UrlRouter().match(target)
            if urlsplit(current.canonical_url).hostname != "b23.tv":
                return current
        except (TimeoutError, URLError) as exc:
            raise AppError(
                "SOURCE_UNREACHABLE", "Bilibili share link could not be reached.", 502, True
            ) from exc
    raise AppError("INVALID_URL", "Bilibili share link has too many redirects.", 422)
