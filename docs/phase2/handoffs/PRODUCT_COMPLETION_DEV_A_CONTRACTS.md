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

## A5. Optional future bridge contract (NOT implemented — Developer A decision)
`c02-bridge-v1` remains cash-basis. If accrual figures should feed `MonthlyRecord`/health score, the bridge would
read `ProfitAndLossService.statement(...)` (revenue, cost of sales, operating expenses) and
`InventoryReadService.valueAsOf(...)` (inventory value) and exclude capitalized purchase payments from operating
disbursements. This changes the scored figures and therefore requires Developer A's explicit methodology decision.
