# Zarwia / FinSight Candidate v3 — Final engineering handoff

## Status: ENGINEERING FROZEN — READY FOR SULEMAN TECHNICAL REVIEW

This status means only that every required **engineering** gate passed on the exact
integrated tree and that no further independent feature work is planned before
Developer A (Suleman) reviews it. It does **not** mean production-ready,
deployment-approved, public-demo-approved, real-client-pilot-approved or approved
for merge into `main`. Those decisions remain open (see sections 8–9).

| Item | Value |
|---|---|
| Branch | `integration/finsight-production-candidate-v3` |
| Frozen application/test tree (final merge) | **`19547bf3dda9744d51e74c87a908e2e42577299c`** |
| Published head | The commit that adds this file on top of `19547bf`; it changes documentation only. Resolve it with `git log -1 --format=%H -- docs/release/CANDIDATE_V3_FINAL_ENGINEERING_HANDOFF.md` |
| Base of the final merge | Stage-C head `e2fcb978448176208dfee6a8ae52ab0e389350d4` |
| Last integrated fix | `fix/finsight-v3-demo-container-ownership` @ `f1ec5646243a86cbd5000567d4b255ee2209fe03` |
| Not modified | `main`, `dev/fatima`, `dev/suleman`, all source fix branches, Stage-A/B/C reports and evidence |

---

## 1. Integration provenance (complete, in order)

Every integration was a normal `--no-ff` merge of an exact, reviewed head. There
were no rebases, squashes, amends or force-pushes. Both parents of every merge are
preserved in history.

| Step | Input / operation | Exact SHA |
|---|---|---|
| Frozen v2 starting point | `integration/finsight-production-candidate-v2` | `71ac4e0e7e74cbfe2f08fe3a049d37cd26b0c7c3` |
| Stage A | Provider-disclosure source (Copilot provenance + delivery-row provider) | `0f3729f27a015457b3a3aee98deea6ea1a9244e4` |
| Stage A | No-ff merge of provider disclosure | `5736d452f72a40b11acd757107336b2e287bf8f3` |
| Stage A | Historical-browser reconciliation source | `380acac342cc162f11e39c2a33651d2b916802ab` |
| Stage A | No-ff merge of historical browser | `603f3b3173eba3c4daccf5e06f81ea3191eb5294` |
| Stage A | CI wiring for v3 gates | `3ab1069615932664c570336da2306d2d84a915d3` |
| Stage A | Evidence publication | `93c268dacf9825f600f8e7997f6db1ad63c40ecb` |
| Stage B | Demo tooling source (fresh-only seeding, HTTP/asset readiness, owned processes, local-only) | `086957dfdb421e1d62bed64c43e124c97cc1f915` |
| Stage B | No-ff merge of demo tooling | `ce68723e5526b98b70be4cd74984ebc3438b0b54` |
| Stage B | Demo standalone CI gate / scope | `aa0b9e15ebc6dd1274983c31b2338fcce7e0e132` |
| Stage B | Historical login synchronization (test only) | `ee004c5421aacfc8ce43939040a5861550c4d645` |
| Stage B | Retained seeded-login / ownership probes | `9a27edcb10dbd315026ea0d7279af890bba65bbb` |
| Stage B | Evidence publication | `ce3d1b06436c478b71f792b700b8e42467ad5e84` |
| Stage C | Outbound WhatsApp capability disclosure source | `7ecf6097779cbb64963baf3f50f6bb724f0dd140` |
| Stage C | No-ff merge of outbound disclosure | `10e4b71937177b3957f3c333117b06abfec22156` |
| Stage C | Verification reservation / capability QA helpers | `5cf46349272960baadd261140a61aebc8602a5b6`, `5f20c4cf8bf733f6af8f0fa6aa703ab8b90074da` |
| Stage C | Evidence publication | `e2fcb978448176208dfee6a8ae52ab0e389350d4` |
| **Final** | Container-ownership fix source (Stage-B P1) | `f1ec5646243a86cbd5000567d4b255ee2209fe03` |
| **Final** | **No-ff merge of container ownership** | **`19547bf3dda9744d51e74c87a908e2e42577299c`** |

### Final merge review

- Parents: `e2fcb97` (Stage C) and `f1ec564` (fix). Merged without conflicts. The fix
  branch starts at Stage-B `ce3d1b0`, an ancestor of Stage C. Stage C changed no
  demo-tooling file, so no Stage-C work could be reverted.
- Relative to Stage C, the merge changes exactly nine files, all demo tooling and
  its tests. Their contents are byte-identical to `f1ec564`:
  `docker-compose.demo.yml` (one container label), `scripts/demo-guards.psm1`,
  `scripts/init-demo.ps1`, `scripts/start-demo.ps1`, `scripts/stop-demo.ps1`,
  `scripts/backup-demo.ps1`, `scripts/restore-demo.ps1`,
  `backend/src/test/java/.../demotooling/DemoContainerOwnershipTest.java` and
  `backend/src/test/resources/demotooling/DockerFunctionFixture.ps1`.
- `git diff e2fcb97 19547bf -- backend/src/main frontend/src database` is empty.
  There is no change to production accounting logic, financial calculations,
  V1–V52 migrations, permissions/roles, tenant isolation, session/CSRF, providers,
  workflows, package scripts or lockfiles.

---

## 2. Implemented modules and architecture overview

**Stack.** Spring Boot backend (Java 21 target), PostgreSQL with Flyway V1–V52,
Next.js 16.4.1 / React 19 frontend (App Router, English + Urdu RTL), Python FastAPI
OCR service. Cookie sessions with CSRF, MFA/TOTP, server-resolved active business
context and role-based permissions. Roles are OWNER, ACCOUNTANT, MANAGER, VIEWER and
SALESPERSON. No client-supplied tenant overrides are accepted.

**Business modules** (`ModuleRegistry`, 15 canonical keys). `core_finance` is required;
`documents`, `whatsapp` and `ai` are legacy defaults; the others are `transactions`,
`receivables`, `invoices`, `projects`, `costing`, `inventory`, `production`,
`commissions`, `multi_currency`, `operations` and `reports`. Writes to a disabled
module are refused server-side (`403 module_disabled`). Reads of retained history
stay available.

**Backend domains** (`backend/src/main/java/com/app/sme_health_backend/`). Identity,
security, MFA, audit and crypto (AES-256-GCM with versioned keys); configuration
and module resolver; accounting ledger, transactions, transfers, reconciliation,
recurring and drafts; counterparties, obligations, payments and advances;
commercial documents (invoices/bills); inventory and COGS; projects, estimates,
samples and project attribution; commissions; FX; cash flow, forecast, liquidity,
funding, risk and reports; monthly records bridge and health scoring
(`health-score-v1`, 30/25/20/15/10), insights and recommendations; documents with
OCR review (evidence-only; confirmation never posts financial facts); search; zakat;
WhatsApp outbound summaries and inbound assistant; AI Copilot with read-only
deterministic tools (`FinancialQueryFacade`). All financial figures are computed by
the backend; the frontend formats and displays them only.

**Provider posture in this candidate.** AI uses mock/offline unless explicitly
activated. WhatsApp outbound defaults to `mock` in development and is refused in
production (`disabled` or fully configured `meta` only). Inbound WhatsApp is off
unless every secret is provided. OCR is configurable. No live provider was activated
or contacted for this candidate.

**Demo tooling** (`scripts/`). Local-only synthetic demonstration:
`init-demo.ps1` → `start-demo.ps1` → `stop-demo.ps1`. Public exposure is refused.
See section 7.

---

## 3. Final test results on the frozen tree `19547bf`

All results below are fresh executions on `19547bf`. Earlier stage results are not
substituted. Local runs used an isolated worktree with JDK 26.0.1 targeting
Java 21, Node 26.13.0, Python 3.11.9 (venv from the repository's tested constraints)
and frontend dependencies from a pnpm 10.28.2 frozen-lockfile install (Next 16.4.1,
Playwright 1.62.0 Chromium). The lockfile and `package.json` are identical to this
tree apart from line endings. Fresh embedded PostgreSQL, random ports and passwords,
synthetic data only. No Docker, demo database on 5433, public tunnel or real
provider was used.

| Gate | Local result (Windows) | Hosted (Ubuntu) |
|---|---|---|
| Backend unit suite | **179 classes / 1,846 tests, 0 failures/errors**, 1 skip (Windows cannot create the symlink fixture in `LocalFileSystemStorageServiceTests`). Includes `DemoContainerOwnershipTest` **9/9** and `DemoToolingContractTest` **12/12** | `backend-unit` **success** |
| PostgreSQL integration + migrations (`scripts/release/postgres_gate.py`) | **91 IT classes / 485 tests, 0 failures/errors/skips** (fresh, upgrade and one-shot migrations; finance, lifecycle, security, isolation, provider modes; fresh-only demo seeding 5/5) | `postgres (1..4)` **success** |
| Frontend unit/contract | **482/482**, 0 skipped | `frontend` **success** |
| TypeScript / ESLint | Pass / 0 errors, 51 existing warnings | Same warnings as annotations |
| Production build (`next build --webpack`, live mode) | Pass | Pass |
| Real full product browser (Spring + PostgreSQL + Next) | **26/26** (run twice, once in each wrapper below) | Runs in `browser-smoke` on v3 pushes (`--candidate-v2` contract pack): **success** |
| Release smoke browser | **6/6**, 36 page loads, no rebuild storm | `browser-smoke` **success** |
| Original independent DEF-08 assertions (unchanged Stage-A wrapper) | **DEF-08-WHATSAPP-EMPTY-HISTORY PASS, DEF-08-COPILOT PASS** | — |
| Stage-C capability wrapper (unchanged) | 26/26 + both DEF-08 **PASS** + capability read-only probe **PASS** | — |
| Provider-disclosure browser | **10/10** | Runs in `browser-smoke`: **success** |
| Historical browser | **5 drivers / 86 checks** (landing 20, primary 24, phase-two 20, extra 10, risk 12) | `historical-browser` **success** |
| Demo standalone (real build) | **PASS**: unpackaged build rejected (24/24 assets 404); packaged serves 3 CSS + 21 JS (200) | Runs in `browser-smoke`: **success** |
| OCR Python | **248/248** | `ocr-and-operations` **success** |
| Java–FastAPI HTTPS OCR contracts | **15/15** (`OcrFastApiClosureProbe` 4 + `OcrIntegrationConfigurationTests` 11), 0 skipped | — |
| Backup / production configuration / release tools | **63 / 17 / 14** | Included in hosted jobs |
| V52 ACL inventory review | All **84** table ACLs match the frozen migration statements | — |
| Dependency audit (strict OSV) | **0 matches, 0 lookup errors** (npm 440, PyPI 44, Maven 136) | `resolved-dependencies` **success** |
| Current-source secret scan | — | `current-source-secrets` **success** |

### Hosted workflows for the frozen tree `19547bf`

- [Release validation — success](https://github.com/aviongg/sme-financial-webapp/actions/runs/43884590163). Jobs: frontend, ocr-and-operations, backend-unit, browser-smoke, postgres 1–4, release-gate
- [Historical browser reconciliation — success](https://github.com/aviongg/sme-financial-webapp/actions/runs/43884589811)
- [Dependency and secret checks — success](https://github.com/aviongg/sme-financial-webapp/actions/runs/43884589992). Jobs: resolved-dependencies, current-source-secrets

The documentation-only head that adds this file triggers its own runs. Their status
is reported in the publication handoff and must be checked against that exact SHA.
Hosted job logs and XML need repository-admin access; the hosted per-test totals
above are taken from job conclusions, not from re-parsed logs. Workflow artifacts
expire after 90 days. Archive any evidence you want to keep.

### Local execution notes (environment, not code)

- `postgres_gate.py` was run with `FINSIGHT_MAVEN=<absolute path>\backend\mvnw.cmd`,
  because Windows Python cannot resolve the bare `mvnw.cmd` name.
- The production-configuration tests used `FINSIGHT_TEST_SH=C:/Program Files/Git/bin/sh.exe`, as documented in Stage A.
- The OCR suite used a scratch `--basetemp`, because this host's default pytest temp
  folder was not accessible. With it, all 248 passed and no test was changed.
- `DatabaseRolePrivilegesPostgreSqlIT` and `PostgreSqlTlsConnectionIT` are
  production-target suites, excluded by the gate by design. They were **not** run.

---

## 4. DEF-01 – DEF-23 status matrix (as of `19547bf`)

| ID | Severity | Status | Basis |
|---|---|---|---|
| DEF-01 | P0 | **Closed** | Independent v2 runtime closure; manual-review/non-posting/provenance regressions pass in the 26-check real gate and PostgreSQL suite |
| DEF-02 | P0 | **Closed** | Seeded configurations with 15 canonical keys; fresh-seeding PostgreSQL contract passes |
| DEF-03 | P1 | Closed | Dashboard widget library/preferences; regressions pass |
| DEF-04 | P1 | Closed | Module write enforcement / retained reads; module-enforcement ITs and real gate pass |
| DEF-05 | P1 | Closed | Inventory item IDs persisted on invoice/bill; real gate stock assertions pass |
| DEF-06 | P1 | Closed | Return selector sends the exact issue ID; stock/COGS assertions pass |
| DEF-07 | P1 | Closed | Recognized-document lifecycle guidance; recognition/lifecycle ITs pass |
| **DEF-08** | P1 | **Closed in the tested scenarios** | See section 5 |
| DEF-09 | P1→P2 | Original slice closed; **remaining P2 open** | `DIRECT_SALE_RECEIPT` still shown as a raw activity enum (EN/UR) |
| DEF-10 | P1 | Closed | Add-another-business flow |
| **DEF-11** | P1 | **Open — product decision required** | Cross-tab business switch clears unsaved work without warning. The security clearing is correct and must stay; the UX decision is pending (section 6) |
| DEF-12 | P2 | Open | Lifecycle evidence still needs a typed upload UUID |
| DEF-13 | P2 | Closed (misleading-posting claim) | OCR confirmation is evidence-only by design |
| DEF-14 | P2 | Open | Navigation: bill OCR evidence link, payable routing, commercial DOCUMENT badges |
| DEF-15 | P2 | Deferred / device-unverified | Explicit camera `capture` absent; native camera not tested |
| DEF-16 | P2 | Open | `/search?q=` does not prefill the query input |
| DEF-17 | P2 | Intentional | Role read-only boundaries verified safe; wording polish only |
| DEF-18 | P2 | **Code corrections integrated; operator acceptance pending** | Fresh-only seeding, HTTP/asset readiness, owned processes, local-only (`086957d`) and verified container ownership (`f1ec564`) integrated and contract-tested. The real Docker rehearsal is **NOT VERIFIED** (section 7) |
| DEF-19 | P3 | Open | Anonymous browser Back keeps the sign-in view |
| DEF-20 | P3 | Open | Active-business badge hidden at 390×600 on invoice entry |
| DEF-21 | P0 | **Closed** | Seeded businesses accept canonical configured writes |
| DEF-22 | — | **Retracted (false positive)** | Real writes are `/api/inventory/**` and are enforced. Do not revive `/api/stock/**` |
| DEF-23 | P1 | Closed | Unconfigured business exposes all implemented modules; configured choices round-trip |

No P0 is open. The open P1 is DEF-11, which is a product/UX decision, not an engineering
defect; no new critical application-level blocker was found in this run.
DEF-09 (remaining), 12, 14, 15, 16, 19 and 20 are deferred P2/P3 usability items.

---

## 5. DEF-08 resolution (tested scenarios)

| Slice | Result on `19547bf` |
|---|---|
| Copilot `provider_unavailable` answer under an AI (`openai`) status | **PASS**, original independent assertion unchanged. Shown as an evidence-based fallback that is "not AI-generated"; exact values retained |
| WhatsApp delivery-history rows | **PASS**: mock = simulated/no transmission; disabled = nothing transmitted; Meta and unknown are distinct; recorded statuses preserved |
| WhatsApp outbound card with **empty** delivery history (real Spring, mock provider) | **PASS**, original independent assertion unchanged. The card reads "Simulated: a test provider is active. No WhatsApp messages are transmitted…" |
| Mock / disabled / configured Meta / unknown in EN and UR | **PASS** (provider browser suite; real-Spring PostgreSQL contracts for mock/disabled/Meta wiring) |
| Capability endpoint read-only and authorization | **PASS**: `GET /api/whatsapp/delivery-capability` needs `FINANCIAL_DATA_READ`. Salesperson gets 403, anonymous 401, writes 405. Financial facts are unchanged before and after |

**Boundary.** This resolves the disclosure finding for these contracts and UI
scenarios. It is not acceptance of real Meta, OpenAI or Google accounts,
credentials, templates, quotas or delivery. A configured Meta provider is shown as
configuration only, never as proof of transmission.

---

## 6. Remaining product decisions and deferred UX

- **DEF-11 (P1 — decision needed).** When another tab switches the active business,
  open forms for the old business are cleared without a dirty-form warning. Clearing
  is the correct security behavior and must not be weakened: no cross-business data
  or stale mutation replay. The decision is whether to add an unsaved-work warning or
  notice, and its exact design. Owner: product, with Suleman.
- **Deferred P2/P3 usability** (Suleman-led review): DEF-09 remaining raw activity
  enum, DEF-12 evidence UUID input, DEF-14 navigation links, DEF-15 camera capture
  (needs a real device), DEF-16 search prefill, DEF-19 browser Back, DEF-20 mobile
  business badge. DEF-17 wording polish is optional.

---

## 7. Demo tooling: verified safeguards and limitations

**Verified on the merged tree** (fixture-backed and contract tests; no real Docker):

- **Container ownership.**
  - `init-demo.ps1` labels the new container with a fresh instance GUID. It proves Compose created exactly that container and records its full ID, creation time, instance, Compose working directory, volume and checkout in `.demo-runtime/environment.json`.
  - `start-demo.ps1`, `stop-demo.ps1` and `backup-demo.ps1` act only on the container whose recorded **ID** still matches every recorded fact, and address it by that ID.
  - Shutdown additionally needs the launch's `runtime.json` to name the same container.
- **Negative cases tested** (the exact scripts with an isolated Docker *function* fixture): foreign container with the demo name, stale ID, recreated, renamed, different instance label/project/volume/creation time, different checkout, old-format manifest, runtime/environment mismatch, container replaced mid-start, reused PID, missing manifests. Every case leaves all containers untouched (no start, stop or exec), exit 3/4.
- **Positive cases tested:** authorized shutdown stops the launcher-owned process and the verified container by ID, keeping the volume and document store. Startup-failure cleanup stops only a container the launcher itself started that still verifies.
- **Original probe:** Codex's unchanged Stage-B probe (`Probe-ContainerShutdown.ps1`) now records **zero** Docker calls without a runtime manifest. Previously it recorded stop requests for `finsight-postgres-demo` and `finsight-tunnel-demo`.
- **Disabled and removed:** legacy tunnel shutdown removed; `-PublicTunnel` refused (exit 2); sandbox reset and demo restore refused (exit 2); init requires `-CreateFreshDisposableDemo`.
- **Preserved:**
  - fresh-only seeding (the seeder requires the disposable marker and empty tables);
  - V52 migration check;
  - HTTP and static-asset readiness;
  - loopback binding;
  - live providers off.

**Limitations (not verified / still open):**

- **Real Docker-backed init → start → verify → stop rehearsal: NOT VERIFIED.** This
  host has no Docker CLI or daemon, on Windows or in WSL. It needs an authorized,
  disposable Windows runner with Docker Desktop (Linux containers) and Compose v2.
  Use Java, Maven, Node and pnpm, free ports 5433/8080/3000 and no existing demo
  container or volume. Build the jar and the frontend with
  `BACKEND_API_URL=http://127.0.0.1:8080`, then run
  init → start → HTTP/API/assets/all five seeded roles → stop. Confirm the container
  is stopped by ID and the volume and documents remain.
- **Not exercised against real Docker:** the exact `docker container inspect` output
  format and the Compose `working_dir` label value. Any mismatch makes init or start
  **refuse** (fail safe); it may need adjustment during the rehearsal.
- **Environment migration:** demo environments created before `f1ec564` lack the
  identity record and are refused. Remove them deliberately and re-run init.
- **Platform coverage:** port-ownership and startup tests are Windows-only. On hosted
  Linux they are skipped by assumption where PowerShell or the Windows cmdlets are
  unavailable.
- **Known demo secrets:** the demo DB passwords and the static demo AES key are
  committed. They are for synthetic local use only and must never be reused.
  Every seeded role is a member of every seeded business.
- **Stale scripts:** `verify-public-demo.mjs` and two older browser scripts still
  point at an obsolete public tunnel URL. They are unused, because public exposure
  stays disabled.

---

## 8. Outstanding production / provider / recovery acceptance

None of the following is covered by this freeze:

- PostgreSQL 17 with TLS/HBA and production grants (`DatabaseRolePrivilegesPostgreSqlIT`,
  `PostgreSqlTlsConnectionIT`, `V52ProductionPrivilegesAcceptance` on real infrastructure).
- Container image builds and image scanning (`images.yml` is manual) and Git-history
  secret scanning.
- VPS/ingress/TLS termination, secret provisioning (ConfigTree `crypto_key_k1` etc.)
  and session cookie `Secure` in the deployed environment.
- Real providers: Meta WhatsApp (templates, credentials, delivery), OpenAI activation
  (`AI_ACTIVATION_RUNBOOK.md`), Google Vision OCR, SMTP.
- Paired database, document-store and all-historical-key backup/restore, rollback
  drill and measured RPO/RTO.
- Real-device camera (DEF-15) and accessibility review on devices.
- Public synthetic demo and real-client pilot approval.

---

## 9. Technical handoff checklist for Suleman

1. **Check out the exact frozen tree** in a new, isolated worktree. Do not reuse
   another person's worktree, demo database or running processes.
   ```sh
   git fetch origin
   git worktree add --detach ../finsight-v3-review 19547bf3dda9744d51e74c87a908e2e42577299c
   # or the published documentation head:
   git worktree add ../finsight-v3-review-head origin/integration/finsight-production-candidate-v3
   ```
2. **Verify provenance:**
   - `git log --graph --oneline 71ac4e0..19547bf`;
   - `git show --stat 19547bf`, which should list nine demo-tooling files only;
   - `git diff e2fcb97 19547bf -- backend/src/main frontend/src database`, which should be empty.
3. **Review by area** (suggested order):
   - DEF-08 code (`WhatsAppDeliveryCapabilityController`, `providerDisclosure.ts`, `LiveSettings.tsx`, `OperationsViews.tsx`);
   - demo tooling (`scripts/demo-guards.psm1`, `init/start/stop-demo.ps1`);
   - CI gates (`.github/workflows/*.yml`, `scripts/release/`);
   - Developer A domains you own (ledger, obligations, funding, commissions, FX), all unchanged since v2.
4. **Re-run the gates** with pinned tooling (Java 21, Node 22, Python 3.11, pnpm 10.28.2,
   Playwright 1.62.0). Follow `docs/release/candidate-v3-stage-c/README.md`; the
   Stage-A wrapper also still runs unchanged. On Windows, set `FINSIGHT_MAVEN` for
   `postgres_gate.py` and `FINSIGHT_TEST_SH` for the configuration tests. Run browser
   harnesses one at a time.
5. **Confirm the hosted runs** for the exact published head (section 3) before signing off.
6. **Decide or assign:**
   - DEF-11 UX disposition;
   - P2/P3 usability backlog ownership;
   - who runs the Docker demo rehearsal and on which machine;
   - production/provider/recovery acceptance owners (section 8).
7. **Sign-off record:** add your review outcome as a new document. Do not edit the
   Stage-A/B/C reports or this handoff's results; they are historical evidence.

---

## 10. Recommended procedure for future branch integration

- Branch every fix from the **exact frozen SHA** (`19547bf` or the published head),
  never from a moving branch tip. Name it `fix/finsight-v3-<topic>` and keep each branch
  to one topic.
- Integrate only reviewed, exact heads with `git merge --no-ff <sha>` in an isolated
  worktree. Never rebase, squash, amend or force-push shared branches. Do not merge
  candidate branches into `main` without an explicit release decision.
- After each merge, check:
  - that both parents are as intended;
  - `git diff <previous head> <merge> --stat`, confirming only the intended paths changed;
  - that protected areas (`backend/src/main`, migrations V1–V52, permissions,
    financial services) are untouched unless that is the reviewed purpose.
- Never modify existing migrations. New schema changes need a new, reviewed migration
  number (coordinate reservations in `docs/phase2/IMPLEMENTATION_STATE.md`).
- Keep all CI gates. Required checks are `release-gate`, `resolved-dependencies`,
  `current-source-secrets` and `historical-browser`. Do not add retries, skips,
  `continue-on-error`, relaxed assertions or scanner suppressions to get a green run.
- Record fresh evidence per stage under `docs/release/candidate-v3-<stage>/` and never
  relabel earlier results as new runs. Report hosted runs only against their exact SHA.
- Synthetic data only; no customer data, public tunnel, live provider or deployment
  without separate explicit approval.

Earlier reports remain authoritative for their own stages:
[Stage A](CANDIDATE_V3_STAGE_A_VERIFICATION.md),
[Stage B](CANDIDATE_V3_STAGE_B_VERIFICATION.md),
[Stage C](CANDIDATE_V3_STAGE_C_VERIFICATION.md).
