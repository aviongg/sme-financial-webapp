# FinSight Phase 2 Implementation State & Dependency Register

**Version:** `2.13`
**Last Updated:** 29 October 2026
**Canonical Branch:** `dev/suleman`
**Current HEAD SHA:** `e07bba93b79ab823447e40c2139f8970f97b3613`
**Baseline Release:** PR #3 integrated into `main` (Flyway V1–V17)  

---

## 1. Environment & Baseline Status

| Item | Value | Evidence / Verification |
| :--- | :--- | :--- |
| **Active Branch** | `dev/suleman` | Verified via `git branch --show-current` |
| **Commit SHA** | `e07bba93b79ab823447e40c2139f8970f97b3613` | Published C11 Financial Query Facade HEAD (`e07bba9`) |
| **Flyway Head** | **V29** | `backend/src/main/resources/db/migration/V29__create_recurring_transactions.sql` (V29 authored & integration verified; 0 migrations introduced in C02 Phase A; V30 reserved for Dev B F05) |
| **Untracked Artifacts** | `backend/.cp.txt` | Maven classpath artifact (13,680 bytes); preserved untouched |
| **Phase 2 Implementation** | **Monthly Ledger Cutover Phase A (C02), Financial Query Facade (C11 V1), Recurring Operations, Bank Reconciliation, Payment Cycles & Advances, Obligations & Allocations, Counterparties, Import Drafts, Reversals, Transfers, Drafts, Transactions, Summaries, Records, Import Preview & Calculators** | `GET /api/records/monthly/projection`, `GET /api/records/monthly/comparison`, `GET /api/finance/query/**`, `POST/GET/PUT /api/finance/recurring/**`, `POST/GET /api/finance/reconciliation/**`, `POST/GET /api/finance/payments/**`, `POST/GET /api/finance/advances/**`, `GET /api/finance/payment-calendar`, `POST/GET/PUT /api/finance/obligations/**`, `POST/GET/PUT /api/finance/counterparties`, `POST /api/finance/transactions/{id}/reverse`, `POST /api/finance/transfers`, `POST /api/finance/drafts`, `GET /api/finance/drafts`, `POST /api/finance/transactions`, `GET /api/finance/transactions/summary`, Import Preview route/wizard, and pure Obligation Aging & Funding Gap calculators |
| **Blueprint Availability** | **Missing** | Master Phase 2 Technical Integration Blueprint not present locally |
| **Onboarding Audits** | **Completed** | Antigravity Backend Baseline Audit & Codex Frontend Baseline Audit completed |
| **Accounts Read Status** | **INTEGRATION_VERIFIED (Bounded Slice)** | Verified across Spring Boot, PostgreSQL V18, Next.js, and Edge |
| **Accounts Create Status** | **INTEGRATION_VERIFIED (Bounded Slice)** | Contract v1.1, V19 authored, 105 BE tests, 23 FE creation / 83 total FE tests, 21-check real live harness exit 0 |
| **Records, Accounts & Transactions Status** | **INTEGRATION_VERIFIED (Bounded Slice)** | Real Spring Boot (port 8180) + Next.js (port 3180) + PostgreSQL 14.22 (V20) + Playwright/Edge; 33 passing check groups, exit 0, 0 blockers |
| **F07 Funding Gap Calculator Status** | **UNIT_VERIFIED (Bounded Pure Calculator)** | 24/24 unit tests passed both in isolated execution and via project Maven runner `mvnw.cmd test -Dtest=ProjectFundingGapCalculatorTest`; application/API integration pending |
| **Record Drafts & Review Status** | **INTEGRATION_VERIFIED (Bounded Slice)** | Checkpoint committed (`796a904d9c49098bc5bbdb6060c2394eeb9c6f27`). Fresh isolated V21 environment, backend class hash `595b0f69705cce3d21022e6ad67e8be4105575ebdce89781500b0a939bc50b4a` verified, 47 grouped checks passed. Live review verified at `http://127.0.0.1:3480/records`. |
| **Account Transfers (A-F01-TRANSFERS)** | **INTEGRATION_VERIFIED (Bounded Slice)** | Real PostgreSQL 14.22 (V22) + Spring Boot + Next.js + Playwright/Edge combined harness exit 0 (54 check groups, 0 blockers). Atomic dual movements, negative cash prevention, capacity bounds, deterministic deadlock-free locking, idempotent replay (201), conflict detection (409), cash neutrality, account summary in/out aggregation, and bilingual Urdu RTL verified. |
| **Transaction Reversals (A-F01-REVERSALS)** | **INTEGRATION_VERIFIED (Ready for Commit A)** | Real PostgreSQL 14.22 (V23) + Spring Boot + Next.js + Playwright/Edge combined harness exit 0 (64 check groups, 0 blockers). Compensating inverse entries for receipts, expenses, and transfers; zero cash deficit simulation; durable idempotency; single reversal per transaction (`uq_fin_tx_reversed_transaction_id`); no reversal-of-reversals; summary neutrality; detail sheet UI with confirmation & reason; bidirectional original/reversal links; Urdu RTL verified. |
| **Spreadsheet Import Preview (A-F12A-IMPORT-PREVIEW)** | **FRONTEND_PREVIEW_VERIFIED (Ready for Commit B)** | Pure client-side CSV/TSV parser & preview wizard (0 non-GET private requests; no financial persistence/writes). In-memory preview state retention across EN $\leftrightarrow$ UR switches verified in 186 unit tests and real headless Edge browser on live review gateway (port 3580). Stale accounts rejected, immediate clearance on session expiry/business switch, zero localStorage usage. |
| **Obligation Aging Calculator (A-F03-AGING-CALCULATOR)** | **UNIT_VERIFIED (Ready for Commit C)** | Pure Java calculator verified via project Maven test runner (`mvnw.cmd test "-Dtest=ObligationAgingCalculatorTest"`, 19/19 passed, 0 failures, 11.284s). 7 canonical buckets, exact reconciliation between total balance and bucket amounts, zero/active reconciliation, boundary dates, PKR-only currency validation, and multi-direction support. |
| **Persistent Import-to-Draft (A-F12A-IMPORT-DRAFTS)** | **INTEGRATION_VERIFIED (Ready for Commit D)** | Flyway V24 authored (`V24__create_import_draft_provenance.sql`), composite tenant FKs, chunked save ($\le 100$ rows), idempotent replay & conflict detection (409), verbatim amount input preservation, reload recovery via URL `batchId`, 26 BE tests passing (unit, controller, PostgreSQL IT), 189 FE tests passing, tsc/lint exit 0, production build exit 0, zero financial mutations. |
| **Counterparty Foundation (A-F03-COUNTERPARTIES)** | **INTEGRATION_VERIFIED (Ready for Checkpoint)** | Flyway V25 authored (`V25__create_counterparties.sql`), tenant isolation, composite FK `(counterparty_id, business_id) ON DELETE RESTRICT` on transactions and drafts, least-privilege DML (`DELETE` revoked from `finsight_app`), non-unique index `idx_counterparties_business_lower_name` allowing same-name entities, type compatibility (`CUSTOMER`/`SUPPLIER`/`BOTH`), active/archived state, search/filter/pagination, 33 BE tests passing (unit, controller, PostgreSQL IT), 198 FE tests passing (189 preceding + 9 counterparty unit/contract), tsc/lint exit 0, production build exit 0, real browser acceptance Journeys A–G passed cleanly (exit 0). |
| **Receivables & Payables Obligations (A-F03-OBLIGATIONS-V1)** | **INTEGRATION_VERIFIED (Ready for Checkpoint)** | Flyway V26 authored (`V26__create_financial_obligations.sql`), canonical obligations (`RECEIVABLE`/`PAYABLE`), allocation engine, settlement cash posting (`RECEIVABLE_COLLECTION`/`PAYABLE_SETTLEMENT`), obligation aging (7 buckets), settlement reversals, multi-tenant isolation, 37 BE tests passing (unit, controller, PostgreSQL IT), 206 FE tests passing, tsc/lint exit 0, production build exit 0, real browser acceptance Journeys A–H passed cleanly on PostgreSQL V26 (exit 0). |
| **Payment Cycles & Advances (A-F03-PAYMENT-CYCLES-V2)** | **INTEGRATION_VERIFIED (Ready for Checkpoint)** | Flyway V27 authored (`V27__create_payment_cycles_and_advances.sql`), multi-obligation payment allocation, customer/supplier advances lifecycle, derived unapplied balances, advance applications (0 cash movements), advance application reversals (0 cash movements), payment reversals with atomic allocation & advance cancellation, downstream application protection, non-posting installment schedules and payment calendar aggregation, 78 BE tests passing (unit, controller, PostgreSQL IT), 216 FE tests passing, tsc clean, lint 0, production build exit 0, real Microsoft Edge browser acceptance Journeys A–I passed cleanly on PostgreSQL V27 (exit 0). |
| **Bank Reconciliation (A-F12A-BANK-RECONCILIATION-V1)** | **INTEGRATION_VERIFIED (Ready for Checkpoint)** | Flyway V28 authored (`V28__create_bank_reconciliation.sql`), CSV/TSV statement import, debit/credit normalization, duplicate detection & idempotent replay, deterministic matching engine with hard financial filters, user-confirmed 1-to-1 active reconciliation links, match reversal/undo with audit trail, row exclusion with mandatory reason, unmatched-entry-to-incomplete-draft integration (unresolved event classification), dual-leg transfer matching, account reconciliation summaries, zero financial posting mutations strictly verified, 32 BE tests passing (unit, controller, PostgreSQL IT), 220 FE tests passing, tsc clean, lint 0, production build exit 0, real Microsoft Edge browser acceptance Journeys A–J passed cleanly on PostgreSQL V28 (exit 0). |
| **Recurring Operations (A-F12A-RECURRING-OPERATIONS-V1)** | **INTEGRATION_VERIFIED (Ready for Checkpoint)** | Flyway V29 authored (`V29__create_recurring_transactions.sql`), deterministic recurrence engine preserving anchor days across short months and leap years, automated and manual bounded due draft generation (max 50/run), draft provenance integration with To Review, zero cash mutations on scheduling/draft generation, explicit posting integration (at most 1 transaction per occurrence), transaction reversal synchronization, reversal terminal guard preventing draft reposting (409 Conflict), skip with draft discard, bilingual EN/UR with RTL, mobile 390x844 responsive layout, 35 BE tests passing (unit, controller, PostgreSQL IT), 104 shared-service regression tests passing, 226 FE tests passing, tsc clean, lint 0, production build exit 0, real Microsoft Edge browser acceptance Journeys A–M passed cleanly on PostgreSQL V29 (exit 0). |
| **Financial Query Facade (A-W7-FINANCIAL-QUERY-FACADE-V1)** | **PUBLISHED_TO_GITHUB (`e07bba9`)** | C11 V1 authorized read-only intelligence contract (`com.app.sme_health_backend.financialquery`, `contractVersion = "1.0"`), 3 available tools (`getCashPosition`, `getOverdueReceivables`, `getUpcomingObligations`) + `getCapabilities`, 4 explicit typed unavailable tools (`getProjectFinancials`, `getProjectFundingGap`, `getMarginDrivers`, `getFxSettlementImpact`), zero fabricated numbers, strict `@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)`, dual-layer server-side tenant & RBAC enforcement, 21/21 C11 tests passing, 523/523 Phase 2 backend regression tests passing (`544/544` combined), 226/226 frontend tests passing. Committed as `e07bba9` and published to `origin/dev/suleman`. |
| **Monthly Ledger Cutover Phase A (A-C02-MONTHLY-LEDGER-CUTOVER-PHASE-A)** | **INTEGRATION_VERIFIED (Phase A Read-Only Foundation & Cutover Policy)** | Deterministic monthly ledger projection engine (`MonthlyLedgerProjectionService`) and shadow comparison engine (`compareWithMonthlyRecord`), contract v1.0, 14-metric source-of-truth reconciliation matrix, zero live scoring cutover, zero database mutations, 2 endpoints (`GET /api/records/monthly/projection` and `GET /api/records/monthly/comparison`), 24/24 C02 tests passing (11 unit, 4 WebMvc controller, 9 PostgreSQL IT with Fixtures A–G and 12-table SHA-256 snapshots), 39/39 backend regression tests passing, 226/226 frontend tests passing. V30 reserved for Dev B F05; Phase B cutover migration reserved as V31+. |

---

## 2. Active Tasks & Allowed Paths

*   **Developer A Backend (Antigravity):**
    *   **Task ID:** `A-RECORDS-02-BE` (Persistent Drafts and Safe Draft-to-Transaction Recording)
    *   **Status:** `INTEGRATION_VERIFIED — CHECKPOINT_COMMITTED`
    *   **Backend Paths Owned:**
        *   `backend/src/main/resources/db/migration/V21__create_record_drafts.sql`
        *   `backend/src/main/java/com/app/sme_health_backend/accounting/draft/**`
        *   `backend/src/test/java/com/app/sme_health_backend/accounting/draft/**`
    *   **Shared Backend Files Edited:**
        *   `backend/src/main/java/com/app/sme_health_backend/shared/exception/GlobalExceptionHandler.java` (added `DraftValidationException` 400 and `DraftConflictException` 409 handlers)
    *   **Documentation Paths:**
        *   `docs/PHASE2_IMPLEMENTATION_STATE.md`
        *   `docs/phase2/RECORD_DRAFTS.md` (Revision 1.0 CONTRACT_READY & INTEGRATION_VERIFIED)

*   **Developer A Frontend (Codex):**
    *   **Task ID:** `A-RECORDS-02-FE` (Central Records Workspace Draft UI & Integration)
    *   **Status:** `INTEGRATION_VERIFIED — CHECKPOINT_COMMITTED`
    *   **Frontend Paths:**
        *   `frontend/src/components/records/**`
        *   `frontend/src/lib/api/dev-a/drafts.ts`
        *   `frontend/tests/record-drafts.test.mjs`
        *   `frontend/tests/record-drafts.live.mjs`

*   **Developer A Pure Calculator (Antigravity):**
    *   **Task ID:** `A-F07-CALCULATOR` (Independent Project Funding-Gap Calculation)
    *   **Status:** `UNIT_VERIFIED — CHECKPOINT_COMMITTED`
    *   **Paths Owned:**
        *   `backend/src/main/java/com/app/sme_health_backend/liquidity/**`
        *   `backend/src/test/java/com/app/sme_health_backend/liquidity/**`
        *   `docs/phase2/FUNDING_GAP_CALCULATION.md`
    *   **Scope Note:** Pure in-memory calculation foundation for F07; application/API/persistence integration remains pending.

*   **Developer A Full Stack (Antigravity):**
    *   **Task ID:** `A-F01-TRANSFERS` (PKR Transfers Between Accounts)
    *   **Status:** `INTEGRATION_VERIFIED — CHECKPOINT_COMMITTED`
    *   **Backend Paths Owned:**
        *   `backend/src/main/resources/db/migration/V22__add_internal_transfers.sql`
        *   `backend/src/main/java/com/app/sme_health_backend/accounting/service/TransferService.java`
        *   `backend/src/main/java/com/app/sme_health_backend/accounting/controller/TransferController.java`
        *   `backend/src/main/java/com/app/sme_health_backend/accounting/dto/TransferPostRequest.java`
        *   `backend/src/main/java/com/app/sme_health_backend/accounting/util/CanonicalAmountValidator.java`
        *   `backend/src/main/java/com/app/sme_health_backend/accounting/model/TransactionEventType.java`
        *   `backend/src/main/java/com/app/sme_health_backend/accounting/entity/FinancialTransaction.java`
        *   `backend/src/main/java/com/app/sme_health_backend/accounting/service/TransactionReadService.java`
        *   `backend/src/main/java/com/app/sme_health_backend/accounting/dto/TransactionSummaryResponse.java`
        *   `backend/src/main/java/com/app/sme_health_backend/accounting/dto/TransactionDetailResponse.java`
        *   `backend/src/test/java/com/app/sme_health_backend/accounting/TransferServiceTest.java`
        *   `backend/src/test/java/com/app/sme_health_backend/accounting/TransferControllerTest.java`
        *   `backend/src/test/java/com/app/sme_health_backend/accounting/FinancialTransferPostgreSqlIT.java`
    *   **Frontend Paths Owned:**
        *   `frontend/src/components/finance/transfers/**`
        *   `frontend/src/lib/api/dev-a/transfers.ts`
        *   `frontend/src/lib/api/dev-a/transaction-contracts.ts`
        *   `frontend/src/lib/api/dev-a/transaction-totals.ts`
        *   `frontend/src/lib/api/dev-a/transactions.ts`
        *   `frontend/src/components/finance/transactions/TransactionsList.tsx`
        *   `frontend/src/components/finance/transactions/TransactionDetail.tsx`
        *   `frontend/src/components/finance/transactions/transaction-copy.ts`
        *   `frontend/src/components/finance/accounts/AccountsList.tsx`
        *   `frontend/tests/transfers.test.mjs`
        *   `frontend/tests/transfers.live.mjs`
        *   `frontend/tests/accounts-live.integration.mjs`
    *   **Documentation Paths:**
        *   `docs/phase2/ACCOUNT_TRANSFERS.md` (Revision 1.1 INTEGRATION_VERIFIED)

*   **Developer A Full Stack (Antigravity):**
    *   **Task ID:** `A-F01-REVERSALS` (Safe Correction of Recorded Transactions)
    *   **Status:** `INTEGRATION_VERIFIED — READY_FOR_COMMIT`
    *   **Backend Paths Owned:**
        *   `backend/src/main/resources/db/migration/V23__add_transaction_reversals.sql`
        *   `backend/src/main/java/com/app/sme_health_backend/accounting/dto/TransactionReverseRequest.java`
        *   `backend/src/main/java/com/app/sme_health_backend/accounting/service/TransactionReverseService.java`
        *   `backend/src/main/java/com/app/sme_health_backend/accounting/entity/FinancialTransaction.java`
        *   `backend/src/main/java/com/app/sme_health_backend/accounting/repository/FinancialTransactionRepository.java`
        *   `backend/src/main/java/com/app/sme_health_backend/accounting/controller/TransactionController.java`
        *   `backend/src/main/java/com/app/sme_health_backend/accounting/service/TransactionReadService.java`
        *   `backend/src/main/java/com/app/sme_health_backend/accounting/dto/TransactionDetailResponse.java`
        *   `backend/src/test/java/com/app/sme_health_backend/accounting/TransactionReverseServiceTest.java`
        *   `backend/src/test/java/com/app/sme_health_backend/accounting/TransactionControllerTest.java`
        *   `backend/src/test/java/com/app/sme_health_backend/accounting/FinancialTransactionReversalPostgreSqlIT.java`
    *   **Frontend Paths Owned:**
        *   `frontend/src/lib/api/dev-a/transaction-contracts.ts`
        *   `frontend/src/lib/api/dev-a/transactions.ts`
        *   `frontend/src/components/finance/transactions/transaction-copy.ts`
        *   `frontend/src/components/finance/transactions/TransactionDetail.tsx`
        *   `frontend/src/components/finance/transactions/TransactionsList.tsx`
        *   `frontend/tests/transaction-reversals.test.mjs`
        *   `frontend/tests/reversals.live.mjs`
        *   `frontend/tests/accounts-live.integration.mjs`
    *   **Documentation Paths:**
        *   `docs/phase2/TRANSACTION_REVERSALS.md` (Revision 1.0 INTEGRATION_VERIFIED)

*   **Developer A Frontend (Codex & Antigravity):**
    *   **Task ID:** `A-F12A-IMPORT-PREVIEW` (Spreadsheet Import Preview & Usability)
    *   **Status:** `FRONTEND_PREVIEW_VERIFIED — READY_FOR_COMMIT`
    *   **Frontend Paths Owned:**
        *   `frontend/src/app/records/import/page.tsx`
        *   `frontend/src/components/records/import/**`
        *   `frontend/src/lib/imports/**`
        *   `frontend/src/components/integration/LanguageControl.tsx`
        *   `frontend/src/components/auth/SessionProvider.tsx`
        *   `frontend/tests/record-import-preview.test.mjs`
    *   **Documentation Paths:**
        *   `docs/phase2/RECORD_IMPORT_PREVIEW.md` (Revision 1.1 FRONTEND_PREVIEW_VERIFIED)

*   **Developer A Pure Calculator (Antigravity):**
    *   **Task ID:** `A-F03-AGING-CALCULATOR` (Independent Obligation Aging Calculation)
    *   **Status:** `UNIT_VERIFIED — READY_FOR_COMMIT`
    *   **Backend Paths Owned:**
        *   `backend/src/main/java/com/app/sme_health_backend/obligation/**`
        *   `backend/src/test/java/com/app/sme_health_backend/obligation/**`
*   **Developer A Full Stack (Antigravity):**
    *   **Task ID:** `A-F12A-IMPORT-DRAFTS` (Persistent Spreadsheet Import to Record Drafts)
    *   **Status:** `INTEGRATION_VERIFIED — READY_FOR_COMMIT`
    *   **Backend Paths Owned:**
        *   `backend/src/main/resources/db/migration/V24__create_import_draft_provenance.sql`
        *   `backend/src/main/java/com/app/sme_health_backend/accounting/draft/imports/**`
        *   `backend/src/test/java/com/app/sme_health_backend/accounting/draft/imports/**`
    *   **Frontend Paths Owned:**
        *   `frontend/src/lib/api/dev-a/import-drafts.ts`
        *   `frontend/src/components/records/import/**`
        *   `frontend/src/lib/imports/lifetime.ts`
        *   `frontend/src/app/records/page.tsx`
        *   `frontend/src/components/records/DraftsWorkspace.tsx`
        *   `frontend/tests/record-import-preview.test.mjs`
    *   **Documentation Paths:**
        *   `docs/phase2/RECORD_IMPORT_PREVIEW.md` (Revision 2.0 & Section 6 INTEGRATION_VERIFIED)

*   **Developer A Full Stack (Antigravity):**
    *   **Task ID:** `A-F03-COUNTERPARTIES` (Customer & Supplier Foundation, Transaction & Draft Integration)
    *   **Status:** `INTEGRATION_VERIFIED — READY_FOR_COMMIT`
    *   **Backend Paths Owned:**
        *   `backend/src/main/resources/db/migration/V25__create_counterparties.sql`
        *   `backend/src/main/java/com/app/sme_health_backend/counterparty/**`
        *   `backend/src/main/java/com/app/sme_health_backend/accounting/dto/TransactionPostRequest.java`
        *   `backend/src/main/java/com/app/sme_health_backend/accounting/dto/TransactionDetailResponse.java`
        *   `backend/src/main/java/com/app/sme_health_backend/accounting/entity/FinancialTransaction.java`
        *   `backend/src/main/java/com/app/sme_health_backend/accounting/service/TransactionPostService.java`
        *   `backend/src/main/java/com/app/sme_health_backend/accounting/service/TransactionReadService.java`
        *   `backend/src/main/java/com/app/sme_health_backend/accounting/draft/DraftCreateRequest.java`
        *   `backend/src/main/java/com/app/sme_health_backend/accounting/draft/DraftUpdateRequest.java`
        *   `backend/src/main/java/com/app/sme_health_backend/accounting/draft/DraftRecordRequest.java`
        *   `backend/src/main/java/com/app/sme_health_backend/accounting/draft/DraftSummaryItem.java`
        *   `backend/src/main/java/com/app/sme_health_backend/accounting/draft/DraftDetailResponse.java`
        *   `backend/src/main/java/com/app/sme_health_backend/accounting/draft/RecordDraft.java`
        *   `backend/src/main/java/com/app/sme_health_backend/accounting/draft/RecordDraftService.java`
        *   `backend/src/main/java/com/app/sme_health_backend/shared/exception/GlobalExceptionHandler.java`
        *   `backend/src/test/java/com/app/sme_health_backend/counterparty/**`
    *   **Frontend Paths Owned:**
        *   `frontend/src/app/records/contacts/page.tsx`
        *   `frontend/src/app/records/page.tsx`
        *   `frontend/src/components/records/counterparties/**`
        *   `frontend/src/lib/api/dev-a/counterparties.ts`
        *   `frontend/src/lib/api/dev-a/transaction-contracts.ts`
        *   `frontend/src/lib/api/dev-a/transactions.ts`
        *   `frontend/src/components/finance/transactions/PostTransactionForm.tsx`
        *   `frontend/src/components/finance/transactions/TransactionsList.tsx`
        *   `frontend/src/components/finance/transactions/TransactionDetail.tsx`
        *   `frontend/src/components/finance/transactions/transaction-copy.ts`
        *   `frontend/src/components/finance/transactions/transaction-input.ts`
        *   `frontend/src/lib/api/dev-a/drafts.ts`
        *   `frontend/src/components/records/DraftEntryFields.tsx`
        *   `frontend/src/components/records/DraftsWorkspace.tsx`
        *   `frontend/src/components/records/draft-copy.ts`
        *   `frontend/src/components/records/draft-entry.ts`
        *   `frontend/src/components/records/records-copy.ts`
        *   `frontend/tests/counterparties.test.mjs`
    *   **Documentation Paths:**
        *   `docs/phase2/COUNTERPARTIES.md` (Revision 1.0 INTEGRATION_VERIFIED)

*   **Developer A Full Stack (Antigravity):**
    *   **Task ID:** `A-F03-OBLIGATIONS-V1` (Canonical PKR Receivables and Payables Workflow)
    *   **Status:** `INTEGRATION_VERIFIED — READY_FOR_COMMIT`
    *   **Backend Paths Owned:**
        *   `backend/src/main/resources/db/migration/V26__create_financial_obligations.sql`
        *   `backend/src/main/java/com/app/sme_health_backend/obligation/**`
        *   `backend/src/main/java/com/app/sme_health_backend/accounting/model/TransactionEventType.java`
        *   `backend/src/main/java/com/app/sme_health_backend/accounting/service/TransactionReadService.java`
        *   `backend/src/main/java/com/app/sme_health_backend/accounting/service/TransactionReverseService.java`
        *   `backend/src/main/java/com/app/sme_health_backend/shared/exception/GlobalExceptionHandler.java`
        *   `backend/src/test/java/com/app/sme_health_backend/obligation/**`
    *   **Frontend Paths Owned:**
        *   `frontend/src/app/records/receivables/page.tsx`
        *   `frontend/src/app/records/payables/page.tsx`
        *   `frontend/src/app/records/page.tsx`
        *   `frontend/src/components/records/obligations/**`
        *   `frontend/src/lib/api/dev-a/obligations.ts`
        *   `frontend/src/components/records/records-copy.ts`
        *   `frontend/tests/obligations.test.mjs`
        *   `frontend/tests/obligations.live.mjs`
        *   `frontend/tests/accounts-live.integration.mjs`
    *   **Documentation Paths:**
        *   `docs/phase2/OBLIGATIONS_AND_ALLOCATIONS.md` (Revision 1.0 INTEGRATION_VERIFIED)

*   **Developer A Full Stack (Antigravity):**
    *   **Task ID:** `A-F03-PAYMENT-CYCLES-V2` (Multi-Obligation Allocation, Advances, Unapplied Balances & Scheduling)
    *   **Status:** `INTEGRATION_VERIFIED — READY_FOR_COMMIT`
    *   **Backend Paths Owned:**
        *   `backend/src/main/resources/db/migration/V27__create_payment_cycles_and_advances.sql`
        *   `backend/src/main/java/com/app/sme_health_backend/obligation/model/AllocationType.java`
        *   `backend/src/main/java/com/app/sme_health_backend/obligation/model/AdvanceStatus.java`
        *   `backend/src/main/java/com/app/sme_health_backend/obligation/model/ScheduleStatus.java`
        *   `backend/src/main/java/com/app/sme_health_backend/obligation/entity/CounterpartyAdvance.java`
        *   `backend/src/main/java/com/app/sme_health_backend/obligation/entity/ObligationPaymentSchedule.java`
        *   `backend/src/main/java/com/app/sme_health_backend/obligation/entity/ObligationPaymentAllocation.java`
        *   `backend/src/main/java/com/app/sme_health_backend/obligation/repository/CounterpartyAdvanceRepository.java`
        *   `backend/src/main/java/com/app/sme_health_backend/obligation/repository/ObligationPaymentScheduleRepository.java`
        *   `backend/src/main/java/com/app/sme_health_backend/obligation/repository/ObligationPaymentAllocationRepository.java`
        *   `backend/src/main/java/com/app/sme_health_backend/obligation/dto/**`
        *   `backend/src/main/java/com/app/sme_health_backend/obligation/service/PaymentAllocationService.java`
        *   `backend/src/main/java/com/app/sme_health_backend/obligation/service/AdvanceService.java`
        *   `backend/src/main/java/com/app/sme_health_backend/obligation/service/PaymentPlanService.java`
        *   `backend/src/main/java/com/app/sme_health_backend/obligation/controller/PaymentController.java`
        *   `backend/src/main/java/com/app/sme_health_backend/obligation/controller/AdvanceController.java`
        *   `backend/src/main/java/com/app/sme_health_backend/obligation/controller/PaymentCalendarController.java`
        *   `backend/src/main/java/com/app/sme_health_backend/obligation/controller/FinancialObligationController.java`
        *   `backend/src/main/java/com/app/sme_health_backend/accounting/service/TransactionReverseService.java`
        *   `backend/src/test/java/com/app/sme_health_backend/obligation/PaymentCyclesPostgreSqlIT.java`
    *   **Frontend Paths Owned:**
        *   `frontend/src/lib/api/dev-a/payments.ts`
        *   `frontend/src/lib/api/dev-a/advances.ts`
        *   `frontend/src/lib/api/dev-a/payment-calendar.ts`
        *   `frontend/src/components/records/payments/**`
        *   `frontend/src/components/records/advances/**`
        *   `frontend/src/components/records/calendar/**`
        *   `frontend/src/app/records/page.tsx`
        *   `frontend/src/components/records/records-copy.ts`
        *   `frontend/tests/payment-cycles.test.mjs`
    *   **Documentation Paths:**
        *   `docs/phase2/PAYMENT_CYCLES_AND_ADVANCES.md` (Revision 1.0 INTEGRATION_VERIFIED)

*   **Developer A Full Stack (Antigravity):**
    *   **Task ID:** `A-F12A-BANK-RECONCILIATION-V1` (Bank Statement Import, Transaction Matching, Reconciliation & Duplicate Detection)
    *   **Status:** `INTEGRATION_VERIFIED — READY_FOR_COMMIT`
    *   **Backend Paths Owned:**
        *   `backend/src/main/resources/db/migration/V28__create_bank_reconciliation.sql`
        *   `backend/src/main/java/com/app/sme_health_backend/reconciliation/**`
        *   `backend/src/main/java/com/app/sme_health_backend/accounting/service/TransactionReverseService.java` (reconciliation cancellation on reversal)
        *   `backend/src/main/java/com/app/sme_health_backend/shared/exception/GlobalExceptionHandler.java` (reconciliation handlers)
        *   `backend/src/test/java/com/app/sme_health_backend/reconciliation/**`
    *   **Frontend Paths Owned:**
        *   `frontend/src/app/records/reconciliation/page.tsx`
        *   `frontend/src/components/records/reconciliation/**`
        *   `frontend/src/lib/api/dev-a/reconciliation.ts`
        *   `frontend/src/components/records/records-copy.ts` (reconciliation tab copy)
        *   `frontend/src/app/records/page.tsx` (connected Bank Reconciliation tab and workspace routing)
        *   `frontend/tests/reconciliation.test.mjs`
        *   `frontend/tests/reconciliation.live.mjs`
        *   `frontend/tests/accounts-live.integration.mjs`
    *   **Documentation Paths:**
        *   `docs/phase2/BANK_RECONCILIATION.md` (Revision 1.0 INTEGRATION_VERIFIED)

*   **Developer A Backend (Antigravity):**
    *   **Task ID:** `A-W7-FINANCIAL-QUERY-FACADE-V1` (Authorized Read-Only Financial Intelligence & AI Integration Contracts)
    *   **Status:** `PUBLISHED_TO_GITHUB (`e07bba9`)`
    *   **Backend Paths Owned / Modified:**
        *   `backend/src/main/java/com/app/sme_health_backend/financialquery/**`
        *   `backend/src/main/java/com/app/sme_health_backend/accounting/repository/FinancialAccountRepository.java` (added `findByBusinessId`)
        *   `backend/src/main/java/com/app/sme_health_backend/accounting/entity/FinancialAccount.java` (added 7-arg constructor)
        *   `backend/src/main/java/com/app/sme_health_backend/obligation/PaymentPlanService.java` (partial-settlement chronological installment absorption & residual cap)
        *   `backend/src/main/java/com/app/sme_health_backend/shared/exception/GlobalExceptionHandler.java` (added `FinancialQueryValidationException` 400 handler)
        *   `backend/src/test/java/com/app/sme_health_backend/financialquery/**`
    *   **Documentation Paths:**
        *   `docs/phase2/FINANCIAL_QUERY_FACADE.md` (Revision 1.1 INTEGRATION_VERIFIED; Developer B AI Copilot integration pending)
        *   `docs/PHASE2_IMPLEMENTATION_STATE.md`

*   **Developer A Backend (Antigravity):**
    *   **Task ID:** `A-C02-MONTHLY-LEDGER-CUTOVER-PHASE-A` (Canonical Ledger Monthly Projection, Financial Source Reconciliation and Cutover Readiness)
    *   **Status:** `INTEGRATION_VERIFIED (Phase A Read-Only Foundation & Cutover Policy Proposal)`
    *   **Backend Paths Owned / Modified:**
        *   `backend/src/main/java/com/app/sme_health_backend/records/projection/**`
        *   `backend/src/main/java/com/app/sme_health_backend/accounting/repository/AccountMovementRepository.java` (added `findMovementSumsByBusinessIdAndDateRange`)
        *   `backend/src/main/java/com/app/sme_health_backend/accounting/repository/FinancialTransactionRepository.java` (added `findByBusinessIdAndDateRange`)
        *   `backend/src/main/java/com/app/sme_health_backend/accounting/draft/RecordDraftRepository.java` (added `findByBusinessIdAndStatus` & `countByBusinessIdAndStatus`)
        *   `backend/src/test/java/com/app/sme_health_backend/records/projection/**`
    *   **Documentation Paths:**
        *   `docs/phase2/C02_MONTHLY_PROJECTION.md` (Version 1.1 INTEGRATION_VERIFIED)
        *   `docs/PHASE2_IMPLEMENTATION_STATE.md`

---

## 3. Integration & Unit Verification Evidence Summary

| Layer / Gate | Command / Target | Result | Evidence Details |
| :--- | :--- | :--- | :--- |
| **Backend Monthly Ledger Cutover Phase A Suite (Unit + Controller + PostgreSQL IT)** | `.\mvnw.cmd test "-Dtest=MonthlyLedgerProjectionServiceTest,MonthlyLedgerProjectionControllerTest,MonthlyLedgerProjectionPostgreSqlIT"` | **24 / 24 PASSED** | Disposable PostgreSQL 14.22 + Flyway V29. 11 unit tests (cash calculation across opening/inflow/outflow/closing, accrual vs cash recognition basis, three-tier revenue recognition with non-revenue obligation exclusion, zero substitution rejection for COGS/gross profit/operating profit/net profit, diagnostic naming `revenueLessKnownOperatingExpensesBeforeCogs`, unclassified cash movement tracking with partial warnings, draft/occurrence counts, 14-metric shadow comparison, dual-layer RBAC); 4 WebMvc controller tests (both endpoints, parameter validation, role enforcement, active business context binding); 9 PostgreSQL IT fixtures (Fixtures A–G: multi-account cash balance, expense reversal neutrality, draft/recurring occurrence isolation, three-tier accrual revenue provenance, cash vs accrual operating expenses, shadow record comparison, and bit-for-bit SHA-256 database snapshots across 12 populated tables verified 100% unchanged). |
| **Backend Financial Query Suite (Unit + Controller + PostgreSQL IT)** | `.\mvnw.cmd test "-Dtest=FinancialQueryFacadeServiceTest,FinancialQueryControllerTest,FinancialQueryPostgreSqlIT"` | **21 / 21 PASSED** | Disposable PostgreSQL 14.22 + Flyway V29. 8 unit tests (capabilities, multi-scale currency separation across PKR/USD/BHD/JPY + identifier masking, date/type validation, 7-bucket overdue aging + `offset`/`limit` `PARTIAL` pagination, upcoming installments + deduplicated V29 active rule projection, 90-day window/direction bounds, 4 unavailable tools + 500-char query limit, and service-layer RBAC enforcement); 7 WebMvc controller tests (all 8 endpoints, client `businessId` param ignoring, `limit`/`offset` forwarding, malformed UUID/integer HTTP 400 handling, HTTP 403 access denial); 6 PostgreSQL + MockMvc integration tests (Fixture A: 100k opening + 20k receipt - 5k exp - 10k xfer = 105k Bank + 10k Cash = **115,000.00 PKR** combined cash, exclusion of drafts/unpaid obligations/recurring occurrences, historical `asOfDate` progression, and V23 expense reversal = 120k PKR; Fixture B: 50k receivable - 20k settlement = **30,000.00 PKR** in `OVERDUE_1_30`, pre-settlement `asOfDate` 50k, 10k advance application -> 20k, settlement reversal -> 40k, and 2-page `offset`/`limit` pagination; Fixture C: 50k payable with 2x 25k installments + active `RecurringRule` -> **50,000.00 PKR** upcoming payables with zero double counting and recurring rule isolated in `recurringProjections`, then 20k partial settlement -> **30,000.00 PKR** upcoming payables; Fixture D: 4 typed `UNAVAILABLE` tools; Fixture E: full Spring Security HTTP session login across `OWNER`/`ACCOUNTANT`/`MANAGER`/`VIEWER`, unauthenticated/logged-out `401`, no-membership `403`/`409`, cross-tenant override prevention, HTTP `400` bounds; Fixture F: bit-for-bit SHA-256 value-level snapshots + row counts verified strictly unchanged across 16 populated tables). |
| **Backend Phase 2 Full Regression Suite (C11 + 12 Financial Domains)** | `.\mvnw.cmd test "-Dtest=FinancialQueryFacadeServiceTest,...,RecurringTransactionsPostgreSqlIT,BusinessAuthorizationServiceTest,ProjectFundingGapCalculatorTest"` | **544 / 544 PASSED** | 21 C11 tests + 523 regression tests across 40 test classes (Accounts, CurrencyRegistry, Posting, Transfers, Reversals, Record Drafts, Import Drafts, Counterparties, Obligations & Aging, Advances & Payment Cycles, Bank Reconciliation, Recurring Operations, Business Authorization, and Funding Gap Calculator) on PostgreSQL 14.22 + Flyway V29 (0 failures, 0 errors, 01:09 min). |
| **Frontend Full Regression Suite (Read-Only Check)** | `npm test` | **226 / 226 PASSED** | All 226 frontend unit & contract tests across accounts, transactions, transfers, reversals, drafts, imports, counterparties, obligations, payment cycles, reconciliation, and recurring operations passed cleanly (0 failures, 0 skipped). |
| **Backend Bank Reconciliation Suite (Unit + Controller + PostgreSQL IT)** | `.\mvnw.cmd test "-Dtest=BankStatementImportServiceTest,BankReconciliationServiceTest,BankReconciliationControllerTest,BankReconciliationPostgreSqlIT"` | **36 / 36 PASSED** | Disposable PostgreSQL 14.22 + Flyway V28 (BUILD SUCCESS, 44s). Verifies external statement import, candidate ranking, 1-to-1 match confirmation, unmatching with audit trail, row exclusion, partial unique index concurrency enforcement, cross-tenant database rejection via composite FKs, zero financial ledger mutations, automatic reconciliation invalidation & statement entry restoration upon transaction reversal, complete statement-to-draft-to-posting-to-match lifecycle, pure read-only GET safety across all roles (zero database mutations/audit writes), and bank-to-bank dual-leg transfer matching. |
| **Frontend Bank Reconciliation & Total Suites** | `npm test` | **220 / 220 PASSED** | All 4 dedicated reconciliation contract/unit tests (`reconciliation.test.mjs`: typed API adapter, copy parity, ranking and candidate wire shape preservation) + 216 existing frontend tests, 0 failed, 0 skipped. |
| **Real Browser Integration Harness (Bank Reconciliation)** | `node tests/accounts-live.integration.mjs --reconciliation-only` | **EXIT 0 (15 grouped checks, 0 blockers)** | Disposable PostgreSQL 14.22 (Flyway V28) + Spring Boot (8680) + transparent gateway (3580) + Next.js (3581) + Microsoft Edge 155.0.4283.62. 15 check groups passed; blockers=[]; integrated=true. Verified Journeys A–J: statement import (paste TSV, column map, preview, commit, reload persistence, 0 financial writes); exact match (PKR 50k receipt corroborated by bank credit, ranking, confirmation, 0 new cash postings); ambiguous matches (distinguish same-amount movements, select intended candidate, enforce 1-to-1 link); unmatched entry to draft (linked draft with provenance in To Review, unresolved classification); reconciliation correction (undo match with audit reason and status reversal); internal transfer dual-leg (outgoing debit to Leg A, incoming credit to Leg B, 0 duplicate income/expense); duplicate statement handling (idempotent replay and duplicate warnings without auto-deletion); authorization matrix (OWNER/ACCOUNTANT pass, VIEWER/MANAGER 403, cross-tenant 404, CSRF 403); interface quality (bilingual Urdu RTL `بینک مفاہمت`, mobile 390x844 responsive rendering with 0 horizontal overflow, keyboard focus); financial integrity invariant strictly verified (zero mutations to financial transactions, account movements, monthly records, or score results). |
| **Real Browser Integration Harness (Payment Cycles & Advances)** | `node tests/accounts-live.integration.mjs --payment-cycles-only` | **EXIT 0 (14 grouped checks, 0 blockers)** | Disposable PostgreSQL 14.22 (Flyway V27) + Spring Boot (8680) + transparent gateway (3580) + Next.js (3581) + Microsoft Edge 155.0.4283.62. 14 check groups passed; blockers=[]; integrated=true. Verified Journeys A–I: multi-receivable payment (1 tx, 1 movement, +100k, 0 duplicate revenue); multi-payable settlement (1 tx, 1 movement, -150k, 0 duplicate expense); customer advance (50k cash, 50k unapplied, 0 revenue); advance application (70k outstanding, 0 unapplied, 0 new tx, 0 new movements); supplier advance (30k cash, applied to 80k bill -> 50k outstanding, 0 extra movements); reversals (multi-allocation payment reversal with compensating tx and restored UNPAID obligations, advance application reversal restoring unapplied balance with 0 movements, downstream application protection HTTP 400); payment calendar & 3x 50k installment plan (0 tx, 0 double counting); authorization matrix (VIEWER read-only, MANAGER 403, cross-business 404, CSRF 403); and bilingual Urdu RTL (`dir="rtl"`) + mobile 390x844 responsive rendering with 0 horizontal overflow. |
| **Backend Payment Cycles Suite (PostgreSQL IT)** | `.\mvnw.cmd test "-Dtest=PaymentCyclesPostgreSqlIT"` | **15 / 15 PASSED** | Disposable PostgreSQL 14.22 + Flyway V27. Verifies multi-obligation payment allocation (1 cash transaction, 2 allocations, accurate outstanding), unapplied residual advance retention, customer/supplier advances, advance applications (0 cash movements), advance application reversals (0 cash movements), payment reversals with atomic allocation & advance cancellation, downstream application protection, non-posting installment schedules and payment calendar aggregation, idempotent replay, and conflict detection. |
| **Backend Full Obligations & Payments Suite** | `.\mvnw.cmd test "-Dtest=AdvanceControllerTest,PaymentCalendarControllerTest,PaymentControllerTest,FinancialObligationControllerTest,FinancialObligationServiceTest,ObligationAgingCalculatorTest,FinancialObligationPostgreSqlIT,PaymentCyclesPostgreSqlIT"` | **78 / 78 PASSED** | All 78 backend domain tests pass on PostgreSQL 14.22 + Flyway V27 (BUILD SUCCESS, 29.01s). |
| **Frontend Payment Cycles Suite & Total Suites** | `npm test` | **216 / 216 PASSED** | 10 dedicated payment cycles unit/contract tests (`payment-cycles.test.mjs`: copy parity, arithmetic, residual calculation, exhaustion, coverage classification, double-submit prevention, typed API adapters) + 206 existing frontend unit/contract tests, 0 failed, 0 skipped. |
| **Whole Frontend Static & Quality Gates (Payment Cycles)** | `npx tsc --noEmit`, `npm run lint`, `npm run build` | **PASSED (EXIT 0)** | 0 TypeScript errors, 0 ESLint errors (React 19 `set-state-in-effect` and pure function rules compliant). Next.js production build succeeded with Turbopack; static/dynamic pages compiled cleanly. |
| **Backend Obligations Suite (Unit + Controller + PostgreSQL IT)** | `.\mvnw.cmd test "-Dtest=FinancialObligationServiceTest,FinancialObligationControllerTest,FinancialObligationPostgreSqlIT"` | **38 / 38 PASSED** | FinancialObligationServiceTest (21), FinancialObligationControllerTest (9), FinancialObligationPostgreSqlIT (8). EmbeddedPostgres 14.22 with Flyway V26. Verifies canonical obligations creation/listing/details/voiding, partial & full settlement posting with deterministic lock ordering (Obligation -> Account), durable idempotency hash validation, allocation cancellation upon reversal, aging mapping, runtime least privilege (`DELETE` denied), multi-tenant isolation, and historical reversal as-of balance reconciliation (Jan 10, Jan 25, Feb 11). |
| **Real Browser Integration Harness (Obligations Workflow)** | `node tests/accounts-live.integration.mjs --obligations-only` | **EXIT 0 (14 grouped checks, 0 blockers)** | Disposable PostgreSQL 14.22 (Flyway V26) + Spring Boot (8680) + transparent gateway (3580) + Next.js (3581) + Microsoft Edge. 14 check groups passed; blockers=[]; integrated=true. Verified Journeys A–H: customer receivable creation, reload persistence, zero financial mutation; partial payment (+40k bank cash, 60k outstanding, 0 duplicate revenue); full settlement (100k paid, 0 outstanding, boundary protection); supplier payable (80k payable, 30k paid, 50k outstanding, cash -30k, 0 duplicate expense); aging 7-bucket display and allocation detail history; settlement reversal (cash reversed, 80k outstanding restored, allocation CANCELLED); viewer role enforcement and multi-tenant cross-business 404; bilingual Urdu RTL and mobile 390x844 responsive rendering. |
| **Backend Counterparty Suite (Unit + Controller + PostgreSQL IT)** | `.\mvnw.cmd test "-Dtest=CounterpartyServiceTest,CounterpartyControllerTest,CounterpartyPostgreSqlIT"` | **33 / 33 PASSED** | CounterpartyServiceTest (16), CounterpartyControllerTest (9), CounterpartyPostgreSqlIT (8). EmbeddedPostgres 14.22 with Flyway V25. Verifies schema migration, runtime privilege revocation (`DELETE` denied with error 42501), multi-tenant isolation, composite foreign key `(counterparty_id, business_id) ON DELETE RESTRICT` on transactions and drafts, direct SQL cross-tenant rejection (`fk_record_drafts_counterparty`, `fk_fin_tx_counterparty`), same-name entity independence with separate UUIDs, and zero financial mutation guarantee. |
| **Real Browser Integration Harness (Counterparties Foundation)** | `node tests/accounts-live.integration.mjs --counterparties-only` | **EXIT 0 (12 grouped checks, 0 blockers)** | Disposable PostgreSQL 14.22 (Flyway V25) + Spring Boot (8680) + transparent gateway (3580) + Next.js (3581) + Microsoft Edge. 12 check groups passed; blockers=[]; integrated=true. Verified Journeys A–G: customer CRUD, same-name independence, supplier payment with counterparty linkage, customer receipt with idempotency, imported draft workflow with provenance & verbatim amount preservation, archive/restore visibility toggling with historical preservation, RBAC matrix (OWNER/ACCOUNTANT write, MANAGER/VIEWER read-only), multi-tenant cross-business rejection, Urdu RTL layout, mobile 390x844 responsive bounds, and zero unintended financial mutations. |
| **Frontend Counterparty Suite & Total Suites** | `npm test` | **207 / 207 PASSED** | All 9 counterparty tests passed (`counterparties.test.mjs`: API adapter, parser, query string serialization, Urdu unicode, copy parity, transaction and draft contract shape preservation). Total frontend test suite: 198 global + 9 counterparty unit/contract tests passed, 0 failed, 0 skipped. |
| **Whole Frontend Static & Quality Gates (Counterparties)** | `npx tsc --noEmit`, `npm run lint`, `npm run build` | **PASSED (EXIT 0)** | 0 TypeScript errors, 0 ESLint errors (React 19 `set-state-in-effect` compliant). Next.js production build succeeded; static/dynamic pages generated including `/records/contacts` and `/records`. |
| **Real Browser Integration Harness (Import Drafts & Provenance)** | `node tests/accounts-live.integration.mjs` / `node tests/accounts-live.integration.mjs --import-only` | **EXIT 0 (66 grouped checks, 0 blockers)** | Disposable PostgreSQL 14.22 (Flyway V24) + Spring Boot (8680) + transparent gateway (3580) + Next.js (3581) + Microsoft Edge (155.0.4283.62). 66 check groups passed; blockers=[]; integrated=true. Verified Sections 3A–E (CSV/TSV preview, column mapping, amount normalization, unresolved classifications preserved, row exclusion/restoration, URL `batchId`, deep-links, DraftEditor reload persistence, fresh context reload recovery, Urdu RTL and mobile 390x844 bounds, cross-business 404, unauthenticated sign-in boundary with zero private leaks); Section 4 (>100 row chunking, atomic failure rollback, retry, idempotent replay, 409 conflict, distinct twin representation); Section 5 (`uq_import_batch_rows_unique` race handling, VIEWER/MANAGER 403, ACCOUNTANT 201, recorded/discarded draft preservation); Section 6 (zero financial mutation: initial 0 tx / 0 movements / 0 monthly / 0 scores; saving 112 drafts produced 0 financial mutations; recording exactly 1 draft posted exactly 1 transaction and 1 movement, duplicate recording blocked with 409). |
| **Backend Import Draft Suite (Unit + Controller + PostgreSQL IT)** | `.\mvnw.cmd test "-Dtest=ImportDraftServiceTest,ImportDraftControllerTest,ImportDraftPostgreSqlIT"` | **26 / 26 PASSED** | ImportDraftServiceTest (12), ImportDraftControllerTest (6), ImportDraftPostgreSqlIT (8). Real PostgreSQL 14.22 with Flyway V24. Bounded chunks ($\le 100$), composite tenant FKs, idempotent replay, conflict detection (409), verbatim amount input preservation, reload recovery via URL `batchId`, zero financial writes. |
| **Frontend Import Draft & Total Suites** | `npm test` | **189 / 189 PASSED** | All 37 import tests passed (copy parity, lifetime storage of `batchId` and `savedDrafts`, API response contract validation, in-memory preview retention across language switch, business switch clearance, session expiry clearance). Total frontend suite: 0 failed, 0 skipped. |
| **Whole Frontend Static & Quality Gates (Import Drafts)** | `npx tsc --noEmit`, `npm run lint`, `npm run build` | **PASSED (EXIT 0)** | 0 TypeScript errors, 0 ESLint errors. Next.js production build succeeded in 9.1s; static/dynamic pages generated including `/records/import` and `/records`. |
| **Real Browser Integration Harness (Transaction Reversals)** | `node tests/accounts-live.integration.mjs` | **EXIT 0 (64 grouped checks, 0 blockers)** | Disposable PostgreSQL 14.22 (Flyway V23) + Spring Boot (8680) + transparent gateway (3580) + Next.js (3581) + Playwright/Edge. 64 check groups passed; blockers=[]; integrated=true. Verified compensating entries for receipts, expenses, transfers; balance restoration (Bank 50,000, Cash 2,000); summary neutrality (moneyReceived/moneyPaid netted); chronological CASH non-negative simulation rejection; role authorization (OWNER/ACCOUNTANT allowed, VIEWER/MANAGER 403, cross-business 404); detail sheet reversal button, reason & confirmation; bidirectional navigation; and bilingual Urdu RTL badges (`منسوخ شدہ` / `منسوخی`). |
| **Backend Reversal Suite (Unit + Controller + PostgreSQL IT)** | `.\mvnw.cmd test "-Dtest=TransactionReverseServiceTest,TransactionControllerTest,FinancialTransactionReversalPostgreSqlIT"` | **35 / 35 PASSED** | TransactionReverseServiceTest (9), TransactionControllerTest (16), FinancialTransactionReversalPostgreSqlIT (10). Disposable PostgreSQL 14.22 + Flyway V23. Verified deterministic locking, inverse movements, running CASH deficit rejection, capacity limits, single reversal per transaction (`uq_fin_tx_reversed_transaction_id`), composite tenant FK (`fk_fin_tx_reversed_tx_business`), no reversal-of-reversals, idempotent replay, conflict detection. |
| **Frontend Reversal Suite (Targeted Unit & Contracts)** | `node tests/transaction-reversals.test.mjs` | **6 / 6 PASSED** | Verified inverted movement direction wire parser, dual movement transfer reversal parser, `transactionsApi.reverse` client adapter, parameter validation, bilingual copy parity. |
| **Real Browser Integration Harness (Account Transfers & Dual Movements)** | `node tests/accounts-live.integration.mjs` | **EXIT 0 (54 grouped checks, 0 blockers)** | Disposable PostgreSQL 14.22 (Flyway V22) + Spring Boot (8680) + transparent gateway (3580) + Next.js (3581) + Playwright/Edge. 54 check groups passed; blockers=[]; integrated=true. Verified atomic dual-movement transfers, 5,000 PKR Meezan-to-Cash transfer, balance updates (50,000 -> 45,000 / 1,000 -> 6,000), cash flow neutrality (moneyReceived/moneyPaid untouched), directional transfer summaries, idempotent replay (201), conflict (409), role authorization (OWNER/ACCOUNTANT allowed, VIEWER/MANAGER 403), natural form entry, plain confirmation string, and bilingual Urdu RTL. Live review verified at `http://127.0.0.1:3480/records`. |
| **Real Browser Integration Harness (Record Drafts & Review Workflow)** | `node tests/accounts-live.integration.mjs` | **EXIT 0 (47 grouped checks, 0 blockers)** | Fresh isolated V21 environment, Spring Boot + Next.js + PostgreSQL 14.22 (Flyway V21) + Playwright/Edge. 47 grouped checks passed; blockers=[]; integrated=true. Verified draft persistence, resume, edit, discard, atomic recording, exact-version checks, 27 malformed initial/replay syntax rejections (HTTP 400), idempotent replay (HTTP 201), changed-payload conflict (HTTP 409), concurrent racing, verbatim raw amount input preservation, no duplicate financial writes, and untouched monthly/score fingerprints. |
| **Real Browser Integration Harness (Records, Accounts & Transactions)** | `node tests/accounts-live.integration.mjs` | **EXIT 0 (33 grouped checks, 0 blockers)** | Real Spring Boot (port 8180) + Next.js (port 3180) + PostgreSQL 14.22 (Flyway V20) + Playwright/Edge. 33 grouped checks passed; blockers=[]; integrated=true. Verified account read/create/post, current-only capacity overflow rendering, summary aggregation (`4501.06 / 0.00 / 4501.06`, count 3), natural/Arabic/Urdu inputs, concurrency, idempotent replay, CSRF, multi-tenant isolation, and untouched monthly records/score fingerprints. |
| **Frontend Targeted Unit & Total Suites** | `npm test` / `node --test tests/*.test.mjs` | **186 / 186 PASSED** (total frontend) | All unit, contract, and regression suites passed with zero failures or skips. Includes 34 record import preview tests (parsing, mapping, review, in-memory preview retention across language switch, business switch clearance, session expiry clearance), 11 transfer tests, 6 reversal tests, and existing suites. |
| **Type Check & Lint** | `npx tsc --noEmit`, `npm run lint` | **PASSED** | 0 TypeScript errors, 0 ESLint errors (1 existing baseline warning in SessionProvider.tsx:213). |
| **Next.js Production Build** | `npm run build` (`BACKEND_API_URL=http://127.0.0.1:8280`) | **EXIT 0** | Turbopack compilation succeeded in 867ms, static pages generated. Routes manifest includes `/records/import`. |
| **Demo Mode Isolation** | `node tests/accounts-live.integration.mjs --demo` | **EXIT 0** | Verified English/Urdu unavailable preview with zero `/api/` network requests (zero private requests). |
| **Baseline Data & Score Integrity** | Pre/Post creation & posting snapshot check | **VERIFIED UNCHANGED** | Row-count and MD5 content fingerprints of `monthly_records`, `score_results`, `insights`, and `recommendations` remained identical after all creation, posting, transfer, and draft mutations. |
| **Backend Transfer Suite (Unit + WebMvc + PostgreSQL IT)** | `mvnw.cmd test "-Dtest=TransferServiceTest,TransferControllerTest,FinancialTransferPostgreSqlIT"` | **27 / 27 PASSED** | TransferServiceTest (11), TransferControllerTest (7), FinancialTransferPostgreSqlIT (9). Disposable PostgreSQL 14.22 + Flyway V22. Verified deterministic lock ordering, simulation, cash non-negative invariant, capacity limits, idempotent replay, conflict detection, summary arithmetic. |
| **Backend Comprehensive Suite (Drafts + Accounts + Transactions)** | `mvn test "-Dtest=RecordDraftServiceTest,RecordDraftControllerTest,TransactionPostServiceTest,RecordDraftPostgreSqlIT"` | **113 / 113 PASSED** | RecordDraftServiceTest (74), RecordDraftControllerTest (15), TransactionPostServiceTest (13), RecordDraftPostgreSqlIT (11). Verified 54 parameterized syntax cases, live PostgreSQL draft lifecycle, idempotent recovery, verbatim raw input. |
| **Backend Comprehensive Suite (Accounts + Transactions + Summaries)** | `mvn test "-Dtest=TransactionControllerTest,TransactionPostServiceTest,FinancialTransactionPostgreSqlIT,FinancialAccountPostgreSqlIT,AccountReadServiceTest,AccountCreateServiceTest,AccountControllerTest,CurrencyRegistryTest,BusinessAuthorizationServiceTest"` | **140 / 140 PASSED** | TransactionControllerTest (11), TransactionPostServiceTest (13), FinancialTransactionPostgreSqlIT (11), FinancialAccountPostgreSqlIT (7), AccountReadServiceTest (18), AccountCreateServiceTest (15), AccountControllerTest (19), CurrencyRegistryTest (39), BusinessAuthorizationServiceTest (7). Disposable PostgreSQL 14.22 + Flyway V18 + V19 + V20. |
| **Maven Project Runner (Liquidity Calculator)** | `mvnw.cmd test -Dtest=ProjectFundingGapCalculatorTest` | **24 / 24 PASSED** (0 failures, 0 errors, 14.0s) | Verified reference fixture (50k gap, peak 11-01, recovery 11-04), zero events/opening, sufficient funds, non-recovery, temporary recovery, final-day recovery, same-day netting, repeated peaks, order invariance, large decimal accuracy, invalid input rejections, and immutability. |
| **Maven Project Runner (Obligation Aging Calculator)** | `.\mvnw.cmd test "-Dtest=ObligationAgingCalculatorTest"` | **19 / 19 PASSED** (0 failures, 0 errors, 11.284s) | Pure Java calculator verified via project Maven test runner. Tested 7 canonical buckets (`UNKNOWN_DUE_DATE`, `NOT_YET_DUE`, `DUE_TODAY`, `OVERDUE_1_30`, `OVERDUE_31_60`, `OVERDUE_61_90`, `OVERDUE_91_PLUS`), zero/active reconciliation, boundary dates, PKR-only currency enforcement, multi-direction support, and total balance consistency. |
| **Real Browser Integration Harness (Import Preview & Usability)** | Real headless Edge on live gateway (port 3580) | **9 / 9 PASSED** (0 blockers) | Authenticated CSV upload, column mapping, account selection, review navigation, purpose override, EN $\leftrightarrow$ UR toggle preview retention, RTL flipped, reset/abandonment verified, and zero non-GET private requests. |
| **Backend Authorization** | `mvn test -Dtest=BusinessAuthorizationServiceTest` | **7 / 7 PASSED** | OWNER, ACCOUNTANT, MANAGER, VIEWER permission grants verified for `FINANCIAL_ACCOUNT_READ`, `FINANCIAL_ACCOUNT_CREATE`, `FINANCIAL_TRANSACTION_READ`, and `FINANCIAL_TRANSACTION_POST`. |

### Limitations & Outstanding Gates
1. **Transfer & Reversal Scope Bounded:** Internal transfers are strictly limited to active, distinct PKR accounts within the server-resolved active business. Transaction reversals provide complete bookkeeping inverse entries with non-negative cash simulation under V23, committed locally. Multi-currency / FX transfers, transfer fees, recurring transfers, and transfer drafts remain pending.
2. **Monthly Snapshots & Cutover Preserved:** Monthly health scores, `MonthlyRecord` summaries, and Hanafi Zakat recommendations remain untouched in this slice. Existing snapshots remain authoritative for monthly intelligence; monthly ledger cutover remains pending.
3. **Opening & Movement Calculation:** Balances reflect persisted opening amounts plus sum of posted movements $\le$ cutoff date. Current-only capacity overflow preserves valid opening per authorized v1.3 invariant.
4. **Pure Calculators Scope:** F07 Project Funding Gap and F03 Obligation Aging calculators are pure, deterministic Java calculation foundations. Spring registration, HTTP REST endpoints, persistence entities, and background jobs remain pending.
5. **Spreadsheet Import Scope:** Pure client-side CSV/TSV parser and preview wizard. No backend persistence, draft creation, transaction mutation, or financial writes exist in this slice.
6. **Migration Notification:** V18, V19, V20, V21, V22, and V23 are authored and verified locally on `dev/suleman`. Formal cross-team notification to Fatima remains pending.
7. **Production Gates:** PostgreSQL 17 TLS/HBA, Docker production container builds, external Google Vision OCR, WhatsApp API, baseline items R01–R03, and cross-team integration gates remain open.

---

## 4. Project Lead Decisions (`SULEMAN_AUTHORIZED`)

The following decisions were authorized by project lead Muhammad Suleman Adil:
1.  **Permission Grants:** `FINANCIAL_ACCOUNT_READ` and `FINANCIAL_TRANSACTION_READ` granted to `OWNER`, `ACCOUNTANT`, `MANAGER`, and `VIEWER`. `FINANCIAL_ACCOUNT_CREATE` and `FINANCIAL_TRANSACTION_POST` granted strictly to `OWNER` and `ACCOUNTANT` only (`MANAGER` and `VIEWER` are denied creation/posting with 403 Forbidden). Future role `SALESPERSON` receives no grants.
2.  **Direct Route Access:** Direct `/finance/accounts` and `/finance/transactions` route access permitted under standard session/business/permission checks.
3.  **Module Resolver Scope:** No C03 module resolver, module keys, or global navigation links in this initial slice.
4.  **V18, V19 & V20 Reservations:** Flyway `V18` (financial accounts), `V19` (financial idempotency records), and `V20` (financial transactions & movements) reserved for Dev A following clean collision checks against remote `main` and `dev/fatima`.
5.  **Durable Idempotency Specification:** Account creation and transaction posting require `Idempotency-Key` header with atomic SHA-256 payload digest matching. Duplicate names return 409 Conflict.
6.  **Raw Identifier Omission:** `accountIdentifier` input omitted in V1 wire creation to prevent raw unencrypted banking identifiers from entering storage or logs.

*Cross-Team Notification Note:* Fatima has not yet been notified of these decisions. Her formal approval has not been fabricated, and these decisions apply strictly to the accounts and transaction boundary.

---

## 5. Published Remote Context & Collision Check

A fresh, read-only remote inspection was conducted against GitHub without fetching or modifying the shared working tree:
*   `origin/main` commit: `b51e27696cccc59b94aa9d9c9388ad0faa5b4176` (Flyway head: `V17`).
*   `origin/dev/fatima` commit: `60f570c53fe0e35248544d24f4d3ac55459564ad` (Dated 10 Oct 2026; Flyway head: `V17`).
*   **Collision Result:** `V18`, `V19`, and `V20` confirmed unreserved on remote `main` and `dev/fatima`.
*   **Limitation:** Unpushed local work on Fatima's machine cannot be inspected via GitHub API; this limitation is formally recorded.
*   **Namespace Reconciliation Item:** Fatima's published draft conventions propose `/api/v2`, whereas Contract v1.2 preserves `/api/finance/accounts` and `/api/finance/transactions` as currently consumed by Codex. In this bounded slice, existing paths are preserved. Namespace reconciliation is recorded as an A/B integration item.

---

## 6. Baseline Findings Register (From Fatima Handoff)

The following findings reported in Fatima's published handoff are recorded for tracking (not modified or touched in this task):
*   **R01 (Document Security IT Flake):** Two full integration runs failed `DocumentSecurityPostgreSqlIT` (`expected processing_interrupted, actual unavailable`); isolated class execution passed.
*   **R02 (Same-Month Document Duplication):** Suspected risk of same-month document contribution or source duplication.
*   **R03 (Audit Identity Confusion):** Suspected audit actor vs. business identity confusion in security audit events (resolved in Phase 2 accounting and transactions by explicitly passing distinct `context.userId()` as actor and `context.businessId()` as business).

---

## 7. Migration Reservation Register

| Version | Scope / Description | Assigned Owner | Global Status |
| :--- | :--- | :--- | :--- |
| **V1–V17** | Completed MVP schema & provenance triggers | Core / Baseline | **COMMITTED & LOCKED** |
| **V18** | Financial Accounts Schema (`V18__create_financial_accounts.sql`) | Antigravity (Dev A) | **COMMITTED (LOCAL `8070af5`)** |
| **V19** | Financial Idempotency Records (`V19__create_financial_idempotency.sql`) | Antigravity (Dev A) | **COMMITTED (LOCAL `68d2995`)** |
| **V20** | Financial Transactions & Account Movements (`V20__create_financial_transactions.sql`) | Antigravity (Dev A) | **COMMITTED (LOCAL `e586b06`)** |
| **V21** | Persistent Record Drafts Schema (`V21__create_record_drafts.sql`) | Antigravity (Dev A) | **COMMITTED (LOCAL)** |
| **V22** | Account Transfers & Dual Movement Constraints (F01) | Antigravity (Dev A) | **COMMITTED (LOCAL)** |
| **V23** | Transaction Reversals & Constraints (F01) (`V23__add_transaction_reversals.sql`) | Antigravity (Dev A) | **AUTHORED (LOCAL / READY_FOR_COMMIT)** |
| **V24** | Import Draft Provenance Schema (`V24__create_import_draft_provenance.sql`) | Antigravity (Dev A) | **AUTHORED (LOCAL / READY_FOR_COMMIT)** |
| **V25** | Counterparty Schema & Integrations (`V25__create_counterparties.sql`) | Antigravity (Dev A) | **AUTHORED (LOCAL / INTEGRATION_VERIFIED)** |
| **V26** | Financial Obligations & Payment Allocations (`V26__create_financial_obligations.sql`) | Antigravity (Dev A) | **AUTHORED (LOCAL / INTEGRATION_VERIFIED)** |
| **V27** | Payment Cycles, Advances & Payment Schedules (`V27__create_payment_cycles_and_advances.sql`) | Antigravity (Dev A) | **AUTHORED (LOCAL / INTEGRATION_VERIFIED)** |
| **V28** | Bank Reconciliation Schema (`V28__create_bank_reconciliation.sql`) | Antigravity (Dev A) | **COMMITTED (LOCAL)** |
| **V29** | Recurring Financial Operations Schema (`V29__create_recurring_transactions.sql`) | Antigravity (Dev A) | **COMMITTED (`f1049c1` — CURRENT HEAD)** |
| **V30** | Project Planning & Budgeting (Phase A) | Fatima (Dev B) | **GLOBALLY RESERVED BY DEVELOPER B** |
| **V31+** | Monthly Ledger Cutover Phase B Configuration (Pending Cutover Policy Approval) | Antigravity (Dev A) | **RESERVED FOR PHASE B (Subject to Dev B V30 Integration)** |
| *Note* | **C02 Phase A introduces 0 migrations** (read-only projection & shadow comparison engine). | Dev A | **VERIFIED (0 MIGRATIONS IN PHASE A)** |

---

## 8. Retained Later-Policy Issues (From Codex Review)

*   **M1 (Opening Authority):** Single opening balance authority; cached balance equals opening balance plus sum of subsequent movements.
*   **M2 (Source Identity):** Event UUID vs. content SHA-256 fingerprint.
*   **M3 (Idempotency):** Durable uniqueness table and replay authorization checks.
*   **M4 (Date Invariants):** Account opening date must be $\le$ cutover month start date.
*   **M5 (Cash Propagation):** Backdated cash movements propagate month-end cash forward indefinitely.
*   **M6 (Negative Compatibility):** Derived negative totals return atomic domain error, never silent clamping.
*   **M7 (Monthly Mapping):** Historical account inclusion and month-end foreign currency base valuation.
*   **M8 (Declarations Matrix):** Hybrid maintenance of non-derivable optional declarations via PATCH.
*   **M9 (OCR Posting):** Confirm-once creates posted transactions; unconfirmed drafts never post actuals.
*   **M10 (DTO Consistency):** Consistent enum naming (`INCREASE`/`DECREASE`) and non-cash recognition.
