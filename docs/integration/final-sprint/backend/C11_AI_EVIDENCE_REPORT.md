# FinSight Final Sprint — Task 2A Integration Report
## Authoritative Financial Evidence Integration (C11 to Ask FinSight)

**Task ID:** `FINAL-S2A-C11-AI-FINANCIAL-EVIDENCE`
**Date:** 5 November 2026
**Status:** `BACKEND S2A COMPLETE — EDITING_PAUSED`
**Branch:** `dev/suleman`
**Baseline Commit:** `2fa20c3b6ca59d04504290542fdfef21ef7e1da2`
**Author:** Antigravity (Backend Lead, Financial Systems Architect & Security Engineer)

---

## 1. Executive Implementation Summary

Task 2A successfully establishes the authoritative read-only bridge between Developer A's `FinancialQueryFacade` (C11) and Developer B's Ask FinSight financial intelligence system (`MvpFinancialEvidenceAdapter`, `AiQuestionRouter`, `AiText`).

Prior to this task, Ask FinSight answered questions solely from persisted monthly summary snapshots (`MonthlyRecord`, `ScoreResult`, historical 6-month cashflow charts). Questions regarding real-time ledger balances, bank cash positions, overdue customer balances, or payment schedules were either blocked or fell back to historical monthly records.

With this integration:
- **Zero Hallucination / Zero Ledger Mutation:** Ask FinSight queries canonical domain services strictly through `@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)` queries on `FinancialQueryFacade`.
- **Strict Distinction of Provenance:** Financial facts carry explicit provenance modes: `POSTED_ACTUAL` for real-time ledger balances and recognized obligations, `FORECAST` for non-binding recurring projections, `SNAPSHOT` for historical monthly records, and `PLAN` for trend projections.
- **Tenant Isolation & Security Preservation:** AI queries honor server-resolved `BusinessAccessContext` and enforce RBAC permissions (`FINANCIAL_ACCOUNT_READ`, `FINANCIAL_TRANSACTION_READ`). Cache partitioning incorporates tenant, user, role, and evidence fingerprint.
- **Health Score Methodology Untouched:** Canonical `health-score-v1` weights (30/25/20/15/10) remain strictly evaluated on `MonthlyRecord`; ledger projections remain shadow.

---

## 2. Exact Files Changed

### Backend Main Sources (`backend/src/main/**`):
1. **`backend/src/main/java/com/app/sme_health_backend/ai/AiQuestionRouter.java`**:
   - Refined `BLOCKED` pattern to block unsafe actions (`create account`, `settle obligation`, `create invoice`, `post transaction`) and unsupported module drilldowns (`by customer`, `customer balance`, `inventory`, `project financial`, `payroll`).
   - Wired routing for:
     - `"cash_position"`: questions on current cash, bank balance, cash in bank, account balance.
     - `"overdue_receivables"`: questions on overdue receivables, aging amounts, unpaid sales.
     - `"upcoming_obligations"`: questions on upcoming obligations, payables schedule, due dates.
   - Preserved cash buffer routing to `"cashflow"` to maintain complete compatibility with the 100-scenario grounding test baseline.

2. **`backend/src/main/java/com/app/sme_health_backend/ai/AiText.java`**:
   - Added bilingual (English and Urdu) explanation summaries for `"cash_position"`, `"overdue_receivables"`, and `"upcoming_obligations"`.
   - Added localized fact labels to `LABELS` registry for:
     - `current_cash_balance`, `opening_balance`, `net_movements`, `total_accounts`.
     - `total_overdue`, `overdue_count`, `upcoming_receivables`, `upcoming_payables`.
     - `recurring_projection`, `aging_bucket`.
     - Canonical aging buckets: `CURRENT`, `DAYS_1_TO_30`, `DAYS_31_TO_60`, `DAYS_61_TO_90`, `DAYS_OVER_90`, `NOT_YET_DUE`, `DUE_TODAY`, `OVERDUE_1_30`, `OVERDUE_31_60`, `OVERDUE_61_90`, `OVERDUE_91_PLUS`, `UNKNOWN_DUE_DATE`.

3. **`backend/src/main/java/com/app/sme_health_backend/ai/MvpFinancialEvidenceAdapter.java`**:
   - Injected `@Autowired(required = false) FinancialQueryFacade financialQuery` with overloaded constructors (7-arg for backward compatibility, 8-arg for Spring dependency injection).
   - Wired C11 queries:
     - `getCashPosition`: extracts currency summaries (opening, net movements, current balance) and masked account identifiers (`****1234`), tagged with `POSTED_ACTUAL`.
     - `getOverdueReceivables`: extracts total overdue, overdue count, 7-bucket aging summaries, and individual overdue obligations, tagged with `POSTED_ACTUAL`.
     - `getUpcomingObligations`: extracts upcoming receivables/payables totals, recognized scheduled obligations (`POSTED_ACTUAL`), and recurring rule projections (`FORECAST`).
     - Handled typed unavailable queries (`getProjectFinancials`, `getMarginDrivers`) returning capability unavailable reasons with `unsupported_modules` limitation.

### Backend Test Sources (`backend/src/test/**`):
4. **`backend/src/test/java/com/app/sme_health_backend/ai/AiQuestionRouterTests.java`**:
   - Added test `routesAuthoritativeC11FinancialTopicsInBothLanguages()` covering English and Urdu prompts for cash position, overdue receivables, upcoming obligations, and cash buffer disambiguation.
5. **`backend/src/test/java/com/app/sme_health_backend/ai/MvpFinancialEvidenceAdapterTests.java`**:
   - Injected mock `FinancialQueryFacade` and added 5 new unit tests:
     - `authoritativeCashPositionBuildsPostedActualEvidence()`
     - `authoritativeOverdueReceivablesBuildsAgingEvidence()`
     - `authoritativeUpcomingObligationsBuildsActualAndForecastEvidence()`
     - `unavailableFinancialToolReturnsTypedUnavailableReason()`
     - `missingFinancialQueryFallsBackCleanly()`
6. **`backend/src/test/java/com/app/sme_health_backend/ai/AiEvidencePostgreSqlIT.java`**:
   - Added 4 real disposable PostgreSQL integration tests:
     - `testAuthoritativeCashPositionFromRealPostgresData`: verifies cash position against real PostgreSQL `financial_accounts` and `account_movements`.
     - `testAuthoritativeOverdueReceivablesFromRealPostgresData`: verifies overdue aging against real PostgreSQL `financial_obligations`.
     - `testTenantIsolationBetweenBusinessAAndBusinessB`: verifies Tenant A never accesses Tenant B balances.
     - `testFinancialDatabaseRemainsBitForBitIdenticalAcrossAiExplanationQueries`: verifies row counts and checksums across 5 financial tables remain identical after AI explanation execution.

---

## 3. C11 Queries Wired into AI Evidence

| C11 Tool Method | Query DTO | AI Topic | Output Facts Generated | Provenance Mode |
| :--- | :--- | :--- | :--- | :--- |
| `getCashPosition` | `CashPositionQuery(asOfDate, null)` | `cash_position` | `cash_position.<CCY>.balance`, `opening`, `net_movements`, `total_accounts`, `cash_position.acc.<id>` | `POSTED_ACTUAL` |
| `getOverdueReceivables` | `OverdueReceivablesQuery(asOfDate, 50, 0)` | `overdue_receivables` | `overdue_receivables.total`, `overdue_count`, `overdue_receivables.aging.<bucket>`, `overdue_receivables.item.<id>` | `POSTED_ACTUAL` |
| `getUpcomingObligations` | `UpcomingObligationsQuery("ALL", null, null)` | `upcoming_obligations` | `upcoming_obligations.receivables`, `payables`, `overdue_receivables`, `overdue_payables`, `item.<id>` | `POSTED_ACTUAL` |
| *(Recurring Projections)* | *(Embedded in upcoming obligations)* | `upcoming_obligations` | `upcoming_obligations.recurring.<ruleId>.<date>` | `FORECAST` |

---

## 4. Queries Deliberately Left Unavailable

As mandated by FinSight architecture and Phase 2 boundaries, the following tools remain typed `CapabilityStatus.UNAVAILABLE`:

1. **`getProjectFinancials`**:
   - *Status:* `UNAVAILABLE` (`data: null`).
   - *Reason:* Project planning (F05) is owned by Developer B and has not been integrated into the authoritative posting ledger.
   - *AI Limitation:* Returns `"unsupported_modules"` limitation with Developer B ownership reason.
2. **`getProjectFundingGap`**:
   - *Status:* `UNAVAILABLE` (`data: null`).
   - *Reason:* Persisted project cash plans remain unintegrated.
3. **`getMarginDrivers`**:
   - *Status:* `UNAVAILABLE` (`data: null`).
   - *Reason:* Inventory valuation and COGS margin drivers (F08) are unposted catalog items; actual inventory journal accounting is unintegrated.
4. **`getFxSettlementImpact`**:
   - *Status:* `UNAVAILABLE` (`data: null`).
   - *Reason:* Multi-currency realized FX settlement ledger (F11) is not implemented.

---

## 5. Source & Provenance Behavior

| Mode | Semantics in FinSight | When Applied |
| :--- | :--- | :--- |
| `POSTED_ACTUAL` | Legally posted account movements, reconciled balances, and recognized obligations. | Accounts, bank balances, overdue invoices, recognized upcoming obligations. |
| `FORECAST` | Non-binding projected operational estimates (never legally recognized liabilities). | V29 unposted recurring operational rules. |
| `SNAPSHOT` | Monthly summary inputs submitted by SME or confirmed via OCR. | Historical monthly records, canonical health scores, advice items. |
| `PLAN` | Trend projections generated by deterministic forecasting algorithms. | 6-month cashflow trend projections (`CashFlowProjectionResponse`). |
| `UNAVAILABLE` | Explicitly unbuilt or unintegrated modules. | Inventory COGS, project finance, multi-currency realized FX. |

---

## 6. Cache and Authorization Behavior

1. **Tenant Isolation:**
   - Every AI request requires a valid server-resolved `BusinessAccessContext`. AI queries never accept tenant IDs from client parameters.
   - Direct In-JVM invocation of `financialQuery` executes under the caller's context and enforces role permissions:
     - `FINANCIAL_ACCOUNT_READ` for cash position.
     - `FINANCIAL_TRANSACTION_READ` for overdue receivables and upcoming obligations.
2. **Cache Partitioning & Invalidation:**
   - `AiExplanationService` computes an SHA-256 cache key consisting of:
     - `businessId`
     - `userId`
     - `role`
     - `context.fingerprint()` (SHA-256 over topic, language, evidence facts, clauses, limitations, revisions).
   - In `MvpFinancialEvidenceAdapter`, ledger account IDs, obligation IDs, recurring rule IDs, and evaluated timestamps are appended to `revisions`.
   - When ledger balances or obligations change, the fingerprint changes automatically, preventing stale cached responses without cross-tenant collision.

---

## 7. Numerical Evidence & Verification Results

### A. AI Unit & Evaluation Tests (`mvnw test`):
- **`AiGroundingEvaluationTests`**: 101/101 PASSED (0 failures, 0 errors).
  - Preserved full 100-scenario safety grounding baseline, preventing hallucination or leakage.
- **`AiQuestionRouterTests`**: 6/6 PASSED (0 failures, 0 errors).
  - Verifies English/Urdu routing for `cash_position`, `overdue_receivables`, `upcoming_obligations`, and `cashflow`.
- **`MvpFinancialEvidenceAdapterTests`**: 14/14 PASSED (0 failures, 0 errors).
  - Verifies exact decimal representation, currency separation, masked identifiers (`****1234`), aging buckets, and `POSTED_ACTUAL` vs `FORECAST` modes.
- **`AiExplanationServiceTests`**: 10/10 PASSED (0 failures, 0 errors).
- **`AiAvailabilityTests`**: 6/6 PASSED (0 failures, 0 errors).
- **`AiControllerTests`**: 9/9 PASSED (0 failures, 0 errors).
- **`AiResourceLimitsTests`**: 7/7 PASSED (0 failures, 0 errors).
- **`OpenAiResponsesProviderTests`**: 7/7 PASSED (0 failures, 0 errors).

### B. Real Disposable PostgreSQL Integration Tests:
- **`AiEvidencePostgreSqlIT`**: 8/8 PASSED (0 failures, 0 errors).
  - `testAuthoritativeCashPositionFromRealPostgresData`: PASSED (verified PKR 150,000.00 actual balance from `financial_accounts`).
  - `testAuthoritativeOverdueReceivablesFromRealPostgresData`: PASSED (verified PKR 75,000.00 actual overdue obligation from `financial_obligations`).
  - `testTenantIsolationBetweenBusinessAAndBusinessB`: PASSED (verified Tenant A sees 180,000.00, Tenant B sees 25,000.00; zero cross-tenant leakage).
  - `testFinancialDatabaseRemainsBitForBitIdenticalAcrossAiExplanationQueries`: PASSED (row counts across `financial_accounts`, `account_movements`, `financial_obligations`, `monthly_records`, and `score_results` identical before and after AI calls).
  - `persistedEvidenceMatchesCanonicalScoreAndDoesNotMutateAdviceLifecycle`: PASSED.
  - `rejectsAnotherTenantsRecommendationEvenWithValidUuid`: PASSED.
  - `persistedRecordChangesInvalidateCachedExplanations`: PASSED.
  - `realSecurityRequiresSessionMembershipAndCsrfAndSupportsViewerRead`: PASSED.
- **`FinancialQueryPostgreSqlIT`**: 6/6 PASSED (0 failures, 0 errors, 26.56s).

---

## 8. Compilation Results

- **Backend Source Compilation:** 666 main source files compiled cleanly with 0 errors.
- **Backend Test Compilation:** 223 test source files compiled cleanly with 0 errors.
- **Total Tests Executed in Task 2A:** 168 tests passed, 0 failures, 0 errors.

---

## 9. APIs Ready for Codex (Frontend Integration)

The backend endpoint for Ask FinSight explanations is existing, stable, and now backed by authoritative C11 queries:

- **Endpoint:** `POST /api/ai/explain`
- **Security:** Active session cookie (`FINSIGHT_SESSION`) + CSRF header (`X-XSRF-TOKEN`).
- **Authorization:** `OWNER`, `ACCOUNTANT`, `MANAGER`, `VIEWER`.
- **Request Payload:**
```json
{
  "type": "ask",
  "language": "en",
  "question": "What is our current cash position across accounts?",
  "month": null,
  "conversation": []
}
```
- **Response Payload Structure:**
```json
{
  "text": "This explanation reflects the current authoritative cash and bank position from posted account movements.",
  "evidence": [
    {
      "id": "cash_position.PKR.balance",
      "kind": "cash_position",
      "label": "Current cash balance",
      "value": "150000.00",
      "unit": "PKR",
      "period": "2026-11-05",
      "sourceMode": "POSTED_ACTUAL"
    },
    {
      "id": "cash_position.acc.0192e2fb-...",
      "kind": "account",
      "label": "Habib Bank (****1234)",
      "value": "150000.00",
      "unit": "PKR",
      "period": "2026-11-05",
      "sourceMode": "POSTED_ACTUAL"
    }
  ],
  "interpretations": [
    {
      "id": "fact-cash_position.PKR.balance",
      "text": "Current cash balance (2026-11-05): 150000.00",
      "evidenceIds": ["cash_position.PKR.balance"]
    }
  ],
  "considerations": [],
  "limitations": [
    "Recorded associations do not establish why a business event happened..."
  ],
  "provenance": {
    "mode": "simulated",
    "cached": false,
    "evaluatedAt": "2026-11-05T21:31:28Z"
  }
}
```

---

## 10. Remaining Integration Limitations & Blocked Work

1. **Unintegrated Dev B Modules:**
   - Detailed project financial margins (F05/F07) and inventory valuation/COGS (F08) remain unposted in Phase 2 and cannot provide authoritative ledger figures.
2. **Parallel Frontend Development:**
   - Codex is actively working on the frontend. No frontend files were edited.
3. **Git Mutation Lock:**
   - In accordance with team rules, no Git staging, commit, push, or branch change was performed during parallel development.
