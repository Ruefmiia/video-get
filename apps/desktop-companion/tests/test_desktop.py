import json
from http.cookies import SimpleCookie

import pytest
from video_get_companion.bilibili_login import session_cookies
from video_get_companion.client import DesktopApi, download_payload
from video_get_companion.desktop import DesktopApp


def media():
    return {
        "source_url": "https://x.com/test/status/123",
        "title": "测试视频",
        "assets": [
            {"id": "first", "formats": [{"id": "f1", "label": "720p"}]},
            {"id": "second", "formats": [{"id": "f2", "label": "1080p"}]},
        ],
    }


def test_download_uses_selected_asset():
    payload = download_payload(media(), 1, 0)
    assert payload["asset_ids"] == ["second"]
    assert payload["format_id"] == "f2"


def test_cookie_scope_expiry_and_names():
    jars = []
    for name, domain, path, expires in [
        ("SESSDATA", ".bilibili.com", "/", ""),
        ("bili_jct", "evil.com", "/", ""),
        ("unrelated", ".bilibili.com", "/", ""),
        ("DedeUserID", ".bilibili.com", "/other", ""),
        ("DedeUserID__ckMd5", ".bilibili.com", "/", "Wed, 01 Jan 2020 00:00:00 GMT"),
    ]:
        jar = SimpleCookie()
        jar[name] = "test-value"
        jar[name]["domain"] = domain
        jar[name]["path"] = path
        jar[name]["expires"] = expires
        jars.append(jar)
    assert session_cookies(jars) == [{"name": "SESSDATA", "value": "test-value", "expires": None}]


def test_api_reads_current_token_and_disables_proxy(tmp_path):
    (tmp_path / "api-token").write_text("test-token")
    api = DesktopApi(tmp_path, 17400)

    class Response:
        def __enter__(self):
            return self

        def __exit__(self, *_):
            pass

        def read(self):
            return b'{"configured":true}'

    class Opener:
        def open(self, request, timeout):
            assert request.full_url == "http://127.0.0.1:17400/api/v1/sessions/bilibili"
            assert request.get_header("Authorization") == "Bearer test-token"
            assert json.loads(request.data) == {"cookies": []}
            return Response()

    api.opener = Opener()
    assert api.request("/sessions/bilibili", "POST", {"cookies": []})["configured"]
    with pytest.raises(ValueError):
        api.request("https://example.org/")


def test_native_ui_selection_clear_and_job_states(tmp_path):
    app = DesktopApp(tmp_path, hidden=True)
    try:
        app.root.withdraw()
        app._set_status(True)
        app.media = media()
        app.asset.configure(values=["视频 1", "视频 2"], state="readonly")
        app.asset.current(1)
        app._formats()
        assert app.format.get() == "1080p"
        assert str(app.download_button["state"]) == "normal"
        app.url.set("changed")
        assert app.media is None
        assert str(app.download_button["state"]) == "disabled"
        job = {
            "id": "job1",
            "source_url": "https://x.com/test/status/123",
            "state": "downloading",
            "progress": {"progress": 0.5},
        }
        app._render_jobs([job])
        assert app.progress["value"] == 50
        assert "50.0%" in app.job_notice.get()
        assert "50.0%" in app.tree.item("job1", "values")[1]
        assert str(app.cancel_button["state"]) == "normal"
        job["state"] = "failed"
        job["error_message"] = "测试错误"
        app._render_jobs([job])
        assert app.job_notice.get() == "测试错误"
        assert str(app.retry_button["state"]) == "normal"
        app.root.update_idletasks()
    finally:
        app.closed = True
        app.root.destroy()
