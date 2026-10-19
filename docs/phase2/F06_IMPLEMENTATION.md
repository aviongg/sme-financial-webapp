# F06 bounded pre-order costing and pricing

Implemented scope: stateless, non-posting manual planning preview, with an authenticated English/Urdu/RTL screen at `/estimates`. This is a bounded F06 slice; versioned estimates, approval and conversion are not implemented. Development began 18 October and resumed 19 October 2026 (Asia/Karachi) on isolated `codex/f06-bounded-costing`, based on `1f72c6e8500f4a94a103768f1d71f980395af143`. Integration into `dev/fatima` is deferred until the separate R03 remediation is reviewed/integrated and this feature is rebased/revalidated.

## Availability and authority

The preview uses existing mandatory `core_finance` access and `FINANCIAL_DATA_READ`. It accepts manually entered assumptions and creates no financial resource. Every current active role (OWNER, ACCOUNTANT, MANAGER, VIEWER) already has this permission. No new role grants or permission semantics are introduced. Missing, inactive or suspended business membership cannot obtain an authorized context; no hypothetical future salesperson permission is granted.

The `costing` module remains future/unavailable and still depends on the future `projects` module. `ModuleRegistry`, module defaults, dependencies and C03/P09 precedence are unchanged. Making the full costing module implemented would require a separate availability decision. This preview does not provide any F05 project contract or pretend that the full F06 module is ready.

The endpoint follows the actual `/api` convention used by F02/F13. The broader C12 `/api/v2` namespace remains unfrozen. This feature's explicit decimal-string schema is local to the new endpoint; existing numeric APIs are unchanged. No C03/C05/C09/C12 or P05/P10/P13 approval is asserted.

## API contract

`POST /api/estimates/preview`, `Content-Type: application/json`, authenticated session plus existing `X-XSRF-TOKEN` CSRF protection. Server-side `BusinessAuthorizationService` resolves the active business before parsing/calculating. Responses use `Cache-Control: no-store`.

```json
{
  "lines": [{
    "costGroup": "MATERIAL",
    "description": "Manually estimated material",
    "quantity": "1",
    "unit": "batch",
    "unitCost": "800.00"
  }],
  "saleableQuantity": "10",
  "pricingMode": "TARGET_MARGIN",
  "pricingValue": "0.20"
}
```

This is a documented acceptance example, never a live-mode fallback. Cost groups are `MATERIAL`, `LABOUR`, `OVERHEAD`, `FREIGHT`, `OTHER`. Pricing modes are `PROPOSED_PRICE`, `TARGET_MARGIN`, `TARGET_MARKUP`. The pricing value is a batch price for proposed mode and a ratio for target modes. The UI accepts percentages and moves the decimal point as text when serializing; it never computes pricing results.

All numeric financial inputs are plain non-negative decimal **strings**: 1–12 whole digits and optional 1–12 fractional digits. Signs, exponents, separators, whitespace and JSON numeric tokens are rejected. Leading zero digits count toward the bound. Required values cannot be null or omitted. `saleableQuantity` alone may be omitted/null to mean unknown; zero is separately represented.

Limits: 1–100 lines, 65,536 request bytes (including streaming/unknown Content-Length), description 1–200 and unit 1–40 nonblank characters, no control characters. JSON nesting/string/number bounds, duplicate-key rejection, trailing-token rejection and a closed schema apply. Unknown fields, including business/user/currency/actual/source authority, are rejected. No client tenant ID, currency selection, source lookup or calculated total is accepted.

Response fields:

| Field | Meaning |
|---|---|
| `businessId` | Server-derived active business UUID, response only |
| `currency`, `currencyBasis`, `planningOnly` | `PKR`, `APPLICATION_FIXED_PKR`, `true` |
| `lines[]` | Normalized input fields, integer `lineNumber`, decimal-string `plannedCost` |
| `groupSubtotals` | Decimal strings for all five groups; absent groups are known zero from the submitted complete line list |
| `totalPlannedCost`, `plannedBatchPrice`, `plannedContribution` | Decimal strings; negative contribution is valid |
| `saleableQuantity`, `pricingMode`, `pricingValue` | Normalized assumptions; quantity may be null |
| `plannedMargin`, `plannedMarkup`, `plannedPerUnitPrice` | Decimal strings or explicit null for unavailable denominator |
| `unavailableReasons` | Corresponding `ZERO_PRICE`, `ZERO_COST`, `SALEABLE_QUANTITY_UNKNOWN`, `ZERO_SALEABLE_QUANTITY`, or null |
| `calculation` | `version: f06-preview-v1`, `divisionScale: 24`, `divisionRounding: HALF_EVEN`, `accountingRoundingPolicy: null` |

Invalid input returns HTTP 400 with `{status,error:"estimate_invalid",message,errors:{fieldPath:reasonCode}}`; field paths include `lines[0].quantity`. Request-size rejection returns 413 `estimate_request_too_large`. Field reason codes include `required`, `decimal_invalid`, `margin_out_of_range`, `line_count_invalid`, `text_invalid`, `string_required`, `enum_invalid`, `object_required`, `unknown_field`, `json_invalid`. Existing session/permission/active-business/MFA/CSRF status behavior remains authoritative. No success response substitutes zero for an unknown denominator.

## Calculation precision and policy boundary

Java `BigDecimal` is the sole numerical authority. Quantity × unit cost, group/total summation, price minus cost and target-markup multiplication are exact, with no MathContext or currency quantization. Division uses deterministic scale 24 and HALF_EVEN **calculation precision only**. All returned digits are displayed; amount serialization pads at least two fractional digits without removing precision or rounding to currency minor units. Ratios are returned as ratios, displayed as percentages using text transformations only.

For total cost C and proposed batch price P: contribution = P − C; margin = (P − C)/P only for P > 0; markup = (P − C)/C only for C > 0. Target margin m requires 0 ≤ m < 1 and gives P = C/(1 − m). Non-negative target markup u gives P = C × (1 + u). Per-unit price = P/saleable quantity only when quantity is positive.

Acceptance fixtures: C=`800.00`, margin=`0.20` → P=`1000.00`, contribution=`200.00`, margin=`0.2`, markup=`0.25`. The same cost with markup=`0.20` → P=`960.00`, contribution=`160.00`, margin=`0.166666666666666666666667`. Zero-price margin and zero-cost markup stay null, including 0/0. Blank saleable quantity stays unknown and zero saleable quantity has its own reason.

PKR follows the current application's fixed currency presentation. Business entities currently have no selectable base-currency field. This new manual preview does not infer currency for old snapshots, create FX rates or introduce F11 currency behavior.

P05 project attribution, P10 accounting/settlement/currency rounding and P13 budgets/reporting remain open. This preview is not a settlement amount, approved quotation or accounting posting. Tax, FX, yield/wastage, automatic overhead, inventory cost suggestions and actual-profit attribution are excluded. Any future use as an exact persisted/approved commercial amount requires the relevant reviewed policies rather than reusing calculation precision as financial rounding.

## UI and lifecycle

The page provides cost-line add/remove controls, all cost groups, decimal-text quantity/unit-cost inputs, optional saleable quantity, three independent pricing inputs, server line/group/total results, contribution/margin/markup/per-unit results, and a margin-versus-markup explanation. Inputs begin blank. Planning, no persistence, no posting, currency, exclusions and calculation precision are disclosed in English and Urdu.

Results clear on edits and requests are aborted/discarded when superseded. Existing transport generation invalidation and authenticated layout boundaries protect business/session changes. Language switches preserve the draft. Validation, request failure, loading, cancellation, zero/unknown results, focus movement and responsive numeric wrapping are explicit. Decimal controls stay left-to-right within Urdu RTL layout. Drafts live only in component memory; browser unload warns after editing.

## Persistence, side effects and exclusions

There is no repository, entity, save/approve/convert/export endpoint, version history, migration or database financial write. No V20 reservation is made. Persistence awaits policy/contract readiness and an explicitly coordinated migration reservation. F05/C05 readiness is still required for conversion; this task does not start it.

The calculator has no dependencies on MonthlyRecord, scoring, ledger, cash, receivable/payable, stock, project-actual or funding-gap engines. Existing financial, audit, identity and security services and V1–V19 are unchanged. R01 recovery, R02 source/race and R03 attribution remediation are outside this diff. Tests and fresh evidence are recorded in [implementation state](IMPLEMENTATION_STATE.md) and the F06 verification record.
