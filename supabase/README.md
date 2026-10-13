# Supabase / Edge Functions target architecture

Owner-directed target: move both FastAPI server services (the accounting API and Part 2 conversational gateway) to Supabase Edge Functions (Deno/TypeScript), using Supabase Postgres/Auth. The Vite web app and Android APK remain clients and must call the server/API boundary; neither client may connect directly to PostgreSQL or perform table writes. This repository contains a Deno-testable candidate vertical slice: `accounting-api` serves books and accounts, while the remaining accounting routes and Part 2 routes are incomplete. One `accounting-api` Edge Function is deployed on Book-01-dev with gateway JWT verification enabled; the dashboard reports zero invocations. The exact deployed source commit could not be verified, so deployment does not prove the remote code matches this repository.

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

No service-role/secret key belongs in source, web, Android, or caller requests. The project URL and publishable key are public configuration, not secrets. The Edge Function does not yet implement the complete existing accounting contract or conversational service. `.github/workflows/supabase-functions.yml` runs Deno type-checking/tests with mocked Auth/PostgREST responses only; it never contacts the hosted project. The workflow passed for current `main` commit `1015d8b` ([run 38463225904](https://github.com/46Neon/Book-01/actions/runs/38463225904)); this is mocked CI only and does not contact or validate the hosted function.

## Migrations and mutation boundary

Migrations in filename order:

1. `202610120001_initial_core.sql` — tenant-scoped books and memberships, USD accounts/periods, draft journal entries/lines, RLS, constraints, triggers and audit events.
2. `202610120002_read_only_rest_access.sql` — removes direct DML from application roles and grants only direct table SELECT to authenticated callers.
3. `202610130001_draft_proposals.sql` — introduces narrow draft-proposal and confirmation RPCs; it does not enable posting or voiding.
4. `202610130002_account_hierarchy_counterparty.sql` — adds nullable same-book parent-account IDs and optional journal counterparties to align with Room v10. Historical values remain `NULL`; parent links are same-book/same-type and immutable after insertion; counterparties are normalized, control-free text limited to 80 characters. This adds no client DML grants.

Migrations are versioned in this repository.

## Verified live development project state (2026-10-13)

The live project is **Book-01-dev** (project ref `qjbduqwwpbnyxwyxfdsj`). Its migration history contains all four migrations above: `202610120001_initial_core`, `202610120002_read_only_rest_access`, `202610130001_draft_proposals`, and `202610130002_account_hierarchy_counterparty`. Nine public tables exist: `accounting_periods`, `accounts`, `audit_events`, `book_memberships`, `books`, `currencies`, `draft_proposals`, `journal_entries`, and `journal_entry_lines`. RLS is enabled on all nine; `FORCE ROW LEVEL SECURITY` is false on all. Policy counts are: `accounting_periods` 3, `accounts` 3, `audit_events` 1, `book_memberships` 4, `books` 3, `currencies` 1, `draft_proposals` 0, `journal_entries` 3, and `journal_entry_lines` 4.

On the original eight tables, `authenticated` has direct `SELECT` only; there are no `anon`/`PUBLIC` grants or direct DML grants. `draft_proposals` has no table-grant rows. The two proposal RPCs, `create_draft_proposal` and `confirm_draft_proposal`, are `SECURITY DEFINER` with an empty fixed `search_path`; `EXECUTE` is granted to `authenticated` only, not `anon`. They require `auth.uid()` and check ownership, role, date/period, active USD accounts, and balanced draft data; confirmation creates an entry with `draft` status, never a posted entry. The 10-minute proposal expiry and 50-line cap are provisional and require owner approval before proposal routes are exposed. This metadata does not amount to a live Auth-token test: there are no Auth users and no real-token/two-identity RLS test has been run. No real ledger data is present. GitHub Supabase **Deploy to production** targets only Book-01-dev; there is no separate production project. The deployed function has not been confirmed to correspond to a particular repository commit. These facts describe the observed development project only; CI/Preview results are not evidence of live Auth/RLS behavior. This is not production-ready: there is no separate production project or real ledger data, and Groq has not been approved for use and must remain unconfigured.

Never weaken direct-DML revocations or use a `service_role` secret for user data operations. The permitted 2B path must call only narrow RPCs; the confirm request may take only an opaque proposal ID plus the caller bearer—not a replacement payload or `confirmed` flag. No model tool may confirm. Before exposing those routes, return the complete canonical proposal payload and expiry through a safe owner-scoped response, preserve atomicity/idempotency/role checks/expiry/audit/draft-only status, and have the UI display it and require an explicit human confirmation action.

Policy values currently present in SQL/code (roles `owner`, `admin`, `editor` as applicable, maximum 50 lines, 10-minute proposal expiry, 1,000-character line descriptions) are **provisional and unapproved for live use** until owner review.

## Security, model, provider and release blockers

- No client-supplied `user_id`, role, or tenant claim is trusted. Every operation must verify Auth server-side and use the caller JWT / RLS for book visibility and authorization.
- Chat must remain tool-allowlisted and GET-only, except for the explicit 2B proposal and confirm endpoints. No arbitrary SQL, model-authored URLs, or model-triggered write/confirm action.
- `GROQ_API_KEY` must remain absent/unconfigured until the owner explicitly approves sending private accounting prompts/results to Groq. If later approved, it belongs only in Supabase Function secrets. No private accounting data was sent externally for this slice.
- No search provider is approved. Do not configure search or request provider keys until the owner chooses a provider and approves data processing.
- This documentation update did not apply migrations, deploy functions, change dashboard settings, create users, or modify cloud resources. There are zero function invocations and no real Auth-token/two-identity RLS validation; arrange controlled Auth identities before attempting that test.
- Preserve USD-only behavior and the accounting API's bounded inputs/results while porting the remaining read routes; the web/Android clients remain untrusted.

## CI and release status

On current `main` commit `1015d8b`, the Supabase database migration checks passed ([run 38463225931](https://github.com/46Neon/Book-01/actions/runs/38463225931)) and the mocked Edge Function tests passed ([run 38463225904](https://github.com/46Neon/Book-01/actions/runs/38463225904)). The database workflow tests SQL against CI Postgres with test-only Auth scaffolding; the function workflow uses mocked Auth/PostgREST responses. Neither contacts nor validates the hosted project, so neither is a live Auth/RLS, migration, or deployment test. Before releasing, complete API route parity tests, port and test the conversational service, integrate explicit 2B endpoints/UI safely, review provisional policy values with the owner, validate controlled test users against the intended Supabase project, select static web hosting/domain, and receive owner approval for any model/search data processing.
