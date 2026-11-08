# C09 — Multicurrency Foundation, Exchange Rates & FX Calculation Contract

**Version:** `1.0`  
**Date:** 8 November 2026  
**Status:** Canonical / Active  
**Feature Scope:** F11A Multicurrency Foundation, Exchange Rates and Pure Java FX Calculation Engine  
**Feature Owner:** Google Antigravity (Sole Full-Stack Developer A Engineer & Financial Systems Architect)  

---

## 1. Architectural Scope & Purpose

FinSight's core financial ledger operates primarily in Pakistani Rupees (`PKR`). With the introduction of **F11 Multicurrency Management**, FinSight introduces:
1. **Central Currency Metadata Foundation:** Comprehensive ISO 4217 currency metadata governing scale, minor units (e.g., JPY=0, PKR/USD/EUR/GBP/AED/SAR=2, KWD/BHD/OMR=3), display symbols, and rounding rules.
2. **Exchange Rate Model:** Rigorous exchange rate quoting conventions (`1 BASE = [RATE] QUOTE`), support for manual rates, reference rates, and locked transaction rates with reciprocal precision.
3. **Pure Java Deterministic Calculation Engine:** Direction-aware settlement math for foreign vendor payments (`OUTGOING`) and foreign customer receipts (`INCOMING`), fee deductions/additions, effective rate computation, and hypothetical scenario variance analysis.
4. **Read-Only Preview API:** Stateless, non-posting preview endpoints allowing businesses to calculate and compare conversions with zero database mutations or balance alterations.

### Invariants & Non-Posting Boundaries
> [!IMPORTANT]
> **F11A is strictly non-posting and non-mutating:**
> - Does **NOT** post general ledger transactions or modify account balances.
> - Does **NOT** alter customer/vendor obligations or allocate settlements (deferred to F11B/F11C).
> - Does **NOT** affect SME Health Scores or historical monthly summaries.
> - Does **NOT** create phantom nominal accounts.
> - Operates under server-side RBAC: Requires active business context and `FINANCIAL_DATA_READ` permission (accessible to `OWNER`, `ACCOUNTANT`, `MANAGER`, `VIEWER`; strictly denied to `SALESPERSON`).

---

## 2. PostgreSQL Port Resolution & Isolation Proof

In prior checkpoint discussions, a reference to PostgreSQL port 5434 was evaluated. The environment configuration is formally clarified as follows:

| Environment | Purpose | Port | Isolation & Integrity Policy |
| :--- | :--- | :--- | :--- |
| **S4B Persistent Integration** | Protected staging container | `5434` | Zero destructive operations during unit/IT suites. Intact and preserved. |
| **Legacy Incident Environment** | Preserved baseline incident container | `5433` | Untouched, read-only baseline. |
| **Zonky DisposablePostgres** | Ephemeral Integration Test harness | `0` (Dynamic OS Ephemeral Port) | Configured via `EmbeddedPostgres.builder().setPort(0).start()`. Binds to random unused high port (e.g. 50000+). Completely isolated from ports 5433 and 5434. |

Integration tests run exclusively against the dynamic `DisposablePostgres` instance with separate `finsight_migrator` and `finsight_app` roles. Ports 5433 and 5434 remain completely unmolested.

---

## 3. Central Currency Metadata Foundation

The central currency registry (`CurrencyMetadataService`) maintains authoritative metadata for supported currencies:

| ISO Code | Display Name | Symbol | Minor Units (Scale) | Rounding Mode | Active |
| :---: | :--- | :---: | :---: | :---: | :---: |
| `PKR` | Pakistani Rupee | ₨ | 2 | `HALF_UP` | Yes |
| `USD` | US Dollar | $ | 2 | `HALF_UP` | Yes |
| `EUR` | Euro | € | 2 | `HALF_UP` | Yes |
| `GBP` | British Pound | £ | 2 | `HALF_UP` | Yes |
| `AED` | UAE Dirham | AED | 2 | `HALF_UP` | Yes |
| `SAR` | Saudi Riyal | SAR | 2 | `HALF_UP` | Yes |
| `JPY` | Japanese Yen | ¥ | 0 | `HALF_UP` | Yes |
| `KWD` | Kuwaiti Dinar | KD | 3 | `HALF_UP` | Yes |
| `BHD` | Bahraini Dinar | BD | 3 | `HALF_UP` | Yes |
| `OMR` | Omani Rial | OMR | 3 | `HALF_UP` | Yes |

### Rounding & Arithmetic Rules
- All monetary arithmetic uses `BigDecimal` with rounding mode `RoundingMode.HALF_UP`.
- Currencies with 0 minor units (e.g., JPY) round to integer values.
- Currencies with 3 minor units (e.g., KWD, BHD, OMR) preserve thousandths.
- Standard currencies (e.g., PKR, USD, EUR) preserve hundredths (cents/paisa).
- Internal conversion math uses `MathContext.DECIMAL128` (34 digits of precision) to eliminate intermediate truncation errors before applying the target currency scale.

---

## 4. Quoting Convention & Exchange Rate Model

### Quotation Standard
FinSight follows the standard direct market quoting convention:
$$\text{Convention: } 1\text{ BASE} = \text{RATE } \text{QUOTE}$$
*Example:* `USD/PKR = 278.500000` signifies that $1\text{ USD} = 278.50\text{ PKR}$.

### Reciprocal Quotation
Reciprocal conversion is calculated deterministically with a minimum scale of 8:
$$\text{Reciprocal Rate} = \frac{1}{\text{RATE}} \quad (\text{Scale 8}, \text{HALF\_UP})$$
*Example:* $\frac{1}{278.500000} = 0.00359066\text{ USD/PKR}$.

---

## 5. Pure Java FX Calculation Rules & Acceptance Fixtures

### Direction-Aware Cash Flow Semantics

#### 1. Outgoing Foreign Payment (`OUTGOING`)
Representing an overseas vendor payment funded from a local operational account:
- Beneficiary receives the converted principal amount.
- Funding account debits the gross converted principal **plus** any banking wire/settlement fees.
$$\text{Gross Settlement Amount} = \text{Principal Amount} \times \text{Exchange Rate}$$
$$\text{Total Fee Amount} = \text{Fixed Fee} + (\text{Gross Settlement Amount} \times \text{Percentage Fee})$$
$$\text{Total Settlement Debited} = \text{Gross Settlement Amount} + \text{Total Fee Amount}$$
$$\text{Net Beneficiary Received} = \text{Gross Settlement Amount}$$
$$\text{Effective Rate} = \frac{\text{Total Settlement Debited}}{\text{Principal Amount}}$$

#### 2. Incoming Foreign Receipt (`INCOMING`)
Representing an overseas customer wire received into a local operational account:
- Payer transfers the principal foreign currency.
- Receiving account receives the gross converted principal **minus** intermediary/recipient bank fees.
$$\text{Gross Settlement Amount} = \text{Principal Amount} \times \text{Exchange Rate}$$
$$\text{Total Fee Amount} = \text{Fixed Fee} + (\text{Gross Settlement Amount} \times \text{Percentage Fee})$$
$$\text{Net Settlement Received} = \text{Gross Settlement Amount} - \text{Total Fee Amount}$$
$$\text{Total Payer Debited} = \text{Gross Settlement Amount}$$
$$\text{Effective Rate} = \frac{\text{Net Settlement Received}}{\text{Principal Amount}}$$

---

### Mandatory Numerical Acceptance Fixtures

#### Case A: Outgoing USD 100 with PKR 150 Fixed Fee
- **Inputs:** Base: `USD`, Quote: `PKR`, Rate: `278.50`, Principal: `100.00 USD`, Direction: `OUTGOING`, Fee: `150.00 PKR`.
- **Calculations:**
  - $\text{Gross Settlement Amount} = 100.00 \times 278.50 = 27,850.00\text{ PKR}$
  - $\text{Total Fee Amount} = 150.00\text{ PKR}$
  - $\text{Total Settlement Debited} = 27,850.00 + 150.00 = 28,000.00\text{ PKR}$
  - $\text{Effective Rate} = \frac{28,000.00}{100.00} = 280.000000\text{ PKR/USD}$
- **Result:** $\mathbf{28,000.00\text{ PKR}}$ debited from funding account.

#### Case B: Incoming USD 100 with PKR 150 Fixed Fee
- **Inputs:** Base: `USD`, Quote: `PKR`, Rate: `278.50`, Principal: `100.00 USD`, Direction: `INCOMING`, Fee: `150.00 PKR`.
- **Calculations:**
  - $\text{Gross Settlement Amount} = 100.00 \times 278.50 = 27,850.00\text{ PKR}$
  - $\text{Total Fee Amount} = 150.00\text{ PKR}$
  - $\text{Net Settlement Received} = 27,850.00 - 150.00 = 27,700.00\text{ PKR}$
  - $\text{Effective Rate} = \frac{27,700.00}{100.00} = 277.000000\text{ PKR/USD}$
- **Result:** $\mathbf{27,700.00\text{ PKR}}$ net received in destination account.

#### Case C: Currency Precision & Minor Units
- **USD to JPY:** Principal: `100.00 USD`, Rate: `155.3456`, Minor Units: `0`.
  $$\text{Gross} = 100.00 \times 155.3456 = 15,534.56 \xrightarrow{\text{HALF\_UP}} \mathbf{15,535\text{ JPY}}$$
- **USD to KWD:** Principal: `100.00 USD`, Rate: `0.307255`, Minor Units: `3`.
  $$\text{Gross} = 100.00 \times 0.307255 = 30.7255 \xrightarrow{\text{HALF\_UP}} \mathbf{30.726\text{ KWD}}$$

#### Case D: Rate Variation Scenario Variance
- **Baseline Rate:** `278.500000` $\rightarrow$ Gross: `27,850.00 PKR`
- **Comparison Rate:** `282.000000` $\rightarrow$ Gross: `28,200.00 PKR`
- **Variance:** $+350.00\text{ PKR}$ ($+1.257\%$)
- **Impact Assessment:**
  - `OUTGOING`: $+350.00\text{ PKR}$ cost increase is assessed as **`UNFAVORABLE`**.
  - `INCOMING`: $+350.00\text{ PKR}$ proceeds increase is assessed as **`FAVORABLE`**.

---

## 6. REST API Specification

### Endpoint: `GET /api/finance/fx/currencies`
Returns the complete list of supported ISO 4217 currencies and metadata.
- **Permission Required:** `FINANCIAL_DATA_READ`

#### Response: `200 OK`
```json
[
  {
    "code": "PKR",
    "displayName": "Pakistani Rupee",
    "symbol": "₨",
    "minorUnit": 2,
    "active": true
  },
  {
    "code": "USD",
    "displayName": "US Dollar",
    "symbol": "$",
    "minorUnit": 2,
    "active": true
  },
  {
    "code": "JPY",
    "displayName": "Japanese Yen",
    "symbol": "¥",
    "minorUnit": 0,
    "active": true
  },
  {
    "code": "KWD",
    "displayName": "Kuwaiti Dinar",
    "symbol": "KD",
    "minorUnit": 3,
    "active": true
  }
]
```

---

### Endpoint: `POST /api/finance/fx/preview`
Simulates a foreign exchange conversion preview with directional fee settlement and scenario comparison.
- **Permission Required:** `FINANCIAL_DATA_READ`
- **CSRF Token:** Required for stateful sessions (`X-XSRF-TOKEN`).

#### Request Body
```json
{
  "baseCurrency": "USD",
  "quoteCurrency": "PKR",
  "rate": 278.500000,
  "direction": "OUTGOING",
  "principalAmount": 100.00,
  "principalCurrency": "USD",
  "fixedFee": 150.00,
  "feeCurrency": "PKR",
  "percentageFeePercent": 0.0,
  "comparisonRate": 282.000000,
  "sourceType": "MANUAL_USER_RATE"
}
```

#### Response: `200 OK`
```json
{
  "baseCurrency": "USD",
  "quoteCurrency": "PKR",
  "rate": 278.500000,
  "reciprocalRate": 0.00359066,
  "rateConvention": "1 USD = 278.500000 PKR",
  "sourceType": "MANUAL_USER_RATE",
  "direction": "OUTGOING",
  "principalAmount": 100.00,
  "principalCurrency": "USD",
  "settlementCurrency": "PKR",
  "grossSettlementAmount": 27850.00,
  "totalFeeAmount": 150.00,
  "fixedFeeAmount": 150.00,
  "percentageFeeAmount": 0.00,
  "feeCurrency": "PKR",
  "netSettlementAmount": 27850.00,
  "totalSettlementDebited": 28000.00,
  "effectiveRate": 280.000000,
  "scenarioVariance": {
    "comparisonRate": 282.000000,
    "comparisonGrossAmount": 28200.00,
    "comparisonSettlementAmount": 28350.00,
    "varianceAmount": 350.00,
    "variancePercentage": 1.2567,
    "impact": "UNFAVORABLE"
  },
  "provenance": {
    "calculationType": "PURE_JAVA_DETERMINISTIC_PREVIEW",
    "disclaimer": "Deterministic non-posting calculation preview. No financial accounts, ledger transactions, or balances were altered.",
    "timestamp": "2026-11-08T11:15:00Z"
  }
}
```

---

## 7. F11B Cross-Currency Settlements Contract

Slice F11B transitions FinSight from conversion simulation to authoritative, auditable cross-currency financial settlements for obligations.

### Database Schema (Flyway V39)
1. **Obligation Currency Relaxation:**
   `V39__create_fx_settlements_schema.sql` relaxes `ck_financial_obligations_currency` from PKR-only to all 10 supported currencies (`PKR`, `USD`, `EUR`, `GBP`, `AED`, `SAR`, `JPY`, `KWD`, `BHD`, `OMR`).
2. **Authoritative Audit Bridge Table (`fx_obligation_settlements`):**
   - Links `obligation_id` (native foreign currency, e.g. USD) with `account_id` (funding currency, e.g. PKR), `financial_transaction_id`, and `allocation_id`.
   - Records locked `exchange_rate` (`BASE_TO_QUOTE`), `converted_principal`, `fee_amount`, `total_account_movement`, and `effective_rate`.
   - Status tracked via `status` (`SETTLED`, `REVERSED`).
   - Append-only security: DML permissions granted to `finsight_app` and `finsight_backup`; `DELETE` revoked.

### Cross-Currency Accounting Mechanics
| Flow | Obligation Balance (USD) | Account Movement (PKR) | Ledger Transaction Type | Effective Rate |
| :--- | :--- | :--- | :--- | :--- |
| **Case A: USD Payable Settlement** | Reduced by native settlement amount (e.g. -$100.00) | Debited by converted principal + bank fees (e.g. -28,000.00 PKR) | `PAYABLE_SETTLEMENT` | $(27,850 + 150) / 100 = 280.000000$ |
| **Case B: USD Receivable Collection** | Reduced by native collection amount (e.g. -$50.00) | Credited by converted principal - bank fees (e.g. +13,825.00 PKR) | `RECEIVABLE_COLLECTION` | $(13,925 - 100) / 50 = 276.500000$ |
| **Compensating Reversal** | Restored to original amount (e.g. +$100.00) | Compensated by opposite movement (+28,000.00 PKR credit) | Reversal transaction linking to parent | Audit status set to `REVERSED` |

### REST Endpoints
1. `POST /api/finance/fx/settlements/preview`: Stateless preview returning gross conversion, fees, net movement, effective rate, and projected post-settlement outstanding balance.
2. `POST /api/finance/fx/settlements`: Authoritative settlement posting with idempotency protection.
3. `GET /api/finance/fx/settlements`: List settlement audit history for active business context.
4. `GET /api/finance/fx/settlements/{id}`: Fetch settlement details.
5. `POST /api/finance/fx/settlements/{id}/reverse`: Compensating reversal with mandatory reason and restored obligation balance.

### Technical Analysis: Deferred Internal Cross-Currency Transfers
True cross-currency transfers between internal accounts (e.g. transferring from USD cash to PKR bank account) are intentionally deferred. 
**Root Cause:** In the baseline ledger schema (`financial_transactions` and `account_movements`), a transaction entity maintains a single `amount` and `currency` column that governs both legs. Cross-currency internal transfers require either:
- A dual-amount transaction model (`source_amount`, `source_currency`, `target_amount`, `target_currency`), or
- A multi-leg FX clearing bridge account.
This structural extension is scheduled for subsequent multicurrency ledger iterations. Obligation settlements are completely unaffected by this limitation, as each obligation settlement links a native-currency obligation allocation with an account-currency cash movement via `fx_obligation_settlements`.


---

## 8. F11C Realized FX Differences & Settlement Accounting Evidence Contract

Slice F11C establishes the authoritative financial evidence layer for foreign exchange realization, fee reconciliation, and downstream commercial costing.

### A. Four Core Monetary Quantities & Mathematical Invariants
Every foreign obligation settlement computes and records four distinct financial values:
1. **Historical Obligation Book Value ($V_{\text{book}}$):**
   $$V_{\text{book}} = \text{Amount}_{\text{native}} \times \text{Rate}_{\text{recognition}}$$
   Recorded in `fx_obligation_recognition_evidence`.
2. **Settlement Principal ($P_{\text{settle}}$):**
   $$P_{\text{settle}} = \text{Amount}_{\text{native}} \times \text{Rate}_{\text{settlement}}$$
   Recorded in `fx_obligation_settlements.converted_principal`.
3. **Realized Exchange Difference ($\Delta_{\text{FX}}$):**
   $$\Delta_{\text{FX}} = P_{\text{settle}} - V_{\text{allocated\_book}}$$
   - **Receivable Collection:** Difference > 0 $\rightarrow$ `GAIN`; Difference < 0 $\rightarrow$ `LOSS`.
   - **Payable Settlement:** Difference > 0 $\rightarrow$ `LOSS`; Difference < 0 $\rightarrow$ `GAIN`.
4. **Bank Transaction Fee ($F_{\text{bank}}$):**
   Strictly separated from currency conversion variance.
   - For payables: Added to cash debit ($P_{\text{settle}} + F_{\text{bank}} = \text{Total Debit}$).
   - For receivables: Deducted from cash receipt ($P_{\text{settle}} - F_{\text{bank}} = \text{Total Credit}$).
   - Cash reconciliation invariant:
     $$\text{CashReconciliationVerified} = (\text{TotalMovement} == P_{\text{settle}} \pm F_{\text{bank}})$$

### B. Multi-Tranche Partial Settlements & Residual Book Allocation
When an obligation is settled across multiple partial payments at different exchange rates:
- Initial tranches allocate: $V_{\text{allocated}} = \text{Round}(\text{TrancheAmount} \times \text{Rate}_{\text{recognition}})$.
- Final tranche allocates exact remaining residual:
  $$V_{\text{final}} = V_{\text{total\_book}} - \sum V_{\text{prior\_allocated}}$$
Zero rounding drift invariant: $\sum V_{\text{allocated}} == V_{\text{total\_book}}$.

### C. Fail-Closed Missing Evidence Reporting
When no recognition evidence exists:
- `recognitionEvidenceStatus` is set to `UNAVAILABLE`.
- `missingEvidenceReason` is set to `ORIGINAL_RECOGNITION_EVIDENCE_MISSING`.
- Both `allocatedBookValue` and `realizedFxDifference` are `null`. FinSight never substitutes zero or current spot rates.

### D. Flyway Migration V40
- Table `fx_obligation_recognition_evidence` storing historical declaration rates, dates, sources, and functional currency.
- Columns on `fx_obligation_settlements`: `allocated_book_value`, `realized_fx_difference`, `realized_fx_impact`, `recognition_evidence_status`, `missing_evidence_reason`.
- Least-privilege permissions: `REVOKE DELETE` from `finsight_app`; `GRANT SELECT, INSERT, UPDATE` to `finsight_app`.

### E. REST Endpoints
1. `POST /api/finance/fx/obligations/{obligationId}/recognition`: Record historical recognition rate and book value.
2. `GET /api/finance/fx/obligations/{obligationId}/recognition`: Retrieve recognition evidence.
3. `GET /api/finance/fx/settlements/{id}/reconciliation`: Comprehensive economic reconciliation breakdown.
4. `GET /api/finance/fx/settlements/reconciliation`: Audit list of reconciliations.
5. `GET /api/finance/fx/settlements/summary`: Aggregate period financial totals (active vs reversed counts, total gains, total losses, net FX difference, total bank fees).

### F. Read-Only Financial Evidence Facade
`FxFinancialEvidenceFacade` provides clean programmatic access for Developer B commercial costing and C11 financial queries:
- `getObligationRecognitionEvidence(UUID businessId, UUID obligationId)`
- `getSettlementReconciliation(UUID businessId, UUID settlementId)`
- `getSettlementReconciliationsForObligation(UUID businessId, UUID obligationId)`
- `getPeriodSummary(UUID businessId, LocalDate startDate, LocalDate endDate)`
- `getCumulativeRealizedFxForObligation(UUID businessId, UUID obligationId)`
