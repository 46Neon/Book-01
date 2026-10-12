from __future__ import annotations

import json
import sys
from pathlib import Path

import anyio
import httpx

# Keep imports stable when pytest is launched from part2/service in CI.
REPOSITORY_ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(REPOSITORY_ROOT))

from backend.app import create_app as create_accounting_app
from part2.service.app import create_app as create_chat_app

BOOK_ID = "10000000-0000-4000-8000-000000000001"
ACCOUNT_ID = "20000000-0000-4000-8000-000000000001"
CALLER_TOKEN = "synthetic-caller-jwt"
GROQ_KEY = "synthetic-groq-key"


def _tool_response():
    return {
        "choices": [{
            "message": {
                "role": "assistant",
                "content": None,
                "tool_calls": [{
                    "id": "call-trial-balance",
                    "type": "function",
                    "function": {
                        "name": "getTrialBalance",
                        "arguments": json.dumps({
                            "dateFrom": "2025-01-01",
                            "dateTo": "2025-01-31",
                            "currency": "USD",
                        }),
                    },
                }],
            },
        }],
    }


def _groq_response():
    return {"choices": [{"message": {"role": "assistant", "content": "El período está balanceado."}}]}


def test_chat_to_accounting_api_to_postgrest_with_access_preflight(monkeypatch):
    monkeypatch.setenv("SUPABASE_URL", "https://project.supabase.test")
    monkeypatch.setenv("SUPABASE_ANON_KEY", "synthetic-publishable-key")

    async def scenario():
        events: list[str] = []
        postgrest_headers: list[dict[str, str]] = []
        groq_headers: list[str | None] = []
        authorized = True

        async def postgrest_handler(request: httpx.Request) -> httpx.Response:
            table = request.url.path.rsplit("/", 1)[-1]
            events.append(f"db:{table}")
            postgrest_headers.append({
                "authorization": request.headers.get("authorization", ""),
                "apikey": request.headers.get("apikey", ""),
            })
            if table == "books":
                return httpx.Response(200, json=[{"id": BOOK_ID}] if authorized else [])
            if table == "accounts":
                return httpx.Response(200, json=[{
                    "id": ACCOUNT_ID,
                    "book_id": BOOK_ID,
                    "code": "1000",
                    "name": "Cash",
                    "account_type": "asset",
                    "currency_code": "USD",
                    "active": True,
                }])
            if table in {"journal_entries", "journal_entry_lines"}:
                return httpx.Response(200, json=[])
            raise AssertionError(f"Unexpected PostgREST table: {table}")

        postgrest_client = httpx.AsyncClient(transport=httpx.MockTransport(postgrest_handler))
        accounting_app = create_accounting_app()

        async def override_postgrest_client():
            yield postgrest_client

        accounting_app.dependency_overrides[accounting_app.state.http_client_dependency] = override_postgrest_client
        accounting_transport = httpx.ASGITransport(app=accounting_app)
        accounting_client = httpx.AsyncClient(transport=accounting_transport, base_url="https://accounting.test")

        async def groq_handler(request: httpx.Request) -> httpx.Response:
            events.append("groq")
            groq_headers.append(request.headers.get("authorization"))
            body = json.loads(request.content)
            has_tool_result = any(message.get("role") == "tool" for message in body["messages"])
            return httpx.Response(200, json=_groq_response() if has_tool_result else _tool_response())

        groq_client = httpx.AsyncClient(transport=httpx.MockTransport(groq_handler))
        chat_app = create_chat_app(
            groq_client=groq_client,
            accounting_client=accounting_client,
            groq_api_key=GROQ_KEY,
            accounting_base_url="https://accounting.test/api/v1",
        )
        chat_client = httpx.AsyncClient(
            transport=httpx.ASGITransport(app=chat_app),
            base_url="http://chat.test",
        )

        response = await chat_client.post(
            "/v1/chat",
            headers={"Authorization": f"Bearer {CALLER_TOKEN}"},
            json={"bookId": BOOK_ID, "message": "Consulta mi balance de comprobación"},
        )

        try:
            assert response.status_code == 200, response.text
            body = response.json()
            assert body["text"] == "El período está balanceado."
            assert body["summary"]["totals"]["balanced"] is True
            # The first API trip authorizes the caller/book before any model request.
            first_groq = events.index("groq")
            assert events[:first_groq] == ["db:books", "db:accounts"]
            assert all(headers["authorization"] == f"Bearer {CALLER_TOKEN}" for headers in postgrest_headers)
            assert all(headers["apikey"] == "synthetic-publishable-key" for headers in postgrest_headers)
            assert groq_headers and all(value == f"Bearer {GROQ_KEY}" for value in groq_headers)
            assert CALLER_TOKEN not in response.text and GROQ_KEY not in response.text
        finally:
            await chat_client.aclose()
            await groq_client.aclose()
            await accounting_client.aclose()
            await postgrest_client.aclose()

    anyio.run(scenario)


def test_inaccessible_book_is_rejected_before_groq(monkeypatch):
    monkeypatch.setenv("SUPABASE_URL", "https://project.supabase.test")
    monkeypatch.setenv("SUPABASE_ANON_KEY", "synthetic-publishable-key")

    async def scenario():
        events: list[str] = []

        async def postgrest_handler(request: httpx.Request) -> httpx.Response:
            table = request.url.path.rsplit("/", 1)[-1]
            events.append(f"db:{table}")
            if table == "books":
                return httpx.Response(200, json=[])
            raise AssertionError("RLS-hidden book must stop before resource reads")

        postgrest_client = httpx.AsyncClient(transport=httpx.MockTransport(postgrest_handler))
        accounting_app = create_accounting_app()

        async def override_postgrest_client():
            yield postgrest_client

        accounting_app.dependency_overrides[accounting_app.state.http_client_dependency] = override_postgrest_client
        accounting_client = httpx.AsyncClient(
            transport=httpx.ASGITransport(app=accounting_app),
            base_url="https://accounting.test",
        )

        async def groq_handler(request: httpx.Request) -> httpx.Response:
            events.append("groq")
            raise AssertionError("Groq must not receive prompts for inaccessible books")

        groq_client = httpx.AsyncClient(transport=httpx.MockTransport(groq_handler))
        chat_app = create_chat_app(
            groq_client=groq_client,
            accounting_client=accounting_client,
            groq_api_key=GROQ_KEY,
            accounting_base_url="https://accounting.test/api/v1",
        )
        chat_client = httpx.AsyncClient(
            transport=httpx.ASGITransport(app=chat_app),
            base_url="http://chat.test",
        )

        response = await chat_client.post(
            "/v1/chat",
            headers={"Authorization": f"Bearer {CALLER_TOKEN}"},
            json={"bookId": BOOK_ID, "message": "Consulta el libro"},
        )

        try:
            assert response.status_code == 404, response.text
            assert response.json()["code"] == "accounting_not_found"
            assert events == ["db:books"]
        finally:
            await chat_client.aclose()
            await groq_client.aclose()
            await accounting_client.aclose()
            await postgrest_client.aclose()

    anyio.run(scenario)
