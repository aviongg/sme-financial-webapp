# Zarwia Candidate v3 — Next-Phase Engineering Ownership & Workstream Governance Proposal

**Document Version:** `1.0.0`  
**Date:** November 17, 2026  
**Author:** Antigravity (Git Integration Coordinator & Senior Backend Architect)  
**Target Candidate:** Zarwia Candidate v3 (`origin/integration/finsight-production-candidate-v3`)  
**Frozen Tree Baseline:** `19547bf3dda9744d51e74c87a908e2e42577299c`  
**Scope:** Post-Review Workstream Division, Coordination Locks, and Release Management  

---

## 1. Governance Principles & Architectural Boundaries

To prevent regressions, cross-owner file collisions, and accidental bypass of core financial rules, the next development phase will adhere to five strict engineering principles:

1. **Financial Authority Invariant:** Developer A (Suleman) remains the sole backend authority for all financial balances, journal entries, obligation calculations, FX conversions, loan amortization, and Zakat calculations. No other assistant or module may compute authoritative financial numbers.
2. **Strict Domain Non-Duplication:** Developer B (Fatima) owns commercial document state lifecycles (F04, F06), sample cost planning (F09), inventory valuation sandboxes (F08), and AI orchestration. When commercial events need to affect the general ledger, they must invoke Developer A's authoritative posting services.
3. **Frontend Display Discipline:** Codex owns UI rendering, form state management, client validation, bilingual presentation (EN/UR/RTL), and accessibility. The frontend formats inputs and surfaces backend calculations, but executes zero independent financial math.
4. **Exclusive File Coordination Locks:** Shared infrastructure files (such as `MonthlyRecordService.java`, `BusinessPermission.java`, and `SessionProvider.tsx`) require explicit coordination and lock windows before modification.
5. **Git Discipline:** All fix and feature branches must branch from the exact frozen tree SHA (`19547bf3dda9744d51e74c87a908e2e42577299c`), use isolated worktrees, and merge exclusively via `--no-ff` PRs after passing all required CI gates. Force-pushing to shared branches is prohibited.

---

## 2. Workstream Breakdown Across the 9 Functional Streams

---

### Stream 1: Financial & Backend Corrections
* **Primary Owner:** Suleman (Developer A)
* **Supporting Roles:** Independent Financial Auditor
* **Core Scope:**
  - Fine-tuning transaction reversal edge cases (multi-currency partial reversals).
  - Currency rounding edge-case hardening for BHD (3 decimal places) and JPY (0 decimal places).
  - Loan amortization and financing liability schedule automation (upgrading `monthly_financing_inputs`).
  - Bank statement parser extension for regional CSV/OFX formats.
  - Recurring transaction scheduling optimization and dead-letter queue handling.
* **Key Files / Modules Owned:**
  - `backend/src/main/java/com/finsight/service/FinancialAccountService.java`
  - `backend/src/main/java/com/finsight/service/JournalEntryService.java`
  - `backend/src/main/java/com/finsight/service/FinancialObligationService.java`
  - `backend/src/main/java/com/finsight/service/TransactionReversalService.java`
  - `backend/src/main/java/com/finsight/service/FxSettlementService.java`
  - `backend/src/main/java/com/finsight/service/BankReconciliationService.java`
  - `backend/src/main/resources/db/migration/V18`–`V29`, `V38`–`V44`
* **Dependencies:** None. Operates as the foundation for other streams.
* **Deliverable / Milestone:** Financial Engine Hardening Milestone (zero variance on regional currencies; automated loan schedule inputs).

---

### Stream 2: Commercial & Operational Corrections
* **Primary Owner:** Fatima (Developer B)
* **Supporting Roles:** Suleman (posting review), Codex (form contract review)
* **Core Scope:**
  - Finalizing commercial lifecycle edge cases (Estimate $\rightarrow$ Invoice $\rightarrow$ Receipt transitions).
  - Inventory valuation adjustments (FIFO vs Weighted Average cost reconciliation).
  - Sample cost recovery tracking and variance analysis against actual bulk production.
  - Inbound WhatsApp message classification routing and customer intent parsing.
* **Key Files / Modules Owned:**
  - `backend/src/main/java/com/finsight/commercial/...`
  - `backend/src/main/java/com/finsight/inventory/...`
  - `backend/src/main/java/com/finsight/samplecost/...`
  - `backend/src/main/java/com/finsight/whatsapp/inbound/...`
  - `backend/src/main/resources/db/migration/V30`–`V37`, `V45`–`V51`
* **Dependencies:** Stream 1 (depends on obligation creation and payment allocation services).
* **Deliverable / Milestone:** Commercial Operations Closure Milestone (complete quote-to-cash lifecycle without orphan documents).

---

### Stream 3: UI/UX Improvements & DEF-11
* **Primary Owner:** Codex (Frontend Lead)
* **Supporting Roles:** Suleman (tenant session review), Fatima (commercial form review)
* **Core Scope:**
  - **DEF-11 Resolution:** Implement unsaved work intercept in `IntegrationBoundary.tsx` and `SessionProvider.tsx` when a cross-tab business switch occurs.
  - Form draft auto-save in `sessionStorage` keyed by `(businessId, route)`.
  - **DEF-09 Resolution:** Replace raw `DIRECT_SALE_RECEIPT` activity enum with localized strings (EN: "Direct Sale Receipt", UR: "براہ راست فروخت کی رسید").
  - **DEF-12 Resolution:** Replace raw text UUID inputs with an intuitive document evidence picker modal.
  - **DEF-14 Resolution:** Add direct bill OCR evidence navigation links and commercial document status badges.
  - **DEF-16 Resolution:** Prefill `/search?q=` query parameter into the search input component.
  - **DEF-19 Resolution:** Prevent blank state on anonymous browser Back button.
  - **DEF-20 Resolution:** Ensure active business badge remains visible on 390×600 mobile viewports.
* **Key Files / Modules Owned:**
  - `frontend/src/components/layout/IntegrationBoundary.tsx`
  - `frontend/src/context/SessionProvider.tsx`
  - `frontend/src/app/(dashboard)/...`
  - `frontend/src/components/ui/...`
* **Dependencies:** Stream 1 & 2 API contracts.
* **Deliverable / Milestone:** Production UX & Usability Milestone (DEF-09 through DEF-20 fully closed).

---

### Stream 4: Advanced Features & AI Copilot Grounding
* **Primary Owner:** Fatima (Developer B)
* **Supporting Roles:** Suleman (facade review), Codex (copilot UI)
* **Core Scope:**
  - Grounding AI Copilot responses exclusively in `FinancialQueryFacade` deterministic data.
  - Conversational WhatsApp business report generation.
  - Multi-business comparative analytics dashboard widgets.
  - Advanced PDF report generation (Zakat summary, Balance Sheet, P&L statement).
* **Key Files / Modules Owned:**
  - `backend/src/main/java/com/finsight/ai/...`
  - `backend/src/main/java/com/finsight/whatsapp/conversation/...`
  - `backend/src/main/java/com/finsight/reporting/...`
* **Dependencies:** Stream 1 (read-only queries through C11 Facade).
* **Deliverable / Milestone:** AI & Intelligence Readiness Milestone.

---

### Stream 5: Shared Integration Contracts & V52 Maintenance
* **Primary Owner:** Joint Ownership (Suleman & Fatima)
* **Supporting Roles:** Architecture / Integration Lead
* **Core Scope:**
  - Maintaining and tuning V52 automatic transaction-driven monthly projection triggers (`trg_*_monthly_projection_dirty`).
  - Ensuring zero lock contention during high-volume ledger batch posting.
  - Validating that project funding commitments map accurately to commercial obligations.
  - Guaranteeing perpetual inventory COGS movements integrate seamlessly into the general ledger.
* **Key Files / Modules Owned:**
  - `backend/src/main/java/com/finsight/service/MonthlyRecordService.java`
  - `backend/src/main/java/com/finsight/service/MonthlyProjectionService.java`
  - `backend/src/main/resources/db/migration/V52__transaction_driven_monthly_projection.sql`
* **Dependencies:** Streams 1 and 2.
* **Deliverable / Milestone:** Integrated Accounting-Operations Pipeline Milestone.

---

### Stream 6: Infrastructure, Deployment & DevOps
* **Primary Owner:** Suleman (DevOps & Backend Lead)
* **Supporting Roles:** Fatima (Docker container configurations)
* **Core Scope:**
  - Physical Windows runner Docker rehearsal: Execute live container initialization, verification, and shutdown on a physical machine equipped with Docker Desktop.
  - Production PostgreSQL 17 deployment with TLS encryption and strict HBA role privilege enforcement (`finsight_migrator` vs `finsight_app`).
  - Production Docker image build pipeline (`images.yml`) and container image vulnerability scanning.
  - Staging VPS deployment, Nginx reverse proxy configuration, TLS termination, and ConfigTree production secret mounting (`crypto_key_k1`).
  - Paired cold database backup and point-in-time recovery verification drill (establishing verified RPO and RTO metrics).
* **Key Files / Modules Owned:**
  - `docker-compose.yml`, `docker-compose.demo.yml`
  - `scripts/demo-guards.psm1`, `scripts/init-demo.ps1`, `scripts/start-demo.ps1`, `scripts/stop-demo.ps1`
  - `.github/workflows/deploy.yml`, `.github/workflows/images.yml`
* **Dependencies:** Stream 7 (Security configuration).
* **Deliverable / Milestone:** Staging & Production Infrastructure Deployment Milestone.

---

### Stream 7: Security, Authentication & Performance Hardening
* **Primary Owner:** Suleman (Security Lead)
* **Supporting Roles:** Codex (Client-side auth integration)
* **Core Scope:**
  - Validation of parent-user locking during MFA enrollment.
  - Production `crypto_key_k1` AES-256-GCM key rotation procedures.
  - CSRF protection validation across all mutating REST endpoints.
  - Enforcing strict production CORS policy (`CorsOriginPolicy.java`) with zero wildcards.
  - Database index profiling for high-cardinality multi-tenant queries.
  - Next.js production bundle size optimization and tree-shaking verification.
* **Key Files / Modules Owned:**
  - `backend/src/main/java/com/finsight/security/...`
  - `backend/src/main/java/com/finsight/config/CorsOriginPolicy.java`
  - `frontend/src/middleware.ts`
* **Dependencies:** Streams 1, 3, and 6.
* **Deliverable / Milestone:** Production Security & Performance Sign-Off.

---

### Stream 8: Pilot User Onboarding & Operational Readiness
* **Primary Owner:** Fatima (Developer B)
* **Supporting Roles:** Codex (UX training aids)
* **Core Scope:**
  - Production tenant onboarding runbook for real Pakistani SMEs.
  - Bilingual user operating manuals and video walkthroughs (English & Urdu).
  - External API credential onboarding (Meta WhatsApp Cloud API business verification, OpenAI spend caps, SMTP server configuration).
  - Pilot feedback collection workflow and defect intake protocol.
* **Deliverable / Milestone:** SME Pilot Readiness Milestone.

---

### Stream 9: QA, Continuous Regression & Release Verification
* **Primary Owner:** Joint Ownership (Suleman & Codex)
* **Supporting Roles:** Fatima
* **Core Scope:**
  - Maintenance and continuous execution of the full regression suite:
    - 1,846 backend unit tests
    - 485 PostgreSQL integration tests
    - 482 frontend unit/contract tests
    - 86 historical browser regression checks
    - 26 real product browser assertions
    - 248 OCR service tests
  - End-to-end browser test execution on staging VPS across Chromium, Firefox, and WebKit.
* **Deliverable / Milestone:** Final Zero-Defect Production Release Candidate.

---

## 3. Exclusive File Ownership & Coordination Lock Matrix

To prevent merge conflicts and accidental architectural violations, the following critical shared files are subject to **Coordination Locks**:

| File Path | Domain Scope | Primary Lock Owner | Required Approvers Before Mutation | Coordination Policy |
|---|---|:---:|:---:|---|
| `backend/.../MonthlyRecordService.java` | Monthly health records & score generation | **Suleman** | Suleman & Fatima | No edits without prior RFC; must preserve 409 conflict detection for derived figures. |
| `backend/.../MonthlyProjectionService.java` | Transaction-driven monthly projections | **Suleman** | Suleman & Fatima | Must not alter trigger-driven calculation formulas without integration test review. |
| `backend/.../BusinessPermission.java` | System RBAC permissions | **Suleman** | Suleman | Append-only. Existing permissions must not be renamed or renumbered. |
| `backend/.../application.properties` | Spring Boot configuration | **Suleman** | Suleman & Fatima | Secret placeholders must use ConfigTree or environment variables. No hardcoded credentials. |
| `frontend/src/context/SessionProvider.tsx` | Client session & auth lifecycle | **Codex** | Codex & Suleman | Must preserve `153e94b` abort-signal fix and tenant-isolation refresh semantics. |
| `frontend/src/components/layout/IntegrationBoundary.tsx` | Tenant UI boundary & remount key | **Codex** | Codex & Suleman | Must preserve tenant-isolation remount while implementing DEF-11 unsaved work warning. |
| `docker-compose*.yml` | Container runtime specifications | **Suleman** | Suleman | Must preserve container GUID labeling, non-root user execution, and network isolation. |
| `scripts/demo-guards.psm1` | Demo environment safety guards | **Suleman** | Suleman | Must preserve container ID verification and prohibited public tunnel flags. |

---

## 4. Git Branching, PR & Release Mechanics

All next-phase engineering activities will strictly follow this standardized Git protocol:

```
                                  [origin/integration/finsight-production-candidate-v3]
                                            (Frozen Tree: 19547bf)
                                                      |
              +---------------------------------------+---------------------------------------+
              |                                       |                                       |
     fix/finsight-v3-def11                  fix/finsight-v3-def09                  infra/finsight-v3-docker-rehearsal
         (Codex / UI)                            (Codex / UI)                             (Suleman / DevOps)
              |                                       |                                       |
              +---------------------------------------+---------------------------------------+
                                                      |
                                            (PR Review & CI Gates)
                                                      |
                                         git merge --no-ff <branch>
                                                      |
                                                      v
                                 [Candidate v3.1 / Release Candidate v4]
```

### 4.1 Branch Creation Rules
1. Every new fix or feature branch must branch directly from `19547bf3dda9744d51e74c87a908e2e42577299c` (or the published documentation head `23e28c12bcd593be824d30cda0a2b79dcbe9d1f5`).
2. Naming convention:
   - Defect fixes: `fix/finsight-v3-<defect_id_or_topic>`
   - Infrastructure: `infra/finsight-v3-<topic>`
   - Documentation / Ops: `docs/finsight-v3-<topic>`
3. Work must be conducted in an isolated git worktree (`git worktree add ...`).

### 4.2 PR & Merge Rules
1. **No Direct Commits:** Direct commits to `main` or `integration/finsight-production-candidate-v3` are strictly forbidden.
2. **Clean `--no-ff` Merges:** All integrations must use `--no-ff` merge commits preserving both parent commit histories. No squashing, rebasing, or history rewriting.
3. **Automated CI Gates Required:**
   - `backend-unit` (1,846 tests)
   - `postgres` (485 integration tests across 4 shards)
   - `frontend` (482 unit/contract tests)
   - `browser-smoke` (26 real product assertions)
   - `historical-browser` (86 checks across 5 drivers)
   - `ocr-and-operations` (248 tests)
   - `current-source-secrets` (secret scan)
4. **Post-Merge Verification:** Inspect `git diff <previous_head> <merge_commit> --stat` to guarantee that only the files authorized for that workstream were modified.
