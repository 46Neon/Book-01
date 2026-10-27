# F1 usability milestone: auxiliary books first

This scoped milestone improves the mobile workflow without changing the ledger schema or accounting records. The Android app remains Kotlin, Jetpack Compose, and Room. Sales and purchases are shown as distinct auxiliary books at the start of navigation; each list filters to its own document kind, offers a matching invoice action and counterparty shortcut, and seeds the invoice form with that book's kind. The generated Journal and General Ledger remain separate, explicitly book-oriented destinations.

## Integrity and scope

- Existing invoice records remain invoice records; no income/expense journal entries are relabeled or inferred as invoices.
- Invoice accounting, configurable tax inputs, posting, settlements, and immutable/auditable accounting data remain backed by the existing F1 domain and repository. This UI change adds no Room schema or migration.
- Generic journal-entry capture remains available in the Journal, but is no longer the first task presented on app launch.
- Rates and purchase-tax treatment are parameters/hypotheses, not legal determinations. No country-specific compliance or tax-rate claim is made.

## Remaining roadmap (not implemented here)

1. Complete F1: configurable chart of accounts and master data, opening balances, invoice correction histories, robust customer/vendor statements beyond the current balance summary, and printable/exportable period books. Verify all accounting rules with an accountant before release.
2. F2: spreadsheet-like responsive table UX, search/filter/sort/totals, verify trial balance and financial statements, XLSX/PDF exports, and one company/year book boundary.
3. F3: balanced manual journal entries and bank import/reconciliation.
4. F4: reviewed invoice recognition, configurable tax/withholding profiles and filing calendar, only after expert validation.
5. F5: accountant acceptance, release checks and signed release. Do not claim tax/legal conformity without this review.

The existing GitHub Actions workflow already limits the debug APK artifact to two-day retention and only uploads it for main/manual builds. No workflows, releases, or artifacts were deleted or otherwise changed in this milestone.

## Invoice balance status (accounting-facing slice)

Invoice cards now display a derived balance status alongside the settled and outstanding amounts: Paid, Partial, Open, or Overdue. The status is calculated from the invoice total, persisted settlements, and the due date only when one was explicitly entered. A missing or malformed due date does not imply overdue; a fully settled invoice remains Paid, and any remaining balance after a passed due date is Overdue even when partially paid. This is an informational view only: it creates no accounting entries and changes no persisted invoice or settlement data. The current settlement UI and ledger continue to use the existing USD storage model; broader currency support remains future work.

## Filter by collection/payment state

The sales and purchase auxiliary books now expose display-only filters for all invoices, open balances, partial settlements, overdue balances, and paid invoices. Each invoice's filter state is derived from the same persisted settlement flow and explicit due date used for its status label. The filter does not alter, post, or hide records from the ledger; changing tabs or filters only changes which cards are currently visible. Unit tests cover that each filter maps exclusively to its corresponding derived state.

## Customer and supplier balance overview (derived, no new accounting records)

Each auxiliary book now provides a per-customer or per-supplier balance summary sourced from saved invoice totals and their saved initial/later settlements. It shows remaining and overdue amounts as of the current device date; past-due amounts require an explicitly stored due date and an outstanding balance. Fully settled invoices contribute zero, missing or malformed dates are not treated as overdue, and aggregation uses exact integer minor units with overflow/over-settlement rejection. The overview is derived only: no ledger postings or database schema changes are introduced. The summary covers the currently selected auxiliary book kind and is independent of the invoice issue-period filter.
