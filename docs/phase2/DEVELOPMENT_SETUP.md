# Development setup and verification commands

Use one verified `dev/fatima` checkout. Never use production data/credentials for local acceptance. This onboarding changes documentation and restores ignored local dependencies; it does not deploy services, provision a permanent database or store machine-specific secrets/configuration in Git.

## Prerequisites and dependency authority

* Java 21 target; repository `backend/pom.xml` is authoritative. This Windows environment used installed JDK 26 and cached Maven 3.9.16 to compile release 21. That is not proof of the Java 21 container runtime.
* Next/React/TypeScript versions come from `frontend/package.json` and the chosen lockfile. Phase 2 uses **pnpm with pnpm-lock.yaml**, matching prior local integrated validation. Current docker/Dockerfile.frontend instead uses npm ci/package-lock.json; both lockfiles must remain consistent, and the Docker build is a separate acceptance gate. Do not alternate npm/pnpm lockfile updates in one feature. Existing npm lockfile is preserved; any consolidation is a separate reviewed task.
* Node 20.9+ as existing frontend README requires; actual tested version belongs in verification evidence. Use pnpm frozen install and current `pnpm-workspace.yaml` build policy; do not run blanket dependency upgrades.
* Python 3.11+ for OCR, repository tested constraints in `ai-service/requirements-tested.txt`. Recovery fixture dependencies are separately declared in `scripts/backup/requirements.txt`; production-configuration tests also require PyYAML.
* PostgreSQL behavior is essential: embedded test harness uses real disposable PostgreSQL with scoped roles, migrations and JSONB/triggers. Docker/PostgreSQL 17/TLS and live external providers have separate acceptance requirements. Do not replace PostgreSQL with H2 and call migration/security checks equivalent.

```powershell
# Root: identity and state before any edit
git remote -v
git status --short
git branch --show-current
git fetch origin
git rev-parse HEAD origin/main origin/dev/fatima
git rev-list --left-right --count HEAD...origin/dev/fatima
```

A clean, strictly behind dev/fatima may be updated with `git merge --ff-only origin/dev/fatima` while on dev/fatima. Divergence requires inspecting and safely merging, not reset or force-push. No checkout/merge command is safe to run blindly over unrelated changes.

## Backend tests and runtime

```powershell
# From backend
.\mvnw.cmd -B test
.\mvnw.cmd -B -Ppostgres-it '-Dit.test=*IT,!DatabaseRolePrivilegesPostgreSqlIT,!PostgreSqlTlsConnectionIT' verify
```

The second command explicitly excludes nineteen production-target tests requiring their configured DB/TLS fixtures; excluded is not passed. Windows wrapper startup failed in this session. The existing cached Maven executable was used without changing the wrapper:

```powershell
$finsightMaven = 'C:\Users\Hp\.m2\wrapper\dists\apache-maven-3.9.16\0daed3be3ebd1c706f0e69e8b07c6b73f5cc4ea3dfce72a8d0ec2e849ca2ddb0\bin\mvn.cmd'
& $finsightMaven '-Dmaven.repo.local=C:\Users\Hp\.m2\repository' -o -B test
& $finsightMaven '-Dmaven.repo.local=C:\Users\Hp\.m2\repository' -o -B -Ppostgres-it '-Dit.test=*IT,!DatabaseRolePrivilegesPostgreSqlIT,!PostgreSqlTlsConnectionIT' verify
```

`-o` works only after dependencies are cached; omit for an authorized dependency download on a new machine. Paths above document this machine's fallback, not universal installation locations. Scope any sandbox escalation to the required test/cache access; never copy credentials into commands/logs. Preserve first failures and rerun evidence. Current document-recovery test failure is in IMPLEMENTATION_STATE; do not skip it to manufacture a green gate.

For actual local application runtime, use existing `application.properties`, `.env.example`, PostgreSQL role scripts and [production runbook](../PRODUCTION_RUNBOOK.md) as references. Provision a dedicated local database, `finsight_migrator` and restricted `finsight_app` roles and valid local encryption keys through ignored environment/secrets. Runtime has `ddl-auto=none` and Flyway disabled; migration is a separate one-shot `database/migration/FlywayMigrationRunner`/`migrate` profile path. Confirm target/database before applying migrations. The ordinary Compose file is a local database skeleton and does not reproduce production TLS/grants by itself. This session's tests use `DisposablePostgres`, not that persistent database.

The existing Java/FastAPI HTTPS contract can run separately after provisioning the OCR interpreter:

```powershell
# From backend; absolute path to a test-only interpreter with OCR deps
$env:FINSIGHT_OCR_CONTRACT_PYTHON = '<absolute OCR virtualenv Python path>'
.\mvnw.cmd -B '-Dtest=OcrIntegrationConfigurationTests,OcrFastApiClosureProbe' test
```

It launches controlled HTTPS/uvicorn provider fixtures, not a real Google call. Do not relabel the historical 15-case result as a fresh run unless actually executed.

## Frontend

```powershell
# From frontend
pnpm install --frozen-lockfile
pnpm test
pnpm exec tsc --noEmit --incremental false
pnpm run lint
$env:BACKEND_API_URL = 'http://127.0.0.1:8080'
$env:NEXT_PUBLIC_DATA_MODE = 'live'
pnpm run build
pnpm dev
```

BACKEND_API_URL is server-only and rewrites are baked into the build. No `/api` suffix. Live mode must not silently fall back to demo. The existing `next/font/google` build needs Google Fonts access. No font/source change was made to conceal network failure. When a dependency installation requires a noninteractive modules replacement, `CI=true` is acceptable for the package manager; never recursively delete computed workspace paths across shells. This session's frozen install required downloads missing from the existing offline store and then restored Next 16.3.8 without lockfile edits.

Custom browser suites need Edge and Playwright. After successful live build, verify ports 3100 and 8080 are unused, start the built Next process on 3100, then run sequentially:

```powershell
# In one terminal: pnpm exec next start --hostname 127.0.0.1 --port 3100
# In another, from frontend, using a provisioned Playwright module:
$env:PLAYWRIGHT_MODULE = '<absolute Playwright package path>'
$env:BROWSER_ARTIFACT_DIR = '<task-local untracked evidence directory>'
node tests/browser.integration.mjs
node tests/browser-extra.integration.mjs
```

Each browser script supplies its own **synthetic** HTTP API on 8080. Do not run against or replace a real backend using that port. Stop only the task's own test server afterward. These tests are separate from actual Spring/PostgreSQL HTTP journeys and deployed ingress validation. Use a hidden window if launching a background process on Windows.

## OCR and operational fixtures

```powershell
# From ai-service, fresh local environment if needed
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -c requirements-tested.txt -e '.[test]'
.\.venv\Scripts\python.exe -m pytest -q

# From root, suitable test interpreter
ai-service/.venv/Scripts/python.exe -m pip install -r scripts/backup/requirements.txt PyYAML
ai-service/.venv/Scripts/python.exe -m unittest discover -s scripts/backup -p 'test_*.py' -v
$env:FINSIGHT_TEST_SH = 'C:\Program Files\Git\bin\bash.exe'
ai-service/.venv/Scripts/python.exe -m unittest discover -s scripts -p test_production_configuration.py -v
```

OCR tests block real provider calls. Backup tests use real cryptography with simulated PostgreSQL/process boundaries; they are not a real encrypted staging restore. This session first used an older test interpreter under `tmp/main-final-integration/ai-service/.venv` for current-source tests because it already had pyrage/PyYAML. The current OCR virtualenv's already-declared multipart dependency was also restored. Temporary-checkout interpreter locations are a recovery convenience, not the long-term setup contract.

## F02 / AI-LAUNCH-01 offline acceptance

The 15 October implementation adds V18, configuration/AI API tests, 100 distinct-question synthetic evaluation cases across 20 families, frontend helper tests and a synthetic browser suite. These cases test routing and grounding contracts, not real-model quality. Use the same default Maven and full local PostgreSQL commands above; they include the new tests. To isolate failures:

```powershell
# From backend; these tests never call a paid provider
.\mvnw.cmd '-Dtest=ModuleResolverTests,Ai*Tests,OpenAiResponsesProviderTests,MvpFinancialEvidenceAdapterTests' test
.\mvnw.cmd -Ppostgres-it '-Dit.test=BusinessConfigurationPostgreSqlIT,ConfigurationMigrationPostgreSqlIT,AiEvidencePostgreSqlIT' verify
# From frontend, after the production build/start described above
node tests/browser-phase-two.integration.mjs
```

Each browser fixture owns port 8080 temporarily. The additional **real HTTP acceptance harness** must run separately after all synthetic suites release it. It starts Spring on loopback 8080 with a real disposable PostgreSQL database and explicit simulated AI; a production Next server on 3100 must already be built with the 8080 rewrite. Edge and Playwright must be installed. From backend:

```powershell
$env:FINSIGHT_BROWSER_NODE = '<absolute Node executable>'
$env:PLAYWRIGHT_MODULE = '<absolute Playwright package path>'
$env:BROWSER_ARTIFACT_DIR = '<absolute task-local evidence directory>'
.\mvnw.cmd '-Dtest=F02AiBrowserAcceptance' test
```

This opt-in class is intentionally excluded from default test naming. It launches `scripts/test_f02_ai_browser.mjs`, creates synthetic owner/viewer accounts and businesses, exercises onboarding/settings/AI through real session/CSRF APIs and the UI, then tears down its own browser and disposable database. It requires no OpenAI key and makes no paid requests. Inspect screenshots as well as assertions. Stop only the task's Next server when done. Do not substitute a developer/customer database or run concurrently with another service on 8080. [Activation runbook](AI_ACTIVATION_RUNBOOK.md) covers the separate later paid-provider milestone.

## F13 workspace acceptance

The regular default and local PostgreSQL suites include the workspace registry, persistence and migration tests. The opt-in real browser harness requires the same production Next server on 3100 and free Spring port 8080 as F02:

```powershell
# From backend, with FINSIGHT_BROWSER_NODE, PLAYWRIGHT_MODULE and BROWSER_ARTIFACT_DIR set as above
.\mvnw.cmd '-Dtest=F13BrowserAcceptance' test
```

It launches `scripts/test_f13_browser.mjs` against actual Spring sessions, CSRF and disposable PostgreSQL with synthetic accounts/data and explicitly simulated AI. It is not a deployed or real-provider acceptance test. The three existing synthetic browser suites remain separate regressions and must run sequentially because each owns port 8080. See [F13 contract](F13_IMPLEMENTATION.md) and the current [test results](IMPLEMENTATION_STATE.md).

On this Windows host, the bundled pnpm launcher attempted an unnecessary store operation and hit a sandbox symlink restriction. The installed, unchanged dependencies can be invoked directly: `node --test tests/*.test.mjs`, `node node_modules/typescript/bin/tsc --noEmit --incremental false`, and `node node_modules/eslint/bin/eslint.js`. Google Fonts needs network access. When Turbopack's local CSS worker failed, `node node_modules/next/dist/bin/next build --webpack` produced the validated production build without a source/configuration or dependency change. Record the actual builder and warnings; do not relabel a failed default build as passed.

## F06 bounded planning acceptance

The bounded preview is integrated on dev/fatima in commit `14cc912a078485ef7499c58cb5b76641c436f7f0`. Its recorded 19 October tests ran on the exact tree later fast-forwarded and pushed. These commands remain reproducible regression checks; full F06 persistence/approval/conversion and production acceptance remain unperformed. The nineteen production-target cases were excluded from local acceptance, not passed.

The stateless `/api/estimates/preview` and `/estimates` screen use the existing identity/session/CSRF path. No schema or financial posting is added. Isolate its deterministic and real PostgreSQL checks with:

```powershell
# From backend; Maven cache fallback and runtime qualifications above still apply
.\mvnw.cmd '-Dtest=PricingCalculatorTests,EstimatePreviewBoundaryTests' -Ppostgres-it '-Dit.test=EstimatePreviewPostgreSqlIT' verify
# From frontend
node --test tests/estimates.test.mjs
```

After building the live production Next frontend with the 8080 rewrite, start it on loopback 3100, verify 8080 is free, and set the existing `FINSIGHT_BROWSER_NODE`, `PLAYWRIGHT_MODULE`, and a task-local `BROWSER_ARTIFACT_DIR`. Run separately from other browser harnesses:

```powershell
# From backend
.\mvnw.cmd '-Dtest=F06BrowserAcceptance' test
```

The opt-in harness launches real Spring/session/CSRF and disposable PostgreSQL with synthetic accounts and explicitly disabled AI. It verifies margin/markup, cost groups, zero/unknown values, strict decimal/request bounds, access/session/business isolation, English/Urdu/mobile/keyboard, and unchanged records/score. Screenshots require visual inspection. Never point it at a developer/customer database. Actual contract, calculation precision and deferred work are in [F06 implementation](F06_IMPLEMENTATION.md); this does not establish production or policy approval.

## F04 Phase A commercial documents acceptance

F04 Phase A adds only **V31** (record-only invoices/bills, `financialEffect = NONE`). Validate fresh V1–V19, V29, V30, V31 and populated V30→V31 on disposable PostgreSQL; never a shared database.

```powershell
# From backend
.\mvnw.cmd '-Dtest=Commercial*Tests' -Ppostgres-it '-Dit.test=CommercialDocument*IT,MonthlyIntegrityMigrationPostgreSqlIT,DisposableDatabaseRolesIT' verify
# From frontend
node --test tests/commercial.test.mjs
# After the live production Next build is running on 127.0.0.1:3100 with 8080 free; same browser environment as above
.\mvnw.cmd '-Dtest=F04CommercialBrowserAcceptance' test
```

The browser harness uses real Spring/session/CSRF, disposable PostgreSQL, Edge and synthetic accounts with AI disabled; it verifies twelve unchanged financial/score/project/configuration tables plus zero write statements. Run it sequentially with the other browser suites. Contract and non-goals: [F04 Phase A](F04_PHASE_A_IMPLEMENTATION.md).

## Client-pilot hardening acceptance

The coordinated migration is **V29**. Validate the current branch's V1–V19 then V29 and populated V19→V29; do not import V20–V28 or claim combined-branch sequencing. Do not target a production/shared database.

```powershell
# From backend, using the existing cached Maven fallback if needed
.\mvnw.cmd '-Dtest=MonthlyRecordServiceTests,MonthlyRecordControllerTests,MonthlyRecordContributionTests,DocumentControllerTests,DocumentConfirmationServiceTests,JpaDocumentDraftStoreTests,DocumentDraftProcessorTests,DocumentStartupRecoveryTests,AsyncDocumentProcessingTests,DocumentUploadServiceTests,WhatsAppDeliveryServiceTest,Feature12PostgreSqlIntegrationTests' -Ppostgres-it '-Dit.test=MonthlyIntegrityPostgreSqlIT,DocumentAttemptOwnershipPostgreSqlIT,DocumentSecurityPostgreSqlIT,Feature8PostgreSqlIT,MonthlyIntegrityMigrationPostgreSqlIT,DisposableDatabaseRolesIT' verify
.\mvnw.cmd -Ppostgres-it '-Dit.test=*IT,!DatabaseRolePrivilegesPostgreSqlIT,!PostgreSqlTlsConnectionIT' verify
# After production Next is running on3100;8080 free; same browser environment as above
.\mvnw.cmd '-Dtest=PilotHardeningBrowserAcceptance' test
```

Run the pilot, F02, F13 and F06 browser harnesses sequentially; each owns Spring8080 and disposable PostgreSQL. Pilot scenarios cover stale-version draft retention and explicit reload (EN/UR), all-field numeric bounds, source floors and correction, duplicate warning/acknowledgement invalidation, absent-month races, optional zero/null and session/business boundaries. OCR alone is simulated in the pilot harness. Normal frontend unit/TypeScript/ESLint/webpack and53 synthetic browser checks remain required. Results and acceptance limits: [hardening](PILOT_HARDENING.md), [dated evidence](verification/2026-10-24-pilot-hardening.json).

## Documentation and publication checks (onboarding baseline)

Review Markdown relative links, all fourteen feature entries, twelve contract owners, unresolved policies and truthful test status. Check source/migration invariants against the audited SHA:

```powershell
git diff --check
git diff --exit-code b412662866519627ccc81ae80dcc6a5aaf48fe8d -- backend frontend ai-service docker scripts database certs docker-compose.yml docker-compose.prod.yml
git diff --exit-code b412662866519627ccc81ae80dcc6a5aaf48fe8d -- backend/src/main/resources/db/migration
git diff --cached --stat
git diff --cached --check
```

That exact baseline is for onboarding only; future chats use their own fetched/tested baseline. No CI workflow is configured in `.github/workflows` at baseline. Do not call absent statuses green CI. Record local failures and actual provider/deployment exclusions honestly, then publish only the authorized coherent milestone following CODEX_WORKFLOW.
