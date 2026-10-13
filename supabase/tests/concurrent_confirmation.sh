#!/usr/bin/env bash
set -euo pipefail

psql -v ON_ERROR_STOP=1 -f supabase/tests/concurrent_confirmation_setup.sql
proposal_id="$(psql -X -qAt -v ON_ERROR_STOP=1 -c "SET ROLE authenticated; SET request.jwt.claim.sub = '00000000-0000-0000-0000-000000000001'; SELECT public.create_draft_proposal('11000000-0000-0000-0000-000000000001', '31000000-0000-0000-0000-000000000001', '2026-10-13', 'Concurrent confirmation', '[{\"account_id\":\"21000000-0000-0000-0000-000000000001\",\"description\":null,\"debit\":7.25,\"credit\":0},{\"account_id\":\"21000000-0000-0000-0000-000000000002\",\"description\":null,\"debit\":0,\"credit\":7.25}]'::jsonb)")"
if [[ ! "$proposal_id" =~ ^[0-9a-fA-F-]{36}$ ]]; then
  echo 'Could not obtain a proposal UUID for the concurrency test.' >&2
  exit 1
fi

psql -v ON_ERROR_STOP=1 <<'SQL'
CREATE FUNCTION public.test_pause_confirmation_insert()
RETURNS trigger LANGUAGE plpgsql SET search_path = '' AS $function$
BEGIN
    IF NEW.idempotency_key LIKE 'proposal:%' THEN PERFORM pg_catalog.pg_sleep(1); END IF;
    RETURN NEW;
END;
$function$;
CREATE TRIGGER test_pause_confirmation_insert
BEFORE INSERT ON public.journal_entries
FOR EACH ROW EXECUTE FUNCTION public.test_pause_confirmation_insert();
SQL

tmpdir="$(mktemp -d)"
cleanup() {
  psql -v ON_ERROR_STOP=1 -c 'DROP TRIGGER IF EXISTS test_pause_confirmation_insert ON public.journal_entries; DROP FUNCTION IF EXISTS public.test_pause_confirmation_insert();' >/dev/null 2>&1 || true
  rm -rf "$tmpdir"
}
trap cleanup EXIT

confirm_cmd="SET ROLE authenticated; SET request.jwt.claim.sub = '00000000-0000-0000-0000-000000000001'; SELECT public.confirm_draft_proposal('${proposal_id}'::uuid)"
psql -X -qAt -v ON_ERROR_STOP=1 -c "$confirm_cmd" >"$tmpdir/one" & pid_one=$!
psql -X -qAt -v ON_ERROR_STOP=1 -c "$confirm_cmd" >"$tmpdir/two" & pid_two=$!
wait "$pid_one"
wait "$pid_two"

entry_one="$(cat "$tmpdir/one")"
entry_two="$(cat "$tmpdir/two")"
if [[ ! "$entry_one" =~ ^[0-9a-fA-F-]{36}$ || "$entry_one" != "$entry_two" ]]; then
  echo 'Concurrent confirmations did not return the same entry UUID.' >&2
  exit 1
fi
result="$(psql -X -qAt -v ON_ERROR_STOP=1 -c "SELECT count(*) || ':' || count(*) FILTER (WHERE status = 'draft') FROM public.journal_entries WHERE idempotency_key = 'proposal:${proposal_id}'")"
if [[ "$result" != '1:1' ]]; then
  echo "Concurrent confirmation created an unexpected result: $result" >&2
  exit 1
fi
printf '%s\n' 'Concurrent draft confirmation serialized to one idempotent draft.'
