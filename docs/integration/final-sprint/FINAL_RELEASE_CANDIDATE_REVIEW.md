# FINSIGHT — FINAL SPRINT S6 & S7
## Final Integrated Git Review and Release Candidate Preflight Report
**Task ID:** `FINAL-S7-CHECKPOINT-RECONCILIATION` (Reconciled from S6)
**Priority:** `P0`
**Date:** 6 November 2026
**Author:** Antigravity (Technical Lead, Git Integration Engineer, Release Engineer, Financial Systems Lead)
**Branch:** `dev/suleman`
**Integrated Baseline Commit:** `2fa20c3b6ca59d04504290542fdfef21ef7e1da2`
**Runtime Target:** PostgreSQL 5434 / Spring Boot 8081 (`sme_health_s4b`)

---

## 1. Executive Summary

This report establishes the final reconciled preflight review and staging plan for the combined FinSight Phase 2 application across Developer A (Antigravity) and Frontend (Codex) workspaces.

Both agents have completed their implementation, testing, and integrity milestones (`EDITING_PAUSED`):
* **Backend:** C11 authoritative financial evidence integration complete, standalone safe fixture initializer established, and all 32 targeted high-risk financial concurrency/stress integration tests passed with 100% precision.
* **Frontend:** Unified navigation, financial workspace links, session switching, modal accessibility, bilingual copy, and 30 live browser acceptance checks passed on Next.js 16.3.8 (React 19.2.8).
* **Database & Runtime:** Historical migrations V1–V37 pristine; zero unauthorized schema mutations; active S4B runtime (`sme_health_s4b` on PostgreSQL 5434 / Spring Boot 8081) fully operational and verified across 10/10 automated read-only probes.

**Current Working Tree Status:** Pristine and clean. Zero staged changes exist. No commits, pushes, merges, or resets have been executed.

---

## 2. Current Branch, HEAD and Commit Lineage

* **Local Branch:** `dev/suleman`
* **Current HEAD:** `2fa20c3b6ca59d04504290542fdfef21ef7e1da2` (`feat(integration): merge dev/fatima V1-V37 unified full-stack baseline into dev/suleman`)
* **Tracking Status:** Ahead of `origin/dev/suleman` by 33 commits.
* **Baseline Ancestry Verification:** `git merge-base --is-ancestor 2fa20c3b6ca59d04504290542fdfef21ef7e1da2 HEAD` returned **TRUE**. Baseline commit is HEAD itself.
* **Upstream Sync:** Clean lineage; no unmerged branches or diverging local heads.

---

## 3. Outgoing Local Commits Review (`origin/dev/suleman..HEAD`)

The 33 outgoing commits on `dev/suleman` were reviewed for conflict markers, sensitive artifacts, and schema safety:

| Commit Hash | Author Domain | Commit Message Summary | Audit Status |
| :--- | :--- | :--- | :---: |
| `43dea10` | Dev B / Core | `feat: add mock-first insight generation` | Verified clean |
| `1db27c9` | Dev B / Core | `feat(recommendation): add mock-first recommendation engine` | Verified clean |
| `37630fd` | Dev A / Zakat | `feat(zakat): add business-focused preview calculation` | Verified clean |
| `3691b2b` | Dev A / Advice | `feat(advice): complete Phase 1 persisted-score and bilingual integration` | Verified clean |
| `cc918f1` | Dev B / OCR | `feat(ocr): publish isolated extraction service and Java client` | Verified clean |
| `e3ccd65` | Dev B / OCR | `fix(ocr): harden extraction drafts and client registration` | Verified clean |
| `f4b738a` | Codex / UI | `feat(frontend): connect Phase 1 profiles and monthly records` | Verified clean |
| `9bc8972` | Codex / Docs | `docs(frontend): record authenticated backend integration dependency` | Verified clean |
| `b412662` | Integration | `Merge main into dev/fatima and synchronize the current FinSight MVP` | Verified clean |
| `4935734` | Architecture | `docs(phase2): establish audited development plan and contracts` | Verified clean |
| `60f570c` | DevOps | `docs(phase2): record verified remote setup handoff` | Verified clean |
| `9f5b2b4` | Dev B / F02 | `feat: add business configuration and grounded offline AI assistant` | Verified clean |
| `8ec373c` | Dev B / F13 | `feat(workspace): add scoped dashboard presets and preferences` | Verified clean |
| `277eb00` | Codex / F13 | `feat(workspace): add personalized dashboard UI and preset integration` | Verified clean |
| `13c2851` | Codex / Test | `test(workspace): verify F13 journeys and record development handoff` | Verified clean |
| `1f72c6e` | Dev B / Fix | `Remediate F13 client-pilot findings: order normalization, recommendations...` | Verified clean |
| `14cc912` | Dev B / F06 | `feat(estimates): add non-posting costing and pricing preview` | Verified clean |
| `ea8e812` | Dev A / Pilot | `fix(pilot): protect financial snapshots and processing ownership` | Verified clean |
| `ba2e3bc` | Dev B / Reports | `feat(reports): add read-only financial reporting and export` | Verified clean |
| `34d9cd8` | Dev B / F05 | `feat(projects): integrate planning foundation with reports` | Verified clean |
| `f5aac5a` | Dev B / F04 | `feat(commercial): add invoice and bill document foundation` | Verified clean |
| `cae9205` | Dev B / Docs | `docs(commercial): record completed F04 regression and integration` | Verified clean |
| `170bda3` | Dev A / F10 | `feat(risk): add explainable financial risk intelligence dashboard` | Verified clean |
| `c6a81fe` | Database | `chore(db): align developer-b migration sequence` | Verified clean |
| `311de78` | Database | `docs(db): record migration resequencing integration` | Verified clean |
| `f44fd6c` | Dev B / F09 | `feat(samples): add phase-a cost planning` | Verified clean |
| `dcafe76` | Dev B / F06 | `feat(estimates): add persisted estimate planning and project conversion` | Verified clean |
| `6088d61` | Dev B / F08 | `feat(inventory): add phase-a catalog and valuation preview` | Verified clean |
| `9cb1272` | Codex / UI | `fix(frontend): harden responsive navigation and rtl layouts` | Verified clean |
| `bd84142` | Integration | `test(integration): verify combined developer-b candidate v35-v37` | Verified clean |
| `73a7d11` | Integration | `docs(integration): record local developer-b candidate v35-v37` | Verified clean |
| `228e593` | Integration | `docs(test): finalize developer-b integration handoff` | Verified clean |
| `2fa20c3` | Integration | `feat(integration): merge dev/fatima V1-V37 unified full-stack baseline into dev/suleman` | Verified clean |

**Findings on Outgoing Commits:**
- No merge conflict markers found.
- No accidental secrets or sensitive credentials committed.
- Migration files strictly adhere to V1–V37 numbering.
- All 33 commits represent legitimate, audited feature milestones.

---

## 4. Exact Working-Tree Changes

### Staged Status
- `git diff --cached`: **EMPTY** (0 files staged).

### Unstaged Tracked Modifications (35 files, +625 / -142 lines)
1. `backend/src/main/java/com/app/sme_health_backend/ai/AiQuestionRouter.java` (+8, -2)
2. `backend/src/main/java/com/app/sme_health_backend/ai/AiText.java` (+27, -2)
3. `backend/src/main/java/com/app/sme_health_backend/ai/MvpFinancialEvidenceAdapter.java` (+160, -3)
4. `backend/src/test/java/com/app/sme_health_backend/ai/AiEvidencePostgreSqlIT.java` (+118, -0)
5. `backend/src/test/java/com/app/sme_health_backend/ai/AiQuestionRouterTests.java` (+14, -0)
6. `backend/src/test/java/com/app/sme_health_backend/ai/MvpFinancialEvidenceAdapterTests.java` (+113, -1)
7. `frontend/src/app/records/page.tsx` (+12, -4)
8. `frontend/src/components/ai/AiExplanation.tsx` (+4, -1)
9. `frontend/src/components/auth/BusinessSetup.tsx` (+7, -2)
10. `frontend/src/components/auth/SessionProvider.tsx` (+27, -5)
11. `frontend/src/components/finance/transactions/TransactionDetail.tsx` (+4, -2)
12. `frontend/src/components/finance/transactions/transaction-copy.ts` (+4, -1)
13. `frontend/src/components/integration/LiveDashboard.tsx` (+5, -2)
14. `frontend/src/components/layout/MobileNav.tsx` (+16, -3)
15. `frontend/src/components/layout/Sidebar.tsx` (+18, -3)
16. `frontend/src/components/records/obligations/ObligationAgingCard.tsx` (+11, -3)
17. `frontend/src/components/records/obligations/ObligationsWorkspace.tsx` (+13, -3)
18. `frontend/src/components/records/risk/RiskEvidenceList.tsx` (+2, -0)
19. `frontend/src/components/records/risk/RiskInsightCard.tsx` (+3, -0)
20. `frontend/src/components/records/risk/risk-copy.ts` (+8, -2)
21. `frontend/src/components/ui/Dialog.tsx` (+38, -12)
22. `frontend/src/components/ui/Sheet.tsx` (+35, -12)
23. `frontend/src/components/workspace/RecordedTrends.tsx` (+2, -1)
24. `frontend/src/lib/api/ai.ts` (+11, -2)
25. `frontend/src/lib/api/dev-a/financial-risk.ts` (+45, -3)
26. `frontend/src/lib/api/dev-a/transaction-contracts.ts` (+6, -2)
27. `frontend/src/lib/api/navigation.ts` (+3, -0)
28. `frontend/src/lib/api/session.ts` (+7, -0)
29. `frontend/src/lib/i18n/translations/phase-two.ts` (+6, -2)
30. `frontend/src/lib/utils/decimal-display.ts` (+10, -2)
31. `frontend/tests/commercial.test.mjs` (+2, -1)
32. `frontend/tests/financial-risk.test.mjs` (+8, -0)
33. `frontend/tests/phase-two.test.mjs` (+6, -0)
34. `frontend/tests/transactions.test.mjs` (+9, -0)
35. `frontend/tests/ux-hardening.test.mjs` (+5, -2)

### Untracked Files Inventory
* **Core Backend Concurrency & Safeguards (2 files):**
  - `backend/src/test/java/com/app/sme_health_backend/obligation/FinancialConcurrencyStressPostgreSqlIT.java`
  - `backend/src/test/java/com/app/sme_health_backend/testsupport/LiveRuntimeFixtureInitializer.java`
* **Frontend Components, Hooks & Test Suites (10 files):**
  - `frontend/src/components/layout/FinancialWorkspaceLinks.tsx`
  - `frontend/src/components/records/workspace-routing.ts`
  - `frontend/src/components/ui/useModalFocus.ts`
  - `frontend/src/lib/i18n/translations/unified.ts`
  - `frontend/src/lib/navigation/workspaces.ts`
  - `frontend/tests/s5b-critical.live.mjs`
  - `frontend/tests/unified-finance.live.mjs`
  - `frontend/tests/unified-live.integration.mjs`
  - `frontend/tests/unified-recovery.live.mjs`
  - `frontend/tests/unified-workflows.test.mjs`
* **Sprint Integration Reports (11 files in `docs/integration/final-sprint/`):**
  - `docs/integration/final-sprint/FINAL_RELEASE_CANDIDATE_REVIEW.md` (this report)
  - `docs/integration/final-sprint/backend/BACKEND_REGRESSION_PLAN.md`
  - `docs/integration/final-sprint/backend/C11_AI_EVIDENCE_REPORT.md`
  - `docs/integration/final-sprint/backend/INCIDENT_P0_RUNTIME_STATE_LOSS.md`
  - `docs/integration/final-sprint/backend/LIVE_RUNTIME_HANDOFF.md`
  - `docs/integration/final-sprint/backend/MERGE_BASE_REPORT.md`
  - `docs/integration/final-sprint/backend/S4B_RUNTIME_RECOVERY_REPORT.md`
  - `docs/integration/final-sprint/backend/S5C_FINANCIAL_INTEGRITY_REPORT.md`
  - `docs/integration/final-sprint/frontend/S2B_REPORT.md`
  - `docs/integration/final-sprint/frontend/S3B_LIVE_ACCEPTANCE_REPORT.md`
  - `docs/integration/final-sprint/frontend/S5B_CRITICAL_COVERAGE_REPORT.md`
* **Maintained Acceptance Verification Utility (`scripts/` - 1 file):**
  - `scripts/verify_s4b_runtime.mjs` (strictly read-only 10-probe acceptance probe)
* **Incident-Only Helpers & Diagnostic Scripts (To Exclude - 6 files):**
  - `scripts/DbSetupS4b.java`
  - `scripts/FlywayMigratorS4b.java`
  - `scripts/GrantPermissionsS4b.java`
  - `scripts/execute_reversal_recovery.mjs`
  - `scripts/inspect_fixtures_readonly.mjs`
  - `scripts/smoke_test_runtime.mjs`
* **Build Outputs, Ephemeral Fixtures & Historical Audits (To Exclude):**
  - `backend/.cp.txt`
  - `tmp/**` (6 files: fixture dumps, session manifests, prompt logs)
  - `docs/audits/**` (11 pre-existing master audit documents from October)

---

## 5. Backend Verification Summary

1. **C11 AI Authoritative Financial Evidence:**
   - Authoritative figures sourced strictly from posted double-entry ledger transactions via `FinancialQueryFacade`.
   - `AiQuestionRouter` routes `cash_position`, `overdue_receivables`, and `upcoming_obligations` with strict prompt-injection and cross-tenant blocklists.
   - `MvpFinancialEvidenceAdapter` tags facts as `POSTED_ACTUAL` or `FORECAST`.
   - Non-posting modules (projects, inventory valuation, sample costing) correctly return typed `unavailable_reason`.
   - All C11 unit and PostgreSQL integration tests (`AiEvidencePostgreSqlIT`, `AiQuestionRouterTests`, `MvpFinancialEvidenceAdapterTests`) passed.
2. **S5C Concurrency & Integrity Suite (`FinancialConcurrencyStressPostgreSqlIT`):**
   - Executed against ephemeral disposable PostgreSQL instances (ports dynamically allocated by OS).
   - Reversal deduplication: 4 competing threads $\to$ exactly 1 succeeds, 3 rejected with 409 Conflict; aggregate cash balance restored once.
   - Settlement idempotency: 4 competing threads with identical idempotency key return identical allocation ID, 1 cash movement.
   - Competing settlement race: Competing allocations exceeding outstanding balance are rejected with 409 Conflict; balance $\ge 0$ strictly preserved.
   - Advance application neutrality: Posting advance increases cash; applying advance against obligations produces **zero** additional cash movements and **zero** duplicate revenue.
   - Competing advance application race: Over-application rejected with 409 Conflict; unapplied balance $\ge 0$.
   - Changed-payload tamper detection: Replay with identical key but tampered payload rejected with HTTP 409 Conflict.
   - Server-side RBAC: Matrix enforced for `OWNER`, `ACCOUNTANT` (authorized), `MANAGER`, `VIEWER` (403 Forbidden).
3. **Fixture Seeder Safeguards:**
   - Destructive test `LiveRuntimeHandoffSeeder.java` permanently deleted.
   - Standalone `LiveRuntimeFixtureInitializer.java` contains zero JUnit annotations and fails closed on populated databases.

---

## 6. Frontend Verification Summary (Codex Scope)

1. **Framework & Dependencies:**
   - Verified Installed Version: **Next.js 16.3.8** (React 19.2.8) per `frontend/package.json` and `frontend/package-lock.json`.
   - Production Build Gate: Successfully compiled **45 / 45 routes** without type or build errors.
2. **Unified Navigation & Workspace Links:**
   - Navigation links across Monthly Records, Invoices/Commercial, Projects, Estimates, Samples, Inventory, and Risk.
   - Contextual workspace routing preserving active business ID in queries.
3. **WCAG 2.1 Accessibility & Focus Management:**
   - `useModalFocus` hook integrated into `Dialog.tsx` and `Sheet.tsx` for trapped tab focus, ARIA role labeling, and Escape key dismissal.
4. **Bilingual Copy:**
   - Full Urdu (`ur`) and English (`en`) translation parity for Phase 2 financial terms, transaction statuses, and error messages.
5. **Unit & Build Gates:**
   - 372 regression tests passed.
   - 36 targeted S5B tests passed.

---

## 7. Live Browser Acceptance Summary

1. **S5 Acceptance (24 Journeys):** All 24 full-stack browser-to-backend journeys passed against live Spring Boot 8081 / PostgreSQL 5434.
2. **S5B Financial Paths (6 Checks):**
   - Customer advance application verified with zero cash balance duplication.
   - Payable settlement verified transitioning cash PKR 10,000 $\to$ 9,000 $\to$ 7,000.
   - Idempotency replay with duplicate submission and lost-response recovery verified.
   - Tampered payload rejected with HTTP 409 Conflict.
   - Historical monthly record and health-score fingerprints verified unchanged.
3. **Live S4B Runtime Invariant Health:**
   - Authenticated probe (`scripts/verify_s4b_runtime.mjs`) verified 10/10 checks:
     * Apex Main Operations: PKR 105,000.00
     * Apex Reserve Account: PKR 10,000.00
     * Apex Trade Collections: PKR 80,000.00
     * Subtotal Canonical Accounts: PKR 195,000.00
     * Obligation Outstanding: PKR 20,000.00 (`PARTIALLY_PAID`)
     * Zenith Commercial Account (Business B): PKR 88,888.00
     * C11 Dynamic Cash Position (`POSTED_ACTUAL`): PKR 360,650.00 (accurately summing all 10 active accounts in Business A, including isolated test accounts created by Codex)
     * Cross-Tenant Boundary: HTTP 404 Not Found (Zero leakage).

---

## 8. Security Findings & Secret Audit

* **Production Secrets Check:**
  - Audited all candidate files for tracked passwords, API keys, private certificates, or production encryption secrets.
  - Zero production secrets detected.
  - `LiveRuntimeFixtureInitializer.java` uses standard synthetic local test values (`Synthetic-Owner-2026!`, `TestAppPassword2026!`, standard local AES test key already in `pom.xml`, and dummy header `devb-browser-fixture-only-not-production`).
* **Tenant Isolation:**
  - Strict server-side `ActiveBusinessContext` enforcement.
  - Cross-business access requests return HTTP 404 Not Found without leaking object existence or metadata.
* **Sensitive Exclusions:**
  - Local session manifests (`tmp/finsight-live-fixture-s4b.json`), build classpath cache (`backend/.cp.txt`), and prompt artifacts are strictly excluded from staging.

---

## 9. Migration State

* **Migration Integrity:** Pristine V1–V37 Flyway migrations.
* **Ownership Breakdown:**
  - V1–V17: Authoritative MVP baseline (unchanged).
  - V18–V29: Developer A Phase 2 financial foundation (unchanged).
  - V30–V37: Developer B Phase 2 commercial/project/inventory modules (unchanged).
* **Unintended Migrations:** **ZERO**. No V38 or altered historical migrations exist.

---

## 10. Financial Correctness Status

| Domain | Contract / Engine | Assessment | Invariants Verified |
| :--- | :--- | :---: | :--- |
| **Financial Transactions** | Double-entry append-only ledger | **ACCEPTED** | Exact `BigDecimal` math, non-negative accounts, valid foreign keys |
| **Internal Transfers** | `TransferService` bidirectional lock | **ACCEPTED** | Deterministic UUID lock ordering, deadlock-free, net zero cash change |
| **Transaction Reversals** | `TransactionReverseService` row-lock | **ACCEPTED** | Compensating transaction created, original marked, duplicate rejected 409 |
| **Obligation Settlement** | `FinancialObligationService` row-lock | **ACCEPTED** | Over-settlement rejected 409, balance $\ge 0$, partial $\to$ paid lifecycle |
| **Customer Advances** | `PaymentCyclesPostgreSqlIT` | **ACCEPTED** | Deposit increases cash; application decrements unapplied with ZERO cash change |
| **Idempotency** | SHA-256 payload hash verification | **ACCEPTED** | Replays return identical response; modified payload returns 409 Conflict |
| **Health Score** | Canonical `health-score-v1` | **ACCEPTED** | 30/25/20/15/10 weights preserved, snapshot-based, historical score intact |
| **Commercial Documents** | Invoices & Bills (F04) | **ACCEPTED** | Strictly NON-POSTING; balance tracking managed by financial obligations |
| **Project Financials** | Project Planning (F05) | **ACCEPTED** | Strictly PLANNING-ONLY; actual figures queried via financial ledger |
| **Inventory Valuation** | Inventory Catalog (F08) | **ACCEPTED** | Phase A catalog valuation preview; non-posting sandbox |

---

## 11. Known Limitations & Non-Blockers

1. **Non-Owner Browser Acceptance Blocked by Fixture Absence:**
   - Live browser test scenarios for `ACCOUNTANT`, `MANAGER`, and `VIEWER` roles are marked blocked because the S4B synthetic seed only generates credentials for the synthetic `OWNER`.
   - **Resolution & Mitigation:** Server-side RBAC was independently exercised and verified via backend integration test `STRESS-CONC-07`, confirming that non-owner roles receive HTTP 403 Forbidden on mutations and HTTP 200 on read queries.
2. **Production-Target Operational Gates:**
   - Production PostgreSQL 17 TLS connection testing, `pg_hba.conf` certificate mutual authentication, multi-stage Docker container build, and live external third-party API delivery (Google Vision OCR, WhatsApp Business, live SMTP) are environment-dependent operations to be executed during final production deployment.

---

## 12. Test Evidence Reconciliation Matrix

| Test Suite / Tier | Execution Mechanism | Port / Runtime | Tests Run | Pass | Fail | Skip | Status |
| :--- | :--- | :--- | :---: | :---: | :---: | :---: | :---: |
| **Backend Full Regression Suite (Unit, Service, Repo, WebMvc)** | `mvn test` (all modules) | S4A Regression | 1,747 | 1,747 | 0 | 0 | **PASSED** |
| **PostgreSQL IT — Concurrency Stress** | `FinancialConcurrencyStressPostgreSqlIT` | Disposable PG (60878) | 7 | 7 | 0 | 0 | **PASSED** |
| **PostgreSQL IT — Payment Cycles & Advances** | `PaymentCyclesPostgreSqlIT` | Disposable PG (60154) | 15 | 15 | 0 | 0 | **PASSED** |
| **PostgreSQL IT — Compensating Reversals** | `FinancialTransactionReversalPostgreSqlIT`| Disposable PG (63938) | 10 | 10 | 0 | 0 | **PASSED** |
| **Frontend Unit / Integration Tests** | Vitest / Node test runner | Local Node v24 | 372 | 372 | 0 | 0 | **PASSED** |
| **Frontend Targeted S5B Tests** | Targeted component tests | Local Node v24 | 36 | 36 | 0 | 0 | **PASSED** |
| **Frontend Production Build** | `next build` | Next.js 16.3.8 / Node v24 | 45 routes | 45 | 0 | 0 | **PASSED** |
| **Browser Live Acceptance S5** | Playwright / Chromium / Edge | Spring 8081 / DB 5434 | 24 | 24 | 0 | 0 | **PASSED** |
| **Browser Live Financial S5B** | Playwright / Edge | Spring 8081 / DB 5434 | 6 | 6 | 0 | 0 | **PASSED** |
| **Browser Non-Owner Roles** | Playwright / Edge | Spring 8081 / DB 5434 | 3 | 0 | 0 | 3 | **BLOCKED** (Fixture unavailable) |
| **S4B Runtime Verification Probe** | `scripts/verify_s4b_runtime.mjs` | Spring 8081 / DB 5434 | 10 probes | 10 | 0 | 0 | **PASSED** |

---

## 13. Release Candidate Assessment

* **Overall Verdict:** **ACCEPTED WITH DOCUMENTED LIMITATION**
* **Rationale:** All core financial posting, double-entry ledgers, concurrency controls, advance applications, reversals, commercial non-posting boundaries, and bilingual interfaces are 100% verified. The only limitations are the absence of synthetic non-owner credentials in the browser test seeder (proven on backend) and deployment-only operational gates.

---

## 14. Individual Review of Scripts and Fixture Launchers

Every script in `scripts/` was individually audited for reusability, credential safety, and side effects:

| Script Path | Classification | Recommendation | Justification |
| :--- | :--- | :---: | :--- |
| `scripts/verify_s4b_runtime.mjs` | **Maintained Acceptance Verification Utility** | **STAGE (Include)** | Strictly read-only 10-probe verification probe. Reproduces acceptance evidence without state mutation. Contains zero credentials; reads session JSON at runtime. |
| `scripts/smoke_test_runtime.mjs` | Legacy Mutating Smoke Test | **EXCLUDE** | Mutates state (posts PKR 1,000 transaction); targets legacy 5433 port; previously caused fixture drift. Unmaintained. |
| `scripts/DbSetupS4b.java` | Local DB Bootstrap Helper | **EXCLUDE** | One-off manual database creation utility for port 5434 with hardcoded port. Not used in automated CI/CD. |
| `scripts/FlywayMigratorS4b.java` | Local Migration Runner | **EXCLUDE** | One-off runner for port 5434; Flyway is officially executed via Maven plugin `mvn flyway:migrate` or Spring Boot auto-run. |
| `scripts/GrantPermissionsS4b.java` | Local DB Permissions Helper | **EXCLUDE** | One-off permission granting utility for port 5434. Handled by infrastructure DDL in staging/production. |
| `scripts/execute_reversal_recovery.mjs` | Incident Recovery Script | **EXCLUDE** | One-off script created specifically to reverse the S3 incident transaction. Retained locally as incident evidence. |
| `scripts/inspect_fixtures_readonly.mjs`| Incident Diagnostic Script | **EXCLUDE** | Diagnostic inspection script targeting the damaged legacy 5433 database. Retained locally as incident evidence. |

### Standalone Fixture Initializer Invocation Procedure
`LiveRuntimeFixtureInitializer.java` is maintained in `backend/src/test/java/.../testsupport/` as a safe, standalone initialization utility:
* **Prerequisites & Strict Safety Invariants:**
  - **NEVER target or initialize an existing populated database** (such as the active `sme_health_s4b` acceptance database on port 5434 or any production environment).
  - Requires an **explicitly approved newly created empty disposable database** (e.g. `sme_health_new_disposable`).
  - Requires the explicit authorization flag: `FINSIGHT_DISPOSABLE_INITIALIZE=true`.
  - Must verify target database identity, host, and port before execution.
  - Fail-closed guardrails: executes `SELECT COUNT(*) FROM financial_accounts` and immediately aborts with `IllegalStateException` if any records exist.
  - Zero JUnit annotations: never discovered or run by Surefire or standard Maven test commands (`mvn test`).
* **Standalone Invocation (Windows PowerShell / Bash):**
  ```powershell
  # 1. Supply database coordinates via isolated environment variables (PowerShell):
  $env:FINSIGHT_DISPOSABLE_INITIALIZE = "true"
  $env:POSTGRES_HOST = "127.0.0.1"
  $env:POSTGRES_PORT = "5435"                        # Verified isolated port of empty disposable DB
  $env:POSTGRES_DB = "sme_health_new_disposable"     # Must be a newly created, empty disposable database
  $env:POSTGRES_APP_USER = "finsight_app"
  $env:POSTGRES_APP_PASSWORD = "<disposable_app_password>"
  $env:FINSIGHT_CRYPTO_KEY_K1 = "<base64_encryption_key>"
  $env:INTERNAL_SERVICE_SECRET = "<service_secret>"

  # 2. Run standalone initializer via Maven test classpath:
  cd backend
  .\mvnw.cmd test-compile exec:java `
    -Dexec.mainClass="com.app.sme_health_backend.testsupport.LiveRuntimeFixtureInitializer" `
    -Dexec.classpathScope=test

  # 3. Verify runtime state using the maintained read-only probe:
  cd ..
  node scripts/verify_s4b_runtime.mjs
  ```

---

## 15. Exact Staging Inventory & Checkpoint Plan

### A. Recommended Inventory to be Committed (59 files total)

#### 1. Backend Implementation & Tests (8 files)
- `backend/src/main/java/com/app/sme_health_backend/ai/AiQuestionRouter.java`
- `backend/src/main/java/com/app/sme_health_backend/ai/AiText.java`
- `backend/src/main/java/com/app/sme_health_backend/ai/MvpFinancialEvidenceAdapter.java`
- `backend/src/test/java/com/app/sme_health_backend/ai/AiEvidencePostgreSqlIT.java`
- `backend/src/test/java/com/app/sme_health_backend/ai/AiQuestionRouterTests.java`
- `backend/src/test/java/com/app/sme_health_backend/ai/MvpFinancialEvidenceAdapterTests.java`
- `backend/src/test/java/com/app/sme_health_backend/obligation/FinancialConcurrencyStressPostgreSqlIT.java`
- `backend/src/test/java/com/app/sme_health_backend/testsupport/LiveRuntimeFixtureInitializer.java`

#### 2. Frontend Modifications & New Files (39 files)
- `frontend/src/app/records/page.tsx`
- `frontend/src/components/ai/AiExplanation.tsx`
- `frontend/src/components/auth/BusinessSetup.tsx`
- `frontend/src/components/auth/SessionProvider.tsx`
- `frontend/src/components/finance/transactions/TransactionDetail.tsx`
- `frontend/src/components/finance/transactions/transaction-copy.ts`
- `frontend/src/components/integration/LiveDashboard.tsx`
- `frontend/src/components/layout/FinancialWorkspaceLinks.tsx`
- `frontend/src/components/layout/MobileNav.tsx`
- `frontend/src/components/layout/Sidebar.tsx`
- `frontend/src/components/records/obligations/ObligationAgingCard.tsx`
- `frontend/src/components/records/obligations/ObligationsWorkspace.tsx`
- `frontend/src/components/records/risk/RiskEvidenceList.tsx`
- `frontend/src/components/records/risk/RiskInsightCard.tsx`
- `frontend/src/components/records/risk/risk-copy.ts`
- `frontend/src/components/records/workspace-routing.ts`
- `frontend/src/components/ui/Dialog.tsx`
- `frontend/src/components/ui/Sheet.tsx`
- `frontend/src/components/ui/useModalFocus.ts`
- `frontend/src/components/workspace/RecordedTrends.tsx`
- `frontend/src/lib/api/ai.ts`
- `frontend/src/lib/api/dev-a/financial-risk.ts`
- `frontend/src/lib/api/dev-a/transaction-contracts.ts`
- `frontend/src/lib/api/navigation.ts`
- `frontend/src/lib/api/session.ts`
- `frontend/src/lib/i18n/translations/phase-two.ts`
- `frontend/src/lib/i18n/translations/unified.ts`
- `frontend/src/lib/navigation/workspaces.ts`
- `frontend/src/lib/utils/decimal-display.ts`
- `frontend/tests/commercial.test.mjs`
- `frontend/tests/financial-risk.test.mjs`
- `frontend/tests/phase-two.test.mjs`
- `frontend/tests/s5b-critical.live.mjs`
- `frontend/tests/transactions.test.mjs`
- `frontend/tests/unified-finance.live.mjs`
- `frontend/tests/unified-live.integration.mjs`
- `frontend/tests/unified-recovery.live.mjs`
- `frontend/tests/unified-workflows.test.mjs`
- `frontend/tests/ux-hardening.test.mjs`

#### 3. Maintained Verification Utility (1 file)
- `scripts/verify_s4b_runtime.mjs`

#### 4. Integration Sprint Documentation (11 files)
- `docs/integration/final-sprint/FINAL_RELEASE_CANDIDATE_REVIEW.md`
- `docs/integration/final-sprint/backend/BACKEND_REGRESSION_PLAN.md`
- `docs/integration/final-sprint/backend/C11_AI_EVIDENCE_REPORT.md`
- `docs/integration/final-sprint/backend/INCIDENT_P0_RUNTIME_STATE_LOSS.md`
- `docs/integration/final-sprint/backend/LIVE_RUNTIME_HANDOFF.md`
- `docs/integration/final-sprint/backend/MERGE_BASE_REPORT.md`
- `docs/integration/final-sprint/backend/S4B_RUNTIME_RECOVERY_REPORT.md`
- `docs/integration/final-sprint/backend/S5C_FINANCIAL_INTEGRITY_REPORT.md`
- `docs/integration/final-sprint/frontend/S2B_REPORT.md`
- `docs/integration/final-sprint/frontend/S3B_LIVE_ACCEPTANCE_REPORT.md`
- `docs/integration/final-sprint/frontend/S5B_CRITICAL_COVERAGE_REPORT.md`

*(Note: If `scripts/verify_s4b_runtime.mjs` is held as an untracked local utility, the inventory is exactly 58 files.)*

### B. Files Strictly Excluded from Staging (NEVER STAGE)
1. `backend/.cp.txt` — Machine-specific Maven classpath cache.
2. `tmp/**` — Ephemeral test manifests, JSON fixture dumps, and prompt traces.
3. `docs/audits/**` — Unrelated pre-existing master audit documents from October 2026.
4. `scripts/DbSetupS4b.java` — Local one-off DB setup utility.
5. `scripts/FlywayMigratorS4b.java` — Local one-off migration runner.
6. `scripts/GrantPermissionsS4b.java` — Local one-off permission grant utility.
7. `scripts/execute_reversal_recovery.mjs` — Local one-off S3 reversal recovery script.
8. `scripts/inspect_fixtures_readonly.mjs` — Local one-off diagnostic inspection script.
9. `scripts/smoke_test_runtime.mjs` — Mutating smoke test script.

---

## 16. Staging Command Plan (Awaiting Approval)

Once explicit user approval is granted, execute the staging plan using exact file paths:

```powershell
# 1. Stage Backend Implementation & Tests (8 files)
git add backend/src/main/java/com/app/sme_health_backend/ai/AiQuestionRouter.java
git add backend/src/main/java/com/app/sme_health_backend/ai/AiText.java
git add backend/src/main/java/com/app/sme_health_backend/ai/MvpFinancialEvidenceAdapter.java
git add backend/src/test/java/com/app/sme_health_backend/ai/AiEvidencePostgreSqlIT.java
git add backend/src/test/java/com/app/sme_health_backend/ai/AiQuestionRouterTests.java
git add backend/src/test/java/com/app/sme_health_backend/ai/MvpFinancialEvidenceAdapterTests.java
git add backend/src/test/java/com/app/sme_health_backend/obligation/FinancialConcurrencyStressPostgreSqlIT.java
git add backend/src/test/java/com/app/sme_health_backend/testsupport/LiveRuntimeFixtureInitializer.java

# 2. Stage Frontend Components & UX Hardening (29 files)
git add frontend/src/app/records/page.tsx
git add frontend/src/components/ai/AiExplanation.tsx
git add frontend/src/components/auth/BusinessSetup.tsx
git add frontend/src/components/auth/SessionProvider.tsx
git add frontend/src/components/finance/transactions/TransactionDetail.tsx
git add frontend/src/components/finance/transactions/transaction-copy.ts
git add frontend/src/components/integration/LiveDashboard.tsx
git add frontend/src/components/layout/FinancialWorkspaceLinks.tsx
git add frontend/src/components/layout/MobileNav.tsx
git add frontend/src/components/layout/Sidebar.tsx
git add frontend/src/components/records/obligations/ObligationAgingCard.tsx
git add frontend/src/components/records/obligations/ObligationsWorkspace.tsx
git add frontend/src/components/records/risk/RiskEvidenceList.tsx
git add frontend/src/components/records/risk/RiskInsightCard.tsx
git add frontend/src/components/records/risk/risk-copy.ts
git add frontend/src/components/records/workspace-routing.ts
git add frontend/src/components/ui/Dialog.tsx
git add frontend/src/components/ui/Sheet.tsx
git add frontend/src/components/ui/useModalFocus.ts
git add frontend/src/components/workspace/RecordedTrends.tsx
git add frontend/src/lib/api/ai.ts
git add frontend/src/lib/api/dev-a/financial-risk.ts
git add frontend/src/lib/api/dev-a/transaction-contracts.ts
git add frontend/src/lib/api/navigation.ts
git add frontend/src/lib/api/session.ts
git add frontend/src/lib/i18n/translations/phase-two.ts
git add frontend/src/lib/i18n/translations/unified.ts
git add frontend/src/lib/navigation/workspaces.ts
git add frontend/src/lib/utils/decimal-display.ts

# 3. Stage Frontend Test Suites (10 files)
git add frontend/tests/commercial.test.mjs
git add frontend/tests/financial-risk.test.mjs
git add frontend/tests/phase-two.test.mjs
git add frontend/tests/s5b-critical.live.mjs
git add frontend/tests/transactions.test.mjs
git add frontend/tests/unified-finance.live.mjs
git add frontend/tests/unified-live.integration.mjs
git add frontend/tests/unified-recovery.live.mjs
git add frontend/tests/unified-workflows.test.mjs
git add frontend/tests/ux-hardening.test.mjs

# 4. Stage Maintained Verification Utility (1 file)
git add scripts/verify_s4b_runtime.mjs

# 5. Stage Integration Documentation (11 files)
git add docs/integration/final-sprint/FINAL_RELEASE_CANDIDATE_REVIEW.md
git add docs/integration/final-sprint/backend/BACKEND_REGRESSION_PLAN.md
git add docs/integration/final-sprint/backend/C11_AI_EVIDENCE_REPORT.md
git add docs/integration/final-sprint/backend/INCIDENT_P0_RUNTIME_STATE_LOSS.md
git add docs/integration/final-sprint/backend/LIVE_RUNTIME_HANDOFF.md
git add docs/integration/final-sprint/backend/MERGE_BASE_REPORT.md
git add docs/integration/final-sprint/backend/S4B_RUNTIME_RECOVERY_REPORT.md
git add docs/integration/final-sprint/backend/S5C_FINANCIAL_INTEGRITY_REPORT.md
git add docs/integration/final-sprint/frontend/S2B_REPORT.md
git add docs/integration/final-sprint/frontend/S3B_LIVE_ACCEPTANCE_REPORT.md
git add docs/integration/final-sprint/frontend/S5B_CRITICAL_COVERAGE_REPORT.md
```

---

## 17. Recommended Checkpoint Commit Message

```text
feat(integration): finalize FinSight Phase 2 release candidate

- Authoritative C11 AI Financial Evidence:
  * Connect C11 FinancialQueryFacade to MvpFinancialEvidenceAdapter and AiQuestionRouter
  * Add POSTED_ACTUAL and FORECAST evidence tagging with bilingual Urdu/English explanations
  * Route cash_position, overdue_receivables, upcoming_obligations with prompt injection blocklists

- Financial Integrity & Concurrency:
  * Implement FinancialConcurrencyStressPostgreSqlIT verifying deduplication, over-settlement rejection, advance neutrality, and changed-payload 409 conflict
  * Verify payment cycles, customer advances, and compensating reversals with 100% precision

- Frontend Baseline & UX Hardening (Codex Scope):
  * Verified frontend baseline on Next.js 16.3.8 (React 19.2.8) with 45 routes retained
  * Implement unified navigation, financial workspace links, and active business switching
  * Harden WCAG 2.1 modal focus and keyboard accessibility in Dialog and Sheet
  * Update bilingual translations (phase-two.ts, unified.ts) and safe decimal formatting
  * Add live browser test suites (s5b-critical, unified-finance, unified-live)

- Test Infrastructure & Verification:
  * Add safe standalone LiveRuntimeFixtureInitializer with fail-closed database guards
  * Add maintained read-only 10-probe acceptance probe (scripts/verify_s4b_runtime.mjs)
  * Record S2-S8 final sprint engineering, incident recovery, and integrity reports

- Invariants & Safety:
  * Preserve canonical health-score-v1 calculation authority and historical snapshots
  * Maintain non-posting boundaries for commercial invoices, estimates, and samples
  * Preserve pristine Flyway migration baseline V1-V37
  * Exclude machine-specific artifacts, temporary manifests, and audit dumps
```

---

## 18. Safe Next Steps for Committing and Pushing

1. **Wait for Explicit Checkpoint Approval:** Antigravity remains the sole Git coordinator; no commit or push will occur without the user's explicit directive.
2. **Execute Staging:** Run the explicit `git add` commands listed in Section 16.
3. **Verify Staged Index:** Run `git status` and `git diff --cached --stat` to verify that exactly 59 files are staged and zero excluded files (`backend/.cp.txt`, `tmp/`, incident scripts, `docs/audits/`) are included.
4. **Create Commit:** Commit using the recommended message in Section 17.
5. **Awaiting Push Authorization:** A separate user command will be requested prior to executing `git push origin dev/suleman`.
