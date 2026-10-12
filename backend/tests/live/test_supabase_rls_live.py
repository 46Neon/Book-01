"""Optional, read-only smoke tests against a real Supabase dev project.

Enable explicitly with SUPABASE_LIVE_TESTS=1. Never run against production.
"""
from __future__ import annotations

import os
from urllib.parse import urlsplit
import uuid

import httpx
import pytest

pytestmark = pytest.mark.skipif(
    os.getenv("SUPABASE_LIVE_TESTS") != "1",
    reason="Opt-in live Supabase tests; set SUPABASE_LIVE_TESTS=1 to run",
)


def _settings() -> tuple[str, str, str, str, str, str]:
    names = (
        "SUPABASE_URL",
        "SUPABASE_PUBLISHABLE_KEY",
        "SUPABASE_TEST_USER_A_JWT",
        "SUPABASE_TEST_USER_B_JWT",
        "SUPABASE_TEST_BOOK_A_ID",
        "SUPABASE_TEST_BOOK_B_ID",
    )
    values = {name: os.getenv(name, "").strip() for name in names}
    missing = [name for name, value in values.items() if not value]
    if missing:
        pytest.fail(
            "Missing required environment variables: " + ", ".join(missing)
            + ". Set them in a secure local environment; values are never printed.",
            pytrace=False,
        )

    project_url = values["SUPABASE_URL"].rstrip("/")
    parsed = urlsplit(project_url)
    if parsed.scheme != "https" or not parsed.netloc or parsed.path or parsed.query or parsed.fragment:
        pytest.fail("SUPABASE_URL must be the HTTPS project origin, without a path or query.", pytrace=False)

    for name in ("SUPABASE_TEST_BOOK_A_ID", "SUPABASE_TEST_BOOK_B_ID"):
        try:
            values[name] = str(uuid.UUID(values[name]))
        except ValueError:
            pytest.fail(f"{name} must be a UUID.", pytrace=False)
    if values["SUPABASE_TEST_BOOK_A_ID"] == values["SUPABASE_TEST_BOOK_B_ID"]:
        pytest.fail("The two test book IDs must be different.", pytrace=False)

    return (
        project_url,
        values["SUPABASE_PUBLISHABLE_KEY"],
        values["SUPABASE_TEST_USER_A_JWT"],
        values["SUPABASE_TEST_USER_B_JWT"],
        values["SUPABASE_TEST_BOOK_A_ID"],
        values["SUPABASE_TEST_BOOK_B_ID"],
    )


def _get_json(path: str, token: str, params: dict[str, str] | None = None):
    project_url, publishable_key, *_ = _settings()
    headers = {
        "apikey": publishable_key,
        "Authorization": f"Bearer {token}",
        "Accept": "application/json",
    }
    try:
        response = httpx.get(
            f"{project_url}{path}",
            headers=headers,
            params=params,
            timeout=10.0,
            follow_redirects=False,
        )
    except httpx.HTTPError as exc:
        pytest.fail(
            f"Supabase request failed ({type(exc).__name__}); response details are omitted.",
            pytrace=False,
        )
    assert response.status_code == 200, f"Expected HTTP 200 for {path}; got {response.status_code}."
    try:
        return response.json()
    except ValueError:
        pytest.fail(f"Supabase returned non-JSON data for {path}; response body is omitted.", pytrace=False)


def test_both_auth_sessions_are_valid() -> None:
    _, _, token_a, token_b, _, _ = _settings()
    for label, token in (("A", token_a), ("B", token_b)):
        user = _get_json("/auth/v1/user", token)
        assert isinstance(user, dict) and user.get("id"), f"Auth user {label} did not return a user ID."


def test_books_and_accounts_are_isolated_by_rls() -> None:
    _, _, token_a, token_b, book_a, book_b = _settings()
    cases = (("A", token_a, book_a, book_b), ("B", token_b, book_b, book_a))

    for label, token, own_book, other_book in cases:
        own_rows = _get_json(
            "/rest/v1/books", token, {"select": "id", "id": f"eq.{own_book}"}
        )
        other_rows = _get_json(
            "/rest/v1/books", token, {"select": "id", "id": f"eq.{other_book}"}
        )
        assert own_rows == [{"id": own_book}], f"User {label} cannot read its own test book."
        assert other_rows == [], f"RLS exposed the other user's book to user {label}."

        own_accounts = _get_json(
            "/rest/v1/accounts", token, {"select": "id", "book_id": f"eq.{own_book}"}
        )
        other_accounts = _get_json(
            "/rest/v1/accounts", token, {"select": "id", "book_id": f"eq.{other_book}"}
        )
        assert own_accounts, f"Test book for user {label} needs at least one account."
        assert other_accounts == [], f"RLS exposed another book's accounts to user {label}."
