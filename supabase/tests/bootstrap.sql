-- Minimal test-only stand-ins for Supabase Auth's roles and identity function.
DO $$ BEGIN
    CREATE ROLE anon NOLOGIN;
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;
DO $$ BEGIN
    CREATE ROLE authenticated NOLOGIN;
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;
DO $$ BEGIN
    CREATE ROLE service_role NOLOGIN BYPASSRLS;
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

CREATE SCHEMA IF NOT EXISTS auth;
CREATE TABLE IF NOT EXISTS auth.users (
    id uuid PRIMARY KEY,
    email text UNIQUE
);
CREATE OR REPLACE FUNCTION auth.uid()
RETURNS uuid
LANGUAGE sql
STABLE
AS $$ SELECT nullif(current_setting('request.jwt.claim.sub', true), '')::uuid $$;
GRANT USAGE ON SCHEMA auth TO anon, authenticated;
GRANT EXECUTE ON FUNCTION auth.uid() TO anon, authenticated;
INSERT INTO auth.users (id, email) VALUES
 ('00000000-0000-0000-0000-000000000001', 'owner1@test.invalid'),
 ('00000000-0000-0000-0000-000000000002', 'owner2@test.invalid'),
 ('00000000-0000-0000-0000-000000000003', 'viewer@test.invalid')
ON CONFLICT (id) DO NOTHING;
