# Financial Obligations & Payment Allocations Specification & Contract

**Feature:** `A-F03-OBLIGATIONS-V1` (Receivables, Payables, Partial Settlements, Payment Allocations, Aging & Connected Records Workspace)
**Date:** 19 October 2026 (Asia/Karachi)
**Author:** Antigravity (Developer A Backend & Git Coordinator)
**Status:** **INTEGRATION_VERIFIED**
**Allocated Migration:** `V26__create_financial_obligations.sql`

---

## 1. Feature Purpose & Scope

### 1.1 Objective
Provide an authoritative, multi-tenant financial obligation engine managing canonical receivables and payables in PKR (scale 2). It provides full partial settlement support, deterministic allocation tracking, non-duplicative cash accounting, 7-bucket historical aging, settlement reversal handling, and full Next.js bilingual workspace integration.

### 1.2 Non-Duplication & Boundaries
1. **Developer A Scope (Authoritative Financial Engine):**
   - Canonical obligations (`RECEIVABLE`, `PAYABLE`).
   - Settlement cash posting (`RECEIVABLE_COLLECTION`, `PAYABLE_SETTLEMENT`).
   - Payment allocations (`obligation_payment_allocations`).
   - Deterministic 7-bucket aging (`com.app.sme_health_backend.obligation.aging.ObligationAgingCalculator`).
   - Reversal compensation & allocation cancellation.
   - Connected workspace (`/records/receivables`, `/records/payables`).
2. **Developer B (Fatima Scope):**
   - Commercial document models (invoices, supplier bills, line items, VAT/sales tax rules, inventory costing).
   - FinSight's obligation architecture provides loose references (`sourceType`, `sourceId`) for future Fatima commercial document linkage.

### 1.3 Strict Financial Invariants
1. **Four Independent Financial Dimensions:**
   - **Obligation:** Commercial balance due (`financial_obligations`).
   - **Recognition:** Revenue / Expense recognition in Monthly Records (`monthly_records`).
   - **Cash Settlement:** Cash movement in/out of liquid accounts (`financial_transactions`, `account_movements`).
   - **Allocation:** Deterministic linkage between settlement transaction and obligation (`obligation_payment_allocations`).
2. **Non-Duplicative Cash Accounting:**
   - Settlement cash collections (`RECEIVABLE_COLLECTION`) and payments (`PAYABLE_SETTLEMENT`) have `isRevenue() = false` and `isOperatingExpense() = false`.
   - Money received / paid affects liquid balances, but never inflates or duplicates revenue / expense totals in financial summaries.
3. **PKR Scale 2 & Positive Quantities:**
   - Strictly positive amounts in `PKR` scale 2 up to capacity `999,999,999,999.99`.
4. **Deterministic Concurrency & Lock Ordering:**
   - In `recordSettlement`: Obligation row is locked first (`PESSIMISTIC_WRITE`), followed by Account row. This eliminates deadlock risk with concurrent transfers and settlements.
   - Durable idempotency checking executes before comparing amount with outstanding balance, guaranteeing clean replays for completed settlements.
5. **Reversals:**
   - Reversing a settlement transaction restores the obligation's outstanding balance and marks the allocation record `CANCELLED`, preserving complete audit history.

---

## 2. Database Schema (Flyway V26)

Allocated in `backend/src/main/resources/db/migration/V26__create_financial_obligations.sql`:

```sql
-- 1. Extend transaction event types
ALTER TABLE financial_transactions
    DROP CONSTRAINT IF EXISTS chk_financial_transactions_event_type;

ALTER TABLE financial_transactions
    ADD CONSTRAINT chk_financial_transactions_event_type CHECK (
        event_type IN (
            'CAPITAL_INJECTION', 'DIRECT_SALE_RECEIPT', 'OTHER_INCOME_RECEIPT',
            'RECEIVABLE_COLLECTION', 'OPERATING_EXPENSE_PAYMENT', 'TAX_PAYMENT',
            'OWNER_DRAWING', 'INTERNAL_TRANSFER', 'PAYABLE_SETTLEMENT'
        )
    );

-- 2. Financial Obligations Table
CREATE TABLE IF NOT EXISTS financial_obligations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL,
    direction VARCHAR(20) NOT NULL,
    counterparty_id UUID NOT NULL,
    source_type VARCHAR(50) NOT NULL DEFAULT 'MANUAL_DECLARATION',
    source_id VARCHAR(100),
    reference_number VARCHAR(100),
    original_amount NUMERIC(19,4) NOT NULL,
    paid_amount NUMERIC(19,4) NOT NULL DEFAULT 0.0000,
    outstanding_amount NUMERIC(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'PKR',
    issue_date DATE NOT NULL,
    due_date DATE,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    settlement_status VARCHAR(20) NOT NULL DEFAULT 'UNPAID',
    void_reason VARCHAR(500),
    void_date DATE,
    description VARCHAR(1000),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_obligations_business FOREIGN KEY (business_id)
        REFERENCES businesses(id) ON DELETE CASCADE,
    CONSTRAINT fk_obligations_counterparty FOREIGN KEY (counterparty_id, business_id)
        REFERENCES counterparties(id, business_id) ON DELETE RESTRICT,
    CONSTRAINT chk_obligations_direction CHECK (direction IN ('RECEIVABLE', 'PAYABLE')),
    CONSTRAINT chk_obligations_status CHECK (status IN ('OPEN', 'VOIDED')),
    CONSTRAINT chk_obligations_settlement_status CHECK (settlement_status IN ('UNPAID', 'PARTIALLY_PAID', 'FULLY_PAID')),
    CONSTRAINT chk_obligations_currency CHECK (currency = 'PKR'),
    CONSTRAINT chk_obligations_amounts CHECK (
        original_amount > 0 AND
        paid_amount >= 0 AND
        outstanding_amount >= 0 AND
        paid_amount + outstanding_amount = original_amount
    ),
    CONSTRAINT chk_obligations_dates CHECK (due_date IS NULL OR due_date >= issue_date),
    CONSTRAINT uq_obligations_id_business UNIQUE (id, business_id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_obligations_business_source
    ON financial_obligations (business_id, source_type, source_id)
    WHERE source_id IS NOT NULL;

-- 3. Payment Allocations Table
CREATE TABLE IF NOT EXISTS obligation_payment_allocations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL,
    obligation_id UUID NOT NULL,
    transaction_id UUID NOT NULL,
    amount NUMERIC(19,4) NOT NULL,
    effective_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ALLOCATED',
    reversal_transaction_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_allocations_business FOREIGN KEY (business_id)
        REFERENCES businesses(id) ON DELETE CASCADE,
    CONSTRAINT fk_allocations_obligation FOREIGN KEY (obligation_id, business_id)
        REFERENCES financial_obligations(id, business_id) ON DELETE RESTRICT,
    CONSTRAINT fk_allocations_transaction FOREIGN KEY (transaction_id, business_id)
        REFERENCES financial_transactions(id, business_id) ON DELETE RESTRICT,
    CONSTRAINT fk_allocations_reversal_tx FOREIGN KEY (reversal_transaction_id, business_id)
        REFERENCES financial_transactions(id, business_id) ON DELETE RESTRICT,
    CONSTRAINT chk_allocations_amount CHECK (amount > 0),
    CONSTRAINT chk_allocations_status CHECK (status IN ('ALLOCATED', 'CANCELLED')),
    CONSTRAINT uq_allocations_id_business UNIQUE (id, business_id)
);

-- 4. Runtime Database Least-Privilege Grants
REVOKE DELETE ON TABLE financial_obligations FROM finsight_app;
REVOKE DELETE ON TABLE obligation_payment_allocations FROM finsight_app;
GRANT SELECT, INSERT, UPDATE ON TABLE financial_obligations TO finsight_app;
GRANT SELECT, INSERT, UPDATE ON TABLE obligation_payment_allocations TO finsight_app;
```

---

## 3. Normative REST API Contract

Base URL: `/api/finance/obligations`

### 3.1 List Obligations
- `GET /api/finance/obligations?direction=RECEIVABLE&status=OPEN&settlementStatus=UNPAID&search=...&page=0&size=20`
- **RBAC:** `FINANCIAL_TRANSACTION_READ` (`OWNER`, `ACCOUNTANT`, `MANAGER`, `VIEWER`)
- **Returns:** Paginated `ObligationListResponse`.

### 3.2 Get Obligation Detail
- `GET /api/finance/obligations/{id}`
- **RBAC:** `FINANCIAL_TRANSACTION_READ`
- **Returns:** `ObligationDetailResponse` including array of allocations.

### 3.3 Create Obligation
- `POST /api/finance/obligations`
- **RBAC:** `FINANCIAL_TRANSACTION_POST` (`OWNER`, `ACCOUNTANT`)
- **Zero financial mutation:** Does not alter cash or monthly records.

### 3.4 Settle Obligation
- `POST /api/finance/obligations/{id}/settlements`
- **Headers:** `Idempotency-Key: <client-uuid>`
- **RBAC:** `FINANCIAL_TRANSACTION_POST` (`OWNER`, `ACCOUNTANT`)
- **Body:** `{ "accountId": "...", "amount": "40000.00", "settlementDate": "2026-10-19", "reference": "..." }`
- **Posting:**
  - `RECEIVABLE`: Posts `RECEIVABLE_COLLECTION` (`INCREASE` cash, `isRevenue=false`).
  - `PAYABLE`: Posts `PAYABLE_SETTLEMENT` (`DECREASE` cash, `isOperatingExpense=false`).

### 3.5 Void Obligation
- `POST /api/finance/obligations/{id}/void`
- **RBAC:** `FINANCIAL_TRANSACTION_POST` (`OWNER`, `ACCOUNTANT`)
- **Body:** `{ "reason": "Duplicate invoice entered in error" }`
- **Condition:** Allowed only when `paidAmount == 0.00`.

### 3.6 Obligation Aging & Summary Totals
- `GET /api/finance/obligations/summary?asOfDate=2026-10-19`
- `GET /api/finance/obligations/aging?asOfDate=2026-10-19`
- **RBAC:** `FINANCIAL_TRANSACTION_READ`
- **Returns:** 7 canonical aging buckets (`UNKNOWN_DUE_DATE`, `NOT_YET_DUE`, `DUE_TODAY`, `OVERDUE_1_30`, `OVERDUE_31_60`, `OVERDUE_61_90`, `OVERDUE_91_PLUS`).

---

## 4. Frontend Workspace Implementation

- **API Adapter:** `frontend/src/lib/api/dev-a/obligations.ts`
- **Bilingual Copy:** `frontend/src/components/records/obligations/obligation-copy.ts` (100% EN/UR parity).
- **Workspace Routes:**
  - `/records/receivables`
  - `/records/payables`
  - `/records` (with dedicated Receivables & Payables tabs)
- **UI Components:**
  - `ObligationsWorkspace.tsx`: Top metrics, filter controls, data table, detail sheet trigger.
  - `ObligationAgingCard.tsx`: 7 responsive aging buckets with color-coded overdue stages.
  - `ObligationModal.tsx`: Creation modal with `CounterpartySelector` integration.
  - `ObligationSettlementModal.tsx`: Settlement modal with eligible PKR account selector & Max helper.
  - `ObligationVoidModal.tsx`: Destructive void modal with mandatory reason prompt.
  - `ObligationDetailSheet.tsx`: Side sheet with full allocation transaction history.
