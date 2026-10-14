# A-W1-02-FE-CREATE frontend handoff

- Date: 2026-10-14 (Asia/Karachi)
- Current status: **INTEGRATION_VERIFIED — PAUSED** (A-W1-02-INTEGRATION; original isolated implementation evidence is retained below)
- Checkout: `C:\Users\PC\Projects\sme-financial-webapp`
- Branch: `dev/suleman`
- Starting and final observed HEAD: `8070af595ed48a8b88fe6fc67d05ac0fc86cd97b`
- Consumed contract: `docs/phase2/contracts/ACCOUNT_CREATE_V1.md`, **v1.0**, dated 2026-10-14; response uses Accounts Read v1.3.
- Readiness evidence: `docs/phase2/handoffs/ACCOUNT_CREATE_CONTRACT_READY.md` explicitly declared CONTRACT_READY / stable specification published before adapter wiring. It was initially absent; independent form work preceded the single readiness recheck that found it ready.

## Exact changed paths (Codex)

1. `frontend/src/components/finance/accounts/AccountsList.tsx`
2. `frontend/src/components/finance/accounts/account-copy.ts`
3. `frontend/src/lib/api/dev-a/accounts.ts`
4. `frontend/src/components/finance/accounts/CreateAccountForm.tsx` (new)
5. `frontend/src/lib/api/dev-a/account-create-contracts.ts` (new)
6. `frontend/tests/accounts-create.test.mjs` (new)
7. `docs/phase2/handoffs/CODEX_W1_02_FE_CREATE.md` (new)

## Implemented behavior

- Feature-local inline Add account form, presented for active OWNER/ACCOUNTANT memberships using existing SessionProvider business role. MANAGER/VIEWER read behavior is preserved; backend authorization remains decisive.
- Required account name, CASH/BANK/WALLET, supported currency, exact string opening amount and explicit calendar date. Optional institution/notes are deliberately omitted from this bounded UI pending the response-limit discrepancy below. Raw account identifiers are never collected or sent.
- English/Urdu copy explains held/owed opening funds, no sales/revenue/monthly-record effect and future effective dates. Amount input is text with Western-digit validation, LTR isolation and no Number conversion. Exact full currency scale and maximum 12 integer digits are required; no rounding/padding/coercion. CASH disallows negatives; BANK/WALLET allow them.
- Existing Card/Input/Select/Button and language/session providers reused. Form has accessible labels/help/errors, focus on opening and invalid fields, responsive grid, pending field disablement and focus restoration. Browser/keyboard behavior has not yet been exercised live.
- POST `/api/finance/accounts` reuses existing apiClient cookies/CSRF, no-store and AbortSignal/generation rejection. Payload explicitly allowlists five fields and trims account name. No businessId, role, status, currencyDecimals or accountIdentifier is accepted into the request.
- Returned detail is validated against v1.3 plus ACTIVE/AVAILABLE and submitted-field consistency; no optimistic balances or fabricated response. Success closes/resets the form, uses the returned account name for confirmation, requests the real list again with existing filters and page reset to zero, and explains that filters can hide the new account.
- Memory-only logical submission generates one opaque UUID key. Synchronous in-flight locking prevents double submission. Network/5xx/malformed success/unknown conflict outcomes remain uncertain: fields/cancel are locked and only explicit retry of the identical frozen payload with the original key is allowed. No automatic mutation replay and no localStorage payloads.
- Known duplicate-name and validation errors preserve inputs and permit correction with a new logical key. Same-key/different-payload conflict locks further attempts; denial/session changes do not silently replay. Session/business/role/membership context teardown aborts and invalidates submission state, discarding late success.
- Demo route remains the existing bilingual preview-unavailable route and does not mount this form or make a creation request.

## Fresh verification

All commands ran in `frontend/` with process-only `pnpm_config_verify_deps_before_run=false`; existing dependencies only.

| Command | Fresh result |
| --- | --- |
| `pnpm exec node --test tests/accounts-create.test.mjs` | Exit 0; 22 passed, zero failures/skips |
| `pnpm test` | Exit 0; 82 passed (22 new creation + 60 existing), zero failures/skips |
| `pnpm exec tsc --noEmit --incremental false` | Exit 0 |
| `pnpm run lint` | Exit 0; only the existing SessionProvider.tsx:204 exhaustive-deps warning |
| `git diff --check -- frontend` | Exit 0; Git separately reports ordinary LF/CRLF conversion notices |

Tests transpile actual modules using the existing Node test approach. Controlled fixtures cover scale/capacity/sign/date validation, request allowlisting, actual adapter cookie/CSRF/header behavior, response validation, duplicate and field errors, role presentation, double clicks, uncertain-outcome retry/key/payload preservation, conflicts, denial, abort and stale-generation rejection. Actual form components are server-rendered for EN/UR labelled text inputs and locked retry controls. These are isolated tests using synthetic fixtures, not real POST or browser integration evidence. Top-level `tests/*.test.mjs` discovers the new file without configuration changes.

## Contract clarifications and next integration dependencies

1. **CSRF header wording:** creation v1.0 sections 2.1/3.2 and its readiness handoff say `X-CSRF-TOKEN`; the established apiClient bootstraps and sends `X-XSRF-TOKEN`. The user requires preserving existing transport. Frontend therefore uses that existing header unchanged. Antigravity must confirm POST accepts the existing configured header and reconcile the specification wording before live acceptance; this frontend task does not modify the contract or transport.
2. **Optional response limits:** creation v1.0 permits institutionName 150 and notes 1000, while the v1.3 read consumer validates 100 and 500. The UI avoids that ambiguity by omitting both optional inputs. Do not weaken existing read validation or truncate data to hide it. Align producer/read limits before exposing those optional fields.
3. The UI deliberately requires an explicit opening date and exact full-scale decimal text, which are a stricter accepted subset of v1.0's optional date and at-most-scale request rules. No timezone-dependent client default or amount normalization is invented.
4. Live creation acceptance awaits Antigravity's completed POST/persistence/idempotency implementation and a coordinated real backend/browser task. Validate actual success/replay, durable idempotency, duplicate names, authorization/demotion, business switches and uncertain outcomes there. Existing backend changes observed in shared status belong to Antigravity and were not edited by Codex.

No builds, browser services, backend tests, migrations or database operations ran in this task. The existing live harness and accounts-read tests were not edited. No backend, migration, contract, shared-state, shared auth/transport/navigation/translation registry, dependency, manifest, lockfile or environment file was changed by Codex. backend/.cp.txt and teammate work were preserved. No Git mutations occurred.

This is frontend isolated verification, not INTEGRATED, accepted financial policy, full F01 completion or production readiness. **PAUSED.**

## Integration — A-W1-02-INTEGRATION (2026-10-14)

**INTEGRATION_VERIFIED — PAUSED**, bounded creation plus preserved accounts reads. Branch/HEAD remained `dev/suleman` / `8070af595ed48a8b88fe6fc67d05ac0fc86cd97b`.

### Changed paths in this integration task

- `frontend/src/lib/api/dev-a/account-create-contracts.ts`: recognize the actual backend's quoted-key idempotency conflict as a terminal conflict; unknown conflicts remain uncertain. No financial validation was weakened.
- `frontend/tests/accounts-create.test.mjs`: regression for that real message variant.
- `frontend/tests/accounts-live.integration.mjs`: combined V19 HTTP/browser creation acceptance, preserving all read journeys, plus failure screenshots/stack diagnostics and form-scoped selectors.
- `docs/phase2/handoffs/CODEX_W1_02_FE_CREATE.md`: this section/status.

### Parity and documentation correction

POST path, five-field frontend request subset, 201 detail/decimal/null invariants and validation envelope matched the actual controller/service. The producer's key-conflict message includes `Idempotency key '<key>' ...`; the frontend now recognizes that precise variant without displaying raw service text.

**CSRF is documentation-only:** `SecurityConfig.java:90–101` uses CookieCsrfTokenRepository and its established `X-XSRF-TOKEN` header; apiClient validates the bootstrap header at client.ts:52 and sends it at :79–80. Real missing/invalid tokens returned 403; valid `X-XSRF-TOKEN` succeeded. Antigravity should replace `X-CSRF-TOKEN` with `X-XSRF-TOKEN` in ACCOUNT_CREATE_V1.md sections 2.1/3.2 and ACCOUNT_CREATE_CONTRACT_READY.md security/header examples. No transport, security or contract file was changed here. Optional institution/notes limit alignment remains a documentation/interface follow-up before exposing those fields; this UI omits them.

### Fresh evidence

Task-owned Spring 8180, production Next 3180, Edge, disposable PostgreSQL **14.22**, migrations through **V19**. Standard registration/login, real cookie sessions, CSRF and active-business handling were used. SQL controls were confined to the disposable fixture (membership roles, snapshots and session expiry). No production data or security bypass.

| Check | Fresh result |
| --- | --- |
| Targeted `pnpm exec node --test tests/accounts-create.test.mjs` | Exit 0; 23 passed |
| Full `pnpm test` | Exit 0; 83 passed, zero skipped |
| `pnpm exec tsc --noEmit --incremental false` | Exit 0 |
| `pnpm run lint` (including final harness) | Exit 0; existing SessionProvider:204 warning only |
| Live/demo/final restored-live `pnpm run build` | All exit 0; existing middleware deprecation notice |
| `node tests/accounts-live.integration.mjs` | Final exit 0; 21 grouped checks; blockers=[]; integrated=true |
| `node tests/accounts-live.integration.mjs --demo` | Exit 0; EN/UR preview, zero private API requests including creation |

Every pnpm command used process-only `pnpm_config_verify_deps_before_run=false`. Two earlier combined attempts exited 1 on newly added harness selectors: a global form-focus selector hit the header search form, then an exact success-text selector omitted the appended account name. Both were corrected; the complete combined harness subsequently passed. No backend defect was concealed or frontend response check weakened.

**Real HTTP assertions:** OWNER/ACCOUNTANT creation; MANAGER/VIEWER denial; missing/invalid/valid CSRF; signed PKR/BHD BANK/WALLET; future JPY planned value/canonical zero; name/date/precision/capacity/negative CASH validation; duplicate names; identical replay returns the original body; changed-payload 409; eight simultaneous actual same-key POSTs all returned 201 with the same resource ID and one persisted account; replay after demotion returned 403; same key in another business created a distinct account and could not retrieve the first business's detail.

**Real browser assertions:** OWNER and ACCOUNTANT creation, filtered-list refresh/detail, two synchronous form submissions producing one POST, duplicate input retention, exact PKR/JPY/BHD, Urdu/RTL/mobile bounds, initial focus/Tab order/focus return, and lost-response recovery. The uncertainty case forwarded a real POST to Spring, verified its committed 201, dropped only the browser's response, then explicitly retried with identical key/body and observed one persisted account. No synthetic API response substituted for Spring. Delayed real creation successes were discarded on business switch and actual JDBC session expiry. All prior read/filter/pagination/unavailable/stale-detail/tenant/session/backend-failure journeys passed.

**Unchanged financial data:** established one nonempty monthly record and persisted score through real APIs before creation. Full-row count/fingerprint snapshots of monthly_records, score_results, insights and recommendations matched after all mutations/retries and again after the final expiry journey. This proves no changes to that controlled baseline; it does not assert those latter two tables were necessarily nonempty or prove future ledger policy.

Fresh ignored artifacts: `frontend/test-artifacts/accounts-live/results.json`, `demo-results.json`, and desktop/Urdu/mobile/creation screenshots. Backend's reported 105 tests were not rerun: the existing FinancialAccountPostgreSqlIT Surefire XML was inspected (15 tests, zero failures/errors/skips, 2026-10-14 16:10:46 local), separately from the fresh actual Spring/PostgreSQL harness.

No remaining blocker for this bounded UI was observed. Balances remain opening-only; full posting/ledger/cutover, full F01, production gates and financial-policy acceptance remain outside scope. Task-owned services were stopped, disposable database closed, ports 3180/8180 released, final build restored live. No Git mutations or backend/migration/contract/shared-state/security/transport/dependency/manifest/lockfile/environment edits by Codex. Existing teammate changes and backend/.cp.txt were preserved.
