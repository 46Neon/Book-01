-- Proposal-backed, explicit draft confirmation. This is a database transaction
-- primitive only; no API/UI is enabled by this migration.
-- Provisional policy pending owner review: owner/admin/editor may propose;
-- maximum 50 lines; proposals expire after 10 minutes.

CREATE TABLE public.draft_proposals (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE RESTRICT,
    book_id uuid NOT NULL REFERENCES public.books(id) ON DELETE CASCADE,
    payload jsonb NOT NULL,
    payload_sha256 text NOT NULL CHECK (payload_sha256 ~ '^[0-9a-f]{64}$'),
    created_at timestamptz NOT NULL DEFAULT now(),
    expires_at timestamptz NOT NULL,
    confirmed_entry_id uuid,
    confirmed_at timestamptz,
    CONSTRAINT draft_proposals_expiry_after_creation CHECK (expires_at > created_at),
    CONSTRAINT draft_proposals_confirmation_pair CHECK (
        (confirmed_entry_id IS NULL AND confirmed_at IS NULL)
        OR (confirmed_entry_id IS NOT NULL AND confirmed_at IS NOT NULL)
    ),
    CONSTRAINT draft_proposals_book_id_id_unique UNIQUE (book_id, id),
    CONSTRAINT draft_proposals_result_same_book_fk
        FOREIGN KEY (book_id, confirmed_entry_id)
        REFERENCES public.journal_entries (book_id, id) ON DELETE RESTRICT
);

ALTER TABLE public.draft_proposals ENABLE ROW LEVEL SECURITY;
REVOKE ALL PRIVILEGES ON TABLE public.draft_proposals FROM PUBLIC, anon, authenticated;

CREATE FUNCTION public.guard_draft_proposal_mutation()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = ''
AS $function$
BEGIN
    IF TG_OP <> 'UPDATE' THEN
        RAISE EXCEPTION 'draft proposals are immutable and cannot be deleted' USING ERRCODE = '55000';
    END IF;
    IF NEW.id IS DISTINCT FROM OLD.id
       OR NEW.user_id IS DISTINCT FROM OLD.user_id
       OR NEW.book_id IS DISTINCT FROM OLD.book_id
       OR NEW.payload IS DISTINCT FROM OLD.payload
       OR NEW.payload_sha256 IS DISTINCT FROM OLD.payload_sha256
       OR NEW.created_at IS DISTINCT FROM OLD.created_at
       OR NEW.expires_at IS DISTINCT FROM OLD.expires_at
       OR OLD.confirmed_entry_id IS NOT NULL
       OR OLD.confirmed_at IS NOT NULL
       OR NEW.confirmed_entry_id IS NULL
       OR NEW.confirmed_at IS NULL THEN
        RAISE EXCEPTION 'draft proposal snapshot is immutable' USING ERRCODE = '55000';
    END IF;
    RETURN NEW;
END;
$function$;
REVOKE ALL ON FUNCTION public.guard_draft_proposal_mutation() FROM PUBLIC, anon, authenticated;
CREATE TRIGGER draft_proposals_immutable
BEFORE UPDATE OR DELETE ON public.draft_proposals
FOR EACH ROW EXECUTE FUNCTION public.guard_draft_proposal_mutation();

-- Only the authenticated caller can create a proposal for a book they can edit.
-- The function canonicalizes line JSON before persisting and hashing it.
CREATE FUNCTION public.create_draft_proposal(
    p_book_id uuid,
    p_period_id uuid,
    p_entry_date date,
    p_description text,
    p_lines jsonb
)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $function$
DECLARE
    v_user_id uuid := auth.uid();
    v_line jsonb;
    v_normalized_lines jsonb := '[]'::jsonb;
    v_account_id uuid;
    v_line_description text;
    v_debit numeric;
    v_credit numeric;
    v_total_debit numeric := 0;
    v_total_credit numeric := 0;
    v_line_count integer := 0;
    v_payload jsonb;
    v_hash text;
    v_proposal_id uuid;
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'authentication is required' USING ERRCODE = '42501';
    END IF;
    IF p_book_id IS NULL OR p_period_id IS NULL OR p_entry_date IS NULL THEN
        RAISE EXCEPTION 'book, period and entry date are required' USING ERRCODE = '22023';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM public.book_memberships AS membership
         WHERE membership.book_id = p_book_id
           AND membership.user_id = v_user_id
           AND membership.role IN ('owner', 'admin', 'editor')
    ) THEN
        RAISE EXCEPTION 'caller is not authorized to propose drafts for this book' USING ERRCODE = '42501';
    END IF;
    IF p_description IS NULL OR length(btrim(p_description)) = 0 THEN
        RAISE EXCEPTION 'description must not be empty' USING ERRCODE = '22023';
    END IF;
    IF p_lines IS NULL OR pg_catalog.jsonb_typeof(p_lines) <> 'array' THEN
        RAISE EXCEPTION 'lines must be a JSON array' USING ERRCODE = '22023';
    END IF;

    FOR v_line IN SELECT value FROM pg_catalog.jsonb_array_elements(p_lines) AS element(value) LOOP
        v_line_count := v_line_count + 1;
        IF v_line_count > 50 THEN
            RAISE EXCEPTION 'a draft proposal may contain at most 50 lines' USING ERRCODE = '22023';
        END IF;
        IF pg_catalog.jsonb_typeof(v_line) <> 'object'
           OR (SELECT count(*) FROM pg_catalog.jsonb_object_keys(v_line)) <> 4
           OR NOT (v_line ?& ARRAY['account_id', 'description', 'debit', 'credit']) THEN
            RAISE EXCEPTION 'each line must contain exactly account_id, description, debit and credit' USING ERRCODE = '22023';
        END IF;
        IF pg_catalog.jsonb_typeof(v_line->'account_id') <> 'string'
           OR pg_catalog.jsonb_typeof(v_line->'debit') <> 'number'
           OR pg_catalog.jsonb_typeof(v_line->'credit') <> 'number'
           OR pg_catalog.jsonb_typeof(v_line->'description') NOT IN ('string', 'null') THEN
            RAISE EXCEPTION 'line fields have invalid JSON types' USING ERRCODE = '22023';
        END IF;
        BEGIN
            v_account_id := (v_line->>'account_id')::uuid;
        EXCEPTION WHEN invalid_text_representation THEN
            RAISE EXCEPTION 'invalid account id in proposal line' USING ERRCODE = '22023';
        END;
        v_line_description := CASE WHEN pg_catalog.jsonb_typeof(v_line->'description') = 'null'
                                   THEN NULL ELSE v_line->>'description' END;
        IF v_line_description IS NOT NULL AND length(v_line_description) > 1000 THEN
            RAISE EXCEPTION 'line description is too long' USING ERRCODE = '22023';
        END IF;
        IF (v_line->>'debit') !~ '^[0-9]+(\.[0-9]{1,2})?$'
           OR (v_line->>'credit') !~ '^[0-9]+(\.[0-9]{1,2})?$' THEN
            RAISE EXCEPTION 'line amounts must be finite nonnegative values with at most two decimals' USING ERRCODE = '22023';
        END IF;
        v_debit := (v_line->>'debit')::numeric;
        v_credit := (v_line->>'credit')::numeric;
        IF NOT ((v_debit > 0 AND v_credit = 0) OR (v_credit > 0 AND v_debit = 0)) THEN
            RAISE EXCEPTION 'each line must have exactly one positive side' USING ERRCODE = '22023';
        END IF;
        v_total_debit := v_total_debit + v_debit;
        v_total_credit := v_total_credit + v_credit;
        v_normalized_lines := v_normalized_lines || pg_catalog.jsonb_build_array(
            pg_catalog.jsonb_build_object(
                'account_id', v_account_id,
                'description', v_line_description,
                'debit', v_debit,
                'credit', v_credit
            )
        );
    END LOOP;
    IF v_line_count < 2 THEN
        RAISE EXCEPTION 'a draft requires at least two lines' USING ERRCODE = '22023';
    END IF;
    IF v_total_debit <> v_total_credit THEN
        RAISE EXCEPTION 'draft proposal debits and credits must balance' USING ERRCODE = '22023';
    END IF;

    v_payload := pg_catalog.jsonb_build_object(
        'book_id', p_book_id,
        'period_id', p_period_id,
        'entry_date', p_entry_date,
        'description', btrim(p_description),
        'currency_code', 'USD',
        'lines', v_normalized_lines
    );
    v_hash := pg_catalog.encode(pg_catalog.sha256(pg_catalog.convert_to(v_payload::text, 'UTF8')), 'hex');
    INSERT INTO public.draft_proposals (user_id, book_id, payload, payload_sha256, expires_at)
    VALUES (v_user_id, p_book_id, v_payload, v_hash, pg_catalog.clock_timestamp() + interval '10 minutes')
    RETURNING id INTO v_proposal_id;
    RETURN v_proposal_id;
END;
$function$;

CREATE FUNCTION public.confirm_draft_proposal(p_proposal_id uuid)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $function$
DECLARE
    v_user_id uuid := auth.uid();
    v_proposal public.draft_proposals%ROWTYPE;
    v_payload jsonb;
    v_hash text;
    v_role text;
    v_period public.accounting_periods%ROWTYPE;
    v_line jsonb;
    v_line_number integer := 0;
    v_account_id uuid;
    v_line_description text;
    v_debit numeric;
    v_credit numeric;
    v_total_debit numeric := 0;
    v_total_credit numeric := 0;
    v_account_active boolean;
    v_account_currency text;
    v_entry_id uuid;
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'authentication is required' USING ERRCODE = '42501';
    END IF;
    SELECT proposal.* INTO v_proposal
      FROM public.draft_proposals AS proposal
     WHERE proposal.id = p_proposal_id
     FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'draft proposal not found' USING ERRCODE = 'P0002';
    END IF;
    IF v_proposal.user_id <> v_user_id THEN
        RAISE EXCEPTION 'draft proposal belongs to a different user' USING ERRCODE = '42501';
    END IF;
    SELECT membership.role INTO v_role
      FROM public.book_memberships AS membership
     WHERE membership.book_id = v_proposal.book_id
       AND membership.user_id = v_user_id
       AND membership.role IN ('owner', 'admin', 'editor')
     FOR SHARE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'caller is no longer authorized to confirm drafts for this book' USING ERRCODE = '42501';
    END IF;

    v_hash := pg_catalog.encode(pg_catalog.sha256(pg_catalog.convert_to(v_proposal.payload::text, 'UTF8')), 'hex');
    IF v_hash <> v_proposal.payload_sha256 THEN
        RAISE EXCEPTION 'draft proposal payload hash does not match immutable snapshot' USING ERRCODE = '55000';
    END IF;
    IF v_proposal.confirmed_entry_id IS NOT NULL THEN
        IF NOT EXISTS (
            SELECT 1 FROM public.journal_entries AS entry
             WHERE entry.id = v_proposal.confirmed_entry_id
               AND entry.book_id = v_proposal.book_id
               AND entry.created_by = v_user_id
               AND entry.status = 'draft'
               AND entry.idempotency_key = 'proposal:' || v_proposal.id::text
        ) THEN
            RAISE EXCEPTION 'confirmed draft result is missing or inconsistent' USING ERRCODE = '55000';
        END IF;
        RETURN v_proposal.confirmed_entry_id;
    END IF;
    IF v_proposal.expires_at <= pg_catalog.clock_timestamp() THEN
        RAISE EXCEPTION 'draft proposal has expired' USING ERRCODE = '22023';
    END IF;

    v_payload := v_proposal.payload;
    IF pg_catalog.jsonb_typeof(v_payload) <> 'object'
       OR v_payload->>'book_id' IS DISTINCT FROM v_proposal.book_id::text
       OR v_payload->>'currency_code' IS DISTINCT FROM 'USD'
       OR v_payload->>'description' IS NULL
       OR length(btrim(v_payload->>'description')) = 0
       OR v_payload->>'period_id' IS NULL
       OR v_payload->>'entry_date' IS NULL
       OR pg_catalog.jsonb_typeof(v_payload->'lines') <> 'array' THEN
        RAISE EXCEPTION 'stored draft proposal payload is invalid' USING ERRCODE = '55000';
    END IF;
    SELECT period.* INTO v_period
      FROM public.accounting_periods AS period
     WHERE period.id = (v_payload->>'period_id')::uuid
       AND period.book_id = v_proposal.book_id
     FOR UPDATE;
    IF NOT FOUND OR NOT v_period.is_open THEN
        RAISE EXCEPTION 'accounting period is missing, foreign, or closed' USING ERRCODE = '22023';
    END IF;
    IF (v_payload->>'entry_date')::date < v_period.start_date
       OR (v_payload->>'entry_date')::date > v_period.end_date THEN
        RAISE EXCEPTION 'entry date must fall within the open accounting period' USING ERRCODE = '22023';
    END IF;

    FOR v_line IN SELECT value FROM pg_catalog.jsonb_array_elements(v_payload->'lines') AS element(value) LOOP
        v_line_number := v_line_number + 1;
        IF v_line_number > 50
           OR pg_catalog.jsonb_typeof(v_line) <> 'object'
           OR (SELECT count(*) FROM pg_catalog.jsonb_object_keys(v_line)) <> 4
           OR NOT (v_line ?& ARRAY['account_id', 'description', 'debit', 'credit'])
           OR pg_catalog.jsonb_typeof(v_line->'account_id') <> 'string'
           OR pg_catalog.jsonb_typeof(v_line->'debit') <> 'number'
           OR pg_catalog.jsonb_typeof(v_line->'credit') <> 'number'
           OR pg_catalog.jsonb_typeof(v_line->'description') NOT IN ('string', 'null')
           OR (v_line->>'debit') !~ '^[0-9]+(\.[0-9]{1,2})?$'
           OR (v_line->>'credit') !~ '^[0-9]+(\.[0-9]{1,2})?$' THEN
            RAISE EXCEPTION 'stored draft proposal line is invalid' USING ERRCODE = '55000';
        END IF;
        BEGIN
            v_account_id := (v_line->>'account_id')::uuid;
        EXCEPTION WHEN invalid_text_representation THEN
            RAISE EXCEPTION 'stored draft proposal has invalid account id' USING ERRCODE = '55000';
        END;
        v_line_description := CASE WHEN pg_catalog.jsonb_typeof(v_line->'description') = 'null'
                                   THEN NULL ELSE v_line->>'description' END;
        v_debit := (v_line->>'debit')::numeric;
        v_credit := (v_line->>'credit')::numeric;
        IF NOT ((v_debit > 0 AND v_credit = 0) OR (v_credit > 0 AND v_debit = 0)) THEN
            RAISE EXCEPTION 'stored proposal line must have exactly one positive side' USING ERRCODE = '55000';
        END IF;
        v_total_debit := v_total_debit + v_debit;
        v_total_credit := v_total_credit + v_credit;
        SELECT account.active, account.currency_code
          INTO v_account_active, v_account_currency
          FROM public.accounts AS account
         WHERE account.id = v_account_id
           AND account.book_id = v_proposal.book_id
         FOR SHARE;
        IF NOT FOUND OR NOT v_account_active OR v_account_currency <> 'USD' THEN
            RAISE EXCEPTION 'account is missing, inactive, foreign, or not USD' USING ERRCODE = '22023';
        END IF;
    END LOOP;
    IF v_line_number < 2 OR v_line_number > 50 OR v_total_debit <> v_total_credit THEN
        RAISE EXCEPTION 'draft proposal must contain 2 to 50 balanced lines' USING ERRCODE = '22023';
    END IF;

    INSERT INTO public.journal_entries
        (book_id, period_id, entry_date, description, idempotency_key, currency_code, status, created_by)
    VALUES (
        v_proposal.book_id,
        v_period.id,
        (v_payload->>'entry_date')::date,
        btrim(v_payload->>'description'),
        'proposal:' || v_proposal.id::text,
        'USD',
        'draft',
        v_user_id
    )
    RETURNING id INTO v_entry_id;

    v_line_number := 0;
    FOR v_line IN SELECT value FROM pg_catalog.jsonb_array_elements(v_payload->'lines') AS element(value) LOOP
        v_line_number := v_line_number + 1;
        v_account_id := (v_line->>'account_id')::uuid;
        v_line_description := CASE WHEN pg_catalog.jsonb_typeof(v_line->'description') = 'null'
                                   THEN NULL ELSE v_line->>'description' END;
        v_debit := (v_line->>'debit')::numeric;
        v_credit := (v_line->>'credit')::numeric;
        INSERT INTO public.journal_entry_lines
            (book_id, journal_entry_id, account_id, line_number, description, debit, credit, currency_code)
        VALUES
            (v_proposal.book_id, v_entry_id, v_account_id, v_line_number, v_line_description, v_debit, v_credit, 'USD');
    END LOOP;

    UPDATE public.draft_proposals
       SET confirmed_entry_id = v_entry_id,
           confirmed_at = pg_catalog.clock_timestamp()
     WHERE id = v_proposal.id;
    RETURN v_entry_id;
END;
$function$;

REVOKE ALL ON FUNCTION public.create_draft_proposal(uuid, uuid, date, text, jsonb) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.confirm_draft_proposal(uuid) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.create_draft_proposal(uuid, uuid, date, text, jsonb) TO authenticated;
GRANT EXECUTE ON FUNCTION public.confirm_draft_proposal(uuid) TO authenticated;

COMMENT ON TABLE public.draft_proposals IS
  'Private immutable snapshots for the database-only 2B draft confirmation primitive; no direct REST access. Provisional policy: 10-minute expiry and 50-line maximum pending owner review.';
COMMENT ON FUNCTION public.create_draft_proposal(uuid, uuid, date, text, jsonb) IS
  'Creates an immutable canonical proposal for owner/admin/editor. Provisional 50-line maximum; expires after 10 minutes.';
COMMENT ON FUNCTION public.confirm_draft_proposal(uuid) IS
  'Atomically confirms an authenticated user-owned proposal as a draft after live membership, period, account, money, and balance checks. Never posts or voids.';
