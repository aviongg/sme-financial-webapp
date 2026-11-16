# FinSight / Zarwia Candidate v3 — Stage A integration and verification

**Stage A only; not a final candidate, deployment approval or merge to main.**
Branch: `integration/finsight-production-candidate-v3`.
Isolated worktree: `C:/Users/Hp/FYP/sme-financial-webapp/tmp/finsight-production-candidate-v3`.
Application verification head: **`3ab1069615932664c570336da2306d2d84a915d3`**.
The containing publication adds test/evidence/documentation only; resolve its full
SHA with `git log -1 --format=%H -- docs/release/CANDIDATE_V3_STAGE_A_VERIFICATION.md`.

**Supported CI is green. Independent DEF-08 retest remains RED:** Copilot fallback
disclosure and WhatsApp delivery-history disclosure now pass, but the original
outbound settings-card disclosure still fails when delivery history is empty.
This failure was neither weakened nor converted into an expected pass.
Demo-tooling operational closure remains pending a separately reviewed and
authorized Stage B. No demo-tooling branch was integrated.

## Exact provenance and integration review

| Input / operation | Exact SHA |
|---|---|
| Frozen v2 starting point | `71ac4e0e7e74cbfe2f08fe3a049d37cd26b0c7c3` |
| Provider disclosure source | `0f3729f27a015457b3a3aee98deea6ea1a9244e4` |
| Normal no-ff provider merge | `5736d452f72a40b11acd757107336b2e287bf8f3` |
| Historical-browser source | `380acac342cc162f11e39c2a33651d2b916802ab` |
| Normal no-ff historical merge | `603f3b3173eba3c4daccf5e06f81ea3191eb5294` |
| CI integration wiring | `3ab1069615932664c570336da2306d2d84a915d3` |
| Independent v2 QA reference, not merged or relabelled | `a70f1860628c4d0659ba86a90c4366f81fb2f5c6` |

Both source heads are ancestors of Stage A; there were **no merge conflicts** and
the two sources changed disjoint paths. The provider application tree matches
its published source exactly. Backend, V1–V52 migrations, Docker and demo launch/
seed/migration scripts remain identical to v2. No accounting, isolation, role or
provider-activation policy was changed. Source branches, v2, independent QA,
`main`, `dev/fatima` and `dev/suleman` were not changed.

CI review found missing v3 branch predicates, not a textual merge conflict.
Stage A adds v3 push triggers to release/security/historical workflows, runs
historical reconciliation on PRs, preserves the provider browser step, and runs
the full inherited `--candidate-v2` financial-contract pack on v3 pushes.
That flag names the inherited contract pack; these are fresh **v3** executions.
The existing separate historical aggregate still runs all five drivers, retains
all failures and exits nonzero for any failed driver. No retries, skips, relaxed
assertions or `continue-on-error` were introduced. Artifacts retain 90 days.

Required check names are `release-gate`, `resolved-dependencies`,
`current-source-secrets` and **`historical-browser`**. A green `release-gate` alone
does not subsume the separate historical or independent-QA evidence. Repository
branch protection settings were not changed. A new release-tool regression
checks the v3/PR triggers and retention of the provider/full/historical gates.

The [independent v2 report](https://github.com/aviongg/sme-financial-webapp/blob/a70f1860628c4d0659ba86a90c4366f81fb2f5c6/docs/qa/CANDIDATE_V2_INDEPENDENT_QA_71ac4e0.md)
and its evidence remain immutable historical QA of **v2**, not fresh v3 results.
The integrated historical reconciliation documents and red v2 evidence also
remain unchanged. This document supersedes neither original failure record.

## Disposable environments and boundaries

- Local: Windows; JDK 26.0.1 compiling target 21, Node 26.13.0, **pnpm 10.28.2**
  frozen lock, Python 3.12 with repository-tested constraints. The globally
  available pnpm 11 was not used for dependency installation or frontend gates.
- Real browser acceptance: fresh embedded **PostgreSQL 14.22**, random passwords/
  port, separate migrator/runtime/backup roles, Flyway V1–V52, new document storage
  and synthetic encryption key; real Spring 8093, Next 3593 and gateway 3592.
  Owned ports are checked before startup; the persistent demo on 5433 is not used.
- The generated QA extension delegates to the original full product gate without
  editing it. Only the explicitly labelled Copilot failure response is injected.
  Other real-runtime calls use Spring/session/CSRF and the disposable database.
- Historical drivers: production Next 13100 and synthetic API 18080. Provider
  browser suite: isolated synthetic API 18291, Next 13292, gateway 13291.
  These UI fixtures are not real Spring/provider acceptance.
- Playwright **1.62.0** is the declared local package; its Chromium installation
  was verified using `pnpm exec playwright install chromium`. The versioned
  browser cache is not another tool's hidden Playwright installation.
- Hosted: Ubuntu 22.04, Java 21, Node 22, Python 3.11 and pinned pnpm; fresh
  checked-out Stage-A commit, disposable embedded PostgreSQL, no live providers.
- OCR explicitly disabled in the real browser runtime, Copilot mock/offline,
  WhatsApp default mock and scheduler off. OCR tests and HTTPS probes use fake
  provider transports and test certificates. No Google/OpenAI/Meta/SMTP calls,
  real client data, public tunnel, persistent Docker stack or deployment.
- Processes stopped by the harnesses belong to this task. No unrelated listener
  was killed. No reset, force-push or modification of the stable demo occurred.

## Actual validation results

| Gate | Fresh Stage-A result | Evidence / qualification |
|---|---|---|
| Hosted backend unit | **1,821 passed**, 0 failures/errors/skips | Linux complete default suite |
| Local complete backend unit | **1,821 cases, 0 failures/errors, 1 capability skip** | Windows cannot create the external-storage symlink fixture; the same Linux test passes |
| Hosted PostgreSQL | **87 classes / 468 cases passed**, 0 failures/errors/skips | Four discovered shards: 105 + 112 + 115 + 136; schema/upgrade, security, finance, documents, stock, recurrence, projection and recovery catalog included |
| Flyway/startup | **Passed within the complete suites and real browser runtime** | Fresh V1–V52 plus populated upgrade and one-shot migration contracts; restricted runtime startup; no SQL changed |
| Frontend unit/contracts | **473/473 passed**, 0 skipped | 463 v2 + five historical-fixture + five provider-disclosure tests |
| TypeScript / ESLint / production build | **Passed** | 0 lint errors, 51 existing warnings; live-mode Next 16.4.1 webpack production build |
| Real cross-stack financial/product gate | **26/26 passed** | 72 EN/UR responsive page loads, stock return and financial amount checks, manual-review/provenance/non-posting, module/tenant contracts |
| Separate real browser smoke | **6/6 passed** | 36 page loads, real Spring/PostgreSQL/session/CSRF, no rebuild storm |
| Strict historical browser | **5/5 drivers passed; 86 checks** | Landing 20, primary 24, phase-two 20, extra 10, risk 12. These are synthetic-API UI checks |
| Claude provider browser suite | **9/9 passed** | EN/UR Copilot + mock/disabled/Meta/unknown delivery rows; exact values/statuses retained; no provider/financial mutations |
| Extra independent DEF-08 retest | **1 passed / 1 failed**, aggregate **RED** | Copilot PASS; original outbound settings-card assertion FAIL. Separate from the green supported CI gates |
| OCR Python | **248/248 passed** locally and hosted | Offline provider fakes, not Google acceptance |
| Explicit Java–FastAPI HTTPS | **15/15 passed**, 0 skipped | 4 HTTPS client probes + 11 configuration contracts; synthetic CA/shared secrets/provider |
| Backup / production configuration / release tools | **63 / 17 / 14 passed** locally and hosted | Fixture boundaries, not an operator restore/container drill. V52 ACL inventory matches all 84 tables |
| Dependency audit | **0 matches, 0 lookup errors** | Strict OSV: npm 440, PyPI 44, resolved Maven 136; no new suppressions |
| Current-source secret scan | **Passed hosted** | Current checkout, not image/Git-history scanning |

The local complete backend unit and explicit OCR HTTPS results are recorded in
the [local manifest](candidate-v3-stage-a/evidence/local-verification.json).
The one Windows skip is `symlinkEscapeOutsideStorageRootIsPrevented(Path)` in
`LocalFileSystemStorageServiceTests`; the hosted Linux suite has zero skips and
exercises that storage path-validation test.
The full PostgreSQL suite ran on hosted Linux, rather than repeating all four
shards locally. Focused local real-runtime journeys also use actual PostgreSQL.

Initial production-configuration execution selected the Windows WSL launcher
and failed six shell-boundary assertions because its environment/paths differ
from the expected POSIX shell. The **unchanged** suite passed all 17 checks with
the documented `FINSIGHT_TEST_SH=C:/Program Files/Git/bin/sh.exe`; initial and
corrected log hashes are retained. Hosted Linux also passed. This is not a
production-entrypoint fix or a suppressed test failure.

## DEF-08 retest: closure is deliberately split

**Copilot fallback slice: runtime verified closed in the tested UI contract.**
Both the unchanged historical phase-two assertion and independent fault case
show `mode=deterministic, reasonCode=provider_unavailable` under an `openai`
page status as “Evidence-based fallback — not AI-generated”, with an unavailable
provider explanation. Prior AI answers retain their per-answer provenance.
Offline, disabled, rate-limited, unsupported and provider-generated cases pass;
English/Urdu exact evidence values remain unchanged. No paid provider was called.

**WhatsApp delivery-history slice: runtime verified closed in fixture-backed UI.**
Mock `SENT` rows explicitly say simulated/no transmission; disabled rows say
nothing was transmitted; Meta rows identify the Cloud API; absent/unrecognized
provider names remain unknown. Recorded SENT/FAILED statuses remain visible.
EN and UR, including Meta/unknown labels added to the browser assertions in
Stage A, pass. This proves faithful rendering, not actual Meta delivery.

**WhatsApp outbound settings/empty-history slice: genuine unresolved P1.**
The original independent QA checks the outbound “WhatsApp updates” card, not
the separate inbound configuration and not an existing delivery row. On the
real fresh mock runtime its exact text is:

```text
WhatsApp updates
Receive financial updates on WhatsApp
You can withdraw consent in Settings at any time.
Save preferences
```

Delivery history is empty and contains no provider disclosure. The exact original
`/mock|simulat|not live|provider.*disabled|delivery.*disabled/i` assertion still
fails. Delivery-history labels do not resolve this pre-delivery capability
expectation. Neither branch integration nor an assertion rewrite closes it.
See [the failed assertion result](candidate-v3-stage-a/evidence/independent-provider-results.json)
and [captured settings response](candidate-v3-stage-a/evidence/whatsapp-empty-history.json).
Claude/product review must resolve the remaining scope and supply an application
fix or explicit recorded requirement disposition before claiming full DEF-08 closure.

## Current historical defect disposition

| IDs | Stage-A disposition |
|---|---|
| **DEF-01, DEF-02, DEF-21 (P0)** | Closed by independent v2 QA; fresh v3 manual-review/non-posting/module/tenant regression gates pass. Not a relabelling of the entire v2 QA run |
| **DEF-03/04/05/06/07/10/13/23** | Historical v2 closures carried forward; relevant v3 financial/module/role/browser/backend regressions pass |
| **DEF-08 Copilot** | Tested closed as above; real provider activation remains external acceptance |
| **DEF-08 WhatsApp** | Delivery rows fixed/tested; original outbound-card/empty-history slice remains open P1 |
| **DEF-11** | Unresolved unsaved-work UX/product decision. Existing server-resolved tenant isolation and cross-tab invalidation remain unchanged; no client business-ID override or stale mutation replay introduced |
| **DEF-09 remaining raw activity enums, DEF-12, DEF-14, DEF-15/16, DEF-19/20** | Documented for later Suleman-led usability/device review; not fixed or claimed retested closed by Stage A. DEF-15 native camera behavior remains device-unverified |
| **DEF-17** | Intentional role/read-only boundary from independent v2 QA, preserved by unchanged backend; no permission expansion |
| **DEF-18 / demo operational issues** | Pending Claude's corrected demo-tooling branch and separately authorized Stage B review/integration |
| **DEF-22** | Formally retracted; real write routes are `/api/inventory/**`, remain protected. Do not revive `/api/stock/**` |

There is no new P0 finding in executed Stage-A paths. Do not convert open P1
disclosure/product/operator requirements into closed financial defects or final
release approval simply because supported source CI passed.

## Hosted evidence and acceptance limits

For exact verification head `3ab1069615932664c570336da2306d2d84a915d3`:

- [Release validation — success](https://github.com/aviongg/sme-financial-webapp/actions/runs/43837326896)
- [Dependency and secret checks — success](https://github.com/aviongg/sme-financial-webapp/actions/runs/43837326873)
- [Strict historical browser — success](https://github.com/aviongg/sme-financial-webapp/actions/runs/43837326944)

[Hosted job/count evidence](candidate-v3-stage-a/evidence/hosted-verification.json)
records job IDs and parsed actual log totals. The later evidence publication does
not change production source. Its fresh workflow status must be checked against
its exact SHA; the above run IDs must not be represented as runs of another SHA.

Stage B is **not authorized/included**. Prior demo review's incomplete standalone
assets, port-only readiness, non-seeded-data override, destructive/preservation
risks, known fixture credentials/keys and provider/exposure assumptions remain
open until the corrected exact branch is reviewed and operator-tested. This
task did not start its old launcher or infer safety from its nine contract tests.

Not executed/accepted here: actual PostgreSQL 17/TLS/HBA/production-grant fixtures
(`DatabaseRolePrivilegesPostgreSqlIT`, `PostgreSqlTlsConnectionIT`,
`V52ProductionPrivilegesAcceptance`), Docker image builds/scans, native camera,
live providers/SMTP, public tunnel, VPS/ingress/secrets, paired DB/document/all-
historical-key recovery and rollback/operator drill. These remain distinct from
the embedded migration/grant/catalog/crypto fixtures. Older optional standalone
commercial/FX/project/funding/accounts/unified acceptance wrappers outside the
supported release/full-product/historical suites were not rerun.

**Local synthetic demonstration:** conditional on a verified isolated loopback
stack and disclosed limitations; not a certification of one-click demo tooling.
**Public synthetic demonstration and real-client pilot:** not approved. Remaining
DEF-08, DEF-11 disposition, Stage B and external infrastructure/provider/recovery
acceptance prevent declaring Candidate v3 final.

See [reproduction and retained evidence](candidate-v3-stage-a/README.md).
Stop after Stage-A publication and verification; no main merge or deployment.
