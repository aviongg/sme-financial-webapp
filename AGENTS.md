# FinSight Team & Assistant Working Rules (Phase 2)

**Version:** `1.1`  
**Date:** 14 October 2026  
**Baseline:** PR #3 integrated into `main` (`b51e27696cccc59b94aa9d9c9388ad0faa5b4176`)  
**Active Development Branch:** `dev/suleman`  
**Shared Checkout:** `C:\Users\PC\Projects\sme-financial-webapp`  

---

## 1. Project Purpose & Completed MVP Baseline

FinSight is an SME financial health and intelligence platform. The completed and integrated MVP (integrated via PR #3 into `main`, Flyway migrations V1–V17) provides:
*   **Identity & Security:** Cookie/JDBC-backed sessions, CSRF protection, two-factor authentication (MFA/TOTP) with parent/factor locking, password change/reset lifecycles, and append-only security audit events.
*   **Tenant Isolation & RBAC:** Strict server-side resolution of active business context (`ActiveBusinessContext`), role-based permissions (`OWNER`, `ACCOUNTANT`, `MANAGER`, `VIEWER`). No client-supplied tenant overrides.
*   **Monthly Records & Health Score:** Scoped monthly financial records, optional-input normalization, and the canonical `health-score-v1` engine (30% Cash Flow Stability, 25% Profitability, 20% Repayment, 15% Trend, 10% Compliance).
*   **Explainable Intelligence:** Structured score explanations, deterministic bilingual advice/recommendations (`NEW`, `VIEWED`, `DONE`, `DISMISSED`), and 6-month cash flow trend projection.
*   **Document Provenance:** Persistent uploads, asynchronous OCR, immutable original extractions, append-only review corrections, and atomic confirm-once semantics.
*   **Search, Delivery & Zakat:** Tenant-scoped search, scheduled WhatsApp notifications, and versioned Hanafi business Zakat calculations.

Phase 2 transitions FinSight from high-level monthly summaries to a transaction-based, event-driven financial operating platform while preserving the completed MVP baseline and historical data integrity.

---

## 2. Assistant Roles & Boundaries

| Assistant | Domain Ownership | Responsibilities |
| :--- | :--- | :--- |
| **Antigravity** | **Developer A Backend & Git Coordinator** | • Models, Flyway migrations, repositories, domain services, DTOs, REST endpoints, and backend tests for Dev A scope.<br>• Sole Git operator at agreed paused checkpoints.<br>• Backend authority for all financial math, balances, and aggregations. |
| **Codex** | **Developer A Frontend** | • Next.js App Router routes, bilingual UI (EN/UR/RTL), forms, tables, sheets, client API adapters, and frontend unit/contract tests.<br>• Displays backend financial figures; performs no independent financial math. |
| **Fatima** | **Developer B & AI Orchestration** | • Business configuration & module resolver (F02), invoices & bills (F04), project finance (F05), pre-order costing & pricing (F06), inventory valuation & COGS (F08), sample costs (F09), operations UI & vault extensions (F12B), dashboard customization (F13), AI provider integration, LLM copilot, and conversational WhatsApp. |

### Strict Non-Duplication Boundaries
1.  **Invoices & Bills:** Fatima owns invoice/bill document status and line totals; Dev A owns canonical obligations, outstanding balances, allocations, and payment posting.
2.  **Inventory & COGS:** Fatima owns inventory valuation and COGS calculations; Dev A maps approved COGS events into monthly record inputs.
3.  **Project Profitability:** Fatima owns project profitability; Dev A funding analysis consumes Fatima's authoritative project outputs and combines them with posted movements.
4.  **Commissions:** Dev A owns commission recognition and payment; Fatima consumes the recognized cost feed.
5.  **OCR Review vs. Ledger:** Fatima owns OCR review and UI navigation extensions; Dev A owns ledger-mode financial posting after user confirmation.
6.  **AI & LLM Tools:** AI orchestration belongs to Fatima. Dev A supplies authorized, deterministic, read-only tools via `FinancialQueryFacade` (C11). LLMs never compute or post authoritative financial figures directly.

---

## 3. Repository, Branch & Working Tree Rules

1.  **Single Shared Checkout:** Both Antigravity and Codex operate in the same existing local checkout: `C:\Users\PC\Projects\sme-financial-webapp` on branch `dev/suleman`.
2.  **No Branch Switching or Clones:** Do not create branches, worktrees, clones, or alternative project directories. Do not switch branches.
3.  **Preserve Untracked Work & Build Outputs:** The untracked file `backend/.cp.txt` and all other untracked or modified files from teammates must be preserved untouched.
4.  **Exclusive File Ownership:** An assistant may only edit files within its permitted task paths. No cross-owner edits are allowed without explicit release and reassignment.
5.  **Shared Files & Coordination:** Shared files (e.g., `MonthlyRecordService.java`, `BusinessPermission.java`, `application.properties`, navigation layouts) require an exclusive editing window. Coordinate directly with Fatima whenever her domain or interface is affected.
6.  **Git Checkpoint Coordination:**
    *   Antigravity is the sole Git operator. Codex must be explicitly `PAUSED` with an exact path list before any Git mutation.
    *   Antigravity inspects `git status` and diffs, stages explicit accepted paths (`git add <file>`), and commits coherent slices.
    *   No blanket `git add .`.
    *   No automatic push, pull, rebase, merge, reset, clean, or stash without separate explicit user authorization.

---

## 4. Financial & Architecture Discipline

1.  **Backend Calculation Authority:** All financial balances, outstanding amounts, funding gaps, forecasts, FX gains/losses, and scores originate from the backend. The UI formats and validates inputs, but never re-computes financial authority.
2.  **No Live Mock Fallback:** Live mode (`NEXT_PUBLIC_DATA_MODE=live`) must surface real backend API failures. No synthetic mock fallback in live mode. Explicit demo mode (`NEXT_PUBLIC_DATA_MODE=demo`) remains isolated.
3.  **Preserve Existing Financial Model:** Historical monthly records, `health-score-v1` weights (30/25/20/15/10), normalization formulas, and deterministic advice engines must not be redesigned or broken.
4.  **Currency Scale & Precision:** Use `BigDecimal` with currency-specific scale based on metadata (e.g. JPY scale 0, BHD scale 3, PKR/USD scale 2) and separate FX rate precision (scale 6).
5.  **Database & Migration Discipline:**
    *   Authoritative migrations V1–V17 must never be modified or rewritten.
    *   New migrations begin at V18+ and must be **globally reserved** in coordination with Fatima. No assumption of V18 availability without cross-team confirmation.
    *   Runtime role (`finsight_app`) remains restricted to DML only. Migrations run under the `finsight_migrator` role.
6.  **Security Controls:**
    *   Preserve parent-user locking on MFA enrollment and pending session lifecycle validation.
    *   Preserve AES-256-GCM encryption with mandatory production ConfigTree secret `crypto_key_k1`.
    *   Preserve immutable document extractions and append-only correction triggers.

---

## 5. Verification & Evidence Rules

1.  **No False Claims of Execution:** Historical recorded test evidence (such as 754 Surefire tests from 8 October) must not be presented as a fresh local run.
2.  **Distinguish Verification Tiers:**
    *   *Unit / Regression Suites:* Fast local execution without external services.
    *   *PostgreSQL Integration Tests:* Requires disposable PostgreSQL instance (embedded or container). Exclusions (`DatabaseRolePrivilegesPostgreSqlIT`, `PostgreSqlTlsConnectionIT`) are conditional on unavailable production fixtures and must be explicitly reported in evidence.
    *   *Production Gates:* Container image builds, PostgreSQL 17 TLS/HBA, staging recovery drills, persistent volume recreation, real browser journeys, and external provider deliveries (Google Vision, SMTP, WhatsApp) are separate gates and must be recorded as pending until run on capable infrastructure.
3.  **State Tracking:** Consult `docs/PHASE2_IMPLEMENTATION_STATE.md` before starting work to confirm active task assignments, locked files, and migration reservations.
