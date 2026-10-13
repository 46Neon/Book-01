\set ON_ERROR_STOP on

DO $$
DECLARE
    table_ref text;
    privilege_name text;
    table_refs text[] := ARRAY[
        'public.books',
        'public.book_memberships',
        'public.accounts',
        'public.accounting_periods',
        'public.journal_entries',
        'public.journal_entry_lines',
        'public.currencies',
        'public.audit_events'
    ];
    mutation_privileges text[] := ARRAY[
        'INSERT', 'UPDATE', 'DELETE', 'TRUNCATE', 'REFERENCES', 'TRIGGER'
    ];
BEGIN
    FOREACH table_ref IN ARRAY table_refs LOOP
        IF NOT has_table_privilege('authenticated', table_ref, 'SELECT') THEN
            RAISE EXCEPTION 'authenticated cannot SELECT %', table_ref;
        END IF;
        IF has_table_privilege('anon', table_ref, 'SELECT') THEN
            RAISE EXCEPTION 'anon unexpectedly can SELECT %', table_ref;
        END IF;
        FOREACH privilege_name IN ARRAY mutation_privileges LOOP
            IF has_table_privilege('authenticated', table_ref, privilege_name) THEN
                RAISE EXCEPTION 'authenticated unexpectedly has % on %', privilege_name, table_ref;
            END IF;
            IF has_table_privilege('anon', table_ref, privilege_name) THEN
                RAISE EXCEPTION 'anon unexpectedly has % on %', privilege_name, table_ref;
            END IF;
        END LOOP;
    END LOOP;

    IF has_table_privilege('authenticated', 'public.draft_proposals', 'SELECT')
       OR has_table_privilege('anon', 'public.draft_proposals', 'SELECT') THEN
        RAISE EXCEPTION 'proposal snapshots must not be directly readable through PostgREST';
    END IF;
    FOREACH privilege_name IN ARRAY ARRAY['INSERT', 'UPDATE', 'DELETE', 'TRUNCATE', 'REFERENCES', 'TRIGGER', 'SELECT'] LOOP
        IF has_table_privilege('authenticated', 'public.draft_proposals', privilege_name)
           OR has_table_privilege('anon', 'public.draft_proposals', privilege_name) THEN
            RAISE EXCEPTION 'direct proposal table privilege unexpectedly granted: %', privilege_name;
        END IF;
    END LOOP;
    IF NOT (SELECT relation.relrowsecurity FROM pg_class AS relation JOIN pg_namespace AS namespace ON namespace.oid = relation.relnamespace WHERE namespace.nspname = 'public' AND relation.relname = 'draft_proposals') THEN
        RAISE EXCEPTION 'proposal snapshot table must have RLS enabled';
    END IF;
    IF NOT (SELECT proc.prosecdef AND 'search_path=""' = ANY(proc.proconfig)
              FROM pg_proc AS proc
             WHERE proc.oid = 'public.create_draft_proposal(uuid,uuid,date,text,jsonb)'::regprocedure)
       OR NOT (SELECT proc.prosecdef AND 'search_path=""' = ANY(proc.proconfig)
                 FROM pg_proc AS proc
                WHERE proc.oid = 'public.confirm_draft_proposal(uuid)'::regprocedure) THEN
        RAISE EXCEPTION 'proposal RPCs must be SECURITY DEFINER with an empty fixed search_path';
    END IF;
    IF NOT has_function_privilege('authenticated', 'public.create_draft_proposal(uuid,uuid,date,text,jsonb)', 'EXECUTE')
       OR NOT has_function_privilege('authenticated', 'public.confirm_draft_proposal(uuid)', 'EXECUTE')
       OR has_function_privilege('anon', 'public.create_draft_proposal(uuid,uuid,date,text,jsonb)', 'EXECUTE')
       OR has_function_privilege('anon', 'public.confirm_draft_proposal(uuid)', 'EXECUTE') THEN
        RAISE EXCEPTION 'proposal RPC execute grants do not match authenticated-only policy';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM unnest(table_refs) AS required(table_ref)
        WHERE NOT EXISTS (
            SELECT 1
            FROM pg_class AS relation
            JOIN pg_namespace AS schema ON schema.oid = relation.relnamespace
            WHERE format('%I.%I', schema.nspname, relation.relname) = required.table_ref
              AND relation.relrowsecurity
        )
    ) THEN
        RAISE EXCEPTION 'one or more Book-01 tables do not have RLS enabled';
    END IF;
END;
$$;

-- A real authenticated member still reads only its own book through RLS.
SET ROLE authenticated;
SET request.jwt.claim.sub = '00000000-0000-0000-0000-000000000002';
DO $$
BEGIN
    IF (SELECT count(*) FROM public.books) <> 1 THEN
        RAISE EXCEPTION 'member read visibility changed after DML revocation';
    END IF;

    BEGIN
        INSERT INTO public.books (name) VALUES ('forbidden direct insert');
        RAISE EXCEPTION 'authenticated unexpectedly inserted a book';
    EXCEPTION WHEN insufficient_privilege THEN
        NULL;
    END;

    BEGIN
        UPDATE public.accounts SET name = name;
        RAISE EXCEPTION 'authenticated unexpectedly updated accounts';
    EXCEPTION WHEN insufficient_privilege THEN
        NULL;
    END;

    BEGIN
        DELETE FROM public.journal_entries;
        RAISE EXCEPTION 'authenticated unexpectedly deleted journal entries';
    EXCEPTION WHEN insufficient_privilege THEN
        NULL;
    END;

    BEGIN
        PERFORM count(*) FROM public.draft_proposals;
        RAISE EXCEPTION 'authenticated unexpectedly read proposal snapshots directly';
    EXCEPTION WHEN insufficient_privilege THEN
        NULL;
    END;

    BEGIN
        INSERT INTO public.draft_proposals (user_id, book_id, payload, payload_sha256, expires_at)
        VALUES (auth.uid(), '00000000-0000-0000-0000-000000000001', '{}'::jsonb, repeat('0', 64), now() + interval '1 minute');
        RAISE EXCEPTION 'authenticated unexpectedly inserted proposal directly';
    EXCEPTION WHEN insufficient_privilege THEN
        NULL;
    END;
END;
$$;
RESET ROLE;

SELECT 'Read-only Data API grants and RLS checks passed' AS result;
