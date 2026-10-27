# F04 — Invoice and Bill Management: Phase A implementation contract (commercial document foundation)

* **Date:** 28 October 2026 (Asia/Karachi)
* **Slice:** F04 Phase A only — record-only customer INVOICE and supplier BILL documents
* **Lead / reviewer:** Developer B / Developer A (cross-domain financial review pending)
* **Base:** `dev/fatima` `34d9cd86b237cc35ec2fad2cc4ef69ec6de0f877` (F12B + F05 + F06 + F13 + F02 + pilot hardening)
* **Worktree / branch:** `C:/Users/Hp/FYP/wt-f04-commercial-documents` / `claude/f04-commercial-documents-phase-a` (local only; no push, no remote branch)
* **Migration:** `V31__commercial_documents_foundation.sql` only. V1–V19, V29 and V30 are byte-unchanged; no V20–V28 or V32+ are added here (V32 is used by the separate F09 worktree).
* **Formal status:** **PARTIAL_VERIFIED** for this bounded Phase A. Full F04 (issue/approve, C04 obligations, settlement) is **not** complete.

## 1. Financial boundary

Every document, list and preview response carries `financialEffect = "NONE"` and `settlementAvailability = "NOT_AVAILABLE_PHASE_A"`. A finalized invoice or bill is **not** evidence that money moved. F04 Phase A never creates, updates or infers FinancialTransaction/AccountMovement, receivables, payables, obligations, settlement allocations, cash, revenue/expense recognition, COGS, inventory, MonthlyRecord, `document_financial_contributions`, `score_results`, `health-score-v1`, project actuals, F05 planned/actual values or F06 persisted estimates/conversion. No `amountPaid`, `amountDue`, `outstandingBalance` or `paymentStatus` exists in the schema, API, UI or tests (other than assertions of their absence). `ModuleRegistry` `invoices` remains `future("invoices", "receivables")` and the F13 `invoices` widget remains unimplemented.

## 2. Lifecycle

`DRAFT -> FINALIZED -> VOID` only. `ISSUED` and `APPROVED` are reserved for future C04 integration and rejected by the status CHECK.

| Action | From | Result / guard |
|---|---|---|
| Create | — | Always `DRAFT` (database `BEFORE INSERT` rejects any other status), version 0, no number |
| Edit (PUT) | DRAFT | Full replacement of header + lines, version +1; otherwise `409 document_not_draft` |
| Delete | DRAFT | Removes the draft and its lines; FINALIZED/VOID `409 document_delete_forbidden` (also blocked by trigger) |
| Finalize | DRAFT | Requires counterparty, document date, ≥1 line and total > 0 (`422` with `counterparty_required`, `document_date_required`, `lines_required`, `total_must_be_positive`); assigns number; already finalized `409 document_already_finalized`; VOID `409 invalid_document_transition` |
| Void | FINALIZED | Requires reason (1–500 chars); keeps number and content; DRAFT `409 invalid_document_transition`; VOID `409 document_already_void` |

All mutations require `expectedVersion`; a mismatch returns `409 stale_document_version` and is never replayed automatically. The frontend keeps unsaved edits and offers an explicit reload.

## 3. Calculation (`CommercialDocumentCalculator`, the sole arithmetic)

* `quantity`: decimal string, > 0, ≤ 9 integer and ≤ 6 fractional digits (`NUMERIC(15,6)`).
* `unitPrice`: decimal string, ≥ 0, ≤ 12 integer and ≤ 2 fractional digits (`NUMERIC(14,2)`).
* `lineAmount = quantity × unitPrice` exactly. **No rounding policy**: if the exact product has more than 2 decimals the request is rejected with `lines[i].lineAmount = line_amount_not_exact` (e.g. `1.5 × 333.33 = 499.995`). Line amounts and totals are capped at `NUMERIC(18,2)` (`line_amount_out_of_range`, `total_out_of_range`).
* `subtotal = Σ lineAmount`; Phase A `total = subtotal` (no tax, discount or FX). Currency is fixed `PKR`.
* Null and zero stay distinct: a draft without lines has `subtotal = total = null`; explicit zero-priced lines yield `"0.00"`; finalization requires `total > 0`.
* Client totals are never accepted; the closed reader rejects `subtotal`, `total`, `lineAmount` and every other server-owned field.

Amounts serialize as exact 2-decimal strings; quantities as stripped plain decimals. Calculation version `f04-commercial-v1`.

## 4. Numbering

`INV-000001` / `BILL-000001`: six digits, one monotonic sequence per business + kind in `commercial_number_sequences`, allocated only inside a successful finalize transaction. Lock order is always document row (`FOR UPDATE`) then sequence row (`INSERT … ON CONFLICT DO NOTHING` + `SELECT … FOR UPDATE`), so a rollback also rolls back the allocation and no gap is created. The sequence trigger forbids decrease, reset, delete and truncate; finalized/void numbers are immutable and tenant-scoped unique `(business_id, kind, document_number)`. Exhaustion past 999999 returns `409 document_number_exhausted`.

## 5. Schema (`V31`)

* `commercial_documents`: UUID id, business FK, kind, status, nullable `document_number` (format CHECK per kind), `counterparty_name_ciphertext` (CHECK requires the `enc:v1:` AES-256-GCM envelope — plaintext cannot be stored), document/due dates (due ≥ document), notes, `subtotal`/`total NUMERIC(18,2)` (paired null; total = subtotal), `base_currency = 'PKR'`, `financial_effect = 'NONE'`, `calculation_version`, optimistic `version`, created/updated/finalized/voided actor UUIDs and timestamps, `void_reason`, `UNIQUE (id, business_id)`, lifecycle CHECK (DRAFT has no number/finalize/void fields; FINALIZED/VOID require number, counterparty, date, total > 0 and actors; VOID requires a non-blank reason).
* `commercial_document_lines`: UUID id, business, document, `line_number 1..100` unique per document, description, quantity > 0, optional unit, unit_price ≥ 0, `line_amount = quantity * unit_price` CHECK (also rejects silently rounded 3-decimal inserts), composite FK `(document_id, business_id) → commercial_documents(id, business_id) ON DELETE CASCADE`.
* `commercial_number_sequences`: PK `(business_id, kind)`, `next_value 1..1000000`.
* `enforce_commercial_document_immutability()` triggers: TRUNCATE forbidden on all three tables; create as DRAFT only; only DRAFT deletable; identity/kind/currency/creation provenance immutable and version strictly increasing; DRAFT→FINALIZED only when stored lines exist and sum exactly to the subtotal; FINALIZED content immutable except FINALIZED→VOID; VOID fully immutable; lines changeable only while both old and new parent are DRAFT; sequences monotonic and undeletable.
* Grants: `finsight_app` SELECT/INSERT/UPDATE/DELETE on documents and lines, SELECT/INSERT/UPDATE (no DELETE) on sequences, no TRUNCATE/REFERENCES/TRIGGER; `finsight_backup` SELECT only.
* No FK to counterparties, obligations, projects or estimates (none exist on this branch). Future linkage is additive.

## 6. Counterparty protection

Free-text counterparty name (1–160 code points, no control characters) encrypted with the existing `SensitiveDataCipher` (AES-256-GCM, AAD `FinSight|CommercialDocument|counterpartyName`). It is decrypted only for authorized responses, excluded from audit metadata and **not searchable**. No competing counterparty master or fake counterparty ID is created. Known limitation: `CryptoMigrationService` key rotation does not yet include this column (finalized rows are immutable by design), so retired keys must stay in the keyring until a reviewed rotation path exists.

## 7. API (`/api`, closed-schema JSON, 64 KB cap, `Cache-Control: no-store`)

| Endpoint (same shape under `/api/bills`) | Permission |
|---|---|
| `GET /api/invoices[?status=DRAFT\|FINALIZED\|VOID]` (≤200, newest first, items have `lineCount` and `lines: null`) | `FINANCIAL_DATA_READ` |
| `GET /api/invoices/{id}` | `FINANCIAL_DATA_READ` |
| `POST /api/invoices/preview` `{lines}` (stateless; CSRF required) | `FINANCIAL_DATA_READ` |
| `POST /api/invoices` `{counterpartyName?, documentDate?, dueDate?, notes?, lines[]}` → 201 | `RECORD_CREATE_UPDATE` |
| `PUT /api/invoices/{id}` (same + `expectedVersion`) | `RECORD_CREATE_UPDATE` |
| `DELETE /api/invoices/{id}?expectedVersion={v}` → 204 | `RECORD_CREATE_UPDATE` |
| `POST /api/invoices/{id}/finalize` `{expectedVersion}` | `RECORD_CREATE_UPDATE` |
| `POST /api/invoices/{id}/void` `{expectedVersion, reason}` | `RECORD_CREATE_UPDATE` |

Line object: `{description, quantity, unit?, unitPrice}`. Validation errors: `400 commercial_document_invalid` with `errors{field: code}` (unknown/authority fields → `unknown_field`). OWNER/ACCOUNTANT may mutate; MANAGER/VIEWER are read/preview only. Business and actor always come from the authenticated active-business session; cross-business or cross-kind IDs return `404 document_not_found`. Audit events `COMMERCIAL_DOCUMENT_{CREATED,UPDATED,DELETED,FINALIZED,VOIDED}` record kind/status/version/number/total only.

## 8. Frontend

Routes `/invoices`, `/invoices/new`, `/invoices/[id]`, `/bills`, `/bills/new`, `/bills/[id]` share `components/commercial/{CommercialDocumentList,CommercialDocumentWorkspace,CommercialLineEditor}.tsx`, `lib/api/commercial.ts`, `lib/commercial/form.ts` and `lib/i18n/translations/commercial.ts`. States: list with status filter, empty, loading, error/retry, record-only and settlement-unavailable banners, draft create/edit with a responsive line editor (table-like grid on desktop, cards on mobile), debounced backend preview with stale-request abort, per-line server errors (including not-exact), finalize confirmation stating it records and freezes the document only and does NOT post money, create a receivable/payable, record a payment or alter financial health calculations, void dialog with required reason, finalized/void read-only views, MANAGER/VIEWER read-only and create-forbidden states, 403/404 states, 409 conflict banner with preserved edits and explicit reload, active-business switching that never shows another business's rows. EN/UR with RTL; amounts, dates and numbers are LTR. Sidebar and mobile action sheet add Invoices and Bills while keeping Projects, Reports, Estimates, Ask FinSight and record/document navigation; `/invoices/new` and `/bills/new` require `RECORD_CREATE_UPDATE`.

## 9. Not implemented (explicitly out of Phase A)

Receivable/payable or C04 obligation creation, settlement and payment tracking, outstanding balances, ISSUED/APPROVED states, tax, discounts, FX, project-actual linkage (C05), inventory/COGS (C07), F06 estimate→invoice conversion, F12B invoice/bill register reporting, PDF/email delivery, attachments/evidence links, OCR auto-posting, credit notes, counterparty master/search and key-rotation coverage for counterparty ciphertext.

## 10. Verification and reproduction

Tests: `commercial/CommercialDocumentCalculatorTests`, `CommercialDocumentRequestReaderTests`, `CommercialDocumentLifecycleTests`, `CommercialDocumentMigrationPostgreSqlIT`, `CommercialDocumentPostgreSqlIT`, `frontend/tests/commercial.test.mjs`, and the opt-in real browser harness `testsupport/F04CommercialBrowserAcceptance` + `scripts/test_f04_commercial_browser.mjs` (22 checks). Measured results are in [implementation state](IMPLEMENTATION_STATE.md) and [dated evidence](verification/2026-10-28-f04-phase-a.json).

Browser reproduction: build the frontend with `BACKEND_API_URL=http://127.0.0.1:8080` and `NEXT_PUBLIC_DATA_MODE=live` (`node node_modules/next/dist/bin/next build --webpack`), start it on `127.0.0.1:3100`, keep 8080 free, set `FINSIGHT_BROWSER_NODE`, `PLAYWRIGHT_MODULE`, `FINSIGHT_BROWSER_BASE_URL` and a disposable `BROWSER_ARTIFACT_DIR`, then from `backend` run `mvn -o -B -Dtest=F04CommercialBrowserAcceptance test`. It starts real Spring on loopback 8080 with `DisposablePostgres`, synthetic accounts and AI disabled; it compares twelve complete financial/score/project/configuration tables before and after, installs statement-level write probes on records, scores, contributions and project tables, and verifies the persisted INV/BILL facts and ciphertext-only storage.
