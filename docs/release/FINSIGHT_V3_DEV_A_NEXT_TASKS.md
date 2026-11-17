# FinSight Candidate v3 — Developer A Next Implementation Sequence & Task Plan

**Document ID:** `FINSIGHT-V3-DEV-A-NEXT-TASKS-01`  
**Date:** November 17, 2026  
**Lead Engineer:** Suleman / Antigravity (Backend Architect & Git Coordinator for Developer A)  
**Target Codebase Baseline:** Candidate v3 Frozen Tree (`19547bf3dda9744d51e74c87a908e2e42577299c`)  
**Branching Convention:** `fix/finsight-v3-<topic>` branched strictly from `19547bf3dda9744d51e74c87a908e2e42577299c`  

---

## 1. Prioritized Implementation Sequence

Tasks are ordered by **financial integrity and dependency risk**, rather than convenience:

```
[Phase 1: Session & Defect Intercept]
       |
       +---> TASK 1: DEF-11 Tenant Session Boundary Intercept Contract (Joint with Codex)
       |
[Phase 2: Operational & Concurrency Verification]
       |
       +---> TASK 2: Docker Demo Runner Live Rehearsal on Physical Host (Stream 6)
       |
       +---> TASK 3: V52 Database Trigger High-Concurrency Batch Posting Profile (Stream 5)
       |
[Phase 3: Production Security & Infrastructure]
       |
       +---> TASK 4: PostgreSQL 17 Production Role Privileges & TLS/HBA Gate (Stream 6)
       |
       +---> TASK 5: Live External Provider Activation & Credentials Vault Setup (Stream 8)
```

---

## 2. Recommended First Developer A Implementation Task

### Task Profile: FINSIGHT-V3-TASK-01-DEF11-SESSION-INTERCEPT-CONTRACT

| Field | Detail |
|---|---|
| **Task ID** | `FINSIGHT-V3-TASK-01-DEF11-SESSION-INTERCEPT-CONTRACT` |
| **Descriptive Name** | Cross-Tab Active Business Switch Session Intercept & Unsaved Work Protection |
| **Workstream** | Stream 3 (UI/UX Improvements) & Stream 7 (Security Hardening) |
| **Primary Owner** | **Codex** (Frontend Implementation) & **Suleman** (Session Security & Contract Authority) |
| **Target Topic Branch** | `fix/finsight-v3-def11` |
| **Base Commit** | `19547bf3dda9744d51e74c87a908e2e42577299c` |
| **Complexity Estimate** | Moderate (Frontend React state management + Session contract alignment) |

#### 1. Problem Being Solved
When a user has an unsubmitted form open in Tab 1 (e.g. `/invoices/new`, `/bills/new`, or `/records/monthly/financing`) and switches the active business context in Tab 2 to a different organization, Tab 1 detects the context change on window focus via `SessionProvider.tsx`. In `IntegrationBoundary.tsx`, the component subtree is keyed on `session.business.businessId`:
```tsx
<div key={`business-boundary-${session.business?.businessId}`}>
  {children}
</div>
```
The key change causes React to unmount and remount the subtree, silently clearing all user form state without an alert or dirty-form prompt. If Tab 1 was on a detail route (`/invoices/[id]`), the page refreshes and renders a 404 error (`"This document was not found in the active business."`).

#### 2. Scope & Permitted Files
- `frontend/src/context/SessionProvider.tsx` (Detection of active business change with dirty form event hooks)
- `frontend/src/components/layout/IntegrationBoundary.tsx` (Unsaved form modal intercept before remounting)
- `frontend/src/context/FormDirtyContext.tsx` (New lightweight hook for tracking unsubmitted user drafts)
- `frontend/src/components/ui/UnsavedBusinessSwitchModal.tsx` (Bilingual EN/UR prompt dialog)
- `frontend/tests/def11-session-switch.contract.test.ts` (Automated browser contract test)

#### 3. Files Requiring Coordinated Approval
- `frontend/src/context/SessionProvider.tsx` $\rightarrow$ Primary owner: Codex. Requires Suleman approval to guarantee that the `153e94b` authentication abort signal fix is preserved and server-side tenant isolation is never weakened.
- `frontend/src/components/layout/IntegrationBoundary.tsx` $\rightarrow$ Primary owner: Codex. Requires Suleman approval to ensure no cross-tenant mutation can be dispatched.

#### 4. Exact Acceptance Criteria
1. **Security Invariant Preserved:** No API request for Organization A may be dispatched while the server session is resolved to Organization B.
2. **Modal Warning Triggered:** When window focus shifts to Tab 1, if an unsubmitted form has dirty state AND the active business changed in another tab:
   - Component unmounting is paused.
   - A modal prompt appears:
     > **English:** *"Active Organization Changed. You switched to [Org B] in another window. You have unsaved changes in [Org A]. Would you like to switch back to [Org A] to save your work, or discard these changes and load [Org B]?"*  
     > **Urdu:** *"فعال ادارہ تبدیل ہو گیا ہے۔ آپ نے دوسری ونڈو میں [Org B] پر سوئچ کیا ہے۔ آپ کے پاس [Org A] میں غیر محفوظ کام موجود ہے۔"*
3. **Switch Back Action:** Choosing *"Switch back to [Org A]"* invokes `POST /api/auth/select-business` for Organization A, restoring session alignment without losing form inputs.
4. **Discard Action:** Choosing *"Discard and switch to [Org B]"* clears the form dirty flag and allows `IntegrationBoundary` to remount for Organization B.
5. **SessionStorage Draft Auto-Save (Enhancement):** Form inputs are auto-persisted in `sessionStorage` keyed by `(businessId, formRoute)` so accidental reloads can recover unsubmitted drafts.

#### 5. Test Requirements
- **Unit / Component Test:** `pnpm test SessionProvider.test.tsx` verifying dirty state interception.
- **Contract Test:** Playwright browser test simulating two browser contexts with distinct active businesses, asserting modal appearance and draft preservation.
- **Regression Suite:** All 482 frontend tests and 26 real product browser assertions must remain green.

#### 6. Rollback Considerations
- Work on topic branch `fix/finsight-v3-def11`. If any regression occurs in auth hydration, discard branch; frozen Candidate v3 remains untouched.

#### 7. Dependencies
- No backend code changes required. Purely a frontend/session coordination task between Codex and Suleman.

---

## 3. Subsequent Developer A Implementation Tasks

### Task 2: FINSIGHT-V3-TASK-02-DOCKER-DEMO-RUNNER-REHEARSAL
* **Owner:** Suleman (DevOps Lead)
* **Scope:** `scripts/demo-guards.psm1`, `scripts/init-demo.ps1`, `scripts/start-demo.ps1`, `scripts/stop-demo.ps1`, `docker-compose.demo.yml`.
* **Topic Branch:** `infra/finsight-v3-docker-rehearsal`
* **Objective:** Execute full physical Docker Desktop rehearsal on a dedicated Windows runner:
  1. `.\scripts\init-demo.ps1 -CreateFreshDisposableDemo` (Proves container GUID labeling and manifest creation).
  2. `.\scripts\start-demo.ps1` (Proves HTTP readiness on ports 5433, 8080, 3000).
  3. `node scripts/verify-full-demo-lifecycle.mjs` (Executes live synthetic user login and financial sanity checks).
  4. `.\scripts\stop-demo.ps1` (Proves container shutdown by recorded ID without touching foreign containers).
* **Acceptance Criteria:** Real execution log recorded and saved in `docs/release/candidate-v3-docker-rehearsal/`.

### Task 3: FINSIGHT-V3-TASK-03-V52-PROJECTION-CONCURRENCY-PROFILE
* **Owner:** Suleman (jointly with Fatima)
* **Scope:** `backend/src/main/resources/db/migration/V52__transaction_driven_monthly_projection.sql`, `MonthlyProjectionService.java`.
* **Topic Branch:** `fix/finsight-v3-projection-concurrency`
* **Objective:** Load-test `trg_*_monthly_projection_dirty` under 50 concurrent transactions per second across 5 businesses. Verify that `pg_advisory_xact_lock(hashtext('monthly-projection:' + businessId))` avoids deadlocks and that `dirty_seq` accurately triggers coalesced recalculations.
* **Acceptance Criteria:** Zero deadlock exceptions; recalculation duration $< 250$ms per business.

### Task 4: FINSIGHT-V3-TASK-04-POSTGRESQL-PRODUCTION-GRANTS-VALIDATION
* **Owner:** Suleman (Database Lead)
* **Scope:** PostgreSQL 17 TLS connection script, Flyway migrator user vs app runtime user privileges.
* **Topic Branch:** `infra/finsight-v3-db-privileges`
* **Objective:** Run `DatabaseRolePrivilegesPostgreSqlIT` and `PostgreSqlTlsConnectionIT` against a physical PostgreSQL 17 instance with strict TLS and HBA role separation (`finsight_migrator` owning DDL; `finsight_app` restricted to DML-only).
* **Acceptance Criteria:** 100% pass on physical enterprise PostgreSQL cluster.

---

## 4. Branching, PR & Release Mechanics Checklist

1. [ ] Branch from exact frozen SHA:
   ```powershell
   git checkout -b fix/finsight-v3-def11 19547bf3dda9744d51e74c87a908e2e42577299c
   ```
2. [ ] Conduct all work in isolated worktree or topic branch.
3. [ ] Verify that no protected files outside permitted task scope are touched.
4. [ ] Run full CI gate locally:
   - Backend unit (1,846 tests)
   - PostgreSQL integration (485 tests)
   - Frontend unit (482 tests)
   - Real product browser (26 checks)
5. [ ] Integrate strictly via `--no-ff` PR:
   ```powershell
   git merge --no-ff fix/finsight-v3-def11
   ```
6. [ ] Zero force-push to `integration/finsight-production-candidate-v3` or `main`.
