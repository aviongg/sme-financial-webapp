# Current Phase 2 implementation state

## Client-pilot hardening — completed local validation 24 October 2026 (Asia/Karachi)

The explicitly requested R01/R02/R03, numeric-bound and F06-documentation pass is complete on **dev/fatima**, based on `14cc912a078485ef7499c58cb5b76641c436f7f0`. The coordinated migration is **V29**. Only this branch was fetched during the continuation; no other developer implementation was inspected, copied or imported. The user authorized one normal push after all checks. The containing commit is the delivery identity (`git log -1 -- docs/phase2/PILOT_HARDENING.md`); exact post-push SHA and remote verification are recorded in the external final handoff rather than a self-referencing commit. No production/shared database or deployment action was taken.

Historical allocation: the initial unpublished V20 was this task's own SQL. Work stopped on 21 October when another developer's external reservation was discovered. The explicit continuation allocated V29; only the filename/references changed. The final migration set is unchanged V1–V19 plus V29, with no V20–V28/V30 imported or authored here.

| Continuation-start WIP | Retained and completed |
|---|---|
| Records version/locking/provenance, confirmation DTO/service, V20 SQL and partly adapted tests | Retained; renamed own unpublished migration V29; completed exact numeric checks, JSON compatibility, mandatory transactions, real concurrency/rollback/tenant/source tests |
| OCR attempts/recovery, upload and WhatsApp actor changes | Retained; unified stale timestamp interpretation, required audit dependencies, exact persisted token checks, terminal audit transactions and deterministic races |
| Frontend saved workspace draft | Reviewed and applied; stale-version reload, editable source-floor error, explicit duplicate acknowledgement, EN/UR and browser acceptance completed |
| F06 publication documentation and upgrade/role test draft | Retained; publication facts corrected; V29 upgrade/constraint/privilege evidence completed; current handoff and API/policy boundary recorded |

No valid WIP was discarded, reset or stashed. Only incomplete implementations and test fixtures were corrected; no user-owned files were written or staged. Detailed design is in [PILOT_HARDENING.md](PILOT_HARDENING.md) and machine-readable evidence in [2026-10-24-pilot-hardening.json](verification/2026-10-24-pilot-hardening.json).

| Check | Result |
|---|---|
| Targeted hardening | 100 default cases and 44 PostgreSQL cases passed, zero failures/errors/skips |
| Full backend regression | 973 default cases: 972 passed, 1 existing Windows symlink skip; 154 local PostgreSQL cases passed, zero failures/errors |
| Frontend | 86 unit tests passed; TypeScript and ESLint passed (one existing SessionProvider warning); production Next webpack build passed |
| Synthetic browser | 53 checks passed (24 + 10 + 19); eight screenshots visually reviewed |
| Real production-frontend browser | Pilot hardening: 11 checks; F02: 11 checks; F13: 16 checks; F06: 14 checks; all passed with real Spring/session/CSRF/disposable PostgreSQL and synthetic accounts; external providers simulated/disabled |
| Migration/permissions | Fresh V1–V19 then V29 and populated V19→V29 passed; legacy null/negative values and scores preserved; scoped FK, unique document, append-only and runtime/backup privileges verified |
| Preserved boundaries | V1–V19, F06 source, scoring/CashFlowService, AI/F13 and ModuleRegistry unchanged; main untouched; no other developer code imported |

Earlier failed runs are retained as failures: the first targeted default run had two confirmation request errors because an omitted primitive duplicate flag failed JSON deserialization; the fix normalizes omitted/null to false. The next targeted run had six missing business-profile fixture errors and two insufficient-scoring-fixture failures. Fixtures were completed with real required parent rows and known synthetic repayment evidence. The new pilot browser fixture likewise needed sufficient unchanged scoring inputs while preserving its zero/null assertions. F02 mobile width initially failed before the Urdu font/viewport settled; the test now awaits fonts and two animation frames before the unchanged width assertion and records measured layout diagnostics (390px viewport/390px scroll width, no overflowing elements). The first full regression had one older deliberate-edit test omit expectedVersion; it now submits the initial response version and asserts version advancement while retaining all snapshot-replacement/null assertions. A subsequent full run exposed the new migration fixture leaving a plaintext full name in the shared disposable database; the fixture now uses the existing encryption implementation and test key, preserving strict authentication decryption and all privilege assertions. No assertions, score policy, encryption policy or concurrency checks were weakened. The corrected targeted, browser and final full runs pass.

R01 now has deterministic stale/fresh/equality and old/new attempt PostgreSQL evidence, including the historically flaky recovery scenario on a separate never-dispatched stale document. R02 has same-business parent locks, exact version conflicts, source floors, append-only provenance and human duplicate acknowledgement, with complete rollback tests. R03 uses authenticated actor plus business for interactive financial actions and null actor plus business for background outcomes. Historical failures below remain dated evidence, not current unresolved hardening status.

V29 reservation: `{version:29,feature:"client-pilot R02",author:"Developer B implementation",reviewer:"explicit user pilot policy and coordinated allocation; reciprocal signoff not asserted",filename:"V29__monthly_record_integrity.sql",status:"IMPLEMENTED_LOCALLY_VERIFIED",producerBranch:"dev/fatima",producerCommit:"containing commit",date:"2026-10-24"}`. Tests validate **V1–V19 then V29 / V19→V29 only**. Combined V20–V29 sequencing remains deferred until intentional branch integration. Nineteen production-target cases remain excluded and unaccepted. Java21/PostgreSQL17/TLS, production restore/live-provider/client UAT and reciprocal review remain separate release gates.

## F06 bounded planning — 18–19 October 2026 (Asia/Karachi)

Explicit user request authorizes only a stateless, non-posting cost/pricing preview and EN/UR/RTL user journey. Originally developed on isolated branch `codex/f06-bounded-costing`, base `1f72c6e8500f4a94a103768f1d71f980395af143`, verified after fetching origin. The exact tested tree became commit `14cc912a078485ef7499c58cb5b76641c436f7f0` and was subsequently fast-forwarded into local and remote `dev/fatima` under explicit integration authorization. Integration changed no feature code and preserved the existing untracked files.

| Reserved scope | Sole editor | Status |
|---|---|---|
| New backend estimates package and its unit/endpoint tests | B / backend agent | Complete; editor released |
| Frontend estimates page/components/API, navigation, EN/UR and unit tests | B / frontend agent | Complete; editor released |
| New F06 real browser harness/script | B / browser agent | Complete; editor released |
| F06 documentation/evidence, shared test commands and integrated review | B / root | Complete in containing commit |

No migration reserved or authored; V1–V19 remain unchanged. The preview is a core-finance planning capability using existing permission/session/CSRF/business context, under current `/api` conventions. The future `costing` module and its `projects` dependency remain unchanged/unavailable; no C03/P09 availability semantics change or approval is asserted. P05/P10/P13 remain open; calculation precision is not settlement/accounting rounding. Persistence, approval/history, F05 conversion, tax, FX and all financial posting remain blocked/out of scope.

**Requested bounded development slice complete; full F06 remains PARTIAL_VERIFIED / BLOCKED_DEPENDENCY for persistence and conversion.** [Actual scope and API](F06_IMPLEMENTATION.md), [dated verification](verification/2026-10-19-f06.json). All existing active roles already possess FINANCIAL_DATA_READ; suspended/removed/no-membership and missing-permission paths are tested without inventing a disallowed current role. BigDecimal exact cost arithmetic and explicitly labeled 24-place division calculation precision preserve null denominators; no accounting/settlement policy is approved. Required 800 / 20% fixtures yield 1000 for margin and 960 for markup.

| Fresh F06 gate | Result / qualifications |
|---|---|
| Targeted backend | **33 unit + 8 real PostgreSQL endpoint cases passed**, zero failures/errors/skips |
| Full local backend verify | **968 default cases: 967 passed, 1 existing Windows symlink skip; 122 PostgreSQL cases passed**, zero failures/errors. Includes relevant session/MFA/CSRF/tenant/security tests. Nineteen production-target cases explicitly excluded, not accepted. R01 did not recur; no R01 fix is claimed. |
| Frontend | **81 tests passed**, including 15 F06 cases; TypeScript passed; ESLint zero errors and one existing SessionProvider ref-cleanup warning |
| Production build | **Webpack build passed**, with framework middleware deprecation/Edge runtime warnings; no dependency or lockfile change |
| Real F06 browser | **14 checks passed**, production Next + actual Spring/session/CSRF + disposable PostgreSQL; synthetic accounts/data, AI disabled. Zero browser/CSP/hydration errors or external browser requests. Final evidence capture reviewed in desktop and Urdu/mobile. |
| Existing browser regressions | **24 + 10 + 19 = 53 checks passed** against separate controlled synthetic API fixtures on the production frontend; distinct from real backend evidence |
| Financial/source integrity | Before/after full-row snapshots across nine financial/configuration tables and actual browser monthly/score equality pass. Financial/security/identity/configuration production source, V1–V19, and R01/R02/R03 source/tests unchanged. |

Runtime: Java 26.0.1 compiling release 21, Maven 3.9.16, disposable PostgreSQL 14.22, Node 24.19.0, Next 16.3.8 and Edge. Local fixtures do not establish production Java21/PostgreSQL17/TLS or release acceptance. Initial sandbox dependency copy/JVM inspection stalled; frozen offline pnpm installation lacked policy metadata. Normal permitted frozen installation passed registry policy checks with unchanged manifests/lockfiles. Feature tests passed on first execution; a later browser rerun improves screenshot position only. Details and exact commands are in the verification record.

Published F06 commit: `14cc912a078485ef7499c58cb5b76641c436f7f0`, sole parent `1f72c6e8500f4a94a103768f1d71f980395af143`. The subsequent integration was a clean fast-forward and normal push, verified on `origin/dev/fatima`, without rewriting or retesting an unchanged tree. The test results above describe that exact F06 tree, not later hardening changes. Main and dev/suleman remained unchanged. Full F06 remains PARTIAL_VERIFIED; production acceptance and the nineteen production-target cases were not performed.


## F13 client-pilot remediation — 18 October 2026 (Asia/Karachi, isolated branch `f13-client-pilot-remediation`)

Bounded fixes on base `13c2851c916f6a35968841bfccc8a404708b299a`; not pushed to `dev/fatima` and not merged. Scope: F13 order normalization (`WorkspaceResolver` baseline now filters unavailable widgets before seeding), live recommendation text/status/actions with read-only preview, 409 reload that rebases the unsaved draft onto the latest server layout, language switch that no longer remounts an open draft (non-boundary session revalidation), neutral cash-flow presentation for unknown net using the authoritative `netCashFlow` field, V18/V19 role-privilege test coverage, and first/last keyboard focus retention. No migration, `health-score-v1`, `CashFlowService` or accounting change. `DatabaseRolePrivilegesPostgreSqlIT` is production-target and was updated but not executed locally; `DisposableDatabaseRolesIT` covers the same privileges on disposable PostgreSQL.


## F13 development completion — 17 October 2026 (Asia/Karachi)

The user's explicit F13 implementation request authorizes the complete independent MVP workspace journey. Fetched `HEAD=origin/dev/fatima=9f5b2b46bf9aaa8bacc462c7217a45db46b0fc77`; origin/main remains `b51e27696cccc59b94aa9d9c9388ad0faa5b4176`. Existing untracked document and tmp files are preserved. C03/C10/C12/P09 reciprocal review is pending; no approval or financial policy change is implied.

| Reserved scope | Sole editor | Status |
|---|---|---|
| V19__dashboard_preferences.sql; backend dashboard/workspace package and workspace tests; AuditEventType additions | B / backend agent | Complete; reservation made before editing, V18→V19 upgrade verified; editor released |
| Frontend workspace components/API/translations, LiveDashboard, CashFlowTrend compatibility, F02 preset-help EN/UR strings and workspace helper tests | B / frontend agent | Complete; existing components and financial source values reused; editor released |
| Synthetic browser fixtures and three existing browser suite compatibility edits | B / review agent | Complete; existing assertions preserved and empty-state coverage added; editor released |
| F13 real browser harness/script, docs/contracts/state and integrated validation | B / root | Complete in the containing acceptance milestone; editor released |

**Requested development scope is complete. Formal status is PARTIAL_VERIFIED pending reciprocal C03/C10/C12/P09 review; production acceptance is separate.** Read the [actual F13 contract](F13_IMPLEMENTATION.md) and [dated machine-readable evidence](verification/2026-10-17-f13.json). Four meaningful F02 presets, authenticated batched workspace data, actor/business-scoped versioned overrides, and complete EN/UR add/hide/reorder/resize/preview/save/cancel/reset journeys work end to end. Permission/module restrictions precede saved layout. Empty businesses have one useful first-record state; their layout remains editable. Future producers remain unavailable. Existing authoritative financial services, `health-score-v1`, role grants and global AI defaults are unchanged; no paid provider calls.

**Publication:** M1/M2 backend milestone was pushed and verified at `8ec373c1a600a2a480a1c9813512b2302a403319`. The initial backend gate passed 11 unit + 8 PostgreSQL cases; two later tests strengthened validation and inactive-order retention. M3–M5 frontend/personalization/F02-AI integration was pushed and live-verified at `277eb00945475ae51ab0e23e754bc635ba64fc3e`. M6 records the integrated evidence below. Resolve the final acceptance commit with `git log -1 --format=%H -- docs/phase2/verification/2026-10-17-f13.json`, then verify live origin/dev/fatima/ancestry. The exact final remote SHA is reported after publication, not embedded recursively in its own commit.

| Final gate | Result / scope |
|---|---|
| Full local backend verify, 16 October | **931 default cases: 930 passed, 1 existing Windows symlink skip; 113 PostgreSQL cases passed**, zero failures/errors. R01 did not recur; its historical intermittent defect remains open. Nineteen production-target cases explicitly excluded, not accepted. |
| Final workspace verify, 17 October | **12 unit + 9 real PostgreSQL cases passed**, after the default preset width/order polish. Includes V18→V19 preservation/grants, first-save race, stale versions/configuration, scope and exact source parity. |
| Frontend helpers / static checks / build | **64 passed; TypeScript passed; ESLint zero errors, one existing SessionProvider warning; production webpack build passed**. Final build includes final empty-state and narrow-card fixes. |
| Real F13 browser | **16 checks passed**, production Next + Spring/session/CSRF + real disposable PostgreSQL; all four presets, persistence, concurrency, modules, permissions, business switch, session expiry, Urdu/mobile/keyboard. No runtime/CSP/hydration or external-network errors. |
| Real F02/AI regression | **11 checks passed** on the final frontend, separate Spring/PostgreSQL harness. No paid provider, external-network or browser errors. |
| Synthetic browser regressions | **22 + 10 + 19 = 51 checks passed**, production Next with controlled loopback fixtures; separate from real backend evidence. |
| Visual review | Desktop presets, empty workspace and Urdu mobile screenshots inspected; wide evidence/components avoid tall empty half-rows and narrow money cells retain exact formatted amounts. |

Runtime is Java 26.0.1 compiling release 21, Maven 3.9.16, disposable PostgreSQL 14.22, Node 24.19.0, Next 16.3.8 and Edge. This is not production Java21/PostgreSQL17/TLS acceptance. Sandbox JVM/browser startup and Google Fonts access were blocked; allowed execution succeeded. The network-enabled default Turbopack build failed starting its CSS worker. The supported `next build --webpack` passed without dependency/config changes; the final build retains the existing middleware deprecation warning. All failed attempts remain recorded in the evidence. Browser suites ran sequentially on temporary loopback services with synthetic accounts/data and explicitly simulated AI.

## Historical F02 / AI delivery context

Updated 15 October 2026 (Asia/Karachi). **F02 Business-Specific Configuration + AI-LAUNCH-01 offline development are complete for the requested MVP scope**, explicitly requested by Developer B after onboarding. Formal status remains PARTIAL_VERIFIED pending reciprocal review and the existing full-regression defect R01; client-launch acceptance is deferred. No live model requests, credentials request, production deployment or financial methodology change is authorized. Read [actual scope/contracts](F02_AI_IMPLEMENTATION.md) and the [activation runbook](AI_ACTIVATION_RUNBOOK.md), rather than treating older proposed paths as the implementation.

## Implementation reservation and completion (reserved 12 October 2026)

Fetched baseline: `60f570c53fe0e35248544d24f4d3ac55459564ad`, local and origin/dev/fatima synchronized. Main and dev/suleman are unchanged. V18 is absent in both fetched developer branches (A currently has migrations through V14); unpublished work remains subject to normal coordination.

| Reserved scope | Sole editor | Status |
|---|---|---|
| V18__business_configuration.sql; backend configuration package, related module guards and tests | B / f02_backend | Implemented and tested; editor released; A security review pending |
| backend ai package, schemas/prompts/evaluation resources and tests | B / ai_backend | Offline implementation complete; editor released; no AI migration |
| frontend API/types, onboarding/settings/navigation, translations and AI surfaces/tests | B / feature_frontend | Implemented and tested; editor released; existing permissions and scoring categories retained |
| shared backend configuration, integrated verification, documentation and publication | B / root | Completed in the containing milestone commit; future edits require a new reservation |

The 12 October request authorizes the F02 implementation and a B-owned read-only MVP evidence adapter without waiting for A's future C11. It supersedes the onboarding-only restriction for these two workstreams. It does not approve accounting changes or impersonate Developer A's required review. Existing role permissions remain unchanged. Contract/security review is recorded as pending; real-provider and client-launch acceptance are separate gates.

Implementation direction: preserve mandatory core finance and all legacy MVP defaults; optional module disabling retains historical data/read access and prevents new operational writes; unavailable future modules cannot be enabled. Operating profiles do not change score categories. AI defaults disabled; simulated output requires explicit development configuration and labels. New feature endpoints remain under the existing `/api` convention; the broader `/api/v2` proposal remains unfrozen.

## Repository and publication identity

| Field | Value |
|---|---|
| Repository / persistent branch | `aviongg/sme-financial-webapp` / `dev/fatima` |
| Fetched F13 implementation baseline | `9f5b2b46bf9aaa8bacc462c7217a45db46b0fc77`, HEAD and origin/dev/fatima synchronized before reservation on 15 October |
| Historical F13 backend milestone | `8ec373c1a600a2a480a1c9813512b2302a403319` on origin/dev/fatima; live `ls-remote` matched and subsequent fetches on 17 October confirmed no intervening remote commits before final publication |
| Historical F13 frontend milestone | `277eb00945475ae51ab0e23e754bc635ba64fc3e`; push and live `ls-remote` confirmed on 17 October before the final acceptance commit |
| Verified origin/main | `b51e27696cccc59b94aa9d9c9388ad0faa5b4176` (PR #3 merged 8 October) |
| Baseline relationship | dev/fatima contains main and the prior onboarding/handoff; no branch history replacement |
| Other developer boundary | Outside this task; no implementation inspection, import, checkout or write during the coordinated continuation. Earlier dated references are not current remote verification. |
| Source tested | Final pilot-hardening source on baseline 14cc912a078485ef7499c58cb5b76641c436f7f0; detailed results above |
| Publication commit | Resolve this containing hardening commit with `git log -1 --format=%H -- docs/phase2/verification/2026-10-24-pilot-hardening.json`; exact final normal-push SHA is verified in the external handoff |
| Latest migration | **V29__monthly_record_integrity.sql**, this authorized hardening; V1–V19 preserved |
| Migration allocation / next candidate | V29 implemented for this pass; V20–V28 reserved externally and absent here. Future numbers require explicit coordination. |

The direct user instruction to use dev/fatima supersedes the supplied work division's phase2/integration procedure. Ownership and required financial review remain as specified in that document. No main PR is authorized. No open PR was returned by GitHub search during the audit; no configured tracked GitHub Actions workflow or baseline status was found. An empty CI response is not a pass.

## Completed onboarding deliverables

* Source-grounded repository audit with backend/security/OCR, frontend/live/demo/i18n, all migrations and financial data flow; live GitHub/ancestry and scope verification.
* All fourteen packages plus committed supporting finance/intelligence mapped to reusable code, missing modules, proposed schema/API/frontend/calculations/auth/tests and concrete DoD in FEATURE_REGISTER and three detailed package files.
* Master architecture, source/cutover/OCR double-count prevention, metric ownership, dependency waves, critical path, Developer B sequence and production acceptance separation.
* C01–C12 draft specifications with types/nullability/errors/transitions/versioning/authorization/examples/tests; **C08 belongs to A**. Draft policy register P01–P14; no fabricated approvals.
* Root AGENTS, repeatable new-chat/recovery/commit/push workflow, prompt standard, migration/shared-file reservation rules, source document transcript/hash and verification evidence.
* Local ignored frontend installation restored to frozen pnpm lockfile (Next 16.3.8); declared OCR multipart test dependency restored. No dependency manifest/lockfile/source edits. No permanent DB/server/secrets provisioned. Test server stopped after verification.

## Historical feature and contract status (15 October 2026)

F02, F13 and AI-LAUNCH-01 are **PARTIAL_VERIFIED** in the register's formal review vocabulary. Their requested development journeys are complete: persistent profile/module settings, personalized dashboards, server capability enforcement, opt-in explanations, bounded Ask FinSight, EN/UR and session/tenant isolation. Remaining gates are reciprocal C03/C10/C12/P09/P12 review; R01 is historically intermittent and passed the 16 October full run without a source fix. There is no missing initial UI/API/DB journey or A implementation dependency for these authorized MVP slices. Real-provider compatibility, model quality and production/client acceptance remain separate and unperformed. No contract is FROZEN, no P01–P14 decision is signed, and no owner/reviewer signoff has been supplied.

Apart from the separately recorded bounded F06 preview above, all other F01/F03/F04/F05/F07/F08/F09/F10/F11/F12A/F12B and supporting packages remain **DRAFT, not implemented**, apart from explicitly reused MVP capabilities. A dependency cannot be considered ready merely because its proposed DTO appears in documentation. The current slices preserve financial recognition, valuation, accounting rounding and `health-score-v1`.

| Contract owner | IDs | Review / readiness |
|---|---|---|
| A | C01 C02 C04 C06 **C08** C09 C11 | B reviewer; all draft / real producer absent |
| B | C03 C05 C07 C10 C12 | A reviewer; C03/configuration, C10/workspace and narrow C12 wire subsets implemented in the actual-scope documents; broader drafts unfrozen; C05/C07 producers absent |

## F02 / AI offline verification, 14–15 October 2026

Exact commands, dates, totals and limitations are preserved in [machine-readable evidence](verification/2026-10-15-f02-ai.json). Results come from individual run logs, not accumulated stale Maven XML. Runtime: Java 26.0.1 compiling release 21, Maven 3.9.16, real disposable PostgreSQL 14.22, Node 24.19.0, Next 16.3.8 and Edge. This is not the Java 21/PostgreSQL 17 production stack.

| Gate | Result | Scope / qualifications |
|---|---|---|
| Final default backend suite | **919 run, 918 passed, 1 existing Windows symlink skip**, zero failures/errors | Included in the final local Maven verify on 15 October |
| Final full local PostgreSQL/package verify | **FAIL: 104 run, 103 passed, 1 failure**, zero errors/skips | R01 document-recovery assertion recurred. All 12 new F02/AI integration cases passed. Nineteen production-target cases explicitly excluded, not accepted. |
| Isolated new PostgreSQL suites | **12 passed**, zero failures/errors/skips | Real login, JDBC sessions/CSRF, tenant/role/module boundaries, audited persistence, V17→V18 upgrade and exact authoritative AI evidence |
| AI evaluation revision 2 | **100 parameterized cases passed** as part of the default suite | 20 families, 30 accepted/45 rejected provider outputs/25 unsupported questions; synthetic contract checks, not model-quality evidence or 100 independent financial situations |
| Frontend helper/API tests | **52 passed**, zero failures/skips | 14 October; final frontend source unchanged afterward |
| TypeScript / ESLint / production Next build | **Pass / pass / pass** | One existing SessionProvider cleanup-ref warning; no dependency/lockfile change |
| Synthetic browser suites | **22 + 10 + 18 = 50 checks passed** | Production Next with loopback fixture API; no browser/CSP/hydration errors; screenshots inspected |
| Real HTTP browser journey | **11 checks passed** on final source, 15 October | Production Next + real Spring/session/CSRF + disposable PostgreSQL, simulated AI and synthetic accounts/data; owner/viewer, persistence, explanations, module/history safety, tenant switching and Urdu mobile; no external browser requests or runtime/CSP/hydration errors; desktop and both Urdu screenshots inspected |
| Source / documentation integrity | **Pass** | V1–V17, authoritative financial services, security/identity services, dependencies and lockfiles unchanged; relative links and whitespace checked; final staged secret scan recorded in evidence |

The initial 14 October full run exposed new test-fixture session/CSRF setup and a missing synthetic document UUID; these were corrected using actual login/JDBC sessions and real CSRF cookies without changing production security. The strengthened evaluation exposed score questions being routed by incidental words such as “recorded”; the explicit-score route now takes precedence, with a regression assertion. The intermediate targeted rerun caught CSRF test-postprocessor contamination; both new suites now use the real token/cookie flow.

One 15 October broad command omitted the documented production-suite exclusions: its default suite passed and all 104 local integration tests passed, but all 19 production-target cases errored on missing explicit acceptance-environment settings. That command is **failed**, not production verification. The corrected final local command then reproduced R01 and remains **failed**; earlier passes do not erase it. No assertions were removed and no unrelated recovery code was changed. OCR/backup/production-configuration fixture results below are historical 10 October evidence, not fresh runs for this milestone.

Offline development is implemented and feature-tested. Real-model quality, real API/billing compatibility and deployed client acceptance are **not run / not accepted**. AI remains disabled by default; development simulation is explicitly labeled. C03/C12/P09/P12 review is pending, and no new A-side implementation is required for this MVP evidence adapter.

## Historical onboarding verification on 10 October 2026

Commands and scopes are detailed in DEVELOPMENT_SETUP and [machine-readable evidence](verification/2026-10-10.json). Counts below come from **current run logs**, not summing stale Maven report files from prior runs. Historical 8 October results are not counted again.

| Gate | Result | Scope / qualifications |
|---|---|---|
| Backend default Maven test | **754 run, 753 passed, 1 skipped**, 0 failures/errors | Unit/MockMvc plus included real embedded PostgreSQL tests; existing Windows storage symlink capability skip |
| Full Maven local PostgreSQL/package verify | **FAIL: 92 integration tests, 91 passed, 1 failure**, 0 errors/skips; Surefire again 754/1 skip | R01 below; two production-target suites excluded explicitly, nineteen cases not accepted. Packaging before Failsafe is not overall build success. |
| Full local Failsafe rerun | **Same 91/92 pass, same failure** | Confirms full regression gate remains failed; no assertions disabled |
| Isolated DocumentSecurityPostgreSqlIT rerun | **2 passed** | Does not erase failure in full runs; possible async race/test isolation issue |
| Java/FastAPI HTTPS/configuration | **15 passed** (4 HTTPS + 11 config) | Actual local TLS/uvicorn/auth; provider output controlled; no Google call |
| Frontend frozen dependency install | **Pass**, 356 packages, Next 16.3.8 | Initial stale 16.3.5 corrected; offline store lacked one package, authorized network install succeeded; lockfiles unchanged |
| Frontend API/helper tests | **36 passed**, no failures/skips | Rerun on restored exact install; mocked fetch and real modules |
| TypeScript / ESLint | **Pass / pass**, one pre-existing lint warning | `tsc --noEmit --incremental false`; SessionProvider cleanup ref warning remains |
| Next production build | **Pass**, live mode | Correct API rewrite; initial font-download block resolved with allowed network; middleware deprecation remains |
| Browser fixture suites | **22 + 10 passed**, zero browser/CSP/hydration errors | Current production Next + Edge + synthetic API on loopback; mobile/Urdu checks and screenshot inspected. No real Spring in browser fixture. |
| OCR regression | **248 passed**, two upstream warnings | Current source; fixture providers only. Also ran using existing integration test interpreter; not two distinct coverage sets. |
| Backup/recovery fixtures | **46 passed** | Real age/AES-GCM but simulated PostgreSQL/process boundary; first environment lacked pyrage, recovered with existing test interpreter |
| Production configuration | **15 passed** | PyYAML + Git Bash shell fixtures; not actual Docker/TLS/provider acceptance |
| Docs/source integrity | **Passed**: links/heading anchors, 14 package IDs, 12 parseable contract examples, 17 unchanged migration hashes, whitespace, Gitleaks scans of new docs and the complete staged diff (zero findings) and unchanged application/configuration/lockfiles | See final verification evidence/commit review; no secret/generated-file commit intended |

## Historical findings and remaining acceptance gates

The observations below retain their original failure evidence. R01–R03 are now remediated and locally verified by the authorized hardening pass at the top of this document; production and reciprocal-review gates remain separate.

| ID | Finding / state | Owner or next review / required action |
|---|---|---|
| R01 | `backend/src/test/java/com/app/sme_health_backend/documents/DocumentSecurityPostgreSqlIT.java:416` failed in two 10 October full runs and recurred in the final 15 October local full run: expected `processing_interrupted`, actual `unavailable`. Other full/isolated runs passed. Test retries asynchronously, then manually sets processing/recovery state. Timing interference is source-consistent, not yet proved as sole cause. | Remediated in this explicit pilot-hardening pass; deterministic PostgreSQL/browser/full-regression evidence and remaining release limits are recorded above. Historical failed runs remain failures. |
| R02 | Source-inferred same-month distinct-document/manual contribution race and lack of economic-source deduplication; snapshot overwrite can lose prior OCR contribution. | Remediated in this explicit pilot-hardening pass; deterministic PostgreSQL/browser/full-regression evidence and remaining release limits are recorded above. Historical failed runs remain failures. |
| R03 | Several monthly/document/WhatsApp audit calls confuse business UUID with actor UUID and omit business linkage. | Remediated in this explicit pilot-hardening pass; deterministic PostgreSQL/browser/full-regression evidence and remaining release limits are recorded above. Historical failed runs remain failures. |
| R04 | Legacy scale validation, source/evidence history, negative cash/overdraft and score-unavailable posting behavior need decisions. | P01/P02/P10/P14; preserve old methodology and missing values. |
| R05 | OCR startup recovery/scheduling/storage has single-node and DB/file-boundary limitations; high-volume search/list unbounded. | Relevant future operational slice; no unsolicited infrastructure replacement. |
| R06 | Required A/B financial policy and interface approvals absent. | Relevant owner/reviewer review; F02 starts with C03/C12/P09 subset, F13 adds C10. No implementation approval inferred from docs push. |
| E01 | Docker/production Java21/PostgreSQL17 TLS/HBA/grants, nineteen target tests, deployed ingress browser, real encrypted restore not run. | A DB/recovery + B Docker/browser, explicitly authorized disposable acceptance environment; production deploy requires separate request. |
| E02 | Real SMTP, Google OCR accuracy/CA/proxy and WhatsApp inbound/outbound provider acceptance absent. | Assigned owners plus approved credentials/consent; fixtures do not close these gates. |

The historical baseline failure remains recorded even if a later run passes. R01 passed the 16 October F13 full run; this does not establish a fix. Current local engineering evidence does not establish production acceptance or reciprocal policy signoff. No unrelated document recovery/source-accounting fix is included in F02/AI/F13.

## Migration and shared-file reservations

| Version | Feature | Author / reviewer | Filename / status / commit |
|---|---|---|---|
| V1–V17 | Existing MVP | Historical | Applied/tested baseline; immutable |
| V18 | F02 | B author / A reviewer pending | V18__business_configuration.sql; implemented and upgrade-tested on dev/fatima; reserved at 60f570c on 2026-10-12; producer is the containing milestone commit |
| V19 | F13 | B author / A reviewer pending | V19__dashboard_preferences.sql; reserved at 9f5b2b4 on 2026-10-15, V18 upgrade tested on 16–17 October; producer `8ec373c1a600a2a480a1c9813512b2302a403319` |
| V29 | Client-pilot R02 | B author / explicit user policy and coordinated allocation; reciprocal signoff not asserted | V29__monthly_record_integrity.sql; V19→V29 and fresh branch schema verified; producer is this hardening commit |

Reserve `{version,feature,author,reviewer,filename,status,producerBranch,producerCommit,date}` before authoring SQL. Only reuse a cancelled reservation if it was never applied/shared and both agree. Coordinate numbers across branches in this file; do not assume A's unseen WIP cannot collide. No blanket V18–V40 preallocation.

Shared-file editing for this pass is complete and released. V29 adds the documented contribution privileges; financial writes use the bounded integrity contract above. Scoring, cash-flow methodology and other feature implementations remain unchanged. Future slices must renew reservations; A's work remains A-owned regardless of which reviewed source is later integrated into dev/fatima.

## Next actions and recovery

This authorized hardening task ends after its single verified normal push. Do not begin another feature automatically. A later intentional branch integration must validate combined V20–V29 migrations; production/provider/client and reciprocal-review gates remain separate. F06's bounded preview is already integrated; persistence, approval/history and F05 conversion remain future work requiring a separate request. Historical R01/R02/R03 failures are retained above and superseded for this bounded development scope by the new evidence.

Preserved unrelated local files: `docs/Phase_1_Backend_Remaining_Work.docx` and pre-existing `tmp/`. F13 raw evidence is under `tmp/f13` and `tmp/f13-workspace-postgres*.log`; historical F02 and onboarding evidence remains under its original directories. All are untracked and excluded from publication. Do not clean them by broad recursive deletion. New chats use committed summaries and repeat required checks rather than depending on local scratch. Task-owned loopback servers are stopped after final browser acceptance; harnesses close their own browser/Spring/disposable database. No permanent local server or database was provisioned.
