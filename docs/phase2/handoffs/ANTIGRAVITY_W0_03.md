# Antigravity Task Handoff: A-W0-03

**Task ID:** `A-W0-03`  
**Task Title:** Correct the accounts-read contract and record Codex’s review findings  
**Author:** Antigravity (Developer A Backend & Checkpoint Git Coordinator)  
**Date:** 14 October 2026  
**Status:** `COMPLETED (DOCUMENTATION ONLY)`  

---

## 1. Exact Changed Files & Revisions

1.  [`AGENTS.md`](file:///c:/Users/PC/Projects/sme-financial-webapp/AGENTS.md) (v1.1) — Aligned currency-specific scale discipline, conditional test exclusions, and Fatima coordination rules.
2.  [`frontend/AGENTS.md`](file:///c:/Users/PC/Projects/sme-financial-webapp/frontend/AGENTS.md) (v1.1) — Added explicit rules preserving request cancellation/abort on business switching, prohibiting automatic mutation replay, and mandating coordination with Fatima on shared shell changes.
3.  [`backend/AGENTS.md`](file:///c:/Users/PC/Projects/sme-financial-webapp/backend/AGENTS.md) (v1.1) — Clarified currency-specific scale (not blanket scale 2), documented conditional test exclusions, and established non-disclosing 404 behavior.
4.  [`docs/PHASE2_IMPLEMENTATION_STATE.md`](file:///c:/Users/PC/Projects/sme-financial-webapp/docs/PHASE2_IMPLEMENTATION_STATE.md) (v1.1) — Updated contract register to v1.1, recorded Codex review verdict (`READY AFTER LISTED CORRECTIONS`), and narrowed task `A-W1-01` to accounts-only read slice.
5.  [`docs/phase2/contracts/ACCOUNTS_API_DRAFT.md`](file:///c:/Users/PC/Projects/sme-financial-webapp/docs/phase2/contracts/ACCOUNTS_API_DRAFT.md) (v1.1) — Bounded read contract, resolved Codex findings A1–A5, defined balance semantics (`observationAt` vs `effectiveCutoffDate`), aligned error bodies with `apiClient`, specified canonical decimal string syntax and supported currencies.
6.  [`docs/phase2/contracts/C01_POSTING_DRAFT.md`](file:///c:/Users/PC/Projects/sme-financial-webapp/docs/phase2/contracts/C01_POSTING_DRAFT.md) (v1.1) — Addressed mutation findings M1–M3, M5–M6, M10: removed opening balance double-counting, separated source event ID from content hash, defined durable idempotency table and replay auth, documented forward cash carry-forward, rejected derived negative figures, and split fixtures into independent scenarios.
7.  [`docs/phase2/contracts/C02_MONTHLY_PROJECTION_DRAFT.md`](file:///c:/Users/PC/Projects/sme-financial-webapp/docs/phase2/contracts/C02_MONTHLY_PROJECTION_DRAFT.md) (v1.1) — Addressed mutation findings M4–M9: detailed opening date vs. cutover date, multi-month cash propagation, field authority matrix and PATCH semantics for declarations, and OCR posting authority.
8.  [`docs/phase2/adr/ADR_001_LEDGER_AUTHORITY_AND_OPENING_BALANCES_DRAFT.md`](file:///c:/Users/PC/Projects/sme-financial-webapp/docs/phase2/adr/ADR_001_LEDGER_AUTHORITY_AND_OPENING_BALANCES_DRAFT.md) (v1.1) — Updated ADR with single opening balance authority, cash balance forward propagation, and atomic rejection of derived negative numbers.
9.  [`docs/phase2/handoffs/ANTIGRAVITY_W0_03.md`](file:///c:/Users/PC/Projects/sme-financial-webapp/docs/phase2/handoffs/ANTIGRAVITY_W0_03.md) — This handoff document.

*Note:* [`docs/phase2/handoffs/ANTIGRAVITY_W0_02.md`](file:///c:/Users/PC/Projects/sme-financial-webapp/docs/phase2/handoffs/ANTIGRAVITY_W0_02.md) was preserved untouched as historical evidence.

---

## 2. Codex Review Findings Disposition (A1–A5)

| Finding | Topic | Resolution in `ACCOUNTS_API_DRAFT.md` (v1.1) |
| :--- | :--- | :--- |
| **A1** | **Balance Meaning & Cutoff** | Formally defined: `currentBalance` includes `openingBalance` plus posted movements through `effectiveCutoffDate` (business date in PKT, UTC+05:00). Future-dated events excluded. Added separate `observationAt` (wall-clock UTC query time). If balance calculation is unavailable or reconciling, returns `balanceStatus: "UNAVAILABLE"` and `currentBalance: null` (never silently 0.00). |
| **A2** | **Schemas & Error Bodies** | Provided normative required/nullable field matrix. UUID validation rejects malformed IDs with 400. Documented pagination defaults (`page=0`, `size=50`, max 100). Defined empty vs. out-of-range page responses. Aligned error response JSON (`status`, `error`, `message`, `errors`) exactly with `frontend/src/lib/api/client.ts` decoder. Decoupled `combinedBalances` and `recentMovementCount` from read schema. |
| **A3** | **Module Capability Behavior** | Did not hard-code unapproved C03 module key. Documented bounded read slice where direct navigation to `/finance/accounts` works during dev. If module disabled, returns HTTP 403 with `error: "module_disabled"`. Distinguishes disabled module from empty accounts list (`items: []`). |
| **A4** | **Read Grants & Tenant Isolation** | Recommended explicit matrix: `OWNER`, `ACCOUNTANT`, `MANAGER`, and `VIEWER` permitted read access; `SALESPERSON` explicitly DENIED. Proposes explicit `FINANCIAL_ACCOUNT_READ` permission. Non-existent IDs or accounts belonging to other tenants return non-disclosing HTTP 404 Not Found. |
| **A5** | **Decimal Reads & Currencies** | Defined canonical decimal string syntax `^-?(0|[1-9]\d*)(\.\d+)?$`. Normalizes zero to `"0.00"` (never `"-0.00"`). Emits explicit `currencyDecimals` (0 for JPY, 2 for PKR/USD/EUR, 3 for BHD/KWD/OMR). Max 12 integer digits. Provided exact string reference fixtures for Codex UI unit tests. Separated FX rate precision (scale 6). |

---

## 3. Codex Mutation Findings Disposition (M1–M10)

| Finding | Topic | Disposition in C01 / C02 / ADR-001 (v1.1) |
| :--- | :--- | :--- |
| **M1** | **Opening Authority** | Removed double-counting formula. `opening_balance` is stored once on `financial_accounts`. Cached balance = opening balance + sum of subsequent movements. |
| **M2** | **Source Identity** | Separated economic event identity (unique event UUID) from document content fingerprints (`source_fingerprint`). Legitimate identical transactions are fully supported. |
| **M3** | **Idempotency Standards** | Standardized conflict error code `ERROR_IDEMPOTENCY_CONFLICT` (HTTP 409). Specified durable uniqueness table (`financial_idempotency_records`) and re-authorization on replay. |
| **M4** | **Date Semantics** | Clarified `openingDate` (effective date of account starting capital) vs. `ledger_cutover_month` (calendar month where ledger authority starts). Opening date must be $\le$ cutover month start. |
| **M5** | **Forward Propagation** | Backdated cash changes propagate closing cash balances forward across all subsequent months ($M+1, M+2, \dots$), separate from the 6-month health score recalculation window. |
| **M6** | **Negative Compatibility** | Unresolved policy formalized: Reversals or refunds causing derived monthly totals to drop below 0.00 trigger an atomic domain error (`ERROR_DERIVED_NEGATIVE_UNSUPPORTED`), never silent clamping to zero. |
| **M7** | **Monthly Mapping** | Explicitly defined inclusion of all accounts active during month $M$, and month-end foreign-to-base conversion using locked closing rates. |
| **M8** | **Declarations Maintenance** | Created field authority matrix distinguishing ledger-derived fields from user declarations. Optional fields updated via `PATCH /api/records/monthly/{month}/declarations`. Compliance remains on `BusinessProfile`. |
| **M9** | **OCR & Recognition** | Confirmed unconfirmed OCR drafts never post actuals. Document confirmation in ledger mode generates a posted transaction, which then derives into `MonthlyRecord`. |
| **M10** | **DTO Consistency** | Unified enum naming (`INCREASE`/`DECREASE`, `ACTIVE`/`INACTIVE`). Supported non-cash recognition without invented cash movements. Replaced "Approved Rule" with "Proposed Policy". |

---

## 4. Migration Status & Backend Readiness

*   **Flyway V18 remains UNRESERVED.** No migrations have been created, reserved, or run.
*   **Backend Endpoints DO NOT EXIST YET.** No Java controllers, entities, services, or repository files for financial accounts have been created.
*   The codebase remains strictly at the verified MVP baseline (`b51e276`).

---

## 5. Decisions Still Blocking Accounts Read Freeze

The accounts read contract (`ACCOUNTS_API_DRAFT.md` v1.1) is now technically self-contained for the frontend read slice. The remaining cross-team decisions needed before coding begins are:

1.  **Read Permission Matrix Confirmation (A4):** Confirm whether `VIEWER` and `MANAGER` roles should have read access to financial account balances in Phase 2. *(Recommendation: Allow read for transparency, disallow mutation)*.
2.  **Capability Gating / Module Key (A3 with Fatima):** Confirm whether the read endpoints should be gated by a capability check immediately or deferred until Fatima's C03 Module Resolver is ready. *(Recommendation: Implement endpoint without hardcoded module check in slice 1, add module filter once C03 is frozen)*.
3.  **Migration Number Reservation:** Agree with Fatima on reserving Flyway `V18` for `financial_accounts` schema.

---

## 6. What is Ready for Codex to Re-Review

Codex is invited to review:
*   [`docs/phase2/contracts/ACCOUNTS_API_DRAFT.md`](file:///c:/Users/PC/Projects/sme-financial-webapp/docs/phase2/contracts/ACCOUNTS_API_DRAFT.md) (v1.1) — Specifically the bounded read schema in Section 2, error bodies in Section 3 (A2), and exact decimal test fixtures in Section 3 (A5).
*   [`frontend/AGENTS.md`](file:///c:/Users/PC/Projects/sme-financial-webapp/frontend/AGENTS.md) (v1.1) — For updated request cancellation and replay rules.

---

## 7. Confirmation

*   **No application code, migrations, databases, services, or Git state were changed.**
*   **The working tree is clean except for the authorized documentation files and untracked `backend/.cp.txt`.**
*   **Antigravity is PAUSED and awaiting next instructions.**
