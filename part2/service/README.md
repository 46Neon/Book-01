# Part 2A read-only conversational service (scaffold)

This is a **testable, non-production scaffold** for the server-side conversational layer. It exposes `POST /v1/chat`, requires a caller bearer token and an explicit UUID `bookId`, and provides the model only five allowlisted read tools matching the operation IDs and `GET` paths in `docs/parte-2a-openapi-propuesta.yaml`:

- `listBookAccounts` → `GET /books/{bookId}/accounts`
- `listJournalEntries` → `GET /books/{bookId}/journal-entries`
- `getAccountBalance` → `GET /books/{bookId}/accounts/{accountId}/balance`
- `getTrialBalance` → `GET /books/{bookId}/reports/trial-balance`
- `getIncomeStatement` → `GET /books/{bookId}/reports/income-statement`

There is no SQL interface, arbitrary URL access, write operation, confirmation tool, or runtime mock accounting data. The service injects `bookId` from the authenticated request context into the accounting API URL; it is not a model argument. The caller's bearer token is sent only to the accounting API, which must perform the definitive user/book authorization check. The Groq key is server-side configuration only. Redirects are disabled so an accounting bearer is not forwarded across redirects.

## Current limitations and required Part 1 dependencies

The OpenAPI file is a **draft proposal** with response fields revised to match the current migration; it is not a canonical production API. The FastAPI read API and this service can be tested end-to-end with synthetic HTTP transports, but no Supabase project, JWT issuer/auth settings, deployment, HTTPS endpoint, or Groq key is available. Tests do not prove live authorization. The account API forwards the caller bearer to Supabase PostgREST and relies on project JWT validation, grants, and RLS. The API currently exposes the database's actual status values (`draft`, `posted`, `void`), account types (`asset`, `liability`, `equity`, `income`, `expense`), USD amounts, descriptions, and lines; it does not invent reference, counterparty, parent-account, or reversal fields. The income statement remains typed `501 not_implemented` when posted data exists because the schema does not distinguish costs from expenses.

Before a real deployment, securely configure and validate:

1. Non-secret project settings: Supabase project URL/ref (`SUPABASE_URL`), project Auth issuer/audience and enabled sign-in provider settings, REST schema exposure/grants for `authenticated`, RLS/membership policies and test users/books, the HTTPS accounting API base URL (`ACCOUNTING_API_BASE_URL`, including `/api/v1`), HTTPS Part 2 service URL, browser origin/CORS or same-origin routing, and a reviewed model/data-processing policy.
2. Runtime settings via the hosting provider's secure environment/secrets: Supabase publishable/anon key for the server's `apikey` header (`SUPABASE_PUBLISHABLE_KEY` or `SUPABASE_ANON_KEY`), `GROQ_API_KEY` (secret; only after model processing is approved), and optional `GROQ_MODEL`. Never put caller JWTs or Groq keys in source, browser storage, or logs. Do not use a service-role key.
3. Apply the committed Supabase migrations to the selected project and prove with real signed-in test users that JWT validation, book RLS, no cross-book access, and GET-only behavior work. Run contract integration tests against that deployment before considering a live integration.
4. Add an approved browser sign-in/session adapter before connecting `part2/web/`. The page must keep an access token in memory only (not localStorage/sessionStorage), send it only to the HTTPS Part 2 service, and fail closed on missing/expired auth. The current web shell intentionally has no network calls or auth adapter.

`SUPPORTED_CURRENCIES` defaults to `USD`, matching the committed database constraint. Override it only after the backend/schema supports the same currencies and the owner approves that domain change. Date ranges are inclusive and limited to ten years in the conversational service. FastAPI remains provisional/reversible.

## Local setup

Python 3.11+ is required.

```sh
cd part2/service
python -m venv .venv
. .venv/bin/activate
python -m pip install -e '.[test]'
```

Set environment variables in the server environment (do not commit `.env` files or real values):

```sh
export GROQ_API_KEY='<owner-provided Groq key>'
export ACCOUNTING_API_BASE_URL='https://api.example.invalid/api/v1'
export SUPPORTED_CURRENCIES='USD'
# Optional; default shown:
export GROQ_MODEL='llama-3.3-70b-versatile'
uvicorn app:app --host 127.0.0.1 --port 8000
```

Example request (replace the placeholders; never paste a real bearer token into logs or source control):

```sh
curl -X POST http://127.0.0.1:8000/v1/chat \
  -H 'Authorization: Bearer <caller-token>' \
  -H 'Content-Type: application/json' \
  -d '{"bookId":"00000000-0000-4000-8000-000000000001","message":"Resume el balance de comprobación del período"}'
```

Missing service configuration fails closed with HTTP 503. Missing bearer authentication returns 401. Accounting 401/403/404/400/422 responses remain distinguishable; network timeouts return 504, and upstream availability/contract failures return typed errors rather than fabricated accounting results. Error payloads contain a request ID and do not echo credentials or upstream bodies.

## Validation

Run the unit tests from this directory:

```sh
python -m pytest
```

Tests use only `httpx.MockTransport` and synthetic fixtures; they do not connect to Groq or an accounting backend. They cover authentication, upstream authorization denials, date/currency/argument validation, supported tools, scope injection, timeouts, malformed tool calls, and credential non-disclosure. These tests verify the scaffold, not the real Part 1 contract or production authorization.
