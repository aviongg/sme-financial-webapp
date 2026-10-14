# Accounts v1.3 frontend retest

- Task: `A-W1-01-FE-V13-RETEST`
- Date: 2026-10-14 (Asia/Karachi)
- Status: **INTEGRATION_VERIFIED — PAUSED**, bounded accounts list/detail only.
- Branch: `dev/suleman`
- HEAD: `b51e27696cccc59b94aa9d9c9388ad0faa5b4176`
- Contract: `ACCOUNTS_API_DRAFT.md` v1.3; remains a draft. Backend nullable-opening handoff explicitly reports complete and PAUSED. No financial-policy approval is implied.

## Exact changed paths in this task

1. `frontend/src/lib/api/dev-a/account-contracts.ts`
2. `frontend/src/components/finance/accounts/AccountDetail.tsx`
3. `frontend/tests/accounts-read.test.mjs`
4. `frontend/tests/accounts-live.integration.mjs`
5. `docs/phase2/handoffs/CODEX_W1_01_V13_RETEST.md`

The discriminated wire type and validator require exact non-null amounts for AVAILABLE. Precision/capacity opening failures require null opening/current and valid currency metadata. Unsupported currency/scale requires null opening/current and inconsistent/unsupported metadata. Current-only INTEGRITY_CHECK_FAILED preserves a valid exact opening and null current. Missing, malformed or invalid null combinations reject safely. Valid future openings retain their exact opening and canonical-zero current. Detail passes the nullable opening directly to the existing bilingual unavailable renderer; no zero substitution or amount-to-Number conversion.

## Fresh checks

All pnpm invocations used process-only `pnpm_config_verify_deps_before_run=false` to disable dependency synchronization. No installation was requested.

| Command (frontend working directory) | Result |
| --- | --- |
| `pnpm exec node --test tests/accounts-read.test.mjs` | Exit 0; 24 passed |
| `pnpm test` | Exit 0; 60 passed, no skips (24 accounts + 36 existing) |
| `pnpm exec tsc --noEmit --incremental false` | Exit 0 |
| `pnpm run lint` | Exit 0; existing SessionProvider.tsx:204 warning only |
| `pnpm run build` with live mode and backend URL port 8180 | Exit 0 |
| `node tests/accounts-live.integration.mjs` | Exit 0; 12 grouped real-integration checks; no blockers |
| `pnpm run build` with demo mode | Exit 0 |
| `node tests/accounts-live.integration.mjs --demo` | Exit 0; EN/UR preview and zero private API requests |
| `pnpm run build` with live mode restored | Exit 0 |

An initial lint run caught a new test variable named `module`; it was renamed within the authorized test file and lint/full tests rerun successfully. Builds retain the existing Next middleware deprecation notice.

## Real integration evidence

The existing harness launched task-owned Spring on 8180, Next production on 3180 and a disposable embedded PostgreSQL 14.22 cluster migrated through V18. It used existing compiled backend/test classes and normal registration, cookie session, CSRF and active-business APIs; no security controls were disabled. Synthetic accounts and memberships were inserted only into that disposable cluster through its established isolated fixture. Edge ran against actual Spring responses, not synthetic API browser fixtures.

- **Original defect: PKR stored scale 3 — PASS.** Real detail returns UNAVAILABLE / UNSUPPORTED_CURRENCY with openingBalance=null and currentBalance=null; stored metadata is not silently repaired.
- **Original defect: opening excess precision 1.0001 — PASS.** Real detail returns UNAVAILABLE / PRECISION_OVERFLOW with both balances null.
- A persisted valid INACTIVE account and both unavailable accounts coexist in one rendered list; both invalid accounts' detail Sheets show unavailable opening/current rather than zero.
- Valid future opening shows planned PKR 500.00 and AVAILABLE current PKR 0.00. PKR negative, maximum JPY and three-decimal BHD display exactly.
- Normal sign-in, direct route, persisted detail, masked identifiers, filters/reset/pagination/empty/out-of-range, Viewer/Manager real reads and cross-business API 404 passed.
- English/Urdu, RTL, desktop/mobile bounds, Sheet focus/keyboard, close/change stale detail response, cross-tab business switch and actual JDBC session expiry passed.
- Stopping only task-owned Spring produced a visible error without mock/empty fallback. Demo made zero private API requests.

Fresh machine-readable evidence and screenshots are in ignored `frontend/test-artifacts/accounts-live/` (`results.json` has integrated=true, no blockers; `demo-results.json` records the separate demo check). No credentials or secrets are recorded here.

Backend Maven was **not rerun** in this task. The existing Surefire XML was inspected: FinancialAccountPostgreSqlIT records 10 tests, zero failures/errors/skips, timestamp 2026-10-14 15:24:09 local. This is Antigravity's prior runner evidence, distinct from this task's fresh actual PostgreSQL/browser run. The standalone live harness is explicitly invoked; it is not part of top-level `tests/*.test.mjs` discovery.

## Limits and cleanup

No remaining blocker for the bounded v1.3 accounts-read integration was observed. Backend balances remain **opening-only**; transaction-derived totals, posting, creation, transfers, reversals and cutover editing are outside acceptance. Current-only integrity failure is covered by isolated validator tests; the opening-only backend does not provide a real movement/reconciliation path to exercise it live. MFA enrollment/challenge, production PostgreSQL/TLS, full accessibility certification and production readiness were not established by this retest. Existing authorization journeys exercised the implemented backend grants; this does not approve a broader role policy or financial policies.

Only task-owned processes were stopped and their disposable database removed. Ports 3180/8180 were free after cleanup. The final build is live. No backend, migration, contract, shared auth/transport/navigation, manifest, lockfile or environment file was edited. Existing teammate changes and backend/.cp.txt were preserved. No Git mutations occurred.

**PAUSED.** This verifies bounded accounts-read integration, not full F01 completion or production acceptance.
