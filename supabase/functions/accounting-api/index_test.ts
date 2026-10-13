import { createHandler } from "./index.ts";

function assertEquals(actual: unknown, expected: unknown): void {
  if (JSON.stringify(actual) !== JSON.stringify(expected)) throw new Error(`Expected ${JSON.stringify(expected)}, got ${JSON.stringify(actual)}`);
}
function assertStringIncludes(actual: string, expected: string): void {
  if (!actual.includes(expected)) throw new Error(`Expected string to include ${expected}`);
}
function assert(condition: unknown, message: string): asserts condition { if (!condition) throw new Error(message); }

const env = (name: string) => ({ SUPABASE_URL: "https://example.supabase.co", SUPABASE_PUBLISHABLE_KEY: "public-test-key" }[name]);
const user = { id: "d9428888-122b-4f31-9d8f-1f35f6f7c111" };
const headers = { Authorization: "Bearer valid-test-jwt" };
const BOOK = "11111111-1111-4111-8111-111111111111";
const OTHER_BOOK = "22222222-2222-4222-8222-222222222222";
const CASH = "33333333-3333-4333-8333-333333333333";
const EQUITY = "44444444-4444-4444-8444-444444444444";
const ENTRY_OLD = "55555555-5555-4555-8555-555555555551";
const ENTRY_CURRENT = "55555555-5555-4555-8555-555555555552";
const respond = (data: unknown, status = 200) => new Response(JSON.stringify(data), { status, headers: { "Content-Type": "application/json" } });
const raw = (text: string, status = 200, headers: Record<string, string> = {}) => new Response(text, { status, headers: { "Content-Type": "application/json", ...headers } });
const bookRow = { id: BOOK };
const entry = (id: string, date: string) => ({ id, book_id: BOOK, entry_date: date, description: "fixture", currency_code: "USD", status: "posted", counterparty: null });
const line = (id: string, journal_entry_id: string, account_id: string, debit: string, credit: string, line_number: number) => ({ id, book_id: BOOK, journal_entry_id, account_id, line_number, debit, credit, currency_code: "USD", description: null });

Deno.test("rejects missing bearer without calling Supabase", async () => {
  let calls = 0;
  const handler = createHandler(env, async () => { calls++; return respond(user); });
  const response = await handler(new Request("https://edge.test/functions/v1/accounting-api/api/v1/books"));
  assertEquals(response.status, 401); assertEquals(calls, 0);
});

Deno.test("verifies bearer then lists RLS-visible books using the same caller JWT", async () => {
  const seen: Array<{ url: string; authorization: string | null; apikey: string | null }> = [];
  const fetcher: typeof fetch = async (input, init) => {
    const url = String(input); const requestHeaders = new Headers(init?.headers);
    seen.push({ url, authorization: requestHeaders.get("Authorization"), apikey: requestHeaders.get("apikey") });
    if (url.endsWith("/auth/v1/user")) return respond(user);
    return respond([{ id: BOOK, name: "Primary" }, { id: OTHER_BOOK, name: "Second" }]);
  };
  const handler = createHandler(env, fetcher);
  const response = await handler(new Request("https://edge.test/functions/v1/accounting-api/api/v1/books?pageSize=1", { headers }));
  assertEquals(response.status, 200);
  assertEquals(await response.json(), { items: [{ id: BOOK, name: "Primary" }], nextCursor: "djE6MQ" });
  assertEquals(seen.length, 2); assertEquals(seen[0].url, "https://example.supabase.co/auth/v1/user");
  assertEquals(seen[1].authorization, "Bearer valid-test-jwt"); assertEquals(seen[1].apikey, "public-test-key");
  assertStringIncludes(seen[1].url, "/rest/v1/books?");
});

Deno.test("account listing checks book membership and scopes accounts to that book", async () => {
  const seen: string[] = [];
  const fetcher: typeof fetch = async (input) => {
    const url = String(input); seen.push(url);
    if (url.endsWith("/auth/v1/user")) return respond(user);
    if (url.includes("/rest/v1/books?")) return respond([bookRow]);
    return respond([{ id: CASH, book_id: BOOK, code: "1000", name: "Cash", account_type: "asset", active: true, parent_account_id: null }]);
  };
  const response = await createHandler(env, fetcher)(new Request(`https://edge.test/functions/v1/accounting-api/api/v1/books/${BOOK}/accounts`, { headers }));
  assertEquals(response.status, 200);
  assertEquals(await response.json(), { items: [{ id: CASH, code: "1000", name: "Cash", type: "asset", active: true, parentAccountId: null }], nextCursor: null });
  assertEquals(seen.length, 3); assertStringIncludes(seen[1], `id=eq.${BOOK}`); assertStringIncludes(seen[2], `book_id=eq.${BOOK}`);
});

Deno.test("rejects unmodeled parameters and unsupported write methods before upstream calls", async () => {
  let calls = 0; const handler = createHandler(env, async () => { calls++; return respond(user); });
  const injected = await handler(new Request("https://edge.test/functions/v1/accounting-api/api/v1/books?select=*", { headers }));
  assertEquals(injected.status, 422);
  const post = await handler(new Request("https://edge.test/functions/v1/accounting-api/api/v1/books", { method: "POST", headers }));
  assertEquals(post.status, 405); assertEquals(calls, 0);
});

Deno.test("inaccessible books are indistinguishable from absent books and stop before child reads", async () => {
  let accountQueryMade = false;
  const fetcher: typeof fetch = async (input) => {
    const url = String(input); if (url.endsWith("/auth/v1/user")) return respond(user);
    if (url.includes("/rest/v1/books?")) return respond([]);
    accountQueryMade = true; return respond([]);
  };
  const response = await createHandler(env, fetcher)(new Request(`https://edge.test/functions/v1/accounting-api/api/v1/books/${BOOK}/accounts`, { headers }));
  assertEquals(response.status, 404); assertEquals(accountQueryMade, false);
});

Deno.test("rejects invalid Auth identity, duplicate/unsafe filters and malformed date ranges", async () => {
  let calls = 0;
  const rejectAuth = createHandler(env, async () => { calls++; return respond({ id: "not-a-uuid" }); });
  const invalidIdentity = await rejectAuth(new Request("https://edge.test/functions/v1/accounting-api/api/v1/books", { headers }));
  assertEquals(invalidIdentity.status, 401); assertEquals(calls, 1);
  const countBefore = calls;
  const handler = createHandler(env, async () => { calls++; return respond(user); });
  for (const query of ["pageSize=1&pageSize=2", "pageSize=201", "cursor=bad", "foo=bar"]) {
    const response = await handler(new Request(`https://edge.test/functions/v1/accounting-api/api/v1/books?${query}`, { headers }));
    assertEquals(response.status, 422);
  }
  assertEquals(calls, countBefore);
  let dateCalls = 0;
  const badDateHandler = createHandler(env, async (input) => {
    dateCalls++;
    return String(input).endsWith("/auth/v1/user") ? respond(user) : respond([bookRow]);
  });
  const badDate = await badDateHandler(new Request(`https://edge.test/functions/v1/accounting-api/api/v1/books/${BOOK}/reports/trial-balance?dateFrom=2026-02-30&dateTo=2026-03-01&currency=USD`, { headers }));
  assertEquals(badDate.status, 422); assertEquals(dateCalls, 2);
});

Deno.test("journal listing applies bounded literal filters, stable ordering, exact page totals and RLS headers", async () => {
  const seen: Array<{ url: URL; headers: Headers; redirect?: RequestRedirect }> = [];
  const fetcher: typeof fetch = async (input, init) => {
    const url = new URL(String(input)); const h = new Headers(init?.headers); seen.push({ url, headers: h, redirect: init?.redirect });
    if (url.pathname.endsWith("/auth/v1/user")) return respond(user);
    if (url.pathname.endsWith("/books")) return respond([bookRow]);
    if (url.pathname.endsWith("/journal_entries")) return respond([entry(ENTRY_CURRENT, "2026-01-10")]);
    return respond([line("66666666-6666-4666-8666-666666666661", ENTRY_CURRENT, CASH, "90071992547409.91", "0.00", 1), line("66666666-6666-4666-8666-666666666662", ENTRY_CURRENT, EQUITY, "0.00", "90071992547409.91", 2)]);
  };
  const response = await createHandler(env, fetcher)(new Request(`https://edge.test/functions/v1/accounting-api/api/v1/books/${BOOK}/journal-entries?dateFrom=2026-01-01&dateTo=2026-01-31&status=posted&accountId=${CASH}&search=fixture&pageSize=10`, { headers }));
  assertEquals(response.status, 200); const body = await response.json();
  assertEquals(body.totals, { debits: { amount: "90071992547409.91", currency: "USD" }, credits: { amount: "90071992547409.91", currency: "USD" } });
  assertEquals(body.items[0].lines[0].debit, "90071992547409.91"); assertEquals(body.appliedFilters.accountId, CASH);
  const rest = seen.filter((x) => x.url.pathname.includes("/rest/v1/"));
  assert(rest.every((x) => x.headers.get("Authorization") === "Bearer valid-test-jwt" && x.headers.get("apikey") === "public-test-key"), "must preserve the caller bearer and public key");
  assert(rest.every((x) => x.redirect === "error"), "all upstream requests must reject redirects");
  const journal = rest.find((x) => x.url.pathname.endsWith("/journal_entries"))!;
  assertStringIncludes(journal.url.searchParams.get("order")!, "entry_date.asc,id.asc");
  assertEquals(journal.url.searchParams.get("status"), "eq.posted");
  assertEquals(journal.url.searchParams.get("description"), "ilike.*fixture*");
});

Deno.test("account balance uses exact integer cents and account normal-side semantics", async () => {
  const fetcher: typeof fetch = async (input) => {
    const url = new URL(String(input));
    if (url.pathname.endsWith("/auth/v1/user")) return respond(user);
    if (url.pathname.endsWith("/books")) return respond([bookRow]);
    if (url.pathname.endsWith("/accounts")) return respond([{ id: CASH, book_id: BOOK, account_type: "asset", currency_code: "USD" }]);
    if (url.pathname.endsWith("/journal_entries")) return respond([entry(ENTRY_CURRENT, "2026-01-10")]);
    return raw(`[{"id":"66666666-6666-4666-8666-666666666661","book_id":"${BOOK}","journal_entry_id":"${ENTRY_CURRENT}","account_id":"${CASH}","line_number":1,"debit":12345678901234567890.01,"credit":0.00,"currency_code":"USD"}]`);
  };
  const response = await createHandler(env, fetcher)(new Request(`https://edge.test/functions/v1/accounting-api/api/v1/books/${BOOK}/accounts/${CASH}/balance?asOf=2026-01-10&currency=USD`, { headers }));
  assertEquals(response.status, 200); assertEquals(await response.json(), { bookId: BOOK, accountId: CASH, asOf: "2026-01-10", balance: "12345678901234567890.01", currency: "USD", normalSide: "debit" });
});

Deno.test("trial balance computes opening/period/closing sides with exact decimals", async () => {
  const fetcher: typeof fetch = async (input) => {
    const url = new URL(String(input));
    if (url.pathname.endsWith("/auth/v1/user")) return respond(user);
    if (url.pathname.endsWith("/books")) return respond([bookRow]);
    if (url.pathname.endsWith("/accounts")) return respond([{ id: CASH, book_id: BOOK, code: "1000", name: "Cash", account_type: "asset", currency_code: "USD" }, { id: EQUITY, book_id: BOOK, code: "3000", name: "Equity", account_type: "equity", currency_code: "USD" }]);
    if (url.pathname.endsWith("/journal_entries")) {
      return url.searchParams.get("entry_date") === "gte.2026-01-01" ? respond([entry(ENTRY_OLD, "2025-12-31")]) : respond([entry(ENTRY_CURRENT, "2026-01-01")]);
    }
    if (url.searchParams.get("journal_entry_id")?.includes(ENTRY_OLD)) return respond([line("66666666-6666-4666-8666-666666666661", ENTRY_OLD, CASH, "90071992547409.91", "0.00", 1), line("66666666-6666-4666-8666-666666666662", ENTRY_OLD, EQUITY, "0.00", "90071992547409.91", 2)]);
    return respond([line("66666666-6666-4666-8666-666666666663", ENTRY_CURRENT, CASH, "0.20", "0.00", 1), line("66666666-6666-4666-8666-666666666664", ENTRY_CURRENT, EQUITY, "0.00", "0.20", 2)]);
  };
  const response = await createHandler(env, fetcher)(new Request(`https://edge.test/functions/v1/accounting-api/api/v1/books/${BOOK}/reports/trial-balance?dateFrom=2026-01-01&dateTo=2026-01-31&currency=USD`, { headers }));
  assertEquals(response.status, 200); const body = await response.json();
  assertEquals(body.rows[0].openingDebit.amount, "90071992547409.91"); assertEquals(body.rows[0].periodDebits.amount, "0.20"); assertEquals(body.rows[0].closingDebit.amount, "90071992547410.11");
  assertEquals(body.totals.balanced, true); assertEquals(body.totals.closingDebits.amount, body.totals.closingCredits.amount);
});

Deno.test("income statement is zero only with no posted data; posted data stays typed 501", async () => {
  let posted = false;
  const fetcher: typeof fetch = async (input) => {
    const url = new URL(String(input));
    if (url.pathname.endsWith("/auth/v1/user")) return respond(user);
    if (url.pathname.endsWith("/books")) return respond([bookRow]);
    return posted ? respond([entry(ENTRY_CURRENT, "2026-01-10")]) : respond([]);
  };
  const handler = createHandler(env, fetcher);
  const path = `https://edge.test/functions/v1/accounting-api/api/v1/books/${BOOK}/reports/income-statement?dateFrom=2026-01-01&dateTo=2026-01-31&currency=USD`;
  const empty = await handler(new Request(path, { headers })); assertEquals(empty.status, 200);
  assertEquals((await empty.json()).netResult, { amount: "0.00", currency: "USD" });
  posted = true; const unsupported = await handler(new Request(path, { headers })); assertEquals(unsupported.status, 501);
  const body = await unsupported.json(); assertEquals(body.code, "not_implemented"); assert(!JSON.stringify(body).includes("Supabase"), "error response must not expose internals");
});

Deno.test("upstream redirects are refused and upstream error bodies are redacted", async () => {
  const handler = createHandler(env, async (input, init) => {
    if (String(input).endsWith("/auth/v1/user")) return respond(user);
    assertEquals(init?.redirect, "error"); return new Response("internal secret", { status: 302, headers: { Location: "https://elsewhere.invalid/" } });
  });
  const response = await handler(new Request("https://edge.test/functions/v1/accounting-api/api/v1/books", { headers }));
  assertEquals(response.status, 502); // a redirect is a rejected upstream response, never followed
  assert(!(await response.text()).includes("internal secret"), "upstream error body must be redacted");
});
