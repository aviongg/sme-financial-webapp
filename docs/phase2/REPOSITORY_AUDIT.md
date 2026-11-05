# Actual repository audit

Audit dates: 9–10 October 2026, Asia/Karachi. Inspected current checkout and live GitHub; old `tmp/` source snapshots were excluded from code discovery. The supporting audits document entities/repositories/services/DTOs/controllers/authorization/tests and every migration. This is a code and test audit, not certification of production.

## Live Git and source identity

| Fact | Verified value / method |
|---|---|
| Repository/remote | `aviongg/sme-financial-webapp`, `https://github.com/aviongg/sme-financial-webapp.git`; remote and GitHub connector agree |
| Current working branch | `dev/fatima`; no branch switch was needed |
| Baseline HEAD / origin/dev/fatima | `b412662866519627ccc81ae80dcc6a5aaf48fe8d` |
| main / origin/main | `b51e27696cccc59b94aa9d9c9388ad0faa5b4176` |
| Difference | `git rev-list --left-right --count origin/main...origin/dev/fatima` = `0 9`; direct tree diff empty; local HEAD versus origin/dev/fatima = `0 0` before setup |
| Merge ancestry | b412662 has parents historical dev/fatima `9bc89720e6e42427e3dab9a6061a4a41661d1863` and integrated main b51e276; main contains refinement `da62a4164f3ed90a9d1a75f6a3e75da5c881c4a1` |
| PR #3 | GitHub metadata confirms merged, merge SHA b51e276, 8 October 2026; final source head `2ce99238de6d95f22f6fd78dcfee7d77f86f3d6b` |
| Current remote heads | Live `ls-remote --heads` found main, dev/fatima and dev/suleman (`f2ed9bda671404598bd73729563c6e0ceb2b08d1`). Historical reports list other former branches; do not infer their present existence from those reports. No branch was deleted by onboarding. |
| Active PR work | GitHub open-PR search returned none during audit; no active Phase 2 implementation in current main/dev/fatima source. This does not prove no unpushed work exists on another developer's machine. |
| CI | No tracked `.github/workflows`; baseline combined statuses empty and PR-triggered workflow-runs result empty. The latter tool has limited scope; no CI success inferred. |
| Migration | Exactly V1–V17 in `backend/src/main/resources/db/migration`; no current V18 or Phase 2 reservation |
| Initial local work | No tracked modifications. Untracked `docs/Phase_1_Backend_Remaining_Work.docx` and `tmp/` pre-existed and were preserved/excluded from commit. Ignored dependencies/builds/environment files were not treated as source. |

A successful fetch refreshed metadata at start and resume. Initial sandbox fetch could not write `.git/FETCH_HEAD`; scoped authorized execution resolved it. Before publication another fetch/branch/remote verification is mandatory. Exact delivered commit belongs to the final verified report and containing Git commit, not a fabricated self-referential hash.

## Recursive tracked-source inventory

Baseline `git ls-files`: 598 files. Backend 371, frontend 128, OCR 27, docs 27, Docker directory 16, scripts 14, certs 5, database 2, root configuration/readme/progress/compose files 8. Backend test tree 111 files, frontend tests 3 scripts, OCR tests 9 files (these are file counts, not test counts). No existing AGENTS.md was found in the checkout or inspected parent locations. New root instructions apply only after this onboarding.

| Domain | Detailed audit and extension consequence |
|---|---|
| Authentication, sessions, MFA, CSRF, identity, selection, membership/invitations, profile | [Backend](audit/BACKEND.md); real shared platform to extend. No alternate Phase 2 auth/tenant model. |
| Monthly records, score/components/methodology/evidence/history, advice/recommendation lifecycle | [Financial data](audit/FINANCIAL_DATA.md); deterministic implemented flow with snapshot/current-score limits. |
| Cash-flow history/projection, documents/OCR/review/confirmation, search, Zakat, WhatsApp, audit/crypto | Both backend/financial audits; no invoice ledger or LLM inferred from OCR/search. |
| Next routes/live versus demo, session/API wire types, onboarding, dashboard/forms/documents/settings/team | [Frontend](audit/FRONTEND.md); reusable live integrations and strict demo boundary. |
| English/Urdu/RTL/mobile/loading/errors/empty UI and component reuse | Frontend audit; existing typed translation and responsive patterns plus modal/accessibility limitations. |
| Every migration and resulting schema/index/FK/precision/nullability | Financial audit V1–V17 table and relationship diagram. Legacy financial user_id = business ID. |
| Docker, Nginx/CSP, PostgreSQL role/TLS, backups/migration/security documentation | Backend audit and existing PRODUCTION_RUNBOOK/SECURITY_ARCHITECTURE/SECURITY_PRODUCTION_CLOSURE; source presence does not establish deployed acceptance. |

Current documentation reviewed includes README, PROJECT_PROGRESS, MAIN_INTEGRATION_HANDOFF, MVP_CORE_REFINEMENT_HANDOFF, security architecture/final audit/closure, production runbook, storage encryption and backup guides, frontend/OCR READMEs and historical branch guides. Preserve dated reports rather than overwrite them. Root README's generic main-based branch guidance receives a B-specific pointer; phase2 documents own the current B workflow.

## Verified findings and phase 2 implications

1. **No Phase 2 transactional foundation exists.** Monthly financial persistence, evidence/advice/history and reviewed OCR are real. Accounts/ledger/obligations/invoice lines/projects/estimates/stock movements/commission/FX/workspace/LLM remain new work; all proposed classes are labeled.
2. **Source integrity is the central financial integration gap.** Same-document confirmation is guarded; multiple uploads of one economic source and distinct-document concurrent month updates are not protected by a canonical source journal/month version. Manual snapshot replacement can erase prior contribution without changing document confirmation. C01/C02/P14 must govern every new source.
3. **Audit actor/tenant linkage needs scoped remediation.** Some financial/document/WhatsApp callers pass legacy business UUID as actor and null tenant; SecurityAuditService clears an actor not in app_users. DocumentReviewService's explicit actor is a useful correct pattern. This is source-verified, not a claim of reproduced exploitation.
4. **Financial semantics must not drift.** Legacy money precision, score null-COGS behavior, declared payment/compliance and monthly history/upsert constraints are documented; new analytics must expose unknowns and source basis without silently changing historical methodology.
5. **Phase 2 security goes beyond current role-only reads.** SALESPERSON row scope, module guards, report exports and AI tool authorization require A/B contracts and tests. Reusing broad financial read alone is insufficient for restricted views.
6. **Local runtime/test setup had recoverable gaps.** Wrapper/cache access, older frontend installation and missing Python test dependencies are tracked in setup/evidence. Dependency restoration changes ignored installs only, not lockfiles or app source.
7. **A real regression failure remains.** Full local PostgreSQL integration runs twice report 91/92 passed; `DocumentSecurityPostgreSqlIT.testDocumentLifecycleAndCompensation` line 416 expects `processing_interrupted`, gets `unavailable`. Isolated class rerun passes 2/2. Source overlaps async retry with manually forced recovery, consistent with timing interference; exact root cause is not proved. Do not erase full-run failure or skip it. No product/test fix is included in docs-only onboarding.

## Verification scope

The current results/commands and exclusions are authoritative in [IMPLEMENTATION_STATE](IMPLEMENTATION_STATE.md) and [verification evidence](verification/2026-10-10.json). Presence of a test is not execution. Prior 8 October acceptance remains historical. This session runs real disposable PostgreSQL and source tests; production PostgreSQL 17 TLS/HBA/grants, final Docker/ingress browser, real encrypted staging restore and real SMTP/Google/WhatsApp delivery remain unverified. Do not claim production readiness from these artifacts.

## Gap/reuse conclusion and next step

Reuse the integrated MVP's security, monthly scoring/evidence/advice and document lifecycle. Add financial event authority once under A; add B commercial/configuration/intelligence modules against approved contracts. Start B with F02, then F13 core, after the relevant C03/C10/C12/P09 review. Address/document the baseline regression before a feature's full regression acceptance. All fourteen packages and supporting work are mapped in [FEATURE_REGISTER](FEATURE_REGISTER.md); no feature coding was performed during this audit.
