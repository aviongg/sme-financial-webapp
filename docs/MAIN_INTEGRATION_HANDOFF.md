# FinSight final main integration handoff

Date: 8 October 2026 (Asia/Karachi). Repository: [aviongg/sme-financial-webapp](https://github.com/aviongg/sme-financial-webapp).

This integration makes **main the canonical branch containing the complete current FinSight MVP**. Future development starts from main; no historical branch combining is required. Code integration and production/provider acceptance are separate.

## Source and delivery identity

| Field | Recorded value |
| --- | --- |
| Previous main SHA | `9becac35dd75539218470a6a4d1b6720f212a6f0` |
| Refinement source SHA | `da62a4164f3ed90a9d1a75f6a3e75da5c881c4a1` |
| Integration branch | `codex/main-final-integration` |
| Integration PR | [#3 — Integrate complete FinSight application into main](https://github.com/aviongg/sme-financial-webapp/pull/3) |
| Integration branch final SHA | Resolve the PR's `head.sha` after the final documentation commit; exact value is recorded in the final PR report |
| Merge method | Merge commit; no squash, rebase, force-push or protection bypass |
| Merge commit SHA | Assigned by GitHub upon successful merge; recorded in the final PR report as `merge_commit_sha` |
| Final main SHA | Verified after merge and recorded in the final PR report |
| Database | Flyway **V17**, all V1–V17 scripts retained unchanged |

A commit cannot embed its own hash or the hash of a future merge without changing those hashes. Consequently, this committed document contains reproducible pre-merge evidence; [PR #3's final report](https://github.com/aviongg/sme-financial-webapp/pull/3) records the exact final head/merge/main hashes and results measured from the actual merged main. Until that report records success, merge and post-merge validation must not be inferred from this document.

The initial fetch and a second fetch after resuming work both found main unchanged. `git merge-base --is-ancestor origin/main origin/codex/mvp-core-refinement` returned 0; `git rev-list --left-right --count origin/main...origin/codex/mvp-core-refinement` returned **0 73**. The integration branch was created directly from the refinement source in an isolated worktree, preserving the original dev/fatima checkout and its untracked files.

The source includes the security-production closure and secure frontend work. The historical frontend comparison explains why adapted UI files are present without every earlier frontend commit being an ancestor. No older branch was merged individually.

## Integration changes and preserved model

- Root README, current progress, branch guide and frontend README now describe main, V17, the actual architecture/modules and the remaining production gates.
- Original progress and consolidation documents are preserved as [14 September progress](PROJECT_PROGRESS_2026-09-14.md) and [28 September consolidation](CONSOLIDATED_APP_BRANCH_2026-09-28.md); trailing whitespace in the progress snapshot was normalized for the source hygiene check. All other historical reports remain.
- OCR setup and environment examples now match authenticated multipart bytes and the integrated persistence/review/confirmation workflow. The historical Feature 11 report is explicitly labelled as historical.
- A clean pnpm 11 installation exposed an undecided optional `unrs-resolver` build-script policy. `frontend/pnpm-workspace.yaml` explicitly sets `allowBuilds.unrs-resolver: false`, matching the documented intended policy. The frozen lockfile and all dependency versions are unchanged.
- `backend/src/main`, `frontend/src` and `ai-service/app` have **no changes relative to the refinement source**. All historical migrations are byte-identical in Git. No source contract, financial formula, score band, weight, business-type threshold, missing-input normalization, advice rule or Zakat policy was redesigned.

**ALL OLD BRANCHES WERE LEFT UNTOUCHED.**

**NO AI/LLM FINANCIAL API WAS ADDED.**

**NO PHASE 2 TRANSACTION/ACCOUNTING MODULE WAS ADDED.**

## Current application and architecture

The [current progress report](../PROJECT_PROGRESS.md) lists implemented modules. The application comprises Next.js 16.3.8 / React 19 / TypeScript, Java 21 / Spring Boot 4.1.1 with PostgreSQL/JPA/Flyway and JDBC sessions, and authenticated FastAPI OCR with the existing Google Cloud Vision adapter. Production configuration lives in `docker/` and `docker-compose.prod.yml`; operational/recovery tooling is under `scripts/`. Redis/Celery and an LLM financial engine are not required implemented components.

| Area | Verified implementation / evidence |
| --- | --- |
| Auth/security | Registration, login/logout, sessions, CSRF, MFA, password lifecycle, encryption, audit, active business and OWNER/ACCOUNTANT/MANAGER/VIEWER RBAC; security/JDBC/MFA/tenant PostgreSQL suites |
| Businesses/team | Named creation, list/switch/rename, settings, existing-user invite, self accept/decline, roles, suspend/reactivate/remove; actual HTTP role/invitation journeys |
| Records/health | Scoped monthly persistence and automatic recalculation; 30/25/20/15/10 components, normalization, completeness, thin-history, `health-score-v1`, persisted explanation/history and 18 numerical regression cases |
| Advice | Deterministic English/Urdu evidence and prioritized recommendations; NEW/VIEWED/DONE/DISMISSED persistence and language-preserved action state |
| Cash flow | Recorded inflows/outflows/net/ending cash, 3/6/12/all UI, real calendar gaps and backend projection/confidence |
| Documents | Persistent single/bulk upload APIs (single-file live UI), async statuses, immutable extraction, separate review draft, append-only corrections and explicit atomic confirm-once; unconfirmed drafts never post financial records |
| Search | Tenant-scoped records, scores, insights, recommendations and authorized document metadata |
| WhatsApp | Opt-in and phone configuration, scheduled summaries, provider abstraction, duplicate protection, delivery states and history |
| Zakat | Existing `HANAFI_PK_BUSINESS_V1` / `1.0.0`, declared Nisab/Haul, assets/liabilities, warnings and qualified-review disclaimer; no formal Sharia certification claim |
| Frontend | Live auth/onboarding, dashboard, financial flows, documents/review, teams/settings/search, English/Urdu/RTL and responsive screens |

Live API failures surface as errors; there is no financial mock fallback. Explicit demo mode remains separate. Frontend import traversal covered 57 local modules without mockApi references; an API regression also rejects network failure instead of substituting sample data.

## Pre-merge validation on 8 October 2026

Maven 3.9.16 and JDK 26.0.1 compiled Java release 21. Integration tests used real disposable PostgreSQL 14.22 under the restricted `finsight_app` runtime role. Production images target Java 21/PostgreSQL 17 and require the separate acceptance below. Direct installed Maven was used where the Windows wrapper/sandbox could not resolve cached artifacts. `mvn` below denotes that installed Maven; `./mvnw` is the portable repository equivalent.

| Check | Exact command / result |
| --- | --- |
| Backend + local PostgreSQL | From backend: `mvn -B -o -Ppostgres-it '-Dit.test=*IT,!DatabaseRolePrivilegesPostgreSqlIT,!PostgreSqlTlsConnectionIT' verify`: **BUILD SUCCESS**; Surefire **754 total, 753 passed, 1 skipped**, 0 failures/errors; Failsafe **92 passed**, 0 failures/errors/skips. Executable JAR packaged. [Per-suite totals](main-integration-evidence/backend-premerge-totals.json). |
| Numerical score regression | Included in full Maven run: **18 passed**; exact pre-refinement expected values across business types, optional data, thin/mature history and calendar movement. |
| Actual HTTP journey | Existing CoreJourneyClosureIT and MvpCoreJourneyIT included above: registration/login/cookies/CSRF/JDBC sessions, named business, records, score/evidence/history, advice/actions, dashboard, search, upload/review/confirm-once, team invitations/roles, switching, cross-tenant denial and logout/login persistence. External OCR output is a controlled fixture. |
| Database migration | Fresh **V1→V17** via disposable databases and populated **V15→V17** via MvpLegacyMigrationPostgreSqlIT passed. Preserves legacy score 63.53 and existing recommendation/extraction data; does not invent missing historical explanation. |
| Java/FastAPI HTTPS | Set `FINSIGHT_OCR_CONTRACT_PYTHON` to the OCR test interpreter, then from backend: `mvn -B -o '-Dtest=OcrIntegrationConfigurationTests,OcrFastApiClosureProbe' test`: **15 passed** (11 configuration, 4 real HTTPS). Real uvicorn/TLS/auth with controlled provider output; no Google call. |
| Frontend contracts | From frontend: `pnpm test`: **36 passed**, no failures/skips. |
| TypeScript | `pnpm exec tsc --noEmit`: exit 0. |
| Lint | `pnpm run lint`: exit 0; one existing SessionProvider effect-cleanup ref warning. |
| Production build | With `BACKEND_API_URL=http://127.0.0.1:8080` and `NEXT_PUBLIC_DATA_MODE=live`: `pnpm run build`: exit 0; existing Next middleware deprecation warning. |
| Browser contracts | Production Next on port 3100; `node tests/browser.integration.mjs` and `node tests/browser-extra.integration.mjs`: **22 + 10 passed**, no browser exceptions, CSP violations or hydration errors. Edge with synthetic API fixture, distinct from real Spring/PostgreSQL tests. [Main](main-integration-evidence/browser-main.json), [additional](main-integration-evidence/browser-extra.json). |
| OCR regression | From ai-service: `.venv/Scripts/python.exe -m pytest -q -p no:cacheprovider --basetemp=<fresh-evidence-directory>`: **248 passed**, 2 upstream deprecation warnings, no skips or provider calls. |
| Recovery fixtures | From root: `ai-service/.venv/Scripts/python.exe -m unittest discover -s scripts/backup -p 'test_*.py' -v`: **46 passed**; actual age/AES-GCM, simulated PostgreSQL/process boundary. |
| Production configuration | Set `FINSIGHT_TEST_SH=C:\Program Files\Git\bin\bash.exe`; `ai-service/.venv/Scripts/python.exe -m unittest discover -s scripts -p test_production_configuration.py -v`: **15 passed**. |
| Source secrets | Gitleaks **8.30.1** redacted directory scan of tracked/non-ignored current source, excluding generated dependencies/secrets: **0 findings**. Final handoff/artifacts are included in the final scan before commit. |
| Source invariants/whitespace | `git diff --exit-code da62a4164f3ed90a9d1a75f6a3e75da5c881c4a1 -- backend/src/main frontend/src ai-service/app`, migration comparison and `git diff --check`: exit 0. |

Local setup attempts initially encountered sandbox access to Maven/cache/network and a stale Python environment missing the declared multipart dependency. Installing repository-constrained dependencies in an ignored integration-local environment and running authorized commands with normal cache access resolved those setup failures. No test assertion was disabled to make a run pass.

The broader `git diff --check origin/main...HEAD` also reports inherited trailing whitespace/EOF blank lines in eight pre-existing files from the consolidated source, including historical documentation and security/recovery code. These are not introduced by the integration commits and are retained to avoid unrelated source cleanup. The integration-only `git diff --check da62a4164f3ed90a9d1a75f6a3e75da5c881c4a1...HEAD` and working/staged checks pass.

## Skipped and blocked validation

- One existing `LocalFileSystemStorageServiceTests.symlinkEscapeOutsideStorageRootIsPrevented` case skips because this Windows account cannot create its symlink. It must run on a capable host.
- `DatabaseRolePrivilegesPostgreSqlIT` and `PostgreSqlTlsConnectionIT` are explicitly excluded from the local PostgreSQL command because their production-like database/credentials/CA/container fixtures are absent. Their nineteen production-target cases are not counted as passed. Actual disposable-role checks still pass in the included suites.
- No Docker engine/standard CLI was available. Production images, fresh production volumes, PostgreSQL 17 TLS/HBA/grants, Nginx runtime/rendering and backend document-volume recreation have not been accepted here.
- The actual encrypted PostgreSQL staging-restore drill and `test_pg_hba.py` require their provisioned fixtures. Passing recovery/configuration tests is not that drill.
- Browser tests use synthetic API contracts. Actual API-level Spring/PostgreSQL journeys pass, but a full browser journey against deployed frontend/ingress/backend/providers remains pending.
- Actual Google Vision document accuracy and deployed CA/proxy transport, SMTP reset delivery and WhatsApp provider delivery require configured external services. No provider credentials were supplied or committed.

## Historical branch preservation

Before and after integration, compare all pre-existing branch tips; changes are limited to main through its normal PR merge and the newly created integration branch. Recorded historical remote tips:

| Branch | SHA |
| --- | --- |
| dev/fatima | `9bc89720e6e42427e3dab9a6061a4a41661d1863` |
| dev/suleman | `f2ed9bda671404598bd73729563c6e0ceb2b08d1` |
| codex/phase1-frontend | `5914b723a4a78fae1dfb3d4456830fb996b53a93` |
| codex/fatima-secure-frontend | `59c92ed4a86dc6a40652908430e3ea9b479ba208` |
| codex/security-production-closure | `d6653018d475604a973d9c37dad54f9831784e15` |
| codex/mvp-core-refinement | `da62a4164f3ed90a9d1a75f6a3e75da5c881c4a1` |

No branch is deleted, including the integration branch. Repository automatic branch deletion is disabled. Active main protection prohibits deletion and non-fast-forward updates; no bypass is used.

## Post-merge verification procedure and final record

After GitHub records a successful merge, fetch origin and create a clean detached checkout of **the actual origin/main commit**. Confirm backend/, frontend/, ai-service/, docker/, scripts/ and docs/, all 17 migrations, and:

```sh
git merge-base --is-ancestor da62a4164f3ed90a9d1a75f6a3e75da5c881c4a1 origin/main
git diff --exit-code origin/codex/main-final-integration origin/main
```

Re-run backend compilation/tests, PostgreSQL journeys/migrations, frontend tests, TypeScript, lint and build from that checkout; rerun OCR/recovery/configuration and browser checks where possible. Record actual post-merge counts, current main SHA, merge SHA, integration SHA and unchanged old-branch tips in the final PR report. Pre-merge results above must not be relabelled as post-merge evidence. If post-merge code fails, use a normal corrective PR; never rewrite main.

## Known limitations and remaining production acceptance

Use the [production runbook](PRODUCTION_RUNBOOK.md), [security closure report](SECURITY_PRODUCTION_CLOSURE_2026-10-03.md), [MVP refinement handoff](MVP_CORE_REFINEMENT_HANDOFF.md) and [recovery guide](../scripts/backup/README.md) to complete the blocked environment/provider gates. Run migrations through V17 with the migration role before the restricted runtime.

Current model limitations remain documented: absent COGS follows the existing margin formula; stable negative cash movement may still score highly for stability; repayment/compliance are declarations. Legacy OCR provenance cannot be reconstructed, and old score evidence remains absent until recalculation. Invitations require existing accounts; search caps at twenty ranked results and score history at twelve recent scores with older persisted months queryable. No ownership transfer, complete MFA factor-management Settings UI or platform-admin audit viewer is implied.

These limits do not require combining old branches. Future model or product work is outside this integration.
