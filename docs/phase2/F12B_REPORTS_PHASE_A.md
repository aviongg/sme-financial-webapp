# F12B Phase A — Read-only business financial summary, CSV export and print view

Implemented scope: stateless, read-only business financial summary report, UTF-8 CSV export and scoped print/PDF layout, with an authenticated English/Urdu/RTL screen at `/reports`. Developed on isolated branch `codex/f12b-reports-phase-a` in worktree `C:/Users/Hp/FYP/wt-f12b-reports`, based on `dev/fatima` commit `ea8e812f1d891c07362883a4374754e48485c884`.

This is a **bounded F12B Phase A** slice. Full F12B is **not** complete (`PARTIAL_VERIFIED` for this bounded Phase A capability; full F12B remains blocked on future domain producers and persistence/scheduling contracts).

## Implemented in F12B Phase A

- **Read-only financial summary & period report**: On-demand canonical report dataset (`schemaVersion: business-financial-summary-v1`) over an inclusive `fromMonth..toMonth` window (1 to 24 months) from existing persisted `MonthlyRecord` and `ScoreResult` stores.
- **CSV export**: Deterministic UTF-8 (with BOM) CSV export rendered by the backend from the exact same `BusinessFinancialReport` object, with English/Urdu localized labels, stable machine keys, explicit `RECORDED` / `PARTIAL` / `COMPLETE` / `NOT_RECORDED` statuses and spreadsheet formula injection neutralization.
- **Printable frontend**: `/reports` route with scoped `@media print` rules (`body:has([data-report-print])`) that hide navigation, controls and status banners while keeping the report title, business name, period, generated timestamp, flow/snapshot basis disclosures and unclipped financial tables in both English LTR and Urdu RTL.
- **Bilingual EN/UR/RTL UX**: Complete English and Urdu translations (`frontend/src/lib/i18n/translations/reports.ts`), RTL layout, desktop tables and mobile per-month cards (`390px` viewport without horizontal overflow), keyboard navigation and focus management, no-data/partial-data/error/permission states, and immediate state invalidation on period edit, business switch or session expiry.
- **Current canonical sources**: Reads only `Business`, `BusinessProfile`, `MonthlyRecordRepository.findByUserIdAndMonthBetweenOrderByMonthAsc` and `ScoreResultRepository.findFirstByUserIdAndMonthBetweenOrderByMonthDesc`.

## Not implemented (remaining F12B scope)

- Financial operations calendar (`FinancialCalendarService`, due dates from obligations, project milestones or recurrence)
- Document-to-entity links (`document_entity_links` and reviewed commercial entity attachments)
- Persisted report runs (`report_runs`)
- Saved report schedules and background workers (`report_schedules`, `ReportRunWorker`)
- Retained report artifacts and authorized binary vault downloads
- Automated outbound report distribution / delivery
- Future domain sources (F01 ledger transactions, F03 AR/AP ageing, F04 invoices/bills, F05 project actuals/margins, F08 inventory movements/COGS, F09 sample costs, F11 multi-currency/FX, C11 `FinancialQueryFacade`)
- Production Java 21 / PostgreSQL 17 / TLS / deployment acceptance and reciprocal Developer A review

## Module and permission boundary

- **Permission**: Uses the existing `BusinessPermission.FINANCIAL_DATA_READ` check via `BusinessAuthorizationService.requirePermission(request, BusinessPermission.FINANCIAL_DATA_READ)`. Every current active role (`OWNER`, `ACCOUNTANT`, `MANAGER`, `VIEWER`) holds `FINANCIAL_DATA_READ`. Suspended, removed or unauthenticated memberships are rejected before report assembly.
- **F02 ModuleRegistry boundary**: The `reports` entry in `ModuleRegistry` is **not** marked implemented (`implemented: false` remains unchanged), and `routeModule('/reports')` returns `null`. Phase A is a core-finance reporting capability over mandatory `core_finance` records; marking the full `reports` module implemented is deferred until calendar, entity links, persisted runs and schedules exist.
- **Tenant isolation**: The active business is resolved exclusively from the authenticated session context (`BusinessAccessContext.businessId()`). Request bodies, query parameters and headers cannot select a `businessId`, `userId`, `actorUserId`, SQL filter or metric expression.

## API contract

Both endpoints are stateless POST reads under `/api/reports` using the existing session cookie and `X-XSRF-TOKEN` CSRF protection:

1. `POST /api/reports/business-financial-summary` (`Accept: application/json`) -> `200 OK` JSON `BusinessFinancialReport` with `Cache-Control: no-store`.
2. `POST /api/reports/business-financial-summary.csv` (`Accept: text/csv, application/json`) -> `200 OK` `text/csv; charset=UTF-8` (UTF-8 BOM + CRLF rows) with `Content-Disposition: attachment; filename="finsight-business-financial-summary_<fromMonth>_<toMonth>.csv"`, `Cache-Control: no-store` and `X-Content-Type-Options: nosniff`.

### Request schema

Closed JSON object (`MAX_REQUEST_BYTES = 2048`, streaming-enforced via `readNBytes(2049)` even when `Content-Length` is omitted or inaccurate; strict duplicate-key and trailing-token rejection):

```json
{
  "fromMonth": "2026-01",
  "toMonth": "2026-03",
  "language": "en"
}
```

- `fromMonth`, `toMonth`: Required strings matching `^\d{4}-(?:0[1-9]|1[0-2])$`, with `fromMonth <= toMonth` and inclusive month span `<= 24` (`MAX_MONTHS = 24`).
- `language`: Optional string, `"en"` (default when omitted/null) or `"ur"`.
- Unknown fields (`businessId`, `userId`, `actorUserId`, `tenant`, `sql`, `metrics`, `currency`, etc.) are rejected with HTTP `400` (`error: "report_invalid"`, `errors: { "request": "unknown_field" }`).
- Oversized requests (> 2,048 bytes) return HTTP `413` (`error: "report_request_too_large"`).

### Canonical dataset (`BusinessFinancialReport`)

All monetary amounts are serialized as exact decimal strings (minimum scale 2, never binary floats or scientific notation) or `null` when not recorded:

- `schemaVersion`: `"business-financial-summary-v1"`
- `business`: `{ name, businessType }` (no internal UUIDs)
- `period`: `{ fromMonth, toMonth, monthCount }`
- `currency`: `"PKR"`, `currencyBasis`: `"APPLICATION_FIXED_PKR"`
- `language`: `"en"` | `"ur"`
- `generatedAt`: UTC ISO-8601 instant truncated to seconds
- `monthlyRows[]`: Ordered ascending by `month`, containing `revenue`, `cogs`, `operatingExpenses`, `interestExpense`, `cashInflow`, `cashOutflow`, `netCashMovement` (`cashInflow - cashOutflow` when both are non-null), `cashBalanceEom`, `receivablesOutstanding`, `payablesOutstanding`, `inventoryValue`, `loanOutstanding`, `financingType`.
- `periodFlows`: `basis: "SUM_OF_RECORDED_MONTHS"`, `monthsIncluded`, and `Flow(total, recordedMonths, includedMonths, status)` for `revenue`, `cogs`, `operatingExpenses`, `interestExpense`, `cashInflow`, `cashOutflow`, `netCashMovement`.
- `latestBalances`: `basis: "LATEST_RECORD_IN_PERIOD_NOT_SUMMED"`, `asOfMonth`, `cashBalanceEom`, `receivablesOutstanding`, `payablesOutstanding`, `inventoryValue`, `loanOutstanding`.
- `latestHealth`: `basis: "LATEST_SCORE_IN_PERIOD_NOT_AVERAGED"`, `month`, `compositeScore`, `band`, ordered `components[]` (`cashflow`, `profitability`, `repayment`, `trend`, `compliance`), `weakestComponent`, `dataCompleteness`, `methodologyVersion` (`health-score-v1`), `computedAt` — or `null` if no score exists in the period.
- `completeness`: `{ requestedMonths, recordedMonths, missingMonths[], complete, optionalFields[] }`.
- `limitations[]`: Deterministic machine codes (`NO_RECORDS_IN_PERIOD`, `MONTHS_MISSING`, `COGS_PARTIAL`, `COGS_NOT_RECORDED`, `INTEREST_EXPENSE_PARTIAL`, `INTEREST_EXPENSE_NOT_RECORDED`, `BALANCE_NOT_RECORDED_IN_LATEST_MONTH`, `NO_HEALTH_SCORE_IN_PERIOD`, `HEALTH_SCORE_BEFORE_LATEST_RECORD`).

## Financial semantics and invariants

1. **Flow semantics**: Flow figures (`revenue`, `cogs`, `operatingExpenses`, `interestExpense`, `cashInflow`, `cashOutflow`, `netCashMovement`) are summed across recorded months in the selected period using exact `BigDecimal` addition. Each flow carries `recordedMonths`, `includedMonths` and `status` (`COMPLETE`, `PARTIAL`, `NOT_RECORDED`). Partial or unrecorded `cogs` and `interestExpense` emit explicit limitation codes (`COGS_PARTIAL`, `COGS_NOT_RECORDED`, `INTEREST_EXPENSE_PARTIAL`, `INTEREST_EXPENSE_NOT_RECORDED`) and status labels so partial coverage is never mistaken for a complete period total.
2. **Snapshot balance semantics**: End-of-month balances (`cashBalanceEom`, `receivablesOutstanding`, `payablesOutstanding`, `inventoryValue`, `loanOutstanding`) are taken exclusively from the latest recorded month in the selected period (`asOfMonth`). They are **never summed** across months and **never back-filled** from earlier months when null in the latest month.
3. **Null / unknown vs zero**: `null` always means "not recorded" and is never coerced to `0.00`. Recorded zero is preserved as `"0.00"` (`PKR 0.00` / `0.00 روپے` in UI, `0.00` with `RECORDED`/`COMPLETE` status in CSV), while `null` renders as `Not recorded` / `درج نہیں` in UI and an empty value cell with `NOT_RECORDED` status in CSV.
4. **Health-score semantics**: Uses the latest persisted `ScoreResult` within `fromMonth..toMonth`. Scores are never averaged across months and never recalculated by report generation. `health-score-v1`, `ScoringService` and `CashFlowService` are untouched.
5. **CSV injection defense**: Any non-decimal cell (or any text starting with `=`, `+`, `-`, `@`, `\t`, or `\r`) is prefixed with an apostrophe (`'`) before RFC 4180 quoting, while validated negative backend decimal amounts in the `value` column remain numeric (e.g., `-50.00`, `-180.00`).
6. **Stateless / zero side effects**: Report preview and CSV export run inside `@Transactional(readOnly = true)` and do not mutate `monthly_records`, `score_results`, `document_financial_contributions`, `dashboard_preferences`, `business_configuration`, `business_profiles`, `insights`, `recommendations`, `uploaded_documents` or `whatsapp_deliveries`. No Flyway migration is added; V1–V19 and V29 remain unchanged.

## Verification summary (25 October 2026)

| Gate | Result |
|---|---|
| Targeted backend unit tests (`BusinessFinancialReportAssemblerTests`, `BusinessFinancialReportCsvTests`, `ReportRequestReaderTests`) | **25 passed** (14 + 8 + 3), 0 failures, 0 errors, 0 skips |
| Targeted PostgreSQL integration test (`BusinessFinancialReportPostgreSqlIT`) | **9 passed**, 0 failures, 0 errors, 0 skips |
| Full backend default + local PostgreSQL Failsafe verify | **998 default cases (997 passed, 1 existing Windows symlink skip); 163 local PostgreSQL integration cases passed**, 0 failures, 0 errors (19 production-target cases explicitly excluded: `DatabaseRolePrivilegesPostgreSqlIT`, `PostgreSqlTlsConnectionIT`) |
| Frontend unit tests (`node --test tests/reports.test.mjs` and `tests/*.test.mjs`) | **13 F12B report tests passed; 99 total frontend unit tests passed**, 0 failures, 0 skips |
| TypeScript & ESLint | **PASS / PASS** (0 errors, 1 pre-existing warning in `SessionProvider.tsx`) |
| Production Next webpack build (`next build --webpack`) | **PASS** (`/reports` route compiled cleanly; existing middleware deprecation warning only) |
| Real-browser acceptance (`F12bReportsBrowserAcceptance` / `scripts/test_f12b_reports_browser.mjs`) | **15 checks passed** on final source with production Next (`127.0.0.1:3100`), real Spring (`127.0.0.1:8080`) and disposable PostgreSQL 14.22; 4 screenshots (`report-desktop-en.png`, `report-mobile-en.png`, `report-mobile-ur.png`, `report-desktop-ur.png`) visually inspected; 0 runtime/CSP/hydration errors, 0 external requests |
| Real-browser regressions (`PilotHardeningBrowserAcceptance`, `F13BrowserAcceptance`, `F06BrowserAcceptance`, `F02AiBrowserAcceptance`) | **11 + 16 + 14 + 11 = 52 checks passed**, 0 failures |
