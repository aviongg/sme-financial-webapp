# FinSight product completion — handoff to Developer A

* Base: `handoff/finsight-unified-baseline-20261110` @ `8c6e2478fce100fb9bd17c469e288248f5fffdb1` (verified clean, V44).
* Branch: `claude/finsight-product-complete` (local only, never pushed). `git log 8c6e247..HEAD` lists every commit.
* Policies: [FINANCIAL_POLICIES_PRODUCT_COMPLETION.md](../FINANCIAL_POLICIES_PRODUCT_COMPLETION.md).
* Developer-A review items: [PRODUCT_COMPLETION_DEV_A_CONTRACTS.md](PRODUCT_COMPLETION_DEV_A_CONTRACTS.md).

## Migrations after V44 (contiguous, V1–V44 untouched)

| Version | Purpose |
|---|---|
| V45 | F04 credit/debit notes (`commercial_adjustments`, numbering), invoice/bill line ↔ inventory item, additive `CREDIT_NOTE` allocation type |
| V46 | F08 append-only `inventory_movements` + trigger-guarded `inventory_positions` (policy `f08-wac-v1`) |
| V47 | F09 `sample_actual_costs`, `sample_recovery_links` (append-only, compensating corrections) |
| V48 | Classifications + category cost behaviour, business budgets with frozen approved revisions, financial goals |
| V49 | F06 estimate → quotation (draft invoice) lineage |
| V50 | WhatsApp inbound: possession-verified links, conversations, encrypted message log with provider-id dedupe |
| V51 | F04 ↔ reviewed OCR evidence links (provenance only) |
| V52 | Transaction-driven monthly model: `monthly_records` projection metadata (`source` LEGACY_MANUAL/DERIVED, version, fingerprint, period completeness), `monthly_financing_inputs` (supplemental manual financing, legacy values migrated), `monthly_projection_state` + dirty triggers on every authoritative source table |

Fresh DB: `ProductCompletionMigrationPostgreSqlIT` asserts contiguous V1–V52, every new table, runtime/backup grants.
Populated upgrade: same IT migrates a populated V44 schema to V52; every pre-existing row value unchanged (legacy months
labelled LEGACY_MANUAL, financing facts copied), 12 dirty triggers present, a source write bumps the dirty sequence.

## Transaction-driven monthly model (`monthly-projection-v1`)
**Users operate FinSight through ordinary daily financial workflows. Monthly financial records and health-score inputs
are automatically derived from authoritative recognized activity. Users do not separately enter duplicate monthly
financial totals solely for health scoring.** Field source map, accrual/cash
semantics, period attribution, late-entry/backdated handling, recomputation, score recomputation and null/zero rules:
[policies section 10](../FINANCIAL_POLICIES_PRODUCT_COMPLETION.md#10-monthly-projection-v1--transaction-driven-monthly-model-v52).
* One projector (`records/derived/MonthlyFinancialProjector`) + one materialiser (`MonthlyProjectionService`).
* Reads are cheap and coalesced: a current projection is served after one indexed lookup (`FRESH`, no lock); concurrent
  stale reads of one business queue on a per-business advisory lock so exactly one rebuilds and the rest return
  `COALESCED`; other businesses never wait; a failed rebuild rolls back, stays dirty and is retried (policies §10).
* Legacy upgrade: pre-V52 months before ledger coverage stay byte-identical as `LEGACY_MANUAL`; months inside coverage
  are replaced by `DERIVED` months; migrated financing facts flow in (`MonthlyIntegrityPostgreSqlIT`).
* API: `GET /api/records/monthly` (derived months), `GET /api/records/monthly/{month}/sources`,
  `GET /api/records/monthly/projection-status`, `POST /api/records/monthly/recalculate`,
  `GET|PUT /api/records/monthly/financing[/{month}]`. `POST /api/records/monthly` → `409 monthly_figures_derived`.
* UI: `/records/new` explains the automatic model, shows projection status and recalculation, and holds the supplemental
  financing form; `/records/{month}` is a read-only derived month with its source map; `/records/bridge` shows the
  same automation view; OCR review copy states confirmation is evidence only.

## Feature matrix

| Feature | Status | Notes |
|---|---|---|
| F01 Transactions | CODE_COMPLETE | Baseline; P&L/goals/intelligence read it; capitalization guard |
| F02 Configuration | CODE_COMPLETE | All implemented modules live; enabling still dependency-checked |
| F03 AR/AP | CODE_COMPLETE | Baseline + non-cash note allocations; aging/forecast unchanged |
| F04 Invoices/bills | CODE_COMPLETE | Issue → recognize → settle → notes → compensating void; stock actions; OCR evidence |
| F05 Projects | CODE_COMPLETE | Actual profitability vs budget and estimate; completeness explicit |
| F06 Estimates | CODE_COMPLETE | New-project conversion, quotation draft, estimate-vs-actual (FX quoting retired) |
| F07 Funding | CODE_COMPLETE | Baseline (Dev A); dashboard widget live |
| F08 Inventory/COGS | CODE_COMPLETE | Perpetual WAC ledger, COGS, project/sample consumption (BOM/WIP retired) |
| F09 Sampling | CODE_COMPLETE | Actual cost, recovery, FREE/PAID/PARTIAL, contribution |
| F10 Commissions | CODE_COMPLETE | Baseline; recognized cost feeds P&L and projects |
| F11 FX | CODE_COMPLETE | Baseline; realized FX in P&L and facade |
| F12A Operations | CODE_COMPLETE | Baseline; monthly draft-summary leakage fixed |
| F12B Reporting | CODE_COMPLETE | Calendar, relationships, financial pack + per-section CSV + print (schedules retired) |
| F13 Dashboard | CODE_COMPLETE | Widgets for every live source, role/module gated |
| Budgets / goals / break-even | CODE_COMPLETE | Break-even never guesses cost behaviour |
| Financial intelligence | CODE_COMPLETE | Recognized-revenue intelligence + unified action center |
| AI Copilot | EXTERNAL_ACCEPTANCE_ONLY | Code complete (deterministic + OpenAI adapter); real-provider acceptance needs credentials |
| WhatsApp inbound | EXTERNAL_ACCEPTANCE_ONLY | Code complete with mock provider; Meta number/app approval pending |
| OCR / document flow | CODE_COMPLETE | Only confirmed scans link as evidence; confirmation never changes a month |
| Monthly records / health score inputs | CODE_COMPLETE | Derived from daily activity (`monthly-projection-v1`); manual monthly totals retired |

## Proof against double counting (all covered by `FinancialChainPostgreSqlIT` and the browser suite)
* Collections and bill settlements never enter revenue/cost (cash timing only).
* Stock purchases are capitalized (bill receipts or bounded ledger payments) and reach P&L only as COGS/consumption.
* A ledger payment's attribution budget is shared by C05 projects, F09 samples and F08 capitalization (one lock).
* Commission payouts/advances are replaced in P&L by recognized commission.
* Project revenue excludes collection attributions; project cost excludes settlement and capitalized-payment attributions.
* Credit/debit notes reduce outstanding via non-cash allocations; commissions consider CASH allocations only.

## Verification (local, final source of the transaction-driven monthly model, 11 Nov 2026)
Run memory-safe: one JVM at a time (surefire 2 GB heap; failsafe 1.5 GB heap, serial GC, bounded Spring context cache),
PostgreSQL ITs in six sequential package batches, no browser/Next process during backend runs.
* Backend unit (`./mvnw test`): **1784 run, 0 failures, 0 errors, 1 skipped** (platform-conditional skip in
  `LocalFileSystemStorageServiceTests`). The earlier 1802 figure fell by exactly 18 retired manual-monthly tests
  (`MonthlyRecordContributionTests` 4, `MonthlyRecordServiceTests` 12, `MonthlyRecordIntegrationTests` 2).
* PostgreSQL ITs (`./mvnw -Ppostgres-it verify`, embedded PostgreSQL, all 80 accepted classes):
  **447 run, 0 failures, 0 errors, 0 skipped** — core/accounting/monthly 117, obligations/F04/commission/FX 83,
  inventory/projects/estimates/samples 70, reporting/intelligence/dashboard/config 59, AI/WhatsApp/OCR/identity 51,
  migrations/security/audit 67. Excluded (production fixtures required): `DatabaseRolePrivilegesPostgreSqlIT`,
  `PostgreSqlTlsConnectionIT`. The earlier 456 figure: `MonthlyIntegrityPostgreSqlIT` retired 21 manual-write/OCR-to-month
  invocations and gained 11 projection tests; `TransactionDrivenMonthlyPostgreSqlIT` added 1 (18-scenario) test.
* Frontend: **436/436** unit/contract tests, TypeScript 0 errors, ESLint 0 errors (50 pre-existing warnings, none in
  changed files), production webpack build OK.
* Real browser (`tests/product-completion-browser.integration.mjs`, system Edge via Playwright 1.62, real Spring Boot +
  disposable PostgreSQL + Next production build): **20/20 checks** — 9 product pages (incl. `/records/new`,
  `/records/{month}`) × EN/UR × 1280/1024/390×844/390×600 (RTL, no horizontal overflow, no page errors, no console /
  hydration / CSP errors), no rebuild storm across 72 page loads, credit note, invoice fulfilment/WAC/COGS, keyboard
  stock receipt, accrual P&L, break-even, derived month = P&L, late/backdated invoice updates last month and its score
  (dashboard + score history agree), idempotent recalculation, keyboard supplemental financing, manual totals 409,
  keyboard Copilot grounded answer, financial pack CSV, action center.
* Known cosmetic gap (pre-existing, not monthly-related): the app ships no favicon, so browsers log a `/favicon.ico`
  404; the browser suite exempts only that probe.
* Security/tenant: Copilot security corpus (12), WhatsApp inbound IT (signature, replay, dedupe, consent, live
  membership re-check, encryption), cross-business 404 and role 403 checks in every new IT.

## External dependencies only
* OpenAI: `AI_ENABLED`, `AI_PROVIDER=openai`, `AI_LIVE_ACTIVATION_APPROVED`, `OPENAI_API_KEY`, allowed business IDs.
* Meta WhatsApp: `WHATSAPP_INBOUND_ENABLED`, `whatsapp_webhook_verify_token`, `whatsapp_app_secret`,
  `whatsapp_phone_hash_key` (≥32 chars), phone-number ID, access token, webhook registration and number approval.
* Production gates unchanged: container builds, PostgreSQL 17 TLS/HBA, restore drills, UAT.

## Developer A final reconciliation work
1. Review/accept items A1–A4b in the contracts document (no ledger/posting redesign involved).
2. Review A5 (transaction-driven monthly model, implemented): the V52 triggers on Dev A tables, the bridge-apply
   delegation, reversal-as-restatement for month-end cash, PKR-only balances, supplemental financing inputs.
3. Rebase/merge any newer `dev/suleman` work onto this branch; migrations must remain contiguous after V52. Any new
   authoritative source table must get a `trg_<table>_monthly_projection_dirty` trigger and a projector source mapping.
4. Run the combined regression (commands above) and the browser suite after `npm run build`.

## Landing page
Not touched. After product reconciliation, cherry-pick `a67eefa` then `0c13c3e` from the landing worktree onto the
integration branch, re-run `npm run build` and the browser suites, then perform the root-route cutover.
