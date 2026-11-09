# C05 Implementation Progress: Project Actual Financial Attribution & Commitments

**Task IDs:**
- `DEV-A-C05A-PROJECT-ACTUALS` (C05A Baseline)
- `DEV-A-C05B-PROJECT-COMMITMENTS` (C05B Commitments & Commercial Linkage)
- `DEV-A-C05C-OCR-PROJECT-ATTRIBUTION` (C05C OCR Expense Review & Project Evidence)

**Engineer:** Google Antigravity (Sole Full-Stack Developer A Engineer)
**Branch:** `dev/suleman`
**Committed Baseline:** `cca8de3ce6f1f6f7dbfefdb506f211495707fd14`

---

## 1. Feature Checklist

### C05A — Project Actual Financial Attribution
- [x] **Integration Contract:** `docs/integration/dev-a/c05/C05_PROJECT_ACTUAL_ATTRIBUTION_CONTRACT.md` authored.
- [x] **Database Migration (V42):** Additive migration `V42__project_actual_financial_attribution.sql` with split-allocation trigger (`chk_source_attribution_total`) and least-privilege role grants (`finsight_app`, `finsight_backup`).
- [x] **Domain Entity & Repository:** `ProjectFinancialAttribution`, `ProjectFinancialAttributionRepository`.
- [x] **Attribution Service:** `ProjectFinancialAttributionService` supporting eligible source discovery, split allocations, concurrency protection, idempotency, and reversal tracking.
- [x] **Costing Reporting Service:** `ProjectFinancialCostingService` computing planned vs actual cash comparison, headroom, commission evidence, FX evidence, and data completeness indicators.
- [x] **REST API Controller:** `ProjectFinancialAttributionController` endpoints under `/api/finance/projects/{projectId}/...`.
- [x] **Frontend Client & Components:**
  - `frontend/src/lib/api/projectActuals.ts`
  - `frontend/src/components/finance/projects/ProjectActualsWorkspace.tsx`
  - `frontend/src/components/finance/projects/ProjectCostingOverviewCard.tsx`
  - `frontend/src/components/finance/projects/BudgetVsActualCard.tsx`
  - `frontend/src/components/finance/projects/AttributionCreateDialog.tsx`
  - `frontend/src/components/finance/projects/AttributionsTable.tsx`
  - `frontend/src/app/finance/projects/[id]/actuals/page.tsx`
- [x] **Bilingual Localization:** English/Urdu translations and RTL styles in `frontend/src/lib/i18n/translations/projectActuals.ts`.
- [x] **PostgreSQL Integration Tests:** `ProjectAttributionPostgreSqlIT` covering Fixtures A through E, split allocations, over-allocation prevention, concurrent races, reversals, and RBAC (7/7 passed).
- [x] **Real Full-Stack Browser Journeys:** 18 HTTP journeys running against Next.js, Spring Boot, and PostgreSQL (18/18 passed).

### C05B — Project Financial Commitments & Commercial Document Linkage
- [x] **Integration Contract & Handoff:**
  - Updated `docs/integration/dev-a/c05/C05_PROJECT_ACTUAL_ATTRIBUTION_CONTRACT.md` (Section 6 & 7).
  - Authored `docs/integration/dev-a/c05/C05_COMMERCIAL_COMMITMENTS_HANDOFF.md` for Developer B coordination.
- [x] **Database Migration (V43):**
  - Revoked physical `DELETE` on `project_financial_attributions` for `finsight_app`.
  - Added multicurrency provenance columns (`rate_source`, `rate_convention`, `rate_date`, `rate_verified`) to `project_financial_attributions`.
  - Created `project_commercial_document_links` table with least-privilege role grants.
  - Created `project_financial_obligation_links` table with concurrency split-allocation trigger (`check_project_obligation_allocation_limit`).
  - Granted least-privilege permissions to `finsight_app` (`SELECT`, `INSERT`, `UPDATE`) and `finsight_backup` (`SELECT`).
- [x] **Backend Entities & Repositories:**
  - `ProjectCommercialDocumentLink.java`
  - `ProjectFinancialObligationLink.java`
  - `ProjectCommercialDocumentLinkRepository.java`
  - `ProjectFinancialObligationLinkRepository.java`
  - Extended `FinancialObligationRepository.java` with `findByBusinessIdAndSourceId`.
- [x] **Backend DTOs & Domain Services:**
  - `ProjectCommitmentService.java` implementing:
    - Commercial document link/unlink lifecycle and list queries.
    - Financial obligation link/unlink lifecycle and list queries.
    - Commitments summary calculation with Section 10 numerical verification and deduplication.
  - `ProjectCommitmentController.java` exposing REST endpoints:
    - `GET /api/finance/projects/{projectId}/commitments`
    - `GET /api/finance/projects/{projectId}/financial-overview`
    - `GET /api/finance/projects/{projectId}/obligations`
    - `POST /api/finance/projects/{projectId}/obligation-links`
    - `POST /api/finance/projects/{projectId}/obligation-links/{linkId}/unlink`
    - `GET /api/finance/projects/{projectId}/commercial-documents`
    - `POST /api/finance/projects/{projectId}/commercial-document-links`
    - `POST /api/finance/projects/{projectId}/document-links` (alias)
    - `POST /api/finance/projects/{projectId}/commercial-document-links/{linkId}/unlink`
- [x] **Frontend Client & Components:**
  - Updated `frontend/src/lib/api/projectActuals.ts` with commitment and commercial document methods.
  - Updated `frontend/src/lib/i18n/translations/projectActuals.ts` with bilingual (EN/UR/RTL) labels and tooltips.
  - `ProjectCommitmentsCard.tsx` (commitments table, payment evidence, non-duplication badge, unlink dialog).
  - `ProjectCommercialDocumentsCard.tsx` (commercial documents table, non-posting banner, matched vs unposted badges, unlink dialog).
  - `ObligationLinkDialog.tsx` (dialog for linking obligations).
  - `CommercialDocumentLinkDialog.tsx` (dialog for linking commercial documents with non-posting notice).
  - Integrated into `ProjectActualsWorkspace.tsx` and `ProjectCostingOverviewCard.tsx`.
- [x] **Backend PostgreSQL Integration Tests:** `ProjectCommitmentPostgreSqlIT` (6/6 passed) + `ProjectAttributionPostgreSqlIT` (7/7 passed) = 13/13 passed.
- [x] **Frontend Unit & Regression Tests:** Node test runner suite `frontend/tests/project-actuals.test.mjs` (414/414 passed).
- [x] **Next.js Production Build:** `npm run build` (Exit Code 0).
- [x] **Real Full-Stack Browser Journeys:** `frontend/tests/project-commitments-browser.integration.mjs` (19/19 journeys passed).

### C05C — OCR Expense Review & Project Financial Evidence
- [x] **Integration Contract:** `docs/integration/dev-a/c05/C05_OCR_PROJECT_EVIDENCE_CONTRACT.md` authored.
- [x] **Cardinal Rule Enforced:** "OCR evidence is documentary proof, NOT cash authority." Attaching OCR receipts never creates ledger movements or alters cash headroom.
- [x] **Database Migration (V44):**
  - Table `project_ocr_evidence_links` created with foreign keys, check constraints, and indexes.
  - Runtime permissions configured (`REVOKE DELETE` from `finsight_app`, `GRANT SELECT, INSERT, UPDATE` to `finsight_app`, `GRANT SELECT` to `finsight_backup`).
- [x] **Backend Entities, DTOs & Services:**
  - `ProjectOcrEvidenceLink.java`, `ProjectOcrEvidenceStatus.java`, `ProjectOcrMatchMethod.java`
  - `ProjectOcrEvidenceLinkRepository.java`
  - `ProjectOcrEvidenceService.java` (candidate listing, evidence listing, linking, match confirmation, soft invalidation, discrepancy calculation, duplicate split detection)
  - `ProjectOcrEvidenceController.java` (`/api/finance/projects/{projectId}/ocr-candidates`, `/ocr-evidence`)
  - `TransactionReverseService.java` updated with reversal propagation to linked OCR evidence (`REVERSED`).
- [x] **Frontend Client & UI Components:**
  - `frontend/src/lib/api/projectActuals.ts` extended with OCR methods.
  - `frontend/src/lib/i18n/translations/projectActuals.ts` extended with bilingual EN/UR/RTL strings.
  - `ProjectOcrEvidenceCard.tsx` (overview embedding & dedicated tab, status badges, discrepancy warning, split badges, invalidation dialog).
  - `OcrEvidenceLinkDialog.tsx` (candidate selector, duplicate warnings) & `ConfirmMatchDialog.tsx`.
  - `ProjectActualsWorkspace.tsx` tab navigation (`tab-ocr-evidence`) & header quick-link button.
- [x] **Backend PostgreSQL Integration Tests:** `ProjectOcrEvidencePostgreSqlIT.java` (7/7 passed).
- [x] **Full C05 PostgreSQL Integration Suite:** 21/21 passed (C05A: 7, C05B: 7, C05C: 7).
- [x] **Frontend Unit & Component Regression Tests:** 416/416 passed.
- [x] **Real Full-Stack Browser Journeys:** `frontend/tests/project-ocr-evidence-browser.integration.mjs` (20/20 journeys passed).

---

## 2. Key Accounting Decisions

1. **Split Allocation Invariant:**
   A single financial transaction or obligation may be divided across multiple projects. Concurrency is guarded via database trigger and application-level pessimistic locking on source records.
2. **Planned Immutability:**
   F05 budget lines are strictly read-only inputs for C05 headroom calculation; they are never updated by actuals.
3. **Completeness & Profitability:**
   Profitability is marked `UNAVAILABLE` whenever cost basis or recognized revenue is incomplete, preventing misleading profit assertions.
4. **Developer B Non-Posting Boundary (F04):**
   Commercial documents (invoices/bills) carry `financial_effect = 'NONE'`. Linking a bill gives commercial visibility without creating receivables, payables, or cash movements. A prominent banner informs the user that unposted bills have not impacted accounting balances.
5. **Deduplication Engine Invariants:**
   - If a commercial document is matched to an active `financial_obligation` (via `source_id`), it is flagged as `MATCHED_TO_OBLIGATION` and excluded from `unpostedCommercialBills` to avoid double-counting.
   - If a payment allocation's transaction has already been attributed to the project under C05A, the paid amount is already in `attributedCashOutflows`. Therefore, `totalOutstandingCommitments` only includes the `outstandingAmount`, and the obligation is badged with `C05A {paidAmount}`.
6. **Soft Append-Only Unlinking:**
   `finsight_app` has physical `DELETE` privilege revoked on all attribution and linkage tables. Unlinking sets `link_status = 'UNLINKED'`, recording `unlinked_at`, `unlinked_by`, and `unlink_reason`.

---

## 3. Verification Summary

| Test Suite | Environment | Result |
| :--- | :--- | :--- |
| `ProjectOcrEvidencePostgreSqlIT` (C05C) | Disposable PostgreSQL with Flyway V1–V44 | **7 / 7 PASSED (100%)** |
| `ProjectCommitmentPostgreSqlIT` (C05B) | Disposable PostgreSQL with Flyway V1–V44 | **7 / 7 PASSED (100%)** |
| `ProjectAttributionPostgreSqlIT` (C05A) | Disposable PostgreSQL with Flyway V1–V44 | **7 / 7 PASSED (100%)** |
| **Total C05 Backend Integration Suite** | Disposable PostgreSQL with Flyway V1–V44 | **21 / 21 PASSED (100%)** |
| Frontend Regression Suite | Node `--test` runner | **416 / 416 PASSED (100%)** |
| Next.js Production Build | `next build` (Turbopack) | **PASSED (Exit Code 0)** |
| C05A Browser Integration | Chromium $\to$ Next.js $\to$ Spring Boot $\to$ PostgreSQL | **18 / 18 PASSED (100%)** |
| C05B Browser Integration | Chromium $\to$ Next.js $\to$ Spring Boot $\to$ PostgreSQL | **19 / 19 PASSED (100%)** |
| C05C Browser Integration | Chromium $\to$ Next.js $\to$ Spring Boot $\to$ PostgreSQL | **20 / 20 PASSED (100%)** |
