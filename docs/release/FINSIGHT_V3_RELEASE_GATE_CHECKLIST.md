# FinSight Candidate v3 — Release Gate Acceptance Checklist

**Task ID:** `FINSIGHT-V3-TASK-02-RELEASE-DEPLOYMENT-READINESS`  
**Date:** November 17, 2026  
**Scope:** Distinction between **Private Staging Readiness** and **Public Production Readiness**  

Status Key: **PASSED** | **FAILED** | **BLOCKED** | **NOT TESTED**

---

## 1. Software, Git & DEF-11 Verification Gates

| Gate ID | Requirement | Status | Observed Evidence / Basis |
|---|---|:---:|---|
| **RG-01** | Git provenance of Candidate v3 & Developer A backend inclusion | **PASSED** | `8c6e247` and `153e94b` confirmed ancestors of `19547bf` / `23e28c1`; 0 missing commits. |
| **RG-02** | Core financial invariants & V52 transaction projection integrity | **PASSED** | `CurrencyRegistryTest` (39/39), core ledger/domain suites (315/315 fresh local), Candidate v3 hosted/local `postgres_gate.py` (485/485, V1–V52), `409 monthly_figures_derived` enforced. |
| **RG-03** | DEF-11 branch Git isolation & zero backend/schema drift | **PASSED** | `fix/finsight-v3-def11` (`6aac371` / `3680255`) rooted at `19547bf`; diff on `backend/`, `database/`, `scripts/`, `package.json`, `pnpm-lock.yaml` is empty. |
| **RG-04** | DEF-11 frontend static, unit & build verification | **PASSED** | `pnpm typecheck` (0 errors), `pnpm lint` (0 errors, 51 existing warnings), `pnpm test` (**488/488**), `pnpm build:release` passed. |
| **RG-05** | DEF-11 real multi-tab browser & release smoke verification | **PASSED** | `def11-browser.integration.mjs` (**9/9** real multi-tab Chrome journeys on live Spring + Postgres); `test:browser:smoke` (**6/6** across 36 EN/UR/RTL loads). |
| **RG-06** | DEF-11 `--no-ff` integration into Candidate v3.1 | **NOT TESTED / PENDING APPROVAL** | Audited and ready; held pending explicit owner & Codex/Suleman merge sign-off. |

---

## 2. Dependency & Configuration Security Gates

| Gate ID | Requirement | Status | Observed Evidence / Basis |
|---|---|:---:|---|
| **RG-07** | Production Compose, Nginx, TLS HBA & secret configuration contracts | **PASSED** | `scripts/test_production_configuration.py`: **17/17 PASS** (with Git Bash `FINSIGHT_TEST_SH`). |
| **RG-08** | Release CI & paired backup/recovery script contracts | **PASSED** | `scripts/release/test_*.py`: **14/14 PASS**; `scripts/backup/test_*.py`: **63/63 PASS** (simulated DB boundaries). |
| **RG-09** | Demo tooling container ownership & anti-hijack guards | **PASSED** | `DemoContainerOwnershipTest` (**9/9**) & `DemoToolingContractTest` (**12/12**) passed (**21/21**). |
| **RG-10** | Production `docker-compose.prod.yml` container resource limits (`mem_limit`/`cpus`) | **FAILED / MISSING** | No `mem_limit` or `deploy.resources.limits` set on the 7 production services; staging Compose override required. |
| **RG-11** | Production npm runtime dependency scan (`pnpm audit --prod`) | **FAILED (1 Moderate)** | `GHSA-mfr8-8qww-v76h` on `next@16.4.1` (patched in `>=16.4.5`). Verified **unreachable** in FinSight (`'use cache'`/`unstable_cache` absent), but triggers first-build `SecurityFatalError`. Schedule `fix/finsight-v3-nextjs-16-4-5`. |

---

## 3. Physical Infrastructure, Docker & Live Provider Gates

| Gate ID | Requirement | Target Stage | Status | Blocker / Evidence Note |
|---|---|---|:---:|---|
| **RG-12** | Live Docker demo lifecycle (`init` $\rightarrow$ `start` $\rightarrow$ `verify` $\rightarrow$ `stop`) | Private Staging Prerequisite | **BLOCKED** | Docker CLI (`29.7.2`) & Compose (`v5.4.0`) installed on host, but `dockerDesktopLinuxEngine` daemon is not running. |
| **RG-13** | Production 7-image build (`docker-compose.prod.yml build`) | Private Staging | **NOT TESTED** | Blocked by stopped Docker daemon + recommended Next.js `16.4.5` patch for deterministic container builds. |
| **RG-14** | Enterprise PostgreSQL 17 TLS/HBA & role privilege probes (`DatabaseRolePrivilegesPostgreSqlIT`, `PostgreSqlTlsConnectionIT`) | Private Staging | **NOT TESTED** | Requires live TLS-configured PostgreSQL 17 container / VPS instance. |
| **RG-15** | Live paired database + document volume + keyring backup/restore drill | Private Staging / Prod | **NOT TESTED** | Only simulated fixtures executed so far; must execute against running staging PostgreSQL & volume. |
| **RG-16** | External public HTTPS & cross-network browser verification | Private Staging | **NOT TESTED** | Requires provisioned VPS, DNS `A` record, and TLS certificate. |
| **RG-17** | Real external providers (Meta WhatsApp, OpenAI billing/completions, Google Vision OCR, production SMTP) | Public Production | **NOT TESTED** | Must remain disabled/mocked in initial private staging until provider credentials & quotas are provisioned. |
| **RG-18** | Physical mobile hardware camera capture (DEF-15) & remaining P2/P3 UX backlog (DEF-09, 12, 14, 16, 19, 20) | Public Production | **NOT TESTED / OPEN** | Out of DEF-11 scope; tracked for Stream 3 usability pass before real client onboarding. |

---

## 4. Overall Verdict

- **Private Staging Deployment Readiness:** **CONDITIONALLY READY — PENDING 3 PREREQUISITES:**
  1. Owner approval to integrate `fix/finsight-v3-def11` (`3680255`) into Candidate v3.1 via `--no-ff`.
  2. Running Docker Desktop daemon (or Linux Docker runner) to complete RG-12 (`init`/`start`/`verify`/`stop`) and RG-13 (`docker-compose.prod.yml build` with staging resource limits override).
  3. Separate reviewed dependency bump (`next@16.4.5` / `eslint-config-next@16.4.5`) recommended so `pnpm audit --prod` passes cleanly and `Dockerfile.frontend` never hits `SecurityFatalError`.
- **Public Production Readiness:** **NOT READY** until RG-10 through RG-18 (live TLS PostgreSQL 17, paired restore RPO/RTO drill, external providers, and P2 UX/device checks) pass on the deployed staging environment.
