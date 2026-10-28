# FinSight Financial Query Facade (C11 V1)
## Authorized Read-Only Financial Intelligence & Developer B AI Copilot Contract

**Document Version:** `1.1`
**Contract Version (`CONTRACT_VERSION`):** `"1.0"`
**Date:** 29 October 2026
**Status:** `INTEGRATION_VERIFIED — READY_FOR_LOCAL_GIT_CHECKPOINT_REVIEW`
**Developer B AI Copilot Integration Status:** `PENDING_FATIMA_ORCHESTRATOR_INTEGRATION` (Contract published on `dev/suleman`; formal cross-team sign-off and AI orchestrator wiring pending)
**Package:** `com.app.sme_health_backend.financialquery`
**Flyway Baseline:** V29 (`V29__create_recurring_transactions.sql`) — *No new database migrations required*

---

## 1. Executive Summary & Architectural Invariants

`FinancialQueryFacade` provides a secure, deterministic, evidence-backed, strictly read-only financial query layer over FinSight's canonical Phase 2 domain services. It establishes the backend contract for Developer B's (Fatima's) future AI Copilot orchestration while enforcing six non-negotiable invariants:

1. **Single Backend Financial Authority:** Consumes canonical repositories and calculators (`FinancialAccountRepository`, `AccountMovementRepository`, `CurrencyRegistry`, `FinancialObligationService`, `ObligationAgingCalculator`, `PaymentPlanService`, `RecurrenceCalculator`). Never introduces a parallel calculation engine or floating-point arithmetic.
2. **Strict Read-Only Safety:** Annotated with `@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)`. Queries never create transactions, generate drafts, reverse postings, modify obligations, apply advances, trigger recurring jobs, reconcile bank rows, update `MonthlyRecord` / `ScoreResult`, or write audit mutations.
3. **Dual-Layer Tenant & RBAC Enforcement:** Enforces active server-resolved `BusinessAccessContext` and `BusinessAuthorizationService` permissions both at the HTTP controller boundary and inside every `FinancialQueryFacadeService` method so direct in-JVM AI Copilot calls cannot bypass RBAC or supply cross-tenant `businessId` overrides.
4. **Exact Currency Scale & Non-Aggregation:** Serializes `BigDecimal` amounts as exact canonical decimal strings matching each currency's ISO scale (`JPY`: 0, `PKR`/`USD`/`EUR`/`GBP`/`AED`/`SAR`: 2, `BHD`: 3) via `RoundingMode.UNNECESSARY`. Multi-currency cash positions remain separated per currency and are never summed without an approved FX rate policy.
5. **Recognition vs. Projection Discipline:** Scheduled installments absorb partial obligation settlements chronologically without parent/installment double counting. V29 recurring templates and unposted occurrences are segregated into `recurringProjections` (`isEstimate: true`) and never counted in `totalUpcomingPayables` or `totalUpcomingReceivables`.
6. **Honest Unavailable Capabilities:** Tools blocked on Developer B commercial modules or unbuilt FX posting return typed `CapabilityStatus.UNAVAILABLE` envelopes with `data: null` and explicit dependency explanations—never mock fixtures or misleading `"0.00"` amounts.

---

## 2. Capability Matrix (V1)

| Tool / Method | Status | Required Permission | Canonical Services Reused | V1 Behavior & Dependency Notes |
| :--- | :--- | :--- | :--- | :--- |
| `getCapabilities` | **AVAILABLE** | `FINANCIAL_DATA_READ` | `FinancialQueryFacadeService` | Lists all 7 planned financial tools with live `AVAILABLE` / `UNAVAILABLE` descriptors and reasons. |
| `getCashPosition` | **AVAILABLE** | `FINANCIAL_ACCOUNT_READ` | `FinancialAccountRepository`, `AccountMovementRepository`, `CurrencyRegistry` | Batch-loads movements up to `asOfDate` (`m.businessId` + `m.effectiveDate <= cutoff`), computes per-account and per-currency `openingBalance`, `netMovements`, and `currentBalance`, masks account identifiers (`****1234`), and excludes drafts, unpaid obligations, and recurring rules. |
| `getOverdueReceivables` | **AVAILABLE** | `FINANCIAL_TRANSACTION_READ` | `FinancialObligationService`, `ObligationAgingCalculator`, `FinancialObligationRepository`, `CounterpartyRepository` | Evaluates 7-bucket canonical aging as of `asOfDate`, reflects partial settlements, customer advance applications, reversed settlements, and reversed advance applications, and supports bounded `limit` + `offset` pagination. |
| `getUpcomingObligations` | **AVAILABLE** | `FINANCIAL_TRANSACTION_READ` | `PaymentPlanService`, `FinancialObligationRepository`, `RecurringRuleOccurrenceRepository`, `RecurringRuleRepository`, `RecurrenceCalculator` | Evaluates recognized receivables/payables over `[fromDate, toDate]` ($\le 90$ days) with chronological partial-settlement absorption across installments (zero parent/installment double counting). Separately projects `ACTIVE` V29 recurring rules + `SCHEDULED`/`DRAFT_READY` occurrences as non-binding estimates (`isEstimate: true`). |
| `getProjectFinancials` | **UNAVAILABLE** | `FINANCIAL_DATA_READ` | *Blocked on Dev B F05* | Returns `CapabilityStatus.UNAVAILABLE` (`data: null`): *"Project finance domain (F05) is owned by Developer B and has not yet been integrated into authoritative financial services."* |
| `getProjectFundingGap` | **UNAVAILABLE** | `FINANCIAL_DATA_READ` | *Blocked on F05/F07 persistence* | Pure `ProjectFundingGapCalculator` exists in `liquidity`, but persisted project cash plans are unintegrated. Returns `CapabilityStatus.UNAVAILABLE` (`data: null`). |
| `getMarginDrivers` | **UNAVAILABLE** | `FINANCIAL_DATA_READ` | *Blocked on Dev B F06/F08* | Returns `CapabilityStatus.UNAVAILABLE` (`data: null`): *"Inventory valuation and COGS margin drivers (F08) are owned by Developer B and have not yet been integrated."* |
| `getFxSettlementImpact` | **UNAVAILABLE** | `FINANCIAL_DATA_READ` | *Blocked on F11 FX ledger* | Returns `CapabilityStatus.UNAVAILABLE` (`data: null`): *"Multi-currency realized FX settlement impact (F11) is not yet implemented."* |

---

## 3. Developer B (Fatima) Integration Contract Specification

### 3.1 Interface & Schema Version
- **Java Interface:** `com.app.sme_health_backend.financialquery.FinancialQueryFacade`
- **Contract Version Constant:** `FinancialQueryFacadeService.CONTRACT_VERSION = "1.0"`
- Every envelope and capability response echoes `"contractVersion": "1.0"`. Breaking field changes require bumping `contractVersion`.

### 3.2 Internal Java Invocation vs. Authenticated HTTP Endpoints

Fatima's AI Copilot orchestrator runs inside the FinSight backend or authenticated gateway and can consume `FinancialQueryFacade` directly as a Spring bean (recommended for zero network serialization overhead) or via protected HTTP `GET` routes:

```java
public interface FinancialQueryFacade {
    CapabilitiesResponse getCapabilities(BusinessAccessContext context);
    FinancialQueryEnvelope<CashPositionResult> getCashPosition(BusinessAccessContext context, CashPositionQuery query);
    FinancialQueryEnvelope<OverdueReceivablesResult> getOverdueReceivables(BusinessAccessContext context, OverdueReceivablesQuery query);
    FinancialQueryEnvelope<UpcomingObligationsResult> getUpcomingObligations(BusinessAccessContext context, UpcomingObligationsQuery query);
    FinancialQueryEnvelope<Void> getProjectFinancials(BusinessAccessContext context, UUID projectId);
    FinancialQueryEnvelope<Void> getProjectFundingGap(BusinessAccessContext context, UUID projectId);
    FinancialQueryEnvelope<Void> getMarginDrivers(BusinessAccessContext context, String query);
    FinancialQueryEnvelope<Void> getFxSettlementImpact(BusinessAccessContext context, String query);
}
```

| Facade Method | HTTP Endpoint | Request Query Parameters |
| :--- | :--- | :--- |
| `getCapabilities` | `GET /api/finance/query/capabilities` | None |
| `getCashPosition` | `GET /api/finance/query/cash-position` | `asOfDate` (`YYYY-MM-DD`, optional; defaults to `LocalDate.now(Asia/Karachi)`), `accountType` (`CASH` \| `BANK` \| `DIGITAL_WALLET`, optional) |
| `getOverdueReceivables` | `GET /api/finance/query/overdue-receivables` | `asOfDate` (`YYYY-MM-DD`, optional; defaults to Karachi today), `limit` (`1..100`, optional; default `50`), `offset` (`>= 0`, optional; default `0`) |
| `getUpcomingObligations` | `GET /api/finance/query/upcoming-obligations` | `direction` (`ALL` \| `RECEIVABLE` \| `PAYABLE`, optional; default `ALL`), `fromDate` (`YYYY-MM-DD`, optional; default Karachi today), `toDate` (`YYYY-MM-DD`, optional; default `fromDate + 30 days`) |
| `getProjectFinancials` | `GET /api/finance/query/project-financials` | `projectId` (UUID, optional) |
| `getProjectFundingGap` | `GET /api/finance/query/project-funding-gap` | `projectId` (UUID, optional) |
| `getMarginDrivers` | `GET /api/finance/query/margin-drivers` | `query` (String, optional, $\le 500$ chars) |
| `getFxSettlementImpact` | `GET /api/finance/query/fx-settlement-impact` | `query` (String, optional, $\le 500$ chars) |

### 3.3 Request DTO Schemas

- `CashPositionQuery(String asOfDate, String accountType)`
- `OverdueReceivablesQuery(String asOfDate, Integer limit, Integer offset)` *(also provides 2-arg constructor `(asOfDate, limit)` defaulting `offset = 0`)*
- `UpcomingObligationsQuery(String direction, String fromDate, String toDate)`

### 3.4 Standard Envelope, Completeness, Freshness & Evidence Schemas

Every tool method (except `getCapabilities`) returns `FinancialQueryEnvelope<T>`:

```json
{
  "toolName": "getCashPosition",
  "contractVersion": "1.0",
  "businessId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "evaluatedAt": "2026-10-29T00:02:00Z",
  "asOfDate": "2026-10-29",
  "status": "AVAILABLE",
  "completeness": "COMPLETE",
  "unavailableReason": null,
  "warnings": [],
  "evidence": [
    {
      "entityType": "FINANCIAL_ACCOUNT",
      "entityId": "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
      "description": "Ledger account balance as of 2026-10-29: HBL Main (PKR)",
      "recordedAt": "2026-09-01T08:00:00Z"
    }
  ],
  "data": { ... }
}
```

- **Freshness & Evaluation Window:**
  - `evaluatedAt`: UTC `Instant` when the backend read transaction executed.
  - `asOfDate`: Effective `YYYY-MM-DD` cutoff for point-in-time queries (`getCashPosition`, `getOverdueReceivables`), `"YYYY-MM-DD..YYYY-MM-DD"` interval for `getUpcomingObligations`, or `null` when `status == UNAVAILABLE`.
- **Completeness (`DataCompleteness`):**
  - `COMPLETE`: All matching authoritative records for the active business and cutoff window are included in `data`.
  - `PARTIAL`: Pagination (`offset > 0` or `overdueCount > offset + items.size()`) truncated `items`, or an account balance exceeded canonical decimal capacity (`warnings` explains the exact condition; aggregate totals in `OverdueReceivablesResult` remain complete across all overdue items).
  - `EMPTY`: No active accounts exist (`getCashPosition` when 0 accounts) or `status == UNAVAILABLE`.
- **Evidence Provenance (`FinancialEvidenceReference`):**
  - `entityType`: One of `FINANCIAL_ACCOUNT`, `FINANCIAL_OBLIGATION`, `OBLIGATION_PAYMENT_SCHEDULE`, `RECURRING_RULE_OCCURRENCE`, or `RECURRING_RULE`.
  - `entityId`: Authoritative UUID string of the backing entity.
  - `description`: Deterministic summary of the record's contribution.
  - `recordedAt`: Authoritative `createdAt` `Instant` of the underlying record.

### 3.5 Typed Result Payload Schemas

#### A. `CapabilitiesResponse`
- `contractVersion`: `"1.0"`
- `businessId`: `UUID`
- `evaluatedAt`: `Instant`
- `capabilities`: `List<CapabilityItem>` where each item has `(String toolName, CapabilityStatus status, String description, String unavailableReason)`.

#### B. `CashPositionResult`
- `asOfDate`: `LocalDate`
- `currencySummaries`: `List<CurrencyCashSummary>` sorted alphabetically by ISO currency code:
  - `(String currency, int currencyDecimals, String openingBalance, String netMovements, String currentBalance, int accountCount)`
- `accounts`: `List<AccountCashPosition>` sorted by `(createdAt ASC, id ASC)`:
  - `(UUID accountId, String accountName, String accountType, String currency, int currencyDecimals, String openingBalance, LocalDate openingDate, String netMovements, String currentBalance, String status, String institutionName, String maskedIdentifier)`
- `totalAccounts`: `int`

#### C. `OverdueReceivablesResult`
- `asOfDate`: `LocalDate`
- `currency`: `"PKR"`
- `totalOverdueAmount`: Exact decimal string summing **all** overdue receivables for the business as of `asOfDate` (even when `items` is paginated).
- `overdueCount`: Total count of overdue receivable obligations before pagination.
- `agingBuckets`: `List<AgingBucketSummary>` across overdue buckets (`OVERDUE_1_30`, `OVERDUE_31_60`, `OVERDUE_61_90`, `OVERDUE_91_PLUS`) with `(String bucketCode, String bucketLabel, String amount, int count)`.
- `items`: Paginated `List<OverdueReceivableItem>` ordered deterministically by `(overdueDays DESC, outstandingAmount DESC, obligationId ASC)`:
  - `(UUID obligationId, String referenceNumber, UUID counterpartyId, String counterpartyName, LocalDate issueDate, LocalDate dueDate, long overdueDays, String bucketCode, String originalAmount, String settledAmount, String outstandingAmount)`

#### D. `UpcomingObligationsResult`
- `startDate`: `LocalDate`
- `endDate`: `LocalDate`
- `currency`: `"PKR"`
- `totalUpcomingReceivables`, `totalUpcomingPayables`, `totalOverdueReceivables`, `totalOverduePayables`: Exact decimal strings computed strictly from legally recognized obligations/installments (`obligations`).
- `obligations`: `List<UpcomingObligationItem>` with `(UUID obligationId, String direction, UUID counterpartyId, String counterpartyName, String referenceNumber, String sourceType, String scheduleType, LocalDate dueDate, String amount, String paidAmount, String outstandingAmount, boolean isOverdue, String notes)`.
  - `scheduleType` is `"SCHEDULED_INSTALLMENT"` (for scheduled tranches after chronological partial-settlement absorption) or `"OBLIGATION_DUE"` (for unscheduled obligations or unscheduled residual exposure).
- `recurringProjections`: `List<RecurringProjectionItem>` with `(UUID ruleId, String ruleName, String eventType, String direction, LocalDate expectedDate, String amount, String currency, String accountName, boolean isEstimate)` (`isEstimate` is always `true`).

---

## 4. As-Of Semantics, Precision & Query Bounds

1. **Historical As-Of Semantics:**
   - **Cash Position:** If `account.openingDate > asOfDate`, `AccountCashPosition.openingBalance` preserves the account's persisted opening balance metadata while `AccountCashPosition.currentBalance` and `CurrencyCashSummary` report canonical zero at the currency's ISO scale and emit a pre-opening date warning. Missing `openingBalance` or `openingDate` flags `DataCompleteness.PARTIAL` without fabricating `"0.00"`. Posted `AccountMovement` rows are included iff `effectiveDate <= asOfDate`. Under FinSight V23 reversal rules, compensating reversal movements carry `original.getEffectiveDate()`, neutralizing reversed postings from the original effective date onward.
   - **Overdue Receivables:** Obligations with `issueDate > asOfDate` or `status == VOIDED` (as of `asOfDate`) are excluded. Allocations (`CASH_SETTLEMENT` and `ADVANCE_APPLICATION`) reduce outstanding iff `effectiveDate <= asOfDate`. Under FinSight's canonical bookkeeping correction policy, reversed settlements and cancelled advance applications restore outstanding exposure as of their accounting-effective date (`effectiveDate`), keeping audit execution timestamps (`cancelledAt`) distinct and matching `FinancialObligationService.getTotalsSummary`.
2. **Query Bounds & Validation:**
   - `asOfDate`, `fromDate`, `toDate`: Must be valid ISO-8601 `YYYY-MM-DD`.
   - `getUpcomingObligations` window: `toDate >= fromDate` and `ChronoUnit.DAYS.between(fromDate, toDate) <= 90`.
   - `getUpcomingObligations` direction: `ALL`, `RECEIVABLE`, or `PAYABLE` (case-insensitive).
   - `getOverdueReceivables` pagination: `limit` in `1..100` (default `50`), `offset >= 0` (default `0`).
   - `getMarginDrivers` / `getFxSettlementImpact` query string: length $\le 500$ characters.
   - `projectId`: valid canonical UUID string when supplied over HTTP.

---

## 5. Authorization, Tenant Isolation & Error Contract

### Role Permission Matrix
| Role | `getCapabilities` & Unavailable Tools (`FINANCIAL_DATA_READ`) | `getCashPosition` (`FINANCIAL_ACCOUNT_READ`) | `getOverdueReceivables` & `getUpcomingObligations` (`FINANCIAL_TRANSACTION_READ`) |
| :--- | :---: | :---: | :---: |
| `OWNER` | Allowed | Allowed | Allowed |
| `ACCOUNTANT` | Allowed | Allowed | Allowed |
| `MANAGER` | Allowed | Allowed | Allowed |
| `VIEWER` | Allowed | Allowed | Allowed |
| Unauthenticated / Logged-out | `401 Unauthorized` | `401 Unauthorized` | `401 Unauthorized` |
| Authenticated without active business | `409 Conflict` (`active_business_required`) | `409 Conflict` (`active_business_required`) | `409 Conflict` (`active_business_required`) |
| Role without required permission | `403 Forbidden` | `403 Forbidden` | `403 Forbidden` |

### Error & Restriction States
- **`400 Bad Request` (`FinancialQueryValidationException`):** Malformed `YYYY-MM-DD` date, invalid `accountType` / `direction`, `limit` outside `1..100`, `offset < 0`, non-integer `limit`/`offset`, malformed `projectId` UUID, `query` $> 500$ chars, `toDate < fromDate`, or window $> 90$ days. Response includes `"errors": { "<field>": "<reason>" }`.
- **`401 Unauthorized`:** Missing or invalidated session cookie.
- **`403 Forbidden` (`AccessDeniedException`):** Caller lacks required `BusinessPermission`, attempts to select a non-member business via `/api/businesses/active`, or invokes `FinancialQueryFacadeService` with a `null` context or unauthorized role.
- **`409 Conflict` (`ActiveBusinessRequiredException`):** Authenticated user has not selected an active business in session (`FINSIGHT_ACTIVE_BUSINESS_ID`).
- **`200 OK` with `"status": "UNAVAILABLE"`:** Returned for `getProjectFinancials`, `getProjectFundingGap`, `getMarginDrivers`, and `getFxSettlementImpact` so AI tool callers receive structured dependency metadata instead of transport failures.

### Integration Requirements for Developer B (Fatima)
1. **Never pass AI/user-supplied `businessId`:** Always resolve `BusinessAccessContext` via `BusinessAuthorizationService` / `ActiveBusinessContext` from the authenticated user's session before invoking `FinancialQueryFacade`.
2. **Respect `status == UNAVAILABLE` and `warnings`:** Surface `unavailableReason` directly in Copilot responses without estimating or hallucinating missing project, margin, or FX numbers.
3. **Preserve `isEstimate == true` on `recurringProjections`:** Never describe `recurringProjections` as unpaid bills or legal liabilities.
4. **Preserve Exact Strings & Currency Labels:** Render monetary strings verbatim with their `currency` code; never sum `currencySummaries` across different currencies unless an approved FX conversion tool is introduced in F11.

---

## 6. Verification Evidence (29 October 2026)

- **C11 Dedicated Suite (`.\mvnw.cmd test "-Dtest=FinancialQueryFacadeServiceTest,FinancialQueryControllerTest,FinancialQueryPostgreSqlIT"`):** **21 / 21 PASSED**
  - `FinancialQueryFacadeServiceTest` (**8 / 8 PASSED**): Capabilities descriptor check, multi-scale currency separation (`PKR` scale 2, `USD` scale 2, `BHD` scale 3, `JPY` scale 0) + identifier masking, date/type validation, 7-bucket overdue aging + `offset`/`limit` `PARTIAL` pagination, upcoming installments + deduplicated V29 active rule projection (`isEstimate: true`), 90-day window/direction bounds, 4 unavailable tools + 500-char query limit, and service-layer RBAC enforcement across all 8 methods.
  - `FinancialQueryControllerTest` (**7 / 7 PASSED**): All 8 REST endpoints, client `businessId` override ignoring, `limit` & `offset` forwarding, malformed UUID/integer HTTP `400` responses, and HTTP `403` access denial.
  - `FinancialQueryPostgreSqlIT` (**6 / 6 PASSED** on real disposable PostgreSQL 14.22 + Flyway V29):
    - *Fixture A (Cash Position):* Opening PKR 100,000 + Receipt 20,000 - Expense 5,000 + Internal Transfer 10,000 $\rightarrow$ Bank `105000.00` + Cash `10000.00` = **`115000.00` PKR** combined cash; unposted draft (50k), unpaid obligation (40k), and recurring rule/draft occurrence (15k) excluded; historical `asOfDate` progression (`0.00` pre-open $\rightarrow$ `100000.00` $\rightarrow$ `120000.00` $\rightarrow$ `115000.00`) and V23 expense reversal (`120000.00`) verified.
    - *Fixture B (Overdue Receivables):* PKR 50,000 receivable - PKR 20,000 partial settlement = **`30000.00` PKR** in `OVERDUE_1_30`; historical pre-settlement `asOfDate` (`50000.00`), PKR 10,000 advance application (`20000.00`), cash settlement reversal (`40000.00`), and multi-page `offset`/`limit` pagination verified.
    - *Fixture C (Upcoming Obligations):* PKR 50,000 payable with 2x PKR 25,000 installments + active `RecurringRule` (PKR 15,000) $\rightarrow$ **`50000.00` PKR** upcoming payables (zero parent/installment double counting; recurring rule isolated in `recurringProjections`), followed by PKR 20,000 partial settlement reducing upcoming payables to **`30000.00` PKR** (`5000.00` + `25000.00`).
    - *Fixture D (Unavailable Tools):* All 4 unavailable tools return `CapabilityStatus.UNAVAILABLE`, `data == null`, and non-blank reasons.
    - *Fixture E (HTTP Security & Sessions):* Real Spring Security login + `/api/businesses/active` verified across `OWNER`, `ACCOUNTANT`, `MANAGER`, and `VIEWER`, unauthenticated `401`, logged-out session `401`, no-membership `403`/`409`, spoofed `?businessId=` / `X-Business-Id` tenant override prevention, and HTTP `400` validation bounds.
    - *Fixture F (Read-Only Database Integrity):* Populated 16 tables (`financial_accounts`, `financial_transactions`, `account_movements`, `record_drafts`, `financial_obligations`, `obligation_payment_allocations`, `obligation_payment_schedules`, `counterparty_advances`, `recurring_rules`, `recurring_rule_occurrences`, `bank_statement_imports`, `bank_statement_entries`, `bank_reconciliation_matches`, `monthly_records`, `score_results`, `security_audit_events`) and verified identical row counts and SHA-256 value-level content digests before and after invoking all 8 Java facade methods and all 8 HTTP GET endpoints.
- **Backend Phase 2 Regression Suite (40 test classes across Accounts, Currency, Posting, Transfers, Reversals, Drafts, Imports, Counterparties, Obligations, Advances, Payment Cycles, Bank Reconciliation, Recurring Operations, Authorization & Funding Gap Calculator):** **523 / 523 PASSED** (`544 / 544` combined with C11).
- **Frontend Regression Suite (`npm test`):** **226 / 226 PASSED** (0 failures, 0 skipped).
