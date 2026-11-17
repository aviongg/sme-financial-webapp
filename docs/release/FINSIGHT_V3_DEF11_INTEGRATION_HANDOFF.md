# FinSight Candidate v3 — DEF-11 Integration Handoff

**Task ID:** `FINSIGHT-V3-TASK-01-DEF11-SESSION-INTERCEPT`  
**Date:** November 17, 2026  
**Topic Branch:** `fix/finsight-v3-def11`  
**Base Commit (Candidate v3 Frozen Tree):** `19547bf3dda9744d51e74c87a908e2e42577299c`  
**Code Checkpoint SHA:** `6aac371bd0ef029a373231407a597e52ce100254`  

---

## 1. Summary of Topic Branch & Commits

- **Topic Branch Name:** `fix/finsight-v3-def11`
- **Isolated Worktree:** `C:\Users\PC\Projects\sme-financial-webapp-def11`
- **Code Commit SHA:** `6aac371bd0ef029a373231407a597e52ce100254` (`fix(frontend): resolve DEF-11 cross-tab business switch unsaved draft loss`)
- **Documentation Commit:** Immediately follows `6aac371bd0ef029a373231407a597e52ce100254` on `fix/finsight-v3-def11` (resolve exact tip with `git rev-parse fix/finsight-v3-def11`).
- **Original Branches Preserved Untouched:** `main`, `dev/suleman`, `demo/local-public-deployment`, `integration/finsight-production-candidate-v3`.

---

## 2. Modified & Added Files

| Status | Path | Responsibility |
|---|---|---|
| Modified | `frontend/src/app/layout.tsx` | Mounts `FormDirtyProvider` around `SessionProvider` |
| Modified | `frontend/src/components/auth/SessionProvider.tsx` | Intercepts cross-tab business switches with dirty-form capture, preserves `153e94b` auth abort handling & `landing.test.mjs` probe contracts, adds return/discard resolution & logout purge |
| Modified | `frontend/src/components/layout/IntegrationBoundary.tsx` | Unmounts stale business workspace and displays `UnsavedBusinessSwitchModal` when `pendingBusinessSwitch` is set; applies `inert` lock during `switchLocked` transitions |
| Modified | `frontend/src/components/commercial/CommercialDocumentWorkspace.tsx` | Registers invoice/bill dirty state, restores validated `sessionStorage` drafts, clears drafts on save/delete/finalize/void, and renders Scenario E business-changed guidance |
| Added | `frontend/src/context/FormDirtyContext.tsx` | Generic active-form dirty state and synchronous draft capture registry |
| Added | `frontend/src/lib/auth/sessionSync.ts` | Pure state-machine evaluator for boundary sync, repeated switches, auto-revert, and auth termination |
| Added | `frontend/src/lib/commercial/draftStorage.ts` | Versioned, TTL-bounded, `(userId, businessId, route)`-scoped `sessionStorage` draft store |
| Added | `frontend/src/lib/i18n/translations/sessionSwitch.ts` | English and Urdu (RTL) copy for the intercept modal and detail business-changed view |
| Added | `frontend/src/components/ui/UnsavedBusinessSwitchModal.tsx` | Accessible `alertdialog` modal using FinSight tokens and `useModalFocus` |
| Added | `frontend/tests/def11-session-switch.test.mjs` | Unit & contract tests for draft storage, `sessionSync`, EN/UR symmetry, and boundary/modal invariants |
| Added | `frontend/tests/def11-browser.integration.mjs` | End-to-end real Chromium multi-tab Playwright test suite against live Spring Boot + PostgreSQL |

---

## 3. Backend Contracts & Dependencies

- **Backend Changes Required:** **None.** Zero Java files, Flyway migrations (`V1–V52`), permissions (`BusinessPermission.java`), or monthly projection services (`MonthlyRecordService.java`, `MonthlyProjectionService.java`) were modified.
- **Endpoints Used:** Existing `GET /api/auth/me`, `GET /api/businesses`, `GET /api/businesses/active`, `POST /api/businesses/active`, `POST /api/auth/logout`, and `/api/invoices*` / `/api/bills*`.
- **Package Dependencies:** **None added.** `package.json` and `pnpm-lock.yaml` are completely untouched.

---

## 4. Reviewer Checklist (Codex & Suleman)

1. **Tenant Isolation (Suleman):**
   - Verify `IntegrationBoundary.tsx` unmounts `<ConfigurationProvider><AiProvider>{children}</AiProvider></ConfigurationProvider>` whenever `session.pendingBusinessSwitch` is truthy.
   - Verify `SessionProvider.tsx` calls `invalidatePrivateRequests()` on `BroadcastChannel` `'start'` and keeps private APIs blocked while `pendingBusinessSwitch` is active.
   - Verify `resolveReturnToPreviousBusiness()` calls `POST /api/businesses/active` and revalidates server session before re-enabling private requests.
2. **Draft Preservation Security (Suleman):**
   - Verify `draftStorage.ts` uses `sessionStorage` only (no `localStorage`), enforces 30-minute TTL, scopes keys by `(userId, businessId, route)`, checks `baseServerVersion` before overwriting existing `DRAFT` documents, and purges all drafts on logout / `401`.
3. **Frontend & UX Contracts (Codex):**
   - Verify `landing.test.mjs` probe contract (`const publicRoute = isPublicRoute(usePathname());`, 1 `queueMicrotask(`, `revision.current++; probed.current = false;`) passes cleanly.
   - Verify `UnsavedBusinessSwitchModal.tsx` respects `direction` (`rtl` in Urdu), traps focus via `useModalFocus`, handles `Escape` non-destructively, and fits `390x600` mobile viewports cleanly.

---

## 5. Safe Integration Instructions into Candidate v3

When ready to integrate after technical sign-off:

```powershell
# 1. Verify frozen base & diff from an authorized release integration worktree
git fetch origin
git diff 19547bf3dda9744d51e74c87a908e2e42577299c..fix/finsight-v3-def11 --stat

# 2. Verify zero changes outside frontend/ and docs/release/
git diff 19547bf3dda9744d51e74c87a908e2e42577299c..6aac371bd0ef029a373231407a597e52ce100254 -- backend database scripts

# 3. Merge into release candidate branch strictly with --no-ff (after approval)
git merge --no-ff 6aac371bd0ef029a373231407a597e52ce100254
# (or merge the full branch head including documentation)
```

Run CI gates (`frontend` unit tests, `typecheck`, `lint`, `build:release`, `browser-smoke`, `def11-browser.integration.mjs`) on the merge commit before publishing.
