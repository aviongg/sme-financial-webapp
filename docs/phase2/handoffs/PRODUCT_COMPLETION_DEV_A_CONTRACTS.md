# Developer A integration contracts — product completion pass

All items below are already implemented on `claude/finsight-product-complete` as the smallest additive change
and covered by tests. Developer A should review/accept each during final reconciliation; none redesigns a ledger,
posting calculation, monthly bridge or account calculation.

## A1. `CREDIT_NOTE` allocation type (V45) — REVIEW REQUIRED
* `obligation_payment_allocations.allocation_type` accepts `CREDIT_NOTE` with `commercial_adjustment_id` set and no
  transaction/advance. `AllocationType.CREDIT_NOTE` added; obligation detail labels it "Credit/debit note adjustment".
* Effect: every existing reader using `original − effective allocations` (aging, settlement, forecast, funding,
  facade, FX settlement) sees the reduced outstanding with no code change. Commission earning logic filters `CASH`
  only, so notes never create commission. No ledger transaction, account movement or bridge input is created.

## A2. Cross-authority attribution guard in C05 — REVIEW REQUIRED
* `ProjectFinancialAttributionService` subtracts F09 sample claims and F08 capitalized amounts from a payment's
  remaining eligible amount and takes `pg_advisory_xact_lock(hashtext('ledger-tx:' || id))` (same lock as F08/F09).

## A3. C05 costing defect fixes — REVIEW REQUIRED
* `ProjectFinancialCostingService`: planned values are `null` when no approved budget exists; source-discovery
  failure is logged and the unattributed amount becomes `null` (no silent zero).

## A4. Platform touches — REVIEW REQUIRED
* `SecurityConfig`: `/api/whatsapp/webhook` is CSRF-exempt and `permitAll` (authenticated by HMAC signature only).
* `ModuleWebConfiguration`: the webhook is excluded from the session-based module guard (checked per business inside).
* `GlobalExceptionHandler`: unhandled exceptions are logged server-side (response body unchanged).
* `AuditEventType`: new values for inventory, adjustments, samples, classification, budgets, goals, Copilot, WhatsApp.
* `FinancialQueryFacade`: `getMarginDrivers` now returns data; five new read tools (P&L, inventory, budgets/goals,
  break-even, project profitability) injected optionally via setter so existing constructors/tests are unchanged.

## A4b. Pre-existing Developer-A defects fixed (read-side only) — REVIEW REQUIRED
* `FinancialQueryFacadeService.getProjectFinancials/getProjectFundingGap`: tenant-scoped project existence pre-check.
  Before, a missing project made an inner `@Transactional` call mark the read transaction rollback-only, so the
  typed UNAVAILABLE envelope was lost (`UnexpectedRollbackException`, HTTP 500).
* `MonthlyLedgerProjectionService` draft summary: only drafts dated inside the projected month are counted (all open
  drafts previously leaked into every month, which made `MonthlyLedgerProjectionPostgreSqlIT` fixture C date-dependent).

## A5. Transaction-driven monthly model (`monthly-projection-v1`, V52) — IMPLEMENTED, REVIEW REQUIRED
The earlier instruction to keep the monthly bridge semantics was withdrawn: the monthly record is now derived from
authoritative daily records (see policies section 10). `health-score-v1` formula/weights/calculators are unchanged.
Developer-A-owned touches:
* **V52 triggers** `trg_<table>_monthly_projection_dirty` (AFTER INSERT/UPDATE/DELETE, row level) on
  `financial_accounts`, `financial_transactions`, `account_movements`, `financial_obligations`,
  `obligation_payment_allocations`, `commission_earnings`, `commission_payouts`, `commission_advances`,
  `fx_obligation_settlements` (plus F04/F08 tables). They only upsert `monthly_projection_state` (never touch the
  source row); deletes only flag an existing state row, so cascading business deletes still work.
* **`LedgerMonthlyBridgeService.applyLedgerProjection`**: now runs the projector; manual COGS/inventory/loan/interest/
  financing overrides return `409 monthly_figures_derived`. `getMonthlyProjection` (cash-basis preview) is unchanged.
* **`MonthlyRecordService`**: manual save and OCR document contribution removed; `saveMonthlyRecord` refuses with 409.
  `POST /api/records/monthly` therefore returns 409. `document_financial_contributions` is retained as history only.
* **`DocumentConfirmationService`**: confirmation stores reviewed evidence (status, confirmed JSON, linked month) and
  never writes a monthly record.
* **`ScoringService`**: new `recalculateIfScorable` (same computation; returns null instead of throwing for an
  unscorable month so the projector transaction is never marked rollback-only). `calculateAndSaveScore` unchanged.
* **`MonthlyRecord` entity/DTO**: new read-only metadata (`source`, `projectionVersion`, `periodComplete`,
  `projectedAt`, `coverageNotes`).
* **Reversal semantics in the projection**: a reversed transaction and its reversal are excluded from every month's
  flows and month-end cash (restatement, consistent with `c02-bridge-v1` flow treatment and `pnl-accrual-v1`). Dev A's
  account balance screens are unaffected (they still show raw movements).
* **Read-path freshness locking**: `MonthlyProjectionService.refreshIfStale` takes a per-business transaction-scoped
  advisory lock (`hashtext('monthly-projection:' || business_id)`) only when stale and re-checks under the lock, so
  concurrent reads coalesce into one rebuild. Dev A writers must not call `refresh`/`refreshIfStale` inside a
  transaction that has already written an authoritative source row (the V52 trigger row-locks the state row; the
  `REQUIRES_NEW` refresh would wait on it). The bridge apply path satisfies this (it writes nothing before refreshing).
* **Tests rewritten to the new contract** (manual seeding replaced by posted activity or legacy rows):
  `LedgerMonthlyBridgePostgreSqlIT`, `MonthlyIntegrityPostgreSqlIT`, `MonthlyLedgerProjectionPostgreSqlIT`
  (refresh before the read-only snapshot), `TenantIsolationPostgreSqlIT`, `MvpCoreJourneyIT`, `CoreJourneyClosureIT`.

Developer A should confirm: (1) the reversal-as-restatement rule for month-end cash; (2) PKR-only cash/AR/AP with
foreign-currency exclusion flagged (FX translation of balances is a future policy); (3) whether a loan/financing ledger
should later replace the supplemental `monthly_financing_inputs`.
