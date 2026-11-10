# Frozen financial policies — product completion pass

Branch `claude/finsight-product-complete`, base `8c6e2478fce100fb9bd17c469e288248f5fffdb1`. Every policy below is
implemented in code, stamped with a version string in API responses, and covered by PostgreSQL integration tests.
`health-score-v1` (30/25/20/15/10) and Developer A's cash-basis monthly bridge `c02-bridge-v1` are unchanged.

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
