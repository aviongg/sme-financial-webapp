# Independent Project Funding-Gap Calculation Specification

**Document Version:** `1.0`
**Date:** 15 October 2026
**Status:** DRAFT / INTERNAL SPECIFICATION
**Scope:** Developer A Pure Calculation Foundation (Task A-F07-CALCULATOR)
**Authority:** Internal calculator specification; not a frozen public API or Fatima-approved integration contract.

---

## 1. Objective & Boundaries

### 1.1 Objective
The purpose of this component is to provide a pure, deterministic Java calculation engine answering:
> **"How much additional funding does this project need, and when?"**

This serves as an internal calculation foundation for project liquidity analysis (F07). It accepts an already assembled plan of project-specific opening funds, calendar horizon, and dated cash flow events, calculating the continuous daily position curve and key deficit milestones.

### 1.2 Strict Non-Duplication & Boundaries
1. **No Project Model or Profitability Competition:** This component does not define a competing project entity or compute project profitability, margins, COGS, sales, or commissions (which belong to Fatima / Developer B).
2. **Deterministic Pure Math:** Operates strictly in memory with exact `BigDecimal` arithmetic. No Spring framework annotations, dependency injection, database access, HTTP endpoints, or background tasks.
3. **Dedicated Liquidity Scope:** Confined exclusively to:
   - `backend/src/main/java/com/app/sme_health_backend/liquidity/`
   - `backend/src/test/java/com/app/sme_health_backend/liquidity/`
4. **Untouched Existing Systems:** No modifications to accounting packages, financial account contracts, monthly records, identity/RBAC, migrations (V1–V20), build configuration, or frontend files.

---

## 2. Calculation Rules & Semantics

### 2.1 Currency & Precision Discipline
* **Currency:** Strictly `PKR` in this initial version. Any missing or non-PKR currency is rejected.
* **Arithmetic:** Exact `BigDecimal` arithmetic with scale 2. No floating-point conversions (`double`/`float`) or silent rounding (`RoundingMode.HALF_UP`, truncation, etc.).
* **Opening Funds:** Must be non-negative ($\ge 0.00$) and representable exactly at two decimal places without rounding. Missing opening funds fail validation (explicit zero is valid; never assumed).
* **Event Amounts:** Must be strictly positive ($> 0.00$) and representable exactly at two decimal places without rounding.

### 2.2 Daily Position Curve & Intra-Day Semantics
For each calendar day $t$ from inclusive `horizonStart` through inclusive `horizonEnd`:
$$\text{closingPosition}_t = \text{closingPosition}_{t-1} + \text{plannedInflows}_t - \text{plannedOutflows}_t$$
* Day 0 starts from explicit `openingFunds`.
* The curve is continuous: all calendar days in the horizon are included, even days without events (where inflows and outflows are `0.00`).
* **Intra-Day Netting:** Events occurring on the same calendar day are netted into daily aggregate inflow and outflow. This estimates **end-of-day funding needs**; it does not prove funds are sufficient at every intra-day instant before an inflow arrives.
* **Input Ordering Invariance:** Events are grouped and sorted by effective date; input ordering (e.g. forward, reversed, shuffled) has zero effect on the calculation outcome.
* **Pre-Funding Trajectory:** The daily curve represents the cash position **before** any hypothetical extra funding. The engine does not inject funding into the curve automatically.

### 2.3 Shortfall, Peak Shortfall, and Recovery Milestones
1. **Additional Funding Required:**
   $$\text{additionalFundingRequired} = \max(0, -\min_{t \in \text{horizon}} \text{closingPosition}_t)$$
   If the minimum closing position is non-negative, required funding is exactly `0.00`.
2. **First Shortfall Date:**
   The earliest calendar date $t$ where $\text{closingPosition}_t < 0.00$. If no shortfall occurs, this is `null`.
3. **First Peak-Shortfall Date:**
   The earliest calendar date $t$ reaching the overall minimum closing position ($\min_{t} \text{closingPosition}_t$). If multiple dates share the identical minimum deficit, the earliest calendar date is selected. If no shortfall occurs, this is `null`.
4. **Recovery Date:**
   The earliest calendar date $R$ strictly after `firstShortfallDate` from which closing positions remain non-negative through the supplied horizon end ($\forall t \in [R, \text{horizonEnd}], \text{closingPosition}_t \ge 0.00$).
   - If the plan ends in deficit on `horizonEnd`, or never recovers after the first shortfall, `recoveryDate` is `null`.
   - If no shortfall occurs, `recoveryDate` is `null`.
   - Recovery is explicitly defined as **within the supplied plan horizon**, without implying anything about unsupplied later dates.
5. **Shortfall-Day Count:**
   The count of calendar days within the horizon where $\text{closingPosition}_t < 0.00$.

---

## 3. Data Contracts & Model

### 3.1 Input Contract: `ProjectFundingPlanInput`
| Field | Type | Description | Validation |
| :--- | :--- | :--- | :--- |
| `currency` | `String` | Currency code | Required, non-blank, strictly `"PKR"` |
| `openingFunds` | `BigDecimal` | Explicit project funds at start | Required, $\ge 0.00$, scale $\le 2$ |
| `horizonStart` | `LocalDate` | Inclusive horizon start date | Required |
| `horizonEnd` | `LocalDate` | Inclusive horizon end date | Required, $\ge \text{horizonStart}$ |
| `events` | `List<PlannedProjectCashEvent>` | Planned cash flow events | Required (empty list permitted) |

### 3.2 Event Contract: `PlannedProjectCashEvent`
| Field | Type | Description | Validation |
| :--- | :--- | :--- | :--- |
| `eventId` | `String` | Stable unique identifier | Required, non-blank, unique within plan |
| `date` | `LocalDate` | Effective event date | Required, $\in [\text{horizonStart}, \text{horizonEnd}]$ |
| `direction` | `ProjectCashFlowDirection` | `INFLOW` or `OUTFLOW` | Required |
| `amount` | `BigDecimal` | Cash flow amount | Required, $> 0.00$, scale $\le 2$ |
| `description` | `String` | Optional description/reference | Optional (nullable) |

### 3.3 Output Contract: `ProjectFundingGapResult`
| Field | Type | Description |
| :--- | :--- | :--- |
| `currency` | `String` | Always `"PKR"` in this version |
| `openingFunds` | `BigDecimal` | Normalized scale-2 opening funds |
| `horizonStart` | `LocalDate` | Inclusive horizon start date |
| `horizonEnd` | `LocalDate` | Inclusive horizon end date |
| `totalInflow` | `BigDecimal` | Cumulative planned inflows across the horizon |
| `totalOutflow` | `BigDecimal` | Cumulative planned outflows across the horizon |
| `finalClosingPosition` | `BigDecimal` | Cumulative closing position on `horizonEnd` |
| `additionalFundingRequired` | `BigDecimal` | $\max(0, -\min(\text{closingPosition}))$ |
| `firstShortfallDate` | `LocalDate` | Earliest date with negative closing position (`null` if none) |
| `firstPeakShortfallDate` | `LocalDate` | Earliest date reaching maximum deficit (`null` if none) |
| `recoveryDate` | `LocalDate` | Earliest date after first shortfall remaining non-negative (`null` if none) |
| `shortfallDayCount` | `int` | Number of days with closing position $< 0$ |
| `dailyCurve` | `List<DailyProjectFundingPosition>` | Unmodifiable continuous daily position list |

### 3.4 Daily Position Contract: `DailyProjectFundingPosition`
| Field | Type | Description |
| :--- | :--- | :--- |
| `date` | `LocalDate` | Calendar date |
| `plannedInflows` | `BigDecimal` | Sum of planned inflows on this date ($\ge 0.00$) |
| `plannedOutflows` | `BigDecimal` | Sum of planned outflows on this date ($\ge 0.00$) |
| `netFlow` | `BigDecimal` | `plannedInflows - plannedOutflows` |
| `closingPosition` | `BigDecimal` | Cumulative closing cash position |

---

## 4. Reference Fixture Verification

The normative reference fixture specified for Task A-F07-CALCULATOR:
* **Horizon:** `2026-11-01` through `2026-11-05` (5 calendar days)
* **Opening Funds:** `30000.00` PKR
* **Planned Events:**
  1. `2026-11-01`: Outflow `80000.00`
  2. `2026-11-02`: Inflow `20000.00`
  3. `2026-11-03`: Outflow `20000.00`
  4. `2026-11-04`: Inflow `70000.00`
  5. `2026-11-05`: No events

### Expected vs. Verified Results
| Metric | Specification Target | Verified Result | Status |
| :--- | :--- | :--- | :--- |
| **Day 1 (2026-11-01)** | Inflow 0.00, Outflow 80000.00, Closing **-50000.00** | Inflow 0.00, Outflow 80000.00, Closing **-50000.00** | PASS |
| **Day 2 (2026-11-02)** | Inflow 20000.00, Outflow 0.00, Closing **-30000.00** | Inflow 20000.00, Outflow 0.00, Closing **-30000.00** | PASS |
| **Day 3 (2026-11-03)** | Inflow 0.00, Outflow 20000.00, Closing **-50000.00** | Inflow 0.00, Outflow 20000.00, Closing **-50000.00** | PASS |
| **Day 4 (2026-11-04)** | Inflow 70000.00, Outflow 0.00, Closing **20000.00** | Inflow 70000.00, Outflow 0.00, Closing **20000.00** | PASS |
| **Day 5 (2026-11-05)** | Inflow 0.00, Outflow 0.00, Closing **20000.00** | Inflow 0.00, Outflow 0.00, Closing **20000.00** | PASS |
| **Total Inflow** | `90000.00` | `90000.00` | PASS |
| **Total Outflow** | `100000.00` | `100000.00` | PASS |
| **Final Position** | `20000.00` | `20000.00` | PASS |
| **Funding Required** | `50000.00` | `50000.00` | PASS |
| **First Shortfall** | `2026-11-01` | `2026-11-01` | PASS |
| **First Peak Shortfall**| `2026-11-01` (earliest date of -50000.00) | `2026-11-01` | PASS |
| **Recovery Date** | `2026-11-04` | `2026-11-04` | PASS |
| **Shortfall Days** | `3` (Days 1, 2, and 3) | `3` | PASS |

---

## 5. Validation Behaviour & Error Handling

All input validation failures throw `ProjectFundingGapException` (unchecked `IllegalArgumentException` subclass), providing an optional `field` name and descriptive message:

1. **Null Input:** Rejects `null` plan input object.
2. **Currency:** Rejects null, blank, or non-`"PKR"` currency.
3. **Horizon:** Rejects null `horizonStart`, null `horizonEnd`, or `horizonStart.isAfter(horizonEnd)`.
4. **Opening Funds:**
   - Rejects null (explicit zero is valid; missing funds must fail).
   - Rejects negative amounts ($< 0.00$).
   - Rejects amounts with fractional cents (requiring rounding past 2 decimal places).
5. **Events Collection:**
   - Rejects null `events` list (empty list is valid).
   - Rejects null event elements.
   - Rejects blank or null `eventId`.
   - Rejects duplicate `eventId` within the same plan input.
   - Rejects null event `date`.
   - Rejects event dates outside $[\text{horizonStart}, \text{horizonEnd}]$.
   - Rejects null event `direction`.
   - Rejects null event `amount`.
   - Rejects non-positive event amounts ($\le 0.00$).
   - Rejects event amounts with scale $> 2$ requiring rounding.

---

## 6. Limitations & Integration Boundaries

1. **Internal Foundation Only:** This is an internal calculator engine, not an externally exposed REST API or persistence entity.
2. **No Bank Balance Inference:** Does not query business accounts or assume available bank balances. Opening funds represent only funds explicitly earmarked for this calculation.
3. **No Transaction Linking:** Does not link planned cash events to actual bank transactions, invoices, bills, or accounting journals.
4. **No Commercial Math:** Does not calculate revenue recognition, COGS, tax, Zakat, or profit margins.
5. **Intra-Day Netting Assumption:** Assumes end-of-day netting. If an outflow of 100,000 occurs at 9:00 AM and an inflow of 100,000 arrives at 4:00 PM, the daily closing position is 0.00, though intra-day liquidity was negative during the day.
6. **Recovery Horizon Bounded:** Recovery date indicates non-negative positions through the end of the supplied horizon only. It does not extrapolate or guarantee solvency beyond `horizonEnd`.

---

## 7. Changed Paths & Verification Evidence

### 7.1 Created Files
* `backend/src/main/java/com/app/sme_health_backend/liquidity/ProjectCashFlowDirection.java`
* `backend/src/main/java/com/app/sme_health_backend/liquidity/PlannedProjectCashEvent.java`
* `backend/src/main/java/com/app/sme_health_backend/liquidity/ProjectFundingPlanInput.java`
* `backend/src/main/java/com/app/sme_health_backend/liquidity/DailyProjectFundingPosition.java`
* `backend/src/main/java/com/app/sme_health_backend/liquidity/ProjectFundingGapResult.java`
* `backend/src/main/java/com/app/sme_health_backend/liquidity/ProjectFundingGapException.java`
* `backend/src/main/java/com/app/sme_health_backend/liquidity/ProjectFundingGapCalculator.java`
* `backend/src/test/java/com/app/sme_health_backend/liquidity/ProjectFundingGapCalculatorTest.java`
* `docs/phase2/FUNDING_GAP_CALCULATION.md`

### 7.2 Verification Execution
To prevent interference with Codex's ongoing backend integration runs and avoid lock contention on Maven's shared `target/` directory, tests were executed using an isolated temporary build output directory (`$env:TEMP/liquidity-test-build`) using the established project classpath from `backend/.cp.txt`.

**Test Suite:** `com.app.sme_health_backend.liquidity.ProjectFundingGapCalculatorTest`
**Execution Results:**
* **Containers found:** 5 (Root + 3 nested classes: `ReferenceFixtureTests`, `EdgeCaseTests`, `ValidationTests`)
* **Containers succeeded:** 5
* **Tests found:** 24
* **Tests started:** 24
* **Tests succeeded:** 24
* **Tests failed:** 0
* **Test Scenarios Covered:**
  1. `referenceFixtureMatchesSpecification`: Full 5-day reference fixture validation.
  2. `staticDelegateMatches`: Static `ProjectFundingGapCalculator.calculateGap` equivalence.
  3. `noEventsAndExplicitZeroOpeningFunds`: Zero funding gap with null milestone dates.
  4. `sufficientOpeningFunds`: Zero deficit when opening funds absorb all outflows.
  5. `neverRecoveringPlanLeavesRecoveryNull`: Deficit persisting through horizonEnd leaves recovery date null.
  6. `temporaryRecoveryFollowedByAnotherShortfall`: Detects true final recovery after intermediate bounce.
  7. `recoveryOnFinalDay`: Correct identification of recovery on the exact final horizon date.
  8. `sameDayInflowsAndOutflowsNetted`: Multi-event same-day netting into single daily aggregate.
  9. `repeatedPeakDeficitsSelectEarliestDate`: Earliest date selected when peak deficit recurs.
  10. `shuffledInputOrderInvariant`: Identical results across forward, reversed, and shuffled event inputs.
  11. `decimalAccuracyAndLargeValues`: High-magnitude calculations without floating-point distortion.
  12. `immutableOutputs`: Unmodifiable daily curve and input immutability protection.
  13. `nullInput`: Rejection of null input object.
  14. `currencyValidation`: Rejection of missing, blank, or unsupported currencies (e.g. USD).
  15. `horizonValidation`: Rejection of missing start/end or inverted dates.
  16. `missingOpeningFundsFailsValidation`: Validation failure when opening funds are omitted.
  17. `invalidOpeningFunds`: Rejection of negative opening funds or fractional cents.
  18. `nullEventsList`: Rejection of null events parameter.
  19. `nullEventItem`: Rejection of null event elements within list.
  20. `blankEventId`: Rejection of blank or null event IDs.
  21. `duplicateEventIds`: Rejection of repeated event IDs.
  22. `eventDateOutsideHorizon`: Rejection of event dates before `horizonStart` or after `horizonEnd`.
  23. `missingEventDirection`: Rejection of null cash flow direction.
  24. `invalidEventAmount`: Rejection of null, zero, negative, or unrounded fractional amounts.
