\set ON_ERROR_STOP on

-- Create two independent tenants as their respective owners.
SET ROLE authenticated;
SET request.jwt.claim.sub = '00000000-0000-0000-0000-000000000001';
INSERT INTO public.books (name) VALUES ('Tenant One') RETURNING id AS book_one \gset
INSERT INTO public.book_memberships (book_id, user_id, role)
VALUES (:'book_one'::uuid, auth.uid(), 'owner');
INSERT INTO public.accounts (book_id, code, name, account_type)
VALUES (:'book_one'::uuid, '1000', 'Cash', 'asset') RETURNING id AS account_one \gset
INSERT INTO public.accounting_periods (book_id, name, start_date, end_date)
VALUES (:'book_one'::uuid, '2026', '2026-01-01', '2026-12-31') RETURNING id AS period_one \gset
INSERT INTO public.journal_entries (book_id, period_id, entry_date, description, idempotency_key)
VALUES (:'book_one'::uuid, :'period_one'::uuid, '2026-10-12', 'Seed draft', 'client-key-1') RETURNING id AS entry_one \gset
INSERT INTO public.journal_entry_lines
    (book_id, journal_entry_id, account_id, line_number, debit, credit)
VALUES (:'book_one'::uuid, :'entry_one'::uuid, :'account_one'::uuid, 1, 25.50, 0);

SET request.jwt.claim.sub = '00000000-0000-0000-0000-000000000002';
INSERT INTO public.books (name) VALUES ('Tenant Two') RETURNING id AS book_two \gset
INSERT INTO public.book_memberships (book_id, user_id, role)
VALUES (:'book_two'::uuid, auth.uid(), 'owner');
INSERT INTO public.accounts (book_id, code, name, account_type)
VALUES (:'book_two'::uuid, '1000', 'Cash', 'asset') RETURNING id AS account_two \gset
INSERT INTO public.accounting_periods (book_id, name, start_date, end_date)
VALUES (:'book_two'::uuid, '2026', '2026-01-01', '2026-12-31') RETURNING id AS period_two \gset

-- Viewer membership is added by tenant one's owner; save test IDs as session GUCs
-- so they remain addressable inside PL/pgSQL blocks (psql variables are not expanded there).
SET request.jwt.claim.sub = '00000000-0000-0000-0000-000000000001';
INSERT INTO public.book_memberships (book_id, user_id, role)
VALUES (:'book_one'::uuid, '00000000-0000-0000-0000-000000000003', 'viewer');
SELECT set_config('test.book_one', :'book_one', false),
       set_config('test.book_two', :'book_two', false),
       set_config('test.account_one', :'account_one', false),
       set_config('test.period_one', :'period_one', false),
       set_config('test.period_two', :'period_two', false),
       set_config('test.entry_one', :'entry_one', false);
RESET ROLE;

-- RLS isolates tenants and lets a viewer read but not mutate book data.
SET ROLE authenticated;
SET request.jwt.claim.sub = '00000000-0000-0000-0000-000000000002';
DO $$
BEGIN
    IF (SELECT count(*) FROM public.books) <> 1 THEN
        RAISE EXCEPTION 'tenant two can see a foreign or missing book';
    END IF;
    IF (SELECT count(*) FROM public.accounts) <> 1 THEN
        RAISE EXCEPTION 'tenant two can see a foreign or missing account';
    END IF;
    IF EXISTS (SELECT 1 FROM public.journal_entries WHERE book_id = '00000000-0000-0000-0000-000000000001') THEN
        RAISE EXCEPTION 'tenant two can read tenant one entry';
    END IF;
END $$;
SET request.jwt.claim.sub = '00000000-0000-0000-0000-000000000003';
DO $$
BEGIN
    IF (SELECT count(*) FROM public.journal_entries) <> 1 THEN
        RAISE EXCEPTION 'viewer cannot read member book entries';
    END IF;
    IF (SELECT count(*) FROM public.books) <> 1 THEN
        RAISE EXCEPTION 'viewer book visibility is wrong';
    END IF;
    BEGIN
        INSERT INTO public.accounts (book_id, code, name, account_type)
        VALUES (current_setting('test.book_one')::uuid, '9000', 'Forbidden', 'asset');
        RAISE EXCEPTION 'viewer unexpectedly inserted an account';
    EXCEPTION WHEN insufficient_privilege THEN NULL;
    END;
END $$;
RESET ROLE;

-- An attempted direct status transition cannot post; only the guarded migration
-- trigger can write status, and it rejects every transition away from draft.
DO $$
BEGIN
    BEGIN
        UPDATE public.journal_entries
           SET status = 'posted'
         WHERE id = current_setting('test.entry_one')::uuid;
        RAISE EXCEPTION 'direct update unexpectedly posted an entry';
    EXCEPTION WHEN SQLSTATE '55000' THEN NULL;
    END;
    IF (SELECT status FROM public.journal_entries WHERE id = current_setting('test.entry_one')::uuid) <> 'draft' THEN
        RAISE EXCEPTION 'entry status changed despite posting guard';
    END IF;
END $$;

-- Invalid precision, both-sided/zero lines, and cross-book composite references are rejected.
DO $$
BEGIN
    BEGIN
        INSERT INTO public.journal_entry_lines
            (book_id, journal_entry_id, account_id, line_number, debit, credit)
        VALUES (
            current_setting('test.book_one')::uuid, current_setting('test.entry_one')::uuid,
            current_setting('test.account_one')::uuid, 2, 1.001, 0
        );
        RAISE EXCEPTION 'fractional-cent amount unexpectedly accepted';
    EXCEPTION WHEN check_violation THEN NULL;
    END;
    BEGIN
        INSERT INTO public.journal_entry_lines
            (book_id, journal_entry_id, account_id, line_number, debit, credit)
        VALUES (
            current_setting('test.book_one')::uuid, current_setting('test.entry_one')::uuid,
            current_setting('test.account_one')::uuid, 2, 1, 1
        );
        RAISE EXCEPTION 'both-sided line unexpectedly accepted';
    EXCEPTION WHEN check_violation THEN NULL;
    END;
    BEGIN
        INSERT INTO public.journal_entries
            (book_id, period_id, entry_date, description, idempotency_key)
        VALUES (
            current_setting('test.book_one')::uuid, current_setting('test.period_two')::uuid,
            '2026-10-12', 'Cross-book', 'cross-book-key'
        );
        RAISE EXCEPTION 'cross-book period reference unexpectedly accepted';
    EXCEPTION WHEN foreign_key_violation OR check_violation THEN NULL;
    END;
    BEGIN
        INSERT INTO public.journal_entries
            (book_id, period_id, entry_date, description, idempotency_key)
        VALUES (
            current_setting('test.book_one')::uuid, current_setting('test.period_one')::uuid,
            '2026-10-12', 'Duplicate idempotency key', 'client-key-1'
        );
        RAISE EXCEPTION 'duplicate per-book idempotency key unexpectedly accepted';
    EXCEPTION WHEN unique_violation THEN NULL;
    END;
END $$;

-- Simulate a posted row as database owner, then prove it and its lines are immutable.
ALTER TABLE public.journal_entries DISABLE TRIGGER journal_entries_guard_write;
UPDATE public.journal_entries SET status = 'posted'
 WHERE id = current_setting('test.entry_one')::uuid;
ALTER TABLE public.journal_entries ENABLE TRIGGER journal_entries_guard_write;
DO $$
BEGIN
    BEGIN
        UPDATE public.journal_entries SET description = 'mutated'
         WHERE id = current_setting('test.entry_one')::uuid;
        RAISE EXCEPTION 'posted entry unexpectedly mutable';
    EXCEPTION WHEN SQLSTATE '55000' THEN NULL;
    END;
    BEGIN
        DELETE FROM public.journal_entries WHERE id = current_setting('test.entry_one')::uuid;
        RAISE EXCEPTION 'posted entry unexpectedly deletable';
    EXCEPTION WHEN SQLSTATE '55000' THEN NULL;
    END;
    BEGIN
        UPDATE public.journal_entry_lines SET debit = 30
         WHERE journal_entry_id = current_setting('test.entry_one')::uuid;
        RAISE EXCEPTION 'posted line unexpectedly mutable';
    EXCEPTION WHEN SQLSTATE '55000' THEN NULL;
    END;
    BEGIN
        DELETE FROM public.journal_entry_lines
         WHERE journal_entry_id = current_setting('test.entry_one')::uuid;
        RAISE EXCEPTION 'posted line unexpectedly deletable';
    EXCEPTION WHEN SQLSTATE '55000' THEN NULL;
    END;
END $$;

-- Audit rows exist for generated changes and reject edits/deletes.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM public.audit_events
         WHERE book_id = current_setting('test.book_one')::uuid
    ) THEN
        RAISE EXCEPTION 'expected audit events for tenant one';
    END IF;
    BEGIN
        UPDATE public.audit_events SET action = 'DELETE'
         WHERE book_id = current_setting('test.book_one')::uuid;
        RAISE EXCEPTION 'audit event unexpectedly mutable';
    EXCEPTION WHEN SQLSTATE '55000' THEN NULL;
    END;
    BEGIN
        DELETE FROM public.audit_events
         WHERE book_id = current_setting('test.book_one')::uuid;
        RAISE EXCEPTION 'audit event unexpectedly deletable';
    EXCEPTION WHEN SQLSTATE '55000' THEN NULL;
    END;
END $$;

SELECT 'Supabase core migration integration checks passed' AS result;
