# MVP Core Refinement handoff

## Baseline and delivery

- Repository: `aviongg/sme-financial-webapp`.
- Starting branch: fetched `origin/codex/security-production-closure`; its remote HEAD was verified as `d6653018d475604a973d9c37dad54f9831784e15` before creating this branch.
- Working/delivery branch: `codex/mvp-core-refinement`. The security closure branch was not modified or force-pushed.
- Backend and recovery commit: `bb1b11f766ec4a531004f8ac7e399a12f3e770b1`.
- Frontend and final search-label commit: `e4ff5ce93c1b1fb93202419f2437c69de878eef0`.
- Final delivery commit is the documentation commit containing this report; obtain its exact hash with `git log -1 --format=%H -- docs/MVP_CORE_REFINEMENT_HANDOFF.md`. A commit cannot contain its own hash without changing that hash. The final chat handoff also records the pushed HEAD.

The repository implementation covers the twelve requested MVP work packages. Production stack/provider acceptance remains separate, as detailed below. No LLM/API integration or Phase 2 accounting module was introduced.

## Changes by work package

1. **Score governance:** new calculations persist the scoring-owned `health-score-v1` constant. Existing rows remain readable with null version/evidence until an ordinary recalculation; the migration does not pretend historical evidence exists.
2. **Explainability:** persisted component snapshots include availability, score, base/effective weight, history used, driver values, and evidence source. Repayment and registration declarations are explicitly self-declared. Thin-history cash flow explains buffer-only scoring; trend remains unavailable below three recorded months. Optional missing scores stay null.
3. **History:** newest twelve persisted scores, descending by month, without filling gaps. Exact previous-calendar-month comparisons show component transitions, including unavailable-to-available. Updating a prior score also refreshes the next month's comparison without changing unaffected numerical scores.
4. **Insights:** deterministic English/Urdu rules use actual buffer, margin, receivables, history and missing-input evidence. Advice rules version is `mvp-evidence-advice-v2`; source hashes include the persisted evidence and previous-month snapshot.
5. **Recommendations:** driver-based actions and `NEW`, `VIEWED`, `DONE`, `DISMISSED` lifecycle. Language-only refresh preserves IDs, status and status time. A changed calculation timestamp may reset the corresponding action to NEW. Profile/row locks serialize refresh and status writes; the dashboard chooses an actionable next step and prioritizes missing inputs for low completeness.
6. **Search:** adds document filename/type/status/linked-month metadata and persisted health scores. It never searches raw OCR text. All candidates are tenant-scoped and ranked before the twenty-result cap. Viewer searches exclude documents; an explicit forbidden document filter returns 403. Record links use months, document links use document IDs, and health links select the score month.
7. **Document provenance:** immutable original extraction, separate current reviewed JSON and append-only correction snapshots with authenticated actor/time/changed fields. Numeric formatting alone is not an edit; omitted PATCH fields are preserved, explicit null clears the supplied field, and confidence is never upgraded by a human edit. Review never posts financial data; confirmation retains its existing atomic/idempotent behavior. Retry cannot overwrite preserved extraction. Real HTTP testing also exposed and fixed OCR dispatch bypassing its async proxy: explicit executor submission and a separate worker now avoid joining an already-committed transaction, including the executor's caller-runs path.
8. **Live language coverage:** central typed English/Urdu copy and readable enum/error labels across dashboard, health, search, document review, Zakat, settings, onboarding, team and authentication states. Urdu direction and Western financial numerals are retained. Backend advice still uses the backend TranslationService.
9. **Team access:** existing-account invitations, self-only acceptance/decline, scoped membership listing, role changes, suspension/reactivation and removal through existing RBAC. Invitations grant no financial access before acceptance; suspension/removal takes effect on subsequent protected requests. Normal invitations cannot create an OWNER, and the current owner cannot be modified or removed through these endpoints. Actions use the existing audit service.
10. **Business identity:** required name on creation, authoritative name on Business, names in selectors/settings, owner rename, safe legacy placeholder. Business type remains unchanged by rename and does not silently change historical scoring.
11. **Live presentation:** reuses the existing pure dashboard score/components/completeness/next-step UI with API data. No live mock fallback or client-side recomputation of canonical score/band was added. Health exposes history and evidence; cash-flow period controls/detail view and backend projection/confidence remain available.
12. **Integrated regression:** actual HTTP/cookies/CSRF/Spring services/JDBC sessions/PostgreSQL journeys cover the combined flows and all four roles. Only the external OCR provider output is a controlled fixture in the new journey.

## Migrations and rollout

| Migration | Additions and compatibility |
| --- | --- |
| `V16__score_explainability_and_actions.sql` | Nullable score methodology/evidence; constrained recommendation status defaulting to NEW and optional status timestamp. No financial backfill or rescore. |
| `V17__business_team_and_document_provenance.sql` | Business name with `My business` legacy default; reviewed draft/provenance; append-only correction table, runtime grants and database immutability triggers. Existing extracted JSON is conservatively labelled `LEGACY_UNKNOWN` because prior releases overwrote it. |

Historical migrations V1–V15 are unchanged. Fresh V1–V17 and populated V15→V17 upgrades were exercised. Legacy score values, recommendation text and extracted JSON survive; absent historical methodology/explanation is not invented. Recovery validation now also requires the correction table and all three provenance triggers.

Deploy the matched frontend/backend revision and run the existing migrator through V17 before starting the new runtime. New business creation requires `businessName`; older creation clients must be updated. Use the existing [production runbook](PRODUCTION_RUNBOOK.md) and [recovery guide](../scripts/backup/README.md), with this revision's complete migration set.

## API contract changes

All business-resource endpoints resolve the active business on the server; clients do not supply a financial tenant ID. Existing session, MFA, CSRF and permission enforcement remains in place.

| Endpoint | Behavior / authorization |
| --- | --- |
| `GET /api/scores/history` | Up to twelve actual persisted scores; financial read. Existing score/query/dashboard DTOs also include methodologyVersion and explanation. |
| `PATCH /api/recommendations/{id}/status` | `{status}`; scoped existing recommendation; RECORD_CREATE_UPDATE (OWNER/ACCOUNTANT). Returns refreshed advice and lifecycle state. |
| `POST /api/search` | Existing `{query,type}`; types now also include document/score, max query length 200. Documents additionally require DOCUMENT_READ. |
| `POST /api/businesses` | Existing request plus required `businessName` (trimmed, max 120). All business responses include name. |
| `PATCH /api/businesses/active/name` | `{businessName}`; current owner only. Does not edit businessType or rescore. |
| `GET /api/memberships` | Active-business member email/role/status, no internal user ID; MEMBERSHIP_MANAGE. |
| `POST /api/memberships` | `{email,role}`; existing active registered account, ACCOUNTANT/MANAGER/VIEWER; creates INVITED, returns 201. |
| `PATCH /api/memberships/{id}` | Optional role/status; ACTIVE/SUSPENDED for accepted members. Cannot accept an invitation on someone else's behalf. |
| `DELETE /api/memberships/{id}` | Scoped non-owner removal; 204. |
| `GET /api/invitations` | Signed-in account's pending invitations; works without an active business. |
| `POST /api/invitations/{id}/accept` or `/decline` | Invited account only, 204. Acceptance makes the business selectable; it does not silently switch context. |
| `PATCH /api/documents/{id}` | Existing draft correction route, now presence-aware PATCH and separate reviewed snapshot; DOCUMENT_EDIT. |
| `GET /api/documents/{id}/corrections` | Scoped append-only history with before/after JSON, fields, time and actor-is-current-user marker; DOCUMENT_READ. |
| Document DTOs | Add reviewedData and extractionProvenance; extractedData retains original/legacy evidence. Confirm remains explicit. |

## Verification on 4–5 October 2026

Maven 3.9.16/JDK 26 compiled Java release 21; PostgreSQL tests used real disposable PostgreSQL 14.22 with the restricted `finsight_app` runtime role. Production Java 21/PostgreSQL 17 containers require the acceptance gates below. Direct installed Maven was used with the existing local dependency cache; portable commands follow.

| Check | Command and final result |
| --- | --- |
| Backend and local PostgreSQL | `./mvnw -B -o -Ppostgres-it '-Dit.test=*IT,!DatabaseRolePrivilegesPostgreSqlIT,!PostgreSqlTlsConnectionIT' verify`: **BUILD SUCCESS**. Surefire **754 total, 753 passed, 1 existing Windows symlink skip**, 0 failures/errors. Failsafe **92 passed**, 0 failures/errors/skips. Executable JAR packaged. |
| New combined HTTP journey | Included above: **3 passed**. Exercises score gaps/exact-month movement, twelve-row history cap and older-month lookup, language-preserved action state, rename invariance, async upload/review/confirm-once, cross-business denial, and invitation/role/suspension/removal across ACCOUNTANT/MANAGER/VIEWER. |
| Numerical model regression | Included above: **18 new cases passed**, using real calculators and fixed pre-refinement expected outputs. Covers 1/2/3/6 records, zero revenue, negative movement, optional inputs, receivables, four business types, normalized weights and exact-month movement. Calculator source files, weights, band thresholds and profitability benchmarks are unchanged from the baseline. |
| Frontend | `pnpm test`: **36 passed**; `pnpm exec tsc --noEmit`, `pnpm run lint`, `pnpm run build`: exit 0. One existing SessionProvider effect-cleanup ref lint warning; existing middleware deprecation warning. |
| Final search-label check | `./mvnw -B -o '-Dtest=TranslationServiceTests,SearchServiceTests' test`: **29 passed** after adding the English/Urdu bank-statement label. No application logic changed after the full backend run. |
| Browser contract acceptance | Production Next build served locally, then `node tests/browser.integration.mjs` and `node tests/browser-extra.integration.mjs`: **22 + 10 checks passed**, 0 browser exceptions, CSP violations or hydration errors. Real Edge browser with a synthetic API fixture; distinct from the actual Spring/PostgreSQL journeys. Desktop, mobile/Urdu and document screenshots were visually reviewed. Sanitized [main results](mvp-core-refinement-evidence/browser-main.json) and [additional results](mvp-core-refinement-evidence/browser-extra.json) are committed. |
| Existing OCR service | `python -m pytest -q -p no:cacheprovider --basetemp=<fresh-directory>`: **248 passed**, two upstream deprecation warnings. No provider/LLM network calls. |
| Recovery | `python -m unittest discover -s scripts/backup -p 'test_*.py' -v`: **46 passed**. Actual age/AES-GCM with simulated PostgreSQL/process boundary, not a real recovery drill. |
| Production configuration | `python -m unittest discover -s scripts -p test_production_configuration.py -v`, with `FINSIGHT_TEST_SH` set to Git Bash on Windows: **15 passed**. The first default-shell attempt failed because its environment/argument handling did not match this fixture; supported-shell rerun passed without weakening checks. |
| Source hygiene | Gitleaks 8.30.1 redacted staged-diff scans and final combined baseline-to-delivery diff scan: no findings. `git diff --check`: passed. Live-root import traversal: 57 local modules, no mockApi references. |

The new integration tests caught actual defects during development: correction JSON number-scale no-ops, PATCH constructor selection treating omitted fields as supplied, and after-commit OCR dispatch. These were corrected and are included in the passing final suites. The existing symlink-escape test still requires OS symlink creation permission; no new test disabling annotation was added.

## Remaining acceptance and limitations

- Docker engine and production DB/TLS fixture settings are absent here. The two excluded production-target suites are not counted as passed; the prior security closure's nineteen production acceptance cases still need the actual stack. Fresh images/volumes, PostgreSQL 17 HBA/TLS/grants, Nginx rendering/cookies/CSP, document persistence across container recreation, and a browser journey against that deployed stack remain pending.
- Actual encrypted PostgreSQL staging restore, production Google Vision/CA/proxy processing, SMTP reset delivery and any real WhatsApp provider delivery remain environment/provider acceptance. See the [security closure report](SECURITY_PRODUCTION_CLOSURE_2026-10-03.md); this task does not claim those gates closed.
- Legacy OCR original-versus-human provenance cannot be reconstructed. Legacy scores do not gain historical evidence without recalculation. Corrections for deleted unconfirmed drafts are retained for database audit but the normal API requires the document still to exist.
- Search ranks tenant records in memory and returns twenty results; this is intentionally the existing MVP scale, not an indexed/semantic search service. Health lists twelve recent scores; older persisted months remain directly queryable.
- Existing model behavior is preserved: absent COGS is treated as zero by the current margin formula, and consistently negative cash movement can still score high for stability. Evidence makes the basis visible; no promise of causal improvement or observed payment history is made.
- Team invitations require an existing account. No invitation emails, ownership transfer, business-type editing, outcome attribution, transaction ledger, accrual/double-entry engine, AR/AP, inventory management, advanced forecast, tax engine, industry modules, automated Sharia certification or AI/LLM integration was added.

## Recommended next step and change inventory

First complete the deployed-stack acceptance above. Then treat these versioned score/evidence and provenance contracts as the stable boundary for a separately scoped financial-data model proposal; any future ledger or methodology change should define migration/reconciliation and numerical regression requirements before implementation. Do not start those Phase 2 modules as part of this delivery.

Backend changes are concentrated in scoring/shared advice, recommendation/dashboard, identity/membership, document review/processing, search, additive migrations and tests. Frontend changes cover API contracts, central i18n, auth/onboarding, live dashboard/health/search/documents/settings/Zakat, shared presentation, lifecycle/team/evidence components and contract/browser tests. Recovery changes add provenance acceptance checks. The [exact changed-file manifest](mvp-core-refinement-changed-files.txt) lists the complete delivery relative to the starting hash.
