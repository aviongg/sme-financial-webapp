# FinSight Phase 2 Implementation State & Dependency Register

**Version:** `2.4`
**Last Updated:** 18 October 2026
**Canonical Branch:** `dev/suleman`
**Current HEAD SHA:** `c99d193e6eb6ccced11f6df27c2a939b9596bf02`
**Baseline Release:** PR #3 integrated into `main` (Flyway V1–V17)  

---

## 1. Environment & Baseline Status

| Item | Value | Evidence / Verification |
| :--- | :--- | :--- |
| **Active Branch** | `dev/suleman` | Verified via `git branch --show-current` |
| **Commit SHA** | `c99d193e6eb6ccced11f6df27c2a939b9596bf02` | Account transfers checkpoint commit |
| **Flyway Head** | **V23** | `backend/src/main/resources/db/migration/V23__add_transaction_reversals.sql` (V23 authored & integration verified) |
| **Untracked Artifacts** | `backend/.cp.txt` | Maven classpath artifact (13,680 bytes); preserved untouched |
| **Phase 2 Implementation** | **Account Transfers, Reversals, Persistent Drafts, Transactions Backend, Summaries, Records, Import Preview & Calculators** | `POST /api/finance/transactions/{id}/reverse`, `POST /api/finance/transfers`, `POST /api/finance/drafts`, `GET /api/finance/drafts`, `POST /api/finance/transactions`, `GET /api/finance/transactions/summary`, Import Preview route/wizard, and pure Obligation Aging & Funding Gap calculators |
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
| **Obligation Aging Calculator (A-F03-AGING-CALCULATOR)** | **UNIT_VERIFIED (Ready for Commit C)** | Pure Java calculator verified via project Maven test runner (`mvnw.cmd test "-Dtest=ObligationAgingCalculatorTest"`, 19/19 passed, 0 failures, 11.284s). 7 canonical buckets, exact reconciliation between total balance and bucket amounts, zero/active reconciliation, boundary dates, and multi-currency/direction support. |

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
    *   **Documentation Paths:**
        *   `docs/phase2/OBLIGATION_AGING_CALCULATION.md` (Revision 1.0 UNIT_VERIFIED)

---

## 3. Integration & Unit Verification Evidence Summary

| Layer / Gate | Command / Target | Result | Evidence Details |
| :--- | :--- | :--- | :--- |
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
| **Maven Project Runner (Obligation Aging Calculator)** | `.\mvnw.cmd test "-Dtest=ObligationAgingCalculatorTest"` | **19 / 19 PASSED** (0 failures, 0 errors, 11.284s) | Pure Java calculator verified via project Maven test runner. Tested 7 canonical buckets (`UNKNOWN_DUE_DATE`, `NOT_YET_DUE`, `DUE_TODAY`, `OVERDUE_1_30`, `OVERDUE_31_60`, `OVERDUE_61_90`, `OVERDUE_91_PLUS`), zero/active reconciliation, boundary dates, multi-currency support, and total balance consistency. |
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
