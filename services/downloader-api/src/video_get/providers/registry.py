from __future__ import annotations

from video_get.domain.enums import PlatformId, PlatformStatus
from video_get.domain.errors import platform_not_implemented, unsupported_platform
from video_get.domain.models import PlatformCapability

from .base import MatchResult, Provider
from .url_router import UrlRouter

CAPABILITIES = (
    PlatformCapability(
        id=PlatformId.X,
        display_name="X",
        status=PlatformStatus.AVAILABLE,
        available=True,
        url_patterns=["https://x.com/*/status/*", "https://twitter.com/*/status/*"],
        capabilities=["video", "multi_asset", "video_variants"],
    ),
    PlatformCapability(
        id=PlatformId.INSTAGRAM,
        display_name="Instagram",
        status=PlatformStatus.AVAILABLE,
        available=True,
        url_patterns=["https://www.instagram.com/p/*", "https://www.instagram.com/reel/*"],
        capabilities=["video", "multi_asset", "video_variants"],
    ),
    PlatformCapability(
        id=PlatformId.THREADS,
        display_name="Threads",
        status=PlatformStatus.PLANNED,
        available=False,
        url_patterns=[
            "https://www.threads.com/@*/post/*",
            "https://www.threads.com/share/*",
        ],
        capabilities=["video", "multi_asset", "authentication", "video_variants"],
    ),
)


class ProviderRegistry:
    def __init__(self, providers: list[Provider], router: UrlRouter | None = None) -> None:
        self._providers = providers
        self.router = router or UrlRouter()

    def capabilities(self) -> list[PlatformCapability]:
        return [item.model_copy(deep=True) for item in CAPABILITIES]

    def resolve(self, raw_url: str) -> tuple[Provider, MatchResult]:
        match = self.router.match(raw_url)
        if match.platform is PlatformId.THREADS:
            raise platform_not_implemented(match.platform.value)
        for provider in self._providers:
            if provider.supports(match.platform):
                return provider, match
        raise unsupported_platform()
