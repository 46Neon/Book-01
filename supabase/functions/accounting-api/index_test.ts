import { createHandler } from "./index.ts";

function assertEquals(actual: unknown, expected: unknown): void {
  if (JSON.stringify(actual) !== JSON.stringify(expected)) throw new Error(`Expected ${JSON.stringify(expected)}, got ${JSON.stringify(actual)}`);
}
function assertStringIncludes(actual: string, expected: string): void {
  if (!actual.includes(expected)) throw new Error(`Expected string to include ${expected}`);
}

const env = (name: string) => ({
  SUPABASE_URL: "https://example.supabase.co",
  SUPABASE_PUBLISHABLE_KEY: "public-test-key",
}[name]);
const user = { id: "d9428888-122b-4f31-9d8f-1f35f6f7c111" };
const headers = { Authorization: "Bearer valid-test-jwt" };
const respond = (data: unknown, status = 200) => new Response(JSON.stringify(data), { status, headers: { "Content-Type": "application/json" } });

Deno.test("rejects missing bearer without calling Supabase", async () => {
  let calls = 0;
  const handler = createHandler(env, async () => { calls++; return respond(user); });
  const response = await handler(new Request("https://edge.test/functions/v1/accounting-api/api/v1/books"));
  assertEquals(response.status, 401);
  assertEquals(calls, 0);
});

Deno.test("verifies bearer then lists RLS-visible books using the same caller JWT", async () => {
  const seen: Array<{ url: string; authorization: string | null; apikey: string | null }> = [];
  const fetcher: typeof fetch = async (input, init) => {
    const url = String(input);
    const requestHeaders = new Headers(init?.headers);
    seen.push({ url, authorization: requestHeaders.get("Authorization"), apikey: requestHeaders.get("apikey") });
    if (url.endsWith("/auth/v1/user")) return respond(user);
    return respond([
      { id: "11111111-1111-4111-8111-111111111111", name: "Primary" },
      { id: "22222222-2222-4222-8222-222222222222", name: "Second" },
    ]);
  };
  const handler = createHandler(env, fetcher);
  const response = await handler(new Request("https://edge.test/functions/v1/accounting-api/api/v1/books?pageSize=1", { headers }));
  assertEquals(response.status, 200);
  assertEquals(await response.json(), {
    items: [{ id: "11111111-1111-4111-8111-111111111111", name: "Primary" }],
    nextCursor: "djE6MQ",
  });
  assertEquals(seen.length, 2);
  assertEquals(seen[0].url, "https://example.supabase.co/auth/v1/user");
  assertEquals(seen[1].authorization, "Bearer valid-test-jwt");
  assertEquals(seen[1].apikey, "public-test-key");
  assertStringIncludes(seen[1].url, "/rest/v1/books?");
});

Deno.test("account listing first checks book membership and scopes accounts to that book", async () => {
  const seen: string[] = [];
  const fetcher: typeof fetch = async (input, init) => {
    const url = String(input);
    seen.push(url);
    if (url.endsWith("/auth/v1/user")) return respond(user);
    if (url.includes("/rest/v1/books?")) return respond([{ id: "11111111-1111-4111-8111-111111111111" }]);
    return respond([{ id: "33333333-3333-4333-8333-333333333333", book_id: "11111111-1111-4111-8111-111111111111", code: "1000", name: "Cash", account_type: "asset", active: true, parent_account_id: null }]);
  };
  const handler = createHandler(env, fetcher);
  const response = await handler(new Request("https://edge.test/functions/v1/accounting-api/api/v1/books/11111111-1111-4111-8111-111111111111/accounts", { headers }));
  assertEquals(response.status, 200);
  assertEquals(await response.json(), { items: [{ id: "33333333-3333-4333-8333-333333333333", code: "1000", name: "Cash", type: "asset", active: true, parentAccountId: null }], nextCursor: null });
  assertEquals(seen.length, 3);
  assertStringIncludes(seen[1], "id=eq.11111111-1111-4111-8111-111111111111");
  assertStringIncludes(seen[2], "select=id%2Cbook_id%2Ccode%2Cname%2Caccount_type%2Cactive%2Cparent_account_id");
  assertStringIncludes(seen[2], "book_id=eq.11111111-1111-4111-8111-111111111111");
});

Deno.test("rejects unmodeled query parameters and all write methods", async () => {
  let calls = 0;
  const handler = createHandler(env, async () => { calls++; return respond(user); });
  const injected = await handler(new Request("https://edge.test/functions/v1/accounting-api/api/v1/books?select=*", { headers }));
  assertEquals(injected.status, 422);
  const post = await handler(new Request("https://edge.test/functions/v1/accounting-api/api/v1/books", { method: "POST", headers }));
  assertEquals(post.status, 405);
  assertEquals(calls, 0);
});

Deno.test("rejects inaccessible books without exposing account rows", async () => {
  let accountQueryMade = false;
  const fetcher: typeof fetch = async (input) => {
    const url = String(input);
    if (url.endsWith("/auth/v1/user")) return respond(user);
    if (url.includes("/rest/v1/books?")) return respond([]);
    accountQueryMade = true;
    return respond([]);
  };
  const handler = createHandler(env, fetcher);
  const response = await handler(new Request("https://edge.test/functions/v1/accounting-api/api/v1/books/11111111-1111-4111-8111-111111111111/accounts", { headers }));
  assertEquals(response.status, 404);
  assertEquals(accountQueryMade, false);
});
