# FinSight / Zarwia production candidate v2

Verification date: 16 November 2026, Asia/Karachi. This candidate is for independent
QA; it is not a production deployment or a claim that all prior QA findings closed.

## Exact integration and provenance

- Repository: `aviongg/sme-financial-webapp`.
- Branch: `integration/finsight-production-candidate-v2`.
- Isolated worktree: `C:/Users/Hp/FYP/sme-financial-webapp/tmp/finsight-production-candidate-v2`.
- Exact v1 baseline: `493cda58de25f8815e1b0b73d5bb385dfcd997f9`.
- Core fix head: `f9d686da811ae9f53c7a0dd6c1414601085356f0`.
- Frontend fix head: `be4441644fe4f7d57406e098ac3b3713911a8ad3`.
- Core merge: `5297509e51db7a3c8d1b25109548839e08b2b8f0`.
- Frontend merge: `b204656e4e350e6a72c29891b97f02b40d9615de`.
- CI branch wiring: `d3a613f01dc98d4ca50efe59178b1fae37869d20`.
- Additive v2 browser verification: `399764bce1cb3fe690aecefd9eac11621334d60d`.

Both integrations used normal `git merge --no-ff` with the exact source SHA, in
core-then-frontend order. No conflicts, cherry-picks, squashes, rebases or omitted
source commits. The 20 core-changed files and 23 frontend-changed files have no
overlap. All 43 resulting Git blob IDs exactly match their respective source head.
Both source heads and v1 are ancestors of this candidate. No dependency lock,
migration V1–V52, accounting policy or additional production application code was
changed beyond the accepted input branches. Follow-up changes are CI, test-only
browser verification and this evidence. [Machine-readable provenance](CANDIDATE_V2_PROVENANCE.json).

The two source branches, original candidate and frozen infrastructure branch
(`ea0778398cb92d7754dd82463534b8b106a70316`) were verified remotely unchanged.
The stable `dev/fatima` checkout and its existing untracked files were preserved.
No merge to main, deployment, live provider activation or customer-data access.
The containing evidence commit is discoverable with
`git log -1 --format='%H' -- docs/release/CANDIDATE_V2_VERIFICATION.md`;
the final published HEAD is reported separately rather than self-embedded.

## Complete supported regression results

Initial full clean-clone Linux result on `d3a613f`:
[release validation](https://github.com/aviongg/sme-financial-webapp/actions/runs/43752726863)
**passed**, including its aggregate `release-gate`.
[Dependency/secret checks](https://github.com/aviongg/sme-financial-webapp/actions/runs/43752726861)
also **passed**. The additive 26-check browser gate is published at `399764b`;
[its validation run](https://github.com/aviongg/sme-financial-webapp/actions/runs/43755257877)
and [security run](https://github.com/aviongg/sme-financial-webapp/actions/runs/43755257887)
must be inspected for their completed result. Later documentation-only publication
gets the same unchanged gates; use its exact HEAD, never a different branch's CI.

| Gate | Result and boundary |
|---|---|
| Linux backend unit | **1,821**, zero failures/errors/skips |
| Complete disposable PostgreSQL gate | **87 classes / 468 cases**, four shards: **105 + 112 + 115 + 136**, zero failures/errors/skips |
| Migration/startup | Fresh contiguous V1–V52, populated legacy/upgrade tests, catalog/grants/immutability checks, one-shot migration profile tests and real Spring startup exercised by those suites/browser |
| Frontend | Frozen pnpm 10.28.2 install; **463 tests**, TypeScript, ESLint **0 errors / 51 warnings**, production webpack build pass |
| Real supported browser | Existing **6 smoke** and **20 full product** checks pass; additive v2 mode **26/26** passes locally with real Next, Spring, disposable PostgreSQL and declared Playwright Chromium |
| OCR Python | **248 passed** locally and in CI; no real provider calls |
| Explicit OCR HTTPS contracts | **15/15** locally: 4 FastAPI HTTPS probes + 11 configuration checks, synthetic certificates and provider fixtures |
| Recovery/configuration/release tools | **63 + 17 + 13** passed locally and hosted; V52 grant inventory matches all 84 tables |
| Security | Strict resolved-dependency OSV and current-source-secret jobs passed; no new scanner exceptions |
| Windows full backend unit | Command passed: **1,821 cases, 1 platform skip**, zero failures/errors. `symlinkEscapeOutsideStorageRootIsPrevented(Path)` aborted for Windows symlink capability; Linux ran it successfully, so it is not counted as locally tested |

The PostgreSQL gate includes transaction posting/reversals/idempotency, obligations,
allocations/FX/commissions, document/confirmation/provenance, stock/WAC/returns,
commercial/project/reporting, tenant/role/session/CSRF and concurrency regressions.
No class was removed to get a pass. Its existing explicit production-fixture
exclusions remain `DatabaseRolePrivilegesPostgreSqlIT` and
`PostgreSqlTlsConnectionIT`; they are **not passed**. The separate
`V52ProductionPrivilegesAcceptance` is also not a normal disposable gate.

Local environment: Windows, JDK 26 compiling target 21, Node 26, pinned pnpm
10.28.2, Python 3.12; hosted authoritative environment is Ubuntu 22.04, Java 21,
Node 22, Python 3.11. Local full backend unit took about 11 minutes. An initial
OCR run had **247 pass / 1 pytest-temp PermissionError**, then **248 passed** using
a fresh task-owned D: temp directory. The original log remains; no test was edited
or skipped to hide it. Browser/additive-driver authoring errors (selectors, form
required inputs, confirmation-month remount and request-time observation metadata)
were corrected in the test code against the actual contracts, with initial logs
retained. No production behavior was changed during that debugging.

## Cross-stack acceptance of the merged fixes

| Contract | Independent evidence |
|---|---|
| DEF-01 | Real validated upload with OCR explicitly disabled reaches `failed/unavailable`; UI manual action, durable correction save, explicit confirmation and preserved manual/original-failure provenance work. Exact transaction facts/counts remain unchanged; only the API's request `observationAt` clock is excluded from comparison. PostgreSQL tests additionally cover eligible/ineligible/storage, roles, module denial, tenant isolation and evidence linking |
| DEF-02 / DEF-21 | Seeder canonical-module unit contract; real PostgreSQL stale-key normalization/configuration tests; browser's real canonical seeded-style selection saves and remains enabled. The working demo seeder/data were never run or modified |
| DEF-23 | Unconfigured response includes every implemented effective module and permission-aware availability; explicit configured selection round-trips consistently with write enforcement |
| DEF-04 | Disabled module routes show the frontend guard; direct invoice, counterparty and actual inventory mutations return `403 module_disabled`; retained authorized API history remains readable. Full backend route/role enforcement suites pass |
| Counterparty correction | Exact `f9d686d` source and tests included. `/api/finance/counterparties` writes are gated by `transactions`, matching frontend navigation/dependency requirements |
| DEF-05 | Browser inventory selector persists the chosen inventory item through actual invoice creation |
| DEF-06 | Browser selects a prior issue and returns stock through `/api/inventory/movements/returns`, with the exact selected `issueMovementId` in the request |
| DEF-03 / DEF-07 / DEF-09 | Source changes included exactly; 463 frontend contracts plus real responsive EN/UR product journeys, authoritative recognition/credit-note/outstanding values, stock fulfilment, monthly/P&L/score, financing inputs and report CSV run successfully. Detailed UX closure remains independent QA's decision |
| Business isolation | Foreign document detail/manual action and foreign inventory item detail return 404. History is a business-scoped collection: foreign and random unknown filters both return the identical empty `{movements: []}`. No foreign movement is disclosed. Backend actor/role/tenant tests also pass |

**DEF-22 remains formally retracted.** Actual stock writes are `/api/inventory/**`,
already protected. `/inventory/stock` is a UI route; `/api/stock/**` is not the real
API. Source test labels/SKUs retaining an old DEF-22 string are provenance, not a
reopened finding. No replacement stock module policy or SQL was introduced.

## Supplemental historical browser drivers — failures retained

Six additional synthetic-API UI drivers were executed separately from the real
cross-stack gate. These are not PostgreSQL/provider evidence:

| Existing driver | Measured result |
|---|---|
| `cash-forecast-browser.integration.mjs` | **11 checks passed**, including scenario, underlying items, Urdu/RTL/mobile, no runtime/CSP/hydration errors |
| `browser-landing.integration.mjs` | **19/20**; its no-business fixture transition timed out waiting for `Create business`; remaining behavior needs QA/fixture review |
| `browser.integration.mjs` | Failed before login: assumes anonymous `/` immediately shows sign-in |
| `browser-phase-two.integration.mjs` | Failed at initial login fields after visiting anonymous `/` |
| `browser-extra.integration.mjs` | Failed at initial sign-in heading after visiting anonymous `/` |
| `financial-risk-browser.integration.mjs` | Fixture run failed waiting for `overall-severity-badge`; not a proven new financial/backend defect |

Anonymous `/` is already public in candidate v1. The three old root-login
expectations conflict with that contract. Auth/session/public-route implementation
files themselves were unchanged by either input branch; the added module guard is
after authenticated-business resolution. The other two fixture failures are not
silently labelled passes or assigned speculative production DEF IDs. Their logs,
screenshots and results are retained under ignored `tmp/` and `frontend/test-artifacts/`.
No old assertion was suppressed or rewritten. QA should reconcile these fixtures
and retest their remaining assertions; a green supported CI gate does not certify
every historical UI driver. Only task-owned local listeners were started/stopped.

Other historical browser entry points were not executed as whole suites: older
standalone commercial/FX/commission/project/funding drivers require their named
IT's Surefire XML/classpath setup; `accounts-live` additionally expects a specific
draft-source hash; `unified-live` explicitly requires an external disposable V37
manifest. Eleven historical `*BrowserAcceptance` wrappers need their separate
frontend/Edge fixtures. No private fixture manifest or prior-worktree XML was
invented or borrowed to manufacture completion. Their domain tests are covered
by the complete current PostgreSQL gate; their additional old UI assertions are
not claimed verified. The V52 26-check gate is the supported clean-clone browser
acceptance used here.

## Remaining findings and release decision

Prior v1 QA register `qa/finsight-candidate-retest-493cda5` remains the authority
for untouched findings. Carry forward, **without claiming a new retest/closure**:

- **P1:** DEF-08 (remaining provider availability disclosure/settings slice),
  DEF-10 (existing-owner setup/additional-business navigation), DEF-11 (cross-tab
  active-business refresh/detail handling and unsaved form loss).
- **P2:** DEF-12 (remaining UUID-link inputs; stock-return selector improved in
  this candidate), DEF-13 (OCR review wording/classification vs evidence-only
  semantics), DEF-14 (relationship navigation), DEF-15 (mobile camera capture),
  DEF-16 (search query prefill), DEF-17 (remaining role/route boundary UX),
  DEF-18 (demo script/seeder environment issues).
- **P3:** DEF-19 (anonymous browser-back/auth intent), DEF-20 (mobile active-business badge).

No speculative product fix was added for those existing findings or the five
supplemental driver failures. All accepted production application changes remain
byte-identical to the two supplied source heads.

**Ready for independent QA, with conditions.** Retest the supplied DEF fixes,
remaining findings and the supplemental fixture failures against the exact final
published HEAD. This does not authorize a production release. Actual Docker image
build/scans, PostgreSQL 17/TLS/HBA/real role acceptance, paired populated DB/document/
historical-key recovery and rollback, real providers/SMTP, VPS/ingress/secrets and
operational RPO/RTO remain separate unverified gates. Docker/psql/pg_dump and the
dedicated production credentials were unavailable on this local host. Nothing was
merged into another branch or deployed. Stop after publication/verification and
hand the exact candidate to Antigravity for independent retesting.
