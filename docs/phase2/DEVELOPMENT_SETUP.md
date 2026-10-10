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

## Documentation and publication checks

Review Markdown relative links, all fourteen feature entries, twelve contract owners, unresolved policies and truthful test status. Check source/migration invariants against the audited SHA:

```powershell
git diff --check
git diff --exit-code b412662866519627ccc81ae80dcc6a5aaf48fe8d -- backend frontend ai-service docker scripts database certs docker-compose.yml docker-compose.prod.yml
git diff --exit-code b412662866519627ccc81ae80dcc6a5aaf48fe8d -- backend/src/main/resources/db/migration
git diff --cached --stat
git diff --cached --check
```

That exact baseline is for onboarding only; future chats use their own fetched/tested baseline. No CI workflow is configured in `.github/workflows` at baseline. Do not call absent statuses green CI. Record local failures and actual provider/deployment exclusions honestly, then publish only the authorized coherent milestone following CODEX_WORKFLOW.
