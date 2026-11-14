# Integrated release engineering record

**Current continuation:** [14 November security/recovery handoff](CONTINUATION_HANDOFF.md).
It supersedes the historical dependency/recovery/grant limitations and counts
below while preserving original failed-CI evidence. Overall release remains RED.

Baseline verified after fetching origin: **153e94b214ba24ac1d81a094eff3805334edaad9**,
`handoff/finsight-integrated-production-baseline-20261113`.
Implementation branch: `codex/production-closure-infra-tests`.
Worktree: `C:/Users/Hp/FYP/sme-financial-webapp/tmp/production-closure-infra-tests`.
Audit: 13–14 November 2026, Asia/Karachi. This is infrastructure work, not release acceptance.
Claude retains core/backend production fixes; no accounting logic, policy or V1–V52 migration is changed here.

## Clean clone contract

Supported CI host: Ubuntu 22.04 x86-64, JDK 21, Node 22, Python 3.11,
pnpm **10.28.2**. Use a non-root account: embedded PostgreSQL cannot initialize as root.
Allow Maven/npm/PyPI/browser downloads. The continuation pins the existing font
families as licensed local assets; builds no longer depend on Google Fonts.
Budget disk for dependencies, several disposable PostgreSQL instances and build artifacts;
run backend, frontend build and browser gates sequentially on constrained hosts.

```sh
# Repository root; install pnpm 10.28.2 using your approved Node toolchain.
chmod +x backend/mvnw
cd backend
./mvnw -B test
cd ..
python scripts/release/postgres_gate.py

cd frontend
pnpm install --frozen-lockfile
pnpm test
pnpm typecheck
pnpm lint
NEXT_PUBLIC_DATA_MODE=live pnpm build:release
pnpm exec playwright install --with-deps chromium
cd ../backend
./mvnw -B test-compile dependency:build-classpath -Dmdep.includeScope=test -Dmdep.outputFile=target/test-classpath.txt
cd ../frontend
pnpm test:browser:smoke
# Optional full offline product acceptance (additional financial assertions):
pnpm test:browser:product
```

Windows uses `mvnw.cmd`, PowerShell environment assignments and `pnpm browser:install`
(Chromium); Linux `--with-deps` additionally installs OS libraries. Set `JAVA_HOME` to
your JDK. Browser launch defaults to the repository-pinned Playwright/Chromium, with
`FINSIGHT_BROWSER_CHANNEL=msedge` available only as an explicit optional channel.
`PLAYWRIGHT_MODULE` remains an explicit legacy override, never a prerequisite for these gates.
The smoke/product harness owns ports **8093, 3593, 3592**, refuses occupied ports,
starts real Spring with disposable PostgreSQL, and uses synthetic business data and
**mock AI**, without real provider calls. Run sequentially. It retains runtime logs and
screenshots in ignored `frontend/test-artifacts/`; no prior Failsafe XML is required.
Constrained hosts may explicitly choose task-owned scratch storage with
`FINSIGHT_BROWSER_TMP_DIR` and a smaller JVM heap with `FINSIGHT_BROWSER_JAVA_HEAP`
(for example `512m`); these are optional, not hidden machine dependencies.

```sh
# Fresh Python virtualenv; activate before these commands.
python -m pip install -c ai-service/requirements-tested.txt -e './ai-service[test]' -r scripts/release/requirements.txt
cd ai-service
python -m pytest -q
cd ..
python -m unittest discover -s scripts/backup -p 'test_*.py' -v
python -m unittest discover -s scripts -p test_production_configuration.py -v
python -m unittest discover -s scripts/release -p 'test_*.py' -v
# Export Maven test-classpath.txt as above before this read-only online audit:
python scripts/audit_dependencies.py
```

OCR pytest prevents real provider calls. Backup fixtures exercise real cryptography
with simulated PostgreSQL/process boundaries. Neither is a deployed acceptance gate.

## Baseline audit and changes

| Area | Exact-baseline gap | Closure change / remaining limit |
|---|---|---|
| GitHub | No workflows | PR/push validation, four automatic PostgreSQL shards, aggregate `release-gate`; independent dependency/secret checks |
| Backend | Supported tests existed; memory limits/agent setup depended on local commands | Bounded serial forks, bounded Spring context cache, explicit Mockito agent; no changed financial assertions |
| PostgreSQL | `postgres-it` also picked up two production-target classes without fixtures | Normal profile excludes only named role/TLS classes; explicit `production-db-acceptance` profile; discovery gate rejects missing/skipped reports |
| Migrations | V1–V52 and populated upgrade tests existed | Automatically included by IT discovery; SQL unchanged; PostgreSQL 17 container TLS still external |
| Frontend | npm/pnpm authorities disagreed with Docker; no declared Playwright | One frozen pnpm lock for CI/Docker, pinned Playwright 1.62.0 and install command; Node 22 image |
| Baseline assertions | Landing test required the pre-session-hint guard; TypeScript lacked one upload-link event type | Test now asserts the actual integrated guard; explicit event type only, no auth/upload behavior change |
| Browser | Hardcoded Windows Java executables, semicolon classpath, previous IT XML, system Edge | Exported Maven classpath, portable Java argfile, declared Chromium, real-runtime smoke and optional full journey |
| Readiness | Only PostgreSQL health; OCR merely started; edge returned static 200 | Backend startup/DB probe, Next liveness, CA-verified OCR process probe, service health dependencies; no claim of provider or schema acceptance |
| Images/context | Local `latest` tags, Node 20, nested worktrees/test output included in build context | Explicit release tag required, Node 22, ignore tmp/test artifacts/virtualenvs; base image digests still must be recorded |
| Dependency/security | Dated snapshots, audit read npm lock and old Surefire report | Current pnpm/Python/resolved-Maven OSV gate fails on lookup errors/advisories; independent source-secret scan; image/history scans external |
| Recovery | Legacy manifest predated V52 | Continuation covers all 84 tables/64 triggers/18 functions, nine encrypted fields and paired DB/doc/key sets; physical PG17/operator drill still pending |
| Runbooks | Security bootstrap guide and historical V17/V37 setup/manual-monthly steps mixed with current architecture | V52 entry point, current commands, staging/VPS procedure and explicit supersession notices |

Configure repository branch protection/rulesets to **require** `release-gate`,
`resolved-dependencies` and `current-source-secrets` on integration PRs. Workflow files alone
cannot prevent an administrator/unprotected branch from bypassing checks. This task does
not change repository settings, merge a PR or deploy. The full browser journey is manual
workflow-dispatch input `full_browser`; production-provider/DB acceptance is never normal CI.
Scheduled security checks on GitHub run only after this workflow reaches the default branch.
The manual `Offline release image builds` workflow builds/scans all six candidate images,
without secret mounts, a registry push or deployment. Passing builds/scans still do not prove
production TLS/bootstrap/runtime readiness; use the separate staging acceptance procedure.
Direct action references are pinned to verified official commit SHAs, with version comments.

## Probe contracts

Backend `/internal/health/live`: HTTP process responds. `/internal/health/ready`:
Spring accepts traffic and the configured runtime DB login can execute `SELECT 1`;
failure gives 503 with no diagnostics. Production connection acquisition is bounded
to 3 seconds; query timeout is 2 seconds. Neither verifies Flyway history, domain grants,
document storage, SMTP, OCR extraction, OpenAI or Meta. The migrator remains a mandatory
separate one-shot gate; runtime health never authorizes skipping it.

Next `/internal/health` proves HTTP availability. OCR `/health` is process availability
over verified TLS, not a credential/quota/provider readiness promise. Nginx `/healthz`
is edge liveness only. Compose dependency health controls startup order, not continuous
traffic withdrawal or automatic recovery; an unhealthy running container is not restarted
solely because of its health status. Monitor these signals and the deployed HTTPS journey.

## Provider/deployment truth

| Path | Repository/deployment state | External acceptance |
|---|---|---|
| OCR | Production Compose enables Java→FastAPI HTTPS and supplies Google Vision credential-file mount plus allowlisted CONNECT proxy | Actual service-account permissions, billing/quota, DNS/proxy, signed TLS and a controlled receipt extraction; confirmation remains evidence only, never monthly posting |
| OpenAI Copilot | Application defaults `AI_ENABLED=false`, `AI_PROVIDER=mock`; normal tests explicitly simulate AI. Production Compose does not pass activation, allowlist or key settings | Explicit owner activation, model/access/budget, allowed businesses, read-only grounding/refusal/tenant review, rate/timeouts and paid-provider acceptance |
| Meta outbound | Application defaults `WHATSAPP_PROVIDER=mock`; Compose has no real Meta token/number configuration | Phone/app approval, token lifecycle, approved templates, actual delivery/opt-out and failure handling |
| Meta inbound | `WHATSAPP_INBOUND_ENABLED=false`; app supports signed webhook and ConfigTree inbound secrets; Compose does not mount/pass those | Webhook registration/verification, HMAC replay/dedupe, sender possession and consent, current membership/role recheck and real inbound/outbound loop |
| SMTP | Production Compose requires provider host/user/from and mounts password | Trusted STARTTLS, verified sender, actual reset receipt and one-time/session revocation checks |

An operator `.env.production` entry is **not** automatically inherited by a container:
Compose must explicitly map it or mount a ConfigTree secret. Before enabling a provider,
review the deployed effective property names against `application.properties`, use individually
scoped mounted secrets, and keep tokens out of command lines, logs and frontend builds.
Do not claim these providers are configured merely because adapters exist. The canonical
baseline's local demo Compose contains a fixed demo database password and broad DBA account:
it is a public fixture, never a production credential/template. Do not reuse its volume.

## P0/P1 risks and external release gates

No new P0 accounting finding is claimed by this infrastructure audit. These **P1 release
blockers** remain until evidence is recorded:

1. **V52 disaster recovery needs a real production-like drill.** The continuation
   extends the frozen catalog/grants, all-row ciphertext verification and paired
   encrypted DB/document/historical-key tooling; see [the recovery contract](V52_RECOVERY.md).
   Real disposable PostgreSQL catalog and crypto/SQL-boundary fixtures pass, but
   populated PostgreSQL 17 physical restoration/corruption tests remain external.
2. **No validated application/volume/key mount and rollback on an actual server.**
   Paired staging verification is not production acceptance. Rehearse restored file
   access, cross-tenant denial, image/connection/volume switch-back and measured RPO/RTO.
3. **Real-provider activation remains unwired/unaccepted.** Keep unavailable capabilities
   explicitly disabled; real credentials/approval and a reviewed deployment configuration
   are required. No invented credentials or automatic paid calls are introduced here.
4. **Actual Linux images, PostgreSQL 17 TLS/HBA/grants, ingress and secret permissions remain
   deployment acceptance gates.** Fixture/embedded results do not cover them. Test fresh
   installation and populated V52 upgrade; execute the production-target Java profile, now including the V52 catalog/role
   acceptance class and backup-role login. No real PostgreSQL 17 acceptance is claimed.
5. **Merge protection/host operations are not configured by source changes.** Require the
   CI statuses, retain immutable image digests, configure monitoring/certificate renewal,
   backup retention/off-site replication, disk alerts and an owner for incident recovery.
6. **Dependency matches closed; image/provider security remains external.**
   All 95 original advisory records were individually reviewed. Minimal locked
   upgrades now scan 620 versions with zero matches/errors, independently passing
   hosted security run 43601102261. [Triage and upgrade evidence](security/UPGRADE_LOG.md)
   retains the original findings. Nothing is suppressed; this does not accept final
   image OS packages, history secrets or real provider trust compatibility.
7. **Clean-clone CI exposed a core recurring-generation race.** Shard 4 failed in
   `RecurringTransactionsPostgreSqlIT.testConcurrentGenerationRacingAndIdempotency`
   with an unexpected optimistic-lock exception during two simultaneous calls.
   This remains Claude's production fix; no financial concurrency logic or assertion is
   weakened here. [Reproduction, evidence and investigation handoff](CORE_BACKEND_FINDINGS.md).
8. **Two further backend acceptance contracts need owner resolution.** Linux unit tests
   fail the Windows-style storage-path rejection expectation. A repaired, valid synthetic
   score fixture exposes derived-month writes during the facade's declared read-only
   snapshot test (row count 1 → 3). Neither observation by itself proves an outside-file
   read or authoritative ledger mutation. Preserve the assertions and resolve the contracts
   with Claude; see the same [core handoff](CORE_BACKEND_FINDINGS.md).

See [staging/VPS procedure](STAGING_DEPLOYMENT.md) for the release evidence and rollback record.

## Verification / persistent handoff

Verification on the published infrastructure candidate `7f13a0ac6675d33850efe0a1fb55372e3a987706`:

| Gate | Fresh result |
|---|---|
| GitHub clean clone | [Release validation](https://github.com/aviongg/sme-financial-webapp/actions/runs/43494083018): **failed**, exposing existing/backend contract issues; no retries or exclusions added |
| Linux backend unit | 1,789 cases, **1 failure**, 0 errors/skips: Windows-style path rejection expectation; see core handoff |
| Linux disposable PostgreSQL | All **80 classes / 447 cases** executed in four shards: 446 passed, **1 error**, no skips; recurring-generation race in shard 4. Fresh/upgrade migration tests included; two named production-target classes excluded, not passed |
| Linux frontend | Frozen install, 460 tests, TypeScript, lint and webpack build **passed** |
| Linux browser | Real Spring/disposable PostgreSQL + production Next + declared Chromium smoke **passed** (6 checks, 36 EN/UR desktop/mobile page loads); full optional product mode not executed |
| Linux OCR/operations | OCR, backup, production config and gate-parser job **passed** |
| Source secrets | Hosted Trivy current-source scan **passed**; no Git-history guarantee |
| Dependencies | [Security run](https://github.com/aviongg/sme-financial-webapp/actions/runs/43494082894) **failed as intended** on advisory matches; zero lookup errors, finding artifact retained |
| Windows unit coverage | 1,789 fresh XML cases, 0 assertion failures/errors, 1 platform skip across the attempted full run plus four-class recovery (45 cases). The aggregate command itself failed on JVM startup memory errors; this is not a green single-run claim |
| Windows frontend / operations | 460/460, TypeScript, lint (0 errors/51 warnings), release webpack build, 248 OCR cases, 46 encrypted backup fixtures, 17 production-config checks and 4 report-gate regressions passed |
| Repaired facade fixture | Six PostgreSQL cases: **5 passed, 1 failure** (derived-month row-count invariant 1 → 3). Assertion retained and core-owner resolution required |
| Runtime images / deployed stack | Not executed here: no local Docker/VPS production fixture; manual image build/scans and staging gates remain external |

CI uses Ubuntu 22.04, Java 21, Node 22 and Python 3.11 from a clean checkout; it does
not use the local D: workaround or another tool's browser installation. All direct action
refs resolved and executed successfully; failure statuses come from the actual gates.
The latest fixture-only commit is independently verified by its failing invariant; later
publication runs may add evidence but do not waive the first recurring race or dependency findings.

The partial local monolithic PostgreSQL attempt was stopped after the four hosted shards
had executed all 80 classes. It is not counted as a full local pass. Only task-owned
processes were stopped and their disposable PostgreSQL instances shut down; no persistent
developer/customer database was targeted.

Local runtime is Windows, Node 26.13.0, JDK 26.0.1 compiling release 21, Python 3.12.
Pinned pnpm 10.28.2 frozen install succeeded; existing dependency versions were retained
except the newly declared Playwright dependency. This host has a nearly full C: drive and
low available virtual memory. Only this task's dependencies/build scratch were placed under
`D:/FinSightRelease/153e94b-infra-tests`; those paths are local workarounds, never prerequisites
of CI or the clean-clone commands. No hidden Playwright installation is used.
Next's Windows webpack paths could not span C: source and D: packages, so the successful
local build used a copy of current frontend source on D: with the output linked back into
this worktree. The independent hosted Node-22 frontend/browser passes establish the
ordinary clean-clone path; the same-drive copy is not the deployment procedure.

Initial failures retained in task-local ignored logs: native JVM allocation failure,
blocked Mockito self-attachment (fixed by declared test agent), system-drive Python install
failure, inaccessible pytest temp directory, cross-drive module resolution for lint, stale
landing assertion and the upload-link TypeScript error. No test is disabled to hide them.

Mobile screenshot inspection also found a **P2 UI verification limit** in the unchanged
Urdu monthly source table: amounts wrap and some evidence text extends/clips inside the
table at 390px. The smoke gate asserts page overflow, not cell formatting; passing it
does not certify this layout. Screenshots remain in the browser artifact for the UI owner.
No financial/UI redesign was attempted in this release-engineering task.

The next release owner must reconcile Claude's separately reviewed core/backend fixes on
an integration branch, rerun the required gates on that combined tree, and complete the
external acceptance above. Never move this branch's recorded baseline or rewrite the demo.
