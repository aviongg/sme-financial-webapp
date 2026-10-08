# Feature 10 Business Zakat Preview

Phase 1 implements the user-approved `HANAFI_PK_BUSINESS_V1` profile, version
`1.0.0`. It produces business previews only, with no persistence or payment
behavior. It does not certify financing contracts or issue a fatwa.

## Approved Rules

- Monetary/business rate: `0.025`, for a confirmed completed lunar/Hijri year.
- Nisab: `SILVER`, `612.36` grams multiplied by the caller's price per gram.
  Conflicting metal or weight is rejected. No market feed or fixed PKR threshold.
- Include qualifying owned business cash/bank balances, resale inventory at
  current selling value, and `GOOD`/`COLLECTIBLE` receivables.
- Exclude operating fixed assets, `BAD`/`UNRECOVERABLE` receivables, and
  `DOUBTFUL` receivables. Doubtful receivables also produce a warning.
- Unknown inventory purpose, unconfirmed selling valuation, and unknown
  receivable classification make the result incomplete.
- Deduct only declared qualifying current business payables and principal
  currently due or contractually due within the next 12 lunar months.
  The total lifetime loan balance is never the deduction.
- Deduction amounts must not exceed their respective outstanding balances.
  Each underlying obligation must have a unique ID across both deduction lists.
  Where both lists contain positive amounts, `principalExcludedFromPayables`
  must explicitly confirm that principal is not already included in payables.
- `interestExpense` never increases assets or reduces the base. Positive values
  produce `RIBA_RELATED_EXPENSE_RECORDED`.
- `conventional` financing produces a warning without blocking otherwise valid
  arithmetic. `islamic` produces `USER_DECLARED_ISLAMIC`, not certification.
  `none` with a positive loan produces a consistency warning.
- Raw materials and WIP require manual review. Personal wealth, jewellery,
  investments, pensions, crypto, agriculture, livestock, and complex securities
  are unsupported in this phase.

```text
grossZakatableAssets = cash + qualifying inventory + included receivables
deductibleLiabilities = qualifying current payables + qualifying principal
netZakatableAssets = max(0, grossZakatableAssets - deductibleLiabilities)
nisabValue = metalWeightGrams * metalPricePerGram
zakatDue = netZakatableAssets * 0.025
           only when required inputs are known, haul is CONFIRMED,
           and netZakatableAssets >= nisabValue
```

All arithmetic uses `BigDecimal`. Components and thresholds are not rounded.
Only final `zakatDue` is rounded, using `HALF_UP` and the ISO currency's standard
minor unit; `unroundedZakatDue` is also returned.

## Endpoints

| Method and path | Behavior |
| --- | --- |
| `POST /api/zakat/preview` | Manual preview; no repository calls |
| `POST /api/zakat/{userId}/{month}/preview` | Read one monthly record and combine it with supplemental declarations |

`userId` is a UUID and `month` is strictly `YYYY-MM`. No preview is saved.
There is no calculation GET endpoint, assessment history, or finalize endpoint.

### Manual Request

Prices and dates in this example are test inputs, not market quotations.
All amounts, including the price per gram, use the one declared currency.

```json
{
  "assessment": {
    "assessmentDate": "2026-09-30",
    "currency": "PKR",
    "nisabMetal": "SILVER",
    "metalWeightGrams": 612.36,
    "metalPricePerGram": 100,
    "priceSource": "Example caller-supplied quotation",
    "priceTimestamp": "2026-09-30T12:00:00+05:00",
    "haulStatus": "CONFIRMED"
  },
  "assets": {
    "cashAndBankBalances": 100000,
    "inventory": [
      {
        "reference": "resale-stock",
        "type": "RESALE",
        "amount": 20000,
        "valuation": "CURRENT_SELLING_VALUE"
      }
    ],
    "receivables": [
      {"reference": "customer-a", "amount": 30000, "classification": "GOOD"},
      {"reference": "customer-b", "amount": 5000, "classification": "DOUBTFUL"}
    ],
    "unsupportedCategories": []
  },
  "liabilities": {
    "accountsPayable": 10000,
    "currentPayables": [
      {"obligationId": "supplier-invoice-a", "amount": 10000}
    ],
    "principalDueWithin12LunarMonths": [
      {"obligationId": "financing-principal-a", "amount": 20000}
    ],
    "principalExcludedFromPayables": true
  },
  "financing": {
    "financingType": "islamic",
    "loanOutstanding": 300000,
    "interestExpense": 0
  }
}
```

This example produces gross assets `150000`, deductions `30000`, net assets
`120000`, nisab `61236`, and `zakatDue = 3000.00`, with a doubtful-receivable warning.

`inventory` and `receivables` must be explicitly supplied; `[]` means there are
none. Item references must be unique across both lists. Inventory types are
`RESALE`, `FIXED_ASSET`, `RAW_MATERIAL`, `WORK_IN_PROGRESS`, and `UNKNOWN`.
Valuation choices are `CURRENT_SELLING_VALUE`, `HISTORICAL_COST`, and `UNKNOWN`.
Only the first is sufficient for resale inventory. Receivable classifications
are `GOOD`, `COLLECTIBLE`, `DOUBTFUL`, `BAD`, `UNRECOVERABLE`, and `UNKNOWN`.

`unsupportedCategories` is an optional declaration of additional out-of-scope
categories. Any non-empty list produces `UNSUPPORTED`; omitted/empty means no
additional categories were declared. Unknown JSON properties and invalid enum
values are rejected, so unsupported financial inputs cannot silently disappear.

### Saved-Record Request

Use the same `assessment` object as above. The remaining body contains:

```json
{
  "assessment": {
    "assessmentDate": "2026-09-30",
    "currency": "PKR",
    "nisabMetal": "SILVER",
    "metalWeightGrams": 612.36,
    "metalPricePerGram": 100,
    "priceSource": "Example caller-supplied quotation",
    "priceTimestamp": "2026-09-30T12:00:00+05:00",
    "haulStatus": "CONFIRMED"
  },
  "inventory": {
    "reference": "resale-stock",
    "type": "RESALE",
    "amount": 20000,
    "valuation": "CURRENT_SELLING_VALUE"
  },
  "receivables": [
    {"reference": "customer-a", "amount": 30000, "classification": "GOOD"},
    {"reference": "customer-b", "amount": 5000, "classification": "DOUBTFUL"}
  ],
  "currentPayables": [{"obligationId": "supplier-invoice-a", "amount": 10000}],
  "principalDueWithin12LunarMonths": [{"obligationId": "financing-principal-a", "amount": 20000}],
  "principalExcludedFromPayables": true,
  "unsupportedCategories": []
}
```

For parity with the manual example, the source record must contain cash
`100000`, receivables `35000`, payables `10000`, loan principal `300000`,
interest `0`, and financing type `islamic`.

- Cash comes from `cash_balance_eom`; do not additionally add monthly inflows,
  revenue, or bank balances already represented in that value.
- The inventory declaration describes the entire recorded inventory category.
  Omitted `amount` uses the recorded inventory value, but `RESALE` still requires
  explicit `CURRENT_SELLING_VALUE` confirmation. A supplied new valuation is
  used only for the preview, with a warning if it differs from the record.
  Mixed resale/fixed/WIP inventory should use the manual endpoint's item list.
- The full classified receivable list must sum to the stored balance, including
  excluded receivables. A partial/empty list cannot erase positive receivables.
  If the source total is unknown, an explicit complete classified list can supply
  the missing information; the unknown source total is preserved in `source`.
- Explicit zero source inventory/receivables can become empty lists. Null source
  balances do not become zeros. Unknown payable/loan totals require correction
  through the existing record workflow or use of a complete manual preview.
- `currentPayables` and `principalDueWithin12LunarMonths` are caller declarations
  of eligible amounts, not automatically derived from the source totals.
  Empty lists explicitly declare no eligible obligations in that category.
  Omitted lists are only known zero when the corresponding outstanding total is
  explicitly zero; otherwise they require more input.
- Stored `financing_type`, `loan_outstanding`, and `interest_expense` are preserved.
  The response includes their original values and both source and normalized data.

## Dates and Caller Declarations

The caller supplies owned, qualifying business balances at `assessmentDate`,
in one currency, and a pure-silver price in that currency. FX conversion and
automatic valuation are outside this phase. The source record has no currency
field, so the caller must declare the currency actually used for its balances.

`CONFIRMED` attests that the selected profile's lunar-haul conditions are met;
no history count or Gregorian-year arithmetic is used to infer this. Each
principal list entry attests an eligible principal amount due now or within
the next 12 lunar months. The service does not infer a maturity schedule from
an outstanding total or approximate the lunar window with 365 days.

The selected monthly record's effective date is its calendar month end, not
`updated_at`. A different assessment date produces
`ASSESSMENT_DATE_DIFFERS_FROM_RECORD_MONTH_END`. The supplied quote timestamp's
local date is compared with the assessment date; a mismatch produces
`PRICE_DATE_DIFFERS_FROM_ASSESSMENT_DATE`. The supplied offset is preserved.
Warnings do not silently realign values or dates. No arbitrary quote-age
tolerance or automatic price fallback is introduced.

Duplicate detection uses declared obligation IDs. Clients must use the same
identity for the same underlying obligation and must not place a combined
principal/interest amount into a principal-only declaration. The service cannot
independently verify contracts, ownership, collectibility, or distinct real-world
obligations given different IDs; these remain explicit caller declarations.

## Response and Errors

Every preview returns the rule profile/version, rate and debt policy; assessment
and price inputs; asset/liability breakdown; totals; financing status; warnings;
missing fields; normalized input snapshot; and the required disclosure. Saved
previews also return `source` balances, UUID, month, and effective date.

| `calculationStatus` | `zakatDue` |
| --- | --- |
| `CALCULATED` | Rounded amount; conditions confirmed and threshold met |
| `BELOW_NISAB` | Zero |
| `NOT_DUE_HAUL_NOT_COMPLETED` | Zero; informational base still returned |
| `INCOMPLETE_HAUL_CONFIRMATION_REQUIRED` | Null; known base may be returned |
| `INCOMPLETE` | Null; consult `missingFields` |
| `UNSUPPORTED` | Null; unsupported category reported |
| `MANUAL_REVIEW_REQUIRED` | Null; raw materials/WIP reported |

Totals dependent on unknown amounts/treatments are null. Known breakdown entries
are retained. Unsupported/manual-review assessments have no aggregate wealth or
definitive liability. Incomplete metadata may leave an informational known base,
but never a definitive `zakatDue`. Missing required data takes precedence over
haul/below-nisab decisions; unsupported/manual-review categories take precedence
over the ordinary monetary calculation.

Unknown interest expense or financing classification produces an explicit
warning, not a fabricated zero/classification. These warning-only fields do not
block arithmetic when the calculation inputs themselves are complete.

Preview outcomes above use HTTP 200, including incomplete/unsupported results.
Invalid known data (negative values, non-positive price, conflicting preset,
duplicate/overlapping obligations, excess deductions, invalid UUID/month or
malformed JSON) uses HTTP 400. Missing monthly records use 404. Unexpected errors
use the existing generic 500 response. Error responses are not preview results.
The Feature 10 advice handles JSON/path binding errors only for Zakat endpoints.

## Architecture and Verification

All production code is under `com.app.sme_health_backend.zakat`:

- `policy/ZakatPolicy.java`: immutable approved preset, active preset selection,
  version, and disclosure. A future approved weight convention must have its
  own preset/version; never alter the weights within the existing profile.
- `dto/ZakatTypes.java`: explicit statuses and supported classifications.
- `dto/ZakatPreviewRequest.java`: immutable manual input structure.
- `dto/MonthlyRecordZakatRequest.java`: supplemental saved-record declarations.
- `dto/ZakatPreviewResponse.java`: breakdown, provenance, status and result.
- `dto/StrictZakatRequest.java`: reject unknown JSON fields within this feature.
- `service/ZakatCalculationService.java`: deterministic rules and validation;
  no persistence, clock, network, or profile/scoring dependency.
- `service/ZakatService.java`: optional repository adapter, using a read-only
  transaction for saved-record previews and never mutating the loaded entity.
- `controller/ZakatController.java`: two POST endpoints.
- `controller/ZakatExceptionHandler.java`: feature-scoped binding errors.

Tests are in `src/test/java/com/app/sme_health_backend/zakat`:
`ZakatCalculationServiceTests`, `ZakatServiceTests`, `ZakatControllerTests`, and
the shared `ZakatTestFixtures`. HTTP tests run the real services with a mocked
repository. There is no database requirement for these focused tests.

```powershell
# From backend; use the project's Maven wrapper and installed dependency cache.
.\mvnw.cmd '-Dtest=Zakat*Tests' test
```

No shared schema/entity, ScoreResult, Features 4-6, application configuration,
or build dependency changes are required. No feature tables or assessment IDs
are generated. Frontend integration should inspect `calculationStatus`, render
the disclosure/warnings, and distinguish a null amount from a zero obligation.

Broader asset classes, another nisab convention, solar-year rates, automatic
haul/maturity tracking, pricing/FX feeds, purification, and persisted history
require separately approved rules and, where applicable, schema changes.
