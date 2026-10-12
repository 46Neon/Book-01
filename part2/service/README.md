# Part 2A read-only conversational service (scaffold)

This is a **testable, non-production scaffold** for the server-side conversational layer. It exposes `POST /v1/chat`, requires a caller bearer token and an explicit UUID `bookId`, and provides the model only five allowlisted read tools matching the operation IDs and `GET` paths in `docs/parte-2a-openapi-propuesta.yaml`:

- `listBookAccounts` → `GET /books/{bookId}/accounts`
- `listJournalEntries` → `GET /books/{bookId}/journal-entries`
- `getAccountBalance` → `GET /books/{bookId}/accounts/{accountId}/balance`
- `getTrialBalance` → `GET /books/{bookId}/reports/trial-balance`
- `getIncomeStatement` → `GET /books/{bookId}/reports/income-statement`

There is no SQL interface, arbitrary URL access, write operation, confirmation tool, or runtime mock accounting data. The service injects the request's explicit `bookId` into the accounting API path; it is not a model argument. This scaffold does not validate identity itself, so the accounting API must validate the caller's bearer token and authorize that book on every request (with PostgreSQL/Supabase RLS as defense in depth). The caller's bearer token is sent only to the accounting API, never to Groq. Redirects are disabled so an accounting bearer is not forwarded across redirects.

Supabase-managed PostgreSQL will sit behind the Part 1 accounting API. This conversational service must not connect directly to the database and does not need a Supabase service-role key. `GROQ_API_KEY` belongs only in this service's server-side deployment secret environment; database credentials, `SUPABASE_SERVICE_ROLE_KEY`, if needed, belong only to the accounting backend. Neither belongs in the web app, APK, Git, logs, or chat.

## Current limitations and required Part 1 dependencies

The OpenAPI file is a **draft proposal**, not an implemented or canonical production API. No deployed accounting API, identity provider, or Groq key is supplied with this repository. This scaffold does not prove accounting access control and must not be presented as production integration. Before deployment, the owner/backend team must provide and validate:

1. The real Part 1 API, final OpenAPI/auth contract, bearer-token issuer/audience/claims expectations, UUID/resource semantics, error contract, and supported currencies. The Part 1 API must enforce book-level access for every request and return only authorized data.
2. A deployment with HTTPS at the service boundary and for the accounting API, secret management for `GROQ_API_KEY`, network egress controls, rate limits, monitoring, and operational review. This sample does not authenticate or issue caller tokens itself.
3. An owner-provided Groq key and approved model/data-processing configuration. User prompts and accounting query results are sent to Groq from the server; do not configure a key until that use is approved.
4. Contract-level integration tests against the final Part 1 API and security review before wiring the static `part2/web/` page to this endpoint.

The `SUPPORTED_CURRENCIES` default is `USD`, matching the current Part 1 prototype. Widen it only after the final Part 1 API contract and tested currency support explicitly allow additional currencies. Date ranges are inclusive and limited to ten years in this scaffold. The `ACCOUNTING_API_BASE_URL` must include the Part 1 API prefix, e.g. `https://api.example.invalid/api/v1` (placeholder only).

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
