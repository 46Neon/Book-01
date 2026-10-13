-- Additive alignment with Android Room v10. Historical rows deliberately stay NULL.
-- Account parents are same-book, same-type, active at creation, and immutable;
-- the insert-only edge to an existing account makes hierarchy cycles impossible.
-- Journal counterparties are optional normalized labels (the client normalizes
-- whitespace to NULL/trimmed text); Data API table access remains SELECT-only.

ALTER TABLE public.accounts
    ADD COLUMN parent_account_id uuid,
    ADD CONSTRAINT accounts_parent_not_self
        CHECK (parent_account_id IS NULL OR parent_account_id <> id),
    ADD CONSTRAINT accounts_parent_same_book_fk
        FOREIGN KEY (book_id, parent_account_id)
        REFERENCES public.accounts (book_id, id) ON DELETE RESTRICT;

CREATE INDEX accounts_book_parent_account_idx
    ON public.accounts (book_id, parent_account_id);

ALTER TABLE public.journal_entries
    ADD COLUMN counterparty text,
    ADD CONSTRAINT journal_entries_counterparty_valid CHECK (
        counterparty IS NULL
        OR (
            length(counterparty) BETWEEN 1 AND 80
            AND counterparty = btrim(counterparty)
            AND counterparty !~ '[[:cntrl:]]'
        )
    );

CREATE FUNCTION public.guard_account_parent_write()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = ''
AS $function$
DECLARE
    v_parent_type text;
    v_parent_active boolean;
BEGIN
    IF TG_OP = 'UPDATE' THEN
        IF NEW.parent_account_id IS DISTINCT FROM OLD.parent_account_id THEN
            RAISE EXCEPTION 'account parent is immutable after creation' USING ERRCODE = '55000';
        END IF;
        IF OLD.active AND NOT NEW.active AND EXISTS (
            SELECT 1
              FROM public.accounts AS child
             WHERE child.book_id = OLD.book_id
               AND child.parent_account_id = OLD.id
        ) THEN
            RAISE EXCEPTION 'an account with child accounts must remain active' USING ERRCODE = '23514';
        END IF;
        RETURN NEW;
    END IF;

    IF NEW.parent_account_id IS NOT NULL THEN
        SELECT parent.account_type, parent.active
          INTO v_parent_type, v_parent_active
          FROM public.accounts AS parent
         WHERE parent.book_id = NEW.book_id
           AND parent.id = NEW.parent_account_id;
        IF NOT FOUND OR NOT v_parent_active OR v_parent_type IS DISTINCT FROM NEW.account_type THEN
            RAISE EXCEPTION 'account parent must exist in the same book, be active, and have the same type' USING ERRCODE = '23514';
        END IF;
    END IF;
    RETURN NEW;
END;
$function$;

REVOKE ALL ON FUNCTION public.guard_account_parent_write() FROM PUBLIC, anon, authenticated;

CREATE TRIGGER accounts_guard_parent_write
BEFORE INSERT OR UPDATE OF parent_account_id, active ON public.accounts
FOR EACH ROW EXECUTE FUNCTION public.guard_account_parent_write();

COMMENT ON COLUMN public.accounts.parent_account_id IS
    'Optional same-book account parent. Set only on insert to an active same-type parent; immutable afterward.';
COMMENT ON COLUMN public.journal_entries.counterparty IS
    'Optional normalized counterparty label, at most 80 characters; historical rows remain NULL.';
