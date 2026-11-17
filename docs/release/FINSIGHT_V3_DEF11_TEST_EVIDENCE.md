# FinSight Candidate v3 — DEF-11 Test Evidence & Verification Report

**Task ID:** `FINSIGHT-V3-TASK-01-DEF11-SESSION-INTERCEPT`  
**Date:** November 17, 2026  
**Topic Branch:** `fix/finsight-v3-def11`  
**Code Checkpoint SHA:** `6aac371bd0ef029a373231407a597e52ce100254`  
**Environment:** Windows 11, Node v24.20.0, pnpm 10.28.2, Java 21 (Temurin 21.0.12.1), Disposable PostgreSQL, Next.js 16.4.1 production webpack build, Google Chrome (`channel: 'chrome'`)  

---

## 1. Commands Executed & Gate Summary

| Check / Gate | Exact Command | Scope | Result |
|---|---|---|---:|
| **TypeScript Check** | `corepack pnpm typecheck` (`tsc --noEmit --incremental false`) | Entire `frontend/src` | **PASS** (0 errors) |
| **ESLint Check** | `corepack pnpm lint` (`eslint`) | Entire `frontend/` | **PASS** (0 errors; 51 pre-existing warnings, 0 new warnings in added/edited DEF-11 files except existing cleanup ref pattern) |
| **Frontend Unit & Contract Suite** | `corepack pnpm test` (`node --test tests/*.test.mjs`) | 482 existing + 6 new DEF-11 tests | **PASS** (**488 / 488**, 0 failed, 0 skipped) |
| **Production Build (Live Mode)** | `corepack pnpm build:release` (`next build --webpack`) | Production Next.js server & client bundles | **PASS** (Compiled cleanly; retried once after Next.js security advisory notice) |
| **DEF-11 Multi-Tab Browser Suite** | `node tests/def11-browser.integration.mjs` | Live Spring Boot (8094) + DisposablePostgres + Next.js (3594) + Gateway (3593) + 2 real Chrome tabs | **PASS** (**9 / 9 multi-tab journeys**, 0 unexpected console/page/hydration/CSP errors) |
| **Release Smoke Browser Suite** | `$env:FINSIGHT_BROWSER_CHANNEL="chrome"; corepack pnpm test:browser:smoke` | Real Spring Boot (8093) + DisposablePostgres + Next.js (3593) + Gateway (3592) | **PASS** (**6 / 6 smoke checks**, 36 page loads EN/UR/RTL, zero rebuild storm) |

---

## 2. Multi-Tab Scenarios & Requirements Coverage Matrix

Mapping against all 22 required functional, security, and UX test scenarios:

| # | Requirement / Scenario | Automated Test Evidence | Mode | Result |
|---|---|---|---|:---:|
| **1** | Switching business in another tab with a clean form | `def11-browser.integration.mjs` (Scenario B); `def11-session-switch.test.mjs` (`evaluateBoundarySync`) | Real multi-tab Chrome + Unit | **PASS** |
| **2** | Switching business with a dirty invoice form | `def11-browser.integration.mjs` (`/invoices/new` + Tab 2 switch) | Real multi-tab Chrome | **PASS** |
| **3** | Switching business with a dirty bill form | `def11-browser.integration.mjs` (`/bills/new` + Tab 2 switch) | Real multi-tab Chrome | **PASS** |
| **4** | Returning to the previous business | `def11-browser.integration.mjs` (`def11-return-business` + real `POST /api/businesses/active`) | Real multi-tab Chrome | **PASS** |
| **5** | Discarding an unsaved draft | `def11-browser.integration.mjs` (`def11-discard-changes` on dirty bill) | Real multi-tab Chrome | **PASS** |
| **6** | Recovering a valid saved draft | `def11-browser.integration.mjs` (restored counterparty, line item, price, notice banner); `def11-session-switch.test.mjs` | Real multi-tab Chrome + Unit | **PASS** |
| **7** | Preventing restoration under the wrong business | `def11-session-switch.test.mjs` (`bizA` draft rejected for `bizB`, `userB`, wrong kind/route, tampered JSON) | Unit/Contract | **PASS** |
| **8** | Switching businesses repeatedly | `def11-browser.integration.mjs` (`Alpha -> Beta -> Gamma -> Alpha` across 2 tabs); `def11-session-switch.test.mjs` | Real multi-tab Chrome + Unit | **PASS** |
| **9** | Switching during a pending request | `phase-one.test.mjs` (in-flight abort + late-response rejection on `invalidatePrivateRequests()`); `def11-browser.integration.mjs` (preview settled & `SessionProvider` `syncInFlightRef` serialization) | Unit/Contract + Real Browser | **PASS** |
| **10** | Switching while viewing an organization-specific detail page | `def11-browser.integration.mjs` (Scenario E on `/invoices/[id]` renders `commercial-business-changed` + Return to Alpha works) | Real multi-tab Chrome | **PASS** |
| **11** | No stale-business submission | `def11-browser.integration.mjs` (`commercial-workspace` unmounted when modal open; Beta `/invoices` count confirmed `0` before & after Alpha save) | Real multi-tab Chrome | **PASS** |
| **12** | No cross-tenant draft restoration | `def11-browser.integration.mjs` (Beta `/bills/new` input `""` after discard); `def11-session-switch.test.mjs` | Real multi-tab Chrome + Unit | **PASS** |
| **13** | No mutation using a mismatched session | `phase-one.test.mjs` (`AbortError` before fetch when `blocked = true`); `IntegrationBoundary` unmounts stale form + `switchLocked` `inert` guard | Unit/Contract + DOM/Transport | **PASS** |
| **14** | Safe behavior after logout or session expiration | `def11-browser.integration.mjs` (Tab 2 signs out -> Tab 1 dismisses modal, opens login, purges `sessionStorage` drafts); `def11-session-switch.test.mjs` | Real multi-tab Chrome + Unit | **PASS** |
| **15** | Safe behavior when previous business is no longer accessible | `def11-browser.integration.mjs` (intercepted `403` hides `Return`, shows `unavailableError`, keeps `Discard`); `def11-session-switch.test.mjs` | Real Chrome + Route Intercept + Unit | **PASS** |
| **16** | Correct handling of failed business-switch requests | `def11-browser.integration.mjs` (`500` shows retryable `networkError` while preserving modal) | Real Chrome + Route Intercept | **PASS** |
| **17** | English modal and keyboard navigation | `def11-browser.integration.mjs` + `def11-session-switch.test.mjs` (`role="alertdialog"`, `useModalFocus` trap) | Real Chrome + Contract | **PASS** |
| **18** | Urdu translation and RTL layout | `def11-browser.integration.mjs` (`dir="rtl"`, Urdu copy `"دوسرے ٹیب میں کاروبار تبدیل ہو گیا ہے"`, `def11-modal-ur-mobile.png`); `def11-session-switch.test.mjs` (1:1 key symmetry) | Real Chrome + Visual PNG + Unit | **PASS** |
| **19** | Mobile layout and touch interactions | `def11-browser.integration.mjs` (`390x600`: `scrollWidth <= innerWidth`, button height `>= 44px`, screenshot verified) | Real Chrome (390x600) | **PASS** |
| **20** | Focus management, Escape behavior, and accessibility | `def11-browser.integration.mjs` (pressing `Escape` keeps modal open & shows `def11-choice-required-hint`); `ux-hardening.test.mjs` / `useModalFocus` | Real Chrome + Unit | **PASS** |
| **21** | No silent loss of modified form data | `def11-browser.integration.mjs` (dirty invoice persisted across tab switch & restored intact to database save) | Real multi-tab Chrome | **PASS** |
| **22** | No unnecessary prompt for clean forms | `def11-browser.integration.mjs` (`unsaved-business-switch-modal` count `0` on clean switch) | Real multi-tab Chrome | **PASS** |

---

## 3. Visual & Build Evidence Notes

- **Mobile Urdu RTL Screenshot:** `frontend/test-artifacts/def11-browser/def11-modal-ur-mobile.png` verified cleanly (`390x600`, Noto Nastaliq Urdu font, RTL flow, touch-sized buttons stacked cleanly, zero clipping or horizontal scrollbar).
- **Next.js Security Advisory Reminder:** During `corepack pnpm build:release`, Next.js `16.4.1` emitted its built-in upgrade advisory warning (`SecurityFatalError` on first invocation, succeeded on retry). Package versions in `package.json` and `pnpm-lock.yaml` remain intentionally pinned to the Candidate v3 baseline.
- **Remaining Regression Risks:**
  - Simulated HTTP `500` and `403` error injection in Tests 15 & 16 used Playwright `page.route` interception inside the live Chromium session because revoking membership mid-test from a second owner was outside the single-owner fixture setup; behavior against real Spring Security `403`/`404` responses matches the `ApiError` contract verified in `phase-one.test.mjs`.
  - Non-commercial forms (`ProjectForm`, `SampleForm`, `EstimateEditor`) were kept outside this commit per Task 1 scope boundaries.
