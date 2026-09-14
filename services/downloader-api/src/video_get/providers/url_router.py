from __future__ import annotations

import ipaddress
from urllib.parse import parse_qsl, urlencode, urlsplit, urlunsplit

from video_get.domain.enums import PlatformId
from video_get.domain.errors import invalid_url, unsupported_platform

from .base import MatchResult

_HOSTS: dict[str, PlatformId] = {
    "x.com": PlatformId.X,
    "www.x.com": PlatformId.X,
    "twitter.com": PlatformId.X,
    "www.twitter.com": PlatformId.X,
    "instagram.com": PlatformId.INSTAGRAM,
    "www.instagram.com": PlatformId.INSTAGRAM,
    "threads.com": PlatformId.THREADS,
    "www.threads.com": PlatformId.THREADS,
    "threads.net": PlatformId.THREADS,
    "www.threads.net": PlatformId.THREADS,
}

_TRACKING_KEYS = {"igsh", "s", "t", "utm_campaign", "utm_content", "utm_medium", "utm_source"}


class UrlRouter:
    def match(self, raw_url: str) -> MatchResult:
        value = raw_url.strip()
        try:
            parsed = urlsplit(value)
            host = (parsed.hostname or "").encode("idna").decode("ascii").lower()
        except (UnicodeError, ValueError) as exc:
            raise invalid_url() from exc
        if parsed.scheme not in {"http", "https"} or not host:
            raise invalid_url("Only absolute HTTP and HTTPS URLs are allowed.")
        self._reject_ip_host(host)
        platform = _HOSTS.get(host)
        if platform is None:
            raise unsupported_platform()
        if not self._valid_path(platform, parsed.path):
            raise unsupported_platform()
        query = urlencode(
            [(key, value) for key, value in parse_qsl(parsed.query) if key not in _TRACKING_KEYS]
        )
        canonical_host = {
            PlatformId.X: "x.com",
            PlatformId.INSTAGRAM: "www.instagram.com",
            PlatformId.THREADS: "www.threads.com",
        }[platform]
        canonical = urlunsplit(("https", canonical_host, parsed.path.rstrip("/"), query, ""))
        return MatchResult(platform=platform, canonical_url=canonical)

    @staticmethod
    def _reject_ip_host(host: str) -> None:
        try:
            address = ipaddress.ip_address(host)
        except ValueError:
            return
        if not address.is_global:
            raise invalid_url("Local and private network addresses are not allowed.")

    @staticmethod
    def _valid_path(platform: PlatformId, path: str) -> bool:
        parts = [part for part in path.split("/") if part]
        if platform is PlatformId.X:
            return len(parts) >= 3 and parts[1] == "status" and parts[2].isdigit()
        if platform is PlatformId.INSTAGRAM:
            return len(parts) >= 2 and parts[0] in {"p", "reel", "reels"}
        if platform is PlatformId.THREADS:
            return (len(parts) >= 3 and parts[0].startswith("@") and parts[1] == "post") or (
                len(parts) >= 2 and parts[0] == "share"
            )
        return False
