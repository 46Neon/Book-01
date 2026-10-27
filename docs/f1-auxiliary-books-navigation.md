# F1 usability milestone: auxiliary books first

This scoped milestone improves the mobile workflow without changing the ledger schema or accounting records. The Android app remains Kotlin, Jetpack Compose, and Room. Sales and purchases are shown as distinct auxiliary books at the start of navigation; each list filters to its own document kind, offers a matching invoice action and counterparty shortcut, and seeds the invoice form with that book's kind. The generated Journal and General Ledger remain separate, explicitly book-oriented destinations.

## Integrity and scope

- Existing invoice records remain invoice records; no income/expense journal entries are relabeled or inferred as invoices.
- Invoice accounting, configurable tax inputs, posting, settlements, and immutable/auditable accounting data remain backed by the existing F1 domain and repository. This UI change adds no Room schema or migration.
- Generic journal-entry capture remains available in the Journal, but is no longer the first task presented on app launch.
- Rates and purchase-tax treatment are parameters/hypotheses, not legal determinations. No country-specific compliance or tax-rate claim is made.

## Remaining roadmap (not implemented here)

1. Complete F1: configurable chart of accounts and master data, opening balances, invoice correction histories, robust customer/vendor statements and aging/due-date views, and printable/exportable period books. Verify all accounting rules with an accountant before release.
2. F2: spreadsheet-like responsive table UX, search/filter/sort/totals, verify trial balance and financial statements, XLSX/PDF exports, and one company/year book boundary.
3. F3: balanced manual journal entries and bank import/reconciliation.
4. F4: reviewed invoice recognition, configurable tax/withholding profiles and filing calendar, only after expert validation.
5. F5: accountant acceptance, release checks and signed release. Do not claim tax/legal conformity without this review.

The existing GitHub Actions workflow already limits the debug APK artifact to two-day retention and only uploads it for main/manual builds. No workflows, releases, or artifacts were deleted or otherwise changed in this milestone.
