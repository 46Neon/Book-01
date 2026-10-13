\set ON_ERROR_STOP on

-- Database-owner fixtures. The only user-facing path exercised below is the
-- authenticated SECURITY DEFINER RPC, with the bootstrap's simulated JWT claims.
INSERT INTO auth.users (id, email)
VALUES ('00000000-0000-0000-0000-000000000004', 'editor@test.invalid')
ON CONFLICT (id) DO NOTHING;
INSERT INTO public.books (id, name, created_by) VALUES
 ('10000000-0000-0000-0000-000000000001', 'Proposal book', '00000000-0000-0000-0000-000000000001'),
 ('10000000-0000-0000-0000-000000000002', 'Foreign book', '00000000-0000-0000-0000-000000000002');
INSERT INTO public.book_memberships (book_id, user_id, role) VALUES
 ('10000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', 'owner'),
 ('10000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000003', 'viewer'),
 ('10000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000004', 'editor'),
 ('10000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000002', 'owner');
INSERT INTO public.accounts (id, book_id, code, name, account_type, currency_code) VALUES
 ('20000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001', '1000', 'Cash', 'asset', 'USD'),
 ('20000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000001', '4000', 'Income', 'income', 'USD'),
 ('20000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000002', '1000', 'Foreign cash', 'asset', 'USD');
INSERT INTO public.accounting_periods (id, book_id, name, start_date, end_date, is_open) VALUES
 ('30000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001', 'Open 2026', '2026-01-01', '2026-12-31', true),
 ('30000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000001', 'Closed 2026', '2026-01-01', '2026-12-31', false),
 ('30000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000002', 'Foreign 2026', '2026-01-01', '2026-12-31', true);

SELECT set_config('test.dp_book', '10000000-0000-0000-0000-000000000001', false),
       set_config('test.dp_foreign_book', '10000000-0000-0000-0000-000000000002', false),
       set_config('test.dp_cash', '20000000-0000-0000-0000-000000000001', false),
       set_config('test.dp_income', '20000000-0000-0000-0000-000000000002', false),
       set_config('test.dp_foreign_account', '20000000-0000-0000-0000-000000000003', false),
       set_config('test.dp_period', '30000000-0000-0000-0000-000000000001', false),
       set_config('test.dp_closed_period', '30000000-0000-0000-0000-000000000002', false),
       set_config('test.dp_foreign_period', '30000000-0000-0000-0000-000000000003', false);

SET ROLE authenticated;
SET request.jwt.claim.sub = '00000000-0000-0000-0000-000000000001';

-- A valid owner proposal confirms only once, writes draft header + all lines,
-- and generates audit events with the authenticated actor in the same txn.
SELECT public.create_draft_proposal(
    current_setting('test.dp_book')::uuid,
    current_setting('test.dp_period')::uuid,
    '2026-10-13',
    'Customer payment',
    pg_catalog.jsonb_build_array(
        pg_catalog.jsonb_build_object('account_id', current_setting('test.dp_cash'), 'description', 'Cash received', 'debit', 25.50, 'credit', 0),
        pg_catalog.jsonb_build_object('account_id', current_setting('test.dp_income'), 'description', 'Income recognized', 'debit', 0, 'credit', 25.50)
    )
) AS proposal_valid \gset
SELECT set_config('test.dp_proposal_valid', :'proposal_valid', false);
SELECT public.confirm_draft_proposal(:'proposal_valid'::uuid) AS entry_valid \gset
SELECT set_config('test.dp_entry_valid', :'entry_valid', false);
DO $$
BEGIN
    IF (SELECT status FROM public.journal_entries WHERE id = current_setting('test.dp_entry_valid')::uuid) <> 'draft' THEN
        RAISE EXCEPTION 'confirmation did not create a draft';
    END IF;
    IF (SELECT count(*) FROM public.journal_entry_lines WHERE journal_entry_id = current_setting('test.dp_entry_valid')::uuid) <> 2 THEN
        RAISE EXCEPTION 'confirmation did not insert all lines';
    END IF;
    IF (SELECT sum(debit) FROM public.journal_entry_lines WHERE journal_entry_id = current_setting('test.dp_entry_valid')::uuid) <> 25.50
       OR (SELECT sum(credit) FROM public.journal_entry_lines WHERE journal_entry_id = current_setting('test.dp_entry_valid')::uuid) <> 25.50 THEN
        RAISE EXCEPTION 'confirmed journal amounts are not balanced';
    END IF;
    IF EXISTS (
        SELECT 1 FROM public.audit_events
         WHERE entity_type IN ('journal_entries', 'journal_entry_lines')
           AND entity_id IN (
             current_setting('test.dp_entry_valid')::uuid,
             (SELECT id FROM public.journal_entry_lines WHERE journal_entry_id = current_setting('test.dp_entry_valid')::uuid LIMIT 1)
           )
           AND actor_id <> '00000000-0000-0000-0000-000000000001'
    ) THEN RAISE EXCEPTION 'entry or line audit event has the wrong actor'; END IF;
    IF (SELECT count(*) FROM public.audit_events WHERE entity_type = 'journal_entries' AND entity_id = current_setting('test.dp_entry_valid')::uuid AND actor_id = '00000000-0000-0000-0000-000000000001') <> 1 THEN
        RAISE EXCEPTION 'entry audit event missing';
    END IF;
    IF (SELECT count(*) FROM public.audit_events WHERE entity_type = 'journal_entry_lines' AND actor_id = '00000000-0000-0000-0000-000000000001' AND entity_id IN (SELECT id FROM public.journal_entry_lines WHERE journal_entry_id = current_setting('test.dp_entry_valid')::uuid)) <> 2 THEN
        RAISE EXCEPTION 'line audit events missing or duplicated';
    END IF;
END $$;
SELECT public.confirm_draft_proposal(:'proposal_valid'::uuid) AS replayed_entry \gset
SELECT set_config('test.dp_replayed_entry', :'replayed_entry', false);
DO $$
BEGIN
    IF current_setting('test.dp_entry_valid')::uuid <> current_setting('test.dp_replayed_entry')::uuid THEN
        RAISE EXCEPTION 'same proposal retry did not return its original entry';
    END IF;
    IF (SELECT count(*) FROM public.journal_entries WHERE idempotency_key = 'proposal:' || current_setting('test.dp_proposal_valid')) <> 1 THEN
        RAISE EXCEPTION 'proposal retry created a duplicate entry';
    END IF;
END $$;

-- Role and tenant boundaries: viewer, foreign-book member, and a former editor
-- cannot create/confirm, and proposal ownership cannot be changed by the caller.
SET request.jwt.claim.sub = '00000000-0000-0000-0000-000000000003';
DO $$
BEGIN
    BEGIN
        PERFORM public.create_draft_proposal(current_setting('test.dp_book')::uuid, current_setting('test.dp_period')::uuid, '2026-10-13', 'Denied viewer',
            pg_catalog.jsonb_build_array(
                pg_catalog.jsonb_build_object('account_id', current_setting('test.dp_cash'), 'description', NULL, 'debit', 1, 'credit', 0),
                pg_catalog.jsonb_build_object('account_id', current_setting('test.dp_income'), 'description', NULL, 'debit', 0, 'credit', 1)));
        RAISE EXCEPTION 'viewer unexpectedly created proposal';
    EXCEPTION WHEN SQLSTATE '42501' THEN NULL; END;
    BEGIN
        PERFORM public.confirm_draft_proposal(current_setting('test.dp_proposal_valid')::uuid);
        RAISE EXCEPTION 'viewer unexpectedly confirmed another user proposal';
    EXCEPTION WHEN SQLSTATE '42501' THEN NULL; END;
END $$;
SET request.jwt.claim.sub = '00000000-0000-0000-0000-000000000002';
DO $$
BEGIN
    BEGIN
        PERFORM public.create_draft_proposal(current_setting('test.dp_book')::uuid, current_setting('test.dp_period')::uuid, '2026-10-13', 'Foreign book denied',
            pg_catalog.jsonb_build_array(
                pg_catalog.jsonb_build_object('account_id', current_setting('test.dp_cash'), 'description', NULL, 'debit', 1, 'credit', 0),
                pg_catalog.jsonb_build_object('account_id', current_setting('test.dp_income'), 'description', NULL, 'debit', 0, 'credit', 1)));
        RAISE EXCEPTION 'foreign book owner unexpectedly created proposal';
    EXCEPTION WHEN SQLSTATE '42501' THEN NULL; END;
    BEGIN
        PERFORM public.confirm_draft_proposal(current_setting('test.dp_proposal_valid')::uuid);
        RAISE EXCEPTION 'foreign user unexpectedly confirmed proposal';
    EXCEPTION WHEN SQLSTATE '42501' THEN NULL; END;
END $$;
SET request.jwt.claim.sub = '00000000-0000-0000-0000-000000000004';
SELECT public.create_draft_proposal(
    current_setting('test.dp_book')::uuid, current_setting('test.dp_period')::uuid, '2026-10-13', 'Revoked editor',
    pg_catalog.jsonb_build_array(
        pg_catalog.jsonb_build_object('account_id', current_setting('test.dp_cash'), 'description', NULL, 'debit', 1, 'credit', 0),
        pg_catalog.jsonb_build_object('account_id', current_setting('test.dp_income'), 'description', NULL, 'debit', 0, 'credit', 1)
    )
) AS proposal_revoked \gset
SELECT set_config('test.dp_proposal_revoked', :'proposal_revoked', false);
RESET ROLE;
DELETE FROM public.book_memberships WHERE book_id = current_setting('test.dp_book')::uuid AND user_id = '00000000-0000-0000-0000-000000000004';
SET ROLE authenticated;
SET request.jwt.claim.sub = '00000000-0000-0000-0000-000000000004';
DO $$
BEGIN
    BEGIN
        PERFORM public.confirm_draft_proposal(current_setting('test.dp_proposal_revoked')::uuid);
        RAISE EXCEPTION 'revoked editor unexpectedly confirmed proposal';
    EXCEPTION WHEN SQLSTATE '42501' THEN NULL; END;
END $$;
RESET ROLE;

-- Invalid money, exact-one-side, balance, line count, malformed identity, and
-- strict object shape are rejected before any proposal can be persisted.
SET ROLE authenticated;
SET request.jwt.claim.sub = '00000000-0000-0000-0000-000000000001';
DO $$
DECLARE
    v_lines jsonb;
    v_error_expected boolean;
BEGIN
    v_lines := pg_catalog.jsonb_build_array(
        pg_catalog.jsonb_build_object('account_id', current_setting('test.dp_cash'), 'description', NULL, 'debit', 1.001, 'credit', 0),
        pg_catalog.jsonb_build_object('account_id', current_setting('test.dp_income'), 'description', NULL, 'debit', 0, 'credit', 1.001));
    BEGIN PERFORM public.create_draft_proposal(current_setting('test.dp_book')::uuid, current_setting('test.dp_period')::uuid, '2026-10-13', 'Fractional cent', v_lines); RAISE EXCEPTION 'fractional cent accepted'; EXCEPTION WHEN SQLSTATE '22023' THEN NULL; END;
    v_lines := pg_catalog.jsonb_build_array(
        pg_catalog.jsonb_build_object('account_id', current_setting('test.dp_cash'), 'description', NULL, 'debit', -1, 'credit', 0),
        pg_catalog.jsonb_build_object('account_id', current_setting('test.dp_income'), 'description', NULL, 'debit', 0, 'credit', 1));
    BEGIN PERFORM public.create_draft_proposal(current_setting('test.dp_book')::uuid, current_setting('test.dp_period')::uuid, '2026-10-13', 'Negative amount', v_lines); RAISE EXCEPTION 'negative amount accepted'; EXCEPTION WHEN SQLSTATE '22023' THEN NULL; END;
    v_lines := pg_catalog.jsonb_build_array(
        pg_catalog.jsonb_build_object('account_id', current_setting('test.dp_cash'), 'description', NULL, 'debit', 1, 'credit', 1),
        pg_catalog.jsonb_build_object('account_id', current_setting('test.dp_income'), 'description', NULL, 'debit', 0, 'credit', 1));
    BEGIN PERFORM public.create_draft_proposal(current_setting('test.dp_book')::uuid, current_setting('test.dp_period')::uuid, '2026-10-13', 'Both sides', v_lines); RAISE EXCEPTION 'both sides accepted'; EXCEPTION WHEN SQLSTATE '22023' THEN NULL; END;
    v_lines := pg_catalog.jsonb_build_array(
        pg_catalog.jsonb_build_object('account_id', current_setting('test.dp_cash'), 'description', NULL, 'debit', 2, 'credit', 0),
        pg_catalog.jsonb_build_object('account_id', current_setting('test.dp_income'), 'description', NULL, 'debit', 0, 'credit', 1));
    BEGIN PERFORM public.create_draft_proposal(current_setting('test.dp_book')::uuid, current_setting('test.dp_period')::uuid, '2026-10-13', 'Unbalanced', v_lines); RAISE EXCEPTION 'unbalanced lines accepted'; EXCEPTION WHEN SQLSTATE '22023' THEN NULL; END;
    v_lines := pg_catalog.jsonb_build_array(pg_catalog.jsonb_build_object('account_id', current_setting('test.dp_cash'), 'description', NULL, 'debit', 1, 'credit', 0));
    BEGIN PERFORM public.create_draft_proposal(current_setting('test.dp_book')::uuid, current_setting('test.dp_period')::uuid, '2026-10-13', 'One line', v_lines); RAISE EXCEPTION 'one line accepted'; EXCEPTION WHEN SQLSTATE '22023' THEN NULL; END;
    v_lines := pg_catalog.jsonb_build_array(
        pg_catalog.jsonb_build_object('account_id', 'not-a-uuid', 'description', NULL, 'debit', 1, 'credit', 0),
        pg_catalog.jsonb_build_object('account_id', current_setting('test.dp_income'), 'description', NULL, 'debit', 0, 'credit', 1));
    BEGIN PERFORM public.create_draft_proposal(current_setting('test.dp_book')::uuid, current_setting('test.dp_period')::uuid, '2026-10-13', 'Malformed account', v_lines); RAISE EXCEPTION 'malformed account accepted'; EXCEPTION WHEN SQLSTATE '22023' THEN NULL; END;
    v_lines := pg_catalog.jsonb_build_array(
        pg_catalog.jsonb_build_object('account_id', current_setting('test.dp_cash'), 'description', NULL, 'debit', 1, 'credit', 0, 'status', 'posted'),
        pg_catalog.jsonb_build_object('account_id', current_setting('test.dp_income'), 'description', NULL, 'debit', 0, 'credit', 1));
    BEGIN PERFORM public.create_draft_proposal(current_setting('test.dp_book')::uuid, current_setting('test.dp_period')::uuid, '2026-10-13', 'Extra line field', v_lines); RAISE EXCEPTION 'extra payload field accepted'; EXCEPTION WHEN SQLSTATE '22023' THEN NULL; END;
    v_lines := pg_catalog.jsonb_build_array(
        pg_catalog.jsonb_build_object('account_id', current_setting('test.dp_cash'), 'description', NULL, 'debit', 1, 'credit', 0),
        pg_catalog.jsonb_build_object('account_id', current_setting('test.dp_income'), 'description', NULL, 'debit', 0, 'credit', 1));
    FOR i IN 3..51 LOOP v_lines := v_lines || pg_catalog.jsonb_build_array(pg_catalog.jsonb_build_object('account_id', current_setting('test.dp_cash'), 'description', NULL, 'debit', 1, 'credit', 0)); END LOOP;
    BEGIN PERFORM public.create_draft_proposal(current_setting('test.dp_book')::uuid, current_setting('test.dp_period')::uuid, '2026-10-13', 'Too many lines', v_lines); RAISE EXCEPTION 'more than 50 lines accepted'; EXCEPTION WHEN SQLSTATE '22023' THEN NULL; END;
END $$;

-- A stored snapshot cannot be modified/deleted; hash checking also rejects a
-- deliberately corrupted snapshot even if a privileged test disables its guard.
SELECT public.create_draft_proposal(
    current_setting('test.dp_book')::uuid, current_setting('test.dp_period')::uuid, '2026-10-13', 'Tamper test',
    pg_catalog.jsonb_build_array(
        pg_catalog.jsonb_build_object('account_id', current_setting('test.dp_cash'), 'description', NULL, 'debit', 1, 'credit', 0),
        pg_catalog.jsonb_build_object('account_id', current_setting('test.dp_income'), 'description', NULL, 'debit', 0, 'credit', 1)
    )
) AS proposal_tamper \gset
SELECT set_config('test.dp_proposal_tamper', :'proposal_tamper', false);
RESET ROLE;
DO $$
BEGIN
    BEGIN UPDATE public.draft_proposals SET payload = payload || '{"description":"altered"}'::jsonb WHERE id = current_setting('test.dp_proposal_tamper')::uuid; RAISE EXCEPTION 'snapshot changed'; EXCEPTION WHEN SQLSTATE '55000' THEN NULL; END;
    BEGIN DELETE FROM public.draft_proposals WHERE id = current_setting('test.dp_proposal_tamper')::uuid; RAISE EXCEPTION 'proposal deleted'; EXCEPTION WHEN SQLSTATE '55000' THEN NULL; END;
END $$;
ALTER TABLE public.draft_proposals DISABLE TRIGGER draft_proposals_immutable;
UPDATE public.draft_proposals SET payload = payload || '{"description":"tampered"}'::jsonb WHERE id = current_setting('test.dp_proposal_tamper')::uuid;
ALTER TABLE public.draft_proposals ENABLE TRIGGER draft_proposals_immutable;
SET ROLE authenticated;
SET request.jwt.claim.sub = '00000000-0000-0000-0000-000000000001';
DO $$
BEGIN
    BEGIN PERFORM public.confirm_draft_proposal(current_setting('test.dp_proposal_tamper')::uuid); RAISE EXCEPTION 'tampered payload confirmed'; EXCEPTION WHEN SQLSTATE '55000' THEN NULL; END;
END $$;
RESET ROLE;

-- Confirmation rechecks date/period and account state at the transaction edge.
SET ROLE authenticated;
SET request.jwt.claim.sub = '00000000-0000-0000-0000-000000000001';
SELECT public.create_draft_proposal(current_setting('test.dp_book')::uuid, current_setting('test.dp_foreign_period')::uuid, '2026-10-13', 'Foreign period',
    pg_catalog.jsonb_build_array(pg_catalog.jsonb_build_object('account_id', current_setting('test.dp_cash'), 'description', NULL, 'debit', 1, 'credit', 0), pg_catalog.jsonb_build_object('account_id', current_setting('test.dp_income'), 'description', NULL, 'debit', 0, 'credit', 1))) AS proposal_foreign_period \gset
SELECT set_config('test.dp_proposal_foreign_period', :'proposal_foreign_period', false);
SELECT public.create_draft_proposal(current_setting('test.dp_book')::uuid, current_setting('test.dp_closed_period')::uuid, '2026-10-13', 'Closed period',
    pg_catalog.jsonb_build_array(pg_catalog.jsonb_build_object('account_id', current_setting('test.dp_cash'), 'description', NULL, 'debit', 1, 'credit', 0), pg_catalog.jsonb_build_object('account_id', current_setting('test.dp_income'), 'description', NULL, 'debit', 0, 'credit', 1))) AS proposal_closed \gset
SELECT set_config('test.dp_proposal_closed', :'proposal_closed', false);
SELECT public.create_draft_proposal(current_setting('test.dp_book')::uuid, current_setting('test.dp_period')::uuid, '2025-12-31', 'Wrong date',
    pg_catalog.jsonb_build_array(pg_catalog.jsonb_build_object('account_id', current_setting('test.dp_cash'), 'description', NULL, 'debit', 1, 'credit', 0), pg_catalog.jsonb_build_object('account_id', current_setting('test.dp_income'), 'description', NULL, 'debit', 0, 'credit', 1))) AS proposal_wrong_date \gset
SELECT set_config('test.dp_proposal_wrong_date', :'proposal_wrong_date', false);
SELECT public.create_draft_proposal(current_setting('test.dp_book')::uuid, current_setting('test.dp_period')::uuid, '2026-10-13', 'Inactive account',
    pg_catalog.jsonb_build_array(pg_catalog.jsonb_build_object('account_id', current_setting('test.dp_cash'), 'description', NULL, 'debit', 1, 'credit', 0), pg_catalog.jsonb_build_object('account_id', current_setting('test.dp_income'), 'description', NULL, 'debit', 0, 'credit', 1))) AS proposal_inactive \gset
SELECT set_config('test.dp_proposal_inactive', :'proposal_inactive', false);
SELECT public.create_draft_proposal(current_setting('test.dp_book')::uuid, current_setting('test.dp_period')::uuid, '2026-10-13', 'Foreign account',
    pg_catalog.jsonb_build_array(pg_catalog.jsonb_build_object('account_id', current_setting('test.dp_foreign_account'), 'description', NULL, 'debit', 1, 'credit', 0), pg_catalog.jsonb_build_object('account_id', current_setting('test.dp_income'), 'description', NULL, 'debit', 0, 'credit', 1))) AS proposal_foreign_account \gset
SELECT set_config('test.dp_proposal_foreign_account', :'proposal_foreign_account', false);
RESET ROLE;
UPDATE public.accounts SET active = false WHERE id = current_setting('test.dp_income')::uuid;
DO $$
BEGIN
    BEGIN PERFORM public.confirm_draft_proposal(current_setting('test.dp_proposal_foreign_period')::uuid); RAISE EXCEPTION 'foreign period confirmed'; EXCEPTION WHEN SQLSTATE '22023' THEN NULL; END;
    BEGIN PERFORM public.confirm_draft_proposal(current_setting('test.dp_proposal_closed')::uuid); RAISE EXCEPTION 'closed period confirmed'; EXCEPTION WHEN SQLSTATE '22023' THEN NULL; END;
    BEGIN PERFORM public.confirm_draft_proposal(current_setting('test.dp_proposal_wrong_date')::uuid); RAISE EXCEPTION 'wrong period date confirmed'; EXCEPTION WHEN SQLSTATE '22023' THEN NULL; END;
    BEGIN PERFORM public.confirm_draft_proposal(current_setting('test.dp_proposal_inactive')::uuid); RAISE EXCEPTION 'inactive account confirmed'; EXCEPTION WHEN SQLSTATE '22023' THEN NULL; END;
    BEGIN PERFORM public.confirm_draft_proposal(current_setting('test.dp_proposal_foreign_account')::uuid); RAISE EXCEPTION 'foreign account confirmed'; EXCEPTION WHEN SQLSTATE '22023' THEN NULL; END;
END $$;
UPDATE public.accounts SET active = true WHERE id = current_setting('test.dp_income')::uuid;

-- Force a line failure after header and first line insertion. All ledger rows,
-- audit events, and proposal consumption must roll back together.
SET ROLE authenticated;
SET request.jwt.claim.sub = '00000000-0000-0000-0000-000000000001';
SELECT public.create_draft_proposal(current_setting('test.dp_book')::uuid, current_setting('test.dp_period')::uuid, '2026-10-13', 'Atomic rollback',
    pg_catalog.jsonb_build_array(pg_catalog.jsonb_build_object('account_id', current_setting('test.dp_cash'), 'description', NULL, 'debit', 2, 'credit', 0), pg_catalog.jsonb_build_object('account_id', current_setting('test.dp_income'), 'description', NULL, 'debit', 0, 'credit', 2))) AS proposal_rollback \gset
SELECT set_config('test.dp_proposal_rollback', :'proposal_rollback', false);
SELECT set_config('test.dp_audit_before_rollback', (SELECT count(*)::text FROM public.audit_events WHERE book_id = current_setting('test.dp_book')::uuid), false);
RESET ROLE;
CREATE FUNCTION public.test_fail_second_proposal_line()
RETURNS trigger LANGUAGE plpgsql SET search_path = '' AS $function$
BEGIN
    IF NEW.line_number = 2 THEN RAISE EXCEPTION 'intentional test line failure' USING ERRCODE = '23514'; END IF;
    RETURN NEW;
END;
$function$;
CREATE TRIGGER test_fail_second_proposal_line BEFORE INSERT ON public.journal_entry_lines FOR EACH ROW EXECUTE FUNCTION public.test_fail_second_proposal_line();
SET ROLE authenticated;
SET request.jwt.claim.sub = '00000000-0000-0000-0000-000000000001';
DO $$
BEGIN
    BEGIN PERFORM public.confirm_draft_proposal(current_setting('test.dp_proposal_rollback')::uuid); RAISE EXCEPTION 'forced line failure unexpectedly succeeded'; EXCEPTION WHEN check_violation THEN NULL; END;
END $$;
RESET ROLE;
DROP TRIGGER test_fail_second_proposal_line ON public.journal_entry_lines;
DROP FUNCTION public.test_fail_second_proposal_line();
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM public.draft_proposals WHERE id = current_setting('test.dp_proposal_rollback')::uuid AND confirmed_entry_id IS NOT NULL) THEN RAISE EXCEPTION 'failed transaction consumed proposal'; END IF;
    IF EXISTS (SELECT 1 FROM public.journal_entries WHERE idempotency_key = 'proposal:' || current_setting('test.dp_proposal_rollback')) THEN RAISE EXCEPTION 'failed transaction left journal header'; END IF;
    IF (SELECT count(*) FROM public.audit_events WHERE book_id = current_setting('test.dp_book')::uuid) <> current_setting('test.dp_audit_before_rollback')::bigint THEN RAISE EXCEPTION 'failed transaction left audit events'; END IF;
END $$;

-- Expiry is exercised by the test owner adjusting only the timestamp after
-- proposal creation; normal application roles cannot mutate this snapshot.
SET ROLE authenticated;
SET request.jwt.claim.sub = '00000000-0000-0000-0000-000000000001';
SELECT public.create_draft_proposal(current_setting('test.dp_book')::uuid, current_setting('test.dp_period')::uuid, '2026-10-13', 'Expiry test',
    pg_catalog.jsonb_build_array(pg_catalog.jsonb_build_object('account_id', current_setting('test.dp_cash'), 'description', NULL, 'debit', 1, 'credit', 0), pg_catalog.jsonb_build_object('account_id', current_setting('test.dp_income'), 'description', NULL, 'debit', 0, 'credit', 1))) AS proposal_expired \gset
SELECT set_config('test.dp_proposal_expired', :'proposal_expired', false);
RESET ROLE;
ALTER TABLE public.draft_proposals DISABLE TRIGGER draft_proposals_immutable;
UPDATE public.draft_proposals SET expires_at = pg_catalog.clock_timestamp() - interval '1 second' WHERE id = current_setting('test.dp_proposal_expired')::uuid;
ALTER TABLE public.draft_proposals ENABLE TRIGGER draft_proposals_immutable;
SET ROLE authenticated;
SET request.jwt.claim.sub = '00000000-0000-0000-0000-000000000001';
DO $$
BEGIN
    BEGIN PERFORM public.confirm_draft_proposal(current_setting('test.dp_proposal_expired')::uuid); RAISE EXCEPTION 'expired proposal confirmed'; EXCEPTION WHEN SQLSTATE '22023' THEN NULL; END;
END $$;
RESET ROLE;

SELECT 'Draft proposal/confirmation transaction checks passed' AS result;
