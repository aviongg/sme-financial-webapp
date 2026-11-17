# FinSight Candidate v3 — Production Security Audit & Private Staging Deployment Plan

**Task ID:** `FINSIGHT-V3-TASK-02-RELEASE-DEPLOYMENT-READINESS`  
**Date:** November 17, 2026  
**Target Baseline:** Candidate v3 (`19547bf3dda9744d51e74c87a908e2e42577299c`) + approved DEF-11 (`fix/finsight-v3-def11` @ `3680255`)  
**Scope:** Persistent, remotely accessible **private staging** deployment (distinct from public customer production)  

---

## 1. Phase D: Production Security Readiness Audit

Audited directly against `docker-compose.prod.yml`, `application-prod.properties`, `docker/nginx/templates/finsight.conf.template`, `docker/postgres/pg_hba.prod.conf`, and `docker/Dockerfile.*`:

| Security Area | Observed Implementation | Verdict & Notes |
|---|---|---|
| **Edge HTTPS & TLS** | Nginx listens only on `80:8080` (HTTP $\rightarrow$ 301 HTTPS) and `443:8443` (TLS 1.2/1.3 only, modern AEAD ciphers, `ssl_session_tickets off`, HSTS 1-year `max-age=31536000`). Default catch-all server rejects unknown `Host` headers (`444` / `ssl_reject_handshake on`). | **STRONG** (Requires real CA/Let's Encrypt edge cert provisioned on VPS) |
| **CSP & Security Headers** | `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy`, `Permissions-Policy`, strict nonce-based CSP (`script-src 'self' 'nonce-$request_id' 'strict-dynamic'`), HTTP methods restricted (`405` on TRACE/CONNECT). | **STRONG** (`TRUST_INGRESS_NONCE=true` safe only behind Nginx rewriting `x-nonce`) |
| **Cookies & CSRF** | `__Host-FINSIGHT_SESSION`, `cookie-secure=true`, `cookie-same-site=Lax`, CSRF cookie `secure=true`, same-origin Next.js proxy `/api/:path*` $\rightarrow$ `BACKEND_API_URL`. | **STRONG** |
| **CORS Policy** | `CorsOriginPolicy.java` rejects `*` / `null` and rejects wildcard subdomains under `prod` profile; `application-prod.properties` binds `APP_PUBLIC_ORIGIN`. | **STRONG** |
| **Network Segmentation** | 5 isolated Docker bridge networks: `finsight-db-net` (`internal: true`), `finsight-ai-net` (`internal: true`), `finsight-ai-egress-net` (`internal: true`), `finsight-web-net`, `finsight-provider-egress-net`. Only `nginx` publishes host ports `80/443`. | **STRONG** |
| **PostgreSQL Isolation & Privileges** | `pg_hba.prod.conf` rejects all `hostnossl` connections, restricts `finsight_dba` to local Unix socket, and enforces `scram-sha-256` + TLS `verify-full` over `172.28.10.0/24` for `finsight_migrator`, `finsight_app`, and `finsight_backup`. Runtime disables Flyway & Hibernate DDL (`ddl-auto=none`). | **STRONG** (Needs live enterprise PostgreSQL 17 TLS/HBA acceptance run on target VPS) |
| **Secrets & Encryption Keys** | `spring.config.import=configtree:/run/secrets/` fails closed if missing; 32-byte `crypto_key_k1` AES-256-GCM key, separate DB role passwords, `ocr_service_key`, `smtp_password`. Demo tunnel flag refused (`exit 2`). | **STRONG** (Use real random secrets on VPS; never reuse demo defaults) |
| **Non-Root Containers** | `Dockerfile.backend` runs UID/GID `10001` (`finsight`), `Dockerfile.frontend` runs UID/GID `10001` (`nextjs`), `Dockerfile.ai` runs UID/GID `10001` (`appuser`). `no-new-privileges: true` set across Compose services. | **STRONG** |
| **Logging** | `json-file` bounded (`max-size: 10m`, `max-file: 3`), stacktraces/messages hidden (`server.error.include-stacktrace=never`), `mail.debug=false`. | **STRONG** |
| **External Providers** | `WHATSAPP_PROVIDER` defaults to `disabled` (`WhatsAppProductionGuard` blocks mock under `prod`); AI OCR isolated behind Squid `ocr-egress-proxy`. | **SAFE FOR STAGING** (Mock AI / disabled WhatsApp until real provider gates pass) |
| **Container Resource Limits** | **GAP FOUND:** `docker-compose.prod.yml` specifies **no** `mem_limit` / `cpus` (`deploy.resources.limits`) on any of the 7 services. | **ACTION RECOMMENDED:** Add explicit `mem_limit` & `cpus` in staging override file so JVM/Python memory spikes cannot OOM-kill PostgreSQL. |

---

## 2. Next.js `16.4.1` Security Advisory Investigation

### Verified Advisory Evidence
1. **Live Audit Output (`corepack pnpm audit --prod` in `frontend/`):**
   - **Advisory ID:** [`GHSA-mfr8-8qww-v76h`](https://github.com/advisories/GHSA-mfr8-8qww-v76h)
   - **Severity:** Moderate
   - **Title:** *Next.js: Information disclosure across root params when a `'use cache'` function is called through `unstable_cache()`*
   - **Affected Range:** `>=16.3.0 <=16.4.4` (Candidate v3 pins `next@16.4.1`, so it **is** inside the vulnerable version range).
   - **Patched Version:** **`>=16.4.5`** (`16.4.5` for Next.js 16.x; `15.5.29` for 15.x).
2. **Reachability Assessment in FinSight:**
   - Searched all files under `frontend/src/` for `'use cache'`, `unstable_cache`, and `cacheComponents`: **0 matches**.
   - **Practical Exploitability in FinSight:** **Not reachable** in current application code.
3. **Operational Build Risk Identified:**
   - During our live `corepack pnpm build:release` verification, Next.js `16.4.1` triggered an online registry advisory check resulting in `Error [SecurityFatalError]` (exit code `1` on first attempt before succeeding on retry). Inside a fresh Docker image build (`docker/Dockerfile.frontend`: `RUN pnpm build:release`), an uncached build contacting npm advisories can **fail the Docker build non-deterministically** unless patched or explicitly retried.

### Recommended Remediation Plan (Separate Dependency Branch — Do Not Bundle Here)
- **Proposed Branch:** `fix/finsight-v3-nextjs-16-4-5` (branched from `19547bf` / Candidate v3.1).
- **Change Scope:** Bump `next` and `eslint-config-next` in `frontend/package.json` from `16.4.1` $\rightarrow$ `16.4.5`, regenerate `frontend/pnpm-lock.yaml` via `pnpm install --lockfile-only`.
- **Verification Gate:** `pnpm audit --prod` (0 vulnerabilities), `pnpm typecheck`, `pnpm lint`, `pnpm test` (488/488), `pnpm build:release` (single-pass green), `pnpm test:browser:smoke`, and `def11-browser.integration.mjs`.
- **Rollback:** Revert the single lockfile/package commit if any Next `16.4.5` regression occurs.

---

## 3. Private Staging Infrastructure Design (Linux VPS + Docker Compose)

This plan preserves the existing 7-container production Compose architecture without redesign.

### 3.1 VPS Specifications & Estimated Monthly Cost

| Tier | CPU / RAM / Storage | Suitable Providers (indicative pricing) | Monthly Cost Estimate | Suitability |
|---|---|---|---:|---|
| **Minimum Viable Staging** | 2 vCPU, 4 GB RAM (+2 GB swap), 60 GB NVMe | Hetzner Cloud `CX22` (~€4.35–5/mo) or DigitalOcean `s-2vcpu-4gb` (~$24/mo) | **\$5 – \$24 / mo** | Tight when running all 7 containers + `ai-service` + simultaneous `docker build`; requires `-Xmx1024m` on `backend-runtime`. |
| **Recommended Staging** | **4 vCPU, 8 GB RAM, 80–160 GB NVMe** | Hetzner Cloud `CPX31`/`CX32` (~€8–13/mo) or DigitalOcean `s-4vcpu-8gb` (~$48/mo) | **\$9 – \$48 / mo** | Smooth parallel execution of Postgres 17 + Spring Boot (`-Xmx2g`) + Next.js + Python FastAPI + Squid + Nginx + encrypted backup/restore drills. |

*(Add ~\$1–2/mo for automated off-host snapshot/object storage such as Backblaze B2 / Hetzner Storage Box / S3, and ~\$10–15/yr for a domain if one is not already owned.)*

### 3.2 Domain, DNS & External Credentials
1. **DNS:** Create an `A` record for `staging.<your-domain>` (e.g. `staging.finsight.example.pk`) pointing strictly to the VPS public IPv4 address. Do **not** point wildcard DNS or direct hostnames to database/backend ports.
2. **Firewall (UFW / Cloud Firewall):** Allow **only** TCP `80`, `443`, and restricted SSH (`22` limited to operator IPs/VPN). Block external access to `5432`, `3000`, `8080`, `8000`.
3. **TLS Certificates:**
   - **Edge (`edge.crt`, `edge.key`):** Obtain Let's Encrypt certificate via DNS-01 or standalone Certbot before starting `nginx`, or use Cloudflare Full (Strict) Origin CA certificate if proxied.
   - **Internal (`postgres-ca.crt`, `postgres-server.crt/key`, `ai-ca.crt`, `ai-server.crt/key`):** Generate private internal CA + service SAN certs matching `postgres.finsight.internal` and `ai.finsight.internal` per `docs/PRODUCTION_RUNBOOK.md`.
4. **External Service Dependencies (Private Staging Mode):**
   - **PostgreSQL 17, Spring Boot, Next.js:** Self-contained in Docker Compose.
   - **WhatsApp (`WHATSAPP_PROVIDER=disabled`):** Disabled in initial private staging until Meta Business Manager templates & tokens are provisioned.
   - **AI Copilot (`app.ai.enabled=false` or `mock` for synthetic staging):** Keep disabled/mock unless live OpenAI key with strict spend cap is configured.
   - **Google Vision OCR (`secrets/google_vision_credentials.json`):** Placeholder or non-privileged staging credential if testing real OCR egress; otherwise test OCR disabled (`OCR_INTEGRATION_ENABLED=false`) on initial staging smoke before enabling live Google Cloud billing.
   - **SMTP (`SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_FROM`, `secrets/smtp_password`):** Validated authenticated STARTTLS relay (e.g., Postmark/Resend/SES sandbox) required if testing password-reset email delivery.

### 3.3 Recommended Container Resource Limits Override (`docker-compose.staging.override.yml`)
Create a local non-committed override alongside `docker-compose.prod.yml` on the 8 GB staging VPS:
- `postgres`: `mem_limit: 2g`, `cpus: '1.0'`
- `backend-runtime`: `mem_limit: 2g`, `cpus: '1.5'`, `JAVA_TOOL_OPTIONS: "-Xmx1536m"`
- `frontend-runtime`: `mem_limit: 1g`, `cpus: '1.0'`
- `ai-service`: `mem_limit: 1g`, `cpus: '0.5'`
- `nginx`: `mem_limit: 256m`, `cpus: '0.5'`
- `ocr-egress-proxy`: `mem_limit: 256m`, `cpus: '0.25'`

---

## 4. Exact Staging Deployment, Backup & Rollback Commands

### 4.1 Initial Staging Deployment Sequence (Linux VPS)
```sh
# 1. Clone / checkout approved release SHA (e.g. Candidate v3.1 SHA) on VPS
git checkout <APPROVED_CANDIDATE_V3_1_SHA>
export FINSIGHT_RELEASE_TAG=$(git rev-parse HEAD)

# 2. Generate internal PKI certs, edge certs, and /secrets/* (mode 0600, owned by root/10001 per PRODUCTION_RUNBOOK.md)
# Populate .env.production (APP_DOMAIN, FINSIGHT_RELEASE_TAG, SMTP_*, WHATSAPP_PROVIDER=disabled)

# 3. Build immutable tagged images (check docker-compose.prod.yml)
docker compose --env-file .env.production -f docker-compose.prod.yml build

# 4. Boot PostgreSQL first and wait for healthy TLS socket
docker compose --env-file .env.production -f docker-compose.prod.yml up -d postgres

# 5. Run one-shot Flyway V1-V52 migration job as finsight_migrator
docker compose --env-file .env.production -f docker-compose.prod.yml --profile migration run --rm backend-migration

# 6. Start full staging runtime (backend, frontend, ai-service, ocr-egress-proxy, nginx)
docker compose --env-file .env.production -f docker-compose.prod.yml -f docker-compose.staging.override.yml up -d
```

### 4.2 CI/CD Options
1. **Manual Digest-Pinned Promotion (Immediate Safe Path):** Run `.github/workflows/images.yml` to build & push immutable SHA-tagged images (recording image digests per `docs/release/RELEASE_ACCEPTANCE.md`), then pull by exact digest on the VPS.
2. **Automated Staging Deploy (Later):** SSH/Tailscale GitHub Actions deployment job triggered only on protected staging tags after `release-gate`, `resolved-dependencies`, `current-source-secrets`, and `historical-browser` pass.

### 4.3 Backup & Paired Recovery Procedure
- Never back up Postgres without matching `document_storage` volume and `crypto_key_k1` (historical + active keyring) as required by `docs/release/V52_RECOVERY.md`.
- Schedule daily cron running `scripts/backup/paired_recovery.py` encrypted with `age` (`crypto_age.py`) and shipping encrypted archives to off-host storage.
- Verify restore quarterly/pre-release using `scripts/backup/restore_database.py` against a disposable database verifying all 84 V52 tables, 64 triggers, 18 functions, and AAD decryption batches.

### 4.4 Rollback Strategy
1. Stop `nginx`, `frontend-runtime`, `backend-runtime`, `ai-service` (keep `postgres` and `document_storage` volumes intact).
2. Switch `FINSIGHT_RELEASE_TAG` in `.env.production` to the prior verified immutable image tag/digest.
3. Because V1–V52 schema changes are strictly additive/forward-only, if a schema migration must be rolled back, restore the pre-upgrade encrypted paired backup (`restore_database.py` + volume snapshot) rather than modifying migration history.
4. Restart prior containers and verify `/healthz`, `/internal/health`, and read-only financial balances.

### 4.5 Real External-Browser Verification Procedure (Post-Deploy)
From a separate external workstation over HTTPS (`https://staging.<domain>`):
1. Verify TLS certificate chain, HSTS header, and HTTP-to-HTTPS 301 redirect.
2. Register / sign in with staging synthetic owner account; verify `__Host-FINSIGHT_SESSION` `Secure; HttpOnly; SameSite=Lax` cookie attributes.
3. Create invoice draft, switch business in a second browser tab, verify DEF-11 modal triggers, return to Org A, restore draft, save & finalize invoice, recognize into obligation, record payment, and verify V52 derived monthly dashboard matches ledger amounts in EN and UR (RTL).
4. Verify `curl -k https://<VPS_IP>:5432` / `:8080` / `:3000` connection refused (ports strictly firewalled).
