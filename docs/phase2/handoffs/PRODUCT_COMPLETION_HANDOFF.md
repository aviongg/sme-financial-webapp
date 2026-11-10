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

Fresh DB: `ProductCompletionMigrationPostgreSqlIT` asserts contiguous V1–V51, every new table, runtime/backup grants.
Populated upgrade: same IT migrates a populated V44 schema to V51; every pre-existing row unchanged; triggers active.

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
| OCR / document flow | CODE_COMPLETE | Only confirmed scans link as evidence; no automatic posting |

## Proof against double counting (all covered by `FinancialChainPostgreSqlIT` and the browser suite)
* Collections and bill settlements never enter revenue/cost (cash timing only).
* Stock purchases are capitalized (bill receipts or bounded ledger payments) and reach P&L only as COGS/consumption.
* A ledger payment's attribution budget is shared by C05 projects, F09 samples and F08 capitalization (one lock).
* Commission payouts/advances are replaced in P&L by recognized commission.
* Project revenue excludes collection attributions; project cost excludes settlement and capitalized-payment attributions.
* Credit/debit notes reduce outstanding via non-cash allocations; commissions consider CASH allocations only.

## Verification (local, this branch HEAD)
* Backend unit: 1802 run, 0 failures, 0 errors, 1 skipped.
* PostgreSQL ITs (embedded PostgreSQL): 456 run, 0 failures, 0 errors. Excluded (production fixtures required):
  `DatabaseRolePrivilegesPostgreSqlIT`, `PostgreSqlTlsConnectionIT`.
* Baseline before this pass: unit 1802-equivalent with 1 failure; ITs 434 with 11 failures + 2 errors (all repaired:
  stale V37 history assertions, SALESPERSON role loops, shared-DB isolation, facade rollback-only, draft leakage).
* Frontend: 435/435 unit/contract tests, TypeScript 0 errors, ESLint 0 errors (51 pre-existing warnings), production build OK.
* Real browser (`tests/product-completion-browser.integration.mjs`, system Edge via Playwright, real Spring Boot +
  PostgreSQL + Next production build): 16/16 checks — 7 product pages × EN/UR × 1280/1024/390×844/390×600 (RTL,
  no horizontal overflow, no page errors) plus credit note, invoice fulfilment/WAC/COGS, keyboard stock receipt,
  accrual P&L, break-even, keyboard Copilot grounded answer, financial pack CSV, action center.
* Security/tenant: Copilot security corpus (12), WhatsApp inbound IT (signature, replay, dedupe, consent, live
  membership re-check, encryption), cross-business 404 and role 403 checks in every new IT.

## External dependencies only
* OpenAI: `AI_ENABLED`, `AI_PROVIDER=openai`, `AI_LIVE_ACTIVATION_APPROVED`, `OPENAI_API_KEY`, allowed business IDs.
* Meta WhatsApp: `WHATSAPP_INBOUND_ENABLED`, `whatsapp_webhook_verify_token`, `whatsapp_app_secret`,
  `whatsapp_phone_hash_key` (≥32 chars), phone-number ID, access token, webhook registration and number approval.
* Production gates unchanged: container builds, PostgreSQL 17 TLS/HBA, restore drills, UAT.

## Developer A final reconciliation work
1. Review/accept items A1–A4b in the contracts document (no ledger/posting redesign involved).
2. Decide A5 (whether the monthly bridge/health score should consume accrual figures) — methodology decision.
3. Rebase/merge any newer `dev/suleman` work onto this branch; migrations must remain contiguous after V51.
4. Run the combined regression (commands above) and the browser suite after `npm run build`.

## Landing page
Not touched. After product reconciliation, cherry-pick `a67eefa` then `0c13c3e` from the landing worktree onto the
integration branch, re-run `npm run build` and the browser suites, then perform the root-route cutover.
