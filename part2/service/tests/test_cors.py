import httpx
import pytest
from fastapi.testclient import TestClient

from app import configured_cors_origins, create_app

ALLOWED = "https://book.example.test"


def make_app(monkeypatch, origin=ALLOWED):
    monkeypatch.setenv("CORS_ALLOWED_ORIGINS", origin)
    groq = httpx.AsyncClient(transport=httpx.MockTransport(
        lambda request: httpx.Response(200, json={"choices": [{"message": {"role": "assistant", "content": "OK"}}]})
    ))
    accounting = httpx.AsyncClient(transport=httpx.MockTransport(
        lambda request: httpx.Response(200, json={"items": [], "nextCursor": None})
    ))
    return create_app(groq_client=groq, accounting_client=accounting, groq_api_key="synthetic-groq-key",
                      accounting_base_url="https://accounting.example.test/api/v1")


def test_exact_https_origin_is_allowed_and_bearer_flow_still_works(monkeypatch):
    app = make_app(monkeypatch)
    with TestClient(app) as client:
        response = client.post("/v1/chat", headers={
            "Origin": ALLOWED,
            "Authorization": "Bearer synthetic-user-token",
            "Content-Type": "application/json",
        }, json={"bookId": "bc255014-b7d0-4eb5-9b88-444de9867f39", "message": "Saluda"})
    assert response.status_code == 200
    assert response.json()["text"] == "OK"
    assert response.headers["access-control-allow-origin"] == ALLOWED
    assert "access-control-allow-credentials" not in response.headers


def test_unlisted_origin_gets_no_cross_origin_access(monkeypatch):
    app = make_app(monkeypatch)
    with TestClient(app) as client:
        response = client.get("/healthz", headers={"Origin": "https://attacker.example.test"})
    assert response.status_code == 200
    assert "access-control-allow-origin" not in response.headers


def test_options_preflight_allows_only_chat_post_headers_and_exact_origin(monkeypatch):
    app = make_app(monkeypatch)
    with TestClient(app) as client:
        response = client.options("/v1/chat", headers={
            "Origin": ALLOWED,
            "Access-Control-Request-Method": "POST",
            "Access-Control-Request-Headers": "authorization,content-type",
        })
    assert response.status_code == 200
    assert response.headers["access-control-allow-origin"] == ALLOWED
    assert "POST" in response.headers["access-control-allow-methods"]
    assert "authorization" in response.headers["access-control-allow-headers"].lower()
    assert "content-type" in response.headers["access-control-allow-headers"].lower()
    assert "access-control-allow-credentials" not in response.headers


@pytest.mark.parametrize("origins", ["*", "http://book.example.test", "https://example.test/path", "https://user:pass@example.test"])
def test_cors_configuration_rejects_wildcards_non_https_and_non_origin_values(monkeypatch, origins):
    monkeypatch.setenv("CORS_ALLOWED_ORIGINS", origins)
    with pytest.raises(ValueError):
        configured_cors_origins()


def test_cors_defaults_to_no_cross_origin_access(monkeypatch):
    monkeypatch.delenv("CORS_ALLOWED_ORIGINS", raising=False)
    assert configured_cors_origins() == []
    app = create_app(groq_api_key="synthetic-groq-key", accounting_base_url="https://accounting.example.test/api/v1")
    with TestClient(app) as client:
        response = client.get("/healthz", headers={"Origin": ALLOWED})
    assert "access-control-allow-origin" not in response.headers
