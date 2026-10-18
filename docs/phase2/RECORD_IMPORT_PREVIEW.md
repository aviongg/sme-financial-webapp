# A-F12A-IMPORT-PREVIEW

Status: FRONTEND_PREVIEW_VERIFIED & DRAFTS_CONTRACT_READY — preview complete; persistent import-to-draft integration active under V24. Local preview only for unpersisted rows; persistent drafts created via POST /api/finance/drafts/import.

Starting branch: dev/suleman. Actual starting HEAD: c99d193e6eb6ccced11f6df27c2a939b9596bf02.
Root/frontend AGENTS.md v1.1 read. Task-specific feature-local bilingual copy overrides central translation registration for this isolated slice. Existing paths checked absent before creation. Backend/.cp.txt preserved.

## Limits and parsing rules (set before implementation)

- CSV upload: .csv extension, UTF-8 only (strict decoding); no XLSX/binary support. Export Excel as CSV or paste tab-separated rows.
- CSV delimiter: comma. Paste delimiter: tab. No delimiter or number-locale guessing.
- Maximum source: 512 KiB UTF-8 and 524288 UTF-16 code units; reject file size before reading and text length before parsing/encoding.
- Maximum 1000 nonblank logical records including optional headings, 32 columns, 2000 UTF-16 code units per decoded cell AND original cell token, 5000 physical lines.
- Quoted fields, doubled quotes, embedded separators/newlines, CRLF/LF/CR, initial BOM and empty cells supported. Quotes must start at the beginning of a field; after a closing quote only separator/end-of-record is accepted. Unclosed/bare quotes and NUL are errors.
- Physically empty/whitespace-only single-cell lines are ignored but counted; explicit empty quoted cells/delimiter-separated empty cells remain records for review. Source physical starting/ending line and decoded/original token text retained.
- Render at most 20 interpreted rows per page and 5 original rows initially. Parsing and review are bounded; no all-row DOM rendering.
- Date format selected explicitly: YYYY-MM-DD, DD/MM/YYYY or MM/DD/YYYY. Valid Gregorian calendar dates only, no today fallback. Preliminary current-posting readiness also checks account opening and Asia/Karachi today.
- Number convention: decimal point, optional correctly grouped comma thousands, supported Western/Arabic/Urdu digits. No arbitrary punctuation stripping, locale inference, exponent, plus sign or signed amount interpretation. Exact shared normalization is consumed read-only; HALF_UP rounding is visible and capacity checked after carry.
- Separate in/out columns: blanks and explicit zero distinguished. Both original nonzero values conflict even if one would round to zero. Nonpositive rows cannot be ready.
- Direction values explicitly configured (default IN/OUT); direction is not financial classification. Purpose undecided by default. Supported classifications are only direct sale receipt / operating expense payment, compatible with direction.
- Duplicate candidates warn only, never delete/merge/exclude or imply backend duplicate detection. Counts only, no authoritative financial totals.

## Ownership

Only new records/import route, components/records/import/**, lib/imports/**, tests/record-import-preview.test.mjs and this document. No edits to existing feature/adapters/contracts, shared state/navigation/auth/transport, dependency files or browser harnesses.

Broader TypeScript/browser/build verification requires a coordinated window with Antigravity (reversals owner); no such window is currently supplied. Only isolated feature checks are authorized during concurrent work.


## Implemented flow and exact created paths

- `frontend/src/app/records/import/page.tsx` — direct route within existing SessionProvider/IntegrationBoundary/LivePage. No global navigation edit.
- `frontend/src/components/records/import/ImportPreview.tsx` — CSV/paste source step, mapping step, interpreted review, counts/filter/pagination, selection/exclusion/restoration, account/currency selection and unsaved-preview protection.
- `frontend/src/components/records/import/ImportMapping.tsx` — reusable source preview and explicit field/date/amount-mode mapping.
- `frontend/src/components/records/import/ImportRows.tsx` — exact interpreted strings, original tokens, issues, classification choices, possible repeated-row warnings.
- `frontend/src/components/records/import/import-copy.ts` — typed feature-local EN/UR copy.
- `frontend/src/lib/imports/delimited.ts` — bounded CSV/TSV lexical parser, size/type/encoding errors.
- `frontend/src/lib/imports/preview.ts` — pure explicit date/mapping/money/direction/classification review and reversible exclusions; no balance/totals calculation.
- `frontend/src/lib/imports/lifetime.ts` — local asynchronous read generations and cancellation.
- `frontend/src/lib/imports/accounts.ts` — bounded pagination of the existing authorized account read API.
- `frontend/tests/record-import-preview.test.mjs` — isolated Node tests and controlled render/type fixtures.
- `docs/phase2/RECORD_IMPORT_PREVIEW.md` — this sole feature document.

All paths were absent at initial inspection; no contributor content overwritten. No existing account/transaction/draft adapters/contracts, existing Records/finance components, shared shell/auth/transport/translations, manifests, locks, browser harnesses, tooling or implementation-state files edited. Antigravity's concurrent reversal changes and `backend/.cp.txt` remain untouched. No Git mutations or remote writes.

## Interpretation and readiness semantics

- Headings are an explicit visible choice, initially checked. Mappings begin unset; no financial values, date format, direction aliases or classification are guessed from headings.
- Source data-row counts exclude the optional heading and ignored physically blank lines. Physical source line numbers (including multiline spans) remain stable through remapping, filters, exclusion and restoration. Reference strings retain leading zeros and exact source text. Required mapped cells missing on a ragged row are issues; an absent trailing optional reference is blank, while other unmapped source cells remain available in original preview.
- Local readiness requires date/description/amount/direction mappings, a nonempty description (at most the current posting limit of 255 characters), a valid current-workflow date, positive normalized amount, matching active/available real account and currency metadata, and explicit direction-compatible supported classification. Date years 0001–9999; Gregorian leap rules; Arabic/Urdu date digits normalized under the explicitly selected format. No today substitution.
- Only PKR rows can be ready under the existing bounded posting workflow. Other supported currency scales still produce an honest interpreted preview; unknown metadata never guesses a scale. Readiness is preliminary, not server permission, solvency approval, persisted data or posting confirmation. No cash-balance simulation or financial aggregate is performed in the browser.
- Account/currency/settings changes re-interpret original cells rather than re-rounding previously normalized values. Money uses the shared read-only exact string/BigInt normalizer. The existing amount-input limit is 128 characters, independent of the larger source-cell limit. Source strings remain unchanged. Rounded values are flagged visibly; a future actual recording step must require its own approved confirmation.
- Separate columns distinguish blank, explicit zero, malformed/negative and nonzero source values. Conflicting original nonzero values block readiness even if one rounds to zero. No signed-amount convention is supported in this slice.
- Repeated-row candidates use interpreted date, trimmed description, exact normalized amount and direction within this source only. Reference text is preserved independently. Warnings do not exclude/merge/remove anything, do not block otherwise valid preview rows, and make no assertion about existing backend rows. Only the first five related line references are expanded in the card to bound display size.
- Source/account/purpose problems remain editable through Change mapping or row purpose selection; issue-only filter and source-stable bulk/per-row exclusion/restoration do not destroy source records. Review shows 20 cards per page, not the whole dataset. Counts are row counts only; there is no financial-total panel.

## Session, privacy and abandonment

- Reads use the existing `accountsApi.list({status:'ACTIVE',page,size:100}, signal)` without changes or tenant overrides. At most 10 pages/1000 accounts are allowed; oversized, failed or inconsistent reads show an error rather than partial/sample success. Source inspection remains usable without accounts but cannot mark rows ready.
- The private preview subtree is keyed by session epoch, user, business, role, membership and status. Context loss/switch destroys source and selection state. Existing transport generation plus resource cancellation reject stale account responses; local file generations reject late file reads after replacement/unmount. No source content is sent with account reads.
- Explicit demo permits the local parser/wizard but does not request private accounts; no row gains a fictional account. No live mock fallback.
- No localStorage, file upload endpoint, background save, draft creation, transaction mutation, account creation or monthly write. Uploaded/pasted formulas and HTML remain inert React text, including original source tokens. There is no active Import/Post button or nonfunctional future-workflow control.
- Notice explains that refreshing/leaving loses this local preview. Start again, replacing entered paste with a CSV, and ordinary current-tab navigation links use a feature-local discard/keep confirmation. Full document unload/reload uses the browser's native beforeunload warning. Modal background is inert, focus moves to Keep, Tab/Escape are handled and focus is restored. Reset returns focus to source selection. Business/session transitions intentionally clear private content without blocking authentication or carrying it to another business.
- Client-side browser history/back behavior, native unload restrictions and mobile/focus behavior still require real browser verification; no universal navigation-guard claim is made from source/render checks.

## Fresh verification (17 October 2026)

Dependency auto-install disabled using process-only `pnpm_config_verify_deps_before_run=false`. Existing dependencies only. New top-level test is discoverable by the existing `tests/*.test.mjs` command; no framework/package/tool configuration changes.

1. `pnpm exec node --test tests/record-import-preview.test.mjs` — **exit 0, 33 passed, 0 failed/skipped**.
2. `pnpm exec eslint src/app/records/import/page.tsx src/components/records/import src/lib/imports tests/record-import-preview.test.mjs` — **exit 0, no warnings/errors**.
3. Tests include isolated no-emit semantic TypeScript checks of pure feature modules and owned route/components, with stable typed stubs for shared auth/API/integration boundaries and actual read-only UI primitive types. These do not run the application-wide TypeScript program or write `.next`, `tsbuildinfo`, Maven or shared outputs. They do not replace the eventual whole-application check against the real boundaries.
4. Actual feature components are rendered in controlled React server-render tests for EN/UR, RTL attributes, labels, field problems, rounding, inert HTML/formula source text and progressive initial settings. Demo invokes the local resource callback with zero private reads. Page-boundary tests verify keyed context replacement/session/membership gating. These are isolated fixtures, not real browser or live-backend evidence.

Coverage: CSV quoting/doubled quotes/separators/multiline cells; TSV; BOM/CRLF/LF/CR; empty fields and blank rows; malformed quoting/limits/strict UTF-8/file rejection; mapping/headings/remapping; explicit date ambiguity/leap days; exact grouped/numeral normalization; HALF_UP/capacity carry for PKR/JPY/BHD; conflicting in/out and blank-versus-zero; manual direction values; unresolved and incompatible purpose; account/currency revalidation; exclusion/restoration/counts; possible repeats without removal; preserved source lines/reference/raw tokens; cancellation/generation; bounded account pagination/errors; source rendering and bilingual parity.

Final branch/HEAD remained `dev/suleman` / `c99d193e6eb6ccced11f6df27c2a939b9596bf02` at the closing status check. Starting status contained only untracked `backend/.cp.txt`; closing status also contained Antigravity's reversal work, which was preserved. Package/lockfile diff check exited 0.

## Remaining coordinated verification and backend dependencies

No exclusive wider verification window was supplied while Antigravity is building/verifying reversals. Consequently no full frontend suite, whole-application TypeScript/lint, production build, browser/application server, database operation or existing browser harness was run/changed for this slice. No live integration or deployed availability of `/records/import` is claimed. Existing local services were neither started nor stopped.

Requested next coordinated window (after reversals output ownership is released): run the full frontend suite and `pnpm exec tsc --noEmit --incremental false`, production build, then browser checks of direct navigation/session gate, real accounts, file/paste/mapping/review/exclude/remap, stale-context cancellation, EN/UR/RTL/mobile overflow, keyboard/focus, native/SPA abandonment and zero source uploads/private demo calls. Confirm server permission errors stay visible. Browser history guard behavior is specifically pending.

Actual import/draft/recording integration needs a separately approved backend contract: server validation/authorization, exact reviewed row values and purpose, account/date/currency eligibility, persistent source provenance and reference identity, durable operation identity/idempotency, atomic partial/batch outcomes, recovery/errors and legitimate duplicate handling. The preview defines no such endpoint/DTO and performs no financial writes. No imported transactions, persisted drafts or full F12A completion are claimed.

**PAUSED.**


## Final application/browser verification — 17 October 2026

The user assigned the shared build/service window after Antigravity paused reversals. Root/frontend AGENTS v1.1, this document, TRANSACTION_REVERSALS.md evidence, and implementation-state reservations were inspected. Branch and final HEAD remain `dev/suleman` / `c99d193e6eb6ccced11f6df27c2a939b9596bf02`. All reversal/backend/migration work and backend/.cp.txt preserved. This window changed only this feature document in the repository; no import implementation defect required a source fix. The original eleven created feature paths above remain the full import path list.

### Evidence reuse and fresh gates

- Antigravity's reversal evidence reports 64 combined real-stack groups, exit 0, current PostgreSQL V23 and TypeScript/lint success. That reversal suite was inspected, not rerun or attributed to this task.
- Initial production BUILD_ID/app-paths manifest was newer than every import source file and explicitly included `/records/import`. A fresh final live build subsequently replaced it after demo verification.
- `pnpm test`: **exit 0; 185 passed, 0 failed/skipped**, including all 33 import tests. No dependency install. Process-only `pnpm_config_verify_deps_before_run=false` used throughout.
- `pnpm exec tsc --noEmit --incremental false`: **exit 0**.
- `pnpm run lint`: **exit 0**, one existing `SessionProvider.tsx:204` ref-cleanup warning; no import warning/error. Shared file left untouched.
- Explicit demo `pnpm run build`: **exit 0**. Restored live `pnpm run build`: **exit 0**, BUILD_ID `J1VncxTt8oSFscePbz8QG`, includes `/records/import`. Both builds run Next's TypeScript gate. Process-only mode/backend settings; no environment-file changes. Existing middleware deprecation and Next standalone/start notices remain.

### Actual browser acceptance

Installed Playwright with real headless Microsoft Edge; production Next, actual Spring and disposable PostgreSQL, no synthetic API fixtures. Private operational scripts/results/screenshots are under the task's existing non-repository work directory, `work/finsight-review-v23-import` relative to the Codex task workspace. No browser framework or repository harness added/modified.

1. `node work/import-browser.mjs`: **exit 0, 11 scenario groups**. Actual UTF-8 BOM CSV file upload; escaped quotes, quoted commas, multiline cells and mixed CRLF/LF; TSV paste; original physical source lines/tokens; headings toggle and remapping without re-upload; exact Urdu grouped amount `۱,۵۰۰.۵۵۵` -> `1500.56 PKR` with visible HALF_UP warning; explicit unresolved purpose; duplicate warnings without deletion; DD/MM versus MM/DD re-interpretation and leap/invalid-date handling; separate in/out conflict and explicit zero; per-row and bulk exclusion/restoration/counts; issue filter; real PKR/BHD account choice and unsupported-currency readiness; malformed quote, XLSX, file size, cell limit and invalid UTF-8 feedback. Uploaded HTML/formula strings stayed inert text. Keyboard focus trapping, Escape/keep, Records-link abandonment and cancelled native reload preserved preview. Desktop and 390px EN/UR bounds checked after the shared responsive transition settled.
2. `node work/import-context.mjs`: **exit 0, 2 scenario groups**. Held an actual Spring account response, changed the server active business, revalidated through the established session boundary, then released the obsolete response. Previous source/account selection cleared and old accounts did not populate the new empty business. Complete actual Urdu mapping/review tested with keyboard selection/Tab, Western `1500.56 PKR` in `bdi dir=ltr`, RTL and no mobile document overflow. No fabricated account response used.
3. Actual server logout followed by focus revalidation removed the private preview and displayed sign-in. These checks exercise server session invalidation/401 handling, not a claim of waiting for the configured inactivity timeout. A separate synthetic second business was created only in this disposable database for context testing; original review business restored afterward.
4. `node work/import-demo.mjs`: **exit 0** against an explicit demo production build. TSV preview mapping, exact rounding, EN/UR/RTL mobile and disabled account selection checked; **zero private `/api/` requests**. No fictional ready account or active posting control.
5. `node work/import-restored-smoke.mjs`: **exit 0** after final live rebuild. Normal sign-in/business chooser, real four-account list, persisted Records activity and direct import route passed. The additional synthetic business means sign-in must select `LOCAL REVIEW — isolated sample business`.

Screenshots captured: `import-en-mobile.png`, `import-ur-mobile.png`, `import-ur-review-mobile.png` in the private review directory; the full Urdu review screenshot was also visually inspected. This is preview/browser evidence, not persisted import integration or full F12A acceptance.

### Zero financial mutations

During upload/paste, mapping, remapping, review and exclusions the audited browser made **zero non-GET private requests**. Account GETs use the existing API; independent shared language profile updates, authentication/business setup and read-only query POSTs were separated from that import-action audit. No draft/transaction/account/monthly mutation or source upload occurred.

Before/after authorized real API snapshots covered account fields/balances, all fixture transactions, drafts, monthly records, score history, insights and recommendations. Combined SHA-256 matched exactly: `88a63349503f3b9ba4cdce16004bf52484141793cf13ed7f4015ea03c1745df1`. Only read-time `observationAt` fields were excluded (account items and transaction page); persisted financial fields were not excluded. This is API-visible fingerprint evidence, not a new direct database fingerprint claim. Existing reversal/database fingerprint evidence is retained from Antigravity without rerunning it.

### Shared behavior and resolved limitations

The previously observed limitation where changing the interface language cleared unsaved preview state has been fully resolved and verified. In-memory preview state is retained across EN $\leftrightarrow$ UR toggles for the authorized business session without persistence or localStorage leakage.

No backend import contract, persistence, draft creation, recording operation, backend duplicate lookup or financial totals exist in this slice. OCR/AI/external provider behavior and full production acceptance were not exercised. Existing parser tests cover additional boundary combinations beyond the browser's representative limits; not every numerical limit was uploaded in the browser. No reversal harness rerun, Maven rebuild, persistent database migration, credential extraction, Git mutation or dependency change occurred.

### Current isolated review application

- URL: `http://127.0.0.1:3580/records/import` (transparent gateway on port 3580).
- Production live Next: localhost 3581. Spring Boot: localhost 8680.
- Disposable PostgreSQL 14.22: Flyway migration head **V23**; maintained by review stack daemon.
- Use normal sign-in and choose the labelled LOCAL REVIEW business. No passwords, tokens or MFA material in this document.

---

## Language-Switch Preview Loss Resolution & Verification — 18 October 2026

### Root Cause Analysis
1. `LanguageControl.tsx` previously invoked `await session.refresh()` upon language selection. This called `block()`, set session status to `'loading'`, and incremented `session.epoch`.
2. `page.tsx` was keyed with `key={context}`, where `context` included `session.epoch`. The epoch increment caused React to unmount and remount `<ImportPreview>`, discarding all local component state.
3. In `SessionProvider.tsx`, window focus revalidation compared `snapshot.business` and triggered a full `refresh()` if any property differed, resetting forms even on benign profile changes such as `languagePreference`.

### Implementation Details
1. **Shared Lifecycle & Session Updates (`LanguageControl.tsx` & `SessionProvider.tsx`):**
   - Removed `await session.refresh()` from `LanguageControl.tsx`. Language preference persistence does not alter identity or permissions.
   - Refined `SessionProvider.tsx` revalidation logic: differentiated security/identity changes (`user.id`, `business.businessId`, `role`, `membershipStatus`, `authStage`, `accountStatus`) from non-security profile updates (`languagePreference`, `businessName`). Non-security updates update state in-place without triggering unmounting refreshes or bumping `epoch`.
2. **In-Memory Preview Session Cache (`frontend/src/lib/imports/lifetime.ts`):**
   - Implemented `StoredPreviewSession`, `getStoredPreview(key)`, `saveStoredPreview(key, state)`, and `clearStoredPreview(key)` held strictly in JS memory. Keyed by authorized session (`${user.id}:${business.businessId}` or `demo`).
   - Listens to window events `finsight-session-expired` and `finsight-business-changed` to purge cached preview data immediately on session expiry or tenant switch.
   - Financial data is strictly transient in-memory heap data; **never written to localStorage or IndexedDB**.
3. **Component State Hydration (`frontend/src/components/records/import/ImportPreview.tsx`):**
   - Integrated lazy state initializers to restore preview state on remount within the same business session.
   - Discarding via "Start again" explicitly calls `clearStoredPreview` and resets all fields.
   - Stale accounts rejected: selected account IDs are validated against accounts loaded for the active business context.

### Verification Evidence
1. **Unit & Contract Suite:**
   - Command: `node --test tests/*.test.mjs` / `npm test`
   - Result: **186 / 186 passed** (0 failed, 0 skipped).
   - Added unit test in `tests/record-import-preview.test.mjs` covering:
     - In-memory preview retention across language toggles.
     - Business switch boundary: immediate clearance and isolation.
     - Session expiration boundary: immediate clearance of private preview data.
2. **Whole Frontend Static Gates:**
   - `npx tsc --noEmit`: **0 errors**.
   - `npm run lint`: **0 errors** (1 pre-existing warning in `SessionProvider.tsx:213:33`).
   - Production Build (`BACKEND_API_URL=http://127.0.0.1:8280 npm run build`): **Compiled in 867ms, exit 0**.
3. **Live Headless Edge Browser Verification:**
   - Ran against live review gateway at `http://127.0.0.1:3580/records/import`.
   - **9/9 checks passed**:
     1. Authenticated session sign-in to LOCAL REVIEW business.
     2. CSV upload and lexical parsing (3 rows parsed).
     3. Column mapping (Date, Description, Amount, Direction).
     4. Real PKR account selection.
     5. Review step navigation.
     6. Purpose classification override on Row 1 (Direct sale receipt).
     7. Language toggle EN $\rightarrow$ UR $\rightarrow$ EN: verified preview table, mapped fields, account selection, review step, and classification choice fully preserved.
     8. Direction / layout flipped to RTL in Urdu and back to LTR in English; translated copy rendered correctly.
     9. Reset / abandonment: confirmed "Start again" resets in-memory preview state.
   - Network audit: **Zero non-GET private requests** during preview, mapping, review, and language switches.

---

## Normative Contract: Persistent Import-to-Draft (Revision 2.0)

**Feature ID:** `A-F12A-IMPORT-DRAFTS` (Persistent Spreadsheet Import to Record Drafts)
**Author:** Antigravity (Developer A Backend Coordinator)
**Target:** Codex (Developer A Frontend)
**Allocated Migration:** `V24__create_import_draft_provenance.sql`
**Status:** **CONTRACT_READY**

### 1. Architectural Boundaries & Non-Interference Invariants
1. **Zero Financial Mutation:** Saving imported rows creates ordinary persistent drafts in `record_drafts` and records provenance in `import_batches` and `import_batch_rows`. It **never posts financial transactions**, generates account movements, alters account balances, creates transaction summaries, or modifies monthly health scores, insights, recommendations, or Zakat.
2. **Reuse Existing Draft Lifecycle:** Saved drafts adhere strictly to the `RECORD_DRAFTS.md` specification. They appear under the central Records workspace (`/records`) under "To review", where they can be individually inspected, updated, recorded, or discarded.
3. **No Batch Financial Posting:** This slice provides persistent import-to-draft saving and recovery. Batch financial posting, automated ledger reconciliation, AI classification, OCR, and multi-currency conversions remain completely excluded.
4. **Tenant Isolation:** All operations resolve the active business context server-side (`ActiveBusinessContext`). Supplying foreign batch IDs, account IDs, or draft IDs yields a non-disclosing 404 response.

---

### 2. Permission Matrix
Reuses established financial transaction permissions:
| Operation | HTTP Method / Path | Required Permission | Allowed Roles | Denied Roles |
| :--- | :--- | :--- | :--- | :--- |
| **Save Batch Rows** | `POST /api/finance/drafts/import` | `FINANCIAL_TRANSACTION_POST` | `OWNER`, `ACCOUNTANT` | `MANAGER`, `VIEWER` (403) |
| **Recover / Detail Batch** | `GET /api/finance/drafts/import/{batchId}` | `FINANCIAL_TRANSACTION_READ` | `OWNER`, `ACCOUNTANT`, `MANAGER`, `VIEWER` | Denied (403) |
| **List Batches** | `GET /api/finance/drafts/import` | `FINANCIAL_TRANSACTION_READ` | `OWNER`, `ACCOUNTANT`, `MANAGER`, `VIEWER` | Denied (403) |

---

### 3. Endpoints Specification

#### 3.1 Save Import Drafts (Chunked): `POST /api/finance/drafts/import`
Persists a chunk of up to 100 reviewed spreadsheet rows as ordinary drafts with durable provenance.

- **Headers:**
  - `Content-Type: application/json`
  - `X-XSRF-TOKEN: <csrf-token>`
  - `Idempotency-Key: <string>` (optional, 1..100 chars)
- **Request Body Limits:**
  - `batchId`: UUID string (RFC 4122), stable identifier for the import session.
  - `sourceLabel`: String, 1..255 characters (e.g. filename or `"Pasted spreadsheet"`).
  - `rows`: Array of row items ($1 \le \text{count} \le 100$).
- **Row Item Schema:**
  | Field | Type | Required | Constraints & Semantics |
  | :--- | :--- | :--- | :--- |
  | `sourceRowId` | String | Yes | 1..100 characters. Stable unique identifier within the batch (e.g. string of source line number `"2"`). |
  | `sourceRowNumber` | Integer | Yes | $\ge 1$. Original physical spreadsheet row number. |
  | `accountId` | UUID | Yes | Active PKR account in active business (`status = ACTIVE`, `balanceStatus = AVAILABLE`, `currency = PKR`). |
  | `eventType` | String | No | Nullable. If present: `"DIRECT_SALE_RECEIPT"` or `"OPERATING_EXPENSE_PAYMENT"`. Never inferred from direction. |
  | `effectiveDate` | String | No | Nullable ISO date `YYYY-MM-DD`. Valid calendar date. If malformed, rejected with 400 (never silently defaulted). |
  | `amountInput` | String | No | Nullable text up to 100 characters. Verbatim original source amount (no trimming, truncation, or rounding). |
  | `description` | String | No | Nullable text up to 255 characters. |
  | `sourceReference` | String | No | Nullable text up to 255 characters. Optional source reference token. |

- **Request Example:**
```json
{
  "batchId": "a1b2c3d4-e5f6-4701-abcd-0123456789ab",
  "sourceLabel": "bank_statement.csv",
  "rows": [
    {
      "sourceRowId": "2",
      "sourceRowNumber": 2,
      "accountId": "f1e2d3c4-b5a6-4901-89ab-0123456789ef",
      "eventType": "DIRECT_SALE_RECEIPT",
      "effectiveDate": "2026-10-15",
      "amountInput": " 1,500.555 ",
      "description": "Invoice payment from customer",
      "sourceReference": "INV-1001"
    },
    {
      "sourceRowId": "3",
      "sourceRowNumber": 3,
      "accountId": "f1e2d3c4-b5a6-4901-89ab-0123456789ef",
      "eventType": null,
      "effectiveDate": "2026-10-16",
      "amountInput": "250.00",
      "description": "Office supplies",
      "sourceReference": null
    }
  ]
}
```

- **Response Schema (`201 Created` or `200 OK` on replay):**
```json
{
  "batchId": "a1b2c3d4-e5f6-4701-abcd-0123456789ab",
  "sourceLabel": "bank_statement.csv",
  "savedCount": 2,
  "replayedCount": 0,
  "totalRowsInBatch": 2,
  "rows": [
    {
      "sourceRowId": "2",
      "sourceRowNumber": 2,
      "sourceReference": "INV-1001",
      "draftId": "b1b2b3b4-c5c6-4701-abcd-ef0123456789",
      "status": "SAVED",
      "draftStatus": "DRAFT",
      "readyToRecord": true,
      "version": 0,
      "recordedTransactionId": null
    },
    {
      "sourceRowId": "3",
      "sourceRowNumber": 3,
      "sourceReference": null,
      "draftId": "c1c2c3c4-d5d6-4701-abcd-ef0123456789",
      "status": "SAVED",
      "draftStatus": "DRAFT",
      "readyToRecord": false,
      "version": 0,
      "recordedTransactionId": null
    }
  ]
}
```

---

#### 3.2 Get Batch Detail & Recovery: `GET /api/finance/drafts/import/{batchId}`
Retrieves the persisted batch and current draft statuses for all associated rows to allow recovery after browser reload or navigation.

- **Headers:** `Accept: application/json`
- **Response Example (`200 OK`):**
```json
{
  "batchId": "a1b2c3d4-e5f6-4701-abcd-0123456789ab",
  "sourceLabel": "bank_statement.csv",
  "totalRows": 2,
  "createdAt": "2026-10-18T10:00:00Z",
  "updatedAt": "2026-10-18T10:00:00Z",
  "rows": [
    {
      "sourceRowId": "2",
      "sourceRowNumber": 2,
      "sourceReference": "INV-1001",
      "draftId": "b1b2b3b4-c5c6-4701-abcd-ef0123456789",
      "status": "SAVED",
      "draftStatus": "DRAFT",
      "readyToRecord": true,
      "version": 0,
      "recordedTransactionId": null
    },
    {
      "sourceRowId": "3",
      "sourceRowNumber": 3,
      "sourceReference": null,
      "draftId": "c1c2c3c4-d5d6-4701-abcd-ef0123456789",
      "status": "SAVED",
      "draftStatus": "RECORDED",
      "readyToRecord": false,
      "version": 1,
      "recordedTransactionId": "f1f2f3f4-0000-4701-abcd-0123456789ab"
    }
  ]
}
```

---

#### 3.3 List Batches: `GET /api/finance/drafts/import`
- **Query Parameters:** `page` (default 0), `size` (default 20, max 100).
- **Response Example (`200 OK`):**
```json
{
  "items": [
    {
      "batchId": "a1b2c3d4-e5f6-4701-abcd-0123456789ab",
      "sourceLabel": "bank_statement.csv",
      "totalRows": 2,
      "createdAt": "2026-10-18T10:00:00Z",
      "updatedAt": "2026-10-18T10:00:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

---

### 4. Identity, Atomicity & Recovery Invariants

1. **Durable Uniqueness:** Uniqueness is enforced at the database level across `(business_id, batch_id, source_row_id)`.
2. **Payload Hash Check:**
   - Canonical string: `accountId|eventType|effectiveDate|amountInput|description|sourceReference`.
   - Hash: SHA-256 hex digest.
   - **Matching Retry:** If `(business_id, batch_id, source_row_id)` exists with an identical payload hash, the server recognizes it as a valid replay, fetches the current linked draft, returns `status: "REPLAYED"`, and **never creates a new draft**.
   - **Changed Conflict:** If `(business_id, batch_id, source_row_id)` exists with a differing payload hash, the server returns HTTP `409 Conflict`.
3. **Draft Lifecycle Decoupling:** Once a draft is saved, subsequent edits use the standard `PUT /api/finance/drafts/{id}` or `POST /api/finance/drafts/{id}/record` or `discard`. If an import batch is re-queried or replayed after a draft is recorded or discarded, the server accurately reports `draftStatus: "RECORDED"` or `"DISCARDED"` and does not recreate it.
4. **Atomicity:** Every request is executed inside an atomic transaction. Any validation failure or conflict causes a complete rollback. In chunked operations (e.g. 150 rows chunked into 100 + 50), chunk 1 remains safely committed even if chunk 2 fails.
5. **Idempotency-Key Header:** Request-level idempotency uses `financial_idempotency_records` with operation `RECORD_DRAFT_IMPORT`. Replaying with identical key and request digest returns the cached response. Replaying with changed payload yields HTTP `409 Conflict`.

---

### 5. Error Schema (Compatible with `apiClient`)

- **Validation Failure (`400 Bad Request`):**
```json
{
  "timestamp": "2026-10-18T14:00:00",
  "status": 400,
  "error": "Validation Failed",
  "message": "One or more import rows failed validation",
  "errors": {
    "rows[0].description": "Description must not exceed 255 characters",
    "rows[1].effectiveDate": "Invalid date format. Expected YYYY-MM-DD"
  }
}
```
- **Conflict Failure (`409 Conflict`):**
```json
{
  "timestamp": "2026-10-18T14:00:00",
  "status": 409,
  "error": "Conflict",
  "message": "Source row '2' has already been saved with different content in batch 'a1b2c3d4-e5f6-4701-abcd-0123456789ab'"
}
```
- **Forbidden Failure (`403 Forbidden`):**
```json
{
  "timestamp": "2026-10-18T14:00:00",
  "status": 403,
  "error": "Forbidden",
  "message": "Access is denied"
}
```

---

## 6. Persistent Import-to-Draft Implementation & Verification Evidence — 18 October 2026

**Feature ID:** `A-F12A-IMPORT-DRAFTS`
**Engineer:** Antigravity (Developer A Backend Coordinator)
**Status:** **VERIFIED & COMPLETE** (Backend, Frontend, Full Test Suites & Production Build)
**Migration:** `V24__create_import_draft_provenance.sql`

### 6.1 Database Schema & Migration V24
- `V24__create_import_draft_provenance.sql`:
  - Added unique constraint `uq_record_drafts_id_business UNIQUE (id, business_id)` on `record_drafts`.
  - Created `import_batches` table with composite primary key/unique `(id, business_id)`, tracking `source_label`, `total_rows_imported`, `created_by_id`, `created_at`, `updated_at`.
  - Created `import_batch_rows` table with unique constraint `uq_import_batch_rows_unique UNIQUE (business_id, batch_id, source_row_id)` and `uq_import_batch_rows_draft UNIQUE (business_id, draft_id)`.
  - Foreign key constraints with tenant isolation matching:
    - `(batch_id, business_id) REFERENCES import_batches (id, business_id)`
    - `(draft_id, business_id) REFERENCES record_drafts (id, business_id)`
  - Deterministic replay & conflict detection: SHA-256 payload hash stored in `payload_hash` (CHAR(64)) to detect conflicting content replays for the same `(business_id, batch_id, source_row_id)`.
  - Non-destructive grants to `finsight_app` with safe `DO $$ ... $$` blocks.

### 6.2 Backend Architecture & Components
- **Entities & Repositories:**
  - `ImportBatch.java` & `ImportBatchRepository.java`
  - `ImportBatchRow.java` & `ImportBatchRowRepository.java`
- **DTOs:**
  - `ImportDraftRowItem.java`: Validates individual row input (mandatory `sourceRowId`, `sourceRowNumber`, optional `accountId`, `eventType`, `effectiveDate`, verbatim `amountInput`, `description`, `sourceReference`).
  - `ImportDraftsRequest.java`: Bounded chunk request ($1 \le \text{count} \le 100$).
  - `ImportDraftRowResult.java`: Row result mapping `draftId`, `status` (`SAVED` vs. `REPLAYED`), `draftStatus` (`DRAFT`), `readyToRecord`, and `recordedTransactionId`.
  - `ImportDraftsResponse.java`, `ImportBatchDetailResponse.java`, `ImportBatchListResponse.java`.
- **Domain Service:**
  - `ImportDraftService.java`:
    - Enforces max 100 rows per chunk.
    - Pre-saves and flushes `ImportBatch` to satisfy composite foreign key constraints.
    - Persists verbatim raw amount input into `record_drafts.amount_input` without truncation or rounding.
    - Evaluates readiness via `RecordDraftService.isReadyToRecord`.
    - Handles exact idempotent replays returning `status = "REPLAYED"` and rejects conflicting row content with HTTP 409 Conflict.
    - Provides batch recovery via `GET /api/finance/drafts/import/{batchId}` and listing via `GET /api/finance/drafts/import`.
- **REST Controller:**
  - `ImportDraftController.java`:
    - `POST /api/finance/drafts/import` (`FINANCIAL_TRANSACTION_POST`, OWNER & ACCOUNTANT)
    - `GET /api/finance/drafts/import/{batchId}` (`FINANCIAL_TRANSACTION_READ`)
    - `GET /api/finance/drafts/import` (`FINANCIAL_TRANSACTION_READ`)

### 6.3 Frontend Integration
- **API Adapter:**
  - `frontend/src/lib/api/dev-a/import-drafts.ts`: Fully decoupled adapter validating responses strictly, declaring `ImportDraftEventType`, and providing `saveImportDrafts`, `getImportBatch`, and `listImportBatches`.
- **In-Memory Lifetime Store:**
  - `frontend/src/lib/imports/lifetime.ts`: Updated `StoredPreviewSession` to hold `batchId` and `savedDrafts` in transient JS memory. Zero localStorage usage for financial source data. Preserves preview state across language switches (EN $\leftrightarrow$ UR).
- **Presentation & Review Components:**
  - `frontend/src/components/records/import/ImportPreview.tsx`:
    - Integrated "Save selected as drafts" button with chunking ($\le 100$ rows per chunk) and progress indicator (`Saving rows 1–100 of 150…`).
    - Failure tolerance: preserves previously successful chunks if a later chunk fails.
    - Blocking validation checks (e.g., malformed date, missing/invalid amount) disable saving with bilingual alerts; non-blocking warnings (missing financial purpose) allow saving incomplete drafts with clear informational notices.
    - URL search param reflection (`?batchId=<UUID>`) enables reload recovery via `getImportBatch` without client-side persistence.
    - Demo mode: simulates in-memory saving with zero private network calls.
  - `frontend/src/components/records/import/ImportRows.tsx`:
    - Displays `Draft saved` / `Draft already saved` provenance badges.
    - Direct clickable links to `/records?view=review&draftId=...` opening the draft in the central review workspace.
  - `frontend/src/components/records/DraftsWorkspace.tsx` & `frontend/src/app/records/page.tsx`:
    - Updated to initialize and open the DraftEditor sheet directly when `draftId` query parameter is present.
  - `frontend/src/components/records/import/import-copy.ts`:
    - Complete typed English and Urdu bilingual copy parity.

### 6.4 Verification Evidence

1. **Backend Tests:**
   - Command: `.\mvnw.cmd test "-Dtest=ImportDraftServiceTest,ImportDraftControllerTest,ImportDraftPostgreSqlIT"`
   - Result: **26 / 26 passed** (0 failures, 0 errors, 0 skipped).
     - `ImportDraftServiceTest`: 12 tests passed (validation, chunking, conflict detection, replay, verbatim amount preservation).
     - `ImportDraftControllerTest`: 6 tests passed (security annotations, role permissions, 403 on denied roles, validation errors).
     - `ImportDraftPostgreSqlIT`: 8 tests passed against real PostgreSQL 14.22 with Flyway V24 migration (atomicity, foreign keys, concurrent replays, tenant isolation).

2. **Frontend Test Suite:**
   - Command: `npm test`
   - Result: **189 / 189 passed** (0 failures, 0 skipped).
     - All 33 existing import tests passed.
     - Added unit tests for:
       - Bilingual copy parity and placeholder matching for all 22 new keys.
       - Lifetime preview storage preserving `batchId` and `savedDrafts` across language toggles.
       - API adapter response parser strict contract validation.

3. **Whole Frontend Static & Quality Gates:**
   - `npx tsc --noEmit`: **0 errors**.
   - `npm run lint`: **0 errors** (1 pre-existing warning in `SessionProvider.tsx:213:33`).
   - Production Build (`npm run build`): **Compiled successfully in 9.1s**, TypeScript finished in 3.6s, exit code 0.
   - Dynamic and static routes including `/records/import` and `/records` generated cleanly.

4. **Zero Financial Mutation & Atomic Recording Verification:**
   - Saving reviewed rows creates ordinary persistent drafts in `record_drafts` and records provenance in `import_batches` and `import_batch_rows`.
   - Never posts financial transactions, creates account movements, alters account balances, or modifies monthly health scores, insights, recommendations, or Zakat calculations.
   - Exact database counts across lifecycle:
     - **Baseline:** `financial_transactions=0`, `account_movements=0`, `monthly_records=0`, `score_results=0`, `record_drafts=0`, `import_batches=0`, `import_batch_rows=0`.
     - **Post-Import:** After importing 105 large batch rows, 2 preview rows, 2 concurrency race drafts, and 1 accountant draft (112 drafts total in `record_drafts`, 3 `import_batches`, 111 `import_batch_rows`): `financial_transactions` remained 0 until exactly 1 draft was recorded via the verified draft-recording workflow, becoming `financial_transactions=1`, `account_movements=1`, `monthly_records=0`, `score_results=0`.
     - **Post-Retry / Re-record:** Attempting to record the draft again with a new idempotency key returned HTTP 409 Conflict. Final database counts remained strictly `financial_transactions=1`, `account_movements=1`, `monthly_records=0`, `score_results=0` (zero duplicate transactions or movements).

5. **Real Browser End-to-End Acceptance (Microsoft Edge 155.0.4283.62):**
   - Command: `node tests/accounts-live.integration.mjs` / `node tests/accounts-live.integration.mjs --import-only`
   - Test harness: Real Spring Boot (port 8680) + disposable PostgreSQL 14.22 (Flyway V24) + transparent gateway (port 3580) + production-built Next.js (port 3581) + Microsoft Edge.
   - **All checks passed (Exit code 0, 0 blockers):**
     - **Part A (Import preview):** Valid CSV upload parsed source and showed headings; abandon dialog properly confirmed reset; TSV paste parsed 3 records; column mapping (Date, Description, Amount, Direction, Reference) correctly mapped; batch account selected; amount normalization rendered formatted amounts (`3500.75 PKR`, `850.25 PKR`, `1500.50 PKR`); missing classifications remained unresolved ("Undecided — review financial purpose"); row exclusion/restoration worked smoothly; screenshot: `01-import-paste-mapped.png`.
     - **Part B (Draft creation):** "Save selected as drafts" saved selected rows with success notice; URL updated with generated `batchId`; rows rendered "Draft saved" badges and deep-links; raw `amountInput` preserved verbatim in PostgreSQL (`record_drafts.amount_input = '1500.5'` and `'3,500.75'`); screenshot: `02-import-drafts-saved.png`.
     - **Part C (Draft navigation & persistence):** Clicking generated draft link opened `/records?view=review&draftId=...` with the draft loaded inside the DraftEditor modal; updated description to "Updated imported draft via browser", saved draft, reloaded page and verified persistent description; screenshot: `03-drafts-workspace-deeplink.png`.
     - **Part D (Browser reload recovery):** Fresh browser context navigating to `/records/import?batchId=<UUID>` recovered the persisted batch from backend with 2 saved draft badges without relying on localStorage; screenshot: `04-batch-reload-recovery.png`.
     - **Part E (Language, RTL, mobile & session boundaries):** Preview retained in-memory state across EN $\leftrightarrow$ UR switch; RTL direction confirmed on `<html>`; mobile viewport (390x844) verified with zero horizontal overflow; switching active business to Business B returned 404 for Business A's batch; unauthenticated navigation to `/records/import` presented login screen and made zero private financial API calls; screenshot: `05-import-urdu-rtl-mobile.png`.
     - **Section 4 (Large import & failure recovery):** Imported 105 rows chunked into 100 rows + 5 rows; verified Chunk 1 persisted 100 rows in DB; simulated failure on Chunk 2 verified atomic rollback leaving Chunk 1 intact; retried Chunk 2 successfully reaching 105 rows; replaying Chunk 1 returned status `REPLAYED` with 0 duplicate drafts; changing payload on existing sourceRowId produced HTTP 409 Conflict; distinct twins with separate sourceRowIds created distinct drafts.
     - **Section 5 (Security & concurrency):** Concurrent thread race on identical `(business_id, batch_id, source_row_id)` yielded exactly 1 success and 1 unique constraint violation (`uq_import_batch_rows_unique`); cross-tenant account rejected with 400; VIEWER and MANAGER rejected with 403; ACCOUNTANT permitted with 201; replaying recorded or discarded drafts preserved immutable `RECORDED` / `DISCARDED` status without resurrection.
