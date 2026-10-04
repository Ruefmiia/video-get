from __future__ import annotations

import time
from http.cookiejar import CookieJar
from io import BytesIO
from pathlib import Path
from urllib.error import HTTPError
from urllib.request import Request

import pytest
from fastapi.testclient import TestClient
from pydantic import SecretStr
from video_get.domain.enums import PlatformId
from video_get.domain.errors import AppError
from video_get.providers.base import MatchResult
from video_get.providers.bilibili_short import resolve_short
from video_get.providers.url_router import UrlRouter
from video_get.providers.yt_dlp import YtDlpProvider
from video_get.security.bilibili_session import BilibiliCookie, BilibiliSession

URL = "https://www.bilibili.com/video/BV1xx411c7mD"


@pytest.mark.parametrize(
    ("url", "canonical"),
    [
        (URL + "?spm_id_from=333.1007&p=2", URL + "?p=2"),
        ("https://m.bilibili.com/video/av123/?p=3", "https://www.bilibili.com/video/av123?p=3"),
        ("http://b23.tv/Ab123/?share_source=copy", "https://b23.tv/Ab123"),
    ],
)
def test_bilibili_routes(url: str, canonical: str) -> None:
    assert UrlRouter().match(url) == MatchResult(PlatformId.BILIBILI, canonical)


@pytest.mark.parametrize(
    "url",
    [
        "https://bilibili.com.example/video/av123",
        "https://www.bilibili.com/bangumi/play/ep1",
        "https://www.bilibili.com/video/BVbad",
        URL + "?p=0",
        URL + "?p=-1",
        URL + "?p=1&p=2",
        "https://b23.tv/abc/path",
        "https://user@b23.tv/abc",
        "https://b23.tv:444/abc",
        "https://b23.tv:bad/abc",
        "https://live.bilibili.com/123",
    ],
)
def test_rejects_unsupported_bilibili_inputs(url: str) -> None:
    with pytest.raises(AppError):
        UrlRouter().match(url)


@pytest.mark.parametrize(
    "location",
    [
        URL + "?p=2",
        "http://127.0.0.1/a",
        "https://example.com",
        "https://www.bilibili.com/bangumi/play/ep1",
    ],
)
def test_short_redirect_allowlist(monkeypatch: pytest.MonkeyPatch, location: str) -> None:
    class Opener:
        def open(self, req: Request, **kwargs: object) -> None:
            raise HTTPError(req.full_url, 302, "redirect", {"Location": location}, BytesIO())  # type: ignore[arg-type]

    monkeypatch.setattr("video_get.providers.bilibili_short.build_opener", lambda _: Opener())
    match = MatchResult(PlatformId.BILIBILI, "https://b23.tv/Ab123")
    if location == URL + "?p=2":
        assert resolve_short(match).canonical_url == location
    else:
        with pytest.raises(AppError):
            resolve_short(match)


def test_session_api_auth_status_clear_and_no_secret_echo(
    client: TestClient, auth_headers: dict[str, str]
) -> None:
    path = "/api/v1/sessions/bilibili"
    payload = {"cookies": [{"name": "SESSDATA", "value": "a-private-test-secret"}]}
    assert client.post(path, json=payload).status_code == 401
    assert client.get(path, headers=auth_headers).json() == {"configured": False}
    response = client.post(path, headers=auth_headers, json=payload)
    assert response.json() == {"configured": True}
    assert "a-private-test-secret" not in response.text
    response = client.post(
        path,
        headers=auth_headers,
        json={"cookies": [{"name": "evil", "value": "a-private-test-secret"}]},
    )
    assert response.status_code == 422
    assert "a-private-test-secret" not in response.text
    assert client.get(path, headers=auth_headers).json() == {"configured": True}
    assert client.delete(path, headers=auth_headers).json() == {"configured": False}
    response = client.post(path, headers=auth_headers, content=b"x" * 24001)
    assert response.status_code == 413


def test_expired_duplicate_and_new_instance_sessions() -> None:
    session = BilibiliSession()
    cookie = BilibiliCookie(name="SESSDATA", value=SecretStr("secret"))
    with pytest.raises(AppError):
        session.set([cookie, cookie])
    expired = cookie.model_copy(update={"expires": int(time.time()) - 1})
    with pytest.raises(AppError):
        session.set([expired])
    session.set([cookie])
    assert session.status()["configured"]
    assert not BilibiliSession().status()["configured"]
    session.clear()
    assert not session.status()["configured"]


def test_cookie_is_only_sent_to_bilibili_https() -> None:
    cookie = BilibiliCookie(name="SESSDATA", value=SecretStr("secret"))
    jar = CookieJar()
    jar.set_cookie(cookie.to_cookie())
    for url, expected in [
        ("https://api.bilibili.com/", True),
        ("https://www.bilibili.com/", True),
        ("https://video.twimg.com/", False),
        ("https://example.bilivideo.com/", False),
        ("http://api.bilibili.com/", False),
        ("https://bilibili.com.example/", False),
    ]:
        request = Request(url)
        jar.add_cookie_header(request)
        assert bool(request.get_header("Cookie")) is expected


def test_bilibili_analyze_merge_session_change_and_cookie_isolation(
    monkeypatch: pytest.MonkeyPatch, tmp_path: Path
) -> None:
    from test_yt_dlp_provider import FakeYDL, Token

    class BiliYDL(FakeYDL):
        seen_cookies: list[str] = []

        def __init__(self, options):  # type: ignore[no-untyped-def]
            super().__init__(options)
            self.cookiejar = CookieJar()

        def extract_info(self, url: str, *, download: bool):  # type: ignore[no-untyped-def]
            BiliYDL.seen_cookies = [c.name for c in self.cookiejar]
            result = super().extract_info(url, download=download)
            result["formats"] = [
                {
                    "format_id": "80",
                    "height": 1080,
                    "ext": "mp4",
                    "vcodec": "avc1",
                    "acodec": "none",
                },
                {
                    "format_id": "32",
                    "height": 480,
                    "ext": "mp4",
                    "vcodec": "avc1",
                    "acodec": "none",
                },
                {"format_id": "30280", "ext": "m4a", "vcodec": "none", "acodec": "mp4a"},
            ]
            return result

    monkeypatch.setattr("video_get.providers.yt_dlp.yt_dlp.YoutubeDL", BiliYDL)
    session = BilibiliSession()
    session.set([BilibiliCookie(name="SESSDATA", value=SecretStr("secret"))])
    provider = YtDlpProvider(bilibili_session=session)
    match = MatchResult(PlatformId.BILIBILI, URL + "?p=2")
    info = provider.analyze(match)
    assert info.canonical_url == URL + "?p=2"
    assert BiliYDL.seen_cookies == ["SESSDATA"]
    selected = info.assets[0].formats[0]
    assert selected.height == 1080 and selected.requires_merge
    provider.download(match, [info.assets[0].id], selected.id, tmp_path, lambda _: None, Token())
    assert FakeYDL.last_options["format"] == "80+bestaudio[ext=m4a]/80+bestaudio"
    assert FakeYDL.last_options["noplaylist"] is True
    assert FakeYDL.last_options["merge_output_format"] == "mp4"
    provider.analyze(MatchResult(PlatformId.X, "https://x.com/a/status/1"))
    assert BiliYDL.seen_cookies == []
    session.clear()
    with pytest.raises(AppError) as error:
        provider.download(
            match, [info.assets[0].id], selected.id, tmp_path, lambda _: None, Token()
        )
    assert error.value.code == "FORMAT_NOT_AVAILABLE"
