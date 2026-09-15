from __future__ import annotations

from pathlib import Path
from typing import Any

import pytest
from video_get.domain.enums import PlatformId
from video_get.domain.errors import AppError
from video_get.providers.base import MatchResult
from video_get.providers.yt_dlp import YtDlpProvider
from yt_dlp.utils import DownloadError


class FakeYDL:
    last_options: dict[str, Any] = {}

    def __init__(self, options: dict[str, Any]) -> None:
        FakeYDL.last_options = options
        self.options = options
        self.created: Path | None = None

    def __enter__(self) -> FakeYDL:
        return self

    def __exit__(self, *_: object) -> None:
        return None

    def extract_info(self, url: str, *, download: bool) -> dict[str, Any]:
        if download:
            template = str(self.options["outtmpl"])
            # Real yt-dlp expands every output-template field before touching
            # the filesystem; this fake only needs a representative result.
            self.created = Path(template).parent / "x_owner_20260915_1_clip.mp4"
            self.created.parent.mkdir(parents=True, exist_ok=True)
            self.created.write_bytes(b"video")
            for hook in self.options["progress_hooks"]:
                hook({"downloaded_bytes": 10, "total_bytes": 10})
        return {
            "id": "1",
            "title": "Clip",
            "uploader": "Owner",
            "uploader_id": "owner-id",
            "webpage_url": url,
            "duration": 3,
            "thumbnail": "https://cdn.example/thumb.jpg",
            "ext": "mp4",
            "formats": [
                {
                    "format_id": "raw-720",
                    "ext": "mp4",
                    "width": 1280,
                    "height": 720,
                    "fps": 30,
                    "vcodec": "h264",
                    "acodec": "aac",
                    "filesize": 100,
                },
                {"format_id": "audio", "vcodec": "none", "acodec": "aac"},
            ],
        }

    def prepare_filename(self, _: dict[str, Any]) -> str:
        assert self.created is not None
        return str(self.created)


class Token:
    cancelled = False


class VideoOnlyYDL(FakeYDL):
    def extract_info(self, url: str, *, download: bool) -> dict[str, Any]:
        result = super().extract_info(url, download=download)
        result["formats"][0]["acodec"] = "none"
        return result


class MultiAssetYDL(FakeYDL):
    def extract_info(self, url: str, *, download: bool) -> dict[str, Any]:
        result = super().extract_info(url, download=download)
        if download:
            return result
        second = {**result, "id": "2", "title": "Second clip"}
        return {**result, "entries": [result, second]}


def test_analyze_returns_opaque_formats_and_download_resolves_them(
    monkeypatch: pytest.MonkeyPatch, tmp_path: Path
) -> None:
    monkeypatch.setattr("video_get.providers.yt_dlp.yt_dlp.YoutubeDL", FakeYDL)
    provider = YtDlpProvider()
    match = MatchResult(PlatformId.X, "https://x.com/user/status/123")
    info = provider.analyze(match)
    selected = info.assets[0].formats[0]
    assert selected.id.startswith("fmt_")
    assert "raw-720" not in selected.id
    updates: list[dict[str, object]] = []
    output = provider.download(
        match, [info.assets[0].id], selected.id, tmp_path, updates.append, Token()
    )
    assert output.read_bytes() == b"video"
    assert FakeYDL.last_options["format"] == "raw-720"
    assert FakeYDL.last_options["playlist_items"] == "1"
    assert Path(FakeYDL.last_options["outtmpl"]).name.startswith("x_")
    assert "%(upload_date|unknown)s" in FakeYDL.last_options["outtmpl"]
    assert updates[0]["downloaded_bytes"] == 10


def test_video_only_format_selects_audio_and_mp4_merge(
    monkeypatch: pytest.MonkeyPatch, tmp_path: Path
) -> None:
    monkeypatch.setattr("video_get.providers.yt_dlp.yt_dlp.YoutubeDL", VideoOnlyYDL)
    provider = YtDlpProvider()
    match = MatchResult(PlatformId.X, "https://x.com/user/status/123")
    info = provider.analyze(match)
    selected = info.assets[0].formats[0]
    assert selected.requires_merge is True
    provider.download(match, [info.assets[0].id], selected.id, tmp_path, lambda _: None, Token())
    assert VideoOnlyYDL.last_options["format"].startswith("raw-720+bestaudio")
    assert VideoOnlyYDL.last_options["merge_output_format"] == "mp4"


def test_multi_asset_selection_is_constrained_to_playlist_item(
    monkeypatch: pytest.MonkeyPatch, tmp_path: Path
) -> None:
    monkeypatch.setattr("video_get.providers.yt_dlp.yt_dlp.YoutubeDL", MultiAssetYDL)
    provider = YtDlpProvider()
    match = MatchResult(PlatformId.INSTAGRAM, "https://www.instagram.com/p/ABC")
    info = provider.analyze(match)
    assert len(info.assets) == 2
    selected_asset = info.assets[1]
    provider.download(
        match,
        [selected_asset.id],
        selected_asset.formats[0].id,
        tmp_path,
        lambda _: None,
        Token(),
    )
    assert MultiAssetYDL.last_options["playlist_items"] == "2"


def test_download_rejects_unknown_or_cross_url_format(tmp_path: Path) -> None:
    provider = YtDlpProvider()
    match = MatchResult(PlatformId.X, "https://x.com/user/status/123")
    with pytest.raises(AppError) as error:
        provider.download(match, [], "fmt_missing", tmp_path, lambda _: None, Token())
    assert error.value.code == "FORMAT_NOT_AVAILABLE"


def test_rejects_asset_and_format_token_mismatch(
    monkeypatch: pytest.MonkeyPatch, tmp_path: Path
) -> None:
    monkeypatch.setattr("video_get.providers.yt_dlp.yt_dlp.YoutubeDL", FakeYDL)
    provider = YtDlpProvider()
    match = MatchResult(PlatformId.X, "https://x.com/user/status/123")
    info = provider.analyze(match)
    with pytest.raises(AppError) as error:
        provider.download(
            match,
            ["asset-forged"],
            info.assets[0].formats[0].id,
            tmp_path,
            lambda _: None,
            Token(),
        )
    assert error.value.code == "ASSET_NOT_AVAILABLE"


def test_format_tokens_expire(monkeypatch: pytest.MonkeyPatch, tmp_path: Path) -> None:
    monkeypatch.setattr("video_get.providers.yt_dlp.yt_dlp.YoutubeDL", FakeYDL)
    provider = YtDlpProvider(format_token_ttl_seconds=0)
    match = MatchResult(PlatformId.X, "https://x.com/user/status/123")
    info = provider.analyze(match)
    with pytest.raises(AppError) as error:
        provider.download(
            match,
            [info.assets[0].id],
            info.assets[0].formats[0].id,
            tmp_path,
            lambda _: None,
            Token(),
        )
    assert error.value.code == "FORMAT_NOT_AVAILABLE"


def test_format_token_cache_is_bounded(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr("video_get.providers.yt_dlp.yt_dlp.YoutubeDL", MultiAssetYDL)
    provider = YtDlpProvider(max_format_tokens=1)
    info = provider.analyze(MatchResult(PlatformId.INSTAGRAM, "https://www.instagram.com/p/ABC"))
    assert len(info.assets) == 2
    assert list(provider._format_tokens) == [info.assets[1].formats[0].id]


@pytest.mark.parametrize(
    ("message", "code"),
    [
        ("Login required; provide cookies", "AUTHENTICATION_REQUIRED"),
        ("Video does not exist", "MEDIA_NOT_FOUND"),
        ("HTTP Error 429: rate limit", "RATE_LIMITED"),
        ("HTTP Error 403: Forbidden", "SOURCE_FORBIDDEN"),
        ("Read timed out", "SOURCE_TIMEOUT"),
        ("network connection failed", "SOURCE_UNREACHABLE"),
        ("unexpected extractor failure", "DOWNLOAD_FAILED"),
    ],
)
def test_download_errors_are_safely_mapped(message: str, code: str) -> None:
    mapped = YtDlpProvider._map_error(DownloadError(message))
    assert mapped.code == code
    assert message not in mapped.message
