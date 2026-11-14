# FinSight Production-Closure Audit

| Item | Value |
| :--- | :--- |
| Audited baseline | `153e94b214ba24ac1d81a094eff3805334edaad9` (`handoff/finsight-integrated-production-baseline-20261113`) |
| Working branch | `production/finsight-production-closure` (worktree `wt-finsight-production-closure`) |
| Audit dates | 13–14 November 2026 |
| Method | Independent reading of the source at the baseline SHA (not prior reports), plus local automated test runs |
| Migrations | V1–V52 untouched. No V53 was required by any fix in this pass. |

Severity scale: **P0** data loss, cross-tenant exposure or authentication bypass; **P1** wrong financial figures,
false success, unenforced documented control or production-blocking operations gap; **P2** defect with limited
blast radius (demo-only, needs unusual input, or fails safe); **P3** hygiene, labelling or documentation.

No P0 defect was found.

---

## 1. Findings

### P1-01 Production WhatsApp used the in-memory mock and reported fake deliveries — **FIXED (`bd86efc`)**
- **Evidence:** `application.properties` `app.whatsapp.provider=${WHATSAPP_PROVIDER:mock}`; `WhatsAppClientConfiguration`
  wires `MockWhatsAppClient` with `matchIfMissing = true`; `docker-compose.prod.yml` never set `WHATSAPP_PROVIDER`;
  `app.whatsapp.scheduler.enabled` defaults to `true`. `MockWhatsAppClient.sendSummary` returns `SENT` with
  `mock-msg-<uuid>`. `WhatsAppReplySender.send` returned `SENT` for every provider other than `meta`.
- **Reproduction:** start with `SPRING_PROFILES_ACTIVE=prod` and no `WHATSAPP_PROVIDER`; on Monday 09:00 PKT every
  opted-in business gets a `whatsapp_deliveries` row with status `SENT` and a fabricated provider id.
- **Risk:** customers are told summaries were delivered when nothing was sent (violates "no live mock fallback").
  Because `uq_whatsapp_deliveries_user_cycle` allows one row per business per week, the fake row also blocks the
  real send for that cycle. Inbound Copilot replies were silently discarded while recorded as sent.
- **Fix:** `disabled` provider (`DisabledWhatsAppClient`, always `FAILED`, no id) is the prod default;
  `WhatsAppProductionGuard` refuses `mock` (or unknown providers, or `meta` without credentials) under
  `prod`/`production`; the weekly scheduler skips without writing rows when the provider is `disabled`;
  `WhatsAppReplySender` only simulates for `mock` and otherwise reports `FAILED`; prod compose passes
  `WHATSAPP_PROVIDER` (default `disabled`).
- **Tests:** `WhatsAppProductionFailClosedTest`.

### P1-02 Document-backed obligations could be forged and voided outside the commercial lifecycle — **FIXED (`9924be8`)**
- **Evidence:** `FinancialObligationService.createObligation` accepted client `sourceType`/`sourceId` with no
  restriction; `ProfitAndLossService.statement` counts every `source_type='CUSTOMER_INVOICE'` obligation as revenue
  and every `SUPPLIER_BILL` as cost (no direction filter); `CommercialRecognitionService` refuses to recognize a
  document when an obligation with its id already exists; `POST /api/finance/obligations/{id}/void` voided any
  obligation, bypassing `CommercialLifecycleService.voidRecognized` (stock-movement and settlement prerequisites,
  adjustment cancellation, document status).
- **Reproduction:** as OWNER/ACCOUNTANT, `POST /api/finance/obligations` with `sourceType=CUSTOMER_INVOICE`,
  `sourceId=<finalized invoice UUID>`, `direction=PAYABLE`, any amount → revenue includes the forged amount and the
  real invoice can no longer be recognized. Or recognize an invoice that issued stock, then call the generic void:
  revenue disappears while COGS and credit notes remain, and the document stays `FINALIZED`.
- **Risk:** wrong revenue, margin and health score; blocked recognition; inconsistent document state.
- **Fix:** UUID-form source ids for invoice/bill obligations are reserved for recognition (free-text external
  references such as `INV-OCT-01` remain supported for invoices issued outside FinSight); invoice obligations must be
  `RECEIVABLE` and bill obligations `PAYABLE`; the generic void refuses FinSight document-backed obligations with 409,
  and the lifecycle uses the new `voidForCommercialDocument` path after its own checks.
- **Tests:** `DocumentBackedObligationProvenancePostgreSqlIT`.

### P1-03 Module enable/disable is enforced only for documents, WhatsApp and AI — **FIXED (`d5e0a04`)**
- **Evidence:** `ModuleWebConfiguration` guards `/api/documents/**`, `/api/profile/whatsapp`, `/api/whatsapp/**`;
  `AiController`/`CopilotController` check `ai`. The 11 live modules in `ModuleRegistry` (transactions, receivables,
  invoices, projects, costing, inventory, production, commissions, multi_currency, operations, reports) have no
  server-side check. `docs/phase2/features/EXPERIENCE_PACKAGES.md` §11/§16 requires "all feature controllers enforce
  ModuleAccessPolicy … role/module rules apply both on direct API and navigation".
- **Reproduction:** disable `inventory` in settings; `POST /api/inventory/...` still records stock movements.
- **Risk:** a disabled module is only hidden in the UI; direct API writes still change financial data.
- **Compatibility constraint:** businesses with no `business_configuration` row, and every V30-backfilled row
  (`configured = false`), resolve to `LEGACY_DEFAULTS = core_finance, documents, whatsapp, ai`; naive enforcement
  would block transactions, invoices and inventory for all of them.
- **Fix:** state-changing requests map by longest API prefix to their module (transactions, receivables, invoices,
  projects, costing, production, inventory, commissions, multi_currency, documents, whatsapp); reads stay available
  for retained data. A business whose owner never saved a configuration (`configured = false`) keeps every
  implemented module (strictly additive: nothing usable before is blocked); once saved, exactly the saved modules are
  enabled. No migration. `operations` and `reports` have no write surface mapped (reports are read-only; planning
  endpoints were left unmapped pending an owner mapping — see D1).
- **Tests:** `BusinessConfigurationPostgreSqlIT.liveModulesAreEnforcedOnWritesOnceConfiguredAndUnconfiguredBusinessesKeepThem`,
  `…writePathsMapToTheirModules`.

### P1-04 No backend health/readiness and no real OCR readiness — **RECONCILE with Codex `ec7323c`; OCR gap OPEN**
- **Evidence:** no `spring-boot-starter-actuator` and no health controller; no `healthcheck` for `backend-runtime`,
  `frontend-runtime`, `ai-service`, `nginx` in `docker-compose.prod.yml`; backend depends on `ai-service:
  service_started`; `ai-service /health` returns a static `{"status":"ok"}` without checking Google Vision
  credentials; nginx `/healthz` is answered by nginx itself.
- **Risk:** a backend that cannot reach PostgreSQL, or OCR without valid provider credentials, looks healthy;
  orchestration cannot gate traffic or restarts; OCR failures surface only when a customer uploads.
- **Resolution:** not re-implemented here. Codex `ec7323c` (`codex/production-closure-infra-tests`) adds
  `/internal/health/live` and `/internal/health/ready` (readiness state + `SELECT 1`, 2 s timeout, no diagnostics),
  compose healthchecks and `service_healthy` gating. Reviewed read-only: reachable in-container
  (`anyRequest().permitAll()`), not routed publicly by nginx (only `/api/` is proxied to the backend). Remaining gap
  to add at reconciliation: OCR readiness (`OCR_INTEGRATION_ENABLED` state and an authenticated ai-service probe that
  verifies provider credentials without a paid call); ai-service `/health` is still static.

### P2-01 CORS default allows any `*.trycloudflare.com` origin with credentials — **FIXED (`5c5358d`)**
- **Evidence:** `153e94b` changed `app.security.cors.allowed-origins` default (properties and `SecurityConfig`
  `@Value` fallback) to `http://localhost:3000,https://*.trycloudflare.com` and switched to
  `setAllowedOriginPatterns` with `allowCredentials=true`. The `*`/`null` filter no longer guards patterns.
- **Risk:** production overrides it with `APP_PUBLIC_ORIGIN`, so exposure is the public demo and any non-prod
  deployment: any attacker-created quick tunnel is a trusted credentialed origin (mitigated in practice only by
  `SameSite=Lax` and the public-suffix status of `trycloudflare.com`). `start-demo.ps1` already sets
  `CORS_ALLOWED_ORIGINS` explicitly, so the code default is unnecessary.
- **Fix:** default back to `http://localhost:3000`; `CorsOriginPolicy` rejects `*`, `null`, broad patterns
  (`https://*`, `https://*.com`), plain-HTTP wildcards, paths/credentials, and any wildcard under the prod profile.
  Tests: `CorsOriginPolicyTest`, `SecurityFilterChainTest` (tunnel pattern now opted in explicitly).

### P2-02 Demo sandbox reset disables FK/trigger enforcement and deletes incompletely — **OPEN (demo tooling)**
- **Evidence:** `scripts/DemoScenarioSeeder.java` `resetSandboxOnly()` connects as `postgres` and runs
  `SET session_replication_role = 'replica'`, which disables **all** triggers including FK constraint triggers and the
  append-only/immutability triggers. The delete list omits many tenant tables (counterparties, uploaded documents,
  drafts, inventory cost/positions history, estimates, samples, evidence links, audit events, …), leaving orphans
  that FKs would normally prevent. `monthly_records`, `score_results`, `insights`, `recommendations` are deleted with
  `user_id = <businessId>` bound to the business id — correct for `monthly_records`/`score_results` (whose `user_id` is
  the business id), but the seeder depends on that coincidence silently.
- **Correction to the interim report:** an earlier chat summary said these four deletes "delete nothing"; that was
  wrong — for `monthly_records` and `score_results`, `user_id` holds the business id, so they do delete.
- **Risk:** orphaned rows and immutable-history deletion in the demo database; the technique must never be copied to
  production. Recommended: reset via `DELETE … ` in FK order (or delete the business and rely on `ON DELETE CASCADE`
  where it exists) without `replica`, and add a post-reset orphan check. Not changed in this pass (demo database is
  protected and the tooling belongs to the demo owner).

### P2-03 Demo secrets committed and demo credentials message is wrong — **OPEN (demo tooling)**
- **Evidence:** DB password and the AES key `MTIz…MTI=` (also the test key) in `DemoScenarioSeeder.java`,
  `start-demo.ps1`, `demo_roles_init.sql`, `docker-compose.demo.yml`. `start-demo.ps1` prints
  `Password (all accounts): FinSight@Demo2026!` while the seeder sets per-role passwords (`FinSight@Owner2026!` …).
- **Risk:** anyone with the repository can decrypt a copied demo database; evaluators are given wrong passwords.
  Production uses ConfigTree secrets and is not affected. `scripts/grant_demo_permissions.sql` also grants `DELETE`
  on all tables to `finsight_app`, diverging from the least-privilege model the migrations enforce.

### P2-04 Multi-currency activity is excluded from monthly health — **POLICY PROPOSAL (not implemented)**
- **Evidence:** `MonthlyFinancialProjector` limits cash, ending cash and AR/AP to PKR accounts/obligations and only
  records notes (`foreign_currency_accounts_excluded_from_cash`, `foreign_currency_obligations_excluded_from_ar_ap`).
  P&L revenue/cost of FinSight invoices/bills is PKR by schema (`commercial_documents.base_currency = 'PKR'`); no
  case of mixed-currency summation was found. Realized FX is excluded from operating figures by design.
- **Risk:** health of an exporter/importer is understated or skewed. This is a documented policy, not a computation
  defect. See `docs/production-closure/MULTI_CURRENCY_MONTHLY_HEALTH_PROPOSAL.md`.

### P2-05 OCR documents can remain unrecovered after a crash — **FIXED (`c08197c`)**
- **Evidence:** `DocumentStartupRecovery` runs once at `ApplicationReadyEvent` and only fails `processing` rows
  started before `now - 5 min`; `pending` rows whose post-commit dispatch was lost (crash between commit and executor
  submission, or queued work lost on shutdown with a 100-item in-memory queue) are never re-dispatched; documents
  interrupted less than five minutes before a restart stay `processing` until a user retries.
- **Risk:** uploads silently stall; recovery depends on the user noticing and pressing retry.
- **Fix:** `DocumentRecoverySweeper` (enabled by `application-prod.properties`) fails stale `processing` attempts with
  `processing_interrupted` and re-dispatches `pending` uploads older than a grace period, 20 per sweep. Idempotent via
  the worker's atomic pending→processing claim. Test: `DocumentRecoverySweeperPostgreSqlIT`.

### P2-06 Monthly projection retries a failing rebuild on every read — **OPEN**
- **Evidence:** `MonthlyProjectionService.refreshIfStale` records `last_error` but the next read rebuilds every month
  in coverage again; no backoff.
- **Risk:** one bad source row makes each dashboard/report request perform a full failing rebuild (latency, log
  volume). The read itself still succeeds with the last materialised months.

### P2-07 Demo restore drops the database before validating the dump — **OPEN (demo tooling)**
- **Evidence:** `scripts/restore-demo.ps1` runs `DROP DATABASE … WITH (FORCE)` before `pg_restore`; under Windows
  PowerShell 5.1 `$ErrorActionPreference = "Stop"` does not stop on native exit codes, so a failed `pg_restore` still
  prints "successfully restored".
- **Risk:** demo data loss with a false success message. Recommend `pg_restore --list` pre-check, restore into a new
  database, `$LASTEXITCODE` checks, then rename.

### P2-08 Document bytes have no backup tooling — **OPEN (operations)**
- **Evidence:** `scripts/backup/README.md` states uploaded document bytes must be preserved separately; there is no
  script for the `document_storage` volume, and the restore drill does not reconcile `uploaded_documents.storage_path`
  against restored files.
- **Risk:** a database restore references documents that no longer exist (OCR evidence links break).

### P2-09 OCR integration is off unless explicitly enabled — **INFORMATIONAL / readiness**
- **Evidence:** `OcrIntegrationConfiguration` is `@ConditionalOnProperty(OCR_INTEGRATION_ENABLED=true)`; otherwise
  `DocumentProcessingConfiguration` installs a client that always throws `unavailable`. Prod compose enables it; the
  demo scripts do not, so every demo upload ends `failed`.
- **Risk:** expected behaviour, but invisible: there is no readiness signal saying OCR is disabled (covered by P1-04).

### B-1 (P1, from Codex clean-clone CI) Storage-key validation depended on the host OS — **FIXED (`296d3bd`)**
- **Evidence:** `LocalFileSystemStorageService.validateAndResolvePath` used `Paths.get(key).isAbsolute()`; on Linux
  `C:\windows\system32\cmd.exe` is relative, became a literal filename under the root and surfaced as
  `DocumentNotFoundException` (CI: `pathTraversalAttemptIsRejected[5]`). No read outside the root was possible.
- **Contract adopted:** non-host-absolute paths must be canonical keys as written by `store()` — `/`-separated
  `[A-Za-z0-9._-]` segments, no `\`, `:`/drive letter, leading separator, empty, `.` or `..` segment — checked as a
  string, identically on every OS. Host-absolute paths remain accepted only inside the root, because `34471b1`
  (23 Sep 2026, before `e438f21`) persisted absolute paths. `InvalidPathException` no longer escapes.
- **Tests:** `StorageKeyContractTests` (OS-independent); the original `pathTraversalAttemptIsRejected` is unchanged.

### B-2 (P1, from Codex clean-clone CI) Concurrent recurring generation raised optimistic-lock errors — **FIXED (`55c27ce`)**
- **Root cause (confirmed deterministically):** both generators loaded due `RecurringRule` entities unlocked, then
  re-read each with `PESSIMISTIC_WRITE`; when the competitor committed in between, the locked read returned version
  N+1 while N was managed → `ObjectOptimisticLockingFailureException`. No duplicate posting was possible (unique
  occurrence per rule/date); the losing caller got an error instead of "nothing to do".
- **Fix:** the scans return `(id, businessId)` references in stable id order; each rule is locked-and-loaded fresh.
  No retry, idempotency unchanged.
- **Tests:** `RecurringGenerationLockingPostgreSqlIT` — deterministic interleaving (scan → competitor commits → lock),
  a control proving the former entity scan reproduces the CI exception, and 15 real races each generating exactly 1.

### B-3 (P1, from Codex) C11 Financial Query Facade materialised V52 months on GET — **FIXED (`1c3c271`)**
- **Evidence:** with the valid score fixture (`3adcbbe`), `FinancialQueryPostgreSqlIT
  .testReadOnlyDatabaseValueLevelSnapshotInvariants` failed: `monthly_records` 1 → 3. Diagnosis: the only writer was
  `MonthlyProjectionWebConfiguration`'s freshness interceptor, because `/api/finance/query/**` was a refresh path;
  the facade methods themselves wrote nothing and none of the eight tested endpoints read `monthly_records`.
  Materialising there could also convert the fixture's `LEGACY_MANUAL` month to `DERIVED` and rescore it from a GET.
- **Intended architecture (documented in code):** C11 is the published strictly read-only tool contract (zero
  mutations, REPEATABLE_READ). Product surfaces presenting materialised months (dashboard, reports, scores, records,
  Copilot) keep refreshing before their handlers; the sweeper covers the rest. The C11 namespace is not a refresh
  path. With Codex's corrected fixture applied locally (not committed here) the existing assertions pass unchanged
  (6/6); no assertion was weakened.
- **Tests:** `ProjectionFreshnessPathContractTest`; `FinancialQueryPostgreSqlIT` after reconciliation with `3adcbbe`.

### P3-01 Evidence link audit events are mislabelled
- `CommercialLifecycleService.linkEvidence` records `COMMERCIAL_ADJUSTMENT_ISSUED`; `unlinkEvidence` records
  `COMMERCIAL_ADJUSTMENT_CANCELLED` (metadata carries `action`). Audit reporting by event type is misleading.

### P3-02 Obligation audit metadata is built by string concatenation
- `FinancialObligationService` writes `{"reason":"<user text>"}` and similar without JSON escaping; a reason
  containing `"` produces invalid or altered metadata.

### P3-03 Client-side totals on the recognition workspace
- `CommercialRecognitionWorkspace.tsx` sums document totals in the browser (`reduce … Number(d.total)`) for summary
  tiles, contrary to "UI never re-computes financial authority". Display-only, but should come from the backend.

### P3-04 Stale state documents
- `docs/PHASE2_IMPLEMENTATION_STATE.md` still reports V29 / `dev/suleman` / `89f60fd` as current; `AGENTS.md`
  describes a different shared checkout and branch policy than the integrated baseline.

---

## 2. Areas reviewed with no defect found

| Area | What was checked |
| :--- | :--- |
| V52 concurrency | Per-business `pg_advisory_xact_lock`, `dirty_seq` compare-and-clear under READ COMMITTED (a writer committing mid-rebuild leaves the business dirty), coalescing of concurrent stale reads, interceptor runs outside any request transaction (no double connection), sweeper bounded to 50 businesses. |
| Projection sources | Trigger list covers every table read by the projector, P&L and inventory read model; obligations' only statuses are `OPEN`/`VOIDED`, so settlement never removes recognized revenue. |
| Authentication / session | `changeSessionId` fixation protection, max-lifetime filter, account-status filter on every request, MFA pre-auth stage filter, reset tokens stored as SHA-256 with expiry, in-memory reset notifier refused under prod, bounded login/reset rate limiting. |
| RBAC | Every `@RestController` calls `requirePermission`/an explicit role check; SALESPERSON only has `COMMISSION_READ`; Copilot tools and WhatsApp inbound require `FINANCIAL_DATA_READ` per call; platform audit endpoint is `PLATFORM_ADMIN`. |
| Tenant isolation | Sweep for id-only SQL and `findById` usage found only identity/internal lookups; feature repositories take `(businessId, id)`; evidence linking checks `user_id = businessId`. |
| Inventory / WAC | Item row `FOR UPDATE`, backdated movement rejection, proportional WAC issue with exact remainder on full issue, customer returns bounded by the original issue, reversal guards. |
| Copilot | Grounding gate rejects answers containing numbers absent from tool evidence; provider calls gated by `enabled`, `live-activation-approved`, allow-list, rate and circuit limits. |
| Schedulers | WhatsApp deliveries unique per business per cycle; recurring occurrences unique per rule and date. |
| Migrations / grants | V30–V52 grant `finsight_app` least-privilege DML and `finsight_backup` SELECT for every new table. |

Coverage limits: projects, samples, estimates and reports were reviewed at the level of their controllers'
authorization and their published test suites, not line by line.

---

## 3. Verification record

Automated and simulated acceptance only (Windows 11, JDK 26.0.1, embedded PostgreSQL via `DisposablePostgres`). No
real external provider was contacted.

| Run | Command | Result |
| :--- | :--- | :--- |
| Baseline unit (`153e94b`) | `mvnw -B test` | 1,785 run, 0 failures, 0 errors, 1 skipped |
| Baseline PostgreSQL IT (`153e94b`) | `mvnw -B -Ppostgres-it verify` | 466 run, 0 failures, 19 errors, 0 skipped — all 19 are the conditional production-fixture classes `DatabaseRolePrivilegesPostgreSqlIT` (15) and `PostgreSqlTlsConnectionIT` (4), which require `FINSIGHT_TEST_APP_PASSWORD` and a production stack |
| Final (`c08197c`) | same two commands | see §6 |

Not run: `scripts/test_production_configuration.py` (PyYAML not installed on this host); frontend unit/contract
tests and browser journeys (no frontend files changed); Linux execution (Codex CI covers it).

## 4. Decisions required

- **D1 Module mapping for `operations`/planning.** Enforcement is implemented (rule (a): unconfigured businesses keep
  every implemented module; no migration). Owner input is needed only to map `/api/planning/**` and any other
  `operations` write surfaces to a module key.
- **D2 Multi-currency monthly health** — see `MULTI_CURRENCY_MONTHLY_HEALTH_PROPOSAL.md`; no change until review.
- **D3 Demo tooling (P2-02/03/07).** The seeder, demo scripts and demo database are protected/demo-owned; fixes are
  recommended but not applied.

## 5. Real-provider and infrastructure gates still pending

Google Vision OCR with production credentials; Meta WhatsApp Cloud API (templates approved, webhook signature);
SMTP delivery; LLM provider (`AI_LIVE_ACTIVATION_APPROVED`); production container image build; PostgreSQL 17 with TLS
`verify-full` and production HBA; `DatabaseRolePrivilegesPostgreSqlIT`/`PostgreSqlTlsConnectionIT` against production
fixtures; encrypted backup and staging restore drill including the document volume; persistent volume recreation;
real browser journeys against the deployed stack.

## 6. Final regression at branch head

**Not completed.** The full unit + PostgreSQL IT run at `c08197c` was stopped by the host when free memory became
critically low (≈2.5 GB of 15.3 GB free afterwards). Before it stopped, 98 unit test classes had run with 0 failures
and 0 errors; the IT phase never started. This is partial evidence, not a pass.

Targeted runs after each fix (all green, real embedded PostgreSQL where named IT):

| Fix | Tests run |
| :--- | :--- |
| `bd86efc` `9924be8` `5c5358d` | 78 unit (WhatsApp*, CorsOriginPolicy, SecurityFilterChain, FinancialObligation*, CommercialRecognitionService) + 25 IT (DocumentBackedObligationProvenance, CommercialRecognition, WhatsAppInbound, MonthlyLedgerProjection) |
| `296d3bd` | 91 unit (all `documents` tests incl. StorageKeyContractTests, LocalFileSystemStorageServiceTests) |
| `55c27ce` | 17 unit (RecurringRule*) + 12 IT (RecurringGenerationLocking, RecurringTransactions) |
| `1c3c271` | FinancialQueryPostgreSqlIT 6/6 with Codex's valid fixture applied locally (1 failure without the fix); 44 unit + 22 IT (projection, Copilot, MonthlyIntegrity, TransactionDrivenMonthly); ProjectionFreshnessPathContractTest 2/2 |
| `d5e0a04` | 57 unit (configuration, AI, Copilot, production-secret) + 15 IT (BusinessConfiguration incl. 2 new, DocumentBackedObligationProvenance, WhatsAppInbound, Copilot) |
| `c08197c` | 60 unit (documents) + 22 IT (documents, commercial, DocumentRecoverySweeper) |

Required before sign-off: one uninterrupted `mvnw -B test` and `mvnw -B -Ppostgres-it verify` at the reconciled head
(preferably in Codex's Linux CI, which also closes B-1 on Linux).

## 7. Reconciliation with `codex/production-closure-infra-tests` (`28bc6ba`)

Reviewed read-only; nothing merged or cherry-picked. `git merge-tree --write-tree HEAD
origin/codex/production-closure-infra-tests` (in-memory, no refs changed) reports **no conflicts**.

| Codex commit | Recommendation | Overlap with this branch |
| :--- | :--- | :--- |
| `b3ebd83` frontend toolchain freeze | Carry | none |
| `ec7323c` runtime probes, release tags, healthchecks | Carry; then add OCR readiness to `RuntimeHealthController` | `docker-compose.prod.yml` (`bd86efc` adds `WHATSAPP_PROVIDER` near `SMTP_FROM`; Codex edits image tags and healthchecks) and `application-prod.properties` (Codex top, this branch middle) — different hunks, expected to merge cleanly; verify on merge |
| `7f13a0a` CI gates, production acceptance profile | Carry | `backend/pom.xml` — untouched here deliberately |
| `3adcbbe` valid score fixture + findings | Carry; makes B-3's regression authoritative | `FinancialQueryPostgreSqlIT` — untouched here |
| `28bc6ba` V52 gates, staging/recovery docs | Carry after updating its findings to reference B-1..B-3 fixes | docs only |
