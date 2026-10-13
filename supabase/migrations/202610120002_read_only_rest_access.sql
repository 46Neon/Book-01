-- Supabase Data API roles are read-only until reviewed transaction APIs/RPCs exist.
-- FastAPI forwards the user's JWT and publishable key to PostgREST; RLS still
-- determines which rows an authenticated member may read. No client DML is allowed.
REVOKE ALL PRIVILEGES ON TABLE
  public.books,
  public.book_memberships,
  public.accounts,
  public.accounting_periods,
  public.journal_entries,
  public.journal_entry_lines,
  public.currencies,
  public.audit_events
FROM PUBLIC, anon, authenticated;

GRANT SELECT ON TABLE
  public.books,
  public.book_memberships,
  public.accounts,
  public.accounting_periods,
  public.journal_entries,
  public.journal_entry_lines,
  public.currencies,
  public.audit_events
TO authenticated;

COMMENT ON TABLE public.journal_entries IS
  'Posting is blocked. Authenticated Data API roles are read-only until reviewed transactional operations are added.';
COMMENT ON TABLE public.journal_entry_lines IS
  'Authenticated Data API roles are read-only; lines may not be written directly by clients.';
