\set ON_ERROR_STOP on

BEGIN;
SET LOCAL request.jwt.claim.sub = '00000000-0000-0000-0000-000000000001';

DO $$
DECLARE
    v_book_id uuid;
    v_other_book_id uuid;
    v_parent_id uuid;
    v_child_id uuid;
    v_grandchild_id uuid;
    v_inactive_parent_id uuid;
    v_period_id uuid;
    v_rejected boolean;
BEGIN
    IF NOT has_column_privilege('authenticated', 'public.accounts', 'parent_account_id', 'SELECT')
       OR has_column_privilege('authenticated', 'public.accounts', 'parent_account_id', 'INSERT')
       OR has_column_privilege('authenticated', 'public.accounts', 'parent_account_id', 'UPDATE')
       OR has_column_privilege('anon', 'public.accounts', 'parent_account_id', 'SELECT') THEN
        RAISE EXCEPTION 'new account column grants must preserve authenticated SELECT-only access';
    END IF;
    IF NOT has_column_privilege('authenticated', 'public.journal_entries', 'counterparty', 'SELECT')
       OR has_column_privilege('authenticated', 'public.journal_entries', 'counterparty', 'INSERT')
       OR has_column_privilege('authenticated', 'public.journal_entries', 'counterparty', 'UPDATE')
       OR has_column_privilege('anon', 'public.journal_entries', 'counterparty', 'SELECT') THEN
        RAISE EXCEPTION 'new journal column grants must preserve authenticated SELECT-only access';
    END IF;

    SELECT id INTO STRICT v_book_id FROM public.books WHERE name = 'Tenant One';
    SELECT id INTO STRICT v_other_book_id FROM public.books WHERE name = 'Tenant Two';
    SELECT id INTO STRICT v_parent_id FROM public.accounts WHERE book_id = v_book_id AND code = '1000';
    SELECT id INTO STRICT v_period_id FROM public.accounting_periods WHERE book_id = v_book_id AND name = '2026';

    IF (SELECT parent_account_id FROM public.accounts WHERE id = v_parent_id) IS NOT NULL THEN
        RAISE EXCEPTION 'historical account parent should default to NULL';
    END IF;
    IF (SELECT counterparty FROM public.journal_entries WHERE book_id = v_book_id AND description = 'Seed draft') IS NOT NULL THEN
        RAISE EXCEPTION 'historical journal counterparty should default to NULL';
    END IF;

    INSERT INTO public.accounts (book_id, code, name, account_type, parent_account_id)
    VALUES (v_book_id, '9100', 'Child asset', 'asset', v_parent_id)
    RETURNING id INTO v_child_id;
    INSERT INTO public.accounts (book_id, code, name, account_type, parent_account_id)
    VALUES (v_book_id, '9110', 'Grandchild asset', 'asset', v_child_id)
    RETURNING id INTO v_grandchild_id;

    INSERT INTO public.accounts (book_id, code, name, account_type, active)
    VALUES (v_book_id, '9300', 'Inactive parent', 'asset', false)
    RETURNING id INTO v_inactive_parent_id;

    v_rejected := false;
    BEGIN
        INSERT INTO public.accounts (book_id, code, name, account_type, parent_account_id)
        VALUES (v_book_id, '9200', 'Wrong-type child', 'liability', v_parent_id);
    EXCEPTION WHEN check_violation THEN v_rejected := true;
    END;
    IF NOT v_rejected THEN RAISE EXCEPTION 'same-type parent rule was not enforced'; END IF;

    v_rejected := false;
    BEGIN
        INSERT INTO public.accounts (book_id, code, name, account_type, parent_account_id)
        VALUES (v_book_id, '9201', 'Inactive-parent child', 'asset', v_inactive_parent_id);
    EXCEPTION WHEN check_violation THEN v_rejected := true;
    END;
    IF NOT v_rejected THEN RAISE EXCEPTION 'active-parent rule was not enforced'; END IF;

    v_rejected := false;
    BEGIN
        INSERT INTO public.accounts (book_id, code, name, account_type, parent_account_id)
        VALUES (v_book_id, '9202', 'Cross-book child', 'asset',
                (SELECT id FROM public.accounts WHERE book_id = v_other_book_id AND code = '1000'));
    EXCEPTION WHEN check_violation OR foreign_key_violation THEN v_rejected := true;
    END;
    IF NOT v_rejected THEN RAISE EXCEPTION 'same-book parent rule was not enforced'; END IF;

    v_rejected := false;
    BEGIN
        INSERT INTO public.accounts (id, book_id, code, name, account_type, parent_account_id)
        VALUES ('90000000-0000-4000-8000-000000000001', v_book_id, '9203', 'Self parent', 'asset',
                '90000000-0000-4000-8000-000000000001');
    EXCEPTION WHEN check_violation OR foreign_key_violation THEN v_rejected := true;
    END;
    IF NOT v_rejected THEN RAISE EXCEPTION 'self-parent rule was not enforced'; END IF;

    -- New parent links cannot be assigned/changed after row creation, so existing
    -- children cannot be rearranged into a cycle; parents with children stay active.
    v_rejected := false;
    BEGIN
        UPDATE public.accounts SET parent_account_id = v_grandchild_id WHERE id = v_parent_id;
    EXCEPTION WHEN SQLSTATE '55000' THEN v_rejected := true;
    END;
    IF NOT v_rejected THEN RAISE EXCEPTION 'account hierarchy links were mutable after creation'; END IF;

    v_rejected := false;
    BEGIN
        UPDATE public.accounts SET active = false WHERE id = v_parent_id;
    EXCEPTION WHEN check_violation THEN v_rejected := true;
    END;
    IF NOT v_rejected THEN RAISE EXCEPTION 'parent with children was allowed to become inactive'; END IF;

    INSERT INTO public.journal_entries
        (book_id, period_id, entry_date, description, idempotency_key, counterparty)
    VALUES (v_book_id, v_period_id, '2026-06-01', 'Counterparty valid', 'alignment-counterparty-valid', 'Acme')
    RETURNING id INTO STRICT v_child_id;
    IF (SELECT counterparty FROM public.journal_entries WHERE id = v_child_id) <> 'Acme' THEN
        RAISE EXCEPTION 'valid counterparty did not persist unchanged';
    END IF;

    v_rejected := false;
    BEGIN
        INSERT INTO public.journal_entries
            (book_id, period_id, entry_date, description, idempotency_key, counterparty)
        VALUES (v_book_id, v_period_id, '2026-06-01', 'Counterparty blank', 'alignment-counterparty-blank', '   ');
    EXCEPTION WHEN check_violation THEN v_rejected := true;
    END;
    IF NOT v_rejected THEN RAISE EXCEPTION 'blank counterparty was accepted instead of NULL'; END IF;

    v_rejected := false;
    BEGIN
        INSERT INTO public.journal_entries
            (book_id, period_id, entry_date, description, idempotency_key, counterparty)
        VALUES (v_book_id, v_period_id, '2026-06-01', 'Counterparty long', 'alignment-counterparty-long', repeat('x', 81));
    EXCEPTION WHEN check_violation THEN v_rejected := true;
    END;
    IF NOT v_rejected THEN RAISE EXCEPTION 'counterparty longer than 80 characters was accepted'; END IF;

    v_rejected := false;
    BEGIN
        INSERT INTO public.journal_entries
            (book_id, period_id, entry_date, description, idempotency_key, counterparty)
        VALUES (v_book_id, v_period_id, '2026-06-01', 'Counterparty control', 'alignment-counterparty-control', 'Bad' || chr(9) || 'Name');
    EXCEPTION WHEN check_violation THEN v_rejected := true;
    END;
    IF NOT v_rejected THEN RAISE EXCEPTION 'control character in counterparty was accepted'; END IF;

    v_rejected := false;
    BEGIN
        INSERT INTO public.journal_entries
            (book_id, period_id, entry_date, description, idempotency_key, counterparty)
        VALUES (v_book_id, v_period_id, '2026-06-01', 'Counterparty untrimmed', 'alignment-counterparty-untrimmed', ' Acme ');
    EXCEPTION WHEN check_violation THEN v_rejected := true;
    END;
    IF NOT v_rejected THEN RAISE EXCEPTION 'untrimmed counterparty was accepted'; END IF;
END;
$$;

ROLLBACK;

SELECT 'Room v10 account hierarchy and counterparty schema alignment checks passed' AS result;
