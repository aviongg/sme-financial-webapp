# Proposal: Multi-Currency Effects on Monthly Health (for accounting review)

| Item | Value |
| :--- | :--- |
| Status | **PROPOSAL — not implemented.** No code, migration or score change accompanies this document. |
| Baseline | `153e94b` (`monthly-projection-v1`, `pnl-accrual-v1`, `health-score-v1`) |
| Related finding | `PRODUCTION_CLOSURE_AUDIT.md` P2-04 |

## 1. Current behaviour (verified in source)

| Monthly field | Source today | Foreign-currency activity |
| :--- | :--- | :--- |
| `revenue`, `operating_expenses`, `cogs` | `ProfitAndLossService` | Cash sales/expenses use `base_amount_pkr` (converted at posting). FinSight invoices/bills are PKR-only by schema. **Manually declared foreign obligations are not revenue/cost** (P&L reads only `CUSTOMER_INVOICE`/`SUPPLIER_BILL` sources). Realized FX is excluded from operating figures. |
| `cash_inflow`, `cash_outflow`, `cash_balance_eom` | PKR accounts only | Foreign accounts are excluded entirely; note `foreign_currency_accounts_excluded_from_cash`. |
| `receivables_outstanding`, `payables_outstanding` | PKR obligations only | Foreign obligations excluded; note `foreign_currency_obligations_excluded_from_ar_ap`. |

Consequence: for an exporter holding a USD account and USD receivables, health-score-v1 sees PKR revenue from
converted cash sales but no USD cash and no USD receivables — liquidity and collection components are understated, and
the notes are not surfaced as a score caveat.

## 2. Options

**Option A — Keep exclusion, surface it (lowest risk).** No figure changes. When either exclusion note is present,
the score/explanation APIs return a `coverage: PARTIAL_FOREIGN_CURRENCY_EXCLUDED` caveat with the excluded native
totals per currency; UI shows it beside the score. No methodology change.

**Option B — Translate balances at month-end closing rate (recommended for review).**
- Cash: foreign account month-end native balance × closing rate (PKR per unit) for that month.
- AR/AP: open foreign obligations' native outstanding × closing rate.
- Flows: foreign movements translated at the transaction-date rate already stored with the movement where present
  (`base_amount_pkr`), otherwise the monthly average rate.
- Unrealized revaluation differences stay **out** of operating revenue/expenses (reported as a separate line), so
  profitability components do not move with exchange rates.
- Requirements: an authoritative, dated rate source per currency (manual entry with provenance or a provider feed),
  a missing-rate rule (month stays `period_complete=false` with note `fx_rate_missing`, foreign amounts excluded),
  currency-specific scales (JPY 0, BHD/KWD/OMR 3) before translation, rate precision 6.
- Projection: rate rows must become a V52 dirty-trigger source (a new migration, V53+), and the fingerprint must
  include the rates used.

**Option C — Book-value basis.** Use `fx_obligation_recognition_evidence.book_value` for AR/AP (historical rate) and
ignore rate movements until settlement. Simple and deterministic, but cash still needs a rate; mixing historical AR
with translated cash is inconsistent.

## 3. Methodology impact

Options B/C change inputs to health-score-v1 for affected businesses (weights and formulas stay 30/25/20/15/10).
That is a scoring-input change under change control: version the projection (`monthly-projection-v2`), keep v1 rows
reproducible, and rescore only after sign-off.

## 4. Decisions requested

1. Option A, B or C (recommendation: **A now, B after a rate source is approved**).
2. Rate source and who may enter/override rates.
3. Whether unrealized revaluation is shown to users and where.
4. Whether historical months are re-projected under v2 or only months after the cut-over.
