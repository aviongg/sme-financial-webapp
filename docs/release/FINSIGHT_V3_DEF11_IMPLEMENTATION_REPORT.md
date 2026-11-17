# FinSight Candidate v3 — DEF-11 Implementation Report

**Task ID:** `FINSIGHT-V3-TASK-01-DEF11-SESSION-INTERCEPT`  
**Date:** November 17, 2026  
**Topic Branch:** `fix/finsight-v3-def11`  
**Base Frozen Tree SHA:** `19547bf3dda9744d51e74c87a908e2e42577299c`  
**Code Checkpoint SHA:** `6aac371bd0ef029a373231407a597e52ce100254`  

---

## 1. Original Root Cause

When a user edited an unsubmitted commercial document (`/invoices/new`, `/invoices/[id]`, `/bills/new`, or `/bills/[id]`) in Tab 1 under Organization A and switched the active business to Organization B in Tab 2:

1. Tab 2's `SessionProvider` posted `{ phase: 'start' }` and `{ phase: 'done' }` over `BroadcastChannel('finsight-session')`, and/or Tab 1 ran `revalidate()` on `window.focus`.
2. In Tab 1, `block()` immediately invoked `invalidatePrivateRequests()` and reset React session state to `{ ...initial, status: 'loading', epoch: s.epoch + 1 }`.
3. `IntegrationBoundary.tsx` replaced its authenticated subtree with `<main role="status">Loading…</main>` and subsequently remounted `<div key="${user.id}:${business.businessId}:${epoch}">`.
4. React destroyed `CommercialDocumentWorkspace`'s local component state (`draft`, `dirty`) silently without capturing the form edits or informing the user.
5. On organization-specific detail routes (`/invoices/[id]`, `/bills/[id]`), switching cleanly from Organization A to Organization B caused `GET /api/invoices/[id]` to return HTTP `404`, presenting `"This document was not found in the active business."` without explaining that the active business had changed in another tab.

---

## 2. Architecture Selected

The resolution preserves strict **server-authoritative tenant isolation** while protecting unsaved user work across tabs:

```
  Tab 2 switches Org A -> Org B (BroadcastChannel / window.focus)
                 │
                 ▼
  Tab 1: invalidatePrivateRequests() immediately blocks all private API traffic
         & FormDirtyContext.captureAllDrafts() saves draft to sessionStorage
                 │
                 ▼
  evaluateBoundarySync() verifies authoritative /auth/me + /businesses/active
        ├── Auth revoked / user changed ──▶ clearAllCommercialDrafts() -> sign-in / challenge
        ├── Clean form (dirty = false)   ──▶ commit switch immediately (records recentSwitch)
        └── Dirty form (dirty = true)    ──▶ Unmount Org A tree + show UnsavedBusinessSwitchModal
                                                │
                   ┌────────────────────────────┴────────────────────────────┐
                   ▼                                                         ▼
       [Return to previous business]                             [Discard changes and continue]
       POST /api/businesses/active -> revalidate                 Clear sessionStorage draft
       Allow requests -> Remount Org A                           Allow requests -> Mount Org B
       Restore validated draft from sessionStorage               Clean Org B destination
```

Key architectural properties:
- **Zero stale-context execution:** When a cross-tab business switch is confirmed with dirty edits, `IntegrationBoundary` immediately unmounts the Organization A workspace (`ConfigurationProvider`, `AiProvider`, `CommercialDocumentWorkspace`) instead of keeping stale Organization A components mounted behind a cosmetic modal.
- **Synchronous transition lock:** During the brief window between receiving `{ phase: 'start' }` on `BroadcastChannel` and receiving server confirmation on `{ phase: 'done' }`, `invalidatePrivateRequests()` blocks all private HTTP requests and `IntegrationBoundary` applies `inert={session.switchLocked || undefined}` so users cannot interact with or trigger writes against a changing tenant.
- **No client tenant authority:** The client never sends a tenant ID override. Returning to the previous business uses the authorized `POST /api/businesses/active` endpoint and revalidates `/auth/me`, `/businesses`, and `/businesses/active` before unlocking private API calls or restoring any draft.

---

## 3. Session Synchronization Behavior (`SessionProvider.tsx` & `sessionSync.ts`)

1. **`BroadcastChannel('finsight-session')` `phase === 'start'`:**
   - Immediately calls `invalidatePrivateRequests()` so in-flight requests abort and new private requests throw `AbortError`.
   - If authenticated with dirty work (`formDirty?.hasDirtyWork()`), synchronously captures drafts to `sessionStorage` and sets `switchLocked = true` without erasing `state.business` or triggering a `loading` screen flash before server confirmation.
   - If clean (`!hasDirtyWork()`), calls `block()` cleanly as before (`lastBoundaryRef` retains the pre-switch business briefly for Scenario E detection).
2. **`syncWithServerBoundary()` (on `phase === 'done'`, `window.focus`, `finsight-business-changed`):**
   - Serializes concurrent sync triggers via `syncInFlightRef` + `resyncQueuedRef` so rapid multi-tab switches never leave stale state.
   - Evaluates the server response via pure state-machine function `evaluateBoundarySync(...)`:
     - **`TERMINATE_SESSION`:** Auth stage downgraded, user logged out (`401` / `pre_auth_required`), or `user.id` changed $\rightarrow$ clears `sessionStorage` drafts, cancels pending modal, and transitions to anonymous/challenge state.
     - **`AUTO_REVERT_PENDING`:** If Tab 2 switched `Org A -> Org B -> Org A` while the modal was waiting, automatically closes the modal, unlocks Org A, and restores the captured draft.
     - **`UPDATE_PENDING`:** If Tab 2 switched `Org A -> Org B -> Org C`, preserves `Org A` as `previousBusiness`, updates `currentBusiness` to `Org C`, and rechecks whether `Org A` remains accessible (`ACTIVE` membership in `/businesses`).
     - **`INTERCEPT_DIRTY`:** Unmounts Org A, sets `pendingBusinessSwitch`, keeps private APIs blocked, and renders `UnsavedBusinessSwitchModal`.
     - **`COMMIT_CLEAN_SWITCH`:** Applies the new business cleanly without any prompt, and records `recentBusinessSwitch` (`previousBusiness`, `currentBusiness`, `route`, `at`) for detail route clarity.

---

## 4. Dirty-Form Handling & Detail Route Behavior

- **`FormDirtyContext.tsx`:** Registers active commercial workspaces (`CommercialDocumentWorkspace`) keyed by form ID (`commercial:INVOICE:new`, `commercial:BILL:<id>`, etc.), exposing synchronous `hasDirtyWork()`, `captureAllDrafts()`, and `discardAllDrafts()` callbacks.
- **Scenario E (Existing Detail Routes `/invoices/[id]`, `/bills/[id]`):**
  - When a clean cross-tab business switch occurs on a detail route and `GET /api/invoices/[id]` returns `404` under the new business, `CommercialDocumentWorkspace` checks `recentBusinessSwitch` matching `(currentBusinessId, pathname)`.
  - Rather than displaying a misleading `"This document was not found"` deletion message, it renders `[data-testid="commercial-business-changed"]` explaining which business changed (`Alpha Textiles` $\rightarrow$ `Beta Trading Co`), with direct actions to **Return to previous business** or go **Back to list**.
  - Genuine `404` responses (nonexistent or unauthorized IDs navigated directly) continue to render the standard `copy.notFound` card.

---

## 5. Draft Preservation & Residual Risk Evaluation (`draftStorage.ts`)

- **Storage Medium:** Volatile browser `window.sessionStorage` ONLY (cleared when tab/browser closes; never uses `localStorage`).
- **Key Scoping:** `finsight:commercial-draft:v1:${encodeURIComponent(userId)}:${encodeURIComponent(businessId)}:${encodeURIComponent(normalizeRoute(route))}`.
- **Version & TTL:** Schema `version: 1`, `savedAt` timestamp, strict `30`-minute expiration (`COMMERCIAL_DRAFT_TTL_MS`), and automatic sweep of expired or corrupt entries on session load.
- **Minimal Payload:** Stores only `{ counterpartyName, documentDate, dueDate, notes, lines }` sanitized via `sanitizeCommercialDraft()`. Excludes tokens, cookies, CSRF headers, file blobs, user emails, and arbitrary metadata.
- **Cross-Tenant & Stale Overwrite Protection:**
  - `loadCommercialDraft()` asserts exact match on `userId`, `businessId`, normalized `route`, `kind`, and `documentId`.
  - For existing `DRAFT` documents (`/[id]`), `canRestoreCommercialDraftIntoDocument()` enforces `document.businessId === activeBusinessId`, `document.status === 'DRAFT'`, and `envelope.baseServerVersion === document.version`. If another user or tab edited/finalized the server document, the stale draft is discarded rather than overwriting newer server state.
- **Lifecycle Cleanup:** Immediately deleted upon successful Save (`create`/`update`), Delete, Finalize, Void, explicit Discard, `logout()`, `401` session expiration, or user switch.
- **Residual Risk Evaluation:** `sessionStorage` is origin-scoped and readable by JavaScript running on the FinSight origin; therefore, a hypothetical XSS vulnerability could read transient unsubmitted invoice/bill fields during the tab lifetime. This risk is minimized by FinSight's strict CSP, volatile tab-scoped storage (never `localStorage`), 30-minute TTL, exclusion of auth credentials/PII metadata, and aggressive purge on save/discard/logout.

---

## 6. Security Boundaries Verified

1. Writes from stale business contexts are blocked both in React (unmounted subtree / `inert` during sync) and in transport (`invalidatePrivateRequests()` aborts in-flight calls and throws `AbortError` for subsequent calls).
2. Preview autosave timers (`300ms` debounce in `CommercialDocumentWorkspace`) are aborted on unmount and blocked in `client.ts`.
3. `POST /api/businesses/active` requires a valid session and CSRF token (`X-XSRF-TOKEN`); server session remains authoritative.
4. Logout (`POST /api/auth/logout`) and session expiration (`401` / `pre_auth_required`) always dismiss `pendingBusinessSwitch`, purge drafts, and transition to sign-in.
5. Failed return attempts (`403`/`404` when membership was revoked or business deleted) disable "Return to previous business" cleanly while leaving "Discard changes and continue" accessible; transient network failures retain retryability without restoring wrong-business data.

---

## 7. Changed & Added Files

### Modified
- `frontend/src/app/layout.tsx`
- `frontend/src/components/auth/SessionProvider.tsx`
- `frontend/src/components/commercial/CommercialDocumentWorkspace.tsx`
- `frontend/src/components/layout/IntegrationBoundary.tsx`

### Added
- `frontend/src/context/FormDirtyContext.tsx`
- `frontend/src/lib/auth/sessionSync.ts`
- `frontend/src/lib/commercial/draftStorage.ts`
- `frontend/src/lib/i18n/translations/sessionSwitch.ts`
- `frontend/src/components/ui/UnsavedBusinessSwitchModal.tsx`
- `frontend/tests/def11-session-switch.test.mjs`
- `frontend/tests/def11-browser.integration.mjs`

---

## 8. Known Limitations

- Other secondary forms outside commercial invoices/bills (`ProjectForm`, `SampleForm`, `EstimateEditor`) still rely on `beforeunload` for tab close; they can opt into `FormDirtyContext.registerForm(...)` incrementally without changing `SessionProvider` or `IntegrationBoundary`.
- Standalone Playwright browser binaries (`ms-playwright`) were not pre-downloaded on this host, so browser verification executed against locally installed Chrome (`channel: 'chrome'`).
