# Accounts integration verification handoff

Task: `A-W1-01-INTEGRATION`  
Date: 14 October 2026 (Asia/Karachi)  
Status: **BLOCKED_BY_BACKEND_PARITY — PAUSED**

Real Spring/PostgreSQL/browser journeys were exercised successfully for normal accounts and explicit unsupported-currency unavailability. Full integration verification is withheld because two controlled persisted fixtures reproduce producer/consumer contract violations. The live harness exits **1**, not success, for these blockers. This is not full F01 completion or production acceptance.

## Repository and exact Codex changes

- Existing checkout: `C:\Users\PC\Projects\sme-financial-webapp`
- Branch: `dev/suleman`
- HEAD: `b51e27696cccc59b94aa9d9c9388ad0faa5b4176`
- Wire specification reviewed: `ACCOUNTS_API_DRAFT.md` **v1.2**.
- Instructions reviewed: root/frontend AGENTS **v1.1**; implementation state **v1.3**; both requested frontend/backend handoffs.

Files changed during this task, relative to the preceding frontend task:

1. `C:\Users\PC\Projects\sme-financial-webapp\frontend\src\lib\api\dev-a\account-contracts.ts`
2. `C:\Users\PC\Projects\sme-financial-webapp\frontend\tests\accounts-read.test.mjs`
3. `C:\Users\PC\Projects\sme-financial-webapp\frontend\tests\accounts-live.integration.mjs` (new)
4. `C:\Users\PC\Projects\sme-financial-webapp\docs\phase2\handoffs\CODEX_W1_01_INTEGRATION.md` (new)

No backend source, migration, existing handoff, contract, shared instruction/state, authentication, transport, navigation, dependency manifest, lockfile or environment file was edited. No Git mutations were performed. Existing contributor changes and `backend/.cp.txt` were preserved.

## Producer/consumer parity

Compared actual AccountController, response records, repository, AccountReadService, CurrencyRegistry, V18 schema, authorization grants and exception handlers with the frontend adapter/types/validators and v1.2.

Matching normal behavior:

- Both GET paths and query names match: status/accountType/currency/page/size.
- ACTIVE/INACTIVE/ARCHIVED, CASH/BANK/WALLET and AVAILABLE/UNAVAILABLE match.
- Normal supported-currency values are strings at their currency scale; timestamps are whole-second UTC strings; dates use Asia/Karachi.
- List/detail field sets, nullable institution/identifier/notes, response page metadata and default filters are consumed successfully by the actual frontend.
- Repeatable-read list service and deterministic full-precision database ordering are present. Detail reads are separate snapshots.
- Identifiers in the controlled fixture are returned as `****7890`, not the seeded raw identifier.
- Actual validation request `page=abc` returned 400 with `error: "Validation Failed"`; actual cross-business detail requests returned non-disclosing 404 and `Financial account not found`.
- Actual OWNER, VIEWER and MANAGER sessions obtained permitted reads. Server authorization was checked through real requests; it is not inferred from hidden frontend controls. ACCOUNTANT is present in source and prior test reports but was not a separate live browser identity in this task.
- No C03 resolver/module key or global accounts navigation was introduced.

### Frontend fix

The producer orders by full-precision database `createdAt` and UUID but emits `createdAt` truncated to whole seconds. Two different subsecond creation times can therefore have equal wire timestamps while their UUIDs appear in reverse lexical order. The previous client check incorrectly inferred a true database tie from equal wire seconds.

The validator now checks nondecreasing emitted creation seconds and unique IDs without inventing UUID tie ordering. A regression test and a real persisted same-second fixture exercise this case. Decimal, balance-state, metadata and identity validation remain strict; no backend financial violation was concealed.

## Requests for Antigravity — confirmed blockers

### BE-01: stored currency scale is not validated against the registry

Location: `AccountReadService.computeDerivedBalance()` / DTO mapping and `CurrencyRegistry.evaluateBalance()`.

Controlled persisted fixture: currency `PKR`, stored `currencyDecimals = 3`, opening amount `100.00`.

Actual detail response: HTTP 200, currency PKR, scale 3, **AVAILABLE**, monetary strings formatted at scale 2 (`100.00`). The registry evaluator uses the currency code while the DTO returns the stored scale unchanged. This violates the declared AVAILABLE exact-scale and metadata-agreement invariants. The frontend correctly rejects this response.

Requested correction: validate code/scale registry agreement before reporting AVAILABLE. Return an agreed explicit unsupported-metadata state, or a defined error; do not silently guess a scale. Cover both list/detail and future-opening paths with real PostgreSQL/HTTP tests.

### BE-02: unavailable current balance still exposes an unrepresentable opening amount

Location: `AccountReadService.computeDerivedBalance()` fallback to `openingBalance.toPlainString()`.

Controlled persisted fixture: PKR scale 2, opening amount `1.0001`.

Actual response: HTTP 200, current balance null, UNAVAILABLE/PRECISION_OVERFLOW, but `openingBalance = "1.0001"`. This opening value violates v1.2's exact declared scale. Inactive account list/detail cannot pass the strict consumer validator; the UI shows its safe invalid-data error rather than rounding, clamping, substituting zero or treating the business as empty.

Requested correction: settle a contract-compliant representation for an unrepresentable opening value. If the read cannot produce a valid required opening amount, fail the read with an agreed error, or obtain an explicit revised opening-unavailability representation before adapting the consumer. No invented frontend field or relaxation was made. Capacity-overflow fallback has the same source-level risk but was not a separate live fixture in this run.

### Other recorded discrepancies

- v1.2's status header still says backend NOT_IMPLEMENTED/V18_UNRESERVED, while state v1.3 and actual code describe the implemented read service and project-lead authorization. This is documentation drift, not permission to edit the contract. Fatima approval is not inferred.
- Backend entity/schema allow institution/notes lengths beyond the previous defensive frontend limits. The real fixtures fit the existing consumer limits. Publish normative bounds before broadening consumer acceptance.
- Initial minimal business setup omitted the explicit consent boolean and received HTTP 500. Updating the fixture to the established MVP payload (`whatsappOptIn: false`) allowed normal business creation. No backend fix is claimed for malformed/incomplete setup requests.

## Actual isolated environment

- Java **21.0.12.1** and existing compiled backend main/test classes; the old packaged JAR was not used.
- Existing `DisposablePostgres` test support starts real **PostgreSQL 14.22** on an ephemeral port with separate migrator/runtime roles. Flyway applied V1 through **V18** to a fresh disposable cluster. Runtime Spring used `finsight_app`.
- The generated, temporary Java fixture boots the real Spring application; it adds no production seeding endpoint, controller, account-creation feature or authentication bypass.
- Runtime schema head was queried and recorded as 18. Source and compiled-resource V18 file hashes matched.
- Backend: loopback port **8180**. Production Next: loopback port **3180**. Availability was checked before startup; no unrelated listener was killed.
- Existing Edge/Playwright dependency was used from the bundled runtime; no installation or framework change.
- Synthetic `.test` identities were registered and signed in through actual cookie/CSRF endpoints. Businesses were created through the existing API. Synthetic financial accounts and Viewer/Manager memberships were seeded only through the disposable fixture connection.
- All credentials and encryption material were generated/held in process memory and omitted from reports. Security/session filters remained enabled. No production user, database, persistent volume or secret was modified.
- Delivery scheduling was disabled only in the isolated process; no external-provider acceptance is implied.

### Opening-only limitation

The backend has no transaction/movement implementation in this slice. Once effective, **currentBalance equals the persisted opening balance**; before openingDate it is canonical zero. The test verifies that exact bounded behavior. It does not verify posted movements, transfers, ledger reconciliation, monthly projection, cutover or score derivation from transactions.

## Prior backend test evidence and discovery

Inspected current local Surefire XML/text artifacts read-only; did not rerun Maven during this task:

| Prior suite | Actual XML test cases | Failures/errors/skips |
| --- | ---: | --- |
| CurrencyRegistryTest | 20 | 0/0/0 |
| AccountReadServiceTest | 7 | 0/0/0 |
| AccountControllerTest | 12 | 0/0/0 |
| FinancialAccountPostgreSqlIT | 6 | 0/0/0 |
| BusinessAuthorizationServiceTest | 7 | 0/0/0 |

The first four total **45**; authorization is a separate **7**. Reports are from the earlier backend run (account reports around 14:04 and authorization around 14:06 on 14 October), not this integration task.

Discovery was verified beyond the IT suffix: `target/surefire-reports/TEST-...FinancialAccountPostgreSqlIT.xml` contains six named testcase elements, a Surefire booter JVM command, real PostgreSQL 14.22 startup logs and fresh migrations through V18. The reported explicit `-Dtest=CurrencyRegistryTest,AccountReadServiceTest,AccountControllerTest,FinancialAccountPostgreSqlIT` selector explains execution by Surefire during `test`; a default `mvn test` must not be assumed to discover IT classes. The separate Failsafe profile is not evidence that this class ran here.

The new live harness independently started fresh PostgreSQL/Spring instances and exercised HTTP/browser behavior; that is distinct from rerunning the 52 prior JUnit cases. No Maven suite exclusions were introduced or run in this task.

## Commands and outcomes from this task

All pnpm invocations used the process-only setting below. No dependency/shim synchronization was requested or observed:

```powershell
$env:pnpm_config_verify_deps_before_run='false'
```

Working directory for frontend commands: `C:\Users\PC\Projects\sme-financial-webapp\frontend`.

| Command | Actual result |
| --- | --- |
| `pnpm test` | Exit 0; **57 passed**, 0 failed/skipped (21 accounts tests + 36 MVP tests). |
| `pnpm exec tsc --noEmit --incremental false` | Exit 0. |
| `pnpm run lint` | Exit 0; one existing SessionProvider effect-cleanup ref warning, no new-file errors. |
| `pnpm run build`, live process mode, BACKEND_API_URL pointing to port 8180 | Exit 0. Final build output restored to **live**. Existing middleware deprecation warning remains. |
| `pnpm run build`, explicit demo process mode | Exit 0; used only for separate demo acceptance, then restored to live. |
| `node tests/accounts-live.integration.mjs` | Final real run: **10 successful journey groups**, exit **1** for BE-01 and BE-02. Sanitized results explicitly set `integrated: false`. |
| `node tests/accounts-live.integration.mjs --demo` | Final exit **0**; bilingual unavailable-preview state, zero private API requests. This does not claim Spring integration. |

Live/browser commands set `PLAYWRIGHT_MODULE` to the already bundled Playwright directory. The harness requires existing compiled backend classes and Surefire classpath evidence; missing prerequisites fail visibly and are never counted as successful skips. Temporary source/classpath fixtures are generated under an owned temporary directory, then removed. No backend source is edited or compiled by the harness.

Development attempts exposed harness issues that were corrected: directory URLs in the classpath manifest, a trailing empty classpath entry, the explicit business consent field, waiting for language/viewport transitions, the existing Active business label's accessible-name shape, and recognizing signal-terminated child processes as stopped. Earlier failed attempts are not represented as successful integration. The final results no longer contain the false shutdown-timeout finding.

## Real browser journey evidence

The final live run exercised actual Next -> Spring -> PostgreSQL responses:

1. Normal sign-in with cookie/CSRF and direct route access; explicit business selection for the multi-business owner.
2. Persisted list and detail Sheet; exact negative PKR, maximum JPY and three-decimal BHD values; future-opening canonical zero; observation/cutoff labels and masked identifiers.
3. Type/currency filters, page-size changes, page reset, next-page navigation, genuine empty matches and out-of-range recovery. Out-of-range UI was tested by removing only synthetic filler rows before Spring read the requested page; no HTTP response data was fabricated.
4. Real unsupported-currency UNAVAILABLE placeholder, including detail without guessing an opening scale.
5. Viewer and Manager browser sign-in/read, backed by separate real API permission checks.
6. Cross-business ID denial before and after switching, with actual 404 responses checked.
7. Detail close/change while delaying an **actual fetched Spring response**, verifying the old detail cannot replace the newer selection.
8. Cross-tab business switching while an old real list response is in flight; old account data remains absent after the delayed response completes.
9. Removing JDBC sessions only in the disposable database closes private views and returns to sign-in.
10. Stopping only the task-owned Spring context produces a visible frontend error, not samples or an empty list.
11. Separate demo build/browser: English and Urdu unavailable-preview text, RTL and **zero** `/api/` requests.

Desktop and settled 390px Urdu/mobile list screenshots were visually inspected. The mobile Sheet was opened with localized dialog naming, focus containment checked, and Escape used to close. Desktop Tab/Shift+Tab containment and trigger focus restoration passed. No captured page exceptions occurred. Full-page screenshots can place fixed navigation/overlays at the current viewport's scroll position; they are not production visual certification.

## Evidence artifacts and cleanup

Ignored evidence directory: `frontend/test-artifacts/accounts-live/`:

- `results.json`: final real environment, successful groups and two producer blockers.
- `demo-results.json`: separate demo pass, no backend integration claim.
- `desktop.png`, `urdu-mobile.png`, `urdu-mobile-sheet.png`, `demo-urdu-mobile.png`: synthetic data only.

All task-owned Edge, Next, Spring and embedded PostgreSQL processes were stopped. Final listener checks found neither 3180 nor 8180 occupied. No existing synthetic API suite was run on 8080. No unrelated process was killed.

`git diff --name-only` for frontend package.json, package-lock.json, pnpm-lock.yaml and pnpm-workspace.yaml returned no changes. No environment file was overwritten. Build outputs are live-mode artifacts; the backend target remains contributor-owned. Branch/HEAD remained unchanged and no Git mutation occurred.

## Remaining unverified / next action

- Antigravity must resolve BE-01 and BE-02 and publish coherent producer/contract evidence before this can be marked INTEGRATION_VERIFIED. Do not weaken the frontend's exact-money validation to hide these cases.
- Capacity-overflow and INTEGRITY_CHECK_FAILED browser journeys, malformed metadata variations, MFA enrollment/challenge/recovery, full console/CSP auditing, accessibility assistive-technology review and wider device coverage were not comprehensively exercised.
- No production PostgreSQL 17/TLS/HBA, Docker, HTTPS ingress, external provider, real-data recovery or production acceptance is claimed.
- No posting, transaction history, account creation, transfer, reversal, cutover editing or full F01 completion is claimed.
- Fatima's financial-policy approval has not been fabricated. Shared state/contracts were not edited by Codex.

Codex is **PAUSED**, with the four changed paths listed above ready for review. Backend corrections belong to Antigravity.
