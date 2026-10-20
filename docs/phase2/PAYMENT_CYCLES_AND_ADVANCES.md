# FinSight Phase 2: A-F03-PAYMENT-CYCLES-V2
## Multi-Obligation Payment Allocation, Customer/Supplier Advances, Unapplied Balances, and Payment Scheduling

**Status:** `INTEGRATION_VERIFIED`  
**Flyway Migration:** `V27__create_payment_cycles_and_advances.sql`  
**Domain Authority:** Developer A (Antigravity Backend & Codex Frontend)  
**Date:** 20 October 2026  

---

## 1. Overview & Business Context

In SME operations, business transactions rarely follow a 1-to-1 pattern of single invoice to single payment:
1. **Multi-Obligation Settlement:** A customer pays PKR 150,000 to settle two distinct invoices (e.g. PKR 70,000 and PKR 80,000), or a business pays a supplier PKR 200,000 to cover multiple vendor bills in a single bank transfer.
2. **Overpayments & Advances:** A customer overpays (e.g. sends PKR 120,000 for a PKR 100,000 invoice). The remaining PKR 20,000 must be retained as an unapplied customer credit balance (advance) and applied to future sales without creating duplicate revenue.
3. **Upfront Advances:** A customer pays a deposit before any invoice is created, or a business pays a vendor deposit before delivery.
4. **Advance Applications:** An unapplied credit balance is applied to settle an obligation. This settlement occurs **without posting any new cash movements** to the general ledger, since the cash was already accounted for at the time of advance creation.
5. **Reversals with Advances:** Reversing a cash payment that created an unapplied advance must safely cancel all linked allocations and the unapplied advance atomically. If the advance has already been applied downstream to other obligations, reversal is rejected with an actionable error requiring downstream applications to be reversed first.
6. **Payment Planning & Scheduling:** Businesses schedule future installment plans for obligations (e.g. paying an equipment bill in 3 installments). These schedules provide liquidity forecasting on a payment calendar without posting accounting entries or premature liabilities.

---

## 2. Invariants & Accounting Rules

| Invariant | Description | Enforcing Mechanism |
| :--- | :--- | :--- |
| **Single Cash Movement Authority** | Exactly one `FinancialTransaction` and one `AccountMovement` are created per cash payment event, regardless of how many obligations are settled. | `PaymentAllocationService.java` creates one `FinancialTransaction` and one `AccountMovement`, linking multiple `ObligationPaymentAllocation` records to it. |
| **Advance Application Zero Cash** | Applying an unapplied advance to an obligation creates **ZERO** financial transactions and **ZERO** account movements. | `AdvanceService.applyAdvance` creates an `ObligationPaymentAllocation` with `allocationType = ADVANCE` and `financialTransactionId = NULL`. |
| **No Revenue / Expense Recognition** | Cash collections/settlements and advances do NOT recognize revenue or operating expenses (`isRevenue = false`, `isOperatingExpense = false`). Revenue/expense is recognized at invoice/bill/monthly record level. | `FinancialTransaction` records use `RECEIVABLE_COLLECTION` or `PAYABLE_SETTLEMENT` with `isRevenue = false` and `isOperatingExpense = false`. |
| **Derived Unapplied Balances** | Unapplied advance balances are never manually edited or stored statically. They are computed dynamically: $\text{unapplied} = \text{originalAmount} - \sum(\text{effectiveAllocations})$. | `CounterpartyAdvanceRepository.sumEffectiveAllocationsByAdvanceId` and service calculations derive unapplied balances at runtime. |
| **Deadlock-Free Locking** | When settling multiple obligations concurrently, obligations are locked in deterministic order (`UUID::compareTo`) before acquiring account locks. | `PaymentAllocationService.allocatePayment` sorts unique obligation UUIDs and uses `findForUpdateByIdAndBusinessId`. |
| **Reversal Dependency Protection** | A payment that produced an unapplied advance cannot be reversed if that advance has active downstream applications. | `TransactionReverseService.cancelAllocationForReversal` checks for active downstream allocations and throws `TransactionValidationException`. |
| **Non-Posting Schedules** | Payment plans and installments are purely informational; they never create ledger transactions, debts, or journal entries. | `PaymentPlanService` writes strictly to `obligation_payment_schedules` without touching `financial_transactions` or `account_movements`. |
| **PKR Currency Scale 2** | All amounts use `BigDecimal` with scale 2 (`0.00`). Negative amounts and zero values are rejected for payments and allocations. | Input DTO annotations and domain service boundary checks enforce positive amounts $\le 999,999,999,999.99$. |

---

## 3. Database Schema (`V27`)

### 3.1 Table: `counterparty_advances`
Stores customer deposits (receivable advances) and supplier deposits (payable advances).
- `id`: UUID (Primary Key)
- `business_id`: UUID (Tenant scope)
- `counterparty_id`: UUID (FK to `counterparties`)
- `financial_transaction_id`: UUID (FK to `financial_transactions`)
- `direction`: VARCHAR(32) (`RECEIVABLE` / `PAYABLE`)
- `original_amount`: NUMERIC(14,2) NOT NULL
- `currency`: VARCHAR(3) DEFAULT 'PKR'
- `effective_date`: DATE NOT NULL
- `status`: VARCHAR(32) NOT NULL (`ACTIVE`, `EXHAUSTED`, `CANCELLED`)
- `notes`: TEXT
- `created_at`, `updated_at`: TIMESTAMPTZ

### 3.2 Upgraded Table: `obligation_payment_allocations`
Upgraded to support both cash settlements and advance applications.
- `allocation_type`: VARCHAR(32) DEFAULT 'CASH' (`CASH`, `ADVANCE`)
- `advance_id`: UUID NULL (FK to `counterparty_advances`)
- `financial_transaction_id`: UUID NULL (nullable for `ADVANCE` allocations)
- `ck_obligation_allocations_source`: Check constraint ensuring `(allocation_type = 'CASH' AND financial_transaction_id IS NOT NULL AND advance_id IS NULL) OR (allocation_type = 'ADVANCE' AND advance_id IS NOT NULL AND financial_transaction_id IS NULL)`.

### 3.3 Table: `obligation_payment_schedules`
Stores non-posting installment schedules for obligations.
- `id`: UUID (Primary Key)
- `business_id`: UUID (Tenant scope)
- `obligation_id`: UUID (FK to `financial_obligations`)
- `installment_number`: INT NOT NULL
- `expected_date`: DATE NOT NULL
- `expected_amount`: NUMERIC(14,2) NOT NULL
- `status`: VARCHAR(32) NOT NULL (`PLANNED`, `COMPLETED`, `CANCELLED`)
- `notes`: TEXT

---

## 4. API Endpoints

### 4.1 Payment Allocation (`/api/finance/payments`)
- `POST /api/finance/payments/allocate`: Allocate single cash payment across 1 or more obligations with optional residual advance retention (`retainAdvance = true`).
- `GET /api/finance/payments/{id}`: Fetch payment allocation details, linked transaction, allocated obligations, and residual advance.

### 4.2 Counterparty Advances (`/api/finance/advances`)
- `POST /api/finance/advances`: Record standalone customer or supplier advance.
- `GET /api/finance/advances`: List advances with filtering (`direction`, `status`, `counterpartyId`) and derived unapplied balances.
- `GET /api/finance/advances/{id}`: Get advance details including full application history.
- `POST /api/finance/advances/{id}/apply`: Apply unapplied advance credit to an open obligation (zero cash movement).
- `POST /api/finance/advances/{id}/applications/{applicationId}/reverse`: Reverse an advance application (restores unapplied advance and obligation outstanding without cash movement).

### 4.3 Payment Plans & Calendar (`/api/finance/obligations` & `/api/finance/payment-calendar`)
- `GET /api/finance/obligations/{id}/payment-plan`: Get payment plan installments and coverage status.
- `PUT /api/finance/obligations/{id}/payment-plan`: Create or replace payment plan for an obligation.
- `GET /api/finance/payment-calendar`: Query payment calendar across range (`direction`, `from`, `to`), aggregating scheduled installments and due obligations with overdue and upcoming KPI metrics.

---

## 5. Verification & Test Evidence

### 5.1 Backend Verification Suite
- `PaymentCyclesPostgreSqlIT.java`:
  - **Workflow A:** Multi-obligation payment allocation (1 cash transaction, 2 allocations, accurate outstanding balances).
  - **Workflow B:** Customer advance receipt and later application (0 extra movements).
  - **Workflow C:** Supplier advance payment and later application.
  - **Workflow D:** Payment with unapplied residual creates advance automatically.
  - **Workflow E:** Reversal of multi-obligation payment reverses cash once and cancels all allocations.
  - **Workflow F:** Advance application correction / reversal restores balance without cash movement.
  - **Workflow G:** Originating cash payment reversal blocked when advance has active downstream applications.
  - **Workflow H:** Payment scheduling and calendar aggregation (0 financial transactions).
  - **Workflow I:** Idempotency replay and conflict detection.
  - **Part VII Exact Arithmetic:** 100k cash, 40k + 35k allocated, explicit 25k advance; rejects residual when retainAdvance=false.
  - **Part VII Calendar Double-Counting Protection:** 150k payable, 3x 50k installments -> projected outflow 150,000.00 (not 300k).
  - **Part VII Historical Corrections:** Jan 1 advance 50k, Jan 10 apply 30k, Feb 5 reversal -> unapplied balance restored, CANCELLED audit trail preserved.
  - **Part VII Concurrency (V1 vs V2):** V1 single settlement racing V2 multi settlement on same obligation -> deterministic locking, 0 negative balances, 0 over-allocations.
  - **Part VII Concurrency (Advance Applications):** Two concurrent applications racing against single advance -> unapplied balance never negative.
  - **Part VII Concurrency (Idempotency):** Identical idempotency key requested concurrently -> exact 1 record in database, replay safety.
- **Suite Result:** 15/15 tests passing, 0 failures, 0 errors on PostgreSQL 14.22 with Flyway V27 (`BUILD SUCCESS`, 17.21s).
- **Full Obligation & Payment Suite:** 78/78 tests passing, 0 failures (`BUILD SUCCESS`, 29.01s):
  - `AdvanceControllerTest`: 3 tests
  - `PaymentCalendarControllerTest`: 1 test
  - `PaymentControllerTest`: 2 tests
  - `FinancialObligationControllerTest`: 9 tests
  - `FinancialObligationServiceTest`: 21 tests
  - `ObligationAgingCalculatorTest`: 19 tests
  - `FinancialObligationPostgreSqlIT`: 8 tests
  - `PaymentCyclesPostgreSqlIT`: 15 tests

### 5.2 Frontend Verification Suite
- `npm test`: 216/216 unit and contract tests passing (including 10 dedicated payment cycles and advance lifecycle tests).
- `npx tsc --noEmit`: 0 errors (EXIT 0).
- `npm run lint`: 0 errors, 12 warnings (EXIT 0).
- `npm run build`: Production build succeeded with Turbopack (`BACKEND_API_URL=http://127.0.0.1:8280`, EXIT 0).

### 5.3 Real Microsoft Edge Browser Acceptance (accounts-live.integration.mjs --payment-cycles-only)
- **Runtime Environment:**
  - Microsoft Edge: `155.0.4283.62` (headless)
  - Backend: Real Spring Boot on port 8680
  - Frontend: Real Next.js production standalone on port 3581
  - Gateway: Real transparent proxy on port 3580
  - Database: Real disposable PostgreSQL 14.22 with Flyway V27
- **Executed Journeys:**
  - **JOURNEY A (PASS):** Multi-receivable payment via browser UI modal (100k cash -> 60k to REC-MULTI-A1, 40k to REC-MULTI-A2). Verified exactly 1 `FinancialTransaction` (`RECEIVABLE_COLLECTION`), exactly 1 `AccountMovement` (+100k), 0 revenue recognized, both obligations FULLY_PAID.
  - **JOURNEY B (PASS):** Multi-payable payment via browser UI modal (150k cash -> 70k to BILL-MULTI-B1, 80k to BILL-MULTI-B2). Verified exactly 1 `FinancialTransaction` (`PAYABLE_SETTLEMENT`), exactly 1 `AccountMovement` (-150k), 0 duplicate expense recognized, both bills FULLY_PAID.
  - **JOURNEY C (PASS):** Customer advance receipt recorded via modal (50k cash). Verified 1 `FinancialTransaction`, 1 `AccountMovement`, 50k unapplied balance, status ACTIVE, no fabricated obligation, no revenue recognized.
  - **JOURNEY D (PASS):** Advance application via modal against 120k receivable. Verified obligation outstanding reduced to 70k, advance status EXHAUSTED (0 unapplied), and **CRITICAL INVARIANT: ZERO new FinancialTransactions, ZERO new AccountMovements**.
  - **JOURNEY E (PASS):** Supplier advance recorded (30k cash moved once), applied to 80k bill via modal -> 50k outstanding, **ZERO additional cash movements**.
  - **JOURNEY F (PASS):** Reversals:
    1. Multi-obligation payment reversed: 1 compensating cash transaction created, all linked allocations cancelled, outstanding balances and UNPAID statuses restored.
    2. Advance application reversed: advance unapplied balance restored to 50k, obligation outstanding restored to 120k, 0 cash movements.
    3. Reversal dependency protection: Attempting to reverse an originating cash payment when an associated advance has downstream applications is blocked with HTTP 400 (`active downstream applications`).
  - **JOURNEY G (PASS):** Payment calendar and installment scheduling (3x 50k full coverage plan on 150k bill). Verified calendar displays installments, and **ZERO financial transactions posted by payment plans**.
  - **JOURNEY H (PASS):** Authorization and security matrix: VIEWER read-only (modal buttons omitted, direct mutations return 403), MANAGER allocation returns 403, cross-business isolation returns 404, CSRF token missing returns 403.
  - **JOURNEY I (PASS):** Interface quality: Bilingual Urdu RTL (`dir="rtl"`) on Advances and Calendar, responsive mobile viewport (390×844) with zero horizontal scroll overflow (`document.documentElement.scrollWidth <= innerWidth`), keyboard accessibility.
- **Result:** `EXIT 0`, 0 blockers.
- **Artifacts:**
  - `frontend/test-artifacts/accounts-live/payment-cycles-results.json` (`integrated: true`)
  - Screenshots:
    - `journey_a_initial.png`, `journey_a_completed.png`
    - `journey_c_advances_page.png`, `journey_c_advance_created.png`
    - `journey_d_advance_applied.png`
    - `journey_g_calendar_page.png`
    - `journey_i_advances_urdu_rtl.png`, `journey_i_calendar_urdu_rtl.png`
    - `journey_i_mobile_390x844.png`
