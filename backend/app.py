"""Book-01 non-production, read-only accounting API scaffold.

All accounting data is fetched from Supabase PostgREST with the caller's JWT.
This module intentionally has no database driver, service-role secret, write
route, or model-provider integration.
"""
from __future__ import annotations

import base64
import binascii
import json
import os
import re
import uuid
from dataclasses import dataclass
from datetime import date
from decimal import Decimal, InvalidOperation, getcontext
from typing import Any, AsyncIterator, Literal

import httpx
from fastapi import Depends, FastAPI, HTTPException, Query, Request
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from fastapi.exceptions import RequestValidationError
from fastapi.openapi.utils import get_openapi
from fastapi.responses import JSONResponse
from pydantic import BaseModel, ConfigDict, Field

API_PREFIX = "/api/v1"
MAX_PAGE_SIZE = 200
MAX_CURSOR_OFFSET = 10_000
MAX_REPORT_ENTRIES = 1_000
MAX_REPORT_LINES = 5_000
MAX_REPORT_ACCOUNTS = 1_000
MAX_UPSTREAM_RESPONSE_BYTES = 2_000_000
MAX_DECIMAL_TEXT_LENGTH = 128
# Migration amounts are exact NUMERIC with two decimal places; this precision
# safely covers the largest accepted text plus sums of the bounded report rows.
getcontext().prec = 160
UUID_RE = re.compile(r"^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-8][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$")
CURRENCY_RE = re.compile(r"^[A-Z]{3}$")


@dataclass(frozen=True)
class Config:
    supabase_url: str
    anon_key: str

    @classmethod
    def from_env(cls) -> "Config":
        url = os.getenv("SUPABASE_URL", "").strip().rstrip("/")
        key = (os.getenv("SUPABASE_PUBLISHABLE_KEY") or os.getenv("SUPABASE_ANON_KEY") or "").strip()
        if not url or not key:
            raise HTTPException(503, detail=error("service_not_configured", "Supabase API configuration is missing."))
        if not url.startswith("https://"):
            raise HTTPException(503, detail=error("service_not_configured", "Supabase URL must use HTTPS."))
        return cls(url, key)


def error(code: str, message: str, details: dict[str, Any] | None = None) -> dict[str, Any]:
    body: dict[str, Any] = {"code": code, "message": message, "requestId": str(uuid.uuid4())}
    if details is not None:
        body["details"] = details
    return body


class ApiError(BaseModel):
    code: str
    message: str
    requestId: str
    details: dict[str, Any] = Field(default_factory=dict)


ERROR_401 = {401: {"model": ApiError}}
ERROR_403 = {403: {"model": ApiError}}
ERROR_404 = {404: {"model": ApiError}}
ERROR_422 = {422: {"model": ApiError}}
ERROR_501 = {501: {"model": ApiError}}
ERROR_UPSTREAM = {502: {"model": ApiError}, 503: {"model": ApiError}, 504: {"model": ApiError}}


class Account(BaseModel):
    model_config = ConfigDict(populate_by_name=True)
    id: uuid.UUID
    code: str
    name: str
    type: Literal["asset", "liability", "equity", "income", "expense"]
    active: bool


class MoneyTotal(BaseModel):
    amount: str = Field(pattern=r"^-?(0|[1-9]\d*)(\.\d+)?$")
    currency: str = Field(pattern=r"^[A-Z]{3}$")


class JournalLine(BaseModel):
    id: uuid.UUID
    accountId: uuid.UUID
    description: str | None = None
    debit: str = Field(pattern=r"^(0|[1-9]\d*)(\.\d+)?$")
    credit: str = Field(pattern=r"^(0|[1-9]\d*)(\.\d+)?$")
    currency: str = Field(pattern=r"^[A-Z]{3}$")


class JournalEntry(BaseModel):
    id: uuid.UUID
    date: date
    description: str
    status: Literal["draft", "posted", "void"]
    lines: list[JournalLine]


class DateRange(BaseModel):
    from_: date = Field(alias="from")
    to: date


class AccountBalance(BaseModel):
    bookId: uuid.UUID
    accountId: uuid.UUID
    asOf: date
    balance: str = Field(pattern=r"^-?(0|[1-9]\d*)(\.\d+)?$")
    currency: str = Field(pattern=r"^[A-Z]{3}$")
    normalSide: Literal["debit", "credit"] = "debit"


class AccountPage(BaseModel):
    items: list[Account]
    nextCursor: str | None


EntryStatus = Literal["draft", "posted", "void"]


class AppliedFilters(BaseModel):
    dateFrom: date
    dateTo: date
    accountId: uuid.UUID | None = None
    status: list[EntryStatus] = Field(default_factory=list)
    currency: str | None = None
    search: str | None = None


class JournalTotals(BaseModel):
    debits: MoneyTotal
    credits: MoneyTotal


class JournalPage(BaseModel):
    period: DateRange
    appliedFilters: AppliedFilters
    items: list[JournalEntry]
    totals: JournalTotals
    nextCursor: str | None


class TrialBalanceRow(BaseModel):
    accountId: uuid.UUID
    code: str
    name: str
    openingDebit: MoneyTotal
    openingCredit: MoneyTotal
    periodDebits: MoneyTotal
    periodCredits: MoneyTotal
    closingDebit: MoneyTotal
    closingCredit: MoneyTotal


class TrialBalanceTotals(BaseModel):
    openingDebits: MoneyTotal
    openingCredits: MoneyTotal
    periodDebits: MoneyTotal
    periodCredits: MoneyTotal
    closingDebits: MoneyTotal
    closingCredits: MoneyTotal
    balanced: bool


class TrialBalance(BaseModel):
    period: DateRange
    currency: str = Field(pattern=r"^[A-Z]{3}$")
    rows: list[TrialBalanceRow]
    totals: TrialBalanceTotals


class ReportLine(BaseModel):
    accountId: uuid.UUID
    code: str
    name: str
    amount: MoneyTotal


class IncomeStatement(BaseModel):
    period: DateRange
    currency: str = Field(pattern=r"^[A-Z]{3}$")
    income: list[ReportLine]
    costs: list[ReportLine]
    expenses: list[ReportLine]
    netResult: MoneyTotal


def create_app() -> FastAPI:
    app = FastAPI(
        title="Book-01 Accounting Read API (proposal)",
        version="0.1.0-draft",
        description="Proposed read-only contract for Part 2A. Draft for owner/backend review; not yet the repository's canonical API and not a substitute for server implementation.",
    )

    @app.exception_handler(HTTPException)
    async def handle_http_error(_request: Request, exc: HTTPException) -> JSONResponse:
        content = exc.detail if isinstance(exc.detail, dict) else error("request_error", str(exc.detail))
        return JSONResponse(status_code=exc.status_code, content=content, headers=exc.headers)

    @app.exception_handler(RequestValidationError)
    async def handle_validation_error(_request: Request, exc: RequestValidationError) -> JSONResponse:
        # Deliberately return field locations only; never echo submitted values or auth headers.
        fields = [".".join(str(part) for part in item.get("loc", ())) for item in exc.errors()]
        return JSONResponse(status_code=422, content=error("validation_error", "Request parameters are invalid.", {"fields": fields}))

    @app.exception_handler(Exception)
    async def handle_unexpected_error(_request: Request, _exc: Exception) -> JSONResponse:
        return JSONResponse(status_code=500, content=error("internal_error", "The request could not be completed."))

    async def get_http_client() -> AsyncIterator[httpx.AsyncClient]:
        timeout = httpx.Timeout(5.0, connect=2.0, read=5.0, write=5.0, pool=2.0)
        async with httpx.AsyncClient(timeout=timeout, follow_redirects=False, verify=True, limits=httpx.Limits(max_connections=20, max_keepalive_connections=10)) as client:
            yield client

    app.state.http_client_dependency = get_http_client

    bearer_scheme = HTTPBearer(auto_error=False, scheme_name="bearerAuth", bearerFormat="JWT")

    async def require_bearer(credentials: HTTPAuthorizationCredentials | None = Depends(bearer_scheme)) -> str:
        if credentials is None or credentials.scheme.lower() != "bearer":
            raise HTTPException(401, detail=error("unauthorized", "A valid bearer token is required."), headers={"WWW-Authenticate": "Bearer"})
        token = credentials.credentials
        if not token or token.strip() != token or any(c.isspace() for c in token):
            raise HTTPException(401, detail=error("unauthorized", "A valid bearer token is required."), headers={"WWW-Authenticate": "Bearer"})
        return token

    async def get_context(
        token: str = Depends(require_bearer),
        client: httpx.AsyncClient = Depends(get_http_client),
    ) -> tuple[Config, str, httpx.AsyncClient]:
        return Config.from_env(), token, client

    async def upstream(
        cfg: Config, token: str, client: httpx.AsyncClient, table: str,
        params: dict[str, Any],
    ) -> list[dict[str, Any]]:
        url = f"{cfg.supabase_url}/rest/v1/{table}"
        try:
            async with client.stream(
                "GET", url, params=params,
                headers={"apikey": cfg.anon_key, "Authorization": f"Bearer {token}", "Accept": "application/json"},
            ) as response:
                if response.is_redirect:
                    raise HTTPException(502, detail=error("upstream_unavailable", "Accounting data service returned an unsafe redirect."))
                if response.status_code == 401:
                    raise HTTPException(401, detail=error("unauthorized", "The bearer token was rejected."), headers={"WWW-Authenticate": "Bearer"})
                if response.status_code == 403:
                    raise HTTPException(403, detail=error("forbidden", "Access to this accounting resource is denied."))
                if response.status_code == 404:
                    raise HTTPException(404, detail=error("not_found", "Accounting resource was not found."))
                if response.status_code < 200 or response.status_code >= 300:
                    status = 502 if response.status_code >= 500 else 422
                    code, message = ("upstream_unavailable", "Accounting data service is unavailable.") if status == 502 else ("upstream_request_rejected", "Accounting data service could not process the request.")
                    raise HTTPException(status, detail=error(code, message))
                declared_length = response.headers.get("content-length")
                if declared_length and declared_length.isdigit() and int(declared_length) > MAX_UPSTREAM_RESPONSE_BYTES:
                    raise HTTPException(502, detail=error("upstream_invalid_response", "Accounting data service response exceeded the scaffold size limit."))
                chunks: list[bytes] = []
                size = 0
                async for chunk in response.aiter_bytes():
                    size += len(chunk)
                    if size > MAX_UPSTREAM_RESPONSE_BYTES:
                        raise HTTPException(502, detail=error("upstream_invalid_response", "Accounting data service response exceeded the scaffold size limit."))
                    chunks.append(chunk)
                body = b"".join(chunks)
        except HTTPException:
            raise
        except httpx.TimeoutException:
            raise HTTPException(504, detail=error("upstream_timeout", "Accounting data service timed out."))
        except httpx.RequestError:
            raise HTTPException(502, detail=error("upstream_unavailable", "Accounting data service is unavailable."))
        try:
            data = json.loads(body)
        except (ValueError, UnicodeDecodeError):
            raise HTTPException(502, detail=error("upstream_invalid_response", "Accounting data service returned an invalid response."))
        if not isinstance(data, list) or any(not isinstance(row, dict) for row in data):
            raise HTTPException(502, detail=error("upstream_invalid_response", "Accounting data service returned an invalid response."))
        return data

    async def authorize_book(ctx: tuple[Config, str, httpx.AsyncClient], book_id: uuid.UUID) -> tuple[Config, str, httpx.AsyncClient]:
        cfg, token, client = ctx
        rows = await upstream(cfg, token, client, "books", {"select": "id", "id": f"eq.{book_id}", "limit": "1"})
        # Supabase RLS intentionally makes absent and inaccessible books indistinguishable.
        if not rows:
            raise HTTPException(404, detail=error("not_found", "Book was not found or is not accessible."))
        return ctx

    def page_offset(cursor: str | None) -> int:
        if not cursor:
            return 0
        try:
            raw = base64.urlsafe_b64decode(cursor + "=" * (-len(cursor) % 4)).decode("ascii")
            if not raw.startswith("v1:"):
                raise ValueError
            offset = int(raw[3:])
        except (ValueError, UnicodeDecodeError, binascii.Error):
            raise HTTPException(422, detail=error("validation_error", "Cursor is invalid."))
        if offset < 0 or offset > MAX_CURSOR_OFFSET:
            raise HTTPException(422, detail=error("validation_error", "Cursor is outside the supported range."))
        return offset

    def next_cursor(offset: int, page_size: int, has_more: bool) -> str | None:
        if not has_more:
            return None
        value = base64.urlsafe_b64encode(f"v1:{offset + page_size}".encode()).decode().rstrip("=")
        return value

    def iso_date(value: str) -> date:
        try:
            parsed = date.fromisoformat(value)
        except (TypeError, ValueError):
            raise HTTPException(422, detail=error("validation_error", "Dates must use YYYY-MM-DD."))
        if parsed.isoformat() != value:
            raise HTTPException(422, detail=error("validation_error", "Dates must use YYYY-MM-DD."))
        return parsed

    def currency(value: str) -> str:
        if not CURRENCY_RE.fullmatch(value):
            raise HTTPException(422, detail=error("validation_error", "Currency must be a three-letter uppercase code."))
        if value != "USD":
            raise HTTPException(422, detail=error("unsupported_currency", "This migration supports USD only."))
        return value

    def decimal_value(value: Any) -> Decimal:
        raw = str(value)
        if len(raw) > MAX_DECIMAL_TEXT_LENGTH:
            raise HTTPException(502, detail=error("upstream_invalid_response", "Accounting data service returned an amount outside scaffold bounds."))
        try:
            amount = Decimal(raw)
        except (InvalidOperation, ValueError):
            raise HTTPException(502, detail=error("upstream_invalid_response", "Accounting data service returned an invalid amount."))
        if not amount.is_finite() or amount < 0 or amount.adjusted() > 120 or (amount != 0 and amount.adjusted() < -2):
            raise HTTPException(502, detail=error("upstream_invalid_response", "Accounting data service returned an invalid amount."))
        return amount

    def money(value: Decimal) -> str:
        if not value.is_finite():
            raise HTTPException(502, detail=error("upstream_invalid_response", "Accounting data service returned an invalid amount."))
        normalized = value.quantize(Decimal("0.01"))
        return format(normalized, "f")

    async def load_posted(
        ctx: tuple[Config, str, httpx.AsyncClient], book_id: uuid.UUID, end: date,
        start: date | None = None,
    ) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
        cfg, token, client = ctx
        params: dict[str, Any] = {
            "select": "id,book_id,entry_date,description,idempotency_key,currency_code,status",
            "book_id": f"eq.{book_id}", "status": "eq.posted",
            "entry_date": f"lte.{end.isoformat()}", "order": "entry_date.asc,id.asc",
            "limit": str(MAX_REPORT_ENTRIES + 1),
        }
        if start is not None:
            params["entry_date"] = f"gte.{start.isoformat()}"  # explicit ranges need separate caller logic
        entries = await upstream(cfg, token, client, "journal_entries", params)
        if start is not None:
            # PostgREST accepts a bounded explicit range; use an and filter rather than overwrite.
            params["entry_date"] = f"gte.{start.isoformat()}"
            params["and"] = f"(entry_date.lte.{end.isoformat()})"
            entries = await upstream(cfg, token, client, "journal_entries", params)
        if len(entries) > MAX_REPORT_ENTRIES:
            raise HTTPException(422, detail=error("result_limit_exceeded", "Report range exceeds the bounded scaffold limit."))
        if not entries:
            return entries, []
        ids = [str(row["id"]) for row in entries]
        lines: list[dict[str, Any]] = []
        # Keep URL and response sizes bounded; PostgREST filters carry no user-supplied expressions.
        for index in range(0, len(ids), 100):
            batch = ids[index:index + 100]
            rows = await upstream(cfg, token, client, "journal_entry_lines", {
                "select": "id,book_id,journal_entry_id,account_id,line_number,debit,credit,currency_code",
                "book_id": f"eq.{book_id}", "journal_entry_id": f"in.({','.join(batch)})",
                "order": "journal_entry_id.asc,line_number.asc", "limit": str(MAX_REPORT_LINES + 1 - len(lines)),
            })
            lines.extend(rows)
            if len(lines) > MAX_REPORT_LINES:
                raise HTTPException(422, detail=error("result_limit_exceeded", "Report range exceeds the bounded scaffold limit."))
        return entries, lines

    @app.get(f"{API_PREFIX}/books/{{bookId}}/accounts", operation_id="listBookAccounts", response_model=AccountPage, responses={**ERROR_401, **ERROR_403, **ERROR_404, **ERROR_422, **ERROR_UPSTREAM})
    async def list_accounts(
        bookId: uuid.UUID,
        active_only: bool = Query(default=True, alias="activeOnly"),
        page_size: int = Query(default=50, alias="pageSize", ge=1, le=MAX_PAGE_SIZE),
        cursor: str | None = None,
        ctx: tuple[Config, str, httpx.AsyncClient] = Depends(get_context),
    ) -> dict[str, Any]:
        book_id = bookId
        ctx = await authorize_book(ctx, book_id)
        offset = page_offset(cursor)
        cfg, token, client = ctx
        params: dict[str, Any] = {"select": "id,book_id,code,name,account_type,active", "book_id": f"eq.{book_id}", "order": "code.asc,id.asc", "limit": str(page_size + 1), "offset": str(offset)}
        if active_only:
            params["active"] = "eq.true"
        rows = await upstream(cfg, token, client, "accounts", params)
        has_more = len(rows) > page_size
        visible = rows[:page_size]
        items = []
        for row in visible:
            # The migration has no parent_account_id; null is the honest proposal-compatible value.
            items.append(Account(id=row["id"], code=row["code"], name=row["name"], type=row["account_type"], active=row["active"]))
        return {"items": items, "nextCursor": next_cursor(offset, page_size, has_more)}

    @app.get(f"{API_PREFIX}/books/{{bookId}}/journal-entries", operation_id="listJournalEntries", response_model=JournalPage, responses={400: {"model": ApiError}, **ERROR_401, **ERROR_403, **ERROR_404, **ERROR_422, **ERROR_UPSTREAM})
    async def list_journal_entries(
        bookId: uuid.UUID,
        date_from: date = Query(alias="dateFrom"), date_to: date = Query(alias="dateTo"),
        account_id: uuid.UUID | None = Query(default=None, alias="accountId"),
        status: list[EntryStatus] | None = Query(default=None),
        currency_code: str | None = Query(default=None, alias="currency", pattern=r"^[A-Z]{3}$"),
        search: str | None = Query(default=None, max_length=120),
        page_size: int = Query(default=50, alias="pageSize", ge=1, le=MAX_PAGE_SIZE),
        cursor: str | None = None,
        ctx: tuple[Config, str, httpx.AsyncClient] = Depends(get_context),
    ) -> dict[str, Any]:
        book_id = bookId
        ctx = await authorize_book(ctx, book_id)
        start, end = date_from, date_to
        if start > end:
            raise HTTPException(422, detail=error("validation_error", "dateFrom must be on or before dateTo."))
        cur = currency(currency_code) if currency_code else None
        requested_status = status or ["posted"]
        if not requested_status:
            raise HTTPException(422, detail=error("validation_error", "At least one entry status must be requested."))
        if search is not None and (not search.strip() or any(char in search for char in "%_(),*\\")):
            raise HTTPException(422, detail=error("validation_error", "Search must be non-empty and contain only literal text."))
        offset = page_offset(cursor)
        cfg, token, client = ctx
        params: dict[str, Any] = {
            "select": "id,book_id,entry_date,description,currency_code,status",
            "book_id": f"eq.{book_id}", "entry_date": f"gte.{start.isoformat()}",
            "and": f"(entry_date.lte.{end.isoformat()})", "order": "entry_date.asc,id.asc",
            "limit": str(MAX_REPORT_ENTRIES + 1),
        }
        db_statuses = sorted(set(requested_status))
        params["status"] = f"eq.{db_statuses[0]}" if len(db_statuses) == 1 else f"in.({','.join(db_statuses)})"
        if cur:
            params["currency_code"] = f"eq.{cur}"
        if search:
            params["description"] = f"ilike.*{search}*"
        entries = await upstream(cfg, token, client, "journal_entries", params)
        if len(entries) > MAX_REPORT_ENTRIES:
            raise HTTPException(422, detail=error("result_limit_exceeded", "Entry range exceeds the bounded scaffold limit."))
        lines: list[dict[str, Any]] = []
        ids = [str(row["id"]) for row in entries]
        for index in range(0, len(ids), 100):
            batch = ids[index:index + 100]
            chunk = await upstream(cfg, token, client, "journal_entry_lines", {
                "select": "id,book_id,journal_entry_id,account_id,line_number,description,debit,credit,currency_code",
                "book_id": f"eq.{book_id}", "journal_entry_id": f"in.({','.join(batch)})",
                "order": "journal_entry_id.asc,line_number.asc", "limit": str(MAX_REPORT_LINES + 1 - len(lines)),
            })
            lines.extend(chunk)
            if len(lines) > MAX_REPORT_LINES:
                raise HTTPException(422, detail=error("result_limit_exceeded", "Entry range exceeds the bounded scaffold limit."))
        if any(row.get("currency_code") != "USD" for row in entries) or any(line.get("currency_code") != "USD" for line in lines):
            raise HTTPException(422, detail=error("unsupported_currency", "Only USD journal data is supported."))
        lines_by_entry: dict[str, list[dict[str, Any]]] = {}
        for line in lines:
            lines_by_entry.setdefault(str(line["journal_entry_id"]), []).append(line)
        if account_id is not None:
            entries = [e for e in entries if any(str(line["account_id"]) == str(account_id) for line in lines_by_entry.get(str(e["id"]), []))]
        visible = entries[offset:offset + page_size]
        visible_ids = {str(e["id"]) for e in visible}
        page_lines = [line for line in lines if str(line["journal_entry_id"]) in visible_ids]
        debit_total = sum((decimal_value(line["debit"]) for line in page_lines), Decimal("0"))
        credit_total = sum((decimal_value(line["credit"]) for line in page_lines), Decimal("0"))
        items = [{
            "id": entry["id"], "date": entry["entry_date"], "description": entry["description"],
            "status": entry["status"],
            "lines": [{"id": line["id"], "accountId": line["account_id"], "description": line.get("description"),
                       "debit": money(decimal_value(line["debit"])), "credit": money(decimal_value(line["credit"])),
                       "currency": line["currency_code"]}
                      for line in lines_by_entry.get(str(entry["id"]), [])],
        } for entry in visible]
        applied = {"dateFrom": start.isoformat(), "dateTo": end.isoformat(), "accountId": str(account_id) if account_id else None,
                   "status": requested_status, "currency": cur, "search": search}
        return {"period": {"from": start.isoformat(), "to": end.isoformat()}, "appliedFilters": applied,
                "items": items, "totals": {"debits": {"amount": money(debit_total), "currency": cur or "USD"},
                "credits": {"amount": money(credit_total), "currency": cur or "USD"}},
                "nextCursor": next_cursor(offset, page_size, len(entries) > offset + page_size)}

    @app.get(f"{API_PREFIX}/books/{{bookId}}/accounts/{{accountId}}/balance", operation_id="getAccountBalance", response_model=AccountBalance, responses={**ERROR_401, **ERROR_403, **ERROR_404, **ERROR_422, **ERROR_UPSTREAM})
    async def account_balance(
        bookId: uuid.UUID, accountId: uuid.UUID, as_of: date = Query(alias="asOf"),
        currency_code: str = Query(alias="currency", pattern=r"^[A-Z]{3}$"),
        ctx: tuple[Config, str, httpx.AsyncClient] = Depends(get_context),
    ) -> dict[str, Any]:
        book_id, account_id = bookId, accountId
        ctx = await authorize_book(ctx, book_id)
        day, cur = as_of, currency(currency_code)
        cfg, token, client = ctx
        accounts = await upstream(cfg, token, client, "accounts", {"select": "id,book_id,account_type,currency_code", "book_id": f"eq.{book_id}", "id": f"eq.{account_id}", "limit": "1"})
        if not accounts:
            raise HTTPException(404, detail=error("not_found", "Account was not found or is not accessible."))
        if accounts[0].get("currency_code") != cur:
            raise HTTPException(422, detail=error("unsupported_currency", "Account currency does not match the requested currency."))
        entries, lines = await load_posted(ctx, book_id, day)
        posted_ids = {str(e["id"]) for e in entries}
        debit = sum((decimal_value(line["debit"]) for line in lines if str(line["journal_entry_id"]) in posted_ids and str(line["account_id"]) == str(account_id)), Decimal("0"))
        credit = sum((decimal_value(line["credit"]) for line in lines if str(line["journal_entry_id"]) in posted_ids and str(line["account_id"]) == str(account_id)), Decimal("0"))
        side = "debit" if accounts[0]["account_type"] in {"asset", "expense"} else "credit"
        balance = debit - credit if side == "debit" else credit - debit
        return {"bookId": str(book_id), "accountId": str(account_id), "asOf": day.isoformat(), "balance": money(balance), "currency": cur, "normalSide": side}

    @app.get(f"{API_PREFIX}/books/{{bookId}}/reports/trial-balance", operation_id="getTrialBalance", response_model=TrialBalance, responses={**ERROR_401, **ERROR_403, **ERROR_404, **ERROR_422, **ERROR_501, **ERROR_UPSTREAM})
    async def trial_balance(
        bookId: uuid.UUID, date_from: date = Query(alias="dateFrom"), date_to: date = Query(alias="dateTo"),
        currency_code: str = Query(alias="currency", pattern=r"^[A-Z]{3}$"),
        ctx: tuple[Config, str, httpx.AsyncClient] = Depends(get_context),
    ) -> dict[str, Any]:
        book_id = bookId
        ctx = await authorize_book(ctx, book_id)
        start, end, cur = date_from, date_to, currency(currency_code)
        if start > end:
            raise HTTPException(422, detail=error("validation_error", "dateFrom must be on or before dateTo."))
        cfg, token, client = ctx
        accounts = await upstream(cfg, token, client, "accounts", {"select": "id,book_id,code,name,account_type,currency_code", "book_id": f"eq.{book_id}", "order": "code.asc,id.asc", "limit": str(MAX_REPORT_ACCOUNTS + 1)})
        if len(accounts) > MAX_REPORT_ACCOUNTS:
            raise HTTPException(422, detail=error("result_limit_exceeded", "Book exceeds the bounded report account limit."))
        entries_before, lines_before = await load_posted(ctx, book_id, start.fromordinal(start.toordinal() - 1)) if start > date.min else ([], [])
        entries_period, lines_period = await load_posted_between(ctx, book_id, start, end, upstream)
        before_ids = {str(e["id"]) for e in entries_before}
        period_ids = {str(e["id"]) for e in entries_period}
        sums: dict[str, dict[str, Decimal]] = {str(a["id"]): {"od": Decimal(0), "oc": Decimal(0), "pd": Decimal(0), "pc": Decimal(0)} for a in accounts}
        for line, ids, debit_key, credit_key in [(line, before_ids, "od", "oc") for line in lines_before] + [(line, period_ids, "pd", "pc") for line in lines_period]:
            if str(line["journal_entry_id"]) not in ids:
                continue
            acc = str(line["account_id"])
            if acc not in sums:
                continue
            if line.get("currency_code") != cur:
                raise HTTPException(422, detail=error("unsupported_currency", "Mixed-currency reports are unsupported."))
            sums[acc][debit_key] += decimal_value(line["debit"])
            sums[acc][credit_key] += decimal_value(line["credit"])
        rows: list[dict[str, Any]] = []
        totals = {key: Decimal(0) for key in ("openingDebits", "openingCredits", "periodDebits", "periodCredits", "closingDebits", "closingCredits")}
        for account in accounts:
            value = sums[str(account["id"])]
            opening_net = value["od"] - value["oc"]
            closing_net = opening_net + value["pd"] - value["pc"]
            opening_debit, opening_credit = max(opening_net, Decimal(0)), max(-opening_net, Decimal(0))
            closing_debit, closing_credit = max(closing_net, Decimal(0)), max(-closing_net, Decimal(0))
            vals = {"openingDebits": opening_debit, "openingCredits": opening_credit, "periodDebits": value["pd"], "periodCredits": value["pc"], "closingDebits": closing_debit, "closingCredits": closing_credit}
            for key, amount in vals.items(): totals[key] += amount
            rows.append({"accountId": account["id"], "code": account["code"], "name": account["name"], "openingDebit": {"amount": money(opening_debit), "currency": cur}, "openingCredit": {"amount": money(opening_credit), "currency": cur}, "periodDebits": {"amount": money(value["pd"]), "currency": cur}, "periodCredits": {"amount": money(value["pc"]), "currency": cur}, "closingDebit": {"amount": money(closing_debit), "currency": cur}, "closingCredit": {"amount": money(closing_credit), "currency": cur}})
        return {"period": {"from": start.isoformat(), "to": end.isoformat()}, "currency": cur, "rows": rows, "totals": {key: {"amount": money(value), "currency": cur} for key, value in totals.items()} | {"balanced": totals["closingDebits"] == totals["closingCredits"]}}

    @app.get(f"{API_PREFIX}/books/{{bookId}}/reports/income-statement", operation_id="getIncomeStatement", response_model=IncomeStatement, responses={**ERROR_401, **ERROR_403, **ERROR_404, **ERROR_422, **ERROR_501, **ERROR_UPSTREAM})
    async def income_statement(
        bookId: uuid.UUID, date_from: date = Query(alias="dateFrom"), date_to: date = Query(alias="dateTo"),
        currency_code: str = Query(alias="currency", pattern=r"^[A-Z]{3}$"),
        ctx: tuple[Config, str, httpx.AsyncClient] = Depends(get_context),
    ) -> dict[str, Any]:
        book_id = bookId
        ctx = await authorize_book(ctx, book_id)
        start, end, cur = date_from, date_to, currency(currency_code)
        if start > end:
            raise HTTPException(422, detail=error("validation_error", "dateFrom must be on or before dateTo."))
        entries, _lines = await load_posted_between(ctx, book_id, start, end, upstream)
        if entries:
            raise HTTPException(501, detail=error("not_implemented", "The migration does not distinguish cost accounts from expenses, so an income statement cannot be classified faithfully."))
        return {"period": {"from": start.isoformat(), "to": end.isoformat()}, "currency": cur, "income": [], "costs": [], "expenses": [], "netResult": {"amount": "0.00", "currency": cur}}

    def proposal_openapi() -> dict[str, Any]:
        if app.openapi_schema:
            return app.openapi_schema
        schema = get_openapi(title=app.title, version=app.version, description=app.description, routes=app.routes)
        schema["servers"] = [{"url": "https://{host}/api/v1", "variables": {"host": {"default": "api.example.invalid"}}}]
        schema["security"] = [{"bearerAuth": []}]
        paths: dict[str, Any] = {}
        for path, path_item in schema["paths"].items():
            paths[path.removeprefix(API_PREFIX)] = path_item
        schema["paths"] = paths
        app.openapi_schema = schema
        return schema

    app.openapi = proposal_openapi
    return app


async def load_posted_between(ctx: tuple[Config, str, httpx.AsyncClient], book_id: uuid.UUID, start: date, end: date, upstream_fn: Any) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    cfg, token, client = ctx
    params = {
        "select": "id,book_id,entry_date,description,idempotency_key,currency_code,status",
        "book_id": f"eq.{book_id}", "status": "eq.posted",
        "entry_date": f"gte.{start.isoformat()}", "and": f"(entry_date.lte.{end.isoformat()})",
        "order": "entry_date.asc,id.asc", "limit": str(MAX_REPORT_ENTRIES + 1),
    }
    entries = await upstream_fn(cfg, token, client, "journal_entries", params)
    if len(entries) > MAX_REPORT_ENTRIES:
        raise HTTPException(422, detail=error("result_limit_exceeded", "Report range exceeds the bounded scaffold limit."))
    ids = [str(row["id"]) for row in entries]
    lines: list[dict[str, Any]] = []
    for index in range(0, len(ids), 100):
        batch = ids[index:index + 100]
        rows = await upstream_fn(cfg, token, client, "journal_entry_lines", {
            "select": "id,book_id,journal_entry_id,account_id,line_number,debit,credit,currency_code",
            "book_id": f"eq.{book_id}", "journal_entry_id": f"in.({','.join(batch)})",
            "order": "journal_entry_id.asc,line_number.asc", "limit": str(MAX_REPORT_LINES + 1 - len(lines)),
        })
        lines.extend(rows)
        if len(lines) > MAX_REPORT_LINES:
            raise HTTPException(422, detail=error("result_limit_exceeded", "Report range exceeds the bounded scaffold limit."))
    return entries, lines


app = create_app()
