# FinSight Candidate v3 — Docker Availability & Rehearsal Report

**Task ID:** `FINSIGHT-V3-TASK-02-RELEASE-DEPLOYMENT-READINESS`  
**Date:** November 17, 2026  
**Host Inspected:** Windows 11 Workstation (`C:\Users\PC\Projects\sme-financial-webapp`)  
**Target Codebase:** Frozen Candidate v3 (`19547bf3dda9744d51e74c87a908e2e42577299c`) / DEF-11 (`3680255`)  

---

## 1. Infrastructure Preflight Findings (Observed Live)

| Check | Command / Source | Observed Value | Status |
|---|---|---|---:|
| **CPU & RAM** | `Win32_ComputerSystem` | 12 logical processors, **15.91 GB RAM** | **ADEQUATE** |
| **Storage (`C:`)** | `Get-PSDrive C` | **105.02 GB free** (359.70 GB used) | **ADEQUATE** |
| **Docker CLI** | `Get-Command docker` / `docker version` | Installed (`C:\Users\PC\AppData\Local\Programs\DockerDesktop\resources\bin\docker.exe`), Client **v29.7.2**, context `desktop-linux` | **PRESENT** |
| **Docker Compose** | `docker compose version` | **Docker Compose v5.4.0** | **PRESENT** |
| **WSL2 Backend** | `wsl --status` | WSL **v2**, default distribution `docker-desktop` | **PRESENT** |
| **Docker Daemon** | `docker version` / `docker ps -a` | `failed to connect to npipe:////./pipe/dockerDesktopLinuxEngine` — **Docker Desktop daemon is NOT running** | **BLOCKER** |
| **Target Ports** | `Get-NetTCPConnection -State Listen` | Ports `80, 443, 3000, 5432, 5433, 8000, 8080, 8093, 8094` are **all free** | **READY** |
| **Local PostgreSQL Service** | `Get-Service *postgres*` | `postgresql-x64-18` is `Stopped` / `Disabled` | **NON-INTERFERING** |
| **Existing `.demo-runtime`** | `Test-Path .demo-runtime` | `False` across primary, PR4-review, and DEF-11 checkouts | **CLEAN** |

---

## 2. Executed Pre-Rehearsal & Contract Checks

Because the Docker Desktop Linux engine daemon is stopped (and starting the host VM/GUI without authorization is outside Phase B boundaries), live Docker container execution did not run. All safe, non-mutating preflight and contract suites **did** execute locally:

| Suite / Command | Outcome | Observed Counts | Scope Covered |
|---|---:|---:|---|
| `powershell -ExecutionPolicy Bypass -File scripts/init-demo.ps1 -PlanOnly` | **PASS** (exit 0 refusal guard without `-CreateFreshDisposableDemo`) | 1/1 | Confirms explicit safety guard against accidental DB creation |
| `powershell -ExecutionPolicy Bypass -File scripts/start-demo.ps1 -PlanOnly` | **PASS** | 1/1 | Confirms loopback-only `127.0.0.1:3000/8080/5433`, live providers disabled, `-PublicTunnel` refused |
| `.\mvnw.cmd test "-Dtest=DemoContainerOwnershipTest,DemoToolingContractTest"` | **PASS** | **21 / 21** (9 ownership + 12 tooling contract) | Verifies instance GUID validation, foreign container refusal, runtime manifest matching against mock Docker fixture |
| `$env:FINSIGHT_TEST_SH="C:\Program Files\Git\bin\sh.exe"; python -m unittest discover -s scripts -p test_production_configuration.py -v` | **PASS** | **17 / 17** | Verifies Compose secret scoping, TLS HBA rules, non-root execution, nginx domain/nonce guards, AI TLS entrypoint |
| `python -m unittest discover -s scripts/release -p 'test_*.py' -v` | **PASS** | **14 / 14** | Verifies CI evidence retention, Postgres gate reporting, release digest checks, OSV triage logic |
| `python -m unittest discover -s scripts/backup -p 'test_*.py' -v` | **PASS** | **63 / 63** | Verifies V52 catalog coverage, age encryption keyring validation, paired recovery checks (against simulated DB boundary) |

---

## 3. Docker Lifecycle Rehearsal Matrix (Actual Execution Status)

| Rehearsal Step | Expected Behavior | Execution Status | Blocker / Evidence Note |
|---|---|---|---:|
| **1. `scripts/init-demo.ps1 -CreateFreshDisposableDemo`** | Creates `finsight-postgres-demo` (`postgres:17`), labels GUID, runs Flyway V1–V52 as `finsight_migrator`, seeds synthetic data, writes `.demo-runtime/environment.json` | **BLOCKED (Not Executed)** | Requires running `dockerDesktopLinuxEngine` daemon |
| **2. `scripts/start-demo.ps1`** | Verifies container ID & V52, packages Next standalone, boots Spring Boot (`8080`) + Next (`3000`), runs HTTP & static asset probes | **BLOCKED (Not Executed)** | Depends on Step 1 & running Docker daemon |
| **3. `node scripts/verify-full-demo-lifecycle.mjs`** | Authenticates all seeded demo roles, switches businesses, invokes financial APIs | **BLOCKED (Not Executed)** | Depends on Step 2 |
| **4. Restart & DB Persistence Check** | `stop-demo.ps1` $\rightarrow$ `start-demo.ps1` retains seeded businesses/transactions across container stop/start | **BLOCKED (Not Executed)** | Depends on Steps 1–3 |
| **5. `scripts/stop-demo.ps1`** | Stops launcher-owned PIDs and verified container ID only; preserves Docker volume & `.demo-runtime/documents` | **BLOCKED (Not Executed)** | Live test blocked; fixture-backed tests (`DemoContainerOwnershipTest`) passed 9/9 |
| **6. Foreign Container Protection & Failure Recovery** | Refuses touching an unrelated container named `finsight-postgres-demo` | **PASS (Fixture) / NOT TESTED (Live Daemon)** | Verified via `DockerFunctionFixture.ps1`; live `docker run` test pending daemon |
| **7. Production Stack Build (`docker-compose.prod.yml`)** | Builds all 7 production containers (`postgres`, `backend-migration`, `backend-runtime`, `ai-service`, `frontend-runtime`, `nginx`, `ocr-egress-proxy`) | **BLOCKED (Not Executed)** | Also note `Dockerfile.frontend` Next.js `16.4.1` first-build `SecurityFatalError` risk (see Staging Plan §3) |

---

## 4. Runner Options & Step-by-Step Rehearsal Runbook

### Option A — Local Windows Host (Fastest)
Docker Desktop is already installed on this workstation (`v29.7.2` + Compose `v5.4.0`). Launch **Docker Desktop**, wait until `docker info` succeeds (`context: desktop-linux`), and execute sequentially from a clean checkout of the approved candidate SHA (`19547bf` or Candidate v3.1):

```powershell
# 0. Pre-build backend JAR & frontend standalone once
cd backend
.\mvnw.cmd clean package -DskipTests -B
cd ..\frontend
corepack pnpm install --frozen-lockfile
$env:NEXT_PUBLIC_DATA_MODE = "live"
corepack pnpm build:release
cd ..

# 1. Initialize disposable PostgreSQL 17 demo container (5433)
powershell -ExecutionPolicy Bypass -File scripts/init-demo.ps1 -CreateFreshDisposableDemo

# 2. Start loopback demo stack (Backend 8080, Frontend 3000)
powershell -ExecutionPolicy Bypass -File scripts/start-demo.ps1

# 3. Run full demo lifecycle verification
node scripts/verify-full-demo-lifecycle.mjs

# 4. Test persistence across stop -> start
powershell -ExecutionPolicy Bypass -File scripts/stop-demo.ps1
powershell -ExecutionPolicy Bypass -File scripts/start-demo.ps1
node scripts/verify-full-demo-lifecycle.mjs

# 5. Clean teardown (after recording evidence; optionally remove volume when done)
powershell -ExecutionPolicy Bypass -File scripts/stop-demo.ps1
docker compose -p finsight-demo -f docker-compose.demo.yml down -v
```

### Option B — Dedicated Linux VPS / Staging Runner (Recommended for Production Compose)
Execute the full `docker-compose.prod.yml` stack on an Ubuntu 24.04 LTS / 22.04 LTS host with native Linux Docker Engine + Compose v2 (avoiding Windows symlink/line-ending friction and validating TLS Edge Nginx + PostgreSQL 17 HBA + V52 production backup/restore directly).
