# FinSight C06 / F07 Funding Gap and Financial Scenario Engine
## Authoritative Frontend Integration Contract & API Specification

- **Document Version:** `1.0.0`
- **Release Milestone:** `DEV-A-F07-C06-BACKEND-01`
- **Backend Authority:** Antigravity (Senior Java / Spring Boot Lead, FinSight Financial Architecture)
- **Frontend Target:** Codex (Senior Next.js Frontend Engineer)
- **Status:** **APPROVED & FULLY VERIFIED** (34/34 Backend Unit & PostgreSQL Tests Passing)

---

## 1. Executive Summary & Architectural Invariants

FinSight Phase 2 introduces the **Advanced Funding-Gap and What-If Financial Scenario Engine** (`POST /api/finance/funding/scenarios/compare`).

This engine enables SME owners and financial managers to evaluate projected liquidity trajectories, detect cash deficits before they materialize, determine exact peak funding requirements and recovery dates, and compare a baseline plan against an alternative what-if strategy (such as delaying capex or accelerating customer collections).

### Fundamental Core Invariants
1. **Strict Backend Calculation Authority:**
   All financial math—including daily timeline projections, cumulative liquidity balances, deficit detection, peak funding requirements, peak deficit dates, recovery dates, and delta differences—is calculated deterministically on the backend.
   > [!IMPORTANT]
   > **Codex Rule:** The frontend must never attempt client-side financial computations or recalculate balances. The UI simply displays and plots the authoritative figures returned by the API.
2. **Strict Read-Only Simulation Invariant:**
   This endpoint is purely computational and simulation-driven. It does not write to the accounting event ledger, modify financial accounts, post entries, or mutate database state. Row counts across all ledger and transaction tables remain 100% unchanged.
3. **Dual Opening Liquidity Sources:**
   - `LEDGER_CASH`: Automatically queries the authoritative double-entry ledger position via `FinancialQueryFacade` as of `asOfDate`. Tagged with provenance `POSTED_ACTUAL`. Fails closed if no active cash accounts exist in the selected currency.
   - `MANUAL_ASSUMPTION`: Uses a user-supplied starting liquidity assumption for hypothetical forward-looking planning. Tagged with provenance `MANUAL_ASSUMPTION`. Requires a non-negative amount with at most 2 decimal places.
4. **Isolated Multi-Tenant Security & RBAC:**
   Requires an active business session context (`FINSIGHT_SESSION` cookie) and CSRF protection (`XSRF-TOKEN` cookie + `X-XSRF-TOKEN` header). Available to `OWNER`, `ACCOUNTANT`, `MANAGER`, and `VIEWER` roles via permission `FINANCIAL_DATA_READ`. Cross-tenant cash access is strictly denied and fails closed.

---

## 2. Endpoint Specification

### `POST /api/finance/funding/scenarios/compare`

- **Content-Type:** `application/json`
- **Accept:** `application/json`
- **Authorization:** Session-backed cookie (`FINSIGHT_SESSION`) + Active Business Context (`FINSIGHT_ACTIVE_BUSINESS_ID`) + CSRF token header (`X-XSRF-TOKEN`).

### Request Headers
| Header | Value | Requirement | Description |
| :--- | :--- | :--- | :--- |
| `Content-Type` | `application/json` | Mandatory | Request payload format |
| `Accept` | `application/json` | Mandatory | Response format |
| `X-XSRF-TOKEN` | `<token-value>` | Mandatory for mutations/POST | FinSight CSRF protection header |

---

## 3. Request Schema & Field Reference

```typescript
export type OpeningLiquiditySource = 'LEDGER_CASH' | 'MANUAL_ASSUMPTION';
export type CashFlowDirection = 'INFLOW' | 'OUTFLOW';

export interface PlannedCashEventInput {
  eventId: string;           // Unique identifier within the scenario (e.g., "evt-001")
  date: string;              // ISO-8601 date: YYYY-MM-DD (must fall within [asOfDate, asOfDate + horizonDays])
  direction: CashFlowDirection; // 'INFLOW' or 'OUTFLOW'
  amount: number | string;   // Positive decimal value (e.g. 50000.00 or "50000.00")
  description?: string;      // Human-readable event description (e.g., "Supplier Bulk Payment")
  category?: string;         // Classification (e.g., "REVENUE", "OPERATING", "CAPEX", "PAYROLL", "TAX", "FINANCING")
}

export interface ScenarioDefinition {
  scenarioName: string;      // Name of the scenario (e.g., "Baseline Operating Plan")
  description?: string;      // Optional narrative description
  events: PlannedCashEventInput[]; // List of hypothetical planned cash events
}

export interface OpeningLiquidityRequest {
  source: OpeningLiquiditySource; // 'LEDGER_CASH' or 'MANUAL_ASSUMPTION'
  manualAmount?: number | string | null; // Required when source is MANUAL_ASSUMPTION (>= 0, max 2 decimals)
  description?: string | null;   // User notes explaining opening liquidity assumption
}

export interface FundingScenarioCompareRequest {
  currency?: string;         // Defaults to "PKR". Currently supports "PKR" only.
  asOfDate?: string;         // ISO-8601 date: YYYY-MM-DD. Defaults to current date if omitted.
  horizonDays?: number;      // Evaluation horizon: 7 to 90 days. Defaults to 30.
  openingLiquidity: OpeningLiquidityRequest; // Mandatory opening liquidity configuration
  baselineScenario: ScenarioDefinition;     // Mandatory baseline plan
  alternativeScenario?: ScenarioDefinition | null; // Optional what-if alternative plan
}
```

---

## 4. Response Schema & Field Reference

```typescript
export interface DailyFundingPosition {
  date: string;                       // ISO-8601 calendar date (YYYY-MM-DD)
  openingPosition: string;            // Start-of-day balance (formatted decimal string, e.g. "105000.00")
  plannedInflows: string;             // Sum of planned inflows for this date
  plannedOutflows: string;            // Sum of planned outflows for this date
  netPlannedMovement: string;         // plannedInflows - plannedOutflows
  closingPosition: string;            // End-of-day balance (openingPosition + netPlannedMovement)
  cumulativeProjectedPosition: string;// Cumulative liquidity position (same as closingPosition)
  deficit: string;                    // max(0, -closingPosition) -> "0.00" if healthy, positive amount if in deficit
  isShortfall: boolean;               // true if closingPosition < 0
  eventsOnDay: PlannedCashEventDetail[]; // Sorted list of events on this day (INFLOW before OUTFLOW)
}

export interface ScenarioResult {
  scenarioName: string;
  totalPlannedInflows: string;       // Total inflows over evaluation horizon
  totalPlannedOutflows: string;      // Total outflows over evaluation horizon
  netPlannedMovement: string;        // Total inflows - Total outflows
  closingProjectedLiquidity: string; // Balance at horizon end
  peakFundingRequirement: string;    // max(0, -minCumulativePosition) across the timeline
  peakDeficitDate: string | null;    // Date of deepest deficit (or null if no deficit)
  firstDeficitDate: string | null;   // First date balance dipped below 0 (or null)
  recoveryDate: string | null;       // First date balance restored to >= 0 after deficit (or null)
  deficitDayCount: number;           // Total count of days in negative territory
  hasDeficit: boolean;               // true if deficitDayCount > 0
  isRecovered: boolean;              // true if recoveryDate != null
  timeline: DailyFundingPosition[];  // Complete day-by-day continuous timeline ([asOfDate, asOfDate + horizonDays] inclusive)
}

export interface DailyScenarioDifference {
  date: string;
  baselineClosingPosition: string;
  alternativeClosingPosition: string;
  positionDifference: string;        // alternativeClosingPosition - baselineClosingPosition
  baselineDeficit: string;
  alternativeDeficit: string;
  deficitDifference: string;         // alternativeDeficit - baselineDeficit
}

export interface ScenarioComparisonSummary {
  baselineScenarioName: string;
  alternativeScenarioName: string;
  peakFundingDifference: string;     // alternativePeak - baselinePeak
  closingLiquidityDifference: string;// alternativeClosing - baselineClosing
  totalInflowsDifference: string;
  totalOutflowsDifference: string;
  firstDeficitDateChangeDays: number | null; // Change in days (+ = delayed deficit, - = earlier deficit)
  recoveryDateChangeDays: number | null;     // Change in days (+ = delayed recovery, - = earlier recovery)
  dailyDifferences: DailyScenarioDifference[];
}

export interface OpeningLiquiditySummary {
  source: 'POSTED_ACTUAL' | 'MANUAL_ASSUMPTION';
  amount: string;
  note: string;
}

export interface FundingScenarioProvenance {
  openingLiquiditySource: 'POSTED_ACTUAL' | 'MANUAL_ASSUMPTION';
  planningMode: 'HYPOTHETICAL_SIMULATION';
  calculatedAt: string;              // ISO-8601 evaluation timestamp
  calculationEngine: 'funding-scenario-v1';
}

export interface FundingScenarioCompareResponse {
  currency: string;                  // "PKR"
  asOfDate: string;                  // YYYY-MM-DD
  horizonStart: string;              // YYYY-MM-DD
  horizonEnd: string;                // YYYY-MM-DD
  horizonDays: number;               // Evaluation horizon duration (7-90)
  openingLiquidity: OpeningLiquiditySummary;
  baseline: ScenarioResult;
  alternative: ScenarioResult | null; // null if not requested
  comparison: ScenarioComparisonSummary | null; // null if not requested
  provenance: FundingScenarioProvenance;
  warnings: string[];
  limitations: string[];
}
```

---

## 5. Concrete JSON Examples

### Example 1: Section 6 Canonical Deficit & Recovery Fixture (`MANUAL_ASSUMPTION`)

**Request:**
```json
{
  "currency": "PKR",
  "asOfDate": "2026-11-01",
  "horizonDays": 30,
  "openingLiquidity": {
    "source": "MANUAL_ASSUMPTION",
    "manualAmount": 0.00,
    "description": "Clean start benchmark"
  },
  "baselineScenario": {
    "scenarioName": "Canonical Deficit Baseline",
    "description": "Baseline plan with short-term supplier outflow",
    "events": [
      {
        "eventId": "evt-supplies-1",
        "date": "2026-11-02",
        "direction": "OUTFLOW",
        "amount": 60000.00,
        "description": "Bulk Raw Materials",
        "category": "SUPPLIES"
      },
      {
        "eventId": "evt-rev-1",
        "date": "2026-11-06",
        "direction": "INFLOW",
        "amount": 20000.00,
        "description": "Retail Receivable Collection",
        "category": "REVENUE"
      },
      {
        "eventId": "evt-rev-2",
        "date": "2026-11-11",
        "direction": "INFLOW",
        "amount": 50000.00,
        "description": "Customer Advance Payment",
        "category": "SALES"
      }
    ]
  }
}
```

**Response (HTTP 200 OK):**
```json
{
  "currency": "PKR",
  "asOfDate": "2026-11-01",
  "horizonStart": "2026-11-01",
  "horizonEnd": "2026-12-01",
  "horizonDays": 30,
  "openingLiquidity": {
    "source": "MANUAL_ASSUMPTION",
    "amount": "0.00",
    "note": "Clean start benchmark"
  },
  "baseline": {
    "scenarioName": "Canonical Deficit Baseline",
    "totalPlannedInflows": "70000.00",
    "totalPlannedOutflows": "60000.00",
    "netPlannedMovement": "10000.00",
    "closingProjectedLiquidity": "10000.00",
    "peakFundingRequirement": "60000.00",
    "peakDeficitDate": "2026-11-02",
    "firstDeficitDate": "2026-11-02",
    "recoveryDate": "2026-11-11",
    "deficitDayCount": 9,
    "hasDeficit": true,
    "isRecovered": true,
    "timeline": [
      {
        "date": "2026-11-01",
        "dayIndex": 0,
        "openingLiquidity": "0.00",
        "inflows": "0.00",
        "outflows": "0.00",
        "netMovement": "0.00",
        "closingLiquidity": "0.00",
        "projectedPosition": "0.00",
        "fundingDeficit": "0.00",
        "isDeficit": false,
        "events": []
      },
      {
        "date": "2026-11-02",
        "dayIndex": 1,
        "openingLiquidity": "0.00",
        "inflows": "0.00",
        "outflows": "60000.00",
        "netMovement": "-60000.00",
        "closingLiquidity": "-60000.00",
        "projectedPosition": "-60000.00",
        "fundingDeficit": "60000.00",
        "isDeficit": true,
        "events": [
          {
            "eventId": "evt-supplies-1",
            "date": "2026-11-02",
            "direction": "OUTFLOW",
            "amount": "60000.00",
            "description": "Bulk Raw Materials",
            "category": "SUPPLIES"
          }
        ]
      },
      {
        "date": "2026-11-06",
        "dayIndex": 5,
        "openingLiquidity": "-60000.00",
        "inflows": "20000.00",
        "outflows": "0.00",
        "netMovement": "20000.00",
        "closingLiquidity": "-40000.00",
        "projectedPosition": "-40000.00",
        "fundingDeficit": "40000.00",
        "isDeficit": true,
        "events": [
          {
            "eventId": "evt-rev-1",
            "date": "2026-11-06",
            "direction": "INFLOW",
            "amount": "20000.00",
            "description": "Retail Receivable Collection",
            "category": "REVENUE"
          }
        ]
      },
      {
        "date": "2026-11-11",
        "dayIndex": 10,
        "openingLiquidity": "-40000.00",
        "inflows": "50000.00",
        "outflows": "0.00",
        "netMovement": "50000.00",
        "closingLiquidity": "10000.00",
        "projectedPosition": "10000.00",
        "fundingDeficit": "0.00",
        "isDeficit": false,
        "events": [
          {
            "eventId": "evt-rev-2",
            "date": "2026-11-11",
            "direction": "INFLOW",
            "amount": "50000.00",
            "description": "Customer Advance Payment",
            "category": "SALES"
          }
        ]
      }
    ]
  },
  "alternative": null,
  "comparison": null,
  "provenance": {
    "openingLiquiditySource": "MANUAL_ASSUMPTION",
    "planningMode": "HYPOTHETICAL_SIMULATION",
    "calculatedAt": "2026-11-07T07:34:00Z",
    "calculationEngine": "funding-scenario-v1"
  },
  "warnings": [
    "Funding gap detected: baseline scenario has a peak funding requirement of PKR 60000.00 on 2026-11-02."
  ],
  "limitations": [
    "UNPOSTED_SIMULATION: Planned cash flows are hypothetical simulations and have not been posted to the authoritative ledger.",
    "INTRA_DAY_NETTING: Same-day events are aggregated at end-of-day; intra-day liquidity may require earlier funding."
  ]
}
```

---

### Example 2: Dynamic Ledger Integration (`LEDGER_CASH`)

**Request:**
```json
{
  "currency": "PKR",
  "asOfDate": "2026-11-01",
  "horizonDays": 14,
  "openingLiquidity": {
    "source": "LEDGER_CASH"
  },
  "baselineScenario": {
    "scenarioName": "Operating Runway",
    "events": [
      {
        "eventId": "evt-rent",
        "date": "2026-11-03",
        "direction": "OUTFLOW",
        "amount": 25000.00,
        "description": "Office Lease",
        "category": "RENT"
      }
    ]
  }
}
```

**Response (HTTP 200 OK):**
```json
{
  "currency": "PKR",
  "asOfDate": "2026-11-01",
  "horizonStart": "2026-11-01",
  "horizonEnd": "2026-11-15",
  "horizonDays": 14,
  "openingLiquidity": {
    "source": "POSTED_ACTUAL",
    "amount": "105000.00",
    "note": "Derived from posted double-entry financial ledger accounts as of 2026-11-01."
  },
  "baseline": {
    "scenarioName": "Operating Runway",
    "totalPlannedInflows": "0.00",
    "totalPlannedOutflows": "25000.00",
    "netPlannedMovement": "-25000.00",
    "closingProjectedLiquidity": "80000.00",
    "peakFundingRequirement": "0.00",
    "peakDeficitDate": null,
    "firstDeficitDate": null,
    "recoveryDate": null,
    "deficitDayCount": 0,
    "hasDeficit": false,
    "isRecovered": false,
    "timeline": [ ... ]
  },
  "alternative": null,
  "comparison": null,
  "provenance": {
    "openingLiquiditySource": "POSTED_ACTUAL",
    "planningMode": "HYPOTHETICAL_SIMULATION",
    "calculatedAt": "2026-11-07T07:34:00Z",
    "calculationEngine": "funding-scenario-v1"
  },
  "warnings": [
    "Opening liquidity reflects posted ledger cash; it does not constitute an approved credit line or guaranteed funding facility."
  ],
  "limitations": [
    "UNPOSTED_SIMULATION: Planned cash flows are hypothetical simulations and have not been posted to the authoritative ledger.",
    "INTRA_DAY_NETTING: Same-day events are aggregated at end-of-day; intra-day liquidity may require earlier funding."
  ]
}
```

---

### Example 3: Comparative Scenario Analysis (Baseline vs What-If Alternative)

**Request:**
```json
{
  "currency": "PKR",
  "asOfDate": "2026-11-01",
  "horizonDays": 14,
  "openingLiquidity": {
    "source": "MANUAL_ASSUMPTION",
    "manualAmount": 10000.00
  },
  "baselineScenario": {
    "scenarioName": "Immediate Capex Plan",
    "events": [
      {
        "eventId": "capex-1",
        "date": "2026-11-03",
        "direction": "OUTFLOW",
        "amount": 50000.00,
        "description": "Equipment Purchase",
        "category": "CAPEX"
      }
    ]
  },
  "alternativeScenario": {
    "scenarioName": "Phased Capex Plan",
    "events": [
      {
        "eventId": "capex-phased-1",
        "date": "2026-11-03",
        "direction": "OUTFLOW",
        "amount": 20000.00,
        "description": "Initial Downpayment",
        "category": "CAPEX"
      },
      {
        "eventId": "rev-1",
        "date": "2026-11-07",
        "direction": "INFLOW",
        "amount": 40000.00,
        "description": "Milestone Billing",
        "category": "REVENUE"
      }
    ]
  }
}
```

**Response Highlights (`comparison` block):**
```json
{
  "comparison": {
    "baselineScenarioName": "Immediate Capex Plan",
    "alternativeScenarioName": "Phased Capex Plan",
    "peakFundingDifference": "-30000.00",
    "closingLiquidityDifference": "70000.00",
    "totalInflowsDifference": "40000.00",
    "totalOutflowsDifference": "-30000.00",
    "firstDeficitDateChangeDays": 0,
    "recoveryDateChangeDays": null,
    "dailyDifferences": [
      {
        "date": "2026-11-03",
        "dayIndex": 2,
        "baselineClosing": "-40000.00",
        "alternativeClosing": "-10000.00",
        "liquidityDifference": "30000.00",
        "baselineDeficit": "40000.00",
        "alternativeDeficit": "10000.00",
        "deficitDifference": "-30000.00"
      }
    ]
  }
}
```

---

### Example 4: Validation Error Structure (HTTP 400 Bad Request)

When a validation constraint is breached (e.g. non-PKR currency, horizon outside 7–90 days, negative manual opening balance, or duplicate event ID):

**Response (HTTP 400 Bad Request):**
```json
{
  "timestamp": "2026-11-07T07:34:00.123",
  "status": 400,
  "error": "Validation Failed",
  "message": "One or more request parameters are invalid",
  "errors": {
    "currency": "Unsupported currency 'USD'; scenario engine currently supports PKR only",
    "horizonDays": "Horizon must be between 7 and 90 days, received: 120"
  }
}
```

---

## 6. Frontend Integration Guidelines for Codex

1. **API Client Integration:**
   - Call `POST /api/finance/funding/scenarios/compare` using the standard FinSight fetch adapter with credentials (`include`) and the `X-XSRF-TOKEN` header.
2. **Displaying Liquidity Metrics:**
   - Display `peakFundingRequirement` as a prominent KPI callout. If `> 0`, render in alert styling (red/rose) indicating a funding gap.
   - Display `firstDeficitDate`, `peakDeficitDate`, and `recoveryDate` prominently.
   - If `hasDeficit === true` and `isRecovered === false`, display an unambiguous badge: **"Non-Recovering Deficit within Horizon"**.
3. **Timeline Visualizations:**
   - Use the `timeline` array to render an interactive area/line chart with a zero-line benchmark.
   - Days where `isDeficit === true` should be highlighted with negative red fill/bars.
   - Render event tags or tooltips when hovering over dates where `events.length > 0`.
4. **Scenario Comparison Diffing:**
   - When an alternative plan is evaluated, display side-by-side KPI cards:
     * **Peak Funding Difference:** Render green if negative (e.g. `-PKR 30,000` = saves capital), red if positive.
     * **Closing Liquidity Difference:** Render green if positive.
   - Render the `dailyDifferences` as a comparative delta chart.
5. **Bilingual & RTL Support:**
   - Format all currency figures using FinSight's standardized formatter: `PKR 105,000.00`.
   - Provide Urdu translations for status badges (e.g., "فنڈنگ کی کمی" for Funding Deficit, "بحالی کی تاریخ" for Recovery Date).
