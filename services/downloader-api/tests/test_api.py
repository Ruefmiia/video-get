from __future__ import annotations

from fastapi.testclient import TestClient


def test_health_is_public_and_minimal(client: TestClient) -> None:
    assert client.get("/health").json() == {"status": "ok"}


def test_api_requires_local_token(client: TestClient) -> None:
    response = client.get("/api/v1/platforms")
    assert response.status_code == 401
    assert response.json()["error"]["code"] == "UNAUTHORIZED"


def test_platforms_include_planned_threads(
    client: TestClient, auth_headers: dict[str, str]
) -> None:
    response = client.get("/api/v1/platforms", headers=auth_headers)
    assert response.status_code == 200
    platforms = {item["id"]: item for item in response.json()}
    assert platforms["x"]["available"] is True
    assert platforms["instagram"]["available"] is True
    assert platforms["threads"]["status"] == "planned"
    assert platforms["threads"]["available"] is False


def test_threads_is_recognized_but_not_implemented(
    client: TestClient, auth_headers: dict[str, str]
) -> None:
    response = client.post(
        "/api/v1/analyze",
        headers=auth_headers,
        json={"url": "https://www.threads.com/@user/post/ABC"},
    )
    assert response.status_code == 501
    body = response.json()["error"]
    assert body["code"] == "PLATFORM_NOT_IMPLEMENTED"
    assert body["details"] == {"platform": "threads"}


def test_unknown_platform_is_rejected(client: TestClient, auth_headers: dict[str, str]) -> None:
    response = client.post(
        "/api/v1/analyze", headers=auth_headers, json={"url": "https://example.com/video"}
    )
    assert response.status_code == 422
    assert response.json()["error"]["code"] == "UNSUPPORTED_PLATFORM"


def test_version_reports_contract_version(client: TestClient, auth_headers: dict[str, str]) -> None:
    response = client.get("/api/v1/version", headers=auth_headers)
    assert response.status_code == 200
    assert response.json()["api_version"] == "1"


def test_rejects_format_expression(client: TestClient, auth_headers: dict[str, str]) -> None:
    response = client.post(
        "/api/v1/downloads",
        headers=auth_headers,
        json={
            "url": "https://x.com/user/status/123",
            "format_id": "bestvideo+bestaudio",
        },
    )
    assert response.status_code == 422


def test_rejects_multiple_asset_ids(client: TestClient, auth_headers: dict[str, str]) -> None:
    response = client.post(
        "/api/v1/downloads",
        headers=auth_headers,
        json={
            "url": "https://x.com/user/status/123",
            "asset_ids": ["asset-1", "asset-2"],
        },
    )
    assert response.status_code == 422
