# Supabase / Edge Functions target architecture

Owner-directed target: move both FastAPI server services (the accounting API and Part 2 conversational gateway) to Supabase Edge Functions (Deno/TypeScript), using Supabase Postgres/Auth. The Vite web app and Android APK remain clients and must call the server/API boundary; neither client may connect directly to PostgreSQL or perform table writes. This repository currently contains **only a Deno-testable candidate vertical slice** of that port: `accounting-api` serves books and accounts. It is not a complete backend migration and is not deployed.

Supabase Edge Functions do not host the Vite static client. A static hosting target and HTTPS domain are still to be selected; no provider or cost is approved by this work. Android remains a separate client.

## Current Edge Function route mapping and parity status

Supabase function base URL is `https://<project-ref>.supabase.co/functions/v1/accounting-api`; the function exposes the `/api/v1` contract suffixes below:

| Existing contract | Edge Function path | Status |
| --- | --- | --- |
| `GET /api/v1/books` | `GET /functions/v1/accounting-api/api/v1/books` | Implemented as the initial TypeScript slice; cursor/page-size behavior and response envelope retained. |
| `GET /api/v1/books/{bookId}/accounts` | `GET /functions/v1/accounting-api/api/v1/books/{bookId}/accounts` | Implemented as the initial TypeScript slice; membership is checked through RLS-visible book lookup. |
| `GET /api/v1/books/{bookId}/journal-entries` | same suffix under `accounting-api` | Not yet ported; existing FastAPI source remains for parity review. |
| `GET /api/v1/books/{bookId}/accounts/{accountId}/balance` | same suffix under `accounting-api` | Not yet ported. |
| `GET /api/v1/books/{bookId}/reports/trial-balance` | same suffix under `accounting-api` | Not yet ported. |
| `GET /api/v1/books/{bookId}/reports/income-statement` | same suffix under `accounting-api` | Not yet ported; existing API intentionally returns a limitation where cost-vs-expense cannot be faithfully classified. |
| Part 2 chat/book-list routes | A second conversational Edge Function is not yet implemented. | Not ported. No model integration is configured. |
| Part 2B proposal creation/confirmation routes | Not yet exposed through an Edge Function. | SQL migration/RPC work exists separately; no client or edge route is complete. |

The FastAPI source under `backend/` and `part2/service/` is retained for route-contract/parity review, not declared as the target hosting architecture. The Edge Function slice is read-only and denies unknown paths/parameters and write methods. It calls Auth `/user` to verify the bearer token server-side, derives only the identity returned by Auth, then uses the caller's JWT and publishable key for PostgREST reads. Postgres RLS remains the tenant boundary. It does not accept user/tenant/role claims from clients and does not use a `service_role` secret.

## Function configuration and local testing

`supabase/config.toml` enables gateway JWT verification for `accounting-api`; the function also verifies the bearer with Supabase Auth. The function reads:

- `SUPABASE_URL` — Supabase project URL.
- `SUPABASE_PUBLISHABLE_KEY` (preferred) or `SUPABASE_ANON_KEY` — public project key used in the server-side Auth/PostgREST calls.
- `CORS_ALLOWED_ORIGINS` — optional comma-separated exact HTTPS origins; unset means browser cross-origin calls are denied. Do not use wildcard origins.

No service-role/secret key belongs in source, web, Android, or caller requests. The project URL and publishable key are public configuration, not secrets. The Edge Function does not yet implement the complete existing accounting contract or conversational service. `.github/workflows/supabase-functions.yml` runs Deno type-checking/tests with mocked Auth/PostgREST responses only; it never contacts the hosted project. No local Deno executable was available in the implementation environment, so those tests were authored but not executed there; the workflow result is the next validation gate.

## Migrations and mutation boundary

Migrations in filename order:

1. `202610120001_initial_core.sql` — tenant-scoped books and memberships, USD accounts/periods, draft journal entries/lines, RLS, constraints, triggers and audit events.
2. `202610120002_read_only_rest_access.sql` — removes direct DML from application roles and grants only direct table SELECT to authenticated callers.
3. `202610130001_draft_proposals.sql` — introduces narrow draft-proposal and confirmation RPCs; it does not enable posting or voiding.

Migrations are versioned in this repository. This task did not connect to or inspect the hosted project, apply/rollback migrations, deploy functions, or change cloud resources. Therefore this document makes **no claim** about the present remote migration state or hosted side effects. A successful GitHub Supabase Preview check is not evidence that production/development state has been reviewed or that any endpoint is deployed.

Never weaken direct-DML revocations or use a `service_role` secret for user data operations. The permitted 2B path must call only narrow RPCs; the confirm request may take only an opaque proposal ID plus the caller bearer—not a replacement payload or `confirmed` flag. No model tool may confirm. Before exposing those routes, return the complete canonical proposal payload and expiry through a safe owner-scoped response, preserve atomicity/idempotency/role checks/expiry/audit/draft-only status, and have the UI display it and require an explicit human confirmation action.

Policy values currently present in SQL/code (roles `owner`, `admin`, `editor` as applicable, maximum 50 lines, 10-minute proposal expiry, 1,000-character line descriptions) are **provisional and unapproved for live use** until owner review.

## Security, model, provider and release blockers

- No client-supplied `user_id`, role, or tenant claim is trusted. Every operation must verify Auth server-side and use the caller JWT / RLS for book visibility and authorization.
- Chat must remain tool-allowlisted and GET-only, except for the explicit 2B proposal and confirm endpoints. No arbitrary SQL, model-authored URLs, or model-triggered write/confirm action.
- `GROQ_API_KEY` must remain absent/unconfigured until the owner explicitly approves sending private accounting prompts/results to Groq. If later approved, it belongs only in Supabase Function secrets. No private accounting data was sent externally for this slice.
- No search provider is approved. Do not configure search or request provider keys until the owner chooses a provider and approves data processing.
- No hosted migration, function deployment, dashboard, CLI, remote validation, or cloud-resource change was performed. Auth deployment/configuration and live RLS/function validation remain pending.
- Preserve USD-only behavior and the accounting API's bounded inputs/results while porting the remaining read routes; the web/Android clients remain untrusted.

## CI and release status

The existing `.github/workflows/supabase-db.yml` Postgres migration/RLS workflow remains in place. It tests SQL against CI Postgres with test-only Auth scaffolding; it is not hosted Supabase validation. The new Edge Function workflow is likewise local-mock CI, not proof of hosted Auth, RLS, migration, or deployment state. Before releasing, complete API route parity tests, port and test the conversational service, integrate explicit 2B endpoints/UI safely, review provisional policy values with the owner, validate controlled test users against the intended Supabase project, select static web hosting/domain, and receive owner approval for any model/search data processing.
