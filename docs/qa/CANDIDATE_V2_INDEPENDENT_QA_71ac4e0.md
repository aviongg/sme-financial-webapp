# FinSight / Zarwia — definitive independent Candidate-v2 QA

**Tested application: `71ac4e0e7e74cbfe2f08fe3a049d37cd26b0c7c3`.**
QA-only branch: `qa/finsight-candidate-v2-independent-completion`.
The application, migrations, dependencies and existing tests remain byte-identical
to this frozen candidate. This report does **not** test historical-browser HEAD
`380acac` as production, integrate Claude's forthcoming provider fixes, merge or deploy.

**Decision: conditional GO for an isolated local synthetic demonstration;
NO-GO for unrestricted public demonstration or real-client pilot as-is.**
The three original P0 findings are runtime verified closed in the tested paths.
Both P1 DEF-08 provider-disclosure slices remain open. DEF-11's unsaved-work
experience needs a product decision; its tenant invalidation is working correctly.
Demo launch/packaging and preservation risks also require correction before public use.
Green supported release CI is not approval for the uncovered journeys below.

## 1. Exact environment and evidence boundaries

- Fresh isolated worktree:
  `C:/Users/Hp/FYP/sme-financial-webapp/tmp/finsight-v2-independent-qa`, created
  directly from the full frozen SHA. Git application/source invariants were checked
  before tests and publication. The stable demo, source branches, v2 candidate and
  historical-browser branch were not modified.
- Windows, JDK **26.0.1** compiling the repository's Java 21 target; Node **26.13.0**;
  pnpm **10.28.2**, frozen lock; repository-local **Playwright 1.62.0 / Chromium**.
  Browser binaries are a cache of the declared dependency, not another tool's driver.
- New embedded **PostgreSQL 14.22**, random database port and password, separate
  migrator/runtime roles, fresh **Flyway V1–V52**. Runtime uses `finsight_app` with
  Flyway disabled after migration. Fresh temporary uploaded-document directories
  and synthetic encryption key; no persistent demo database/volume was contacted.
- Task-owned Spring loopback **8093**, Next production **3593**, same-origin gateway
  **3592**; port preflight refuses collisions. Seeder compatibility review additionally
  used a separate disposable PostgreSQL/Spring instance with an ephemeral loopback port.
  Its public demo key was used only to read that script's synthetic seed, not real data.
- OCR explicitly off; WhatsApp mock and scheduler off; Copilot mock/offline.
  No OpenAI, Google OCR, Meta, SMTP, real-provider, public-tunnel or client-data calls.
  One labelled **frontend Copilot response fault injection** substitutes a valid
  fallback response. All other core QA browser/API checks use the actual Spring service.
- Source-only QA scripts and selected synthetic evidence are under
  [candidate-v2/](candidate-v2/). Original full failure logs, generated runners,
  builds and databases remain outside Git under task-owned ignored scratch paths.

### Surviving Antigravity artifact

The proposed report was found read-only outside the repository:
`C:/Users/Hp/.gemini/antigravity/brain/3591ef67-2b29-4a58-80a4-727242178ca1/qa_candidate_v2_retest_71ac4e0.md`.
It is a **19,331-byte unpublished draft**, SHA-256
`3d63533340b768673b7d55b6ab4bd193e01a3cdeab100f01170fbb16cd16c5dd`.
Its directory contains Markdown/metadata, not the referenced browser screenshots or
an independently replayable runtime manifest. Its historical findings define retests;
they are not accepted as proof of execution or release approval.

The draft's zero-P1/conditional-client-pilot recommendation is rejected. It omits the
Copilot disclosure slice, downgrades WhatsApp disclosure without an integrated fix,
claims a baseline seeded Manager despite its four-role seeder, and describes a
`/login` redirect not present in frozen routing. Its relationship recommendation also
confuses commercial `DOCUMENT` with uploaded `OCR_EVIDENCE`. See
[artifact review](candidate-v2/evidence/antigravity-draft-review.json).
Closures below were independently reproduced, including DEF-10 and DEF-13.

## 2. Actual checks and final outcomes

| Check | Fresh result | Limit |
|---|---|---|
| Existing full real cross-stack `--candidate-v2` gate | **26/26 passed** | Includes 72 EN/UR page loads across four desktop/mobile sizes, real financial amounts, manual review, module writes, stock returns and tenant isolation |
| Additional independent QA probes | **27 exercised: 17 passed / 10 failed**, aggregate **RED** | Ten failures are explicitly retained; some measure deferred/UX gaps rather than release-critical financial defects |
| Focused PostgreSQL suites | **53/53**, 0 failures/errors/skips, **9 classes** | Relevant document, inventory, configuration, lifecycle, recognition, workspace and session suites; not a new full 87-class regression claim |
| Focused backend unit/security contracts | **18/18**, 0 failures/errors/skips, **4 classes** | Seeder module keys, real inventory routes, Copilot security and synthetic OpenAI-provider contract |
| Frontend | **463/463**, 0 skipped; TypeScript passed; ESLint **0 errors / 51 existing warnings**; production webpack build passed | Frozen candidate's tests, not the historical branch's 468-test suite |
| Eight actual frozen-seeder businesses | **8/8 configurations 200**, all 15 enabled modules; **48/48 valid writes 201**, seed-owner login 200 | Per business: counterparty, inventory item, invoice, bill, account and transaction. All disposable; no main seeder invocation on port 5433 |
| Demo-tooling head `7c784c2` | **9/9 committed contract tests**, 0 skipped; full seed succeeds with 8 businesses and 5 roles × 8 memberships | Separate read-only Git archive; PlanOnly tests, not a successful Docker/tunnel deployment |
| Operational negative probes | **Preservation and packaging failures reproduced** | Pre-existing sentinel phone modified; inert TCP accepted as ready; fresh standalone root 200 but **5/5 sampled JS/CSS assets 404** |

The final full result is [qa-results.json](candidate-v2/evidence/qa-results.json);
the unchanged gate is [baseline-26.json](candidate-v2/evidence/baseline-26.json).
[Backend totals](candidate-v2/evidence/backend-checks.json) include XML hashes.
[Seeded runtime](candidate-v2/evidence/candidate-seeded-runtime.json) retains every
business configuration/write status. A separately labelled security follow-up
extends the missing-CSRF/anonymous probe with a valid foreign-account posting attempt;
its [404/no-financial-change outcome](candidate-v2/evidence/foreign-account-followup.json)
is recorded separately, not counted as a new full-suite pass.

The full QA's ten red probes are: **DEF-08 WhatsApp, DEF-08 Copilot, DEF-09 raw
activity enums, DEF-12 raw evidence UUID, DEF-14 OCR/payable links, DEF-14 commercial
links, DEF-15 explicit camera capture, DEF-16 query prefill, DEF-19 browser Back,
DEF-20 mobile business badge**. DEF-11's deliberate clearing passes its security
assertion but does not close its separate unsaved-work UX finding.

### Prior/debug failures were reconciled, not suppressed

Initial QA-driver errors remain in ignored logs: Manager file/state eligibility was
mistaken for authorization; an empty confirmation body hit validation 400 before
permission checks; an exact label omitted its required `*`; aggregate aging was
incorrectly expected to contain a supplier's name; a commercial relationship was
queried with an uploaded-file ID; and a heading selector matched both h1/h2.
Corrected tests retain the actual 403, source, amount and isolation assertions.

An initial populated Urdu report measured **403px document width in a 390px
viewport** before font/network settling. A failed language case also contaminated
later English selectors; those later results were harness failures. Cases now
reset only their QA language/business context between probes. The complete final
27-probe run and a full-history targeted follow-up pass the same ≤1px layout
assertion after assets settle. Earlier screenshots/13px measurements are retained
as an intermittent pre-readiness observation, not a confirmed persistent P1.

## 3. Definitive DEF-01–DEF-23 matrix

“Closed” means the specified historical failure was runtime verified against frozen
v2, not that every possible feature/provider/production environment is certified.
Original severities come from the published candidate-v1 register. Downgrading a
historical P1 workflow requires a recorded product decision; this QA does not make one.

| ID | Classification on frozen v2 | Evidence / remaining slice |
|---|---|---|
| **DEF-01** (P0) | **Runtime verified closed** | Real OWNER and ACCOUNTANT failed upload → deliberate manual review → durable correction → explicit confirmation. Original null extraction/failure reason and manual provenance preserved. Exact transaction facts and monthly observations unchanged; evidence linking also does not post. Valid Manager/Viewer mutations denied; foreign file/detail guarded |
| **DEF-02** (P0) | **Runtime verified closed** | Actual frozen seeder produces 8 configurations with 15 canonical keys, no `commercial`. Each configured business saves/operates; seeded login and valid writes succeed. Targeted normalization/configuration PG tests pass |
| **DEF-03** (P1) | **Runtime verified closed** | Product widgets are in the real library. Inventory/reports added through UI, saved to preferences, reloaded and found in the authoritative workspace response |
| **DEF-04** (P1) | **Runtime verified closed for tested enforcement/retention boundary** | Four roles × actual inventory/counterparty/invoice POSTs return `403 module_disabled`; document manual writes also deny. Authorized inventory/invoice history remains 200, document history stays role-scoped. Direct frontend routes show guard. No `/api/stock` test invented |
| **DEF-05** (P1) | **Runtime verified closed** | Invoice and bill inventory selectors persist real item ID. Additional edit/save of an existing linked invoice draft retains that ID. Bill finalized/recognized and stock received; original invoice fulfilment also passes |
| **DEF-06** (P1) | **Runtime verified closed** | Prior issue is selected by human-readable dropdown and its exact ID reaches `/api/inventory/movements/returns`; stock/COGS assertions pass |
| **DEF-07** (P1) | **Runtime verified closed** | Recognized invoice/bill show receivables/payables financial effect, no plain Void action, and lifecycle compensation guidance. Recognition/lifecycle PG suites pass; no forged-obligation policy relaxed |
| **DEF-08** (P1) | **Genuine unresolved — TWO separate P1 slices** | **WhatsApp:** outbound card lacks mock/disabled/provider disclosure; inbound “not configured” is not an outbound status. **Copilot:** deterministic `provider_unavailable` response renders under status-derived “AI assistant” without fallback disclosure. Neither separate application fix is integrated/tested here |
| **DEF-09** (original P1) | **Original raw-key/nested-data slice closed; genuine remaining P2 localization defect** | EN/UR field labels and nested aging/inventory render; populated mobile pack passes after settling. `DIRECT_SALE_RECEIPT` remains a raw activity enum in both languages and is retained as a failing probe. Do not call all report localization complete |
| **DEF-10** (P1) | **Runtime verified closed** | Existing owner uses Settings → another business → creates business via UI → `/business-setup`; selected business changes correctly. No redirect loop |
| **DEF-11** (P1) | **Intentional isolation + genuine unresolved UX; P1 retained pending product disposition** | Cross-tab switch clears old-business draft without dirty-form warning and shows not-found for old detail. No foreign content/post survives. Security clearing is correct; silent unsaved-work loss is not closed or unilaterally downgraded |
| **DEF-12** (P2) | **Genuine unresolved usability gap** | Return/item selectors fixed under DEF-05/06, but `lifecycle-evidence-id` is still an INPUT requiring an upload UUID. Do not claim all linking selectors complete |
| **DEF-13** (P2) | **Runtime verified closed for misleading-posting claim; evidence-only architecture intentional** | Review text and confirmed banner explicitly say monthly figures do not change. Nonzero expense/cash-impact metadata was confirmed while exact financial facts stayed equal. Remaining metadata fields / “Confirm and add once” wording do not change V52 semantics |
| **DEF-14** (P2) | **Genuine unresolved navigation defects; old entity-kind assumption corrected** | Bill `OCR_EVIDENCE` row lacks `/upload/[id]`; payable obligation routes to receivables; item-related commercial `DOCUMENT` rows are badges without invoice/bill navigation. A commercial DOCUMENT must not be sent to an uploaded-file URL |
| **DEF-15** (P2) | **Deferred enhancement / device acceptance not yet tested** | Live file input works but `capture` is absent. Explicit rear-camera intent probe fails. Desktop mobile emulation cannot prove native phone camera-picker behavior; no actual device camera test claimed |
| **DEF-16** (P2) | **Genuine unresolved** | `/search?q=Alpha` leaves query input empty |
| **DEF-17** (P2) | **Intentional architecture / runtime verified safe in tested roles** | Owner/Accountant write; Manager/Viewer cannot create commercial documents or financial postings. Viewer monthly explanation is read-only, financing/recalculation controls absent; scan has no upload access. A page shell is not a permission bypass. Generic module-disabled wording for permission denial remains UX polish |
| **DEF-18** (P2) | **Genuine unresolved in candidate; separate demo fix reviewed, not integrated** | Frozen launcher advertises wrong shared password; baseline seed omits Manager and migrator contains developer path. `7c784c2` fixes those contracts but has the operational limitations below. Not certified one-click/public ready |
| **DEF-19** (P3) | **Genuine unresolved** | Anonymous browser Back removes auth query but leaves sign-in rendered rather than restoring landing. No `/login` redirect is fabricated |
| **DEF-20** (P3) | **Genuine unresolved** | Active-business header badge hidden at 390×600 on invoice-entry screen; user must open Settings for context |
| **DEF-21** (P0) | **Runtime verified closed** | All 8 actual seeded businesses accept real canonical configured writes: counterparty/item/invoice/bill/account/transaction 201. This tests the seeded/enforced interaction, not only new unconfigured businesses |
| **DEF-22** | **False positive / formally retracted** | Actual writes are `/api/inventory/**` and deny disabled modules. `/api/stock/**` was the incorrect finding and remains retracted |
| **DEF-23** (P1) | **Runtime verified closed** | New unconfigured business exposes all implemented effective modules; explicit configured choices round-trip and agree with actual write guards and role-aware read availability |

Roll-up: **11 historical IDs runtime closed; 9 IDs with genuine remaining slices
(including both DEF-08 slices and DEF-11's UX decision); 1 deferred/device enhancement;
1 intentional safe boundary; 1 retracted.** No historical ID is silently omitted.
“Not yet tested” remains explicit for native camera behavior, live providers and
production/operator gates, not invented as a pass.

## 4. Manual-review and security proof

[Persisted before/after evidence](candidate-v2/evidence/manual-review.json) shows
identical transaction facts and monthly records across the ACCOUNTANT journey,
including a nonzero `13.37` expense/cash-outflow review. The request observation
clock alone is excluded from transaction equality; amounts, IDs, counts, balances,
source fields and stored month timestamps are compared. Original OWNER flow is
also in the unchanged 26-check gate. Separate deliberate authorized role test
postings occur **after** that equality proof; they are not document side effects.

Original `extractedData` stays null; `failureReason` stays `unavailable`;
`reviewedData.entry_mode=manual` and `ocr_failure_reason=unavailable` survive the
correction. Correction history is append-only with previous unknown amount and
manual new values. The existing `extractionProvenance=ORIGINAL_OCR` column is
preserved, not falsely relabelled as a new machine extraction. Re-entering manual
review returns 409; direct confirmation of a failed document is rejected.
Confirmation/linking does not create a financial entry.

| Operation | OWNER | ACCOUNTANT | MANAGER | VIEWER |
|---|---:|---:|---:|---:|
| Financial/invoice reads | 200 | 200 | 200 | 200 |
| Authorized document read | 200 | 200 | 200 | 403 |
| Foreign document/file | 404 | 404 | 404 | 403 |
| Valid financial posting | 201 | 201 | 403 | 403 |
| Commercial creation UI | Available | Available | Read-only | Read-only |
| Manual review/correction/confirmation | Verified allowed | Verified allowed | 403 for valid requests | Denied by role/document scope |
| Disabled inventory/counterparty/invoice writes | 403 module_disabled | 403 module_disabled | 403 module_disabled | 403 module_disabled |
| Historical inventory/invoice reads while disabled | 200 | 200 | 200 | 200 |

Missing CSRF returns 403; anonymous document read returns 401. Foreign document
detail/manual action and item detail/history remain isolated. Viewer gets neither
financing form nor recalculation action. Eligibility describes file/state, not
authority: Manager may receive `manualReviewEligible: true` but has no manual
action and valid API mutations still return 403. Do not interpret that flag as authorization.

## 5. Separate demo-tooling review — exact `7c784c2`

Reviewed head: **`7c784c28a332d0703a201bcc4ee9496308f1b3f4`**,
`fix/finsight-v2-demo-tooling`. Its merge base is exactly frozen v2 and its five
changed files are four scripts plus `DemoToolingContractTest`; application,
migrations and dependency trees match v2. Review/test used a read-only Git archive,
not a merge, reset, source-branch change or execution of the fixed-port seeder main.

### Compatible improvements actually verified

- The committed **9 contract tests pass**, including compilation of both Java
  scripts and safe PowerShell **PlanOnly** option checks. Manager is in the same
  canonical table as the other roles and advertised credentials agree with the seeder.
- A fresh disposable full seed creates **8 businesses / all 15 modules / 5 roles,
  each with 8 memberships**. Existing canonical-module behavior is preserved.
- Migrator finds V1–V52 from repository/explicit location, rejects partial/wrong
  migration directories, keeps `cleanDisabled(true)` and fixed demo-only loopback URL.
  It does not authorize arbitrary production migration or customer-data use.
- Public exposure requires **both** `-PublicTunnel` and
  `-ConfirmSyntheticPublicDemo`; public mode refuses already-running unverified
  frontend/backend ports and failed/non-seeded count checks unless explicitly overridden.
  No public command/tunnel was run. Local default binds loopback; public mode binds LAN
  `0.0.0.0` and adds Quick Tunnel CORS patterns. AI/live activation and WhatsApp
  are forced mock/off only for a newly started backend.

### Remaining P1 operational risks / public-demo blockers

1. **Fresh standalone package is incomplete.** Build produces
   `.next/standalone/server.js` but not its `.next/static` or `public` directories.
   Launching exactly that entrypoint on an owned ephemeral port yielded root
   **200** and **5/5 sampled CSS/JS paths 404**. The script only checks file existence
   and TCP readiness, and performs no asset copy. Dockerfile.frontend already copies
   both directories, but this launcher does not use that Docker frontend.
   [HTTP evidence](candidate-v2/evidence/standalone-assets.json).
2. **Existing-database preservation is not established and full seeding is unsafe
   for mixed data.** In both frozen and reviewed seeder code, `seedAll` updates
   **all** business profiles with null/non-encrypted WhatsApp values. A synthetic
   pre-existing sentinel survived but its null phone was overwritten. Existing demo
   users also have name/password rewritten on reseed (source review); seeding creates
   fresh businesses, so it is not an idempotent general upgrade. This behavior is
   inherited, not introduced by `7c784c2`, and is not covered by its 9 contracts.
   [Disposable sentinel proof](candidate-v2/evidence/demo-tooling-seed-review.json).
3. **A listening port is not readiness or provenance.** The exact reviewed
   `Test-LocalPort`/`Wait-LocalPort` accepted a task-owned inert TCP socket with no
   HTTP application. Local mode reuses existing listeners as-is, while printing
   disabled-provider/database assertions it cannot verify. Launcher does not
   validate Flyway 52, runtime readiness, provider configuration, source SHA or
   assets. Fresh Compose only creates PostgreSQL, not the required restricted roles,
   migration/seeding/build prerequisites. Those remain operator assumptions.
   [TCP probe](candidate-v2/evidence/demo-port-readiness.json).
4. **The synthetic-data gate tests names, not data content/provenance.** Its SQL
   counts unexpected emails/business names. Real/modified data under allowed
   seeded names can pass. `-AllowNonSeededData` explicitly bypasses even those counts.
   Neither switch certifies absence of real client data, old uploads or historical keys.
   Public review must require a newly created approved synthetic DB/document volume;
   do not permit this override on an unverified or restored database.
5. **Published credentials/key are public demo fixtures, not production secrets.**
   Owner credentials are known and each role belongs to all seeded businesses.
   The DB passwords and static reversible demo encryption key are committed already.
   Loopback DB binding limits exposure but supplies no confidentiality for real data
   encrypted with that key. Unrestricted known-Owner access permits destructive
   changes/resource abuse of the shared demo. Use fresh synthetic-only credentials,
   storage and keys plus access controls/quotas for a controlled public evaluation;
   never reuse them or rotate away historical keys on real/restored data.
6. **Process/provider/exposure assumptions remain.** Public frontend binds all
   interfaces, bypassing a tunnel-only access policy on a reachable LAN. Session
   cookies are configured insecure for local HTTP. OCR/SMTP inherited configuration
   is not forced off by this script. Local reuse bypasses its environment overrides.
   `stop-demo.ps1` still kills by port rather than owned PID, and sandbox reset
   matches business names/bypasses immutability triggers; neither was run here.
   Floating image tags and unchecked JAR/build age also prevent exact release identity.

These do **not** establish a new financial-policy defect or authorize a production
fix on this QA branch. They block claiming this script is safe, reproducible public
demo tooling. A disposable local demonstration can use explicit verified startup
instead, as the real QA harness did.

### Reported port-related local failure

No surviving exact failing startup log/command was found in the reviewed task-owned
artifacts; the prior script worktree retains only a passing 9-test contract summary.
The full Docker startup/5433 failure was **not reproduced or declared fixed**.
This host's QA used private fresh PostgreSQL and owned ports, so it cannot certify
the operator's existing service/volume. Public preflight rejection of occupied ports
is intentional safety behavior, not an application defect. The separate inert-port
and missing-asset probes prove why passing a port check would not close that gate.

## 6. Required before candidate v3 / acceptance decisions

1. Integrate the reviewed application fixes for **both DEF-08 slices** into a new
   candidate, then rerun the unchanged status/answer-disclosure assertions and real
   outbound/inbound provider-status UI checks. A published fix branch alone is not closure.
2. Record a product/UX disposition for DEF-11 and the remaining P2/P3 matrix items.
   Preserve server-resolved tenant boundaries, compensating financial lifecycles,
   null/zero and evidence-only semantics. Do **not** adopt the draft's unreviewed
   client `X-Business-Id` override suggestion to keep stale forms alive.
3. Correct/demo-test complete standalone assets and HTTP readiness; verify owned
   processes, exact build identity, migration/schema prerequisites and rollback.
   Restrict seeding/reset to proven synthetic targets and preserve existing data/keys.
4. For any public synthetic evaluation, require a fresh approved fixture snapshot,
   separate storage/keys/credentials, access restriction and abuse limits; remove
   the contaminated-data override from the approved public procedure, disable
   external providers explicitly, and complete an actual operator startup/shutdown drill.
5. For a real-client pilot, complete PostgreSQL 17/TLS/HBA and actual runtime/migrator/
   backup role acceptance, real deployment/ingress/secrets/provider/SMTP acceptance,
   monitored rollout and tested rollback. Recovery must pair database, document
   snapshot and **all historical encryption keys** at a consistent point with proven
   RPO/RTO. No such drill or provider acceptance is certified by these tests.

| Intended use | Decision | Conditions |
|---|---|---|
| **Local synthetic demo** | **Conditional GO** | Isolated verified loopback stack, current assets/build, only disposable synthetic data, providers off/disclosed, operator aware of red UX/disclosure findings and cross-tab draft clearing. Not the unverified one-click script |
| **Public synthetic demo** | **NO-GO as-is** | Resolve packaging/readiness/data-preservation/exposure controls, both disclosure slices and operator acceptance. Never expose an unverified existing database via the override |
| **Real-client pilot** | **NO-GO** | Application/product decisions plus all real infrastructure, secret/key, recovery, security and provider acceptance above; known demo credentials/key cannot be reused |

## 7. Hosted CI, reproducibility and unverified scope

GitHub was checked for the **exact frozen SHA**, not another branch:
[Release validation](https://github.com/aviongg/sme-financial-webapp/actions/runs/43756002106)
and [Dependency/secret checks](https://github.com/aviongg/sme-financial-webapp/actions/runs/43756002138)
are completed **success**. Those are **prior hosted evidence**, not this task's fresh
full regression. This QA's broader probes remain red. QA-only documentation publication
does not rerun or replace the candidate's hosted release gates.

See [reproduction commands](candidate-v2/README.md). The QA runner verifies frozen
source/test trees and tracked application diffs, makes a task-owned generated copy
of the existing gate and adds these probes. It never edits that original gate,
starts the demo launcher, connects to port 5433 or needs Antigravity's manifest.
No failed assertion is expected-green, skipped or retried automatically.

Not freshly executed here: the complete 1,821-unit/87-IT regression (only focused
18-unit/53-IT subsets ran), OCR Python/HTTPS suite, older standalone commercial/FX/
commission/project/funding/accounts-live/unified-live and 11 historical acceptance
wrappers, native device camera, actual PostgreSQL 17/TLS/production grants, Docker
image builds/scans, live providers/SMTP, paired recovery/operator drills, public
tunnel, VPS deployment and production rollback. Existing v2 CI/documentation is
prior evidence for its covered tests, not a pass for these external gates.

**Stop after QA publication.** No source fix, migration, merge, deployment, public
tunnel or real-client activation was performed or authorized by this report.
