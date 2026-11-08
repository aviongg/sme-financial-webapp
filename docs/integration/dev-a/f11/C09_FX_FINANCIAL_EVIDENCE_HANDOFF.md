# C09 — Multicurrency Foundation, FX Execution & Financial Evidence Handoff Contract

**Version:** `1.0.0`  
**Date:** 8 November 2026  
**Author / Sole Dev A Full-Stack Engineer:** Google Antigravity  
**Target Consumers:** Developer B (Fatima), AI Orchestration, Accounting Ledger Engine, Financial Risk Intelligence  
**Scope Baseline:** F11A, F11B, F11C, F11D (Wave 3 Multicurrency Complete)

---

## 1. Executive Summary & Purpose

The **C09 Multicurrency Foundation and FX Execution Subsystem** provides FinSight with an audit-grade, deterministic foreign currency calculation, settlement, reconciliation, and reporting engine. It enables SMEs in Pakistan and cross-border commercial environments to conduct trade in ten major international currencies while maintaining strict accounting integrity in their functional currency (PKR) and compliance with international accounting standards (IAS 21).

All financial figures, conversions, book values, realized gains/losses, banking frictions, and exposure aggregations are calculated exclusively by the backend under full transaction isolation. LLMs, AI agents, and frontend clients act strictly as consumers and must never compute, re-round, or fabricate financial authority.

---

## 2. Architectural Boundaries & Domain Ownership

```
┌──────────────────────────────────────────────────────────────────────────┐
│                      Client Layer (Web & Mobile)                         │
│  - /finance/fx (Calculator, Cross-Currency Settlements, Reconciliation,  │
│                 Analytics & Statement Reports)                           │
│  - Next.js 16.3.8 / React 19.2.8 / Bilingual EN & UR with RTL             │
└────────────────────────────────────┬─────────────────────────────────────┘
                                     │ JSON / REST / Cookies / CSRF
┌────────────────────────────────────▼─────────────────────────────────────┐
│                     Authoritative Controller Layer                       │
│  - FxController: /api/finance/fx/**                                      │
│  - FxAnalyticsController: /api/finance/fx/analytics/**                   │
└────────────────────────────────────┬─────────────────────────────────────┘
                                     │ BusinessAccessContext
┌────────────────────────────────────▼─────────────────────────────────────┐
│                 Dev A Authoritative Facade & Services                    │
│  - FxFinancialEvidenceFacade (Public Entrypoint for B/Fatima/AI)         │
│  - FxCalculationService (Pure BigDecimal, ISO 4217 minor units)          │
│  - FxSettlementService (Pessimistic Locking, Movement Generation)         │
│  - FxSettlementReconciliationService (Realized FX Difference Separation) │
│  - FxAnalyticsService (Exposure Grouping, Monthly Trends, CSV Export)    │
│  - CurrencyMetadataService (10 Supported Currencies: Minor Units 0, 2, 3)│
└────────────────────────────────────┬─────────────────────────────────────┘
                                     │ Flyway V39, V40, V26
┌────────────────────────────────────▼─────────────────────────────────────┐
│                         PostgreSQL 17 Schema                             │
│  - fx_obligation_settlements (Settlements & Historical Audits)           │
│  - fx_obligation_recognition_evidence (Immutable Recognition Provenance) │
│  - financial_obligations (Native Currency Obligations)                   │
│  - financial_transactions & account_movements (Ledger Audit Trail)       │
└──────────────────────────────────────────────────────────────────────────┘
```

### Domain Separation Matrix
| Feature Area | Developer A (Backend / Financial Authority) | Developer B / Fatima (Consumer / Commercial Workflow) |
| :--- | :--- | :--- |
| **Exchange Rate Math** | Authoritative pure BigDecimal engine; enforces ISO 4217 decimal scales (JPY 0, KWD/BHD/OMR 3, PKR/USD 2). | Consumes rates via `FxFinancialEvidenceFacade`. Does not alter rates. |
| **Settlement Posting** | Posts cash movements in native account currency; atomically allocates and amortizes obligation balance. | Initiates settlement request or displays settlement status. |
| **Realized FX Variance** | Calculates realized difference `(Converted Principal - Allocated Book Value)`. Separates bank fees completely. | Reads realized gain/loss for project costing and reporting. |
| **Exposure Grouping** | Groups outstanding receivables/payables strictly by native currency. | Displays exposures in dashboard or portfolio summary. |
| **Cross-Currency Transfers** | Designated as **F11E** (requires dual-movement bridge transactions). | Out of scope for F11A–F11D. |

---

## 3. Supported Currencies & Precision Rules

FinSight enforces ISO 4217 minor units deterministically across all monetary operations:

| Currency Code | Minor Units (Scale) | Description | Rounding Mode |
| :---: | :---: | :--- | :---: |
| `PKR` | 2 | Pakistani Rupee (Functional Currency) | `RoundingMode.HALF_UP` |
| `USD` | 2 | United States Dollar | `RoundingMode.HALF_UP` |
| `EUR` | 2 | Euro | `RoundingMode.HALF_UP` |
| `GBP` | 2 | British Pound Sterling | `RoundingMode.HALF_UP` |
| `AED` | 2 | UAE Dirham | `RoundingMode.HALF_UP` |
| `SAR` | 2 | Saudi Riyal | `RoundingMode.HALF_UP` |
| `CNY` | 2 | Chinese Yuan Renminbi | `RoundingMode.HALF_UP` |
| `CAD` | 2 | Canadian Dollar | `RoundingMode.HALF_UP` |
| `AUD` | 2 | Australian Dollar | `RoundingMode.HALF_UP` |
| `JPY` | 0 | Japanese Yen (Zero Decimals) | `RoundingMode.HALF_UP` |
| `KWD` | 3 | Kuwaiti Dinar (Three Decimals) | `RoundingMode.HALF_UP` |
| `BHD` | 3 | Bahraini Dinar (Three Decimals) | `RoundingMode.HALF_UP` |
| `OMR` | 3 | Omani Rial (Three Decimals) | `RoundingMode.HALF_UP` |

*Exchange Rates:* Stored and computed at precision `NUMERIC(19, 6)` (`scale = 6`).  
*Monetary Balances:* Stored at precision `NUMERIC(19, 4)` (`scale = 4`) to allow exact 3-decimal precision without truncation.

---

## 4. Financial Rules & Invariants

### 1. Invariant of Independent Currency Exposure
- **Rule:** Amounts in different currencies can **never be directly summed** or conflated into synthetic totals without explicit exchange rate conversion.
- **Enforcement:** `CurrencyExposureSummary` produces per-currency records (`outstandingReceivables`, `outstandingPayables`, `netExposure`). The UI renders separate native balances per currency.

### 2. Invariant of Fee and FX Separation
- **Rule:** Bank transaction fees and foreign currency rate variances are distinct economic phenomena and must never be combined into a single "loss" or "expense" line.
- **Enforcement:**
  - `Realized FX Difference = Converted Principal - Allocated Book Value`.
  - `Bank Fee` is recorded separately in `fee_amount`.
  - Directional cash movements:
    - *Payable (Outflow):* `Total Account Movement = Converted Principal + Bank Fee`.
    - *Receivable (Inflow):* `Total Account Movement = Converted Principal - Bank Fee`.

### 3. Fail-Closed Missing Evidence Provenance
- **Rule:** If initial recognition evidence is absent, the system **never assumes or substitutes zero**.
- **Enforcement:**
  - When evidence is missing, `recognitionEvidenceStatus` is set to `UNAVAILABLE` and `missingEvidenceReason` is populated (`ORIGINAL_RECOGNITION_EVIDENCE_MISSING`).
  - `allocatedBookValue` and `realizedFxDifference` remain `null`.
  - Provenance categories: `VERIFIED_RECOGNITION_EVIDENCE`, `USER_DECLARED_RECOGNITION`, `HISTORICAL_REFERENCE`, `UNAVAILABLE`.

### 4. Residual Allocation Invariant (No Cumulative Drift)
- **Rule:** When settling an obligation across multiple tranches or exchange rates, the final installment absorbs the exact residual book value.
- **Enforcement:** `Allocated Book Value (final) = Total Initial Book Value - SUM(Prior Allocated Book Values)`. Rounding fractions cannot drift over time.

### 5. CSV Formula-Injection Protection (RFC 4180)
- **Rule:** Exported CSV files must protect against spreadsheet cell execution vulnerabilities.
- **Enforcement:** Any field whose string representation begins with `=`, `+`, `-`, `@`, `\t`, or `\r` is automatically prepended with a single quotation mark (`'`).

---

## 5. Authoritative Facade Contract: `FxFinancialEvidenceFacade`

The class `com.app.sme_health_backend.fx.facade.FxFinancialEvidenceFacade` is the sanctioned Java entrypoint for Developer B, Fatima, and AI tools:

```java
public class FxFinancialEvidenceFacade {
    // 1. Previews & Validations
    public FxPreviewResponse previewConversion(FxPreviewRequest request);
    public FxSettlementPreviewResponse previewSettlement(BusinessAccessContext context, FxSettlementPreviewRequest request);

    // 2. Authoritative Financial Settlement Execution
    public FxSettlementResponse postSettlement(BusinessAccessContext context, FxSettlementPostRequest request, String idempotencyKey);
    public FxSettlementResponse reverseSettlement(BusinessAccessContext context, UUID settlementId, FxSettlementReverseRequest request, String idempotencyKey);

    // 3. Initial Recognition Evidence Management
    public FxObligationRecognitionEvidence recordRecognitionEvidence(BusinessAccessContext context, UUID obligationId, FxRecognitionEvidenceRequest request);
    public Optional<FxObligationRecognitionEvidence> getRecognitionEvidence(BusinessAccessContext context, UUID obligationId);

    // 4. Reconciliation & Performance Summaries
    public FxSettlementReconciliationResponse getSettlementReconciliation(BusinessAccessContext context, UUID settlementId);
    public List<FxSettlementReconciliationResponse> listSettlementReconciliations(BusinessAccessContext context, UUID obligationId);
    public FxPeriodSummaryResponse getPeriodSummary(BusinessAccessContext context, LocalDate startDate, LocalDate endDate);

    // 5. Multicurrency Exposure & Analytics (F11D)
    public FxAnalyticsOverviewResponse getAnalyticsOverview(BusinessAccessContext context);
    public List<CurrencyExposureSummary> getCurrencyExposures(BusinessAccessContext context);
    public List<FxPeriodTrendSummary> getPeriodicTrends(BusinessAccessContext context);
    public List<FxSettlementReportItem> getSettlementsReport(BusinessAccessContext context, FxSettlementReportFilter filter);
    public String exportSettlementsCsv(BusinessAccessContext context, FxSettlementReportFilter filter);
}
```

---

## 6. Authoritative REST Endpoints

| HTTP Method | Route | Permission Required | Description |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/finance/fx/currencies` | Authenticated | Lists all 10 supported currencies with minor unit metadata. |
| `POST` | `/api/finance/fx/preview` | Authenticated | Previews currency conversion with directional fee calculations. |
| `POST` | `/api/finance/fx/settlements/preview` | `FINANCIAL_DATA_READ` | Previews obligation settlement against a specific financial account. |
| `POST` | `/api/finance/fx/settlements` | `FINANCIAL_DATA_WRITE` | Posts authoritative settlement, ledger movement, and allocation. |
| `GET` | `/api/finance/fx/settlements` | `FINANCIAL_DATA_READ` | Lists historical settlement audit records for the tenant. |
| `GET` | `/api/finance/fx/settlements/{id}` | `FINANCIAL_DATA_READ` | Retrieves settlement record by ID. |
| `POST` | `/api/finance/fx/settlements/{id}/reverse` | `FINANCIAL_DATA_WRITE` | Reverses settlement and posts compensating ledger movement. |
| `POST` | `/api/finance/fx/obligations/{id}/recognition` | `FINANCIAL_DATA_WRITE` | Records or updates initial functional currency recognition evidence. |
| `GET` | `/api/finance/fx/obligations/{id}/recognition` | `FINANCIAL_DATA_READ` | Retrieves initial recognition evidence for an obligation. |
| `GET` | `/api/finance/fx/settlements/{id}/reconciliation` | `FINANCIAL_DATA_READ` | Retrieves detailed realized FX and fee reconciliation breakdown. |
| `GET` | `/api/finance/fx/settlements/summary` | `FINANCIAL_DATA_READ` | Aggregates period summary KPIs (gains, losses, net FX, fees). |
| `GET` | `/api/finance/fx/analytics/overview` | `FINANCIAL_DATA_READ` | Aggregates exposure cards, periodic trends, and summary metrics. |
| `GET` | `/api/finance/fx/analytics/exposures` | `FINANCIAL_DATA_READ` | Groups outstanding receivables, payables, and net native exposure. |
| `GET` | `/api/finance/fx/analytics/trends` | `FINANCIAL_DATA_READ` | Chronological monthly trend of settled volumes, FX gains/losses. |
| `GET` | `/api/finance/fx/analytics/report` | `FINANCIAL_DATA_READ` | Filterable settlement audit report with provenance metadata. |
| `GET` | `/api/finance/fx/analytics/report/csv` | `FINANCIAL_DATA_READ` | RFC 4180 CSV export with formula injection sanitization. |

---

## 7. Migration Reservation & Schema Integrity

- **Flyway V39:** `backend/src/main/resources/db/migration/V39__create_fx_settlements_schema.sql` (implements `fx_obligation_settlements`).
- **Flyway V40:** `backend/src/main/resources/db/migration/V40__create_fx_recognition_and_reconciliation_schema.sql` (implements `fx_obligation_recognition_evidence` and reconciliation columns).
- **Flyway V41+:** Reserved for future development. No schema modifications were needed for F11D as V39 and V40 already contained all required audit and temporal columns.
- **Historical Migrations (V1–V38):** Maintained completely untouched.

---

## 8. Out-of-Scope Next Wave Boundary: F11E Internal Transfers

Cross-currency internal transfers (e.g., transferring funds between a USD account and a PKR account within the SME's own portfolio) require a dual-movement bridge transaction or an FX clearing account. In accordance with architectural boundaries, this capability is designated formally as **F11E** and is not part of F11A–F11D obligation settlement scope.

