-- Book-01 core schema, tenant-isolated by book_id.
-- Posting intentionally remains blocked until a guarded accounting API/RPC exists.

CREATE TABLE public.currencies (
    code text PRIMARY KEY,
    name text NOT NULL,
    decimal_places smallint NOT NULL,
    CONSTRAINT currencies_usd_only CHECK (code = 'USD'),
    CONSTRAINT currencies_usd_precision CHECK (decimal_places = 2)
);

INSERT INTO public.currencies (code, name, decimal_places)
VALUES ('USD', 'US Dollar', 2);

CREATE TABLE public.books (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name text NOT NULL CHECK (length(btrim(name)) > 0),
    created_by uuid NOT NULL DEFAULT auth.uid() REFERENCES auth.users(id),
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE public.book_memberships (
    book_id uuid NOT NULL REFERENCES public.books(id) ON DELETE CASCADE,
    user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE RESTRICT,
    role text NOT NULL CHECK (role IN ('owner', 'admin', 'editor', 'viewer')),
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (book_id, user_id)
);

CREATE TABLE public.accounts (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    book_id uuid NOT NULL REFERENCES public.books(id) ON DELETE CASCADE,
    code text NOT NULL CHECK (length(btrim(code)) > 0),
    name text NOT NULL CHECK (length(btrim(name)) > 0),
    account_type text NOT NULL CHECK (account_type IN ('asset', 'liability', 'equity', 'income', 'expense')),
    currency_code text NOT NULL DEFAULT 'USD' REFERENCES public.currencies(code),
    active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT accounts_book_id_id_unique UNIQUE (book_id, id),
    CONSTRAINT accounts_book_code_unique UNIQUE (book_id, code),
    CONSTRAINT accounts_usd_only CHECK (currency_code = 'USD')
);

CREATE TABLE public.accounting_periods (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    book_id uuid NOT NULL REFERENCES public.books(id) ON DELETE CASCADE,
    name text NOT NULL CHECK (length(btrim(name)) > 0),
    start_date date NOT NULL,
    end_date date NOT NULL,
    is_open boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT accounting_periods_valid_range CHECK (start_date <= end_date),
    CONSTRAINT accounting_periods_book_id_id_unique UNIQUE (book_id, id)
);

CREATE TABLE public.journal_entries (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    book_id uuid NOT NULL REFERENCES public.books(id) ON DELETE CASCADE,
    period_id uuid NOT NULL,
    entry_date date NOT NULL,
    description text NOT NULL CHECK (length(btrim(description)) > 0),
    idempotency_key text NOT NULL CHECK (length(idempotency_key) BETWEEN 1 AND 200),
    currency_code text NOT NULL DEFAULT 'USD' REFERENCES public.currencies(code),
    status text NOT NULL DEFAULT 'draft' CHECK (status IN ('draft', 'posted', 'void')),
    created_by uuid NOT NULL DEFAULT auth.uid() REFERENCES auth.users(id),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT journal_entries_book_id_id_unique UNIQUE (book_id, id),
    CONSTRAINT journal_entries_book_idempotency_unique UNIQUE (book_id, idempotency_key),
    CONSTRAINT journal_entries_same_book_period_fk
        FOREIGN KEY (book_id, period_id)
        REFERENCES public.accounting_periods (book_id, id) ON DELETE RESTRICT,
    CONSTRAINT journal_entries_usd_only CHECK (currency_code = 'USD')
);

CREATE TABLE public.journal_entry_lines (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    book_id uuid NOT NULL,
    journal_entry_id uuid NOT NULL,
    account_id uuid NOT NULL,
    line_number integer NOT NULL CHECK (line_number > 0),
    description text,
    debit numeric NOT NULL DEFAULT 0,
    credit numeric NOT NULL DEFAULT 0,
    currency_code text NOT NULL DEFAULT 'USD' REFERENCES public.currencies(code),
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT journal_entry_lines_debit_finite_nonnegative_precision CHECK (
        debit >= 0 AND debit < 'Infinity'::numeric AND debit = trunc(debit, 2)
    ),
    CONSTRAINT journal_entry_lines_credit_finite_nonnegative_precision CHECK (
        credit >= 0 AND credit < 'Infinity'::numeric AND credit = trunc(credit, 2)
    ),
    CONSTRAINT journal_entry_lines_exactly_one_side CHECK (
        (debit > 0 AND credit = 0) OR (credit > 0 AND debit = 0)
    ),
    CONSTRAINT journal_entry_lines_usd_only CHECK (currency_code = 'USD'),
    CONSTRAINT journal_entry_lines_entry_line_unique UNIQUE (book_id, journal_entry_id, line_number),
    CONSTRAINT journal_entry_lines_same_book_entry_fk
        FOREIGN KEY (book_id, journal_entry_id)
        REFERENCES public.journal_entries (book_id, id) ON DELETE RESTRICT,
    CONSTRAINT journal_entry_lines_same_book_account_fk
        FOREIGN KEY (book_id, account_id)
        REFERENCES public.accounts (book_id, id) ON DELETE RESTRICT
);

CREATE TABLE public.audit_events (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    book_id uuid NOT NULL REFERENCES public.books(id) ON DELETE RESTRICT,
    actor_id uuid REFERENCES auth.users(id) ON DELETE SET NULL,
    entity_type text NOT NULL,
    entity_id uuid NOT NULL,
    action text NOT NULL CHECK (action IN ('INSERT', 'UPDATE', 'DELETE')),
    occurred_at timestamptz NOT NULL DEFAULT now(),
    old_values jsonb,
    new_values jsonb
);

-- The helper is SECURITY DEFINER to avoid membership RLS recursion. Its owner is
-- the migration role; fixed search_path and fully qualified objects are required.
CREATE FUNCTION public.has_book_role(p_book_id uuid, p_roles text[] DEFAULT NULL)
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = ''
AS $function$
    SELECT EXISTS (
        SELECT 1
        FROM public.book_memberships AS membership
        WHERE membership.book_id = p_book_id
          AND membership.user_id = auth.uid()
          AND (p_roles IS NULL OR membership.role = ANY (p_roles))
    );
$function$;

REVOKE ALL ON FUNCTION public.has_book_role(uuid, text[]) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.has_book_role(uuid, text[]) TO authenticated;

-- A draft can never be made posted/void through direct table writes. This guard
-- also blocks inserting non-drafts. Posting needs a later narrowly-authorized,
-- fully validated transaction rather than a status update from a client.
CREATE FUNCTION public.guard_journal_entry_write()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $function$
DECLARE
    v_start date;
    v_end date;
BEGIN
    IF TG_OP = 'DELETE' THEN
        IF OLD.status <> 'draft' THEN
            RAISE EXCEPTION 'non-draft journal entries are immutable' USING ERRCODE = '55000';
        END IF;
        RETURN OLD;
    END IF;

    IF NEW.status <> 'draft' THEN
        RAISE EXCEPTION 'journal entries can only be created or kept as draft until guarded posting is implemented' USING ERRCODE = '55000';
    END IF;
    IF TG_OP = 'UPDATE' AND OLD.status <> 'draft' THEN
        RAISE EXCEPTION 'non-draft journal entries are immutable' USING ERRCODE = '55000';
    END IF;

    SELECT period.start_date, period.end_date
      INTO v_start, v_end
      FROM public.accounting_periods AS period
     WHERE period.book_id = NEW.book_id
       AND period.id = NEW.period_id;
    IF NOT FOUND OR NEW.entry_date < v_start OR NEW.entry_date > v_end THEN
        RAISE EXCEPTION 'journal entry date must fall within its accounting period' USING ERRCODE = '23514';
    END IF;
    IF TG_OP = 'UPDATE' THEN
        NEW.updated_at := now();
        RETURN NEW;
    END IF;
    RETURN NEW;
END;
$function$;

REVOKE ALL ON FUNCTION public.guard_journal_entry_write() FROM PUBLIC, anon, authenticated;

CREATE TRIGGER journal_entries_guard_write
BEFORE INSERT OR UPDATE OR DELETE ON public.journal_entries
FOR EACH ROW EXECUTE FUNCTION public.guard_journal_entry_write();

CREATE FUNCTION public.guard_journal_line_write()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $function$
DECLARE
    v_status text;
BEGIN
    IF TG_OP IN ('UPDATE', 'DELETE') THEN
        SELECT entry.status INTO v_status
          FROM public.journal_entries AS entry
         WHERE entry.book_id = OLD.book_id AND entry.id = OLD.journal_entry_id;
        IF FOUND AND v_status <> 'draft' THEN
            RAISE EXCEPTION 'lines of non-draft journal entries are immutable' USING ERRCODE = '55000';
        END IF;
    END IF;
    IF TG_OP IN ('INSERT', 'UPDATE') THEN
        SELECT entry.status INTO v_status
          FROM public.journal_entries AS entry
         WHERE entry.book_id = NEW.book_id AND entry.id = NEW.journal_entry_id;
        IF NOT FOUND OR v_status <> 'draft' THEN
            RAISE EXCEPTION 'journal lines may only be changed while the parent entry is draft' USING ERRCODE = '55000';
        END IF;
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$function$;

REVOKE ALL ON FUNCTION public.guard_journal_line_write() FROM PUBLIC, anon, authenticated;

CREATE TRIGGER journal_entry_lines_guard_write
BEFORE INSERT OR UPDATE OR DELETE ON public.journal_entry_lines
FOR EACH ROW EXECUTE FUNCTION public.guard_journal_line_write();

-- Keep entry dates consistent if a period's date bounds are edited later.
CREATE FUNCTION public.guard_period_date_change()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $function$
BEGIN
    IF EXISTS (
        SELECT 1 FROM public.journal_entries AS entry
         WHERE entry.book_id = NEW.book_id
           AND entry.period_id = NEW.id
           AND (entry.entry_date < NEW.start_date OR entry.entry_date > NEW.end_date)
    ) THEN
        RAISE EXCEPTION 'period bounds cannot exclude existing journal entry dates' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$function$;

REVOKE ALL ON FUNCTION public.guard_period_date_change() FROM PUBLIC, anon, authenticated;

CREATE TRIGGER accounting_periods_guard_dates
BEFORE UPDATE OF start_date, end_date ON public.accounting_periods
FOR EACH ROW EXECUTE FUNCTION public.guard_period_date_change();

-- Audit writes are generated by database triggers and cannot be modified/deleted.
CREATE FUNCTION public.record_audit_event()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $function$
DECLARE
    v_old jsonb;
    v_new jsonb;
    v_book_id uuid;
    v_entity_id uuid;
BEGIN
    IF TG_OP IN ('UPDATE', 'DELETE') THEN
        v_old := to_jsonb(OLD);
    END IF;
    IF TG_OP IN ('INSERT', 'UPDATE') THEN
        v_new := to_jsonb(NEW);
    END IF;

    v_book_id := COALESCE((v_new ->> 'book_id')::uuid, (v_old ->> 'book_id')::uuid);
    IF TG_TABLE_NAME = 'books' THEN
        v_book_id := COALESCE((v_new ->> 'id')::uuid, (v_old ->> 'id')::uuid);
    END IF;
    v_entity_id := COALESCE(
        (v_new ->> 'id')::uuid,
        (v_new ->> 'user_id')::uuid,
        (v_old ->> 'id')::uuid,
        (v_old ->> 'user_id')::uuid
    );

    INSERT INTO public.audit_events (book_id, actor_id, entity_type, entity_id, action, old_values, new_values)
    VALUES (v_book_id, auth.uid(), TG_TABLE_NAME, v_entity_id, TG_OP, v_old, v_new);
    RETURN NULL;
END;
$function$;

REVOKE ALL ON FUNCTION public.record_audit_event() FROM PUBLIC, anon, authenticated;

CREATE FUNCTION public.reject_audit_mutation()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = ''
AS $function$
BEGIN
    RAISE EXCEPTION 'audit events are append-only' USING ERRCODE = '55000';
END;
$function$;

REVOKE ALL ON FUNCTION public.reject_audit_mutation() FROM PUBLIC, anon, authenticated;

CREATE TRIGGER audit_events_no_update_delete
BEFORE UPDATE OR DELETE ON public.audit_events
FOR EACH ROW EXECUTE FUNCTION public.reject_audit_mutation();

CREATE TRIGGER books_audit AFTER INSERT OR UPDATE OR DELETE ON public.books
FOR EACH ROW EXECUTE FUNCTION public.record_audit_event();
CREATE TRIGGER book_memberships_audit AFTER INSERT OR UPDATE OR DELETE ON public.book_memberships
FOR EACH ROW EXECUTE FUNCTION public.record_audit_event();
CREATE TRIGGER accounts_audit AFTER INSERT OR UPDATE OR DELETE ON public.accounts
FOR EACH ROW EXECUTE FUNCTION public.record_audit_event();
CREATE TRIGGER accounting_periods_audit AFTER INSERT OR UPDATE OR DELETE ON public.accounting_periods
FOR EACH ROW EXECUTE FUNCTION public.record_audit_event();
CREATE TRIGGER journal_entries_audit AFTER INSERT OR UPDATE OR DELETE ON public.journal_entries
FOR EACH ROW EXECUTE FUNCTION public.record_audit_event();
CREATE TRIGGER journal_entry_lines_audit AFTER INSERT OR UPDATE OR DELETE ON public.journal_entry_lines
FOR EACH ROW EXECUTE FUNCTION public.record_audit_event();

ALTER TABLE public.books ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.book_memberships ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.accounts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.accounting_periods ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.journal_entries ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.journal_entry_lines ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.currencies ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.audit_events ENABLE ROW LEVEL SECURITY;

CREATE POLICY books_select_member ON public.books FOR SELECT TO authenticated
    USING (public.has_book_role(id) OR created_by = auth.uid());
CREATE POLICY books_insert_self ON public.books FOR INSERT TO authenticated
    WITH CHECK (created_by = auth.uid());
CREATE POLICY books_update_admin ON public.books FOR UPDATE TO authenticated
    USING (public.has_book_role(id, ARRAY['owner', 'admin']))
    WITH CHECK (public.has_book_role(id, ARRAY['owner', 'admin']));

CREATE POLICY memberships_select_member ON public.book_memberships FOR SELECT TO authenticated
    USING (public.has_book_role(book_id));
CREATE POLICY memberships_insert_admin_or_initial_owner ON public.book_memberships FOR INSERT TO authenticated
    WITH CHECK (
        (role <> 'owner' AND public.has_book_role(book_id, ARRAY['owner', 'admin']))
        OR (
            role = 'owner' AND user_id = auth.uid()
            AND EXISTS (SELECT 1 FROM public.books AS book WHERE book.id = book_id AND book.created_by = auth.uid())
        )
    );
CREATE POLICY memberships_update_non_owner ON public.book_memberships FOR UPDATE TO authenticated
    USING (role <> 'owner' AND public.has_book_role(book_id, ARRAY['owner', 'admin']))
    WITH CHECK (role <> 'owner' AND public.has_book_role(book_id, ARRAY['owner', 'admin']));
CREATE POLICY memberships_delete_non_owner ON public.book_memberships FOR DELETE TO authenticated
    USING (role <> 'owner' AND public.has_book_role(book_id, ARRAY['owner', 'admin']));

CREATE POLICY accounts_select_member ON public.accounts FOR SELECT TO authenticated
    USING (public.has_book_role(book_id));
CREATE POLICY accounts_insert_editor ON public.accounts FOR INSERT TO authenticated
    WITH CHECK (public.has_book_role(book_id, ARRAY['owner', 'admin', 'editor']));
CREATE POLICY accounts_update_editor ON public.accounts FOR UPDATE TO authenticated
    USING (public.has_book_role(book_id, ARRAY['owner', 'admin', 'editor']))
    WITH CHECK (public.has_book_role(book_id, ARRAY['owner', 'admin', 'editor']));

CREATE POLICY periods_select_member ON public.accounting_periods FOR SELECT TO authenticated
    USING (public.has_book_role(book_id));
CREATE POLICY periods_insert_admin ON public.accounting_periods FOR INSERT TO authenticated
    WITH CHECK (public.has_book_role(book_id, ARRAY['owner', 'admin']));
CREATE POLICY periods_update_admin ON public.accounting_periods FOR UPDATE TO authenticated
    USING (public.has_book_role(book_id, ARRAY['owner', 'admin']))
    WITH CHECK (public.has_book_role(book_id, ARRAY['owner', 'admin']));

CREATE POLICY entries_select_member ON public.journal_entries FOR SELECT TO authenticated
    USING (public.has_book_role(book_id));
CREATE POLICY entries_insert_editor_draft ON public.journal_entries FOR INSERT TO authenticated
    WITH CHECK (status = 'draft' AND public.has_book_role(book_id, ARRAY['owner', 'admin', 'editor']));
CREATE POLICY entries_update_editor_draft ON public.journal_entries FOR UPDATE TO authenticated
    USING (status = 'draft' AND public.has_book_role(book_id, ARRAY['owner', 'admin', 'editor']))
    WITH CHECK (status = 'draft' AND public.has_book_role(book_id, ARRAY['owner', 'admin', 'editor']));

CREATE POLICY lines_select_member ON public.journal_entry_lines FOR SELECT TO authenticated
    USING (public.has_book_role(book_id));
CREATE POLICY lines_insert_editor_draft ON public.journal_entry_lines FOR INSERT TO authenticated
    WITH CHECK (
        public.has_book_role(book_id, ARRAY['owner', 'admin', 'editor'])
        AND EXISTS (
            SELECT 1 FROM public.journal_entries AS entry
             WHERE entry.book_id = journal_entry_lines.book_id
               AND entry.id = journal_entry_id AND entry.status = 'draft'
        )
        AND EXISTS (
            SELECT 1 FROM public.accounts AS account
             WHERE account.book_id = journal_entry_lines.book_id
               AND account.id = account_id AND account.active
        )
    );
CREATE POLICY lines_update_editor_draft ON public.journal_entry_lines FOR UPDATE TO authenticated
    USING (
        public.has_book_role(book_id, ARRAY['owner', 'admin', 'editor'])
        AND EXISTS (
            SELECT 1 FROM public.journal_entries AS entry
             WHERE entry.book_id = journal_entry_lines.book_id
               AND entry.id = journal_entry_id AND entry.status = 'draft'
        )
    )
    WITH CHECK (
        public.has_book_role(book_id, ARRAY['owner', 'admin', 'editor'])
        AND EXISTS (
            SELECT 1 FROM public.journal_entries AS entry
             WHERE entry.book_id = journal_entry_lines.book_id
               AND entry.id = journal_entry_id AND entry.status = 'draft'
        )
        AND EXISTS (
            SELECT 1 FROM public.accounts AS account
             WHERE account.book_id = journal_entry_lines.book_id
               AND account.id = account_id AND account.active
        )
    );
CREATE POLICY lines_delete_editor_draft ON public.journal_entry_lines FOR DELETE TO authenticated
    USING (
        public.has_book_role(book_id, ARRAY['owner', 'admin', 'editor'])
        AND EXISTS (
            SELECT 1 FROM public.journal_entries AS entry
             WHERE entry.book_id = journal_entry_lines.book_id
               AND entry.id = journal_entry_id AND entry.status = 'draft'
        )
    );

CREATE POLICY currencies_select_authenticated ON public.currencies FOR SELECT TO authenticated USING (true);
CREATE POLICY audit_select_member ON public.audit_events FOR SELECT TO authenticated
    USING (public.has_book_role(book_id));

REVOKE ALL ON public.books, public.book_memberships, public.accounts,
    public.accounting_periods, public.journal_entries, public.journal_entry_lines,
    public.currencies, public.audit_events FROM PUBLIC, anon, authenticated;

GRANT SELECT ON public.books, public.book_memberships, public.accounts,
    public.accounting_periods, public.journal_entries, public.journal_entry_lines,
    public.currencies, public.audit_events TO authenticated;
GRANT INSERT (name, created_by) ON public.books TO authenticated;
GRANT UPDATE (name) ON public.books TO authenticated;
GRANT INSERT (book_id, user_id, role) ON public.book_memberships TO authenticated;
GRANT UPDATE (role), DELETE ON public.book_memberships TO authenticated;
GRANT INSERT (book_id, code, name, account_type, currency_code, active) ON public.accounts TO authenticated;
GRANT UPDATE (name, account_type, active) ON public.accounts TO authenticated;
GRANT INSERT (book_id, name, start_date, end_date, is_open) ON public.accounting_periods TO authenticated;
GRANT UPDATE (name, start_date, end_date, is_open) ON public.accounting_periods TO authenticated;
GRANT INSERT (book_id, period_id, entry_date, description, idempotency_key, currency_code) ON public.journal_entries TO authenticated;
GRANT UPDATE (period_id, entry_date, description) ON public.journal_entries TO authenticated;
GRANT INSERT (book_id, journal_entry_id, account_id, line_number, description, debit, credit, currency_code) ON public.journal_entry_lines TO authenticated;
GRANT UPDATE (journal_entry_id, account_id, line_number, description, debit, credit, currency_code) ON public.journal_entry_lines TO authenticated;
GRANT DELETE ON public.journal_entry_lines TO authenticated;

COMMENT ON TABLE public.currencies IS 'Initial support is USD only; monetary values use exact numeric with two-decimal validation.';
COMMENT ON TABLE public.audit_events IS 'Append-only events generated by database triggers; clients cannot insert, update, or delete events.';
COMMENT ON TABLE public.journal_entries IS 'Only draft operations are available. Posting is intentionally blocked pending a guarded transactional API/RPC.';
