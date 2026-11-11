# Frozen financial policies — product completion pass

Branch `claude/finsight-product-complete`, base `8c6e2478fce100fb9bd17c469e288248f5fffdb1`. Every policy below is
implemented in code, stamped with a version string in API responses, and covered by PostgreSQL integration tests.
`health-score-v1` (30/25/20/15/10) is unchanged as a formula. Its inputs now come from the transaction-driven
monthly projection `monthly-projection-v1` (section 10); the manual monthly entry path is retired.

**Monthly financial health is automatically derived from authoritative daily financial activity. Users do not
separately submit duplicate monthly financial statements solely for health scoring.**

## 1. One authority per financial fact

| Fact | Single authority | Readers |
|---|---|---|
| Cash, bank balances, postings, reversals | Dev A ledger (`financial_transactions`, `account_movements`) | P&L, goals, intelligence, facade |
| Receivables / payables, settlements, advances | Dev A obligations (`financial_obligations`, `obligation_payment_allocations`) | F04 lifecycle, aging, F05, F09, forecast |
| Commercial documents and their corrections | F04 (`commercial_documents`, `commercial_adjustments` V45) | C04 recognition, P&L |
| Stock quantity, value, WAC, COGS, project/sample material | F08 ledger (`inventory_movements`, `inventory_positions` V46) | P&L, F05, F09, break-even, facade |
| Project attribution of ledger facts | C05 (`project_financial_attributions`) | F05 profitability |
| Sample direct cost / recovery | F09 (`sample_actual_costs`, `sample_recovery_links` V47) | F05, P&L (via F08), reports |
| Commission cost | C08 `commission_earnings` | P&L, F05 (via C05) |
| Realized FX | C09 `fx_obligation_settlements` | P&L, F05 (via C05) |
| Category / cost behaviour (analysis metadata only) | `financial_classifications`, `category_cost_behaviors` (V48) | P&L categories, budgets, break-even |

## 2. `f08-wac-v1` — perpetual weighted average cost
* State per item `(Q, V)`; V stored at scale 4 (ledger scale). Quantities scale 6.
* Inbound (opening, receipt, adjustment-in) adds the exact supplied/sourced value (capitalization).
* Partial outbound value = `round(V × q / Q, 4, HALF_UP)` (the unified application convention; the Phase A draft was
  HALF_EVEN and is retained only as `WacPolicy.DRAFT` for historical preview evidence).
* An outbound that empties the item takes exactly `V` → depletion always ends at `(0, 0)`; no residual drift.
* Return to stock against an issue: proportional share of that issue's exact value; the last return takes the exact
  remaining residual. Supplier return / count loss: valued at current WAC.
* Reversal = exact negated deltas; refused if later movements make it impossible without negative stock or a
  non-zero empty value (`reversal_blocked_by_later_movements`), or if returns exist against the issue.
* No negative stock. No back-dating before the item's last movement. Item row lock serializes all movements.
* Capitalization sources: bill receipt (one effective receipt per bill line) or an un-reversed operating payment
  (bounded by the payment amount minus C05 and F09 claims). Stock actions default to the current date.

## 3. `pnl-accrual-v1` — profit and loss read model (no posting)
* Revenue = cash sales (`DIRECT_SALE_RECEIPT`) + recognized customer invoices (issue date) − effective credit notes.
* Cost of sales = F08 SALE + PROJECT + SAMPLE consumption (net of returns and reversals).
* Operating costs = operating payments − capitalized amounts − commission payouts/advances
  + recognized bills − capitalized bill receipts − debit notes + supplier-return stock value
  + recognized commission (C08) + internal use / wastage / count adjustments (net).
* Commission accounting date = the effective date of the collection allocation that earned it (the entry timestamp
  only when the earning has no source allocation).
* Realized FX below operating profit. Collections (`RECEIVABLE_COLLECTION`), bill settlements (`PAYABLE_SETTLEMENT`)
  and internal transfers are cash timing only and never appear. Voided obligations are excluded.

## 4. F04 corrections
* Credit note (invoice) / debit note (bill): append-only record + one non-cash `CREDIT_NOTE` allocation; the
  recognized obligation amount is never edited; note ≤ current outstanding; date between document date and today.
* Upward corrections are a new supplementary invoice/bill. Cancelling a note is compensating (status change).
* Void after recognition requires settlements and stock movements to be reversed first; it cancels effective notes,
  voids the obligation through `FinancialObligationService.voidObligation`, then voids the document. Nothing is deleted.

## 5. `f05-profitability-v1`
* Revenue: linked recognized invoices (allocated share, credit notes pro rata) + C05-attributed cash sales.
* Costs: F08 project material (PROJECT + project-tagged SALE), C05 attributed operating payments by category,
  linked recognized bills net of debit notes and capitalized share, recognized commission, linked samples' net cost.
* Excluded with reasons: settlement-sourced attributions (bill carries the cost), collection attributions (invoice
  carries the revenue), attributions of capitalized payments (cost arrives via F08).
* Overhead counts only when explicitly attributed. Missing budget/estimate is null, never zero.

## 6. `f09-actuals-v1`
* Actual cost = F08 SAMPLE consumption + attributed un-reversed operating payments; costs whose source payment was
  later reversed are excluded and reported. One payment's total claim across samples, projects and capitalization
  never exceeds the payment (shared advisory lock with C05 and F08).
* Recovery = linked share of recognized invoices (≤ current net); collected/outstanding from the AR obligation.
* FREE → no recovery allowed; PAID/PARTIAL statuses derived from recovery vs actual cost. Contribution = recovery − cost.

## 7. `break-even-v1`
* Policy-variable: inventory cost of sales and recognized commission. Policy-fixed: inventory losses/internal use.
* Every other expense needs an explicit FIXED/VARIABLE classification; otherwise UNAVAILABLE with the exact list.
* BER = F × R / C computed in one division, HALF_UP scale 2. Zero/negative contribution → NOT_ACHIEVABLE.
* Unit break-even only when exactly one item was sold through stock and all revenue is classified PRODUCT_SALES.

## 8. Copilot `copilot-v1`
* Read-only tool registry; no identity/tenant arguments; per-call role check; closed argument schemas.
* Final answers are delivered only if every number appears in tool evidence or the user's question, citations exist,
  and no links are present; otherwise the evidence-only deterministic rendering is returned (`ungrounded_answer`).

## 9. Retired from authoritative scope (explicit product decisions)
* Tax/GST computation on invoices — requires a jurisdiction policy; documents carry tax-inclusive line amounts.
* Manufacturing BOM / WIP assembly conversion — production is tracked as F09 production-cost activities consuming
  F08 stock.
* Foreign-currency quoting on F06 estimates and F04 documents — FX applies at settlement through C09.
* Persisted report schedules / automated report delivery — on-demand pack, CSV and print cover the product need.

## 10. `monthly-projection-v1` — transaction-driven monthly model (V52)

**Users operate FinSight through ordinary daily financial workflows. Monthly financial records and health-score inputs
are automatically derived from authoritative recognized activity. Users do not separately enter duplicate monthly
financial totals solely for health scoring.**

`monthly_records` is no longer an independent, user-maintained source of truth. Every month inside a business's ledger
coverage is a `DERIVED` materialisation produced by one deterministic projector (`MonthlyFinancialProjector`) and kept
in step by `MonthlyProjectionService`. Pre-V52 rows remain as labelled `LEGACY_MANUAL` history; any of them inside
coverage is replaced by the derived month (one truth per month).

### Source map (every `MonthlyRecord` field)

| Field | Basis | Authoritative source | Period attribution |
|---|---|---|---|
| `revenue` | Accrual | `pnl-accrual-v1`: cash sales + recognized customer invoices − effective credit notes | transaction effective date; invoice issue (accounting) date; note date |
| `cogs` | Accrual | F08 consumption: sale + project + sample material, net of returns/reversals | movement date. **Null** when no inventory movement exists on/before month end |
| `operatingExpenses` | Accrual | operating payments − capitalized stock − commission payouts + recognized bills − capitalized bill receipts − debit notes + supplier-return value + recognized commission + stock losses (net) | payment date; bill issue date; note date; movement date; earning collection date |
| `cashInflow` / `cashOutflow` | Cash | posted `account_movements` on PKR accounts in the month; internal transfers and reversed/reversal pairs excluded | movement effective date |
| `cashBalanceEom` | Snapshot | PKR account opening balances + movements up to month end (reversal pairs excluded) | as of month end |
| `receivablesOutstanding` / `payablesOutstanding` | Snapshot | non-voided PKR obligations issued by month end − EFFECTIVE allocations (cash, advance, credit/debit note) dated by month end | as of month end |
| `inventoryValue` | Snapshot | F08 carrying value (`f08-wac-v1`) at month end; **null** when no stock was ever recorded | as of month end |
| `loanOutstanding`, `interestExpense` | Supplemental (manual) | `monthly_financing_inputs` for that month (no ledger authority for loans yet); null when not entered | the entered month only (balances never carried forward) |
| `financingType` | Supplemental (manual) | latest `monthly_financing_inputs` row on/before the month, else `none` | carried forward (a classification, not a figure) |

Realized FX stays in the P&L below operating profit and is **not** part of these operating fields (noted on the month).
Foreign-currency accounts/obligations are excluded from PKR cash/AR/AP and flagged in `coverage_notes`.

### Accrual vs cash, and no double counting
* Performance fields (revenue, COGS, operating expenses) are accrual; cash fields are posted cash movements; balances
  are month-end snapshots that are never summed across months.
* A customer collection or supplier settlement changes cash and AR/AP only — never revenue or cost again.
* A stock purchase is capitalized (inventory value) and reaches COGS only when issued; project/sample material appears
  once through F08 consumption.
* Drafts (unfinalized/unrecognized documents, record drafts, scheduled recurring occurrences) never feed the projection.

### Coverage, late entries and recomputation
* Coverage starts at the earliest PKR cash account opening month (a month, once derived, stays derived) and runs to the
  current month. A month with no activity is a known-zero month, not a gap.
* V52 row triggers on every authoritative source table (accounts, transactions, movements, obligations, allocations,
  F04 adjustments, F08 movements, commission earnings/payouts/advances, FX settlements, financing inputs) increment the
  business's `dirty_seq`. Tenants are isolated: one business's activity never invalidates another.
* A refresh (on reads of monthly/score/report/dashboard/workspace/intelligence/planning/Copilot/AI/Zakat/search/cash-flow
  paths, `POST /api/records/monthly/recalculate`, or the background sweeper every 5 minutes) takes a per-business
  advisory lock, recomputes **every** month in coverage, writes only months whose SHA-256 fingerprint changed, and
  clears the dirty flag only if no writer committed meanwhile (compare-and-set on `dirty_seq`).
* Late entry: an invoice entered on 5 December with accounting date 28 November changes November's revenue and
  month-end receivables (and every later month-end snapshot), not December's revenue.
* Reversal: a reversed transaction and its reversal are excluded from every month, so the original month is restated
  rather than left stale. A voided obligation is excluded from every month.
* Rebuilding twice without source change writes nothing and rescores nothing (idempotent, byte-identical).

### Read-path concurrency and performance
* A read first does one indexed lookup of `monthly_projection_state`; when the projection is current it returns
  `FRESH` without taking any lock or writing anything, so normal navigation never rebuilds.
* When stale, the request takes the business's transaction-scoped advisory lock (`monthly-projection:<businessId>`)
  and re-checks the state under the lock. Simultaneous page loads for the same business therefore queue behind the first
  one: exactly one performs the rebuild (`REBUILT`), the others find it already current (`COALESCED`) and write nothing.
  Different businesses use different lock keys and never wait on each other.
* A failed rebuild rolls back completely, records `last_error`, leaves the business dirty (`FAILED`) and is retried on
  the next read or sweep; a later success clears the error. Read paths never fail because of a refresh error.
* `FreshnessOutcome` is an internal service result for verification and diagnostics, not an HTTP API.
* Proven by `MonthlyIntegrityPostgreSqlIT` (5 queued readers → 1 `REBUILT` + 4 `COALESCED`, each changed month written
  once, unchanged months untouched; cross-business independence under a held lock; injected-failure recovery; `FRESH`
  while the lock is held elsewhere) and by the browser suite (72 page loads with no rebuild: `lastProjectedAt` unchanged).

### Score recomputation
* `health-score-v1` formula, weights and calculators are unchanged. After a refresh, every month from the earliest
  changed month to the current month is rescored (trend/variance windows look back six months), plus any month in
  coverage that has no score yet.
* Months that cannot be scored (no business profile, or no component with data) are skipped without failing the
  refresh (`ScoringService.recalculateIfScorable`).
* The current month is scored provisionally on month-to-date figures (`period_complete = false` on the month).

### Null vs zero
* Zero is known zero (no invoices this month, nothing outstanding). Null is unknown: COGS and inventory without an
  inventory ledger, loan and interest without a supplemental input. The projector never converts unknown to zero.

### Manual monthly entry
* `POST /api/records/monthly` and manual overrides on `POST /api/finance/ledger-bridge/apply-projection` return
  `409 monthly_figures_derived`. OCR document confirmation stores reviewed evidence only and never changes a month.
* The only manual monthly inputs are supplemental financing facts (`PUT /api/records/monthly/financing/{month}`,
  optimistic `expectedVersion`, audited), labelled `SUPPLEMENTAL_MANUAL` in the source map
  (`GET /api/records/monthly/{month}/sources`).

### Consistency
Monthly records, the F12B report, the dashboard, Financial Intelligence, the Copilot P&L tool, budgets/goals and the
health score read the same chain (`pnl-accrual-v1` + ledger + obligations + F08). `TransactionDrivenMonthlyPostgreSqlIT`
asserts one month shows the same revenue in the P&L, the monthly record, the report and the source map, and that the
dashboard and score read the derived months. The legacy C02 cash-basis preview (`GET /api/finance/ledger-bridge/
monthly-projection`, `c02-bridge-v1`) remains available as an explicitly cash-basis view.
