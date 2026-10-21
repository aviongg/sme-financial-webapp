# FinSight Phase 2: Recurring Financial Operations (V1)
## Feature: A-F12A-RECURRING-OPERATIONS-V1 (W5-05)

**Version:** `1.0`
**Date:** 21 October 2026
**Status:** `INTEGRATION_VERIFIED`
**Migration Baseline:** `V29__create_recurring_transactions.sql`
**Owner:** Developer A (Antigravity Backend & Codex Frontend)

---

## 1. Executive Summary & Core Accounting Principles

FinSight Phase 2 introduces `A-F12A-RECURRING-OPERATIONS-V1`, an end-to-end recurring operations engine allowing SME owners and accountants to schedule predictable, repetitive financial templates (e.g. rent, utilities, weekly cleaning, monthly retainers) and automatically generate unposted financial drafts for explicit review and canonical recording.

### Fundamental Accounting Invariant
> **Recurring financial rules and automated draft generation NEVER post money.**
>
> A recurring rule is a schedule template.
> An occurrence is a temporal expectation.
> A generated draft is an unposted financial draft in "To Review".
> **Only explicit user confirmation through the canonical financial posting engine records actual cash movements.**

1. **Zero Financial Mutation on Scheduling:**
   - Creating, editing, pausing, or archiving a recurring rule creates **ZERO** `FinancialTransaction` records and **ZERO** `AccountMovement` records.
   - Bank/cash account balances, `monthly_records`, and `score_results` remain strictly unchanged.
2. **Zero Financial Mutation on Draft Generation:**
   - Background or manual due generation creates an unposted `RecordDraft` with provenance metadata (`sourceType = 'RECURRING_OPERATION'`).
   - The occurrence enters status `DRAFT_READY`.
   - Cash and ledger accounts remain untouched until the user reviews and confirms the draft.
3. **Canonical Posting Authority:**
   - When confirmed by an authorized user (`OWNER` or `ACCOUNTANT`), canonical `TransactionPostService` creates exactly 1 `FinancialTransaction` and 1 `AccountMovement`.
   - The occurrence transitions to `POSTED` linking the `posted_transaction_id`.
   - Each occurrence can be posted at most once.
4. **Reversal Synchronization:**
   - If a posted recurring transaction is reversed via `TransactionReverseService`, the occurrence transitions to `REVERSED`.
   - The engine does not automatically recreate a duplicate draft or re-post the occurrence.

---

## 2. Database Schema (Flyway V29)

Authored in `backend/src/main/resources/db/migration/V29__create_recurring_transactions.sql`.

### Tables Created

#### 1. `recurring_rules`
Defines recurring financial schedule templates.
```sql
CREATE TABLE recurring_rules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE RESTRICT,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    event_type VARCHAR(64) NOT NULL,
    account_id UUID NOT NULL,
    counterparty_id UUID,
    amount NUMERIC(16, 2) NOT NULL,
    frequency VARCHAR(32) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE,
    next_due_date DATE,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT NOT NULL DEFAULT 0,
    created_by_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    updated_by_user_id UUID REFERENCES users(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_recurring_rules_id_business UNIQUE (id, business_id),
    CONSTRAINT fk_recurring_rules_account FOREIGN KEY (account_id, business_id)
        REFERENCES financial_accounts(id, business_id) ON DELETE RESTRICT,
    CONSTRAINT fk_recurring_rules_counterparty FOREIGN KEY (counterparty_id, business_id)
        REFERENCES counterparties(id, business_id) ON DELETE RESTRICT,
    CONSTRAINT chk_recurring_rules_status CHECK (status IN ('ACTIVE', 'PAUSED', 'ARCHIVED')),
    CONSTRAINT chk_recurring_rules_frequency CHECK (frequency IN ('WEEKLY', 'BIWEEKLY', 'MONTHLY', 'QUARTERLY', 'ANNUALLY')),
    CONSTRAINT chk_recurring_rules_event_type CHECK (event_type IN ('DIRECT_SALE_RECEIPT', 'OPERATING_EXPENSE_PAYMENT')),
    CONSTRAINT chk_recurring_rules_amount CHECK (amount > 0),
    CONSTRAINT chk_recurring_rules_dates CHECK (end_date IS NULL OR end_date >= start_date)
);
```

#### 2. `recurring_rule_occurrences`
Tracks discrete scheduled occurrences across the lifecycle.
```sql
CREATE TABLE recurring_rule_occurrences (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE RESTRICT,
    rule_id UUID NOT NULL,
    rule_version BIGINT NOT NULL,
    scheduled_date DATE NOT NULL,
    amount NUMERIC(16, 2) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    account_id UUID NOT NULL,
    counterparty_id UUID,
    description TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'SCHEDULED',
    draft_id UUID,
    posted_transaction_id UUID,
    generated_at TIMESTAMPTZ,
    posted_at TIMESTAMPTZ,
    skipped_at TIMESTAMPTZ,
    skip_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_recurring_rule_occurrences_id_business UNIQUE (id, business_id),
    CONSTRAINT fk_recurring_rule_occurrences_rule FOREIGN KEY (rule_id, business_id)
        REFERENCES recurring_rules(id, business_id) ON DELETE CASCADE,
    CONSTRAINT fk_recurring_rule_occurrences_account FOREIGN KEY (account_id, business_id)
        REFERENCES financial_accounts(id, business_id) ON DELETE RESTRICT,
    CONSTRAINT fk_recurring_rule_occurrences_draft FOREIGN KEY (draft_id, business_id)
        REFERENCES record_drafts(id, business_id) ON DELETE SET NULL,
    CONSTRAINT fk_recurring_rule_occurrences_transaction FOREIGN KEY (posted_transaction_id, business_id)
        REFERENCES financial_transactions(id, business_id) ON DELETE RESTRICT,
    CONSTRAINT uq_recurring_rule_occurrence_schedule UNIQUE (business_id, rule_id, scheduled_date),
    CONSTRAINT chk_recurring_rule_occurrences_status CHECK (status IN ('SCHEDULED', 'DRAFT_READY', 'POSTED', 'SKIPPED', 'REVERSED'))
);
```

---

## 3. Recurrence Engine & Month-End Anchor Rules

Implemented in `RecurrenceCalculator.java` with fixed-clock determinism in business-local `Asia/Karachi` time.

### Anchor Preservation Across Variable-Length Months
A frequent flaw in recurrence engines is computing date step-by-step from the previous shortened date (e.g. Jan 31 -> Feb 28 -> Mar 28), causing date drift.
`RecurrenceCalculator` preserves the original `anchorDay` across all frequencies:
- **Weekly:** Adds 7 calendar days.
- **Biweekly:** Adds 14 calendar days.
- **Monthly:** Advances `anchorMonth + n`, then clamps to `min(anchorDay, lengthOfMonth)`.
  - Example: Jan 31 -> Feb 28 (or Feb 29 on leap year) -> Mar 31 -> Apr 30 -> May 31.
- **Quarterly:** Advances `anchorMonth + (n * 3)`, clamping to length of target quarter month.
- **Annually:** Advances `anchorYear + n`, clamping Feb 29 to Feb 28 in non-leap years.

---

## 4. Lifecycles & State Transitions

### Rule Lifecycle
- **`ACTIVE`:** Evaluated during scheduled/manual due generation. Advances `next_due_date` as occurrences are processed.
- **`PAUSED`:** Excluded from automatic and manual due generation. Next due date remains preserved. Resuming immediately restores eligibility.
- **`ARCHIVED`:** Terminal state. Never generates new occurrences. Historical occurrences and posted records remain fully preserved and auditable.

### Occurrence Lifecycle
```
[SCHEDULED]
     │
     ▼ (generateDue)
[DRAFT_READY] ──────────(User Skips Occurrence)──────────► [SKIPPED]
     │                                                    (Draft Discarded)
     ▼ (User Records Draft in To Review)
  [POSTED]
     │
     ▼ (Canonical Transaction Reversal)
 [REVERSED]
```

---

## 5. Background Generation, Scheduling & Backlog Protection

Implemented via `RecurringTransactionScheduler.java` and `RecurringRuleService.java`:
- **Automated Scheduler:** Component `RecurringTransactionScheduler` triggered via Spring `@Scheduled`:
  - Default Cron: `0 0 1 * * *` (Daily at 01:00 AM in `Asia/Karachi`).
  - Enabled via `RecurringSchedulingConfiguration` with `@EnableScheduling` and `@ConditionalOnProperty(name = "app.recurring.scheduler.enabled", havingValue = "true", matchIfMissing = true)`.
  - Runs automatically in the background without requiring user interaction or manual endpoint calls.
- **Multi-Tenant Rule Discovery:** Queries active rules across all businesses via `ruleRepository.findByStatusAndNextDueDateLessThanEqual(RecurringRuleStatus.ACTIVE, today)` without requiring an authenticated user session.
- **Actor Attribution & System Audit:** Automated background generation records security audit events with `is_system = true` and `metadata = {"message":"...","automated":true}` while preserving the authorizing user ID (`rule.getCreatedByUserId()`) without impersonating active users.
- **Concurrency & Pessimistic Locks:** Uses database-level row locks (`findForUpdateByIdAndBusinessId`) during due processing to ensure single-instance safety across distributed backend nodes.
- **Database Idempotency:** The composite unique constraint `uq_recurring_rule_occurrence_schedule (business_id, rule_id, scheduled_date)` strictly prevents duplicate occurrences under any concurrent retry.
- **Batch Bounding & Downtime Catch-Up:** Due processing is bounded to a maximum of 50 occurrences per run (`MAX_OCCURRENCES_PER_RUN = 50`). When significant downtime occurs (e.g. 70 overdue weeks), the first batch generates 50 occurrences, and the remaining 21 occurrences remain due and discoverable for the next scheduled batch.
- **Draft Integration:** Creates an unposted `RecordDraft` with `sourceType = 'RECURRING_OPERATION'`, `sourceReferenceId = occurrence.id.toString()`, and `counterpartyId` if set.
- **Audit Logging:** Emits structured JSONB audit events:
  - `RECURRING_RULE_CREATED`
  - `RECURRING_RULE_UPDATED`
  - `RECURRING_RULE_PAUSED`
  - `RECURRING_RULE_RESUMED`
  - `RECURRING_RULE_ARCHIVED`
  - `RECURRING_OCCURRENCE_GENERATED` (`is_system = true` for scheduler runs)
  - `RECURRING_OCCURRENCE_SKIPPED`
  - `RECURRING_OCCURRENCE_POSTED`
  - `RECURRING_OCCURRENCE_REVERSED`

---

## 6. Frontend Workspace (`/records/recurring`)

Built with responsive Next.js App Router, Tailwind CSS, and Deep Spruce Teal tokens:
- **Tabs:**
  1. **Active Rules:** List of rules with event badges, frequency, next due date, actions (Preview, Edit, Pause).
  2. **Paused Rules:** Filtered view with Resume and Edit actions.
  3. **Occurrences & History:** Unified historical ledger showing Scheduled, Draft Ready, Posted, Skipped, and Reversed occurrences.
- **Actions:**
  - **Create Rule:** Full-featured modal with dynamic accounts and counterparties, real-time schedule preview, and form validation.
  - **Generate Due Drafts:** Immediate manual run button with real-time success banners.
  - **Review Draft:** Direct deep-link to `/records?view=review`.
  - **Skip Occurrence:** Confirmation dialog with optional reason; discards linked unposted draft and advances schedule.
- **Bilingual & Responsive:**
  - 100% English and Urdu copy parity with RTL support (`recurring-copy.ts`).
  - Mobile layout verified on 390×844 without horizontal overflow.
  - Mandatory accounting banner displayed:
    *"Recurring entries are not recorded as actual transactions until you review and confirm them."* / *"تکراری اندراجات اس وقت تک اصل لین دین کے طور پر درج نہیں ہوتے جب تک آپ ان کا جائزہ لے کر تصدیق نہ کر لیں۔"*

---

## 7. Verification Evidence & Quality Gates

### Backend Unit & PostgreSQL IT Tests
- **34 / 34 Passed** (`.\mvnw.cmd test`):
  - `RecurrenceCalculatorTest`: 9/9 passed (Weekly, Biweekly, Monthly, Quarterly, Annually, Month-end rollover, Leap year).
  - `RecurringRuleControllerTest`: 7/7 passed (MockMvc endpoints, validation, RBAC).
  - `RecurringRuleServiceTest`: 10/10 passed (CRUD, pause, resume, archive, skip, due generation).
  - `RecurringTransactionsPostgreSqlIT`: 8/8 passed against real Flyway V1–V29 on disposable PostgreSQL 14.22:
    - `testEndToEndRecurringOperationsLifecycle`
    - `testRuleEditingPreservesHistory`
    - `testSkipOccurrenceDiscardsDraft`
    - `testTenantIsolation`
    - `testAutomaticSchedulerProcessingAcrossBusinesses`
    - `testConcurrentGenerationRacingAndIdempotency`
    - `testBoundedCatchUpAfterDowntime`
    - `testRegressionManualDraftAndOrdinaryReversalUnaffected`
- **Shared Financial Services Regression Suite:** 104 / 104 passed:
  - `RecordDraftPostgreSqlIT`: 11/11 passed.
  - `RecordDraftServiceTest`: 74/74 passed.
  - `FinancialTransactionReversalPostgreSqlIT`: 10/10 passed.
  - `TransactionReverseServiceTest`: 9/9 passed.

### Frontend Unit & Contract Tests
- **226 / 226 Passed** (`npm test`):
  - `frontend/tests/recurring.test.mjs`: 6/6 passed (Copy symmetry, review notice, tab presence, API contracts, frequencies, occurrence statuses).
- **TypeScript:** `npx tsc --noEmit` exited with code 0 (0 errors).
- **ESLint:** `npm run lint` exited with code 0 (0 errors, 1 pre-existing warning).
- **Production Build:** `npm run build` compiled static route `/records/recurring` with zero errors.

### Real Microsoft Edge Browser Acceptance (Journeys A–M)
Executed against real Spring Boot (8680), Gateway (3580), Production Next.js (3581), and disposable PostgreSQL 14.22 with Flyway V29:
- **PASS JOURNEY A:** Rule creation (Office rent PKR 50,000 monthly, preview, zero mutations) verified.
- **PASS JOURNEY B:** Due draft generation (DRAFT_READY occurrence, unposted draft in To Review, 0 cash movement) verified.
- **PASS JOURNEY C:** Explicit posting (draft confirmation creates 1 transaction, 1 movement, occurrence POSTED) verified.
- **PASS JOURNEY D:** Idempotent replay (rerun produces 0 duplicate drafts and 0 postings) verified.
- **PASS JOURNEY E:** Pause/resume rule lifecycle verified.
- **PASS JOURNEY F:** Rule editing (historical occurrence preserved at 30,000, future at 35,000) verified.
- **PASS JOURNEY G:** Skip occurrence (draft discarded, status SKIPPED, zero posting) verified.
- **PASS JOURNEY H:** Recurring receipt (revenue classification, positive cash flow, reconciliation eligibility) verified.
- **PASS JOURNEY I:** Authorization and multi-tenant isolation verified (Accountant read/create, Viewer 403 denied, Cross-tenant 404).
- **PASS JOURNEY J:** Bilingual English/Urdu, RTL, 390x844 mobile responsiveness without overflow verified.
- **PASS JOURNEY K:** Reversal synchronization (occurrence transitions to REVERSED without regeneration) verified.
- **PASS JOURNEY L:** Strict financial invariants verified (+3 transactions, +3 movements strictly from explicit user actions).
- **PASS JOURNEY M:** Restart recovery and backlog idempotency verified.

### Browser Artifacts Captured
Screenshots saved in `frontend/test-artifacts/accounts-live/recurring/`:
- `journey_a_initial.png`
- `journey_a_preview_modal.png`
- `journey_a_rule_created.png`
- `journey_b_occurrence_draft_ready.png`
- `journey_b_draft_in_review.png`
- `journey_c_occurrence_posted.png`
- `journey_d_idempotent_replay.png`
- `journey_e_rule_paused.png`
- `journey_f_rule_editing.png`
- `journey_g_skip_occurrence.png`
- `journey_h_recurring_receipt.png`
- `journey_j_urdu_desktop.png`
- `journey_j_urdu_mobile.png`
- `journey_k_reversal.png`
- `journey_m_recovery.png`

---

## 8. Cross-Module Boundaries & Known Limitations

1. **Fatima (Developer B) Domain:**
   - Recurring rules do not generate recurring sales invoices, recurring vendor bills, or recurring project expenses. Fatima owns invoice/bill lifecycles and models.
2. **Internal Transfers:**
   - Deferred from V1 recurring rules to ensure transfer dual-movement validation remains strictly interactive.
3. **Payment Schedules vs Recurring Operations:**
   - V27 payment schedules represent installment liquidation against recognized debts (`FINANCIAL_OBLIGATION`). Recurring operations represent repeating income/expense templates. They remain distinct and un-interleaved in V1.
