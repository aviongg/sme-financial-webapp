# Zarwia Candidate v3.1 — presentation application completion

17 November 2026, Asia/Karachi. Application candidate for **private synthetic
staging**, subject to the infrastructure/operator conditions below. No deployment,
main merge, public tunnel, customer data or live provider activation was performed.
Earlier v2/v3 reports remain historical evidence; their executions are not relabelled.

## Provenance and ownership

Branch: `integration/finsight-production-candidate-v3.1`.
Isolated worktree: `tmp/finsight-production-candidate-v3.1`.

| Step | Exact commit |
|---|---|
| Requested frozen v3 starting point | `23e28c12bcd593be824d30cda0a2b79dcbe9d1f5` |
| Complete DEF-11 source | `36802558ae185dd073fb99cda863d57013afacca` |
| First operation, conflict-free `--no-ff` merge | `a1e077e959e86ad2aaddf9709a2559fb8db1acca` |
| Reviewed exact Next security patch | `ec982a7626d2731bd2d5e252bc14404ed5ff68fa` |
| Zarwia presentation branding / mobile context | `e74ed4c3119126ea700c7f2f547a6ab44e3a36d0` |
| Evidence and presentation journeys | `7620c87f4c81544c32e3ab18c033c36ecdf22469` |
| Registration navigation regression correction | `d3c5f69a18032c56558a4a01a16b30fce9cf4796` |
| Normal full browser command includes presentation + DEF-11 | `4a2e2af40bef55aea9abe87a999365ccbab180ab` |

The last row is the completed application/test head. The publication commit adds
this report, curated evidence and the persistent handoff only; resolve its exact
SHA with `git log -1 --format=%H -- docs/release/CANDIDATE_V31_PRESENTATION_RELEASE.md`.
The final response supplies the exact published branch HEAD and its hosted checks.
See [machine-readable provenance](candidate-v31/evidence/provenance.json).

DEF-11 ancestry is verified. Backend, V1–V52 migrations, database files, scripts,
Docker/Compose and workflows are byte-unchanged from the frozen v3 baseline.
Main, dev/fatima, dev/suleman, frozen candidate and source branches are untouched.
Claude retains deployment/infrastructure ownership. No financial authority, role,
CSRF, tenant, projection or provider contract was changed.

## Application corrections and defect disposition

| Finding | v3.1 disposition and evidence |
|---|---|
| DEF-01 | Retained closed. Real failed OCR upload → manual review → correction → confirmation preserves provenance and leaves ledger transactions unchanged. |
| DEF-02 | Retained closed. Canonical module configuration, fresh-seeding and configured/unconfigured behavior regressions pass. |
| DEF-03 | Retained closed. Frontend/dashboard preference and historical configuration regressions pass. |
| DEF-04 | Retained closed. Disabled-module write denials and retained authorized API reads remain enforced. |
| DEF-05 | Retained closed. Commercial inventory selection and stock/COGS assertions pass. |
| DEF-06 | Retained closed. Returns submit the selected prior issue ID to `/api/inventory/**`. |
| DEF-07 | Retained closed. Commercial recognition/lifecycle behavior remains backend-owned. |
| DEF-08 WhatsApp | Retested closed in tested mock/disabled/configured/unknown scenarios, including real Spring empty history. No Meta delivery acceptance claimed. |
| DEF-08 Copilot | Retested closed, including the unchanged independent `provider_unavailable` disclosure assertion. Fault response is injected; no OpenAI call or acceptance claimed. |
| DEF-09 | Remaining activity-label slice corrected. All five current cash activity types have EN/UR labels; real cash report no longer exposes enum keys. Amount rendering is unchanged. |
| DEF-10 | Retained closed. Existing multiple-business/session and setup regressions pass. |
| DEF-11 | Complete source merged; nine real multi-tab journeys pass, including clean/dirty invoice and bill, return/discard/recovery, repeated A→B→C→A changes, failed returns, detail context, logout cleanup, Urdu and short mobile viewport. Drafts remain tenant-scoped. |
| DEF-12 | Corrected. Native evidence selector lists confirmed, unlinked documents from the existing scoped API, by filename/date. No UUID entry, fabricated evidence or implicit posting. Existing authorization/module guards remain authoritative. |
| DEF-13 | Intentional evidence-only architecture retained. Confirmation/linking is explicitly distinguished from accounting posting. |
| DEF-14 | Corrected in tested commercial journeys. Bill → payable detail, invoice → receivable, commercial document → correct invoice/bill, and linked OCR evidence → uploaded document. Record kind/direction comes from existing scoped read APIs. Project evidence link IDs route to their project rather than being mistaken for upload IDs. |
| DEF-15 | Safe camera entry implemented and browser-tested separately from PDF/JPEG/PNG selection and explicit upload. **Actual Android/iOS camera behavior remains unverified.** |
| DEF-16 | Corrected. `/search?q=` decodes and prefills EN/UR queries; a changed query remounts and aborts stale search results. No automatic writes. |
| DEF-17 | Intentional role/read-only boundaries retained; no permission expansion. |
| DEF-18 | Existing corrections preserved. Standalone asset acceptance passes. **Full Docker/PowerShell initialization/start/seeded-login/stop operator rehearsal remains unverified.** |
| DEF-19 | Corrected. Login and registration Back/Forward follow URL intent; registration also survives reload. Anonymous navigation never requests private finance data. |
| DEF-20 | Corrected. Active business stays visible and bounded in mobile headers; EN/UR 390×600 and product responsive regressions pass. |
| DEF-21 | Retained closed. Canonical seeded-style configuration regression passes. |
| DEF-22 | Retracted false positive. Actual inventory endpoints are `/api/inventory/**`; their protection is retained. |
| DEF-23 | Retained closed. Configuration, UI guards, inventory/counterparty/invoice write enforcement and retained reads agree. |

Zarwia appears in public/login/sidebar wordmarks and public EN/UR copy, retaining
the existing visual mark. Backend routes, cookies, storage keys and the historical
FinSight assistant/service name are preserved. No new logo or wholesale redesign.

The additive browser checks assert that evidence linking, relationship navigation,
reports, search and camera selection leave financial transactions unchanged.
The original accounting, isolation, module enforcement and provider assertions remain.

## Fresh verification

Local: Windows, Java 26 targeting 21, Node 26.13, declared pnpm 10.28.2,
Playwright 1.62 with its matching Chromium, Python 3.12. All 46 pinned Python
manifest entries match the installed environment. Real application browser tests
use fresh embedded PostgreSQL **14.22**, random ports/credentials, synthetic keys,
task-owned document directories and loopback servers. No developer/customer DB.
Hosted: Ubuntu 22.04, Java 21, Node 22, Python 3.11 and declared pinned dependencies.

| Gate | Actual result / scope |
|---|---|
| Frozen frontend install | Pass with pnpm 10.28.2; lockfile unchanged by installation. |
| Frontend tests | **488/488**, zero skipped. |
| TypeScript / lint | Pass; **0 lint errors / 50 warnings**, existing warnings reported rather than suppressed. |
| Production webpack build | Pass, actual **Next.js 16.4.5** live-mode build. Middleware-convention deprecation remains a warning. |
| Normal full product command | **36/36 + 9/9 DEF-11**. Includes original financial and v2 contracts, 72 EN/UR responsive page loads, new navigation/evidence/camera checks, then separate real multi-tab stack. |
| Historical browsers | **5 suites / 86 checks**, zero failures: landing 20, general 24, phase-two 20, extra 10, financial-risk 12. Production frontend with synthetic API fixtures; not provider/backend acceptance. |
| Provider browser | **10/10**, EN/UR mock/disabled/Meta-configured/unknown and empty-history disclosure; no sends or financial mutations. Synthetic provider APIs. |
| Original independent DEF-08 | **2/2**, alongside original **26/26** real-stack product checks. Real Spring empty-history WhatsApp; Copilot response fault injection only. |
| Standalone runtime/assets | Pass: unpackaged assets correctly fail readiness; packaged **3 CSS + 21 JS** assets return HTTP 200. |
| Full backend unit | **1,846 cases**, zero failures/errors. Windows: 1 existing symlink-capability skip. Hosted Linux: 5 Windows-only skips (4 container-ownership, 1 tooling guard); these execute on Windows. |
| PostgreSQL / migrations / startup | Hosted **91 classes / 485 cases**, zero failures/errors/skips. Fresh/upgrade Flyway, application startup, transactions, recognition, inventory, documents, reporting, roles, isolation, encryption and grants covered. The full local run also passes all 91 classes / 485 cases with zero failures/errors/skips. |
| OCR | **248/248**, zero skipped; 2 local deprecation warnings. External provider is synthetic. |
| Java → real FastAPI HTTPS | **4/4** explicit probe: trusted transport, secret/authentication failures, wrong CA and insecure production URL rejection; fake extraction provider. |
| Backup/configuration/release fixtures | **63 / 17 / 14** pass; all **84 V52 table ACLs** match the frozen inventory. This is not an operator recovery or real production-role/TLS acceptance. |
| Strict OSV scan | **0 matches / 0 lookup errors** across 440 npm, 44 Python and 136 Maven versions. |
| pnpm/npm-registry audit | **0 advisories at every severity**, no muted advisories. |

Local PostgreSQL completed successfully on PostgreSQL 14.22: **91 classes / 485 cases**, zero failures/errors/skips. The gate excludes the separately configured production-target role/TLS acceptance suites; they are not counted as passed.

See [product checks](candidate-v31/evidence/product-36.json),
[DEF-11 multi-tab checks](candidate-v31/evidence/def11-browser.json),
[local PostgreSQL](candidate-v31/evidence/postgres.json),
[historical summary](candidate-v31/evidence/historical-summary.json),
[provider checks](candidate-v31/evidence/provider-browser.json),
[independent DEF-08](candidate-v31/evidence/independent-def08.json),
[backend/platform skips](candidate-v31/evidence/backend-unit.json),
[dependency inventory](candidate-v31/evidence/dependency-audit.json),
[pnpm audit](candidate-v31/evidence/pnpm-audit.json),
and [local log hashes](candidate-v31/evidence/local-log-hashes.json).
Curated evidence excludes generated secret keys, credentials, JUnit environment
properties, databases, uploaded files and build outputs.

## Failures discovered and resolved, without suppressions

- Original Next 16.4.1 build compiled then refused completion with
  `SecurityFatalError`. Exact `next` / `eslint-config-next` **16.4.5** and matching
  platform packages were reviewed and locked; React/unrelated versions stayed put.
  See [patch review](security/NEXT_16_4_5.md). No security bypass was used.
- Historical harness first refused a build whose API rewrite was not its documented
  loopback fixture address. Rebuilt with `BACKEND_API_URL=http://127.0.0.1:18080`.
- Historical landing then caught a **genuine introduced registration regression**:
  consuming the register hint unmounted the form after the root Back fix.
  `d3c5f69` retains the non-sensitive intent URL. The test still requires actual
  registration inputs; added Back/Forward/reload coverage passes. Initial published
  [historical failure is preserved](https://github.com/aviongg/sme-financial-webapp/actions/runs/44055010576).
- Initial local frontend invocation from the repository root had an isolated
  TypeScript-fixture resolution failure and sandbox loopback denials; running the
  package command in `frontend/` with loopback permission passes. Initial OCR temp
  directory access failed; a fresh task-owned pytest `--basetemp` passes unchanged.
- Windows' default WSL shell did not carry the POSIX fixture environment. Explicit
  Git Bash plus its awk/grep on PATH passes all 17 unchanged configuration tests.
  No deployment script, security assertion, skip or retry policy was changed.

## Hosted CI evidence and final-head rule

The following completed workflows test application source commit
`d3c5f69a18032c56558a4a01a16b30fce9cf4796`:

- [Release validation, all nine jobs green](https://github.com/aviongg/sme-financial-webapp/actions/runs/44055835495).
- [Historical browser, green](https://github.com/aviongg/sme-financial-webapp/actions/runs/44055846520).
- [Resolved dependencies and current-source secrets, green](https://github.com/aviongg/sme-financial-webapp/actions/runs/44055841038).

The subsequent `4a2e2af` changes test wiring only: normal full product acceptance
now includes all presentation and multi-tab checks, with original explicit
`--candidate-v2` replay preserved. Its combined command passes locally. Existing
workflow push filters do not include v3.1; workflows are dispatched explicitly with
`full_browser=true`. No Claude-owned workflow was edited. A release operator must
verify all three runs against the **exact published HEAD**, including this test
wiring; earlier-source runs alone are not a final-head pass. Final-head run links
and outcomes are supplied in the publication response and retained Actions artifacts.

## Reproduce from a clean checkout

Use Java 21, a supported Node 22–26, Python 3.11+, the checked-in Maven wrapper and
the declared pnpm **10.28.2**. Install Python from the pinned tested requirements
as documented in DEVELOPMENT_SETUP/CI; no tool-private Playwright installation.
On Linux, install Chromium's system libraries with `pnpm exec playwright install
--with-deps chromium`, as hosted CI does.

```sh
# backend/ (all unit cases; disposable PostgreSQL is started only by the tests)
./mvnw -B test
./mvnw -B test-compile dependency:build-classpath -Dmdep.includeScope=test -Dmdep.outputFile=target/test-classpath.txt

# repository root; this gate never accepts a developer/customer database URL
python scripts/release/postgres_gate.py
python scripts/audit_dependencies.py

# frontend/; activate the exact packageManager version first
pnpm install --frozen-lockfile
pnpm exec playwright install chromium
pnpm test
pnpm typecheck
pnpm lint
# Shell-specific environment assignment; historical tests require this rewrite.
BACKEND_API_URL=http://127.0.0.1:18080 pnpm build:release
pnpm test:browser:product
node tests/run-historical-browser-reconciliation.mjs
pnpm test:browser:provider-disclosure
pnpm test:demo-standalone
pnpm audit
```

On Windows use `mvnw.cmd`, PowerShell `$env:BACKEND_API_URL=...`, and a writable
task-owned Java/Python temp directory when required. Set `FINSIGHT_TEST_SH` to Git
Bash and include its `usr/bin` on PATH for POSIX configuration fixtures. Browser
drivers refuse occupied reserved ports; run product/DEF-11/provider stacks
sequentially and stop only processes they own. OCR HTTPS instructions remain in
`ai-service/tests/HTTPS_CONTRACT.md`.

## Remaining conditions and staging assessment

No known unresolved application P0/P1 remains in the tested presentation journeys.
DEF-15 native camera behavior and DEF-18 operator acceptance remain explicitly open.

**Private synthetic staging:** suitable application candidate after exact-head CI
and Claude's infrastructure readiness checks. Use clean synthetic data, isolated
document storage, fresh credentials/keys and mock/disabled external providers.
This assessment is permission to hand off the application, not deployment approval.

**Presentation rehearsal still required:** the actual laptop/browser/network,
seeded role logins, full fresh-only PowerShell/Docker lifecycle and recovery from
a restart. Docker CLI/engine is unavailable on this host, so its full sequence was
not run here. Browser/standalone acceptance does not prove that operator sequence.

**Public synthetic demo / real-client pilot:** not approved by this task. Production
PostgreSQL 17 role/TLS acceptance, final containers/ingress, paired database +
document snapshot + historical-key recovery drill, server rollback/backup checks
and relevant provider/account approvals remain external gates. Do not infer those
from embedded PostgreSQL 14.22 or fixture tests. No live OCR/OpenAI/Meta acceptance
or delivery claim is made. Infrastructure decisions and deferred broader UX work
remain with their owners.
