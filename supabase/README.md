# Supabase schema (initial core)

## Migration order

Apply SQL files in `supabase/migrations/` in filename order with the Supabase CLI (`supabase db push`) or the project's reviewed migration pipeline. `202610120001_initial_core.sql` is the first migration and assumes the standard Supabase `auth` schema, `auth.users`, `auth.uid()` function, and `anon` / `authenticated` database roles already exist. Do not apply this migration to a plain PostgreSQL database without those Supabase Auth prerequisites; the CI test bootstraps minimal equivalents only for exercising SQL behavior.

The migration creates books (tenants), memberships and roles, USD-only accounts and periods, draft journal entries and lines, and trigger-generated append-only audit events. Book-scoped composite foreign keys prevent a line or period reference from crossing books. Money is PostgreSQL `numeric` (never floating point), with explicit finite, nonnegative, two-decimal validation for USD line amounts. Entry idempotency keys are unique within each book.

## Deployment prerequisites and safety boundaries

- Create separate Supabase development, test, and production projects; enable Supabase Auth and define the final issuer/audience and user lifecycle before connecting clients.
- Review and apply migrations through a controlled pipeline. No Supabase project, database deployment, credentials, or secrets were created or configured as part of this commit.
- Clients (APK, web, and chat) must call the accounting API only. Do not distribute a database password or Supabase `service_role` key. Keep Groq and any privileged Supabase credentials in server-side secret storage only.
- RLS is defense in depth, not a replacement for backend authorization. All account and journal operations must be authorized by the accounting API for the authenticated user and requested book.
- USD is the only supported currency in this initial schema. This does not claim multi-currency support, fiscal/legal compliance, attachment storage, or final production readiness.

## Posting intentionally blocked

Entries default to `draft`. This migration does **not** install a `post_journal_entry` RPC: no posting transition is exposed, and triggers reject inserting or changing entries to `posted`/`void`. Draft entries and their lines remain editable subject to RLS; non-draft records are immutable. Before enabling posting, implement and review a narrow transactional operation that validates `auth.uid()`, current book membership/role, open accounting period and date match, active same-book accounts, at least two lines, equal debit/credit totals, idempotent repeats, and an atomic status change plus audit event. If implemented as `SECURITY DEFINER`, it must use a fixed empty `search_path`, fully qualify objects, revoke default/public execute, and grant execute only to the intended caller role. Keep direct table updates unable to set status. Add runtime integration tests for all failure and concurrency cases before any posting grant is introduced.

## Required backend/API work before real integration

Implement Part 1's authenticated accounting API and authorization model; validate Supabase JWT issuer, audience, expiry, and identity; derive user identity from the verified token (never from client-supplied user IDs); enforce membership and role for every book-scoped operation; use transactions and idempotency keys; and keep database access on the server. Add end-to-end tests against an actual Supabase project (including cross-tenant RLS checks), operational backup/restore and migration rollback procedures, and secret management. Existing Part 2 remains a read-only scaffold and must consume the accounting API rather than connect to PostgreSQL.

## Tests

`.github/workflows/supabase-db.yml` starts PostgreSQL 16, bootstraps Supabase Auth/roles for tests, applies the migration, and runs `supabase/tests/rls_and_invariants.sql`. This exercises SQL behavior against PostgreSQL, including role/RLS, constraints, audit append-only behavior, and blocked posting. The CI bootstrap is not a substitute for testing against Supabase Auth/RLS in the deployed Supabase environment.

## Live Auth/RLS smoke test

After creating two isolated test users/books in a Supabase development project, run the opt-in, read-only test described in [`docs/supabase-live-rls-smoke-test.md`](../docs/supabase-live-rls-smoke-test.md). It checks real Auth tokens and that each user can read only their own book and accounts. It is skipped unless explicitly enabled and is not a substitute for end-to-end testing of the deployed API, web client, chat service, or Android synchronization.
