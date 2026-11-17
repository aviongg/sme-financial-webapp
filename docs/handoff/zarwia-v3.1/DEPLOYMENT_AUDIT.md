# Zarwia Candidate v3.1: deployment-readiness source audit

Audit date: 17 November 2026. Audited source: `integration/finsight-production-candidate-v3.1`
at **`eb4d7f5bea2392cf07bf5d17f3efa551c333e7b8`**.

This is a **read-only source review**. Nothing was built, started, deployed or modified.
No Docker engine, server, database or provider was used. Application code, migrations and
financial logic are unchanged. Every finding below comes from reading the files at the
exact SHA.

## Source identity: verified

| Check | Result |
|---|---|
| Branch tip equals the stated SHA | Yes. `git ls-remote` shows `eb4d7f5` as the head of `integration/finsight-production-candidate-v3.1`. |
| Ancestry | Frozen v3 `23e28c1` and DEF-11 `3680255` are ancestors. |
| Hosted checks on the exact SHA | **12/12 completed `success`**, read from the GitHub check-runs API: release-gate, historical-browser, current-source-secrets, resolved-dependencies, backend-unit, postgres (1–4), browser-smoke, frontend, ocr-and-operations. |
| Release evidence | [`docs/release/CANDIDATE_V31_PRESENTATION_RELEASE.md`](../../release/CANDIDATE_V31_PRESENTATION_RELEASE.md) and `docs/release/candidate-v31/evidence/`, retained as is. |
| Flyway | 52 migration files, `V1` to `V52__transaction_driven_monthly_projection.sql`. |

## What was reviewed

- `docker-compose.prod.yml`, `docker-compose.demo.yml` and `docker-compose.yml`.
- All six `docker/Dockerfile.*` files, `docker/entrypoint-ai.sh`, the Nginx template and hooks, and the Postgres entrypoint and HBA.
- `application.properties`, `application-prod.properties` and `application-migrate.properties`.
- `scripts/init-demo.ps1`, `start-demo.ps1`, `stop-demo.ps1`, `backup-demo.ps1`, `restore-demo.ps1`, `reset-sandbox.ps1`, `DemoScenarioSeeder.java`, `FlywayMigratorDemo.java` and `generate_certs.py`.
- `scripts/backup/README.md`.
- `docs/PRODUCTION_RUNBOOK.md` and `docs/release/STAGING_DEPLOYMENT.md`.
- `frontend/next.config.ts` and `frontend/package.json`.
- Backend health, AI availability and Copilot status code.

## Verified as consistent

- **Production compose topology.** Seven services: postgres, backend-migration (profile `migration`), backend-runtime, storage-init, ai-service, frontend-runtime and nginx, plus the ocr-egress-proxy. Five networks. Only Nginx publishes ports (`80:8080`, `443:8443`). The DB, AI and AI-egress networks are `internal: true`.
- **Image tags.** Every image tag needs `FINSIGHT_RELEASE_TAG`, and runtime needs `APP_DOMAIN`. Compose fails fast if either is missing.
- **Secrets.** The eight secret files and five certificate paths in the compose file match `PRODUCTION_RUNBOOK.md`. The backend reads secrets via `configtree:/run/secrets/`.
- **Startup order.** postgres becomes healthy (`pg_isready` over TCP), then the explicit migration job runs, then the runtime starts. backend-runtime waits for postgres healthy, storage-init completed and ai-service healthy. Nginx waits for backend and frontend healthy.
- **Readiness.** `/internal/health/ready` on the backend checks only application readiness and a DB query. SMTP and OCR reachability do not affect it.
- **API routing.** Nginx routes `/api/` directly to the backend, so the Next.js `/api` rewrite is not on the production request path.
- **Sessions and CSRF.** The prod profile sets `__Host-FINSIGHT_SESSION` with `Secure` and `SameSite=Lax`, and the CSRF cookie is `Secure`. **HTTPS on the exact `APP_DOMAIN` is required for login to work.** The default Nginx server rejects unknown hosts.
- **Live AI cannot switch on by accident.** The prod compose passes no `AI_*` variables to the backend, so `app.ai.enabled=false` and the Copilot runs the deterministic provider. The mock AI provider is also refused under the `prod` profile (`simulation_prohibited`).
- **WhatsApp.** It defaults to `disabled` in prod, and the mock provider is refused under `prod`.
- **Demo tooling.**
  - Database: loopback only (`127.0.0.1:5433`). The scripts refuse `-PublicTunnel`.
  - Container ownership: verified by instance GUID, Compose project/service/working directory and volume.
  - Providers: AI, OCR and SMTP are off; WhatsApp is `mock`.
  - Business modules: all 15 are enabled for the seeded businesses, including `ai`, which is what the Copilot page checks.
  - Restore and sandbox reset are deliberately disabled.
- **Demo accounts.** The five accounts in `start-demo.ps1` match `DEMO_ACCOUNTS` in `DemoScenarioSeeder.java`, which is enforced by `DemoToolingContractTest`.
- **Ignored files.** `.gitignore` ignores `.env.*` (except the example), `/secrets/`, `*.key`, `/certs/edge.*`, `/backups/` and `.demo-runtime/`.

## Findings: inconsistencies and deployment gaps

None of these is an application-code defect, and none was fixed in the release. The handoff
document shows how to work around each one.

| # | Severity | Finding (exact cause) | Proposed correction (not applied) |
|---|---|---|---|
| F1 | **High, staging blocker** | `docker-compose.prod.yml` hardcodes `OCR_INTEGRATION_ENABLED: "true"` on backend-runtime, mounts a required `secrets/google_vision_credentials.json` into ai-service, and requires `SMTP_HOST`, `SMTP_USERNAME`, `SMTP_FROM` and `secrets/smtp_password`. The repo has no supported "synthetic staging, providers off" switch. `STAGING_DEPLOYMENT.md` says the same: "stock Compose enables real Google OCR and needs its actual service account". | Operator decision. (a) Supply a real least-privilege Google Vision service account and a real SMTP relay. Or (b) add a reviewed operator override that sets `OCR_INTEGRATION_ENABLED: "false"`. Option (b) was **not executed or tested**: the backend binds that property with `@ConditionalOnProperty`, and the demo stack runs that way, but the prod-profile combination is unverified. Compose still requires the two secret files to exist. |
| F2 | **High, staging gap** | The production stack has **no synthetic seeding path**. `DemoScenarioSeeder.java` and `FlywayMigratorDemo.java` hardcode `jdbc:postgresql://127.0.0.1:5433/finsight_demo` and refuse any database without the init-demo marker. | On prod-compose staging, create synthetic users and businesses by registering through the UI. The seeded demo accounts and businesses exist **only** in the Windows demo path. Do not repoint the seeder at a prod DB. |
| F3 | Medium | The demo tooling is **Windows-only**: PowerShell, `mvnw.cmd` and `backend\target\…` paths. A Linux VPS cannot run it. | Use the demo path on Windows with Docker Desktop. Use prod compose on Linux. |
| F4 | Medium, build pitfall | Next.js rewrites are fixed at **build** time (`next.config.ts`, default `http://localhost:8080`). The release reproduction builds with `BACKEND_API_URL=http://127.0.0.1:18080` for the historical tests. If that build is reused, the `start-demo.ps1` readiness check of the API through the frontend fails, because the proxy targets 18080. | Before `start-demo.ps1`, rebuild with `BACKEND_API_URL` unset or set to `http://127.0.0.1:8080`. |
| F5 | Medium | `scripts/generate_certs.py` generates **only PostgreSQL** certificates, writes the CA private key into `certs/`, and overwrites the tracked historical `certs/postgres-*.crt` fixtures. No repo tool generates `ai-ca.crt` / `ai-server.crt/key`. The runbook says to keep the issuing CA key off the host. | Generate the internal CA and the `ai.finsight.internal` / `postgres.finsight.internal` certificates with a reviewed tool outside the checkout. The handoff gives standard OpenSSL commands, marked **not executed**. |
| F6 | Medium | The superseded documents on `handoff/finsight-v3-dev-a-audit-20261117` (`FINSIGHT_V3_DOCKER_REHEARSAL_REPORT.md`) run `node scripts/verify-full-demo-lifecycle.mjs`. **That file does not exist** at v3.1 or anywhere in the tree. `scripts/verify-public-demo.mjs` targets a historical public tunnel URL. | Don't use either. Verify the demo with `start-demo.ps1`'s own HTTP/asset/proxy readiness checks, then do the manual walkthrough. |
| F7 | Low | The same superseded staging plan names its baseline as `19547bf + 3680255`. | This package supersedes it. The baseline is `eb4d7f5`. |
| F8 | Low | `docker-compose.prod.yml` sets no `mem_limit` or `cpus`. | Optional operator override; see handoff §9. |
| F9 | Low | Fixed `container_name`s and host ports 80/443 allow only one prod stack per Docker host. | Use a dedicated staging host. |
| F10 | Info | The prod `__Host-` secure cookie means the stack cannot be used over plain HTTP or a bare IP address. | Staging needs a DNS name and a certificate the browser trusts. |
| F11 | Info | An earlier untested scratch "staging kit", written during infrastructure planning, required secret files to be mode `600`. That contradicts the runbook: files `0644` inside a `0700` directory, read by non-root UID 10001. | The kit is **not included** in this package. |

## Not verified (no one has run these)

These need someone to actually run them, and passing CI or fixture tests is not evidence
that they work:

- Docker image builds of the six images.
- The full Windows demo lifecycle: init, start, seeded login, stop and restart.
- Production compose start on PostgreSQL 17 with TLS/HBA role acceptance.
- `nginx -t` with a real domain and certificate.
- The F1 override, if it is used.
- Persistent-document recreate.
- Backup and paired restore drill, and rollback.
- Real SMTP, Google Vision, OpenAI and Meta.
- Native Android/iOS camera behaviour (DEF-15).
- An external-browser HTTPS journey.

## Decision

There is no source-level blocker in the application itself. The v3.1 application source is
suitable to hand off. F1 and F2 are **deployment-configuration gaps** that Suleman must
resolve, through a reviewed operator choice, before running the production compose as a
synthetic staging environment. The Windows demo path is not affected by F1 or F2.
