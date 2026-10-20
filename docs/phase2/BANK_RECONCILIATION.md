# FinSight Phase 2: Bank Statement Reconciliation (V1)
## Feature: A-F12A-BANK-RECONCILIATION-V1

**Version:** `1.0`
**Date:** 21 October 2026
**Status:** `INTEGRATION_VERIFIED`
**Migration Baseline:** `V28__create_bank_reconciliation.sql`
**Owner:** Developer A (Antigravity Backend & Codex Frontend)

---

## 1. Executive Summary & Core Accounting Principles

FinSight Phase 2 introduces `A-F12A-BANK-RECONCILIATION-V1`, an end-to-end, auditable bank reconciliation engine connecting external banking evidence (CSV/TSV statements) with internal FinSight financial postings.

### Strict Accounting Invariants
1. **Reconciliation is NOT Posting:**
   - Importing a bank statement creates **ZERO** `FinancialTransaction` records and **ZERO** `AccountMovement` entries.
   - External statement rows represent external corroborating evidence, never independent ledger transactions.
   - Importing or matching statements never alters financial account balances, `monthly_records`, or `score_results`.
2. **Matching is NOT Posting:**
   - Confirming a match links a statement entry with an existing eligible `AccountMovement` via an auditable `BankReconciliationMatch` record.
   - Matching never creates duplicate cash movements or inflates income/expenses.
3. **Draft Creation Preserves Provenance Without Assumption:**
   - For genuine missing transactions, users may create an incomplete draft linked directly to the statement entry.
   - The transaction classification (`eventType`) is left strictly **unresolved** (`null`) until the user explicitly reviews, classifies, and records it in To Review.
   - Bank deposits are never automatically assumed to be revenue; withdrawals are never automatically assumed to be operating expenses.
4. **Active Link Cardinality:**
   - In V1, active reconciliation links are strictly 1-to-1: at most one active statement entry matches exactly one account movement, and vice versa. Enforced via database partial unique indexes.

---

## 2. Database Schema (Flyway V28)

The schema was authored in `backend/src/main/resources/db/migration/V28__create_bank_reconciliation.sql`.

### Tables Created

#### 1. `bank_statement_imports`
Tracks import batches and statement file provenance.
```sql
CREATE TABLE bank_statement_imports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE RESTRICT,
    account_id UUID NOT NULL,
    source_filename VARCHAR(255),
    file_fingerprint VARCHAR(64) NOT NULL,
    mapping_config TEXT,
    total_entries INT NOT NULL DEFAULT 0,
    opening_balance NUMERIC(16, 2),
    closing_balance NUMERIC(16, 2),
    statement_start_date DATE,
    statement_end_date DATE,
    imported_by_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    status VARCHAR(32) NOT NULL DEFAULT 'COMPLETED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_bank_stmt_imports_account
        FOREIGN KEY (account_id, business_id)
        REFERENCES financial_accounts(id, business_id) ON DELETE RESTRICT
);
```

#### 2. `bank_statement_entries`
Stores normalized rows from imported bank statements.
```sql
CREATE TABLE bank_statement_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE RESTRICT,
    import_id UUID NOT NULL REFERENCES bank_statement_imports(id) ON DELETE CASCADE,
    account_id UUID NOT NULL,
    source_row_number INT NOT NULL,
    posted_date DATE NOT NULL,
    value_date DATE,
    direction VARCHAR(16) NOT NULL, -- INCREASE (credit/deposit) or DECREASE (debit/withdrawal)
    amount NUMERIC(16, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'PKR',
    description VARCHAR(500),
    bank_reference VARCHAR(255),
    external_transaction_id VARCHAR(255),
    running_balance NUMERIC(16, 2),
    source_fingerprint VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'UNMATCHED', -- UNMATCHED, MATCHED, EXCLUDED
    exclusion_reason VARCHAR(500),
    linked_draft_id UUID REFERENCES record_drafts(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_bank_stmt_entries_account
        FOREIGN KEY (account_id, business_id)
        REFERENCES financial_accounts(id, business_id) ON DELETE RESTRICT,
    CONSTRAINT chk_bank_stmt_entries_direction
        CHECK (direction IN ('INCREASE', 'DECREASE')),
    CONSTRAINT chk_bank_stmt_entries_amount
        CHECK (amount > 0),
    CONSTRAINT chk_bank_stmt_entries_status
        CHECK (status IN ('UNMATCHED', 'MATCHED', 'EXCLUDED'))
);
```

#### 3. `bank_reconciliation_matches`
Maintains auditable links between external statement entries and internal ledger account movements.
```sql
CREATE TABLE bank_reconciliation_matches (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE RESTRICT,
    statement_entry_id UUID NOT NULL REFERENCES bank_statement_entries(id) ON DELETE RESTRICT,
    account_movement_id UUID NOT NULL REFERENCES account_movements(id) ON DELETE RESTRICT,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, CANCELLED
    match_method VARCHAR(32) NOT NULL DEFAULT 'MANUAL', -- EXACT, SUGGESTED, MANUAL
    confidence_score INT NOT NULL DEFAULT 100,
    match_reasons TEXT,
    confirmed_by_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    confirmed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    unmatched_by_user_id UUID REFERENCES users(id) ON DELETE RESTRICT,
    unmatched_at TIMESTAMPTZ,
    unmatch_reason VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_bank_recon_matches_status
        CHECK (status IN ('ACTIVE', 'CANCELLED')),
    CONSTRAINT chk_bank_recon_matches_confidence
        CHECK (confidence_score >= 0 AND confidence_score <= 100)
);
```

### Constraints & Indexes
1. **Active 1-to-1 Link Constraints:**
   - `CREATE UNIQUE INDEX uq_bank_recon_active_statement_entry ON bank_reconciliation_matches(statement_entry_id) WHERE status = 'ACTIVE';`
   - `CREATE UNIQUE INDEX uq_bank_recon_active_account_movement ON bank_reconciliation_matches(account_movement_id) WHERE status = 'ACTIVE';`
2. **Composite Tenant Indexes:**
   - `idx_bank_stmt_imports_business_account` on `(business_id, account_id, created_at DESC)`
   - `idx_bank_stmt_entries_lookup` on `(business_id, account_id, status, posted_date)`
   - `idx_bank_stmt_entries_fingerprint` on `(business_id, account_id, source_fingerprint)`
   - `idx_bank_recon_matches_lookup` on `(business_id, statement_entry_id, status)`
3. **Least Privilege Runtime Grants:**
   - Runtime user `finsight_app` receives `SELECT, INSERT, UPDATE` on the new tables. Physical `DELETE` is explicitly withheld.

---

## 3. Statement Import & Duplicate Detection Architecture

### Source Formats Supported
- **CSV Upload:** Standard UTF-8, handling comma delimiters, quoted multiline cells, CRLF/LF, UTF-8 BOM.
- **TSV Paste:** Tab-separated rows copied directly from Excel or online banking portals.

### Column Mapping Conventions
- **Date Column:** Normalized to ISO `YYYY-MM-DD` (Asia/Karachi timezone).
- **Description Column:** Raw narration preserved.
- **Bank Reference Column:** Optional transaction reference.
- **Direction / Amount Mapping:**
  - *Separate Debit/Credit Columns:* Disambiguates incoming vs. outgoing amounts.
  - *Signed Single Column:* Positive = Credit (`INCREASE`), Negative = Debit (`DECREASE`).
  - *Credit Only / Debit Only:* Specialized one-direction statements.

### Content Fingerprinting & Duplicate Detection
1. **Row Fingerprint:**
   `SHA-256(postedDate + ":" + direction + ":" + amount + ":" + trimmed(description).lower() + ":" + trimmed(reference).lower())`
2. **Duplicate Warnings vs. Invalidation:**
   - Cross-import identical entries generate a `duplicateWarning: true` indicator in the response and UI badge.
   - Legitimate recurring transactions (e.g., identical weekly rent or subscription) remain fully representable and matchable.
3. **Idempotent Replay:**
   - Re-submitting the same payload with the same `Idempotency-Key` safely returns the existing import (`200 OK` or `201 Created`) without recreating rows.
   - Submitting a different payload under an existing key triggers `409 Conflict`.

---

## 4. Matching Engine & Hard Eligibility Rules

The candidate discovery engine evaluates internal `account_movements` against statement entries using strict financial eligibility rules.

### Hard Eligibility Filters (Zero False Matches)
To be considered as a candidate, an internal `account_movement` must satisfy:
1. **Same Active Business:** `m.businessId == context.businessId()`.
2. **Same Financial Account:** `m.accountId == statementEntry.accountId`.
3. **Same Currency:** `account.currency == "PKR"`.
4. **Same Direction:**
   - Statement `INCREASE` (credit/deposit) $\implies$ AccountMovement `INCREASE`.
   - Statement `DECREASE` (debit/withdrawal) $\implies$ AccountMovement `DECREASE`.
5. **Exact Amount:** `m.amount.compareTo(statementEntry.amount) == 0`.
6. **No Existing Active Match:** `m.id` is not present in `uq_bank_recon_active_account_movement`.
7. **Active Ledger State:** Neither the transaction nor its movement has been reversed.

### Candidate Ranking & Scoring
Candidates passing all hard filters are scored deterministically:
- **Base Score:** 50 points.
- **Exact Date Match:** +30 points (`postedDate == effectiveDate`).
- **Date Proximity:**
  - $\le 3$ days difference: +20 points.
  - $\le 7$ days difference: +10 points.
  - $\le 14$ days difference: +5 points.
- **Reference Match:** +20 points if statement bank reference matches transaction reference number (case-insensitive substring).
- **Description Match:** +10 points if narration tokens overlap.

**High Confidence:** Score $\ge 90$
**Possible Candidate:** Score between 50 and 89
**No Candidate:** No internal movements meet the hard financial criteria.

### Confirmation & Server-Side Concurrency Revalidation
All suggestions require explicit user confirmation. When the user confirms a match:
1. Row-level lock (`SELECT ... FOR UPDATE`) is acquired on `bank_statement_entries`.
2. Revalidation confirms the entry is still `UNMATCHED`.
3. Revalidation confirms the target `account_movement` is still eligible and unlinked.
4. Database unique index `uq_bank_recon_active_account_movement` atomically prevents any concurrent race conditions.

---

## 5. Transfers, Obligations, Advances & Reversals Integration

### Internal Transfers (Dual Leg Matching)
An internal transfer between Bank A and Bank B creates two movements:
- Leg 1: Outgoing `DECREASE` on Bank A.
- Leg 2: Incoming `INCREASE` on Bank B.
Each leg is independently reconciled against its respective bank statement:
- Bank A statement debit $\leftrightarrow$ Leg 1 movement.
- Bank B statement credit $\leftrightarrow$ Leg 2 movement.
**Invariant:** Matching both legs creates 0 duplicate financial transactions and 0 duplicate income/expense.

### Reversals & Corrections
When a financial transaction is reversed:
1. The compensating reversal entry has the opposite direction.
2. In V1, reversed transactions are excluded from candidate suggestions.
3. If an already-matched transaction is subsequently reversed, the match audit history is preserved and the reconciliation link can be undone with an explicit audit reason.

### Draft Integration
For statement entries with no corresponding ledger posting:
- User clicks "Create Draft".
- System generates an incomplete draft via `recordDraftService.createDraft` with:
  - Provenance key: `RECON-DRAFT-{entryId}`
  - Date: statement posted date
  - Amount: statement amount
  - Description: statement description/reference
  - Account: selected bank account
  - `eventType`: strictly `null` (unresolved classification)
- User navigates to To Review to classify and record.
- Once posted, the resulting `AccountMovement` becomes immediately discoverable for reconciliation without re-importing the statement.

---

## 6. Account Reconciliation Summaries

The backend computes live reconciliation metrics for any BANK account:
- `totalEntries`: Count of all imported statement rows.
- `matchedEntries`: Count of entries with active reconciliation links.
- `unmatchedEntries`: Count of unreconciled statement entries.
- `excludedEntries`: Count of reviewed and excluded entries.
- `unmatchedMovements`: Internal ledger movements on the account that have not yet been corroborated by a statement.
- `importedDebitTotal`: Total amount of withdrawals (PKR).
- `importedCreditTotal`: Total amount of deposits (PKR).
- `matchedTotal`: Total amount reconciled (PKR).
- `unmatchedTotal`: Total unreconciled amount (PKR).
- `currentLedgerBalance`: Live calculated account balance.
- `lastReconciledAt`: Timestamp of the most recent match confirmation.

---

## 7. REST API Surface

Base path: `/api/finance/reconciliation`

| Endpoint | Method | Permission | Description |
| :--- | :--- | :--- | :--- |
| `/imports` | `POST` | `FINANCIAL_TRANSACTION_POST` | Import a statement batch (idempotent, multipart or JSON) |
| `/imports?accountId={id}` | `GET` | `FINANCIAL_TRANSACTION_READ` | List statement import batches for an account |
| `/imports/{id}` | `GET` | `FINANCIAL_TRANSACTION_READ` | Get import batch details and summary |
| `/entries` | `GET` | `FINANCIAL_TRANSACTION_READ` | Page statement entries (filters: `accountId`, `status`) |
| `/entries/{id}` | `GET` | `FINANCIAL_TRANSACTION_READ` | Get statement entry detail with active match if present |
| `/entries/{id}/candidates` | `GET` | `FINANCIAL_TRANSACTION_READ` | Discover ranked candidate ledger movements |
| `/entries/{id}/match` | `POST` | `FINANCIAL_TRANSACTION_POST` | Confirm a match against an `accountMovementId` |
| `/entries/{id}/unmatch` | `POST` | `FINANCIAL_TRANSACTION_POST` | Undo an active match with optional audit reason |
| `/entries/{id}/exclude` | `POST` | `FINANCIAL_TRANSACTION_POST` | Exclude a statement row with mandatory reason |
| `/entries/{id}/draft` | `POST` | `FINANCIAL_TRANSACTION_POST` | Create an incomplete linked draft from unmatched entry |
| `/accounts/{id}/summary` | `GET` | `FINANCIAL_TRANSACTION_READ` | Compute account reconciliation summary & KPIs |
| `/accounts/{id}/unmatched-movements` | `GET` | `FINANCIAL_TRANSACTION_READ` | List ledger movements with no active statement match |

---

## 8. Role-Based Access Control & Tenant Isolation

| Operation | OWNER | ACCOUNTANT | MANAGER | VIEWER |
| :--- | :---: | :---: | :---: | :---: |
| View Statements & Summaries | Allowed | Allowed | Allowed | Allowed |
| Import Statements | Allowed | Allowed | 403 Forbidden | 403 Forbidden |
| Confirm Match | Allowed | Allowed | 403 Forbidden | 403 Forbidden |
| Undo Match | Allowed | Allowed | 403 Forbidden | 403 Forbidden |
| Exclude Row | Allowed | Allowed | 403 Forbidden | 403 Forbidden |
| Create Draft from Row | Allowed | Allowed | 403 Forbidden | 403 Forbidden |

### Tenant Isolation
- All database queries enforce `WHERE business_id = context.businessId()`.
- Passing an `accountId` from another business returns `404 Not Found`.
- Foreign keys ensure statement entries cannot be linked to account movements from another tenant.

---

## 9. Verification & Acceptance Evidence

### Backend Unit & Integration Tests (36/36 Passing)
Executed via `mvnw.cmd test "-Dtest=BankStatementImportServiceTest,BankReconciliationServiceTest,BankReconciliationControllerTest,BankReconciliationPostgreSqlIT"`:
- `BankStatementImportServiceTest`: 6/6 passed (import persistence, validation, duplicate detection, idempotency).
- `BankReconciliationServiceTest`: 15/15 passed (candidate ranking, hard eligibility, matching, unmatching, draft creation, exclusions, summaries).
- `BankReconciliationControllerTest`: 4/4 passed (MockMvc controller contracts, validation error responses, permissions).
- `BankReconciliationPostgreSqlIT`: 11/11 passed on embedded PostgreSQL 14.22 with Flyway V28:
  - `testSuccessfulStatementImportWithEntries`: imports external statement rows with correct running balance.
  - `testMatchEntryDirectAndCandidateRanking`: confirms 1-to-1 match link, status transition to MATCHED, and zero financial mutations.
  - `testUnmatchRevertsEntryAndCancelsMatch`: undoes confirmed match with mandatory audit reason; returns status to UNMATCHED.
  - `testExcludeEntryWithReason`: marks row EXCLUDED with reason; removes candidate eligibility.
  - `testUniqueActiveConstraintsEnforced`: PostgreSQL partial unique indexes block duplicate active match on same statement entry or account movement.
  - `testTenantIsolationDirectDatabaseRejection`: composite foreign keys reject cross-tenant imports, entries, and match links directly at PostgreSQL level.
  - `testReconciliationNeverMutatesFinancialLedgerOrScores`: invariant fingerprint test confirming 0 mutations to transactions, movements, monthly records, or scores.
  - `testReconciliationInvalidatedWhenTransactionReversed`: verifies that reversing a transaction automatically cancels its active reconciliation match with audit history and reverts the statement entry to UNMATCHED.
  - `testCompleteUnmatchedEntryToDraftToPostingToMatchLifecycle`: verifies statement evidence $\to$ persistent draft with unresolved event type $\to$ explicit user classification & recording $\to$ exactly one new FinancialTransaction & AccountMovement $\to$ candidate match $\to$ confirmed reconciliation $\to$ zero extra financial mutations $\to$ idempotent replay and provenance retention.
  - `testReadOnlyGetEndpointsNeverMutateDatabaseAcrossRoles`: verifies that GET endpoints across OWNER, ACCOUNTANT, MANAGER, and VIEWER roles perform zero database mutations, create zero audit rows, and present stale matches as unmatched without modifying underlying storage.
  - `testBankToBankTransferDualLegReconciliation`: verifies internal funds transfer between two BANK accounts (HBL to Meezan), confirms cross-account matching is rejected with HTTP 400, confirms debit matching to Leg A and credit matching to Leg B coexist actively, confirms CASH accounts reject statement imports, and confirms zero duplicate income/expense postings.

### 9.1 Transaction Reversal Policy & Pure Read-Safety
FinSight enforces an automated invalidation policy when an underlying financial transaction is reversed:
1. **Atomic Invalidation on Reversal:** When `TransactionReverseService.postReversal` executes, it invokes `cancelReconciliationMatchesForReversal(...)`. Any active `BankReconciliationMatch` pointing to movements of the reversed transaction is set to `CANCELLED`, timestamped, stamped with the actor's user ID, and given the audit reason `"Automatically cancelled due to transaction reversal: <reversalTxId>"`.
2. **Statement Entry Restoration:** The linked `BankStatementEntry` is automatically reverted from `MATCHED` to `UNMATCHED`. Statement evidence is never deleted.
3. **Pure Read-Only HTTP Semantics (Zero GET Mutations):** `BankReconciliationService.getEntryDetail` performs strictly read-only inspection. If an active match points to a reversed transaction, it presents the entry in-memory as `UNMATCHED` with `matchId = null` in the response DTO. It **never** writes to the database, never alters match statuses during GET, and never creates audit events on read-only requests.
4. **Candidate Exclusion:** Both reversed transactions and compensating reversal transactions are strictly excluded from candidate suggestions and unmatched movements listings.
5. **No Double Reversal / Zero Accounting Drift:** Reversal cancellation creates zero additional account movements and performs no redundant reversals.

### 9.2 Bank-to-Bank Dual-Leg Transfer Reconciliation
1. **BANK-Only Restriction:** Statement imports are strictly restricted to `AccountType.BANK`. Attempts to import statements for `AccountType.CASH` are rejected with HTTP 400 (`ReconciliationValidationException: Reconciliation statement import is strictly supported for BANK accounts`).
2. **Account Boundary Enforced:** Outgoing statement debits on Bank A cannot match Bank B account movements; incoming statement credits on Bank B cannot match Bank A movements. Cross-matching attempts fail validation with HTTP 400.
3. **Coexistence of Matches:** The outgoing debit match on Bank A and the incoming credit match on Bank B coexist actively as two distinct 1-to-1 links in `bank_reconciliation_matches`.
4. **Cash Flow Neutrality:** Matching internal transfer legs creates zero new financial transactions or account movements, preventing any duplicate revenue or expense recognition.

### 9.3 Database-Level Tenant Isolation & Concurrent Matching Invariants
1. **Composite Foreign Keys:** Flyway V28 enforces tenant boundaries at the database engine level via composite keys:
   - `fk_bank_stmt_imports_account (account_id, business_id) REFERENCES financial_accounts(id, business_id)`
   - `fk_bank_stmt_entries_import (import_id, business_id) REFERENCES bank_statement_imports(id, business_id)`
   - `fk_reconciliation_statement_entry (statement_entry_id, business_id) REFERENCES bank_statement_entries(id, business_id)`
   - `fk_reconciliation_account_movement (account_movement_id, business_id) REFERENCES account_movements(id, business_id)`
   Any cross-tenant link attempt fails immediately with PostgreSQL foreign key violation (error code `23503`).
2. **Matching Concurrency:**
   - Bank statement entries are acquired using `PESSIMISTIC_WRITE` locks (`SELECT ... FOR UPDATE`).
   - PostgreSQL partial unique indexes enforce 1-to-1 active link invariants:
     - `uq_reconciliation_active_statement_entry ON bank_reconciliation_matches (business_id, statement_entry_id) WHERE status = 'ACTIVE'`
     - `uq_reconciliation_active_account_movement ON bank_reconciliation_matches (business_id, account_movement_id) WHERE status = 'ACTIVE'`
   - Concurrent race attempts to match two statement entries to the same movement, or one statement entry to two movements, result in at most one active match; the second attempt is rejected with HTTP 409 Conflict / unique constraint violation without partial state.

### Frontend Quality Gates
- **TypeScript:** `npx tsc --noEmit` exited with **0 errors**.
- **ESLint:** `npm run lint` completed with **0 errors** across all new reconciliation components (1 pre-existing warning in SessionProvider.tsx).
- **Unit Tests:** `npm test` passed **220/220 tests**.
- **Production Build:** `npm run build` completed successfully, producing dynamic server-rendered `/records/reconciliation`.

### Microsoft Edge Browser Acceptance Suite (Journeys A–J)
Executed on disposable PostgreSQL 14.22 with Flyway V28, Spring Boot on port 8680, Next.js on port 3581:

| Journey | Description | Result | Evidence Artifact |
| :--- | :--- | :---: | :--- |
| **Journey A** | Statement Import: Paste TSV, map columns, preview rows, commit, reload and verify persistence. | **PASS** | `journey_a_initial.png`, `journey_a_preview.png` |
| **Journey B** | Exact Match: Corroborate PKR 50,000 receipt with bank credit, rank candidate, confirm match, verify zero new cash postings. | **PASS** | `journey_b_candidate_sheet.png`, `journey_b_matched.png`, `journey_b_reconciled_details.png` |
| **Journey C** | Ambiguous Matches: Distinguish two movements with identical amounts, select intended candidate, enforce 1-to-1 link. | **PASS** | `journey_c_ambiguous_candidates.png` |
| **Journey D** | Unmatched Entry to Draft: Create linked incomplete draft, verify provenance in To Review, verify unclassified event type. | **PASS** | `journey_d_draft_created.png`, `journey_d_draft_in_review.png` |
| **Journey E** | Reconciliation Correction: Undo confirmed match with audit reason, verify status reverts to UNMATCHED. | **PASS** | `journey_e_unmatched.png` |
| **Journey F** | Internal Transfer Dual Leg: Match outgoing debit to Leg A, incoming credit to Leg B, verify zero duplicate income/expense. | **PASS** | Automated live API assertion |
| **Journey G** | Duplicate Statement Imports: Idempotent replay under same key, warning badges on overlapping content without auto-deletion. | **PASS** | Automated live API assertion |
| **Journey H** | Authorization Matrix & Tenant Isolation: OWNER/ACCOUNTANT pass, VIEWER/MANAGER 403, cross-tenant 404, CSRF 403. | **PASS** | Automated live security assertion |
| **Journey I** | Interface Quality: Bilingual Urdu RTL (`بینک مفاہمت`), mobile viewport 390×844 without horizontal overflow, keyboard focus. | **PASS** | `journey_i_urdu_rtl.png`, `journey_i_mobile_390x844.png` |
| **Journey J** | Financial Integrity: Zero mutations to `financial_transactions`, `account_movements`, `monthly_records`, or `score_results`. | **PASS** | Database fingerprint invariant check |

---

## 10. File Inventory

### Backend Files Authored / Modified
- `backend/src/main/resources/db/migration/V28__create_bank_reconciliation.sql`
- `backend/src/main/java/com/app/sme_health_backend/reconciliation/entity/BankStatementImport.java`
- `backend/src/main/java/com/app/sme_health_backend/reconciliation/entity/BankStatementEntry.java`
- `backend/src/main/java/com/app/sme_health_backend/reconciliation/entity/BankReconciliationMatch.java`
- `backend/src/main/java/com/app/sme_health_backend/reconciliation/model/StatementEntryStatus.java`
- `backend/src/main/java/com/app/sme_health_backend/reconciliation/model/ReconciliationMatchStatus.java`
- `backend/src/main/java/com/app/sme_health_backend/reconciliation/model/ReconciliationMatchMethod.java`
- `backend/src/main/java/com/app/sme_health_backend/reconciliation/repository/BankStatementImportRepository.java`
- `backend/src/main/java/com/app/sme_health_backend/reconciliation/repository/BankStatementEntryRepository.java`
- `backend/src/main/java/com/app/sme_health_backend/reconciliation/repository/BankReconciliationMatchRepository.java`
- `backend/src/main/java/com/app/sme_health_backend/reconciliation/dto/StatementImportRequest.java`
- `backend/src/main/java/com/app/sme_health_backend/reconciliation/dto/StatementEntryItemRequest.java`
- `backend/src/main/java/com/app/sme_health_backend/reconciliation/dto/StatementImportResponse.java`
- `backend/src/main/java/com/app/sme_health_backend/reconciliation/dto/StatementImportDetailResponse.java`
- `backend/src/main/java/com/app/sme_health_backend/reconciliation/dto/StatementEntryResponse.java`
- `backend/src/main/java/com/app/sme_health_backend/reconciliation/dto/StatementEntryDetailResponse.java`
- `backend/src/main/java/com/app/sme_health_backend/reconciliation/dto/MatchCandidateResponse.java`
- `backend/src/main/java/com/app/sme_health_backend/reconciliation/dto/ReconciliationMatchRequest.java`
- `backend/src/main/java/com/app/sme_health_backend/reconciliation/dto/ReconciliationUnmatchRequest.java`
- `backend/src/main/java/com/app/sme_health_backend/reconciliation/dto/ReconciliationExcludeRequest.java`
- `backend/src/main/java/com/app/sme_health_backend/reconciliation/dto/AccountReconciliationSummaryResponse.java`
- `backend/src/main/java/com/app/sme_health_backend/reconciliation/dto/UnmatchedMovementResponse.java`
- `backend/src/main/java/com/app/sme_health_backend/reconciliation/exception/ReconciliationValidationException.java`
- `backend/src/main/java/com/app/sme_health_backend/reconciliation/exception/ReconciliationConflictException.java`
- `backend/src/main/java/com/app/sme_health_backend/reconciliation/service/BankStatementImportService.java`
- `backend/src/main/java/com/app/sme_health_backend/reconciliation/service/BankReconciliationService.java`
- `backend/src/main/java/com/app/sme_health_backend/reconciliation/controller/BankReconciliationController.java`
- `backend/src/main/java/com/app/sme_health_backend/accounting/service/TransactionReverseService.java` (reconciliation match cancellation on reversal)
- `backend/src/main/java/com/app/sme_health_backend/shared/exception/GlobalExceptionHandler.java` (reconciliation handlers added)
- `backend/src/test/java/com/app/sme_health_backend/reconciliation/service/BankStatementImportServiceTest.java`
- `backend/src/test/java/com/app/sme_health_backend/reconciliation/service/BankReconciliationServiceTest.java`
- `backend/src/test/java/com/app/sme_health_backend/reconciliation/controller/BankReconciliationControllerTest.java`
- `backend/src/test/java/com/app/sme_health_backend/reconciliation/BankReconciliationPostgreSqlIT.java`

### Frontend Files Authored / Modified
- `frontend/src/app/records/reconciliation/page.tsx`
- `frontend/src/components/records/reconciliation/ReconciliationWorkspace.tsx`
- `frontend/src/components/records/reconciliation/StatementImportModal.tsx`
- `frontend/src/components/records/reconciliation/MatchReviewSheet.tsx`
- `frontend/src/components/records/reconciliation/reconciliation-copy.ts`
- `frontend/src/components/records/records-copy.ts` (added reconciliation tab copy)
- `frontend/src/app/records/page.tsx` (connected Bank Reconciliation tab and workspace routing)
- `frontend/src/lib/api/dev-a/reconciliation.ts`
- `frontend/tests/reconciliation.test.mjs`
- `frontend/tests/reconciliation.live.mjs`
- `frontend/tests/accounts-live.integration.mjs` (added `--reconciliation-only` flag and V28 schema assertion)

---

## 11. Known Limitations & Future Roadmap

1. **Foreign Currency Statements:** V1 is strictly scoped to PKR BANK accounts. Multi-currency and FX gain/loss reconciliation will be introduced in subsequent phases.
2. **Direct Bank Feeds & Open Banking:** V1 operates via structured CSV/TSV statement imports. Automated banking API feeds are out of scope for V1.
3. **Many-to-Many Reconciliation:** Complex batch clearing (e.g. single bank deposit matching multiple sales receipts) is not supported in V1's 1-to-1 link model.
4. **Automated Auto-Reconciliation:** High-confidence matches are ranked and presented to the user for explicit confirmation. Fully automated clearing without human review is intentionally deferred to protect SME ledger integrity.
