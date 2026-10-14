# Codex handoff — A-W1-01-FE-READ

Date: 14 October 2026 (Asia/Karachi)

Status: **FRONTEND_VERIFIED_BY_ISOLATED_CHECKS — PAUSED**

This is a frontend implementation with controlled API tests, not an integrated, accepted or production-ready feature. No financial policy or backend permission grant is approved by this handoff.

## Checkout and specification

- Repository: `C:\Users\PC\Projects\sme-financial-webapp`
- Branch: `dev/suleman`
- HEAD: `b51e27696cccc59b94aa9d9c9388ad0faa5b4176`
- Contract: `ACCOUNTS_API_DRAFT.md` v1.2, `READY_FOR_BOUNDED_FRONTEND_DEV`.
- Read root/frontend instructions v1.1, implementation state v1.2 and Antigravity W0-04 handoff, which confirms documentation completion and PAUSED.
- Read grants remain `RECOMMENDED_PENDING_TEAM_REVIEW`. V18 was `UNRESERVED` in the supplied state. No reservation or grant approval is inferred from contributor files appearing during this task.

## Exact files created by Codex

1. `C:\Users\PC\Projects\sme-financial-webapp\frontend\src\app\finance\accounts\page.tsx`
2. `C:\Users\PC\Projects\sme-financial-webapp\frontend\src\components\finance\accounts\AccountsList.tsx`
3. `C:\Users\PC\Projects\sme-financial-webapp\frontend\src\components\finance\accounts\AccountDetail.tsx`
4. `C:\Users\PC\Projects\sme-financial-webapp\frontend\src\components\finance\accounts\AccountMoneyValue.tsx`
5. `C:\Users\PC\Projects\sme-financial-webapp\frontend\src\components\finance\accounts\account-copy.ts`
6. `C:\Users\PC\Projects\sme-financial-webapp\frontend\src\lib\api\dev-a\accounts.ts`
7. `C:\Users\PC\Projects\sme-financial-webapp\frontend\src\lib\api\dev-a\account-contracts.ts`
8. `C:\Users\PC\Projects\sme-financial-webapp\frontend\src\lib\utils\decimal-display.ts`
9. `C:\Users\PC\Projects\sme-financial-webapp\frontend\tests\accounts-read.test.mjs`
10. `C:\Users\PC\Projects\sme-financial-webapp\docs\phase2\handoffs\CODEX_W1_01_FE_READ.md`

No existing source, instructions, contracts, state records, environment files, dependency manifests, lockfiles, navigation files or browser scripts were edited by Codex. No Git mutations, production build, application service or database operations were performed. `backend/.cp.txt` was not opened or changed.

## Implemented behavior

- Direct `/finance/accounts` route, with no global navigation additions.
- Account cards with name/type/status/currency, exact current balance, unavailability reason, effective cutoff in Asia/Karachi and UTC snapshot observation.
- ACTIVE/INACTIVE/ARCHIVED filter, optional type/currency filters, page size and pagination. Optional unfiltered choices omit their query parameter; no invented ALL enum. Filter/page-size changes reset page to zero. Empty and out-of-range results remain distinct, with first-page recovery when applicable.
- Detail fetched on selection in the existing Sheet. Shows opening/current amounts and dates, institution, already-masked identifier and permitted notes; no invented evidence links.
- Feature-local typed English/Urdu copy, using existing language provider and RTL direction. Numeric/currency/date text uses Western digits and bidi isolation. Cards and detail content wrap rather than requiring a wide table.
- Feature-local Sheet enhancement: localized accessible dialog/close names, focus containment, background inertness, Escape behavior through the shared Sheet, focus restoration and bounded scrolling. The common Sheet was not modified. Browser verification of these behaviors remains pending.
- Existing apiClient retained for cookie/no-store transport and generational request invalidation. Adapter exposes only the two GET reads, carries AbortSignal, and sends no financial tenant identity. Detail unmount/selection cleanup aborts old requests; list results are keyed to the current query to avoid showing old filter data.
- Structurally invalid responses fail with a safe visible message. List endpoint 404 is a service error, not an empty business. Detail 404 is non-disclosing account-not-found. Connection, forbidden and session/business failures remain errors; no automatic mutation replay or silent mock fallback.
- Explicit demo mode displays a bilingual accounts-preview-unavailable state without mounting account resource loaders or requesting private APIs.

## Wire and money validation

- Validates fields, enums, UUID-shaped IDs, dates/timestamps, Karachi cutoff consistency, required nullable values, detail identity, already-masked identifiers, pagination/filter/order/snapshot invariants and duplicate list IDs. Unexpected fields are discarded.
- AVAILABLE requires an exact string current balance and null reason. UNAVAILABLE requires null current balance and a known reason. No RECONCILING enum.
- Amounts remain strings; validation/formatting never converts monetary amounts to Number or performs balance, FX, score or aggregate calculations.
- Supported currency metadata must match v1.2 scales. Exact scale, 12 integer digits, canonical sign/zero, no leading zeros/plus/exponents and no rounding of invalid values are enforced.
- Unsupported currency metadata is accepted only with explicit UNAVAILABLE/UNSUPPORTED_CURRENCY. Its opening amount is checked for canonical decimal syntax but is never formatted using untrusted scale; the UI shows unavailable. Malformed structural fields still reject the response.
- A future opening date with AVAILABLE current balance must contain the canonical zero supplied by the server; the client does not calculate that balance.

## Executed verification

Working directory: `C:\Users\PC\Projects\sme-financial-webapp\frontend`.

| Command | Result |
| --- | --- |
| `node --test tests/accounts-read.test.mjs` | Exit 0; 20 passed, 0 failed/skipped. |
| `pnpm test` with process-only auto-install suppression below | Final exit 0; 56 passed, 0 failed/skipped (20 new + 36 existing). |
| `pnpm exec tsc --noEmit --incremental false` with suppression | Final exit 0. No incremental build file requested. |
| `pnpm run lint` with suppression | Final exit 0; 0 errors, one existing SessionProvider effect-cleanup ref warning at line 204. No warning in new files. |

All new API tests mock fetch with controlled fixtures and transpile actual adapters into an owned temporary directory, following the existing Node test approach. They cover exact boundaries/currencies/negative values, malformed decimals, query serialization, AVAILABLE/UNAVAILABLE invariants, unsupported metadata, dates, invalid fields, masking, pagination/filter/snapshot validation, detail identity, HTTP/network errors, abort propagation and late response rejection after business-context invalidation. Temporary test files are cleaned up. No test framework or dependency was added.

### Tooling incident and mitigation

The first `pnpm exec tsc` and `pnpm run lint` attempts unexpectedly invoked pnpm 11.25.0's default dependency synchronization before the checks. Output reported lockfile verification/Already up to date, registry lookups and ignored node_modules bin-generation warnings. Both attempts were interrupted; one had reported synchronization completion before interruption. Ignored node_modules/shims may have been refreshed, so dependency installation state cannot be claimed untouched. No tracked frontend manifest or lockfile change was reported.

The installed pnpm source showed `verify-deps-before-run` defaults to `install`. Every subsequent pnpm check explicitly used the process-only setting:

```powershell
$env:pnpm_config_verify_deps_before_run='false'
```

These later invocations performed the requested checks without the bootstrap output. No configuration/environment file was changed, and no dependency upgrade or recovery installation was requested. Preserve this setting for the next existing-dependency verification window.

## Concurrent contributor state

Initial status had no tracked modifications. During verification, unrelated backend changes appeared:

- `backend/src/main/java/com/app/sme_health_backend/identity/model/BusinessPermission.java`
- `backend/src/main/java/com/app/sme_health_backend/identity/service/BusinessAuthorizationService.java`
- `backend/src/main/java/com/app/sme_health_backend/shared/exception/GlobalExceptionHandler.java` (appeared by final check)
- New `backend/src/main/java/com/app/sme_health_backend/accounting/` files
- New `backend/src/test/java/com/app/sme_health_backend/accounting/` files (appeared by final check)
- New `backend/src/main/resources/db/migration/V18__create_financial_accounts.sql`

These are contributor work, preserved without edits, revert, staging or approval inference. Antigravity should reconcile its pause/current-state record at its next checkpoint. Branch and HEAD remained unchanged.

## Remaining checks and cross-owner notes

- Backend endpoints were unavailable at task start according to W0-04 and have not been independently verified live. Later backend files do not establish a running API or permission approval. **Live integration remains unavailable/unverified.**
- No production build or browser server ran. Real Sheet focus restoration/trapping, touch/mobile overflow, Urdu font layout, keyboard journeys and browser cancellation need actual browser evidence.
- Live checks must cover GET list/detail, server permission/tenant enforcement, logout/session expiry, business switching with requests in flight, newer detail snapshots, filtering/pagination and persistence on disposable agreed data.
- v1.2 has complete examples but omits the prior normative field matrix. Nullable institution/identifier/notes and established length/masking constraints are enforced defensively; request a revised contract before relaxing required fields or introducing additional mask formats. The unsupported-metadata exception deliberately exposes no formatted opening value.
- The W0-04 handoff references `ERROR_INVALID_UUID` in field errors while the actual v1.2 wire envelope uses `Validation Failed` with a field message. Implementation follows the actual wire envelope; no transport changes were made.
- No capability resolver/module key exists in this slice. No module-disabled behavior is claimed. Read-only UI does not prove server authorization.
- Central translation registration and global navigation remain deferred to a reserved shared-file window with Fatima.

Codex is **PAUSED**. Changed paths are exactly the ten created files above. No staging, commit or push was performed.
