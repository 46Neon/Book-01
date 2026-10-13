# Supabase schema (initial core)

Supabase Postgres is the shared database source. FastAPI remains Book-01's accounting API and authorization boundary; the APK, web client, and chat service must call that API and must not write directly to database tables. This initial schema supports USD only and makes no foreign-exchange or fiscal-compliance claim.

## Migration order

Apply the SQL files in `supabase/migrations/` in filename order using a reviewed, versioned migration workflow. The current migrations are:

1. `202610120001_initial_core.sql` — creates tenant-scoped books and memberships, USD accounts and periods, draft journal entries and lines, RLS policies, constraints, and trigger-generated append-only audit events. Book-scoped composite foreign keys prevent cross-book references. Money uses PostgreSQL `numeric` (never floating point), with finite, nonnegative, two-decimal validation for USD line amounts; entry idempotency keys are unique per book.
2. `202610120002_read_only_rest_access.sql` — revokes all table privileges from `PUBLIC`, `anon`, and `authenticated` on the eight Book-01 tables, then grants only `SELECT` to `authenticated`. It documents the intentionally read-only Data API boundary.

The first migration assumes the standard Supabase `auth` schema, `auth.users`, `auth.uid()` function, and `anon` / `authenticated` database roles already exist. Do not apply it to plain PostgreSQL without those Supabase Auth prerequisites. CI creates minimal test-only equivalents to exercise SQL behavior; that is not a Supabase CLI or hosted-project validation.

## Current development project status

The Supabase organization **Book-01 Free** and development project **Book-01-dev** exist (project ref `qjbduqwwpbnyxwyxfdsj`, East US (Ohio), Free plan). The Data API is enabled, automatic exposure of new tables is disabled, and automatic RLS is enabled. Supabase Auth email provider and email confirmation are enabled; public sign-up is disabled. No Auth users exist. **Neither migration has been applied to the project yet**; there is no production project, real ledger data, or deployed API/chat service. Project setup is not evidence that Book-01 is live-integrated.

Migrations are versioned in this repository; do not make unversioned SQL changes in the Dashboard or run `db reset`. Connect only this development project to GitHub after reviewing the migrations and CI, because GitHub pushes may automatically deploy schema changes. Confirm the target is `Book-01-dev` before any connection or migration application.

## Data API permissions and posting intentionally blocked

RLS is defense in depth, not a replacement for backend authorization. FastAPI must validate the Supabase JWT issuer, audience, expiry and identity; derive user identity from the verified token rather than client-supplied IDs; and check membership and role for every book-scoped operation.

The `anon` role has no table privileges. `authenticated` has `SELECT` only on the eight listed Book-01 tables; direct `INSERT`, `UPDATE`, `DELETE`, `TRUNCATE`, `REFERENCES`, and `TRIGGER` privileges are intentionally absent. No client may perform direct DML, including edits to draft entries or lines. There is no `post_journal_entry` RPC or other posting transition. Triggers reject inserting/changing entries to `posted` or `void`; do not grant a direct path to `posted`.

Before enabling any accounting mutation or posting operation, implement and review a narrow transactional API operation that validates `auth.uid()`, current membership/role, open accounting period and date match, active same-book accounts, at least two lines, balanced debit/credit totals, idempotent repeats, and atomic status change plus audit event. If implemented as `SECURITY DEFINER`, it must use a fixed empty `search_path`, fully qualify objects, revoke default/public execute, and grant execute only to the intended caller role. Direct table updates must remain unable to set status. Add runtime tests for failure and concurrency cases before any write grant is introduced.

Never use a `service_role` key in user traffic, browsers, APKs, GitHub, or prompts. Groq must not be configured or used until Elisa explicitly approves sending prompts and accounting results to it. Do not deploy the API or chat service until a hosting provider and HTTPS domain have been selected.

## Tests and validation limits

`.github/workflows/supabase-db.yml` runs on PostgreSQL 16. It bootstraps minimal Supabase Auth/roles for CI, applies the baseline migration, runs `supabase/tests/rls_and_invariants.sql`, applies subsequent versioned migrations in filename order, then runs `supabase/tests/read_only_permissions.sql`. The latter checks RLS, the expected SELECT-only grants, and denied direct mutations under the authenticated role. The latest run for commit `64e2049` completed successfully: [run #6](https://github.com/46Neon/Book-01/actions/runs/38330073949). This is PostgreSQL CI with test bootstrap—not a live Supabase Auth/RLS test, not validation with the Supabase CLI, and not API/web/chat/Android end-to-end testing. The run reported GitHub runner/action deprecation notices, not SQL test failures.

The opt-in, read-only live Auth/RLS smoke test is documented in [`docs/supabase-live-rls-smoke-test.md`](../docs/supabase-live-rls-smoke-test.md). It requires two real, controlled test Auth users, distinct books, memberships, and at least one account per book. None have been created. Do not invent users or provision them from the browser; arrange controlled test identities and follow that document. Its success would demonstrate only real Auth-token and PostgREST RLS isolation for those test records, not API or client integration.

## Required work before real integration

Implement Part 1's authenticated accounting API and authorization model; add end-to-end tests against the actual development Supabase project (including cross-tenant RLS checks); define controlled test-user provisioning; and document operational backup/restore and migration rollback procedures. The existing Part 2 remains a read-only scaffold and must consume the accounting API rather than connect directly to PostgreSQL. No production readiness or fiscal/legal compliance is claimed.
