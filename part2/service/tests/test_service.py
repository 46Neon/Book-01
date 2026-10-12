import json
import logging
import uuid

import httpx
import pytest
from fastapi.testclient import TestClient

from app import TOOL_REGISTRY, create_app, groq_tools

BOOK_ID = "bc255014-b7d0-4eb5-9b88-444de9867f39"
ACCOUNT_ID = "4a48234c-198e-44cc-ae74-bc573b39006c"
USER_TOKEN = "synthetic-user-bearer-secret"
GROQ_KEY = "synthetic-groq-key-secret"


def groq_tool_call(name="getTrialBalance", arguments=None):
    return {"choices": [{"message": {"role": "assistant", "content": None, "tool_calls": [{
        "id": "call-test-1", "type": "function", "function": {"name": name, "arguments": json.dumps(arguments or {
            "dateFrom": "2025-01-01", "dateTo": "2025-01-31", "currency": "USD"})}
    }]}}]}


def groq_text(text="Consulta completada con datos autorizados."):
    return {"choices": [{"message": {"role": "assistant", "content": text}}]}


def app_with(groq_handler, accounting_handler, *, groq_key=GROQ_KEY, base="https://accounting.example.test/api/v1"):
    groq_client = httpx.AsyncClient(transport=httpx.MockTransport(groq_handler))
    accounting_client = httpx.AsyncClient(transport=httpx.MockTransport(accounting_handler))
    app = create_app(groq_client=groq_client, accounting_client=accounting_client,
                     groq_api_key=groq_key, accounting_base_url=base)
    return app, groq_client, accounting_client


def ask(client, message="¿Cuál es mi balance?", token=USER_TOKEN):
    headers = {"Authorization": f"Bearer {token}"} if token else {}
    return client.post("/v1/chat", headers=headers, json={"bookId": BOOK_ID, "message": message})


def test_authentication_is_required_before_any_model_or_accounting_call():
    touched = []
    app, g, a = app_with(lambda req: touched.append("groq") or httpx.Response(200, json=groq_text()),
                         lambda req: touched.append("accounting") or httpx.Response(200, json={"period": {}, "currency": "USD", "rows": [], "totals": {}}))
    with TestClient(app) as client:
        response = ask(client, token=None)
    assert response.status_code == 401
    assert response.json()["code"] == "authentication_required"
    assert touched == []


def test_missing_runtime_configuration_fails_closed():
    app, g, a = app_with(lambda req: pytest.fail("Groq must not be called"), lambda req: pytest.fail("API must not be called"), groq_key="", base="")
    with TestClient(app) as client:
        response = ask(client)
    assert response.status_code == 503
    assert response.json()["code"] == "service_unavailable"


@pytest.mark.parametrize("status,expected", [(401, "accounting_unauthorized"), (403, "accounting_forbidden")])
def test_accounting_auth_and_forbidden_denials_are_preserved(status, expected):
    def groq(req):
        return httpx.Response(200, json=groq_tool_call())
    def accounting(req):
        return httpx.Response(status, json={"message": "synthetic denial"})
    app, g, a = app_with(groq, accounting)
    with TestClient(app) as client:
        response = ask(client)
    assert response.status_code == status
    assert response.json()["code"] == expected
    assert "denied" in response.json()["message"].lower() or status == 401


@pytest.mark.parametrize("args", [
    {"dateFrom": "03/04/2025", "dateTo": "2025-04-03", "currency": "USD"},
    {"dateFrom": "2025-02-01", "dateTo": "2025-01-01", "currency": "USD"},
    {"dateFrom": "2025-02-30", "dateTo": "2025-03-01", "currency": "USD"},
    {"dateFrom": "2025-01-01", "dateTo": "2025-01-31", "currency": "XXX"},
    {"dateFrom": "2025-01-01", "dateTo": "2025-01-31", "currency": "USD", "sql": "DROP TABLE"},
    {"dateFrom": "2000-01-01", "dateTo": "2025-01-01", "currency": "USD"},
])
def test_malformed_or_ambiguous_tool_arguments_fail_before_accounting(args):
    called = []
    app, g, a = app_with(lambda req: httpx.Response(200, json=groq_tool_call(arguments=args)),
                         lambda req: called.append(req) or httpx.Response(200, json={}))
    with TestClient(app) as client:
        response = ask(client)
    assert response.status_code == 502
    assert response.json()["code"] == "invalid_tool_arguments"
    assert called == []


def test_only_five_allowlisted_tools_and_no_write_or_scope_arguments():
    expected = {"listBookAccounts", "listJournalEntries", "getAccountBalance", "getTrialBalance", "getIncomeStatement"}
    assert set(TOOL_REGISTRY) == expected
    tools = groq_tools()
    assert {tool["function"]["name"] for tool in tools} == expected
    for tool in tools:
        schema = tool["function"]["parameters"]
        assert schema["additionalProperties"] is False
        assert "bookId" not in schema["properties"]
        assert "sql" not in schema["properties"]
        assert not any(word in tool["function"]["name"].lower() for word in ("create", "update", "delete", "post", "write"))


def test_valid_read_forwards_user_bearer_only_to_accounting_and_structures_result(caplog):
    seen = {}
    def groq(req):
        seen["groq_auth"] = req.headers.get("authorization")
        seen["groq_body"] = json.loads(req.content)
        if any(message["role"] == "tool" for message in seen["groq_body"]["messages"]):
            return httpx.Response(200, json=groq_text("El período consultado está balanceado."))
        return httpx.Response(200, json=groq_tool_call())
    def accounting(req):
        seen["accounting_auth"] = req.headers.get("authorization")
        seen["accounting_url"] = str(req.url)
        return httpx.Response(200, json={
            "period": {"from": "2025-01-01", "to": "2025-01-31"}, "currency": "USD",
            "rows": [{"code": "100", "name": "Caja", "closingDebit": {"amount": "10.00", "currency": "USD"}}],
            "totals": {"balanced": True}
        })
    app, g, a = app_with(groq, accounting)
    with caplog.at_level(logging.WARNING):
        with TestClient(app) as client:
            response = ask(client)
    assert response.status_code == 200
    body = response.json()
    assert body["period"] == {"dateFrom": "2025-01-01", "dateTo": "2025-01-31"}
    assert body["currency"] == "USD"
    assert body["rows"][0]["name"] == "Caja"
    assert body["summary"]["totals"]["balanced"] is True
    assert seen["accounting_auth"] == f"Bearer {USER_TOKEN}"
    assert seen["groq_auth"] == f"Bearer {GROQ_KEY}"
    assert USER_TOKEN not in (seen["groq_auth"] or "")
    assert f"/books/{BOOK_ID}/reports/trial-balance" in seen["accounting_url"]
    assert USER_TOKEN not in response.text and GROQ_KEY not in response.text
    assert USER_TOKEN not in caplog.text and GROQ_KEY not in caplog.text


def test_unsupported_model_tool_is_rejected_without_backend_call():
    called = []
    app, g, a = app_with(lambda req: httpx.Response(200, json=groq_tool_call(name="deleteBook", arguments={})),
                         lambda req: called.append(req) or httpx.Response(200, json={}))
    with TestClient(app) as client:
        response = ask(client)
    assert response.status_code == 502
    assert response.json()["code"] == "invalid_tool_arguments"
    assert called == []


def test_accounting_timeout_is_not_converted_to_empty_results():
    app, g, a = app_with(lambda req: httpx.Response(200, json=groq_tool_call()),
                         lambda req: (_ for _ in ()).throw(httpx.ReadTimeout("synthetic timeout")))
    with TestClient(app) as client:
        response = ask(client)
    assert response.status_code == 504
    assert response.json()["code"] == "accounting_timeout"
    assert response.json().get("rows") is None


def test_backend_failure_is_not_fabricated_as_an_answer():
    app, g, a = app_with(lambda req: httpx.Response(200, json=groq_tool_call()),
                         lambda req: httpx.Response(503, json={"message": "synthetic outage"}))
    with TestClient(app) as client:
        response = ask(client)
    assert response.status_code == 503
    assert response.json()["code"] == "accounting_unavailable"


def test_bad_groq_json_or_tool_argument_json_returns_typed_error():
    app, g, a = app_with(lambda req: httpx.Response(200, json={"choices": [{"message": {"tool_calls": [{"id": "x", "function": {"name": "getTrialBalance", "arguments": "{"}}]}}]}),
                         lambda req: pytest.fail("accounting should not be called"))
    with TestClient(app) as client:
        response = ask(client)
    assert response.status_code == 502
    assert response.json()["code"] == "invalid_tool_arguments"


def test_accounting_request_has_explicit_server_selected_book_scope():
    captured = {}
    def groq(req):
        messages = json.loads(req.content)["messages"]
        if any(message["role"] == "tool" for message in messages):
            return httpx.Response(200, json=groq_text("Balance consultado."))
        return httpx.Response(200, json=groq_tool_call(name="getAccountBalance", arguments={"accountId": ACCOUNT_ID, "asOf": "2025-02-01", "currency": "USD"}))
    def accounting(req):
        captured["url"] = str(req.url)
        return httpx.Response(200, json={"balance": "23.10", "currency": "USD", "asOf": "2025-02-01", "accountId": ACCOUNT_ID, "bookId": BOOK_ID})
    app, g, a = app_with(groq, accounting)
    with TestClient(app) as client:
        response = ask(client)
    assert response.status_code == 200
    assert f"/books/{BOOK_ID}/accounts/{ACCOUNT_ID}/balance" in captured["url"]
    assert response.json()["summary"]["balance"] == "23.10"
