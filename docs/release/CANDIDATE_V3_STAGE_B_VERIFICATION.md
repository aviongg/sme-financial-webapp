# Zarwia Candidate v3 — Stage B demo-tooling integration

**Stage B integrated; Candidate v3 is NOT final. No main merge or deployment.**
Branch: `integration/finsight-production-candidate-v3`.
Worktree: `C:/Users/Hp/FYP/sme-financial-webapp/tmp/finsight-production-candidate-v3`.
Application/demo verification head: **`aa0b9e15ebc6dd1274983c31b2338fcce7e0e132`**.
The containing publication adds a narrowly justified historical-driver synchronization
fix, disposable verification helpers and evidence/docs only. Resolve its SHA with
`git log -1 --format=%H -- docs/release/CANDIDATE_V3_STAGE_B_VERIFICATION.md`.

**Outstanding P1:** DEF-08's outbound WhatsApp card still has no provider disclosure
with empty delivery history. Claude's separate fix is **not** included. A separate
demo shutdown safety gap was reproduced using a Docker function fixture: the stop
script stops fixed-name containers without checking a runtime ownership record.
The full Docker initialization/start/verification/stop sequence is **not verified**.

## Integration and preservation

- Exact Stage-A parent: `93c268dacf9825f600f8e7997f6db1ad63c40ecb`.
- Exact authorized demo head: `086957dfdb421e1d62bed64c43e124c97cc1f915`.
- Normal no-ff merge: `ce68723e5526b98b70be4cd74984ebc3438b0b54`, with those
  two exact parents. The prior `7c784c2` is included through its final successor;
  the old public-tunnel implementation is superseded, not selected as the final tree.
- Two expected conflicts: `.github/workflows/validation.yml` and
  `frontend/package.json`. Both were resolved as a **union**. Normal release,
  security, historical-browser, provider-disclosure and full v3 product gates remain;
  `test:demo-standalone` is added alongside them. All package scripts from both
  parents remain, with unchanged dependency versions and lockfile.
- CI-preservation regression at `aa0b9e1` now explicitly requires the standalone
  step as well as provider/full/historical gates. Discovered PostgreSQL gates
  automatically include `DemoSeederFreshDatabasePostgreSqlIT` (88 classes total).
- Production frontend/backend application trees, V1–V52 and runtime probes remain
  byte-identical to Stage A. Demo source scripts match the final authorized demo
  head exactly. No accounting, tenancy, role permissions, migration or unrelated
  feature change was made. Source branches, `main` and Suleman's branch are untouched.
- The incomplete DEF-08 branch was not merged. Stage-A and independent-v2 reports
  remain unchanged historical evidence, not relabelled Stage-B results.

## Disposable environments and actual results

Local Windows uses JDK 26.0.1 targeting Java 21, Node 26.13.0, pinned pnpm 10.28.2,
Python 3.12 and declared Playwright 1.62.0 Chromium. Fresh embedded PostgreSQL
14.22 uses random ports/passwords, restricted migrator/runtime roles, V1–V52,
task-owned document directories and synthetic keys. No demo/customer database,
public tunnel or real Google/OpenAI/Meta/SMTP provider was contacted.
Hosted runs use Ubuntu 22.04 / Java 21 / Node 22 / Python 3.11.

| Gate | Actual fresh result / boundary |
|---|---|
| Complete backend unit | **1,833 cases**, zero failures/errors locally and hosted. **One platform skip on each OS:** Windows skips symlink creation; Linux skips the Windows networking guard. Windows ran all **12 demo-tooling contracts**, including that guard; Linux exercised the storage symlink assertion |
| Complete PostgreSQL | **88 classes / 473 cases**, zero failures/errors/skips on hosted Linux (129 + 98 + 110 + 136). Includes all prior finance/security/migration suites and five new fresh-database contracts |
| Seeder PostgreSQL locally | **5/5 passed**, zero skipped: fresh marked seed once; refused reseed; sentinel profile preservation; existing credentials preservation; unmarked target and invalid entrypoint/reset requests refused |
| Migration/startup | Complete fresh/populated-upgrade/one-shot migration suites passed; real Spring starts with restricted runtime role. Demo seeder tests migrate disposable databases to V52; fixed-port Docker migrator sequence was not run |
| Frontend | **479/479**, zero skipped; TypeScript passed; ESLint **0 errors / 51 existing warnings**; live production build passed |
| Real product / smoke browser | **26/26 + 6/6 passed**, including manual-review non-posting/provenance, financial/stock assertions, module denial, historical reads, tenant isolation and responsive EN/UR |
| Historical browser | Initially local **5/5 / 86 checks** passed; first hosted phase-two failed after re-login. After the diagnosed synchronization fix, all **5/5 / 86 checks** passed locally with strict fallback/security assertions retained |
| Provider-disclosure browser | **9/9 passed**, EN/UR Copilot and mock/disabled/Meta/unknown delivery history; synthetic transport, not real-provider delivery |
| Independent DEF-08 | **Copilot PASS, empty-history WhatsApp card FAIL** on Stage B after the 26-check real gate; aggregate remains **RED** |
| Standalone real server | Correctly rejects unpackaged CSS/JS 404s; after packaging **all 3 CSS + 21 JS assets return 200**. Production standalone server on an owned ephemeral loopback port; no Spring/Docker acceptance implied |
| Standalone/HTTP unit fixtures | Six new cases included in the 479 frontend tests: inert TCP, closed port, wrong HTTP app, root-200/missing-assets, public/static copy with stale replacement, incomplete/mismatched builds |
| Actual seeded authentication | **5/5 logins**, **40 matching-role memberships**, eight businesses each. Owner/Accountant/Manager/Viewer read canonical 15-module configuration; Salesperson correctly receives **403** for general configuration/financial read |
| Positive Windows process guard | Task-owned Node listener passes PID/start-time/port identity; exact recorded child stops and releases its ephemeral port. No unrelated process is stopped |
| OCR / HTTPS | **248 Python tests + 15 explicit Java–FastAPI HTTPS contracts passed**, zero skipped; fake provider and synthetic certificates |
| Backup/configuration/release tools | **63 / 17 / 14 passed**, V52 ACL inventory matches 84 tables. Fixtures, not a physical/operator recovery drill |
| Security | Strict OSV **zero matches / zero lookup errors** (npm 440, PyPI 44, Maven 136); hosted current-source secret check passed. No scanner exceptions added |

See [local manifest](candidate-v3-stage-b/evidence/local-verification.json),
[standalone HTTP results](candidate-v3-stage-b/evidence/demo-standalone.json),
[seeded authentication](candidate-v3-stage-b/evidence/seeded-logins.json) and
[actual process shutdown](candidate-v3-stage-b/evidence/owned-process.json).
No app source was modified to produce these results. The complete PostgreSQL
suite was run on hosted Linux; only the five new seeder cases were repeated locally.

## Tooling review and what remains unverified

**Asset packaging/readiness — component verified.** `demo-runtime.mjs` checks the
build/server/static/public prerequisites and build-ID compatibility, replaces stale
packaged directories and copies `.next/static` plus `public`. Synthetic public
`logo.svg` copy is tested; the real packaged output's public files match source.
HTTP readiness checks a CSRF token, Next health JSON, root response and every
referenced CSS/JS status/content type. The launcher additionally verifies the API
through Next, PID/start-time/port ownership and a unique datasource connection tag.
Its DB readiness is not inferred from merely accepting TCP.

**Fresh-only seeding/preservation — PostgreSQL component verified.** Initialization
requires `-CreateFreshDisposableDemo`, refuses existing named container/volume and
nonempty document storage, proves zero application tables before initialization,
and writes a unique database marker linked to this checkout. The seeder requires
the explicit fresh flag, marker and all readable application tables empty; unknown/
unreadable application data refuses. It no longer rewrites existing users/profiles
or bypasses immutable triggers; reset is disabled. Disposable before/after row
counts and user/profile/business fingerprints remain equal for refused targets.
Known demo credentials are fixture data read from the final source, not production
secrets. Passwords/keys are not copied into this report/evidence.

**Provider/storage isolation — source review and independent-runtime boundary.**
Launcher forces loopback addresses, AI disabled/mock/unapproved, WhatsApp mock,
inbound off and OCR off, clears production profile/real-provider/SMTP overrides,
and uses `.demo-runtime/documents`. SMTP transport is production-profile-only.
The marker is operational provenance, not cryptographic proof against a privileged
operator modifying data. Only newly created synthetic volumes are approved targets.
Known fixture DB passwords/key must never be used for real data or a client pilot.
Provider settings in the actual Docker-launched child remain part of the missing
full rehearsal; no paid/live provider acceptance is claimed.

**Public exposure — disabled, not merely opt-in.** `-PublicTunnel` is refused even
with `-PlanOnly`; removed override parameters cannot re-enable it. There is no
cloudflared startup, LAN bind or non-seeded-data override in the final launcher.
The PlanOnly/refusal contracts execute real PowerShell without starting services.
No public tunnel was opened during verification.

**Process ownership — positive Windows component verified; container gap OPEN.**
The launcher refuses foreign listeners; process records contain PID and exact UTC
start ticks; mismatched/reused PID is left untouched. A live task-owned listener
was positively identified and stopped, and its port was released. However,
`stop-demo.ps1` unconditionally checks/stops containers named `finsight-postgres-demo`
and legacy `finsight-tunnel-demo`, even when no runtime record exists. The exact
script with a **Docker function fixture only** issued both stop requests without
a runtime manifest. No real Docker/container or unrelated OS process was stopped.
See [negative ownership evidence](candidate-v3-stage-b/evidence/container-shutdown.json).
This is a **P1 demo operator-safety gap**, not an accounting defect. Claude/owner
review should require recorded container identity/marker ownership before stopping
anything; Stage B does not certify unrestricted safe shutdown or silently fix the
final source branch. Do not execute this stop script against unverified named services.

## Full local-demo rehearsal: NOT EXECUTED

This Windows host has PowerShell 5.1/7 but **no Docker CLI on PATH or at the normal
Docker Desktop location**, and therefore no accessible supported Docker daemon.
The actual initializer was invoked only after proving Docker absent and returned
**exit 4, `REFUSED: Docker is required.`**, before creating resources. No start or
real stop command followed. Missing Docker is not recorded as a passed drill.
Default Linux CI exercises component contracts/standalone/PostgreSQL; it is not a
supported Windows launcher rehearsal (`Get-NetTCPConnection`, CIM and Windows
`Start-Process -WindowStyle` are launcher dependencies).

Required operator drill remains: a supported isolated Windows runner with Docker
Desktop **Linux containers**, Compose v2, Java/Maven/Node/pnpm, free 5433/8080/3000,
no fixed demo container/volume and fresh task-owned `.demo-runtime`. Build the JAR
and Next with `BACKEND_API_URL=http://127.0.0.1:8080` (rewrites are baked at build
time), then run init → start → HTTP/API/assets/all seeded roles → owned shutdown.
Do not substitute this task's historical-fixture build target 18080 for that demo
build, reuse an existing demo/client DB, or use an occupied listener. Resolve the
container-ownership gap before certifying the shutdown portion. No Docker install,
global service change, database removal or public exposure was attempted here.

## Hosted failure reconciliation and exact evidence

Initial verification head `aa0b9e15ebc6dd1274983c31b2338fcce7e0e132`:

- [Release validation — success](https://github.com/aviongg/sme-financial-webapp/actions/runs/43865504538)
- [Dependency/secret checks — success](https://github.com/aviongg/sme-financial-webapp/actions/runs/43865504920)
- [Historical browser — failure retained](https://github.com/aviongg/sme-financial-webapp/actions/runs/43865504793)

The failed phase-two driver passed its first 19 checks, signed out, then clicked
sign-in and immediately used `page.goto('/ask-finsight')`. The click does not await
SessionProvider login/load; the hard navigation can abort it. Hosted failure
remained on sign-in awaiting `copilot-mode`. The repair waits for the authenticated
**settings** form (the actual inline sign-in return route), asserts the fixture's
Viewer form remains disabled, then navigates. All 20 checks, including the exact
`provider_unavailable` assertion, remain. No timeout increase, retry, skip or
suppression was introduced. This is a test-driver repair, not a product/auth fix.
The initial red artifact is retained alongside the subsequent local full 86-check
pass. A first repair attempt incorrectly waited for a dashboard widget rather
than the inline settings return; that failure is retained too.

The standalone seeded-login probe initially omitted active-business selection and
hit `409 active_business_required`; this was harness setup, not a bad credential.
It now explicitly chooses one of eight memberships and retains the Salesperson
403. A local historical invocation refused a briefly occupied fixture port before
running assertions; it was executed only after the prior task runner released
the ports. No unrelated service was terminated or adopted. An initial log-path
setup error executed no frontend tests. These setup failures remain in scratch
and hash manifests rather than being labelled product passes.

The final publication's workflow runs must be checked by its own exact SHA;
the initial run IDs above are not runs of that later commit. Exact final-head
status/URLs are reported with the publication handoff. Evidence is retained for
90 days; [initial hosted counts](candidate-v3-stage-b/evidence/initial-hosted-verification.json)
and the original failed phase-two result remain auditable.

## Outstanding conditions

The P0 DEF-01/02/21 closures from independent v2 QA remain supported by fresh v3
manual-review/non-posting, seed/module and tenant regression checks; no new P0
was found in the executed Stage-B paths.

1. Integrate/test Claude's separately authorized completed **DEF-08 empty-history**
   application fix in a later step. Do not declare this P1 closed on delivery-row
   disclosure alone; independent Stage-B assertion remains failed.
2. Resolve the **container shutdown ownership** gap and complete the actual fresh
   Windows/Docker init/start/stop drill. Component tests do not certify that sequence.
3. DEF-11 remains the unsaved-work UX decision; tenant isolation is unchanged.
   DEF-09 raw activity enums, DEF-12/14/15/16/19/20 remain for Suleman's usability/
   device review. DEF-22 remains retracted (`/api/inventory/**` is real).
4. PostgreSQL 17/TLS/HBA/production grants, Linux image builds/scans, actual ingress/
   secrets/provider acceptance, paired DB/document/all-historical-key recovery and
   rollback/RPO/RTO operator drills remain separate unverified production gates.

Local synthetic component demonstration is conditional on explicit verified
startup; the complete one-command operator journey is unaccepted. Public synthetic
demo is disabled by design, and a real-client pilot is **not approved**. Stage B
does not authorize a main merge, deployment or final Candidate-v3 designation.
See [reproduction helpers and boundaries](candidate-v3-stage-b/README.md).
