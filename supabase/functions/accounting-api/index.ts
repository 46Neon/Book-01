// Book-01 Supabase Edge Function vertical slice: books + chart of accounts.
// Reads use the caller JWT and publishable key so Postgres RLS remains authoritative.
// No service-role secret, arbitrary table access, writes, or model integration exists here.

export type Env = (name: string) => string | undefined;
export type Fetcher = typeof fetch;

const API_PREFIX = "/api/v1";
const MAX_PAGE_SIZE = 200;
const MAX_CURSOR_OFFSET = 10_000;
const MAX_RESPONSE_BYTES = 2_000_000;
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const ACCOUNT_TYPES = new Set(["asset", "liability", "equity", "income", "expense"]);

function apiError(code: string, message: string, requestId: string): Response {
  return Response.json({ code, message, requestId }, { status: statusFor(code), headers: { "Cache-Control": "no-store" } });
}
function statusFor(code: string): number {
  if (code === "unauthorized") return 401;
  if (code === "forbidden") return 403;
  if (code === "not_found") return 404;
  if (code === "validation_error" || code === "unsupported_currency") return 422;
  if (code === "upstream_timeout") return 504;
  if (code === "service_not_configured") return 503;
  return 502;
}
function parseCorsOrigins(value: string | undefined): Set<string> {
  const origins = new Set<string>();
  for (const item of (value ?? "").split(",")) {
    const raw = item.trim();
    if (!raw) continue;
    let url: URL;
    try { url = new URL(raw); } catch { throw new Error("Invalid CORS origin configuration"); }
    if (url.protocol !== "https:" || url.username || url.password || url.pathname !== "/" || url.search || url.hash || raw.includes("*")) {
      throw new Error("Invalid CORS origin configuration");
    }
    origins.add(url.origin);
  }
  return origins;
}
function decodeCursor(cursor: string | null): number {
  if (cursor === null || cursor === "") return 0;
  if (cursor.length > 200 || !/^[A-Za-z0-9_-]+$/.test(cursor)) throw new RangeError("invalid cursor");
  try {
    const b64 = cursor.replaceAll("-", "+").replaceAll("_", "/");
    const raw = atob(b64 + "=".repeat((4 - b64.length % 4) % 4));
    const decoded = new TextDecoder("utf-8", { fatal: true }).decode(Uint8Array.from(raw, (c) => c.charCodeAt(0)));
    if (!/^v1:(0|[1-9]\d*)$/.test(decoded)) throw new Error("invalid cursor");
    const offset = Number(decoded.slice(3));
    if (!Number.isSafeInteger(offset) || offset < 0 || offset > MAX_CURSOR_OFFSET) throw new Error("invalid cursor");
    return offset;
  } catch { throw new RangeError("invalid cursor"); }
}
function encodeCursor(offset: number, pageSize: number, hasMore: boolean): string | null {
  if (!hasMore) return null;
  const raw = btoa(`v1:${offset + pageSize}`);
  return raw.replaceAll("+", "-").replaceAll("/", "_").replace(/=+$/, "");
}
function integerParam(url: URL, name: string, fallback: number): number {
  const raw = url.searchParams.get(name);
  if (raw === null) return fallback;
  if (!/^[1-9]\d*$/.test(raw)) throw new RangeError("invalid page size");
  const value = Number(raw);
  if (!Number.isSafeInteger(value) || value > MAX_PAGE_SIZE) throw new RangeError("invalid page size");
  return value;
}
function jsonRows(value: unknown): Record<string, unknown>[] {
  if (!Array.isArray(value) || value.some((row) => row === null || typeof row !== "object" || Array.isArray(row))) {
    throw new Error("invalid upstream response");
  }
  return value as Record<string, unknown>[];
}

export function createHandler(env: Env = (name) => Deno.env.get(name), fetcher: Fetcher = fetch) {
  return async (request: Request): Promise<Response> => {
    const requestId = crypto.randomUUID();
    let cors: Set<string>;
    try { cors = parseCorsOrigins(env("CORS_ALLOWED_ORIGINS")); }
    catch { return apiError("service_not_configured", "Service configuration is invalid.", requestId); }

    const origin = request.headers.get("Origin");
    const corsHeaders: Record<string, string> = { "Cache-Control": "no-store", "Vary": "Origin" };
    if (origin && cors.has(origin)) {
      corsHeaders["Access-Control-Allow-Origin"] = origin;
      corsHeaders["Access-Control-Allow-Headers"] = "Authorization, Content-Type";
      corsHeaders["Access-Control-Allow-Methods"] = "GET, OPTIONS";
      corsHeaders["Access-Control-Max-Age"] = "600";
    } else if (origin) {
      return Response.json({ code: "forbidden", message: "Origin is not allowed.", requestId }, { status: 403, headers: corsHeaders });
    }
    if (request.method === "OPTIONS") return new Response(null, { status: 204, headers: corsHeaders });
    if (request.method !== "GET") return Response.json({ code: "method_not_allowed", message: "Only GET is supported.", requestId }, { status: 405, headers: corsHeaders });

    const supabaseUrl = (env("SUPABASE_URL") ?? "").replace(/\/$/, "");
    const publicKey = env("SUPABASE_PUBLISHABLE_KEY") ?? env("SUPABASE_ANON_KEY") ?? "";
    if (!/^https:\/\//.test(supabaseUrl) || !publicKey) return Response.json({ code: "service_not_configured", message: "Supabase API configuration is missing.", requestId }, { status: 503, headers: corsHeaders });

    const authorization = request.headers.get("Authorization") ?? "";
    const match = /^Bearer ([^\s]+)$/.exec(authorization);
    if (!match || match[1].length > 8_192) return Response.json({ code: "unauthorized", message: "A valid bearer token is required.", requestId }, { status: 401, headers: { ...corsHeaders, "WWW-Authenticate": "Bearer" } });
    const token = match[1];

    const url = new URL(request.url);
    // Supabase invokes /functions/v1/<function-name>/...; tests/proxies may expose path directly.
    let path = url.pathname;
    const functionPrefix = "/functions/v1/accounting-api";
    if (path.startsWith(functionPrefix)) path = path.slice(functionPrefix.length) || "/";
    if (!path.startsWith(API_PREFIX)) return Response.json({ code: "not_found", message: "Route was not found.", requestId }, { status: 404, headers: corsHeaders });

    // Deliberately reject unexpected query parameters rather than passing arbitrary PostgREST filters.
    const route = path.slice(API_PREFIX.length);
    const allowedQuery = route === "/books" ? new Set(["pageSize", "cursor"])
      : /^\/books\/[0-9a-f-]+\/accounts$/.test(route) ? new Set(["activeOnly", "pageSize", "cursor"])
      : new Set<string>();
    for (const key of url.searchParams.keys()) if (!allowedQuery.has(key)) return Response.json({ code: "validation_error", message: "Request parameters are invalid.", requestId }, { status: 422, headers: corsHeaders });

    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), 5_000);
    try {
      // Auth endpoint verifies token signature/expiry and returns its canonical user identity.
      const authResponse = await fetcher(`${supabaseUrl}/auth/v1/user`, {
        method: "GET", headers: { apikey: publicKey, Authorization: `Bearer ${token}`, Accept: "application/json" }, signal: controller.signal,
      });
      if (authResponse.status === 401 || authResponse.status === 403) return Response.json({ code: "unauthorized", message: "The bearer token was rejected.", requestId }, { status: 401, headers: { ...corsHeaders, "WWW-Authenticate": "Bearer" } });
      if (!authResponse.ok) return Response.json({ code: "upstream_unavailable", message: "Authentication service is unavailable.", requestId }, { status: 502, headers: corsHeaders });
      const authUser: unknown = await authResponse.json();
      if (!authUser || typeof authUser !== "object" || typeof (authUser as { id?: unknown }).id !== "string" || !UUID.test((authUser as { id: string }).id)) {
        return Response.json({ code: "unauthorized", message: "The bearer token was rejected.", requestId }, { status: 401, headers: corsHeaders });
      }
      // Only token is forwarded; verified identity is not accepted from request fields/claims.
      void (authUser as { id: string }).id;

      const matchAccounts = /^\/books\/([0-9a-f-]+)\/accounts$/.exec(route);
      if (route !== "/books" && !matchAccounts) return Response.json({ code: "not_found", message: "Route was not found.", requestId }, { status: 404, headers: corsHeaders });
      const pageSize = integerParam(url, "pageSize", route === "/books" ? 100 : 50);
      const offset = decodeCursor(url.searchParams.get("cursor"));
      if (offset + pageSize > MAX_CURSOR_OFFSET && url.searchParams.has("cursor")) throw new RangeError("cursor limit");
      const baseHeaders = { apikey: publicKey, Authorization: `Bearer ${token}`, Accept: "application/json" };
      const fetchRows = async (table: string, params: Record<string, string>): Promise<Record<string, unknown>[]> => {
        const query = new URLSearchParams(params);
        const response = await fetcher(`${supabaseUrl}/rest/v1/${table}?${query}`, { method: "GET", headers: baseHeaders, signal: controller.signal });
        if (response.status === 401) throw new RouteError(401, "unauthorized", "The bearer token was rejected.");
        if (response.status === 403) throw new RouteError(403, "forbidden", "Access to this accounting resource is denied.");
        if (!response.ok) throw new RouteError(response.status >= 500 ? 502 : 422, response.status >= 500 ? "upstream_unavailable" : "upstream_request_rejected", "Accounting data service could not process the request.");
        const declared = Number(response.headers.get("content-length") ?? "0");
        if (Number.isFinite(declared) && declared > MAX_RESPONSE_BYTES) throw new RouteError(502, "upstream_invalid_response", "Accounting data service returned an invalid response.");
        const text = await response.text();
        if (new TextEncoder().encode(text).byteLength > MAX_RESPONSE_BYTES) throw new RouteError(502, "upstream_invalid_response", "Accounting data service returned an invalid response.");
        try { return jsonRows(JSON.parse(text)); } catch { throw new RouteError(502, "upstream_invalid_response", "Accounting data service returned an invalid response."); }
      };

      let result: unknown;
      if (route === "/books") {
        const rows = await fetchRows("books", { select: "id,name", order: "name.asc,id.asc", limit: String(pageSize + 1), offset: String(offset) });
        const hasMore = rows.length > pageSize;
        if (hasMore && offset + pageSize > MAX_CURSOR_OFFSET) return Response.json({ code: "validation_error", message: "Book list exceeds the bounded pagination limit.", requestId }, { status: 422, headers: corsHeaders });
        const items = rows.slice(0, pageSize).map((row) => {
          if (typeof row.id !== "string" || !UUID.test(row.id) || typeof row.name !== "string") throw new RouteError(502, "upstream_invalid_response", "Accounting data service returned an invalid book list.");
          return { id: row.id, name: row.name };
        });
        result = { items, nextCursor: encodeCursor(offset, pageSize, hasMore) };
      } else {
        const bookId = matchAccounts![1];
        if (!UUID.test(bookId)) throw new RangeError("invalid book id");
        // RLS membership check: absent and inaccessible books are intentionally indistinguishable.
        const books = await fetchRows("books", { select: "id", id: `eq.${bookId}`, limit: "1" });
        if (books.length === 0) throw new RouteError(404, "not_found", "Book was not found or is not accessible.");
        const activeOnly = url.searchParams.get("activeOnly") ?? "true";
        if (activeOnly !== "true" && activeOnly !== "false") throw new RangeError("invalid activeOnly");
        const params: Record<string, string> = { select: "id,book_id,code,name,account_type,active,parent_account_id", book_id: `eq.${bookId}`, order: "code.asc,id.asc", limit: String(pageSize + 1), offset: String(offset) };
        if (activeOnly === "true") params.active = "eq.true";
        const rows = await fetchRows("accounts", params);
        const hasMore = rows.length > pageSize;
        const items = rows.slice(0, pageSize).map((row) => {
          if (typeof row.id !== "string" || !UUID.test(row.id) || row.book_id !== bookId || typeof row.code !== "string" || typeof row.name !== "string" || typeof row.active !== "boolean" || typeof row.account_type !== "string" || !ACCOUNT_TYPES.has(row.account_type) || (row.parent_account_id !== null && (typeof row.parent_account_id !== "string" || !UUID.test(row.parent_account_id)))) {
            throw new RouteError(502, "upstream_invalid_response", "Accounting data service returned an invalid account list.");
          }
          return { id: row.id, code: row.code, name: row.name, type: row.account_type, active: row.active, parentAccountId: row.parent_account_id };
        });
        result = { items, nextCursor: encodeCursor(offset, pageSize, hasMore) };
      }
      return Response.json(result, { status: 200, headers: corsHeaders });
    } catch (error) {
      if (error instanceof RouteError) return Response.json({ code: error.code, message: error.message, requestId }, { status: error.status, headers: { ...corsHeaders, ...(error.status === 401 ? { "WWW-Authenticate": "Bearer" } : {}) } });
      if (error instanceof RangeError) return Response.json({ code: "validation_error", message: "Request parameters are invalid.", requestId }, { status: 422, headers: corsHeaders });
      if (error instanceof DOMException && error.name === "AbortError") return Response.json({ code: "upstream_timeout", message: "Accounting data service timed out.", requestId }, { status: 504, headers: corsHeaders });
      return Response.json({ code: "upstream_unavailable", message: "Accounting data service is unavailable.", requestId }, { status: 502, headers: corsHeaders });
    } finally { clearTimeout(timer); }
  };
}

class RouteError extends Error {
  constructor(public status: number, public code: string, message: string) { super(message); }
}

if (import.meta.main) Deno.serve(createHandler());
