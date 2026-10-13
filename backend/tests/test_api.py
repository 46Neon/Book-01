from __future__ import annotations

from urllib.parse import parse_qs

import httpx
import pytest
import yaml
from fastapi.testclient import TestClient

from backend.app import create_app

BOOK = "10000000-0000-4000-8000-000000000001"
CASH = "20000000-0000-4000-8000-000000000001"
EQUITY = "20000000-0000-4000-8000-000000000002"
EXPENSE = "20000000-0000-4000-8000-000000000003"
BEFORE_ENTRY = "30000000-0000-4000-8000-000000000001"
PERIOD_ENTRY = "30000000-0000-4000-8000-000000000002"
TOKEN = "caller.jwt.value"


def client_for(monkeypatch, responder):
    monkeypatch.setenv("SUPABASE_URL", "https://project.supabase.co")
    monkeypatch.setenv("SUPABASE_ANON_KEY", "anon-publishable-test-key")
    app = create_app()
    requests = []

    async def handler(request: httpx.Request) -> httpx.Response:
        requests.append(request)
        return responder(request)

    transport = httpx.MockTransport(handler)

    async def override_client():
        async with httpx.AsyncClient(transport=transport, follow_redirects=False) as client:
            yield client

    app.dependency_overrides[app.state.http_client_dependency] = override_client
    return TestClient(app), requests


def json_response(payload, status=200, headers=None):
    return httpx.Response(status, json=payload, headers=headers)


def test_healthz_is_liveness_only_and_does_not_call_supabase(monkeypatch):
    client, calls = client_for(monkeypatch, lambda request: pytest.fail("health check must not reach Supabase"))
    response = client.get("/healthz")
    assert response.status_code == 200
    assert response.json() == {"status": "ok"}
    assert calls == []


def test_requires_bearer_before_any_upstream_request(monkeypatch):
    def responder(request):
        return json_response([])

    client, calls = client_for(monkeypatch, responder)
    response = client.get(f"/api/v1/books/{BOOK}/accounts")
    assert response.status_code == 401
    assert response.json()["code"] == "unauthorized"
    assert response.json()["requestId"]
    assert calls == []


def test_book_list_forwards_bearer_uses_rls_and_returns_only_id_and_name(monkeypatch):
    second_book = "10000000-0000-4000-8000-000000000002"

    def responder(request):
        assert request.headers["authorization"] == f"Bearer {TOKEN}"
        assert request.headers["apikey"] == "anon-publishable-test-key"
        assert request.url.path.endswith("/books")
        assert request.url.params["select"] == "id,name"
        assert request.url.params["limit"] == "3"
        assert request.url.params["offset"] in {"0", "2"}
        return json_response([{"id": BOOK, "name": "Main", "secret": "not returned"}, {"id": second_book, "name": "Side"}, {"id": "10000000-0000-4000-8000-000000000003", "name": "Third"}])

    client, calls = client_for(monkeypatch, responder)
    response = client.get("/api/v1/books?pageSize=2", headers={"Authorization": f"Bearer {TOKEN}"})
    assert response.status_code == 200, response.text
    assert response.json() == {"items": [{"id": BOOK, "name": "Main"}, {"id": second_book, "name": "Side"}], "nextCursor": "djE6Mg"}
    assert len(calls) == 1
    next_page = client.get("/api/v1/books?pageSize=2&cursor=djE6Mg", headers={"Authorization": f"Bearer {TOKEN}"})
    assert next_page.status_code == 200
    assert calls[1].url.params["offset"] == "2"


def test_book_list_requires_bearer_and_preserves_typed_upstream_denials(monkeypatch):
    client, calls = client_for(monkeypatch, lambda request: httpx.Response(401, json={"message": "private"}))
    missing = client.get("/api/v1/books")
    assert missing.status_code == 401 and missing.json()["code"] == "unauthorized"
    assert calls == []
    rejected = client.get("/api/v1/books", headers={"Authorization": f"Bearer {TOKEN}"})
    assert rejected.status_code == 401 and rejected.json()["code"] == "unauthorized"
    assert "private" not in rejected.text and TOKEN not in rejected.text


def test_account_reads_forward_only_caller_bearer_and_scope_book(monkeypatch):
    def responder(request):
        assert request.headers["authorization"] == f"Bearer {TOKEN}"
        assert request.headers["apikey"] == "anon-publishable-test-key"
        assert "service_role" not in request.headers.get("apikey", "")
        if request.url.path.endswith("/books"):
            return json_response([{"id": BOOK}])
        if request.url.path.endswith("/accounts"):
            return json_response([{
                "id": CASH, "book_id": BOOK, "code": "1000", "name": "Cash",
                "account_type": "asset", "active": True, "parent_account_id": EQUITY,
            }])
        raise AssertionError(request.url)

    client, calls = client_for(monkeypatch, responder)
    response = client.get(
        f"/api/v1/books/{BOOK}/accounts?activeOnly=true&pageSize=20",
        headers={"Authorization": f"Bearer {TOKEN}"},
    )
    assert response.status_code == 200, response.text
    assert response.json() == {
        "items": [{"id": CASH, "code": "1000", "name": "Cash", "type": "asset", "active": True, "parentAccountId": EQUITY}],
        "nextCursor": None,
    }
    book_query = calls[0].url.params
    account_query = calls[1].url.params
    assert book_query["id"] == f"eq.{BOOK}"
    assert account_query["book_id"] == f"eq.{BOOK}"
    assert account_query["active"] == "eq.true"
    assert account_query["limit"] == "21"
    assert account_query["order"] == "code.asc,id.asc"
    assert account_query["select"] == "id,book_id,code,name,account_type,active,parent_account_id"


def test_cross_book_access_is_not_found_under_rls_and_never_queries_accounts(monkeypatch):
    def responder(request):
        assert request.headers["authorization"] == f"Bearer {TOKEN}"
        return json_response([])

    client, calls = client_for(monkeypatch, responder)
    response = client.get(f"/api/v1/books/{BOOK}/accounts", headers={"Authorization": f"Bearer {TOKEN}"})
    assert response.status_code == 404
    assert response.json()["code"] == "not_found"
    assert len(calls) == 1
    assert calls[0].url.path.endswith("/books")


def test_trial_balance_uses_exact_decimal_and_inclusive_date_bounds(monkeypatch):
    accounts = [
        {"id": CASH, "book_id": BOOK, "code": "1000", "name": "Cash", "account_type": "asset", "currency_code": "USD"},
        {"id": EQUITY, "book_id": BOOK, "code": "3000", "name": "Equity", "account_type": "equity", "currency_code": "USD"},
        {"id": EXPENSE, "book_id": BOOK, "code": "6000", "name": "Expense", "account_type": "expense", "currency_code": "USD"},
    ]
    before_entry = {"id": BEFORE_ENTRY, "book_id": BOOK, "entry_date": "2026-01-09", "description": "opening", "idempotency_key": "k1", "currency_code": "USD", "status": "posted"}
    period_entry = {"id": PERIOD_ENTRY, "book_id": BOOK, "entry_date": "2026-01-10", "description": "movement", "idempotency_key": "k2", "currency_code": "USD", "status": "posted"}
    lines = [
        {"id": "40000000-0000-4000-8000-000000000001", "book_id": BOOK, "journal_entry_id": BEFORE_ENTRY, "account_id": CASH, "line_number": 1, "debit": "100.10", "credit": "0.00", "currency_code": "USD"},
        {"id": "40000000-0000-4000-8000-000000000002", "book_id": BOOK, "journal_entry_id": BEFORE_ENTRY, "account_id": EQUITY, "line_number": 2, "debit": "0.00", "credit": "100.10", "currency_code": "USD"},
        {"id": "40000000-0000-4000-8000-000000000003", "book_id": BOOK, "journal_entry_id": PERIOD_ENTRY, "account_id": EXPENSE, "line_number": 1, "debit": "0.20", "credit": "0.00", "currency_code": "USD"},
        {"id": "40000000-0000-4000-8000-000000000004", "book_id": BOOK, "journal_entry_id": PERIOD_ENTRY, "account_id": CASH, "line_number": 2, "debit": "0.00", "credit": "0.20", "currency_code": "USD"},
    ]

    def responder(request):
        path, q = request.url.path, request.url.params
        assert request.headers["authorization"] == f"Bearer {TOKEN}"
        if path.endswith("/books"):
            return json_response([{"id": BOOK}])
        if path.endswith("/accounts"):
            return json_response(accounts)
        if path.endswith("/journal_entries"):
            date_filter = q.get("entry_date", "")
            if date_filter == "lte.2026-01-09":
                return json_response([before_entry])
            if date_filter == "gte.2026-01-10":
                assert q.get("and") == "(entry_date.lte.2026-01-10)"
                return json_response([period_entry])
            raise AssertionError(dict(q))
        if path.endswith("/journal_entry_lines"):
            requested = q["journal_entry_id"]
            return json_response([line for line in lines if line["journal_entry_id"] in requested])
        raise AssertionError(request.url)

    client, calls = client_for(monkeypatch, responder)
    response = client.get(
        f"/api/v1/books/{BOOK}/reports/trial-balance?dateFrom=2026-01-10&dateTo=2026-01-10&currency=USD",
        headers={"Authorization": f"Bearer {TOKEN}"},
    )
    assert response.status_code == 200, response.text
    body = response.json()
    assert body["period"] == {"from": "2026-01-10", "to": "2026-01-10"}
    assert body["totals"]["closingDebits"] == {"amount": "100.10", "currency": "USD"}
    assert body["totals"]["closingCredits"] == {"amount": "100.10", "currency": "USD"}
    assert body["totals"]["balanced"] is True
    rows = {row["accountId"]: row for row in body["rows"]}
    assert rows[CASH]["periodCredits"]["amount"] == "0.20"
    assert rows[CASH]["closingDebit"]["amount"] == "99.90"
    assert rows[EXPENSE]["periodDebits"]["amount"] == "0.20"
    assert len(calls) == 6


def test_journal_empty_posted_range_and_filters_are_scoped_and_inclusive(monkeypatch):
    def responder(request):
        if request.url.path.endswith("/books"):
            return json_response([{"id": BOOK}])
        if request.url.path.endswith("/journal_entries"):
            q = request.url.params
            assert q["book_id"] == f"eq.{BOOK}"
            assert q["entry_date"] == "gte.2026-02-01"
            assert q["and"] == "(entry_date.lte.2026-02-28)"
            assert q["status"] == "eq.posted"
            assert q["currency_code"] == "eq.USD"
            return json_response([])
        raise AssertionError(request.url)

    client, calls = client_for(monkeypatch, responder)
    response = client.get(
        f"/api/v1/books/{BOOK}/journal-entries?dateFrom=2026-02-01&dateTo=2026-02-28&currency=USD&pageSize=4",
        headers={"Authorization": f"Bearer {TOKEN}"},
    )
    assert response.status_code == 200, response.text
    body = response.json()
    assert body["items"] == []
    assert body["totals"]["debits"] == {"amount": "0.00", "currency": "USD"}
    assert body["appliedFilters"]["status"] == ["posted"]
    assert body["nextCursor"] is None
    assert len(calls) == 2


def test_journal_returns_truthful_database_fields_for_drafts(monkeypatch):
    def responder(request):
        if request.url.path.endswith("/books"):
            return json_response([{"id": BOOK}])
        if request.url.path.endswith("/journal_entries"):
            return json_response([{
                "id": PERIOD_ENTRY, "book_id": BOOK, "entry_date": "2026-02-02", "description": "draft",
                "idempotency_key": "key", "currency_code": "USD", "status": "draft", "counterparty": "Acme Ltd.",
            }])
        if request.url.path.endswith("/journal_entry_lines"):
            return json_response([{"id": "40000000-0000-4000-8000-000000000001", "book_id": BOOK, "journal_entry_id": PERIOD_ENTRY, "account_id": CASH, "line_number": 1, "debit": "1.00", "credit": "0.00", "currency_code": "USD"}, {"id": "40000000-0000-4000-8000-000000000002", "book_id": BOOK, "journal_entry_id": PERIOD_ENTRY, "account_id": EQUITY, "line_number": 2, "debit": "0.00", "credit": "1.00", "currency_code": "USD"}])
        raise AssertionError(request.url)

    client, _calls = client_for(monkeypatch, responder)
    bad_range = client.get(f"/api/v1/books/{BOOK}/journal-entries?dateFrom=2026-02-03&dateTo=2026-02-01", headers={"Authorization": f"Bearer {TOKEN}"})
    assert bad_range.status_code == 422
    assert bad_range.json()["code"] == "validation_error"
    response = client.get(f"/api/v1/books/{BOOK}/journal-entries?dateFrom=2026-02-01&dateTo=2026-02-28&status=draft", headers={"Authorization": f"Bearer {TOKEN}"})
    assert response.status_code == 200, response.text
    entry = response.json()["items"][0]
    assert entry["id"] == PERIOD_ENTRY
    assert entry["status"] == "draft"
    assert entry["description"] == "draft"
    assert entry["counterparty"] == "Acme Ltd."
    assert "reference" not in entry
    assert "reversalOfEntryId" not in entry
    assert entry["lines"][0]["debit"] == "1.00"
    assert response.json()["totals"]["debits"]["amount"] == "1.00"
    assert TOKEN not in response.text


def test_upstream_forbidden_maps_to_typed_error_and_drops_body_and_token(monkeypatch):
    secretish = "private upstream payload caller.jwt.value"

    def responder(request):
        return httpx.Response(403, text=secretish)

    client, _calls = client_for(monkeypatch, responder)
    response = client.get(f"/api/v1/books/{BOOK}/accounts", headers={"Authorization": f"Bearer {TOKEN}"})
    assert response.status_code == 403
    assert response.json()["code"] == "forbidden"
    assert secretish not in response.text
    assert TOKEN not in response.text


def test_report_empty_results_are_real_zero_results_and_currency_is_bounded(monkeypatch):
    def responder(request):
        if request.url.path.endswith("/books"):
            return json_response([{"id": BOOK}])
        if request.url.path.endswith("/journal_entries"):
            assert request.url.params["status"] == "eq.posted"
            return json_response([])
        if request.url.path.endswith("/accounts"):
            return json_response([])
        raise AssertionError(request.url)

    client, _calls = client_for(monkeypatch, responder)
    response = client.get(f"/api/v1/books/{BOOK}/reports/income-statement?dateFrom=2026-01-01&dateTo=2026-01-31&currency=USD", headers={"Authorization": f"Bearer {TOKEN}"})
    assert response.status_code == 200
    assert response.json()["netResult"] == {"amount": "0.00", "currency": "USD"}
    bad_currency = client.get(f"/api/v1/books/{BOOK}/reports/trial-balance?dateFrom=2026-01-01&dateTo=2026-01-31&currency=EUR", headers={"Authorization": f"Bearer {TOKEN}"})
    assert bad_currency.status_code == 422
    assert bad_currency.json()["code"] == "unsupported_currency"


def test_api_routes_match_proposal_paths_and_operation_ids():
    schema = create_app().openapi()
    paths = schema["paths"]
    expected = {
        "/books": "listBooks",
        "/books/{bookId}/accounts": "listBookAccounts",
        "/books/{bookId}/journal-entries": "listJournalEntries",
        "/books/{bookId}/accounts/{accountId}/balance": "getAccountBalance",
        "/books/{bookId}/reports/trial-balance": "getTrialBalance",
        "/books/{bookId}/reports/income-statement": "getIncomeStatement",
    }
    assert {path: paths[path]["get"]["operationId"] for path in expected} == expected
    assert set(method for methods in paths.values() for method in methods) == {"get"}
    assert schema["servers"][0]["url"] == "https://{host}/api/v1"
    assert schema["security"] == [{"bearerAuth": []}]
    assert schema["components"]["securitySchemes"]["bearerAuth"] == {"type": "http", "scheme": "bearer", "bearerFormat": "JWT"}
    assert "authorization" not in {p["name"].lower() for p in paths["/books/{bookId}/accounts"]["get"]["parameters"]}
    account_response = paths["/books/{bookId}/accounts"]["get"]["responses"]["200"]["content"]["application/json"]["schema"]
    assert account_response == {"$ref": "#/components/schemas/AccountPage"}
    journal_response = paths["/books/{bookId}/journal-entries"]["get"]["responses"]["200"]["content"]["application/json"]["schema"]
    assert journal_response == {"$ref": "#/components/schemas/JournalPage"}
    journal = schema["components"]["schemas"]["JournalEntry"]
    assert journal["required"] == ["id", "date", "description", "status", "lines"]
    assert set(journal["properties"]) == {"id", "date", "description", "status", "lines", "counterparty"}
    assert journal["properties"]["status"]["enum"] == ["draft", "posted", "void"]
    account = schema["components"]["schemas"]["Account"]
    assert set(account["properties"]) == {"id", "code", "name", "type", "active", "parentAccountId"}
    assert account["required"] == ["id", "code", "name", "type", "active"]
    assert account["properties"]["parentAccountId"]["anyOf"][0]["format"] == "uuid"
    assert journal["properties"]["counterparty"]["anyOf"][0]["maxLength"] == 80


def test_draft_openapi_contract_matches_live_fastapi_shape():
    from pathlib import Path

    contract = yaml.safe_load(Path("docs/parte-2a-openapi-propuesta.yaml").read_text())
    runtime = create_app().openapi()
    for path, path_item in contract["paths"].items():
        for method, operation in path_item.items():
            if method in {"get", "post", "put", "patch", "delete"}:
                runtime_path = path.replace("/books/{bookId}", "/books/{bookId}")
                assert runtime["paths"][runtime_path][method]["operationId"] == operation["operationId"]
    contract_schemas = contract["components"]["schemas"]
    runtime_schemas = runtime["components"]["schemas"]
    assert set(contract_schemas["Account"]["properties"]) == set(runtime_schemas["Account"]["properties"])
    assert set(contract_schemas["JournalEntry"]["properties"]) == set(runtime_schemas["JournalEntry"]["properties"])
    assert contract_schemas["EntryStatus"]["enum"] == runtime_schemas["JournalEntry"]["properties"]["status"]["enum"]
    assert set(contract_schemas["JournalLine"]["properties"]) == set(runtime_schemas["JournalLine"]["properties"])
    assert set(contract_schemas["AppliedFilters"]["properties"]) == set(runtime_schemas["AppliedFilters"]["properties"])
    assert contract_schemas["Account"]["properties"]["type"]["enum"] == runtime_schemas["Account"]["properties"]["type"]["enum"]
    assert contract_schemas["Account"]["properties"]["parentAccountId"]["type"] == ["string", "null"]
    assert contract_schemas["JournalEntry"]["properties"]["counterparty"]["maxLength"] == 80
    assert contract_schemas["CurrencyCode"]["enum"] == ["USD"]
    assert set(contract["paths"]) == set(runtime["paths"])
