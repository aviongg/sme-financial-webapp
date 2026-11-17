# Zarwia Candidate v3 — Engineering Alignment & Technical Review Agenda

**Document Version:** `1.0.0`  
**Date:** November 17, 2026  
**Organizer / Chair:** Antigravity (Git Integration Coordinator & Backend Architecture Lead for Developer A)  
**Participants:**  
- **Suleman (Developer A):** Backend Lead & Financial Systems Authority  
- **Fatima (Developer B):** Commercial Lifecycle, Modules & AI Orchestration Lead  
- **Codex:** Frontend Lead (Next.js, UX, Bilingual & Accessibility)  
- **Architecture / Release Management:** Final Production Engineering  

---

## 1. Meeting Purpose, Objectives & Ground Rules

### 1.1 Objective
Formalize the engineering alignment between Developer A, Developer B, and Codex on **Zarwia Candidate v3** (`origin/integration/finsight-production-candidate-v3` at frozen tree `19547bf3dda9744d51e74c87a908e2e42577299c`). Review all cross-domain contracts, verify that financial calculation authority is respected, resolve pending defect dispositions (specifically DEF-11), review physical infrastructure prerequisites, and establish clear ownership for the 9 next-phase workstreams.

### 1.2 Ground Rules
1. **Financial Authority Invariant:** All financial balances, health scores, funding gaps, FX conversions, and ledger states originate strictly from Developer A's backend services. Frontend and AI tools remain read-only consumers.
2. **Evidence-Based Acceptance:** Historical test claims must not be substituted for fresh execution. Automated test passes are distinct from pending physical infrastructure gates.
3. **No Uncoordinated Mutations:** Code modifications must not occur during or immediately following this meeting without strict workstream assignments and coordination locks.

---

## 2. Review Agenda & Time Allocation (Total: 2 Hours 45 Mins)

| Item | Topic | Lead Presenter | Duration |
|:---:|---|:---:|:---:|
| **1** | Git Provenance, Worktree Truth & Candidate v3 Baseline | Suleman (Dev A) | 15 mins |
| **2** | Core Financial Architecture & V52 Projection Harmonization | Suleman (Dev A) | 25 mins |
| **3** | Commercial Lifecycle & Domain Boundaries (F04, F06, F08) | Fatima (Dev B) | 25 mins |
| **4** | DEF-11 Product & UX Architecture Resolution | Codex / Suleman | 25 mins |
| **5** | Demo Tooling, Docker Containment & Environment Safeguards | Fatima / Suleman | 20 mins |
| **6** | Provider Disclosure & Simulation Realism (DEF-08) | Fatima / Codex | 15 mins |
| **7** | Production Infrastructure, Ingress & Security Gates | Suleman (Dev A) | 20 mins |
| **8** | Next-Phase 9-Workstream Division & Review Sign-Off | All Participants | 20 mins |

---

## 3. Detailed Item Breakdowns & Discussion Guides

---

### Item 1: Git Provenance, Worktree Truth & Candidate v3 Baseline (15 Mins)
* **Context:** Verification that Candidate v3 (`origin/integration/finsight-production-candidate-v3`) accurately incorporates all work from `dev/suleman` (`8c6e247`) and `demo/local-public-deployment` (`153e94b`).
* **Technical Evidence:**
  - `git merge-base --is-ancestor 8c6e247 ...` $\rightarrow$ `0` (Confirmed).
  - `git merge-base --is-ancestor 153e94b ...` $\rightarrow$ `0` (Confirmed).
  - `git diff 153e94b origin/integration/finsight-production-candidate-v3 -- frontend/src/context/SessionProvider.tsx` $\rightarrow$ Empty (Byte-identical).
  - Zero missing commits between Developer A branches and Candidate v3.
* **Key Questions for the Meeting:**
  1. Does any team member have uncommitted or unpushed work outside of these verified hashes?
  2. Are all participants aligned that `19547bf3dda9744d51e74c87a908e2e42577299c` is the authoritative frozen tree for v3 review?
* **Proposed Resolution:** Formally ratify `19547bf` as the engineering frozen baseline for all further phase-closure work.

---

### Item 2: Core Financial Architecture & V52 Projection Harmonization (25 Mins)
* **Context:** Review the evolution from C02 manual monthly record bridge cutover to V52 transaction-driven projection (`monthly-projection-v1`).
* **Technical Invariants:**
  - Database triggers (`trg_*_monthly_projection_dirty`) flag dirty months upon daily activity.
  - `MonthlyProjectionService.refreshProjections()` derives monthly summary inputs automatically.
  - Attempting to pass manual overrides (`manualCogs`, `manualInventoryValue`) via `applyLedgerProjection()` throws `409 monthly_figures_derived` when projections are active.
  - Supplemental financing inputs (`monthly_financing_inputs`) remain segregated for manual interest/principal liabilities until full loan schedule automation is active.
* **Key Questions for the Meeting:**
  1. Are database trigger executions on high-volume account movements presenting any database lock contention risks?
  2. Does Developer B's inventory COGS calculation perfectly align with Developer A's monthly ledger COGS summary?
* **Proposed Resolution:** Confirm that C02 manual cutover is formally superseded by V52 automatic projection and that 409 conflict protection is working as intended.

---

### Item 3: Commercial Lifecycle & Domain Boundaries (F04, F06, F08) (25 Mins)
* **Context:** Non-duplication boundary check between Developer B's commercial documents (invoices, bills, estimates, stock movements) and Developer A's financial obligations and ledger postings.
* **Technical Boundaries:**
  - F04 invoices/bills define commercial document metadata and line items; Developer A's `FinancialObligationService` owns balances, payment allocations, and ledger journal posting.
  - Inventory stock movements (F08) calculate perpetual valuation; Developer A consumes COGS postings in the monthly ledger.
  - OCR document confirmation is evidence-only; it does not silently generate accounting movements without user-confirmed posting.
* **Key Questions for the Meeting:**
  1. Are there any endpoints where commercial document state transitions mutate financial balances without calling Developer A's ledger service?
  2. Does the invoice/bill status transition cleanly handle partial payments and advance allocations?
* **Proposed Resolution:** Reaffirm the strict separation of concerns and confirm zero boundary violations.

---

### Item 4: DEF-11 Product & UX Architecture Resolution (25 Mins)
* **Context:** When a user switches the active business in Tab 2, Tab 1 detects the context change on window focus and unmounts the component tree (`IntegrationBoundary.tsx`), silently wiping unsaved form inputs on `/invoices/new` or showing a 404 on `/invoices/[id]`.
* **Architectural Trade-Off:**
  - *Security Mandate:* Server-side tenant isolation must never allow Tab 1 to submit Organization A's draft under Organization B's active session. Remounting is correct and required.
  - *UX Problem:* The sudden loss of user input without warning is unacceptable for production usability.
* **Proposed Technical Options:**
  - **Option A (Modal Intercept):** On window focus, if `session.business.businessId` changes AND form is dirty, pause re-render and display a modal: *"You switched organizations in another tab. Switch back to Organization A to continue editing, or discard and switch to Organization B?"*
  - **Option B (Tab-Scoped Business Locking):** Bind the active business to the browser tab session via HTTP request headers instead of cookie session (requires extensive architectural changes to multi-tenant resolution).
  - **Option C (Draft Storage Auto-Save):** Auto-save form inputs to `sessionStorage` keyed by `(businessId, formRoute)`. If the user returns to Organization A, the draft is seamlessly restored.
* **Key Questions for the Meeting:**
  1. Does Codex recommend Option A combined with Option C for immediate implementation?
  2. Does this affect any mobile viewport workflows?
* **Proposed Resolution:** Adopt Option A (Modal Intercept) as the immediate UX fix for DEF-11, with Option C (Draft persistence) scheduled for Stream 3.

---

### Item 5: Demo Tooling, Docker Containment & Environment Safeguards (20 Mins)
* **Context:** Candidate v3 hardened the demo management scripts (`scripts/demo-guards.psm1`, `init-demo.ps1`, `start-demo.ps1`, `stop-demo.ps1`, `backup-demo.ps1`, `restore-demo.ps1`) with container GUID tracking and ownership verification.
* **Verified vs Open Findings:**
  - *Verified:* PowerShell unit and contract fixtures confirm that unrelated or foreign containers are never touched (exit 3/4).
  - *Open Constraint:* The physical Docker rehearsal (init $\rightarrow$ start $\rightarrow$ verify $\rightarrow$ stop) has **not been run on real Docker infrastructure** because the current development host lacks Docker Desktop.
* **Key Questions for the Meeting:**
  1. Which physical runner will be designated to execute the live Docker rehearsal?
  2. Who will witness and record the rehearsal evidence?
* **Proposed Resolution:** Assign the physical Docker rehearsal to DevOps/Developer A on a designated staging runner equipped with Docker Desktop.

---

### Item 6: Provider Disclosure & Simulation Realism (DEF-08) (15 Mins)
* **Context:** DEF-08 addressed user transparency when external third-party providers (OpenAI, Meta WhatsApp, Google Vision OCR, SMTP) are inactive or simulated.
* **Candidate v3 State:**
  - WhatsApp delivery history clearly states "Simulated: a test provider is active. No WhatsApp messages are transmitted..." when mock provider is active.
  - Copilot responses disclose when an answer is generated via evidence-based fallback rather than an external LLM.
  - Read-only capability endpoint (`GET /api/whatsapp/delivery-capability`) requires `FINANCIAL_DATA_READ`.
* **Key Questions for the Meeting:**
  1. Are all UI disclosures bilingual (English and Urdu)?
  2. Is there any remaining screen where mock data could be mistaken for live external transmission?
* **Proposed Resolution:** Confirm DEF-08 is satisfactorily resolved for candidate freeze.

---

### Item 7: Production Infrastructure, Ingress & Security Gates (20 Mins)
* **Context:** Review the 8 outstanding acceptance items listed in Section 8 of `CANDIDATE_V3_FINAL_ENGINEERING_HANDOFF.md` that remain unverified.
* **Scope of Outstanding Items:**
  1. PostgreSQL 17 with TLS/HBA and production database role privileges.
  2. Production Docker container image builds and vulnerability scanning.
  3. VPS deployment, TLS termination, and ConfigTree secret mounting (`crypto_key_k1`).
  4. Live Meta WhatsApp, OpenAI, and Google Vision API credential activation.
  5. Cold database backup and point-in-time recovery drill (RPO/RTO).
* **Key Questions for the Meeting:**
  1. What is the target timeline for staging VPS deployment?
  2. Who will manage external API credential acquisition?
* **Proposed Resolution:** Schedule these infrastructure gates under Stream 6 (DevOps & Infrastructure).

---

### Item 8: Next-Phase 9-Workstream Division & Review Sign-Off (20 Mins)
* **Context:** Review the proposed workstream allocation outlined in `ZARWIA_V3_NEXT_PHASE_OWNERSHIP_PROPOSAL.md`.
* **Review of Streams:**
  1. Financial & Backend Corrections (Suleman)
  2. Commercial & Operational Corrections (Fatima)
  3. UI/UX Improvements & DEF-11 (Codex)
  4. Advanced Features (Fatima)
  5. Shared Integration Contracts (Joint: Suleman & Fatima)
  6. Infrastructure & Deployment (Suleman)
  7. Security & Performance (Suleman & Codex)
  8. Pilot User Preparation (Fatima)
  9. QA & Regression (Suleman & Codex)
* **Formal Decision Matrix:**
  - Roll-call vote to approve Candidate v3 freeze as the baseline for next-phase work.
  - Execution of sign-off records.

---

## 4. Decision Framework & Pre-Meeting Checklist

### Pre-Meeting Checklist for Attendees
- [ ] **Suleman:** Verify that `ZARWIA_V3_DEVELOPER_A_BACKEND_RECONCILIATION.md` covers all backend domain concerns.
- [ ] **Fatima:** Review commercial document status transitions and V52 projection triggers.
- [ ] **Codex:** Review `IntegrationBoundary.tsx` and prepare technical proposal for DEF-11 dirty form intercept.
- [ ] **All:** Ensure no uncommitted local changes exist that conflict with the frozen tree `19547bf`.
