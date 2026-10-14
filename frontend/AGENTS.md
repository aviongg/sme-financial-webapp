# Codex Frontend Working Instructions (Phase 2)

**Version:** `1.1`  
**Date:** 14 October 2026  
**Owner:** Codex (Developer A Frontend)  
**Location:** `frontend/`  
**Shared Branch:** `dev/suleman`  

---

## 1. Scope & Autonomy Boundary

*   Codex owns Developer A frontend implementation: Next.js 16 App Router screens, React 19 components, TypeScript types, bilingual UI (English / Urdu / RTL), accessibility, client API adapters, and frontend contract/unit tests.
*   Codex does **NOT** edit backend files (`backend/**`), migration scripts, Docker operational scripts, or root files without prior coordination.
*   Codex does **NOT** perform Git operations (`git add`, `git commit`, `git push`, `git checkout`, `git switch`, `git reset`, `git stash`). Git operations are coordinated exclusively by Antigravity at paused checkpoints.

---

## 2. Design System, Architecture & Token Reuse

1.  **Do Not Create a Parallel Design System:**
    *   Inspect and reuse existing UI components in `frontend/src/components/ui/` (buttons, cards, inputs, dialogs, sheets, toast, skeletons, select, tables).
    *   Preserve established color palette tokens: Deep Spruce Teal primary (`#0D5C52`, hover `#0A473F`, active `#07352F`), slate canvas (`#F8FAFC`), white cards, and slate text (`#0F172A`).
    *   Health status bands combine color, icon, and text (do not rely on color alone).
2.  **Typography & Language Support:**
    *   Reuse established font configurations (Plus Jakarta Sans, Inter, and Noto Nastaliq Urdu for RTL).
    *   All new user-facing strings must be defined with typed bilingual support (English and Urdu) in the central dictionary. Support full RTL layout transitions when Urdu is selected.
3.  **Authentication, Session & Invalidation Integrity:**
    *   Preserve existing `SessionProvider`, `IntegrationBoundary`, and `apiClient` conventions.
    *   **Request Cancellation on Business Switch:** The frontend must preserve the `apiClient` generational abort controller pattern. In-flight private requests must be aborted and stale responses rejected whenever the active business context switches.
    *   **No Automatic Mutation Replay:** Network failures or disconnects during mutations (`POST`, `PATCH`, `DELETE`) must **never** be silently or automatically replayed by client retry loops, preventing duplicate financial contributions.
    *   Do **NOT** introduce a parallel JWT or `localStorage` auth mechanism. The application relies on HttpOnly cookie-based sessions with CSRF tokens.

---

## 3. Financial Calculation & Data Handling

1.  **Strict Calculation Boundary:**
    *   All financial balances, net inflows/outflows, outstanding balances, aging buckets, funding gaps, projected cash flows, FX gains/losses, and health scores **must originate from the backend**.
    *   The frontend formats and displays values (e.g., formatting decimals, attaching currency symbols, date pickers, form validation). It must **never** perform authoritative financial calculations or recalculate balances in the browser.
2.  **Explicit Currency Display & Scale Metadata:**
    *   Display original currency and base currency (PKR) distinctly for multi-currency transactions.
    *   Do not assume all currencies have two decimal places (e.g., JPY uses 0 decimals, BHD uses 3 decimals). Use formatting helpers that respect currency metadata (`currencyDecimals`).
3.  **Missing vs. Zero States:**
    *   Missing, restricted, or stale data must be displayed with explicit placeholder states ("Unavailable", "Pending", or "N/A"), **never as 0.00**.
4.  **Live vs. Demo Mode:**
    *   Live mode (`NEXT_PUBLIC_DATA_MODE=live`) is the default and must surface real backend API errors. Do not silently fall back to synthetic mock data when an API fails in live mode.

---

## 4. Shared Files & Frontend Checkpoint Workflow

1.  **Shared Shell Files & Coordination:**
    *   Modifications to global navigation, sidebar, header, central i18n dictionaries, or root layouts affect Fatima's Dev B modules.
    *   Make changes to shared shell files only during explicitly reserved windows and coordinate directly with Fatima whenever her domain or interface is affected.
2.  **Pausing for Checkpoints:**
    *   When completing a task slice or before a commit, Codex must stop active editing, report the exact list of modified/created files, and explicitly declare a `PAUSED` state.
    *   Wait for Antigravity to complete the Git checkpoint and confirm the new HEAD SHA before resuming.

---

## 5. Testing & Environment Discipline

1.  **Package Manager:**
    *   Use `pnpm` exclusively, adhering to the committed frozen lockfile (`pnpm install --frozen-lockfile`). Do not alternate between `npm` and `pnpm`.
2.  **Validation Commands:**
    *   Unit / Contract Tests: `pnpm test`
    *   TypeScript Typecheck: `pnpm exec tsc --noEmit`
    *   Linter: `pnpm run lint`
    *   Production Build: `pnpm run build` (with `NEXT_PUBLIC_DATA_MODE=live`)
3.  **Browser & Fixture Tests:**
    *   Browser test scripts in `tests/browser.integration.mjs` run against production builds served locally. Synthetic API fixtures run on port 8080 during browser tests and must not overlap with a live running Spring backend.
    *   Check existing root and workspace test discovery configurations before adding new nested test suites.
