# Zarwia Candidate v3.1: deployment handoff for Suleman

> **Status: technical handoff ready for Suleman's deployment.**
> **The application has NOT been deployed.** No Docker image, container, database, server or
> provider has been run for this package. Suleman owns machine and hosting assessment,
> deployment, running the application and recording the demo.

Companion documents in this folder:

- [Deployment audit and known gaps](DEPLOYMENT_AUDIT.md). **Read F1–F6 before you start.**
- [Demo walkthrough](DEMO_WALKTHROUGH.md).

## Command labels used in this document

| Label | Meaning |
|---|---|
| **[RELEASE-TESTED]** | Run successfully during v3.1 release verification (local Windows and/or hosted CI) at this source. See the release report. |
| **[PLAN-ONLY RUN ON v3]** | Developer A's audit ran this on frozen v3 with `-PlanOnly`. These scripts are byte-identical in v3.1. |
| **[NOT YET EXECUTED]** | Matches the repository's files, service names and parameters, but nobody has run it for v3.1. Suleman runs it first. |

---

## 1. Exact application source

| Item | Value |
|---|---|
| Repository | `aviongg/sme-financial-webapp` (public) |
| Branch | `integration/finsight-production-candidate-v3.1` |
| **Exact SHA** | **`eb4d7f5bea2392cf07bf5d17f3efa551c333e7b8`** |
| Hosted checks on that SHA | 12/12 green: release-gate, historical-browser, current-source-secrets, resolved-dependencies, backend-unit, postgres 1–4, browser-smoke, frontend, ocr-and-operations |
| Release report | [`docs/release/CANDIDATE_V31_PRESENTATION_RELEASE.md`](../../release/CANDIDATE_V31_PRESENTATION_RELEASE.md) |
| Evidence | [`docs/release/candidate-v31/evidence/`](../../release/candidate-v31/evidence/) |

Deploy **exactly** this SHA. Do not use the handoff branch's own head for the application. It
adds only documentation on top of `eb4d7f5`.

```sh
# [NOT YET EXECUTED]
git clone https://github.com/aviongg/sme-financial-webapp.git
cd sme-financial-webapp
git checkout --detach eb4d7f5bea2392cf07bf5d17f3efa551c333e7b8
git rev-parse HEAD   # must print eb4d7f5bea2392cf07bf5d17f3efa551c333e7b8
```

## 2. Technology stack and runtime prerequisites

| Component | Version in source |
|---|---|
| Backend | Spring Boot 4.1.1, Java **21** target (`backend/pom.xml`), Maven wrapper. The release ran on Java 26 targeting 21. |
| Frontend | Next.js **16.4.5**, Node **22–26** (`engines`), pnpm **10.28.2** (`packageManager`) |
| OCR/AI service | Python ≥ 3.11 FastAPI (`ai-service/`), Google Cloud Vision provider |
| Database | PostgreSQL **17**. Images: `postgres:17` for the demo, `postgres:17-bookworm` for prod. Flyway V1–V52. |
| Edge | `nginx:1.30.5-alpine` |
| Container base images | `eclipse-temurin:21-jdk/jre-alpine`, `node:22-alpine`, `python:3.11-slim`, `alpine:3.22` |

What you need depends on the path you choose (see §5):

- **Path A, local Windows demo:**
  - Windows with PowerShell 5.1 or later.
  - Docker Desktop with the Linux engine **running**. `docker info` must succeed.
  - Docker Compose v2.
  - JDK 21 or later on `PATH` or `JAVA_HOME`.
  - Node 22–26 with corepack.
  - Git.
  - Free ports **3000, 8080 and 5433**.
- **Path B, Linux private staging:**
  - A dedicated Linux host with Docker Engine and Compose v2, and free ports 80 and 443.
  - A DNS name and a browser-trusted TLS certificate for it.
  - Python 3 for secret generation.
  - OpenSSL for internal certificates.

## 3. Repository structure

| Path | Responsibility |
|---|---|
| `backend/` | Spring Boot API: auth/sessions/CSRF, tenants/businesses, ledger, invoices/bills, inventory, projects, reports, documents, Copilot, WhatsApp. Flyway migrations are in `backend/src/main/resources/db/migration/` (V1–V52). Profiles: default, `prod`, `migrate`. |
| `frontend/` | Next.js app (EN/UR, RTL). Standalone output. `/api/*` is rewritten to `BACKEND_API_URL`, which is fixed at **build** time. |
| `ai-service/` | FastAPI OCR service (Google Vision) over HTTPS with a shared service key. |
| `docker/` | Six Dockerfiles, Nginx template and hooks, Postgres entrypoint, HBA and role init (`docker/postgres/init/10-roles.sh`), OCR egress proxy. |
| `docker-compose.prod.yml` | Production/staging stack: 7 services plus the egress proxy, 5 networks, file-backed secrets. |
| `docker-compose.demo.yml` | Loopback-only demo PostgreSQL container (`127.0.0.1:5433`). |
| `docker-compose.yml` | Developer database only. **Not** for deployment. |
| `scripts/` | Windows demo tooling (`init-demo.ps1`, `start-demo.ps1`, `stop-demo.ps1`, `backup-demo.ps1`, seeder), backup/recovery tools (`scripts/backup/`), release gates (`scripts/release/`), configuration tests. |
| `docs/` | Runbooks: `PRODUCTION_RUNBOOK.md`, `release/STAGING_DEPLOYMENT.md`, `release/V52_RECOVERY.md`, `scripts/backup/README.md`. |
| `database/` | Legacy folder. `database/migrations` is empty; Flyway uses the backend classpath. |

## 4. Setup and build

### 4.1 Backend jar (needed for Path A)

```powershell
# [NOT YET EXECUTED] produces backend\target\sme-health-backend-0.0.1-SNAPSHOT.jar (the path start-demo.ps1 expects)
cd backend
.\mvnw.cmd -B clean package -DskipTests
cd ..
```

`./mvnw -B test` (all backend unit tests) is **[RELEASE-TESTED]**: 1,846 cases, 0 failures.

### 4.2 Frontend production build (needed for Path A)

```powershell
# [RELEASE-TESTED] install and build. In the release the build used BACKEND_API_URL=…:18080
# for the historical test suite. For the demo, leave it UNSET (see audit F4).
cd frontend
corepack enable
corepack pnpm install --frozen-lockfile
Remove-Item Env:BACKEND_API_URL -ErrorAction SilentlyContinue
$env:NEXT_PUBLIC_DATA_MODE = "live"
corepack pnpm build:release
cd ..
```

`NEXT_PUBLIC_DATA_MODE` only switches to sample data when it equals `demo`. Never set `demo`
for the presentation, because the app must show real backend data.

### 4.3 Container images (Path B)

The Dockerfiles build the jar and frontend themselves. You don't need a host JDK or Node to
build images. See §5B.

## 5. Deployment procedures

Choose **one** path. **Path A is the recommended way to record tomorrow's demo.** It is
self-contained, seeds synthetic businesses and accounts, keeps live providers off, and does
not need a domain, certificates or provider credentials. Path B is the real private staging.
It has open configuration gaps F1 and F2, which need operator decisions.

### 5A. Path A: local Windows synthetic demo

Each step is in `scripts/*.ps1` and refuses to act on anything it did not create.

```powershell
# 0. Confirm Docker is running (Suleman's own check). [NOT YET EXECUTED]
docker info
docker compose version

# 1. Dry run: prints the plan and touches nothing. [PLAN-ONLY RUN ON v3]
powershell -ExecutionPolicy Bypass -File scripts/init-demo.ps1 -PlanOnly
powershell -ExecutionPolicy Bypass -File scripts/start-demo.ps1 -PlanOnly

# 2. Create the disposable demo database, run Flyway V1–V52, apply grants, seed once. [NOT YET EXECUTED]
powershell -ExecutionPolicy Bypass -File scripts/init-demo.ps1 -CreateFreshDisposableDemo

# 3. Start the backend (127.0.0.1:8080) and frontend (127.0.0.1:3000) with readiness checks. [NOT YET EXECUTED]
powershell -ExecutionPolicy Bypass -File scripts/start-demo.ps1

# 4. Stop. Keeps the database volume and documents. [NOT YET EXECUTED]
powershell -ExecutionPolicy Bypass -File scripts/stop-demo.ps1
```

What `init-demo.ps1` does, in seven printed steps:

1. Creates container `finsight-postgres-demo` in Compose project `finsight-demo`, with a GUID label.
2. Proves the database is empty and marks it disposable.
3. Creates the restricted roles (`scripts/demo_roles_init.sql`).
4. Resolves the Maven classpath.
5. Migrates V1–V52 as `finsight_migrator` and checks that Flyway is at version **52**.
6. Grants runtime privileges.
7. Seeds the synthetic data once and writes `.demo-runtime/environment.json`.

It refuses if the container, volume or document store already exists.

`start-demo.ps1` declares the demo ready only after all of the following pass:

- Container ownership, the disposable marker, schema 52 and a loopback-only DB port.
- The packaged CSS/JS assets return HTTP 200.
- The backend CSRF endpoint responds.
- The frontend health route and the API through the frontend proxy respond.
- The listening ports belong to the processes it launched, and the backend is connected to the demo DB.

It then prints the URL, the accounts and the businesses.

**Pass criteria for Path A:** step 2 ends with `New disposable synthetic demo initialized (schema V52)`,
and step 3 ends with `FINSIGHT DEMO IS RUNNING LOCALLY (NOT PUBLICLY EXPOSED)`.

The demo is reachable at `http://localhost:3000` **on that computer only**. `-PublicTunnel` is
refused by design. Do not expose it through a tunnel. The demo accounts' passwords are public
in the repository.

### 5B. Path B: Linux private staging (production compose)

Follow `docs/PRODUCTION_RUNBOOK.md` and `docs/release/STAGING_DEPLOYMENT.md`. The sequence,
using the real service names:

```sh
# All [NOT YET EXECUTED]. Run from the checkout at eb4d7f5, after completing §6 and §9.
export COMPOSE_PROJECT_NAME=finsight-staging
docker compose --env-file .env.production -f docker-compose.prod.yml --profile migration config --quiet
docker compose --env-file .env.production -f docker-compose.prod.yml --profile migration build --no-cache
docker compose --env-file .env.production -f docker-compose.prod.yml up -d --wait postgres
docker compose --env-file .env.production -f docker-compose.prod.yml --profile migration run --rm backend-migration
#   STOP if the migration job exits non-zero. Never run Flyway repair or edit V1–V52.
docker compose --env-file .env.production -f docker-compose.prod.yml up -d --wait --wait-timeout 240
docker compose --env-file .env.production -f docker-compose.prod.yml ps -a
docker compose --env-file .env.production -f docker-compose.prod.yml exec nginx nginx -t
```

Before Path B, resolve audit **F1** (Google OCR and SMTP are mandatory in the stock compose)
and **F2** (no seeder for the prod DB, so create synthetic users through registration).

## 6. Environment variables and secrets checklist

**No real values appear in this repository or this document.** Generate them yourself, on
the deployment host.

### 6.1 Path A
No secrets to create. `start-demo.ps1` sets everything itself:

- The local-only demo database password.
- A demo AES key.
- `AI_ENABLED=false`, `OCR_INTEGRATION_ENABLED=false` and `WHATSAPP_PROVIDER=mock`.
- No SMTP.

These values are public demo fixtures and **must never be used on any reachable server**.

### 6.2 Path B: `.env.production` (non-secret, git-ignored)

| Variable | Meaning |
|---|---|
| `APP_DOMAIN` | Public HTTPS hostname only: no scheme, port, path or wildcard. Nginx `server_name` and the backend public origin come from it. |
| `FINSIGHT_RELEASE_TAG` | The image tag. Use the full SHA `eb4d7f5bea2392cf07bf5d17f3efa551c333e7b8`. |
| `SMTP_HOST`, `SMTP_PORT` (default 587), `SMTP_USERNAME`, `SMTP_FROM` | **Required** by the compose file. Must be an authenticated STARTTLS relay; it is used for password-reset email. |
| `WHATSAPP_PROVIDER` | Leave unset or set `disabled`. The default is `disabled`; `mock` is refused under `prod`. |

The prod compose passes no `AI_*` or `OPENAI_*` variables, so the Copilot stays in
deterministic "Offline assistant" mode. Don't add them.

### 6.3 Path B: secret files (`secrets/`, git-ignored)

| File | Content | Used by |
|---|---|---|
| `dba_db_password` | random | PostgreSQL superuser (socket-only) |
| `migrator_db_password` | random | Flyway migration job |
| `app_db_password` | random | backend runtime |
| `backup_db_password` | random | backup role (SELECT only) |
| `crypto_key_k1` | Base64 of **exactly 32 random bytes** | backend AES-256-GCM field encryption. **Back it up separately: losing it makes encrypted data unreadable.** |
| `ocr_service_key` | random | shared backend ↔ ai-service key |
| `smtp_password` | real SMTP relay password | backend |
| `google_vision_credentials.json` | least-privilege Google Vision service-account JSON | ai-service. Required file; see F1. |

`PRODUCTION_RUNBOOK.md` → "Required inputs" has a Python snippet that creates the six
locally generated secrets and refuses to overwrite existing ones. **[NOT YET EXECUTED]**

File permissions, per the runbook: the `secrets/` and `certs/` directories are `0700`, owned
by the operator; the files are `0644`. Containers run as non-root UID 10001 and must be able
to read them. Mode `0600` breaks this.

### 6.4 Path B: certificates (`certs/`)

| File(s) | Required identity |
|---|---|
| `postgres-server.crt/.key`, `postgres-ca.crt` | SAN `DNS:postgres.finsight.internal` |
| `ai-server.crt/.key`, `ai-ca.crt` | SAN `DNS:ai.finsight.internal` |
| `edge.crt/.key` | SAN = `APP_DOMAIN`, browser-trusted chain (for example Let's Encrypt) |

The tracked `certs/postgres-*.crt` files are historical fixtures with no private key, so
replace them. `scripts/generate_certs.py` covers only Postgres and writes the CA key into
`certs/` (audit F5). A standard OpenSSL alternative, run **outside** the checkout so the CA
key never lands in `certs/`:

```sh
# [NOT YET EXECUTED] standard OpenSSL 1.1.1+ / 3.x. Keep internal-ca.key offline afterwards.
openssl req -x509 -newkey rsa:3072 -nodes -days 825 -subj "/CN=Zarwia Staging Internal CA" \
  -keyout internal-ca.key -out internal-ca.crt
for svc in postgres ai; do
  openssl req -newkey rsa:3072 -nodes -subj "/CN=$svc.finsight.internal" \
    -keyout $svc-server.key -out $svc-server.csr
  printf "subjectAltName=DNS:%s.finsight.internal\nextendedKeyUsage=serverAuth\n" "$svc" > $svc.ext
  openssl x509 -req -in $svc-server.csr -CA internal-ca.crt -CAkey internal-ca.key -CAcreateserial \
    -days 825 -sha256 -extfile $svc.ext -out $svc-server.crt
done
# Copy into the checkout: postgres-server.{crt,key}, ai-server.{crt,key};
# internal-ca.crt to both certs/postgres-ca.crt and certs/ai-ca.crt.
```

## 7. PostgreSQL initialization and migration

- **Path A:** `init-demo.ps1` does everything: roles from `scripts/demo_roles_init.sql`,
  Flyway V1–V52 as `finsight_migrator` via `scripts/FlywayMigratorDemo.java`, runtime grants
  from `scripts/grant_demo_permissions.sql`, and the check that the version is `52`.
- **Path B:**
  - On an **empty** volume, the official `_FILE` initialization creates `finsight_dba` (SCRAM).
  - `docker/postgres/init/10-roles.sh` then creates `finsight_migrator` (schema owner), `finsight_app` (runtime DML only, no DDL or TEMP) and `finsight_backup` (SELECT only), and installs `pgcrypto`.
  - The `backend-migration` service (profiles `prod,migrate`, user `finsight_migrator`, `validate-on-migrate=true`, `clean-disabled=true`) applies V1–V52.
  - The runtime runs with Flyway disabled.
  - Existing volumes are **not** re-provisioned, and rotating a password needs an explicit DBA step.
- Migration acceptance check after Path B. **[NOT YET EXECUTED]** The DBA can connect only over the local socket.
  ```sh
  # Interactive: local auth is SCRAM, so psql prompts for the password in secrets/dba_db_password.
  docker compose --env-file .env.production -f docker-compose.prod.yml exec -it postgres \
    psql -U finsight_dba -d sme_health -tAc \
    "SELECT max(version::int), bool_and(success) FROM flyway_schema_history WHERE version IS NOT NULL"
  # expected: 52|t
  ```
- Tenant isolation and role privileges are covered by the hosted PostgreSQL gate: **[RELEASE-TESTED]**
  91 classes / 485 cases. That ran on PostgreSQL 14.22 (local) and in hosted CI, **not** on the
  PG17 TLS production roles. The production-role/TLS acceptance (`scripts/test_pg_hba.py`,
  `-Pproduction-db-acceptance`) is described in `STAGING_DEPLOYMENT.md` and is
  **[NOT YET EXECUTED]**.

## 8. Frontend, backend and API connectivity checks

| Check | Path A | Path B (from an external browser and terminal) |
|---|---|---|
| Backend ready | Done automatically by `start-demo.ps1` (CSRF endpoint) | `docker compose … ps` shows backend-runtime `healthy`. Its probe is `/internal/health/ready`, which checks the DB. |
| Frontend ready and static assets | Automatic: every CSS/JS asset returns 200 | frontend-runtime `healthy`. Load the page with DevTools open: no 404s or CSP errors. |
| API through the proxy | Automatic: API via `:3000` | `curl -I https://$APP_DOMAIN/healthz` returns 200. `curl -I http://$APP_DOMAIN/` returns 301 to https. |
| Session cookie | n/a (HTTP loopback, non-secure demo cookie) | After login, DevTools shows `__Host-FINSIGHT_SESSION` with `Secure; HttpOnly; SameSite=Lax`. |
| Closed ports | DB bound to 127.0.0.1 only | From outside, 5432, 8080, 3000 and 8000 refuse connections. Only 80 and 443 are open. |

All Path B rows are **[NOT YET EXECUTED]**.

## 9. Secure private-staging configuration (Path B)

- Use a dedicated host: the fixed container names and ports 80/443 allow one stack per host.
- Firewall: allow only 80 and 443 publicly, and restrict SSH to operator IPs. Never publish 5432, 3000, 8080 or 8000.
- Use only synthetic data. Never use customer data, real phone numbers or demo passwords.
- WhatsApp stays `disabled`. Add no `AI_*` or `OPENAI_*` variables. OCR follows the F1 decision.
- **Resolve F1 and record the decision.** Either:
  - **(a)** supply a real least-privilege Google Vision account and an SMTP relay; or
  - **(b)** use a reviewed operator override (not committed) that sets
    `services.backend-runtime.environment.OCR_INTEGRATION_ENABLED: "false"`.

  Option (b) has **not been tested** with the prod profile. If you use it, confirm that
  backend-runtime becomes healthy and that a document upload goes to manual review.
- Resource limits are optional (audit F8). Example for an 8 GB host: postgres 2g, backend-runtime 2g, frontend 1g, ai-service 1g, nginx 256m.
- **Access control:** registration is open on the deployed app. Keep the staging URL private
  and, if possible, restrict 443 to known IPs at the firewall. **[Operator decision]**

## 10. Demo users and synthetic businesses

**Path A**, created by `DemoScenarioSeeder.java` and listed by `start-demo.ps1`:

| Role | Email |
|---|---|
| OWNER | `demo.owner@finsight.local` |
| ACCOUNTANT | `demo.accountant@finsight.local` |
| MANAGER | `demo.manager@finsight.local` |
| SALESPERSON | `demo.sales@finsight.local` |
| VIEWER | `demo.viewer@finsight.local` |

- **Passwords:** `start-demo.ps1` prints them, and `-PlanOnly` prints them too. They are local-only public fixtures, so they are not repeated here.
- **Showcase businesses, to read and present from:** Alpha Garments & Packaging Ltd, Apex Digital Solutions, Crescent Electronics Trading and Indus Textiles Export Corp.
- **Sandbox copies ("(Sandbox)" suffix), to create and change records in:** these let you add invoices and bills during the recording without changing the showcase figures.
- Every seeded business has all 15 modules enabled, including `ai`, `inventory`, `projects`, `documents` and `whatsapp`.
- **To return to clean data:**
  1. Run `stop-demo.ps1`.
  2. Run `docker compose -p finsight-demo -f docker-compose.demo.yml down -v`.
  3. Remove `.demo-runtime` once you've confirmed nothing in it is needed.
  4. Run `init-demo.ps1 -CreateFreshDisposableDemo` again.

  `restore-demo.ps1` and `reset-sandbox.ps1` are deliberately disabled. **[NOT YET EXECUTED]**

**Path B:** there is no seeder (audit F2).

1. Register a synthetic owner through the UI with a strong, unique password.
2. Create one or two synthetic businesses.
3. Enter the records you need for the demo.
4. Send credentials to presenters only through a password manager or another secure channel, never through chat or the repository.

## 11. Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| `docker info` fails, or the pipe `dockerDesktopLinuxEngine` is missing | Docker Desktop is not running or uses Windows containers | Start Docker Desktop and switch to Linux containers. |
| `init-demo` says `REFUSED: Container … / Volume … already exists` | An earlier demo exists | Reuse it with `start-demo.ps1`, or remove it deliberately (§10). |
| `init-demo` fails at `[4/7] classpath` | No JDK or no internet access for Maven | Install JDK 21+ and allow Maven Central. Rerun after removing the half-created environment, as the script's message says. |
| `start-demo` says `Backend jar not found` | §4.1 was not run | Build the jar. |
| `start-demo` says `REFUSED: … 8080/3000` | The port is in use | Close the other process yourself. The script never kills foreign processes. |
| `start-demo` fails on `verify-backend http://127.0.0.1:3000` | Frontend was built with a different `BACKEND_API_URL` (F4) | Rebuild the frontend with it unset (§4.2). |
| Static asset failures | Missing or stale `.next/standalone` | Rerun `corepack pnpm build:release`. |
| Next build `SecurityFatalError` | Old Next 16.4.1. v3.1 pins 16.4.5. | Check `git rev-parse HEAD` is `eb4d7f5`. |
| Path B: `Set FINSIGHT_RELEASE_TAG` or `Set APP_DOMAIN` error | Missing in `.env.production` | Set it (§6.2). |
| Path B: compose says secret file not found | Missing `secrets/*` or `certs/*.key` | §6.3 and §6.4, including `google_vision_credentials.json` and `smtp_password` (F1). |
| Path B: ai-service exits with `AI TLS certificate and private key are required` | `certs/ai-server.crt/key` missing or empty | §6.4. |
| Path B: backend unhealthy, DB TLS errors | Wrong CA or SAN | The SAN must be `postgres.finsight.internal`, signed by `postgres-ca.crt`. |
| Path B: login loops or no session | Not on HTTPS at the exact `APP_DOMAIN` (`__Host-` secure cookie) | Use `https://APP_DOMAIN` with a trusted certificate. |
| Path B: Nginx returns 444 or drops the connection | Host header does not equal `APP_DOMAIN` | Browse by the domain name, not the IP address. |
| Copilot says "The assistant is disabled for this business." | The business's `ai` module is off | Turn on AI in the business configuration. Seeded businesses already have it on. |

## 12. Backup, restart and rollback

**Path A:**

- **Restart:** `stop-demo.ps1` then `start-demo.ps1`. Data and documents persist in the volume and in `.demo-runtime/documents`.
- **Backup:** `powershell -ExecutionPolicy Bypass -File scripts/backup-demo.ps1` writes a `pg_dump` to `backups/`.
- **Restore:** deliberately unsupported (`restore-demo.ps1` refuses). Recover by recreating the demo (§10).
- All three are **[NOT YET EXECUTED]**.

**Path B:**

- **Restart:**
  ```sh
  docker compose --env-file .env.production -f docker-compose.prod.yml up -d --wait
  ```
  This keeps the `postgres_data` and `document_storage` volumes. **Never** run `down -v`.
- **Backup:** `scripts/backup/README.md` covers the encrypted DB and keyring backup, and
  `docs/release/V52_RECOVERY.md` covers paired DB, document volume and key recovery.
  Back up the DB, the document volume and `crypto_key_k1` **together**.
- **Rollback:** follow `docs/release/STAGING_DEPLOYMENT.md` → "Backup, promotion and rollback".
  - **Application-only rollback:** redeploy the prior image tag, but only if the schema is compatible.
  - **Data rollback:** restore into a **new** database from the paired backup. Never delete Flyway rows.
  - For a **first** deployment there is no prior release. "Rollback" means stopping the stack and keeping the volumes for inspection.
- All of Path B is **[NOT YET EXECUTED]**. The tool logic passes its fixture tests **[RELEASE-TESTED]** (63 backup tests), but no real recovery drill has been run.

## 13. Infrastructure checks Suleman must personally do

1. The machine or host meets §2 and Docker is **running** (`docker info`).
2. The required ports are free: 3000, 8080 and 5433 for Path A; 80 and 443 for Path B.
3. Disk and RAM are enough for six images and their builds. Plan on at least 8 GB RAM for Path B.
4. `git rev-parse HEAD` equals `eb4d7f5bea2392cf07bf5d17f3efa551c333e7b8`.
5. **Path B only:**
   - DNS points at the host, and the edge certificate is valid for `APP_DOMAIN`.
   - The firewall allows only 80/443, plus SSH restricted to known IPs.
   - All secret and certificate files are present with the correct modes.
   - The F1 decision is recorded.
6. **Path B only:** migration exits 0 and Flyway reports `52|t` (§7).
7. Every container is `healthy` and the external-browser checks in §8 pass.
8. The full demo walkthrough runs once end to end **before** recording.
9. Restart persistence: stop, start, and the same records are still there.
10. **Path B only:** a backup is taken and its files exist before any demo data you care about is entered.

## 14. Already verified vs not yet verified

| Already verified by release CI or local release runs (software) | **Not verified (deployment acceptance)** |
|---|---|
| 12/12 hosted checks on `eb4d7f5` | Any Docker image build |
| Backend unit: 1,846 cases, 0 failures | Windows demo lifecycle: init, start, login, stop, restart |
| PostgreSQL gate: 91 classes / 485 cases (Flyway, roles, isolation, encryption, grants), on PG 14.22 locally and in hosted CI | PG17 production role/TLS/HBA acceptance |
| Frontend: 488/488; product browser 36/36; DEF-11 9/9; historical 86 checks; provider disclosures 10/10 | Nginx with a real domain and certificate; HTTPS cookie, CSP and redirects in a real browser |
| OCR: 248/248; Java→FastAPI HTTPS contract 4/4 (fake provider) | Real Google Vision, SMTP, OpenAI or Meta |
| Standalone asset packaging (3 CSS + 21 JS return 200) | Persistent document volume across container recreation |
| Backup/config/release fixtures: 63 / 17 / 14 | Real backup, restore and rollback drill |
| OSV and pnpm audit: 0 findings | The F1 override (OCR off under the prod profile) |
| | Native mobile camera (DEF-15) |

## 15. GO / NO-GO checklist

**Path A: recording the demo.** It's a GO only when every box is ticked:

- [ ] HEAD = `eb4d7f5bea2392cf07bf5d17f3efa551c333e7b8`
- [ ] `docker info` succeeds, and ports 3000, 8080 and 5433 are free
- [ ] Jar built (§4.1) and frontend built with `BACKEND_API_URL` unset (§4.2)
- [ ] `init-demo.ps1 -CreateFreshDisposableDemo` printed `schema V52`
- [ ] `start-demo.ps1` printed `FINSIGHT DEMO IS RUNNING LOCALLY`
- [ ] Owner login works, and the business selector shows the seeded businesses
- [ ] Full walkthrough rehearsed once without errors
- [ ] Stop/start keeps the data

**Path B: private staging.** It's a GO only when every box is ticked:

- [ ] Everything in §13
- [ ] F1 decision made and recorded; F2 handled by synthetic registration
- [ ] No demo passwords or keys used anywhere; secrets freshly generated
- [ ] Migration job exit 0, Flyway `52|t`
- [ ] All services `healthy`; `nginx -t` OK
- [ ] HTTPS, 301 redirect, secure cookie, closed ports verified from outside
- [ ] WhatsApp shows "disabled" and the Copilot shows "Offline assistant"
- [ ] A paired backup has been taken, and the rollback owner is identified

**It's a NO-GO if any box is unticked. Fix it or fall back to Path A for the recording.**
