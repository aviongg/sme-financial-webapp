# Developer-B final handoff

31 October 2026 (Asia/Karachi). Operational summary for Developer-A integration; details live in [IMPLEMENTATION_STATE.md](IMPLEMENTATION_STATE.md), [FEATURE_REGISTER.md](FEATURE_REGISTER.md) and the [evidence JSON](verification/2026-10-31-dev-b-integration-v35-v37.json).

**Not claimed:** Developer-A review, production acceptance, or full accounting scope for any feature.

## Identity

* Branch: `dev/fatima` in `aviongg/sme-financial-webapp`, fast-forwarded from `311de78f1b517aaaf09420914ebe6a32371280e1`.
* Final Developer-B SHA: **the commit that adds this file** (placeholder; the exact SHA is reported at publication and shown by `git log -1 -- docs/phase2/DEVELOPER_B_FINAL_HANDOFF.md`).
* Built locally as `claude/dev-b-integration-v35-v37`, which was never pushed. Source commits: F09 `f44fd6c`, F06 Phase B `8ac5da2`, F08 Phase A `cd0f624`, UX hardening `f22a1ed`.

## Migration history

Exactly **V1–V17, V30–V37**. There are no duplicates, no Developer-B V18–V29 and no V38.

| Version | Content |
|---|---|
| V30 | Business configuration (F02) |
| V31 | Dashboard preferences (F13) |
| V32 | Monthly record integrity |
| V33 | F05 project planning |
| V34 | F04 commercial documents |
| **V35** | **F09 Sampling / Production Cost Planning** |
| **V36** | **F06 Persisted Estimates** (and exact-fit conversion; authored in isolation as V33) |
| **V37** | **F08 Inventory Catalog Foundation** |

Any database that applied the isolated F06 V33 or F09 V32 must be recreated.

## Bounded features completed (PARTIAL_VERIFIED)

* **F02** configuration and **F13** dashboard preferences.
* **F04** invoices/bills, record-only: `financialEffect = NONE`.
* **F05** project/budget planning: `PLANNED_ONLY`.
* **F06**:
  * Phase A stateless costing preview.
  * Phase B persisted estimates with revisions/approval, plus conversion into an existing F05 project DRAFT planning budget.
* **F08** item catalog plus a stateless, non-authoritative WAC preview (draft policy `f08-wac-draft-1`).
* **F09** sample/production-cost planning: `PLANNED_ONLY`.
* **F12B** read-only reports.
* Frontend UX hardening, covering responsive navigation, the mobile `+` sheet and RTL.

Not implemented: stock journal, COGS, actual project or sample accounting, cash posting, AR/AP posting, and any `health-score-v1` methodology change.

**F06 conversion boundary:** conversion is exact-fit only.
* A revision whose lines cannot be represented exactly is refused with `conversion_precision_unrepresentable`. There is no rounding.
* The target is always an F05 DRAFT, `PLANNED_ONLY` budget.
* Replay is idempotent. Policy P10 is still open.

## Verification (final integrated tree)

| Gate | Result |
|---|---|
| Accepted PostgreSQL | **41 classes / 196 cases / 0 failures, errors, skips** |
| Backend default | **1,154 run / 0 failures, errors / 1 existing Windows symlink skip** |
| Frontend | **158/158**; TypeScript pass; ESLint 0 errors, 1 existing SessionProvider warning; production webpack build pass |
| Browser | **11 suites / 181 checks / all pass** (production Next, real Spring/session/CSRF, disposable PostgreSQL) |
| Mobile `+` sheet | 10 actions; English and Urdu/RTL; 390×844 and 390×600; all actions reachable, internal scroll, no horizontal overflow |
| Financial isolation | Integrated journey: 18 protected tables identical row for row, and zero write statements except the no-op score UPDATEs below. Feature runs: F09, F08 and F04 made zero protected writes; F06 wrote only one F05 DRAFT planning budget plus its lineage row |
| Final privilege definitions | `DisposableDatabaseRolesIT` 6/6 and `DeveloperBIntegratedMigrationPostgreSqlIT` 2/2 re-run after the test-only privilege update |

**Known existing behaviour:** the integrated browser run saw 77 `UPDATE score_results` statements, all leaving the rows byte-for-byte identical.
* **Cause:** dashboard/advice reads run inside the class-level `@Transactional` `AdviceContextService`, which is unchanged since before `311de78`. Hibernate's dirty-checking flushes the unchanged rows.
* **Status:** technical debt, not a data-integrity failure. No numerical score mutation occurred. Deliberately left unchanged.

## Production-target exclusions (external release gate)

`DatabaseRolePrivilegesPostgreSqlIT` (15 cases) and `PostgreSqlTlsConnectionIT` (4 cases), 19 in total, need the production-target role/TLS environment. They are **not executed and not accepted** locally.

The role test now defines assertions for the V35 sample tables, V36 `estimate_project_conversions` and V37 `inventory_items`. These match the migration grants: inventory has no runtime DELETE, and conversions are SELECT+INSERT only. The test compiles.

## Developer-A / policy dependencies

* Developer-A review of:
  * F06 conversion;
  * the F08 draft WAC policy;
  * F09 planning;
  * F04/F05 boundaries.
* Open policies: P05, P06, P07 and P10.
* Ledger, obligations, actuals and inventory movement services (C01/C02/C04/C09). Developer-B work must not substitute for them.

## Next step for A+B integration

Developer A runs a preflight, not a blind merge:
1. Use the latest `dev/suleman` and this final `dev/fatima` in a fresh disposable worktree.
2. Make A's V18–V29 the contiguous unified bridge between B's V1–V17 and V30–V37.
3. Update every shared and feature fresh-history assertion (`CommercialDocumentMigrationPostgreSqlIT`, `MonthlyIntegrityMigrationPostgreSqlIT`, the Sample/Estimate/Inventory migration ITs and `DeveloperBIntegratedMigrationPostgreSqlIT`) to the unified V1–V37 list.
4. Resolve shared files additively.
5. Re-run the accepted PostgreSQL, backend, frontend and browser suites before any merge.

Keep the F09, F06, F08 and UX source worktrees until that combined integration is accepted.
