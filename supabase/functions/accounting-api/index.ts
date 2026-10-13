// Book-01 Supabase Edge Function accounting read API.
// Supabase Auth validates the caller JWT; PostgREST always receives that same JWT
// plus the publishable/anon key so RLS remains the final authorization boundary.
// No service-role key, DML, arbitrary SQL, model calls, or URL proxy is used.

export type Env = (name: string) => string | undefined;
export type Fetcher = typeof fetch;

const API_PREFIX = "/api/v1";
const MAX_PAGE_SIZE = 200;
const MAX_CURSOR_OFFSET = 10_000;
const MAX_RESPONSE_BYTES = 2_000_000;
const MAX_REPORT_ENTRIES = 1_000;
const MAX_REPORT_LINES = 5_000;
const MAX_REPORT_ACCOUNTS = 1_000;
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const ACCOUNT_TYPES = new Set(["asset", "liability", "equity", "income", "expense"]);
const ENTRY_STATUSES = new Set(["draft", "posted", "void"]);
const BASE_HEADERS = { "Cache-Control": "no-store", "Vary": "Origin" };

function apiError(code: string, message: string, requestId: string, status = statusFor(code), headers: HeadersInit = {}): Response {
  return Response.json({ code, message, requestId }, { status, headers: { ...BASE_HEADERS, ...headers } });
}
function statusFor(code: string): number {
  if (code === "unauthorized") return 401;
  if (code === "forbidden") return 403;
  if (code === "not_found") return 404;
  if (code === "validation_error" || code === "unsupported_currency" || code === "result_limit_exceeded") return 422;
  if (code === "not_implemented") return 501;
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
    if (url.protocol !== "https:" || url.username || url.password || url.pathname !== "/" || url.search || url.hash || raw.includes("*")) throw new Error("Invalid CORS origin configuration");
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
  return btoa(`v1:${offset + pageSize}`).replaceAll("+", "-").replaceAll("/", "_").replace(/=+$/, "");
}
function integerParam(url: URL, name: string, fallback: number): number {
  const values = url.searchParams.getAll(name);
  if (values.length > 1) throw new RangeError("duplicate page size");
  const raw = values[0];
  if (raw === undefined) return fallback;
  if (!/^[1-9]\d*$/.test(raw)) throw new RangeError("invalid page size");
  const value = Number(raw);
  if (!Number.isSafeInteger(value) || value > MAX_PAGE_SIZE) throw new RangeError("invalid page size");
  return value;
}
function oneParam(url: URL, name: string): string | null {
  const values = url.searchParams.getAll(name);
  if (values.length > 1) throw new RangeError(`duplicate ${name}`);
  return values[0] ?? null;
}
function jsonRows(value: unknown): Record<string, unknown>[] {
  if (!Array.isArray(value) || value.some((row) => row === null || typeof row !== "object" || Array.isArray(row))) throw new Error("invalid upstream response");
  return value as Record<string, unknown>[];
}
function isoDate(value: string | null, name: string): string {
  if (!value || !/^\d{4}-\d{2}-\d{2}$/.test(value) || value.startsWith("0000")) throw new RangeError(`invalid ${name}`);
  const d = new Date(`${value}T00:00:00.000Z`);
  if (!Number.isFinite(d.getTime()) || d.toISOString().slice(0, 10) !== value) throw new RangeError(`invalid ${name}`);
  return value;
}
function cmpDate(a: string, b: string): number { return a.localeCompare(b); }
function previousDate(value: string): string | null {
  const d = new Date(`${value}T00:00:00.000Z`);
  d.setUTCDate(d.getUTCDate() - 1);
  return d.getUTCFullYear() < 1 ? null : d.toISOString().slice(0, 10);
}
function currency(value: string | null): string {
  if (value === null || !/^[A-Z]{3}$/.test(value)) throw new RangeError("invalid currency");
  if (value !== "USD") throw new RouteError(422, "unsupported_currency", "This migration supports USD only.");
  return value;
}
function cents(value: unknown): bigint {
  if (typeof value !== "string" || value.length > 128 || !/^(0|[1-9]\d*)(?:\.\d+)?$/.test(value)) throw new RouteError(502, "upstream_invalid_response", "Accounting data service returned an invalid amount.");
  const [whole, fraction = ""] = value.split(".");
  if (/[^0]/.test(fraction.slice(2))) throw new RouteError(502, "upstream_invalid_response", "Accounting data service returned an invalid amount.");
  return BigInt(whole) * 100n + BigInt((fraction + "00").slice(0, 2));
}
function money(value: bigint): string {
  const negative = value < 0n;
  const abs = negative ? -value : value;
  return `${negative ? "-" : ""}${abs / 100n}.${String(abs % 100n).padStart(2, "0")}`;
}
function expectString(row: Record<string, unknown>, key: string): string {
  if (typeof row[key] !== "string") throw new RouteError(502, "upstream_invalid_response", "Accounting data service returned an invalid response.");
  return row[key] as string;
}
function expectUuid(row: Record<string, unknown>, key: string): string {
  const value = expectString(row, key);
  if (!UUID.test(value)) throw new RouteError(502, "upstream_invalid_response", "Accounting data service returned an invalid response.");
  return value;
}
function expectNullableString(row: Record<string, unknown>, key: string): string | null {
  const value = row[key];
  if (value !== null && typeof value !== "string") throw new RouteError(502, "upstream_invalid_response", "Accounting data service returned an invalid response.");
  return value as string | null;
}
function dateParam(value: unknown): string {
  if (typeof value !== "string") throw new RouteError(502, "upstream_invalid_response", "Accounting data service returned an invalid date.");
  try { return isoDate(value, "date"); } catch { throw new RouteError(502, "upstream_invalid_response", "Accounting data service returned an invalid date."); }
}
function safeNumericJson(text: string): unknown {
  // PostgREST emits PostgreSQL NUMERIC as JSON number tokens. Quote only the two
  // amount columns before JSON.parse so no IEEE-754 rounding can occur.
  const quoted = text.replace(/(?<!\\)("(?:debit|credit)"\s*:\s*)(-?(?:0|[1-9]\d*)(?:\.\d+)?(?:[eE][+-]?\d+)?)(?=\s*[,}])/g, "$1\"$2\"");
  return JSON.parse(quoted);
}

export function createHandler(env: Env = (name) => Deno.env.get(name), fetcher: Fetcher = fetch) {
  return async (request: Request): Promise<Response> => {
    const requestId = crypto.randomUUID();
    let cors: Set<string>;
    try { cors = parseCorsOrigins(env("CORS_ALLOWED_ORIGINS")); }
    catch { return apiError("service_not_configured", "Service configuration is invalid.", requestId); }
    const origin = request.headers.get("Origin");
    const corsHeaders: Record<string, string> = { ...BASE_HEADERS };
    if (origin && cors.has(origin)) {
      corsHeaders["Access-Control-Allow-Origin"] = origin;
      corsHeaders["Access-Control-Allow-Headers"] = "Authorization, Content-Type";
      corsHeaders["Access-Control-Allow-Methods"] = "GET, OPTIONS";
      corsHeaders["Access-Control-Max-Age"] = "600";
    } else if (origin) return apiError("forbidden", "Origin is not allowed.", requestId, 403, corsHeaders);
    if (request.method === "OPTIONS") return new Response(null, { status: 204, headers: corsHeaders });
    if (request.method !== "GET") return apiError("method_not_allowed", "Only GET is supported.", requestId, 405, corsHeaders);

    const supabaseUrl = (env("SUPABASE_URL") ?? "").replace(/\/$/, "");
    const publicKey = env("SUPABASE_PUBLISHABLE_KEY") ?? env("SUPABASE_ANON_KEY") ?? "";
    let project: URL;
    try { project = new URL(supabaseUrl); } catch { return apiError("service_not_configured", "Supabase API configuration is missing.", requestId, 503, corsHeaders); }
    if (project.protocol !== "https:" || project.username || project.password || project.pathname !== "/" || project.search || project.hash || !publicKey || publicKey.length > 4_096) return apiError("service_not_configured", "Supabase API configuration is missing.", requestId, 503, corsHeaders);
    const authMatch = /^Bearer ([^\s]+)$/.exec(request.headers.get("Authorization") ?? "");
    if (!authMatch || authMatch[1].length > 8_192) return apiError("unauthorized", "A valid bearer token is required.", requestId, 401, { ...corsHeaders, "WWW-Authenticate": "Bearer" });
    const token = authMatch[1];

    const url = new URL(request.url);
    let path = url.pathname;
    const functionPrefix = "/functions/v1/accounting-api";
    if (path === functionPrefix) path = "/";
    else if (path.startsWith(`${functionPrefix}/`)) path = path.slice(functionPrefix.length);
    if (!path.startsWith(API_PREFIX) || path.includes("%") || path.includes("\\") || path.includes("//")) return apiError("not_found", "Route was not found.", requestId, 404, corsHeaders);
    const route = path.slice(API_PREFIX.length);
    const books = route === "/books";
    const accountList = /^\/books\/([0-9a-f-]+)\/accounts$/.exec(route);
    const journalList = /^\/books\/([0-9a-f-]+)\/journal-entries$/.exec(route);
    const balanceRoute = /^\/books\/([0-9a-f-]+)\/accounts\/([0-9a-f-]+)\/balance$/.exec(route);
    const trialRoute = /^\/books\/([0-9a-f-]+)\/reports\/trial-balance$/.exec(route);
    const incomeRoute = /^\/books\/([0-9a-f-]+)\/reports\/income-statement$/.exec(route);
    const routeKey = books ? "books" : accountList ? "accounts" : journalList ? "journal" : balanceRoute ? "balance" : trialRoute ? "trial" : incomeRoute ? "income" : "unknown";
    const allowed = new Set(routeKey === "books" ? ["pageSize", "cursor"]
      : routeKey === "accounts" ? ["activeOnly", "pageSize", "cursor"]
      : routeKey === "journal" ? ["dateFrom", "dateTo", "accountId", "status", "currency", "search", "pageSize", "cursor"]
      : routeKey === "balance" ? ["asOf", "currency"]
      : routeKey === "trial" || routeKey === "income" ? ["dateFrom", "dateTo", "currency"] : []);
    if (routeKey === "unknown") return apiError("not_found", "Route was not found.", requestId, 404, corsHeaders);
    for (const key of url.searchParams.keys()) if (!allowed.has(key)) return apiError("validation_error", "Request parameters are invalid.", requestId, 422, corsHeaders);
    // Only repeated status filters are defined as arrays. Duplicate every other
    // key is rejected (even identical values) to keep the wire contract unambiguous.
    for (const key of allowed) if (key !== "status" && url.searchParams.getAll(key).length > 1) return apiError("validation_error", "Request parameters are invalid.", requestId, 422, corsHeaders);

    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), 5_000);
    try {
      const authResponse = await fetcher(`${project.origin}/auth/v1/user`, { method: "GET", headers: { apikey: publicKey, Authorization: `Bearer ${token}`, Accept: "application/json" }, signal: controller.signal, redirect: "error" });
      if (authResponse.status === 401 || authResponse.status === 403) return apiError("unauthorized", "The bearer token was rejected.", requestId, 401, { ...corsHeaders, "WWW-Authenticate": "Bearer" });
      if (!authResponse.ok) return apiError("upstream_unavailable", "Authentication service is unavailable.", requestId, 502, corsHeaders);
      const authUser: unknown = await authResponse.json();
      if (!authUser || typeof authUser !== "object" || typeof (authUser as { id?: unknown }).id !== "string" || !UUID.test((authUser as { id: string }).id)) return apiError("unauthorized", "The bearer token was rejected.", requestId, 401, { ...corsHeaders, "WWW-Authenticate": "Bearer" });
      const headers = { apikey: publicKey, Authorization: `Bearer ${token}`, Accept: "application/json" };
      const fetchRows = async (table: string, params: Record<string, string>): Promise<Record<string, unknown>[]> => {
        // table names are internal constants, never request-derived.
        const query = new URLSearchParams(params);
        const response = await fetcher(`${project.origin}/rest/v1/${table}?${query}`, { method: "GET", headers, signal: controller.signal, redirect: "error" });
        if (response.status === 401) throw new RouteError(401, "unauthorized", "The bearer token was rejected.");
        if (response.status === 403) throw new RouteError(403, "forbidden", "Access to this accounting resource is denied.");
        if (response.status === 404) throw new RouteError(404, "not_found", "Accounting resource was not found.");
        if (response.status >= 300 && response.status < 400) throw new RouteError(502, "upstream_unavailable", "Accounting data service returned an unsafe redirect.");
        if (!response.ok) throw new RouteError(response.status >= 500 ? 502 : 422, response.status >= 500 ? "upstream_unavailable" : "upstream_request_rejected", "Accounting data service could not process the request.");
        const declared = Number(response.headers.get("content-length") ?? "0");
        if (Number.isFinite(declared) && declared > MAX_RESPONSE_BYTES) throw new RouteError(502, "upstream_invalid_response", "Accounting data service returned an invalid response.");
        const text = await response.text();
        if (new TextEncoder().encode(text).byteLength > MAX_RESPONSE_BYTES) throw new RouteError(502, "upstream_invalid_response", "Accounting data service returned an invalid response.");
        try { return jsonRows(safeNumericJson(text)); } catch { throw new RouteError(502, "upstream_invalid_response", "Accounting data service returned an invalid response."); }
      };
      const authorizeBook = async (bookId: string) => {
        if (!UUID.test(bookId)) throw new RangeError("invalid book id");
        const rows = await fetchRows("books", { select: "id", id: `eq.${bookId}`, limit: "1" });
        if (!rows.length) throw new RouteError(404, "not_found", "Book was not found or is not accessible.");
      };
      const loadEntries = async (bookId: string, from: string, to: string, statuses: string[] = ["posted"], search?: string): Promise<Record<string, unknown>[]> => {
        const params: Record<string, string> = { select: "id,book_id,entry_date,description,currency_code,status,counterparty", book_id: `eq.${bookId}`, entry_date: `gte.${from}`, and: `(entry_date.lte.${to})`, order: "entry_date.asc,id.asc", limit: String(MAX_REPORT_ENTRIES + 1) };
        params.status = statuses.length === 1 ? `eq.${statuses[0]}` : `in.(${statuses.join(",")})`;
        if (search !== undefined) params.description = `ilike.*${search}*`;
        const rows = await fetchRows("journal_entries", params);
        if (rows.length > MAX_REPORT_ENTRIES) throw new RouteError(422, "result_limit_exceeded", "Entry range exceeds the bounded result limit.");
        for (const row of rows) {
          if (expectUuid(row, "book_id") !== bookId || !ENTRY_STATUSES.has(expectString(row, "status")) || typeof row.description !== "string") throw new RouteError(502, "upstream_invalid_response", "Accounting data service returned an invalid journal entry.");
          if (row.currency_code !== "USD") throw new RouteError(422, "unsupported_currency", "Only USD journal data is supported.");
          dateParam(row.entry_date);
        }
        return rows;
      };
      const loadLines = async (bookId: string, entries: Record<string, unknown>[], includeDescription = false): Promise<Record<string, unknown>[]> => {
        const ids = entries.map((row) => expectUuid(row, "id"));
        const lines: Record<string, unknown>[] = [];
        for (let i = 0; i < ids.length; i += 100) {
          const query = await fetchRows("journal_entry_lines", {
            select: includeDescription ? "id,book_id,journal_entry_id,account_id,line_number,description,debit,credit,currency_code" : "id,book_id,journal_entry_id,account_id,line_number,debit,credit,currency_code",
            book_id: `eq.${bookId}`, journal_entry_id: `in.(${ids.slice(i, i + 100).join(",")})`, order: "journal_entry_id.asc,line_number.asc", limit: String(MAX_REPORT_LINES + 1 - lines.length),
          });
          lines.push(...query);
          if (lines.length > MAX_REPORT_LINES) throw new RouteError(422, "result_limit_exceeded", "Journal lines exceed the bounded result limit.");
        }
        const entryIds = new Set(ids);
        for (const line of lines) {
          if (expectUuid(line, "book_id") !== bookId || !entryIds.has(expectUuid(line, "journal_entry_id")) || !UUID.test(expectUuid(line, "account_id"))) throw new RouteError(502, "upstream_invalid_response", "Accounting data service returned an invalid journal line.");
          if (typeof line.line_number !== "number" || !Number.isSafeInteger(line.line_number) || line.line_number < 1) throw new RouteError(502, "upstream_invalid_response", "Accounting data service returned an invalid journal line.");
          if (line.currency_code !== "USD") throw new RouteError(422, "unsupported_currency", "Only USD journal data is supported.");
          cents(line.debit); cents(line.credit);
        }
        return lines;
      };
      const dateRange = (from: string | null, to: string | null): [string, string] => {
        const start = isoDate(from, "dateFrom"); const end = isoDate(to, "dateTo");
        if (cmpDate(start, end) > 0) throw new RangeError("invalid date range");
        return [start, end];
      };
      const moneyTotal = (n: bigint, cur: string) => ({ amount: money(n), currency: cur });
      let payload: unknown;

      if (routeKey === "books") {
        const pageSize = integerParam(url, "pageSize", 100); const offset = decodeCursor(oneParam(url, "cursor"));
        if (url.searchParams.has("cursor") && offset + pageSize > MAX_CURSOR_OFFSET) throw new RangeError("cursor limit");
        const rows = await fetchRows("books", { select: "id,name", order: "name.asc,id.asc", limit: String(pageSize + 1), offset: String(offset) });
        const hasMore = rows.length > pageSize;
        if (hasMore && offset + pageSize > MAX_CURSOR_OFFSET) throw new RouteError(422, "result_limit_exceeded", "Book list exceeds the bounded pagination limit.");
        const items = rows.slice(0, pageSize).map((row) => ({ id: expectUuid(row, "id"), name: expectString(row, "name") }));
        payload = { items, nextCursor: encodeCursor(offset, pageSize, hasMore) };
      } else if (routeKey === "accounts") {
        const bookId = accountList![1]; await authorizeBook(bookId);
        const activeOnly = oneParam(url, "activeOnly") ?? "true";
        if (activeOnly !== "true" && activeOnly !== "false") throw new RangeError("invalid activeOnly");
        const pageSize = integerParam(url, "pageSize", 50); const offset = decodeCursor(oneParam(url, "cursor"));
        if (url.searchParams.has("cursor") && offset + pageSize > MAX_CURSOR_OFFSET) throw new RangeError("cursor limit");
        const params: Record<string, string> = { select: "id,book_id,code,name,account_type,active,parent_account_id", book_id: `eq.${bookId}`, order: "code.asc,id.asc", limit: String(pageSize + 1), offset: String(offset) };
        if (activeOnly === "true") params.active = "eq.true";
        const rows = await fetchRows("accounts", params); const hasMore = rows.length > pageSize;
        const items = rows.slice(0, pageSize).map((row) => {
          if (expectUuid(row, "book_id") !== bookId) throw new RouteError(502, "upstream_invalid_response", "Accounting data service returned an invalid account list.");
          const type = expectString(row, "account_type");
          if (!ACCOUNT_TYPES.has(type) || typeof row.active !== "boolean") throw new RouteError(502, "upstream_invalid_response", "Accounting data service returned an invalid account list.");
          const parent = row.parent_account_id === null ? null : expectUuid(row, "parent_account_id");
          return { id: expectUuid(row, "id"), code: expectString(row, "code"), name: expectString(row, "name"), type, active: row.active, parentAccountId: parent };
        });
        payload = { items, nextCursor: encodeCursor(offset, pageSize, hasMore) };
      } else if (routeKey === "journal") {
        const bookId = journalList![1]; await authorizeBook(bookId);
        const [from, to] = dateRange(oneParam(url, "dateFrom"), oneParam(url, "dateTo"));
        const rawStatuses = url.searchParams.getAll("status");
        const statuses = rawStatuses.length ? rawStatuses : ["posted"];
        if (statuses.length > ENTRY_STATUSES.size || statuses.some((s) => !ENTRY_STATUSES.has(s))) throw new RangeError("invalid status");
        const accountId = oneParam(url, "accountId");
        if (accountId !== null && !UUID.test(accountId)) throw new RangeError("invalid account id");
        const curValue = oneParam(url, "currency"); const cur = curValue === null ? "USD" : currency(curValue);
        const search = oneParam(url, "search");
        if (search !== null && (search.length > 120 || !search.trim() || /[%_(),*\\]/.test(search))) throw new RangeError("invalid search");
        const pageSize = integerParam(url, "pageSize", 50); const offset = decodeCursor(oneParam(url, "cursor"));
        if (url.searchParams.has("cursor") && offset + pageSize > MAX_CURSOR_OFFSET) throw new RangeError("cursor limit");
        const entries = await loadEntries(bookId, from, to, [...new Set(statuses)].sort(), search ?? undefined);
        if (cur !== "USD" || entries.some((e) => e.currency_code !== "USD")) throw new RouteError(422, "unsupported_currency", "Only USD journal data is supported.");
        const lines = await loadLines(bookId, entries, true);
        const lineMap = new Map<string, Record<string, unknown>[]>();
        for (const line of lines) { const id = expectUuid(line, "journal_entry_id"); const group = lineMap.get(id) ?? []; group.push(line); lineMap.set(id, group); }
        let filtered = entries;
        if (accountId) filtered = filtered.filter((entry) => (lineMap.get(expectUuid(entry, "id")) ?? []).some((line) => line.account_id === accountId));
        const visible = filtered.slice(offset, offset + pageSize);
        const pageIds = new Set(visible.map((entry) => expectUuid(entry, "id")));
        let debits = 0n, credits = 0n;
        const items = visible.map((entry) => {
          const id = expectUuid(entry, "id");
          const entryLines = (lineMap.get(id) ?? []).map((line) => {
            const debit = cents(line.debit); const credit = cents(line.credit); debits += pageIds.has(id) ? debit : 0n; credits += pageIds.has(id) ? credit : 0n;
            return { id: expectUuid(line, "id"), accountId: expectUuid(line, "account_id"), description: expectNullableString(line, "description"), debit: money(debit), credit: money(credit), currency: "USD" };
          });
          const status = expectString(entry, "status");
          if (!ENTRY_STATUSES.has(status)) throw new RouteError(502, "upstream_invalid_response", "Accounting data service returned an invalid journal entry.");
          const counterparty = entry.counterparty === undefined ? null : expectNullableString(entry, "counterparty");
          if (counterparty !== null && counterparty.length > 80) throw new RouteError(502, "upstream_invalid_response", "Accounting data service returned an invalid journal entry.");
          return { id, date: dateParam(entry.entry_date), description: expectString(entry, "description"), status, lines: entryLines, counterparty };
        });
        payload = { period: { from, to }, appliedFilters: { dateFrom: from, dateTo: to, accountId, status: statuses, currency: curValue, search }, items, totals: { debits: moneyTotal(debits, cur), credits: moneyTotal(credits, cur) }, nextCursor: encodeCursor(offset, pageSize, filtered.length > offset + pageSize) };
      } else if (routeKey === "balance") {
        const bookId = balanceRoute![1], accountId = balanceRoute![2]; await authorizeBook(bookId);
        if (!UUID.test(accountId)) throw new RangeError("invalid account id");
        const asOf = isoDate(oneParam(url, "asOf"), "asOf"); const cur = currency(oneParam(url, "currency"));
        const accounts = await fetchRows("accounts", { select: "id,book_id,account_type,currency_code", book_id: `eq.${bookId}`, id: `eq.${accountId}`, limit: "1" });
        if (!accounts.length) throw new RouteError(404, "not_found", "Account was not found or is not accessible.");
        const account = accounts[0];
        if (expectUuid(account, "id") !== accountId) throw new RouteError(502, "upstream_invalid_response", "Accounting data service returned an invalid account.");
        if (expectUuid(account, "book_id") !== bookId || account.currency_code !== cur) throw new RouteError(422, "unsupported_currency", "Account currency does not match the requested currency.");
        const type = expectString(account, "account_type"); if (!ACCOUNT_TYPES.has(type)) throw new RouteError(502, "upstream_invalid_response", "Accounting data service returned an invalid account.");
        const entries = await loadEntries(bookId, "0001-01-01", asOf, ["posted"]); const lines = await loadLines(bookId, entries);
        let debit = 0n, credit = 0n;
        for (const line of lines) if (line.account_id === accountId) { debit += cents(line.debit); credit += cents(line.credit); }
        const normalSide = type === "asset" || type === "expense" ? "debit" : "credit";
        payload = { bookId, accountId, asOf, balance: money(normalSide === "debit" ? debit - credit : credit - debit), currency: cur, normalSide };
      } else if (routeKey === "trial") {
        const bookId = trialRoute![1]; await authorizeBook(bookId);
        const [from, to] = dateRange(oneParam(url, "dateFrom"), oneParam(url, "dateTo")); const cur = currency(oneParam(url, "currency"));
        const accounts = await fetchRows("accounts", { select: "id,book_id,code,name,account_type,currency_code", book_id: `eq.${bookId}`, order: "code.asc,id.asc", limit: String(MAX_REPORT_ACCOUNTS + 1) });
        if (accounts.length > MAX_REPORT_ACCOUNTS) throw new RouteError(422, "result_limit_exceeded", "Book exceeds the bounded report account limit.");
        for (const account of accounts) if (expectUuid(account, "book_id") !== bookId || account.currency_code !== cur || !ACCOUNT_TYPES.has(expectString(account, "account_type"))) throw new RouteError(422, "unsupported_currency", "Mixed-currency reports are unsupported.");
        const dayBefore = previousDate(from);
        const before = dayBefore ? await loadEntries(bookId, "0001-01-01", dayBefore, ["posted"]) : [];
        const period = await loadEntries(bookId, from, to, ["posted"]);
        const beforeLines = await loadLines(bookId, before); const periodLines = await loadLines(bookId, period);
        const values = new Map<string, { od: bigint; oc: bigint; pd: bigint; pc: bigint }>();
        for (const account of accounts) values.set(expectUuid(account, "id"), { od: 0n, oc: 0n, pd: 0n, pc: 0n });
        const beforeIds = new Set(before.map((e) => expectUuid(e, "id"))), periodIds = new Set(period.map((e) => expectUuid(e, "id")));
        const accumulate = (lines: Record<string, unknown>[], ids: Set<string>, dKey: "od" | "pd", cKey: "oc" | "pc") => {
          for (const line of lines) {
            if (!ids.has(expectUuid(line, "journal_entry_id"))) continue;
            const accId = expectUuid(line, "account_id"); const target = values.get(accId);
            if (!target) throw new RouteError(502, "upstream_invalid_response", "Accounting data service returned a line for an unknown account.");
            target[dKey] += cents(line.debit); target[cKey] += cents(line.credit);
          }
        };
        accumulate(beforeLines, beforeIds, "od", "oc"); accumulate(periodLines, periodIds, "pd", "pc");
        const totals = { openingDebits: 0n, openingCredits: 0n, periodDebits: 0n, periodCredits: 0n, closingDebits: 0n, closingCredits: 0n };
        const rows = accounts.map((account) => {
          const v = values.get(expectUuid(account, "id"))!; const openingNet = v.od - v.oc; const closingNet = openingNet + v.pd - v.pc;
          const od = openingNet > 0n ? openingNet : 0n, oc = openingNet < 0n ? -openingNet : 0n;
          const cd = closingNet > 0n ? closingNet : 0n, cc = closingNet < 0n ? -closingNet : 0n;
          totals.openingDebits += od; totals.openingCredits += oc; totals.periodDebits += v.pd; totals.periodCredits += v.pc; totals.closingDebits += cd; totals.closingCredits += cc;
          return { accountId: expectUuid(account, "id"), code: expectString(account, "code"), name: expectString(account, "name"), openingDebit: moneyTotal(od, cur), openingCredit: moneyTotal(oc, cur), periodDebits: moneyTotal(v.pd, cur), periodCredits: moneyTotal(v.pc, cur), closingDebit: moneyTotal(cd, cur), closingCredit: moneyTotal(cc, cur) };
        });
        const totalsBody: Record<string, unknown> = Object.fromEntries(Object.entries(totals).map(([k, v]) => [k, moneyTotal(v, cur)]));
        totalsBody.balanced = totals.closingDebits === totals.closingCredits;
        payload = { period: { from, to }, currency: cur, rows, totals: totalsBody };
      } else {
        const bookId = incomeRoute![1]; await authorizeBook(bookId);
        const [from, to] = dateRange(oneParam(url, "dateFrom"), oneParam(url, "dateTo")); const cur = currency(oneParam(url, "currency"));
        const entries = await loadEntries(bookId, from, to, ["posted"]);
        if (entries.length) throw new RouteError(501, "not_implemented", "The migration does not distinguish cost accounts from expenses, so an income statement cannot be classified faithfully.");
        payload = { period: { from, to }, currency: cur, income: [], costs: [], expenses: [], netResult: moneyTotal(0n, cur) };
      }
      return Response.json(payload, { status: 200, headers: corsHeaders });
    } catch (error) {
      if (error instanceof RouteError) return apiError(error.code, error.message, requestId, error.status, { ...corsHeaders, ...(error.status === 401 ? { "WWW-Authenticate": "Bearer" } : {}) });
      if (error instanceof RangeError) return apiError("validation_error", "Request parameters are invalid.", requestId, 422, corsHeaders);
      if (error instanceof DOMException && error.name === "AbortError") return apiError("upstream_timeout", "Accounting data service timed out.", requestId, 504, corsHeaders);
      return apiError("upstream_unavailable", "Accounting data service is unavailable.", requestId, 502, corsHeaders);
    } finally { clearTimeout(timer); }
  };
}

class RouteError extends Error {
  constructor(public status: number, public code: string, message: string) { super(message); }
}

if (import.meta.main) Deno.serve(createHandler());
