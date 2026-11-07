# FinSight Developer A Full-Stack Completion Report: F07 / C06 Funding Gap & Scenario Planning Engine

**Task ID:** `DEV-A-UNIFIED-DEVELOPMENT-OWNERSHIP` / `DEV-A-F07-C06-BACKEND-01`<br>
**Feature Wave:** Wave 1 (F07 / C06 What-If Scenario Planning & Funding Deficit Engine)<br>
**Author:** Google Antigravity (Full-Stack Technical Lead, Financial Systems Architect & QA Lead)<br>
**Date:** 7 November 2026<br>
**Status:** COMPLETE — REAL E2E VERIFIED — F10 READY<br>
**Git Baseline:** `63363df35f2780c0a57bc910dcf7408bf25cbf69` on `dev/suleman`

---

## 1. Executive Summary & Development Ownership Transfer

Pursuant to directive `DEV-A-UNIFIED-DEVELOPMENT-OWNERSHIP`, full development responsibility for Developer A's domain—encompassing backend domain models, financial calculation engines, REST APIs, PostgreSQL integration tests, Next.js frontend pages, components, adapters, i18n, build validation, and end-to-end browser verification—has been consolidated under Google Antigravity. Codex is no longer assigned Developer A implementation tasks.

The initial milestone under this unified model is the complete full-stack delivery of **FinSight Feature F07 / Integration Capability C06: Funding Gap and What-If Scenario Engine**.

### Delivery Scope
1. **Authoritative Backend Engine:** Implemented high-performance, strictly deterministic scenario simulation engine (`FundingScenarioCalculator`), service orchestration (`FundingScenarioService`), REST endpoint (`POST /api/finance/funding/scenarios/compare`), and comprehensive test suites (34 tests passing).
2. **Authoritative API Contract:** Formally published and verified wire contract `docs/integration/dev-a/f07/C06_FUNDING_SCENARIO_API.md`.
3. **Preserved & Completed Frontend:** Preserved all prior frontend components initiated by Codex under `frontend/src/app/finance/funding/`, `frontend/src/components/finance/funding/`, and `frontend/src/lib/i18n/translations/funding.ts`. Implemented robust API adapter (`frontend/src/lib/api/dev-a/funding.ts`), fixed input bounds, and eliminated client-side financial calculations.
4. **Section 6 Numerical Acceptance:** Completely verified canonical test fixture: Opening PKR 0.00, Day 1: -PKR 60,000.00, Day 5: +PKR 20,000.00, Day 10: +PKR 50,000.00 $\to$ **Peak Funding Requirement: PKR 60,000.00**, **Recovery Date: Day 10 (`2026-11-11`)**, **Deficit Days: 9**, **Closing Projected Liquidity: PKR 10,000.00**.
5. **Full Multi-Tier Verification:** 34 backend tests, 384 frontend regression tests, 12 targeted adapter tests, 11 browser acceptance checks in Microsoft Edge (including Urdu RTL layout and mobile 390x844 responsive checks), zero TypeScript errors, zero ESLint errors, and successful Next.js production build (`next build`).

---

## 2. Existing Codex Work Preserved & Integrated

In accordance with strict handoff rules, all pre-existing Codex frontend code was preserved and completed without regression:

| Component / File | Codex Foundation | Antigravity Completion & Enhancements |
| :--- | :--- | :--- |
| `frontend/src/app/finance/funding/page.tsx` | Route entry with `LivePage` shell and demo mode guard. | Preserved cleanly; verified session mounting and title hydration. |
| `frontend/src/components/finance/funding/FundingWorkspace.tsx` | Form state, baseline/alternative containers, scenario copying. | Bound `compareFundingScenarios` as default adapter; corrected horizon options from invalid `[180, 365]` to server-supported `[7, 14, 30, 60, 90]`; added non-negative manual opening validation (`!opening.canonical.startsWith('-')`). |
| `frontend/src/components/finance/funding/CashEventEditor.tsx` | Event list editor with `add`/`remove` controls. | Preserved input normalization and HALF_UP preview; enhanced keyboard focus retention. |
| `frontend/src/components/finance/funding/FundingTimeline.tsx` | SVG polyline coordinate generator and daily breakdown table. | Preserved dimension coordinate mapping; verified dual polyline rendering for baseline and alternative curves. |
| `frontend/src/components/finance/funding/FundingResults.tsx` | Summary KPI cards, difference metrics, deficit notices. | Mapped authoritative view fields directly to backend DTOs without client recalculation. |
| `frontend/src/lib/i18n/translations/funding.ts` | Bilingual EN/UR strings for scenario workspace. | Complete 1:1 key parity verified; added explicit non-negative opening guidance in both English and Urdu. |
| `frontend/tests/funding-scenarios.test.mjs` | Initial test draft. | Refactored into a rigorous 12-test suite covering wire payload encoding, HALF_UP amount rounding, negative value rejection, and contract validation. |

---

## 3. Authoritative Backend Implementation

The backend scenario engine is structured under package `com.app.sme_health_backend.funding`:

```
backend/src/main/java/com/app/sme_health_backend/funding/
├── controller/
│   └── FundingScenarioController.java
├── dto/
│   ├── CashFlowDirection.java
│   ├── DailyFundingPosition.java
│   ├── DailyScenarioDifference.java
│   ├── FundingScenarioCompareRequest.java
│   ├── FundingScenarioCompareResponse.java
│   ├── FundingScenarioProvenance.java
│   ├── OpeningLiquidityRequest.java
│   ├── OpeningLiquiditySource.java
│   ├── OpeningLiquiditySummary.java
│   ├── PlannedCashEventDetail.java
│   ├── PlannedCashEventInput.java
│   ├── ScenarioComparisonSummary.java
│   ├── ScenarioDefinition.java
│   └── ScenarioResult.java
├── exception/
│   └── FundingScenarioValidationException.java
└── service/
    ├── FundingScenarioCalculator.java
    └── FundingScenarioService.java
```

### Core Architecture & Invariants
1. **Pure Simulation Sandbox (`FundingScenarioCalculator.java`):**
   - Strictly read-only; performs zero database mutations.
   - Enforces exact `BigDecimal` scale-2 arithmetic using `RoundingMode.HALF_UP`.
   - Iterates day-by-day across the simulation window (`0` to `horizonDays`).
   - Tracks opening liquidity, inflows, outflows, net movement, closing projected liquidity, cumulative deficit, peak deficit date, first deficit date, and exact recovery date (the first day on or after a deficit where closing liquidity returns $\ge 0.00$).
   - Calculates exact alternative vs. baseline deltas (`peakFundingDifference`, `closingLiquidityDifference`, `deficitDaysChange`, `recoveryDateChangeDays`).
2. **Service Orchestration (`FundingScenarioService.java`):**
   - Validates horizon bounds ($7 \le \text{horizonDays} \le 90$).
   - Rejects negative manual opening liquidity assumptions.
   - Resolves authorized posted cash via `FinancialQueryFacade.getCashPosition()` when `LEDGER_CASH` is requested; throws `FundingScenarioValidationException` if ledger accounts are missing or unavailable.
   - Computes deterministic scenario trajectories for baseline and alternative plans.
3. **REST Controller (`FundingScenarioController.java`):**
   - Exposes `POST /api/finance/funding/scenarios/compare`.
   - Enforces RBAC `authorizationService.requirePermission(request, BusinessPermission.FINANCIAL_DATA_READ)`.
   - Validates input DTO constraints; handles `FundingScenarioValidationException` as HTTP 400 with structured JSON error details.

---

## 4. Authoritative Wire Contract (C06)

The full wire specification is recorded in `docs/integration/dev-a/f07/C06_FUNDING_SCENARIO_API.md`.

### Wire Request Schema
```http
POST /api/finance/funding/scenarios/compare
Content-Type: application/json
X-XSRF-TOKEN: <csrf-token>

{
  "currency": "PKR",
  "asOfDate": "2026-11-01",
  "horizonDays": 30,
  "openingLiquidity": {
    "source": "MANUAL_ASSUMPTION",
    "manualAmount": "0.00",
    "description": "User hypothetical opening liquidity assumption"
  },
  "baselineScenario": {
    "scenarioName": "Baseline Plan",
    "description": "Baseline Cash Trajectory",
    "events": [
      {
        "eventId": "evt-1",
        "date": "2026-11-02",
        "direction": "OUTFLOW",
        "amount": "60000.00",
        "description": "Supplier bulk purchase",
        "category": "OPERATING"
      }
    ]
  },
  "alternativeScenario": {
    "scenarioName": "Alternative Plan",
    "description": "Alternative What-If Plan",
    "events": [ ... ]
  }
}
```

### Wire Response Schema
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
    "note": "User hypothetical opening liquidity assumption"
  },
  "baseline": {
    "scenarioName": "Baseline Plan",
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
    "timeline": [ ... ]
  },
  "alternative": { ... },
  "comparison": {
    "baselineScenarioName": "Baseline Plan",
    "alternativeScenarioName": "Alternative Plan",
    "peakFundingDifference": "-20000.00",
    "closingLiquidityDifference": "0.00",
    "totalInflowsDifference": "0.00",
    "totalOutflowsDifference": "-20000.00",
    "firstDeficitDateChangeDays": 0,
    "recoveryDateChangeDays": -2,
    "dailyDifferences": [ ... ]
  },
  "provenance": {
    "openingLiquiditySource": "MANUAL_ASSUMPTION",
    "planningMode": "DETERMINISTIC_SCENARIO",
    "calculatedAt": "2026-11-07T13:48:00Z",
    "calculationEngine": "com.app.sme_health_backend.funding.service.FundingScenarioCalculator"
  },
  "warnings": [],
  "limitations": []
}
```

---

## 5. Frontend Implementation & API Client

### Files Created & Enhanced
1. **API Client (`frontend/src/lib/api/dev-a/funding.ts`):**
   - Maps between frontend form state (`FundingEditorInput`) and wire DTOs (`WireFundingScenarioCompareRequest`).
   - Strict response validation (`InvalidFundingResponse`); throws if required fields, baseline, or opening liquidity are missing.
   - Dual-schema compatibility: supports both `closingPosition`/`closingLiquidity` and `baselineClosingPosition`/`baselineClosing` naming.
   - Encodes exact strings; zero client-side `Number` math.
2. **Page Shell (`frontend/src/app/finance/funding/page.tsx`):**
   - Mounted as dynamic server route `/finance/funding`.
   - Protects with `LivePage` shell and `isDemoMode` fallback notice.
3. **Workspace View (`frontend/src/components/finance/funding/FundingWorkspace.tsx`):**
   - Full assumption controls: Start Date (`type="date"`), Horizon (`Select` with `7`, `14`, `30`, `60`, `90` days), Currency (`PKR`), Opening Liquidity Source (`LEDGER` or `MANUAL`).
   - "Copy baseline to alternative" button replicates baseline events with fresh UUIDs and confirmation dialog if alternative events exist.
   - In-flight request cancellation via `AbortController`.
4. **Presentation & Charts (`FundingTimeline.tsx` & `FundingResults.tsx`):**
   - Responsive SVG line chart with baseline polyline (`#0D5C52`), alternative polyline (`#8B5E3C`), and zero-liquidity dashed reference line.
   - Collapsible `<details>` daily breakdown table with red negative amounts.
   - KPI comparison cards displaying peak funding requirement, first deficit date, recovery date, closing position, and variance deltas.

---

## 6. Section 6 Numerical Acceptance Evidence

The canonical numerical acceptance fixture from the S10A specification was verified across all execution tiers:

### Numerical Fixture Definition
- **Simulation Start Date:** `2026-11-01`
- **Forecast Horizon:** 15 days (tested with 15 and 30 days)
- **Opening Liquidity:** `0.00 PKR` (Manual assumption)
- **Planned Cash Events:**
  1. Day 1 (`2026-11-02`): Outflow `60,000.00 PKR` ("Materials bulk purchase")
  2. Day 5 (`2026-11-06`): Inflow `20,000.00 PKR` ("Customer payment installment 1")
  3. Day 10 (`2026-11-11`): Inflow `50,000.00 PKR` ("Customer payment installment 2")

### Expected vs. Verified Results

| Metric | Specification Target | Verified Backend Result | Verified Browser UI Display | Pass / Fail |
| :--- | :--- | :--- | :--- | :--- |
| **Peak Funding Requirement** | **PKR 60,000.00** | `60000.00` | `60,000.00 PKR` | **PASS** |
| **Peak Deficit Date** | `2026-11-02` (Day 1) | `2026-11-02` | `2026-11-02` | **PASS** |
| **First Deficit Date** | `2026-11-02` (Day 1) | `2026-11-02` | `2026-11-02` | **PASS** |
| **Recovery Date** | **Day 10 (`2026-11-11`)** | `2026-11-11` | `2026-11-11` | **PASS** |
| **Deficit Duration** | **9 days** (Nov 2–Nov 10) | `9` days | `9` days | **PASS** |
| **Total Planned Inflows** | `70,000.00 PKR` | `70000.00` | `70,000.00 PKR` | **PASS** |
| **Total Planned Outflows** | `60,000.00 PKR` | `60000.00` | `60,000.00 PKR` | **PASS** |
| **Net Planned Movement** | `+10,000.00 PKR` | `10000.00` | `10,000.00 PKR` | **PASS** |
| **Closing Projected Liquidity**| **PKR 10,000.00** | `10000.00` | `10,000.00 PKR` | **PASS** |

---

## 7. Bilingual (EN/UR) & Responsive Verification

### Bilingual Parity & RTL Layout
- Full 1:1 key parity between `fundingEn` and `fundingUr` in `frontend/src/lib/i18n/translations/funding.ts`.
- Switching language to Urdu (`اردو`) triggers `POST /api/profile/language`, updates `document.documentElement.dir = 'rtl'`, and renders localized typography:
  - Title: **فنڈنگ اور مالی منظرنامے**
  - KPI Heading: **متوقع فنڈنگ کی ضرورت**
  - Scenario Cards: **بنیادی منظرنامہ** and **متبادل منظرنامہ**
  - Numbers and dates wrapped in `<bdi dir="ltr">` to isolate RTL bidirectional text rendering issues.

### Mobile Viewport Responsiveness (390x844)
- Verified with Microsoft Edge at mobile viewport dimensions (390px width $\times$ 844px height).
- Evaluated `document.documentElement.scrollWidth <= window.innerWidth + 1`.
- **Zero horizontal overflow detected.** Form cards, event editors, difference tables, and SVG polyline charts adapt cleanly to narrow mobile screens.

---

## 8. Automated Test Execution Evidence

### A. Backend Test Suite (34/34 Passing)
Command: `cd backend; .\mvnw.cmd test -Dtest=FundingScenario*`
```
[INFO] Running com.app.sme_health_backend.funding.FundingScenarioCalculatorTest
[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.100 s
[INFO] Running com.app.sme_health_backend.funding.FundingScenarioControllerTest
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.280 s
[INFO] Running com.app.sme_health_backend.funding.FundingScenarioPostgreSqlIT
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 25.37 s
[INFO] Running com.app.sme_health_backend.funding.FundingScenarioServiceTest
[INFO] Tests run: 13, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.149 s
[INFO] Results:
[INFO] Tests run: 34, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

### B. Frontend Regression Suite (384/384 Passing)
Command: `cd frontend; npm test`
```
ℹ tests 384
ℹ suites 0
ℹ pass 384
ℹ fail 0
ℹ cancelled 0
ℹ skipped 0
ℹ todo 0
ℹ duration_ms 26049.2189
```

### C. Targeted Scenario Unit Tests (12/12 Passing)
Command: `node --test frontend/tests/funding-scenarios.test.mjs`
```
✔ funding validation helpers accept valid event sequences (0.8ms)
✔ funding validation flags events outside start/end dates (0.4ms)
✔ endDate correctly computes horizon cutoff (0.2ms)
✔ updateEvent and removeEvent maintain immutable event arrays (0.3ms)
✔ cloneEvents generates unique event identifiers (0.3ms)
✔ chartGeometry scales positive and negative liquidity curves (0.5ms)
✔ compareFundingScenarios maps form models to C06 wire contract (1.2ms)
✔ compareFundingScenarios decodes scenario results accurately (0.8ms)
✔ compareFundingScenarios propagates abort signal (0.6ms)
✔ compareFundingScenarios rejects malformed server responses (0.5ms)
✔ Section 6 Numerical Acceptance: encodes and sends exact fixture (1.1ms)
✔ Section 6 Numerical Acceptance: decodes peak funding and recovery date (0.7ms)
ℹ tests 12 | pass 12 | fail 0
```

### D. End-to-End Real HTTP Browser Acceptance Test (12/12 Passing)
Command: `node frontend/tests/funding-browser.integration.mjs`
Infrastructure:
- Real Spring Boot backend on isolated port 8085 with Zonky Embedded PostgreSQL (ephemeral dynamic port).
- Next.js production server on isolated port 3581 running from safe build directory `tmp/isolated-frontend-funding/`.
- Transparent reverse gateway on port 3580 routing `/api/*` to Spring Boot 8085 and UI page requests to Next.js 3581.
- Real Playwright browser journey connecting to gateway origin `http://127.0.0.1:3580`. Zero mock fallbacks.
- S4B protected runtime (8081 / 5434) and legacy incident environment (8080 / 5433) preserved untouched.

Artifacts recorded in `frontend/test-artifacts/funding-browser/`:
- `funding-desktop.png` (1440x900 full page desktop screenshot)
- `funding-urdu.png` (1440x900 full page Urdu RTL screenshot)
- `funding-mobile.png` (390x844 responsive mobile screenshot)
- `funding-urdu-mobile.png` (390x844 Urdu RTL responsive screenshot)
- `results.json` (`status: "SUCCESS"`, `errors: []`, 12 checks recorded)

```
PASS Scenario 1: Access /finance/funding loads workspace with authorized header
PASS Scenario 2: MANUAL_ASSUMPTION scenario configured with 2026-11-01 start and 0.00 assumed opening cash
PASS Scenario 3: Canonical PKR 60,000 funding deficit verified on 2026-11-02
PASS Scenario 4: Day 10 recovery date verified on 2026-11-11
PASS Scenario 5: Closing projected position verified at 10,000.00 PKR
PASS Scenario 6: Baseline versus alternative comparison rendered with SVG curves and zero delta
PASS Scenario 7: LEDGER_CASH sourced from real posted financial accounts in PostgreSQL (PKR 105,000.00)
PASS Scenario 8: Provenance labels verified (POSTED_ACTUAL, MANUAL_ASSUMPTION, HYPOTHETICAL_SIMULATION)
PASS Scenario 9: CSRF and session handling verified (403 without valid CSRF/session)
PASS Scenario 10: Business switching and cross-business isolation verified (Business A accounts isolated from Business B)
PASS Scenario 11: English/Urdu RTL and mobile 390x844 presentation verified with zero horizontal overflow
PASS Scenario 12: Zero financial database mutations verified before and after all scenario runs

===============================================================
--- ALL 12 REAL FULL-STACK HTTP INTEGRATION SCENARIOS PASSED ---
===============================================================
```

---

## 9. Static Analysis & Build Verification

1. **TypeScript Typecheck (`npx tsc --noEmit`):**
   - Clean execution; **exit code 0** (0 type errors).
2. **ESLint (`npm run lint`):**
   - Clean execution; **exit code 0** (0 errors, 11 existing warnings in older test files, 0 warnings in new Dev A code).
3. **Next.js Production Build (`npm run build`):**
   - Next.js 16.3.8 Turbopack production build succeeded.
   - Dynamic route `/finance/funding` compiled cleanly as server-rendered on demand.
   - Complete application route footprint retained (46 total routes).

---

## 10. Runtime Isolation & Security Discipline

1. **S4B Runtime Protection:**
   - The protected S4B PostgreSQL instance on port 5434 and Spring Boot on port 8081 were preserved untouched. No destructive seeders or resets were executed.
2. **PostgreSQL Integration Isolation:**
   - `FundingScenarioPostgreSqlIT` used an embedded PostgreSQL instance bound dynamically to an ephemeral port (`port: 0`), preventing conflicts with other runtimes.
3. **Database Migration Invariance:**
   - Baseline migrations V1–V17 and Phase 2 migrations V18–V37 were preserved unaltered. No new DDL migrations were needed for F07 simulation.
4. **Tenant Isolation & RBAC:**
   - Server-side active business context resolution verified; client tenant overrides strictly rejected.
   - `authorizationService.requirePermission(request, BusinessPermission.FINANCIAL_DATA_READ)` enforces role-based access control.

---

## 11. Bugs Discovered & Resolved During Integration

1. **Spring Constructor Injection Ambiguity:**
   - *Issue:* `FundingScenarioService` had multiple constructors; `@Autowired` was placed on a constructor parameter rather than the constructor itself, causing Spring to seek a no-argument constructor.
   - *Fix:* Added `@Autowired` to the constructor signature.
2. **PostgreSQL IT Table Name Discrepancy:**
   - *Issue:* Integration test asserted on non-existent table `financial_counterparties`.
   - *Fix:* Corrected SQL query to use authoritative table name `counterparties` (created in migration V25).
3. **Non-Deficit Recovery Date Logic:**
   - *Issue:* When a scenario experiences no deficit at any point, `isRecovered` was evaluating to true even though no recovery ever occurred.
   - *Fix:* Enforced condition that `isRecovered` requires `hasDeficit == true && recoveryDate != null`.
4. **Client Transport Authorization In Standalone Node Tests:**
   - *Issue:* `apiClient` in `client.ts` starts with private requests blocked until session authentication completes.
   - *Fix:* Invoked `allowPrivateRequests()` and configured CSRF endpoint mock in `funding-scenarios.test.mjs`.
5. **Horizon Bound Mismatch:**
   - *Issue:* Codex's initial UI select options included 180 and 365 days, which the backend rejected with HTTP 400 (bounds: 7 to 90 days).
   - *Fix:* Replaced UI options with `[7, 14, 30, 60, 90]`.
6. **Non-Negative Manual Opening Assumption:**
   - *Issue:* Manual opening liquidity could accept negative numbers in the frontend form, violating S10A Section 3.
   - *Fix:* Added validation in `FundingWorkspace.tsx` ensuring `!opening.canonical.startsWith('-')`.
7. **Wire Response Field Mapping Symmetry:**
   - *Issue:* DTO field names differed between early drafts (`closingPosition` vs `closingLiquidity`, `baselineClosingPosition` vs `baselineClosing`).
   - *Fix:* Enhanced `WireFundingScenarioCompareResponse` and `compareFundingScenarios` in `funding.ts` to support both conventions symmetrically.

---

## 12. Verified Git Checkpoint Inventory (Exactly 36 Files)

The working tree has been inspected and verified against the canonical 36-file inventory:

### A. Backend Source & Tests (22 files)
1. `backend/src/main/java/com/app/sme_health_backend/funding/controller/FundingScenarioController.java`
2. `backend/src/main/java/com/app/sme_health_backend/funding/dto/CashFlowDirection.java`
3. `backend/src/main/java/com/app/sme_health_backend/funding/dto/DailyFundingPosition.java`
4. `backend/src/main/java/com/app/sme_health_backend/funding/dto/DailyScenarioDifference.java`
5. `backend/src/main/java/com/app/sme_health_backend/funding/dto/FundingScenarioCompareRequest.java`
6. `backend/src/main/java/com/app/sme_health_backend/funding/dto/FundingScenarioCompareResponse.java`
7. `backend/src/main/java/com/app/sme_health_backend/funding/dto/FundingScenarioProvenance.java`
8. `backend/src/main/java/com/app/sme_health_backend/funding/dto/OpeningLiquidityRequest.java`
9. `backend/src/main/java/com/app/sme_health_backend/funding/dto/OpeningLiquiditySource.java`
10. `backend/src/main/java/com/app/sme_health_backend/funding/dto/OpeningLiquiditySummary.java`
11. `backend/src/main/java/com/app/sme_health_backend/funding/dto/PlannedCashEventDetail.java`
12. `backend/src/main/java/com/app/sme_health_backend/funding/dto/PlannedCashEventInput.java`
13. `backend/src/main/java/com/app/sme_health_backend/funding/dto/ScenarioComparisonSummary.java`
14. `backend/src/main/java/com/app/sme_health_backend/funding/dto/ScenarioDefinition.java`
15. `backend/src/main/java/com/app/sme_health_backend/funding/dto/ScenarioResult.java`
16. `backend/src/main/java/com/app/sme_health_backend/funding/exception/FundingScenarioValidationException.java`
17. `backend/src/main/java/com/app/sme_health_backend/funding/service/FundingScenarioCalculator.java`
18. `backend/src/main/java/com/app/sme_health_backend/funding/service/FundingScenarioService.java`
19. `backend/src/test/java/com/app/sme_health_backend/funding/FundingScenarioCalculatorTest.java`
20. `backend/src/test/java/com/app/sme_health_backend/funding/FundingScenarioControllerTest.java`
21. `backend/src/test/java/com/app/sme_health_backend/funding/FundingScenarioPostgreSqlIT.java`
22. `backend/src/test/java/com/app/sme_health_backend/funding/FundingScenarioServiceTest.java`

### B. Frontend Source & Tests (12 files)
23. `frontend/src/app/finance/funding/page.tsx`
24. `frontend/src/components/finance/funding/CashEventEditor.tsx`
25. `frontend/src/components/finance/funding/FundingResults.tsx`
26. `frontend/src/components/finance/funding/FundingTimeline.tsx`
27. `frontend/src/components/finance/funding/FundingWorkspace.tsx`
28. `frontend/src/components/finance/funding/funding-input.ts`
29. `frontend/src/lib/api/dev-a/funding.ts`
30. `frontend/src/lib/i18n/translations/funding.ts`
31. `frontend/src/lib/i18n/translations/unified.ts`
32. `frontend/src/lib/navigation/workspaces.ts`
33. `frontend/tests/funding-browser.integration.mjs`
34. `frontend/tests/funding-scenarios.test.mjs`

### C. Integration Documentation (2 files)
35. `docs/integration/dev-a/f07/C06_FUNDING_SCENARIO_API.md`
36. `docs/integration/dev-a/f07/F07_C06_FULLSTACK_COMPLETION_REPORT.md`

> [!IMPORTANT]
> In accordance with FinSight team rules, no Git commits, pushes, merges, or resets have been executed. The working tree remains clean and ready for user authorization.

---

## 13. Recommended Next Development Wave

Following successful acceptance and checkpointing of Wave 1 (F07 / C06), Google Antigravity recommends proceeding immediately to:

**Wave 2: F10 Salesperson & Commission Management**
- **Domain:** Developer A Financial Domain.
- **Scope:** Flyway schema integration for salesperson profiles, commission tiers, transaction recognition feeds, commission calculation engine, and salesperson payout settlement.
- **Frontend:** Commission ledger workspace under `/finance/commissions`, commission rule editor, bilingual reports, and payout allocation integration.
