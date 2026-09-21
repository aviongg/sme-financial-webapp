# Fatima Phase 1 integration handoff

Features 4, 5 and 6 now consume persisted scores and refresh bilingual advice consistently. Feature 10 remains the existing business Zakat preview and is covered by the database integration checks. The reference for Suleman's side is `d571629b0378ee5b7f00e35b50992023cf47dac2`; the verification script records the exact commit it uses. These Phase 1 changes are prepared for publication on `dev/fatima`, separately from local OCR work. Suleman's branch is unchanged; the shared dashboard edits are supplied as tested integration handoffs under `integration/`.

## Implemented behavior

| Area | Behavior |
| --- | --- |
| Insights | Three base insights plus a score delta when the exact preceding calendar month exists. Positive, negative and unchanged comparisons support English and Urdu. Gaps are not treated as one month. |
| Recommendations | Component, health-band and data-completeness advice consumes the canonical score. It does not calculate a new financial score. |
| Refresh | GET generates absent advice and replaces stale or incomplete advice for the selected score month. Identical requests retain IDs, timestamps and content. |
| Language | A profile language change is reflected on the next advice read, including historical months requested explicitly. Stored text for that month is replaced in the chosen language. |
| Concurrency | A native PostgreSQL profile `FOR UPDATE` lock serializes generation and language updates across application instances and blocks insertion of a missing score through its foreign key. Score row share locks protect existing source rows. Unique category constraints provide a second duplicate barrier. |
| No score | Return an empty list even if legacy mock advice exists. No mock score is created or returned. |
| Invalid persisted score | Fail the request rather than display fabricated or silently normalized financial values. |

## HTTP contract

Existing paths and response fields are retained. Advice defaults to the latest **score month**, not creation timestamp or current calendar month.

```text
GET   /api/insights/{userId}
GET   /api/insights/{userId}?month=2026-09
GET   /api/recommendations/{userId}
GET   /api/recommendations/{userId}?month=2026-09
PATCH /api/profile/{userId}/language
      {"languagePreference":"ur"}
```

Language updates accept `en` or `ur`. Missing/unsupported values and malformed months return 400; unknown profiles return 404; an existing profile without a score for the requested month returns 200 with `[]`. No fallback to another month occurs.

Each advice item also includes:

- `language`: language used to generate its text.
- `sourceComputedAt`: the selected persisted score's computation timestamp.
- `sourceVersion`: SHA-256 fingerprint of canonical current and previous score values, computation times, language and advice-rule version. A previous-month correction or a score change with a reused timestamp still invalidates advice.

The response order is deterministic: weakest component, overall health, data quality, and then monthly change for insights when available. Source metadata is nullable only on pre-upgrade cached rows; those rows regenerate before they can be served.

## Scoring boundary

`score.repository.ScoreResultReader` reads the existing `score_results` table through JDBC. It deliberately adds no competing scoring entity. Mapping to Fatima's immutable `score.dto.ScoreResult` is explicit:

| Persisted column / Suleman entity property | Advice contract |
| --- | --- |
| `user_id` / `userId` | UUID user identifier |
| `month` | Strict `YYYY-MM` |
| `composite_score` / `compositeScore` | Decimal from 0 to 100, unchanged |
| `band` | `Strong`, `Stable`, `Needs Attention`, `At Risk`, unchanged |
| `component_scores` / `ComponentScoresDto` | Exactly `cashflow`, `profitability`, `repayment`, `trend`, `compliance`; numeric values or JSON null |
| `weakest_component` / `weakestComponent` | Canonical component key |
| `data_completeness` / `dataCompleteness` | Decimal from 0 to 1, unchanged; never multiplied by 100 |
| `computed_at` / `computedAt` | Persisted timestamp, unchanged |

The generators validate this contract; legacy `good`, `liquidity`, `leverage`, omitted component keys and percentage completeness values are not accepted as real scores. The read DTO defensively copies its map while preserving null values. The generators do not infer missing components or recompute health bands.

## Dashboard integration

Suleman's old dashboard reads a score before obtaining the advice locks. A concurrent rescore could then make that score disagree with the advice. The prepared dashboard handoff under `integration/` acquires `AdviceContextService.latest(userId)` at the beginning of the dashboard's existing write transaction, before reading the profile or score. It loads that exact score month and calls the exact-month advice overloads. The profile and score locks stay held until the complete response has been assembled.

Suleman's existing Features 4 and 5 already use his canonical persisted score when available. This update preserves that data contract and adds strict no-score behavior, bilingual generation, source fingerprints and locking; it removes the remaining mock fallback. When resolving integration conflicts, use the updated `InsightService` and `RecommendationService` together with `ScoreResultReader` and `AdviceContextService`. Retain Suleman's scoring entity, calculators and record hooks, and preserve his three profile fields and their create-profile mappings alongside the new profile repository lock and language endpoint. Do not restore older mock generators or replace his scoring engine with Fatima's read DTO.

The selected workflow is **refresh on read**. Do not add advice generation to the record-save hook. If a future caller explicitly invokes `generateAndSaveInsights` or `generateAndSaveRecommendations`, it must pass the current persisted DTO; arbitrary or stale snapshots are rejected. A JPA scoring save in the same transaction must be flushed before a JDBC read, and the caller should reload the persisted timestamp precision. Normal GET refresh avoids this additional coupling.

## Database installation and upgrade

Canonical migration order now matches Suleman's existing V1–V5 and adds V6:

1. V1 initial schema.
2. V2 compliance and repayment profile fields.
3. V3 nullable COGS.
4. V4 insights.
5. V5 recommendations.
6. V6 advice metadata and uniqueness on `(user_id, month, category)`.

Fresh databases apply all six. Databases already using Suleman's canonical history apply V6 normally. V6 retains the newest cached row per category when removing legacy duplicates; business profiles, monthly records and scores are preserved.

For Fatima's legacy V2-insights/V3-recommendations history, or the local V3-insights/V4-recommendations history:

1. Stop application instances and take a backup of the target database.
2. Inspect `flyway_schema_history` and set the connection's `search_path` to the application schema.
3. Execute `docs/reconcile-legacy-flyway.sql` with a PostgreSQL client configured to stop on errors. It checks exact known filenames/checksums and required tables, then transactionally maps only the advice history entries to V4/V5. Failed, baseline-only or unknown histories are rejected for manual investigation.
4. Start the backend once with `SPRING_FLYWAY_OUT_OF_ORDER=true` so missing canonical V2/V3 and V6 are applied. Remove this setting after the successful upgrade. Normal Flyway validation must then pass.

Do not run generic `flyway repair` to suppress conflicts. The reconciliation script is a deliberate one-time operator step, not an automatic production startup action. It rejects histories already containing V6. Clean old build output after switching migration layouts (`mvnw clean`) so removed SQL resources do not survive in `target/classes`.

## Reproducible verification

Run from `backend/` with Java 21 or newer:

```powershell
.\mvnw.cmd clean test
.\mvnw.cmd -Ppostgres-it verify
```

The PostgreSQL integration tests use isolated [embedded PostgreSQL](https://github.com/zonkyio/embedded-postgres) instances. They do not connect to the configured application database. Initial execution downloads test binaries; the bundled default engine is PostgreSQL 14.22. The application's Docker Compose database remains PostgreSQL 17.

`AdvicePostgresIT` verifies HTTP/profile integration, persisted-score refresh, identical repeated responses, parallel generation, prior-month corrections, historical Urdu output, gap handling, legacy mock rejection and saved-record Zakat. It also starts a separate connection inserting a missing previous score and verifies that it waits for the advice transaction to commit. Scores in this isolated Fatima-only suite are explicit database fixtures, since the scoring engine belongs to Suleman.

`MigrationPostgresIT` verifies fresh installation, both supported legacy upgrades, canonical V5 upgrade, preservation of business data, deterministic duplicate removal, unique constraints and rejection of unknown or failed migration histories.

`scripts/verify-suleman-integration.ps1` creates a disposable combined backend under `target/`, overlays Suleman's real scoring/record/cash-flow/dashboard/search modules and retained unit tests, applies the prepared shared integration edits and runs `CombinedWorkflowIT`. That suite exercises actual record save → scoring → bilingual advice → dashboard and verifies that Search exposes refreshed advice IDs and English/Urdu text. The dashboard service test handoff preserves upstream coverage and verifies locking before score reads. The source worktree and Git state are unchanged by the harness. `-BackendSourceRoot` can select an exported backend containing only the files staged for publication.

```powershell
.\scripts\verify-suleman-integration.ps1 -MavenRepository "$env:USERPROFILE/.m2/repository"
```

## Verified results

Final verification completed on 21 September 2026 against Suleman commit `d571629b0378ee5b7f00e35b50992023cf47dac2` and a clean export of the staged Phase 1 files. OCR source and tests were excluded from that export. This supersedes the earlier `6a3c576` verification and includes the native profile-lock fix plus Suleman's Search module.

| Suite | Passed | Failed / errored / skipped |
| --- | ---: | --- |
| Fatima Phase 1 backend unit and controller suite | 261 | 0 / 0 / 0 |
| PostgreSQL advice and migration integration suite | 12 | 0 / 0 / 0 |
| Suleman retained unit/controller tests, including Search, in combined build | 150 | 0 / 0 / 0 |
| Combined real-scoring HTTP workflow suite | 2 | 0 / 0 / 0 |

The staged export and its standalone reports are retained locally at `target/p1pub-7c8209a2/backend/`. The combined run is retained at `target/combined-integration-b2cf1316601f4920903f1f4a59135efd/`, with exact source hashes and the selected export in `integration-inputs.json`, and reports in its `target/surefire-reports/` and `target/failsafe-reports/`. These ignored build directories are local evidence; the committed harness reproduces the checks. These are separate suite counts; some retained tests overlap between the original and combined builds. No application database was modified. Publication is limited to the Phase 1 commit on `dev/fatima`; it does not merge or update `dev/suleman`.

## Remaining responsibilities on Suleman's side

The Phase 1 remaining-work document still assigns record/rescore failure handling, recalculation of affected later scores after older edits, and month-gap trend projection to Suleman. Advice correctly follows whatever persisted current and prior score snapshots his engine publishes; it cannot repair an outdated score calculation. Any future multi-month scoring transaction should acquire the same profile lock before score writes so lock order remains consistent with advice generation.

The dashboard document-review counter is a Phase 2 dependency. Existing OCR work is preserved outside this Phase 1 commit; camera/bulk upload and WhatsApp remain outside its scope. Suleman's Search is retained and exercised in the combined build. Search reads cached advice directly: an advice/dashboard read refreshes its requested month after a language or score change; Search itself does not regenerate untouched historical months.
