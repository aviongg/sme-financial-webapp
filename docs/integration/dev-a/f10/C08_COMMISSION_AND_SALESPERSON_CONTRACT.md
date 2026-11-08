# FinSight Authoritative Integration Contract C08: Salesperson & Commission Management

**Document Version:** `1.0.0`
**Feature Wave:** Wave 2 (F10 Salesperson & Commission Management)
**Task ID:** `DEV-A-F10-C08-FULLSTACK-01`
**Domain Owner:** Developer A (Google Antigravity — Sole Full-Stack Lead)
**Collaborating Domains:** Developer B (Commercial Documents F04, Project Cost Attribution C05)
**Date:** 8 November 2026
**Status:** AUTHORITATIVE SPECIFICATION — APPROVED FOR IMPLEMENTATION

---

## 1. Domain Ownership & Executive Context

In FinSight's Phase 2 architecture, commission management is an authoritative financial domain owned exclusively by Developer A. It governs the relationship between the business, its sales workforce, customer assignments, commission rules, calculated earnings, advance tracking, and cash payouts.

### Domain Boundaries
1. **Developer A (FinSight Finance & Git Lead):**
   - Salesperson profile management and business membership scoping.
   - Customer-to-salesperson assignment rules and row-level authorization.
   - Commission rule definition (percentage-of-sales, fixed-rate, effective-dated).
   - Commission calculation engine (`CommissionCalculator`).
   - Earning recognition, approval lifecycle, and settlement.
   - Commission advance tracking, allocation, and cash payout recording.
   - Authoritative commission data feeds for Developer B (read-only C08 query facade).
2. **Developer B (Fatima):**
   - Commercial document creation and line items (F04 Phase A non-posting).
   - Project profitability and cost aggregation (F05 / C05): consumes immutable recognized commission cost references published by Developer A; performs no independent commission calculations or salesperson access logic.

---

## 2. Financial Policy Architecture Decision Record (ADR-C08-01)

### ADR-C08-01: Commission Recognition Earning Basis & Accounting Safeguards

#### Context
SMEs utilize two primary models for salesperson compensation:
1. **Invoiced / Accrual-Based Sales:** Commission is earned immediately upon issuing an invoice or commercial sale order.
2. **Collection / Cash-Realized Sales:** Commission is earned only when cash is actually collected from the customer against an issued receivable.

Furthermore, FinSight's current ledger is structured around:
- Authoritative financial accounts (`financial_accounts`): Cash, Bank, Wallet.
- Double-entry cash movements (`account_movements`): `INCREASE` and `DECREASE`.
- Commercial counterparties (`counterparties`): `CUSTOMER`, `SUPPLIER`, `BOTH`.
- Financial obligations (`financial_obligations`): `RECEIVABLE` and `PAYABLE`.
- Commercial documents (`commercial_documents`, F04): Phase A documents are **non-posting** (`DRAFT -> FINALIZED -> VOID`) and do not mutate cash or generate general ledger journals.

#### Decision
1. **No Phantom General Ledger Accounts:** FinSight does not possess an arbitrary general ledger with open nominal accounts for `COMMISSION_EXPENSE` and `COMMISSION_PAYABLE`. We will not invent unbacked journal entries.
2. **Dual-Basis Earning Support:** The C08 engine supports both `COLLECTION_BASED` (earned on cash receipt against a customer's receivable obligation) and `SALES_BASED` (earned on authorized sale declaration or confirmed commercial delivery).
3. **Installment Guardrail:** For collection-based commission, when a customer pays in multiple installments, commission is calculated proportionally on each individual collection allocation. The source fingerprint combines `(obligation_id, allocation_id)` to guarantee that the same sale value is never over-commissioned.
4. **Advance Cash Integrity:**
   - Paying a commission advance posts a real cash outflow (`OPERATING_EXPENSE_PAYMENT` or dedicated `COMMISSION_ADVANCE_PAYMENT`), reducing cash once.
   - Applying an advance against an approved commission payout **moves zero cash**. It merely nets the payable amount.
   - An advance cannot be applied more than once, nor exceed the unapplied advance balance.
5. **Effective Dating Invariance:** Changing or archiving a commission rule never retroactively mutates previously earned commission records.

---

## 3. Database Schema Specification (Migration V38)

Migration `V38__create_salesperson_and_commission_foundation.sql` introduces additive schema entities under strict multi-tenant isolation.

```sql
-- 1. Extend business_memberships role check constraint to include SALESPERSON
ALTER TABLE business_memberships DROP CONSTRAINT IF EXISTS business_memberships_role_check;
ALTER TABLE business_memberships ADD CONSTRAINT business_memberships_role_check
    CHECK (role IN ('OWNER', 'ACCOUNTANT', 'MANAGER', 'VIEWER', 'SALESPERSON'));

-- 2. Salesperson Profiles
CREATE TABLE IF NOT EXISTS salesperson_profiles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    user_id UUID REFERENCES app_users(id) ON DELETE SET NULL,
    display_name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    phone VARCHAR(50),
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED')),
    notes VARCHAR(1000),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_salesperson_business_email UNIQUE (business_id, email),
    CONSTRAINT uq_salesperson_business_user UNIQUE (business_id, user_id)
);
CREATE INDEX IF NOT EXISTS idx_salesperson_business_status ON salesperson_profiles(business_id, status);

-- 3. Customer Sales Assignments
CREATE TABLE IF NOT EXISTS sales_assignments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    salesperson_id UUID NOT NULL REFERENCES salesperson_profiles(id) ON DELETE CASCADE,
    counterparty_id UUID NOT NULL REFERENCES counterparties(id) ON DELETE RESTRICT,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'INACTIVE')),
    effective_start_date DATE NOT NULL,
    effective_end_date DATE,
    assigned_by_user_id UUID NOT NULL REFERENCES app_users(id),
    notes VARCHAR(1000),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_active_customer_assignment UNIQUE NULLS NOT DISTINCT (business_id, counterparty_id, status)
);
CREATE INDEX IF NOT EXISTS idx_sales_assignments_sp ON sales_assignments(business_id, salesperson_id, status);

-- 4. Commission Rules
CREATE TABLE IF NOT EXISTS commission_rules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    salesperson_id UUID REFERENCES salesperson_profiles(id) ON DELETE CASCADE,
    rule_name VARCHAR(255) NOT NULL,
    calculation_type VARCHAR(32) NOT NULL
        CHECK (calculation_type IN ('PERCENTAGE', 'FIXED')),
    earning_basis VARCHAR(32) NOT NULL DEFAULT 'COLLECTION_BASED'
        CHECK (earning_basis IN ('COLLECTION_BASED', 'SALES_BASED')),
    rate_percentage NUMERIC(8, 4), -- e.g. 5.0000%
    fixed_amount NUMERIC(19, 4),    -- e.g. 2000.0000 PKR
    currency VARCHAR(3) NOT NULL DEFAULT 'PKR',
    effective_start_date DATE NOT NULL,
    effective_end_date DATE,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_commission_rules_lookup ON commission_rules(business_id, salesperson_id, status);

-- 5. Commission Earnings
CREATE TABLE IF NOT EXISTS commission_earnings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    salesperson_id UUID NOT NULL REFERENCES salesperson_profiles(id) ON DELETE CASCADE,
    rule_id UUID NOT NULL REFERENCES commission_rules(id) ON DELETE RESTRICT,
    counterparty_id UUID NOT NULL REFERENCES counterparties(id) ON DELETE RESTRICT,
    source_type VARCHAR(32) NOT NULL
        CHECK (source_type IN ('OBLIGATION_ALLOCATION', 'MANUAL_SALE_DECLARATION', 'COMMERCIAL_ORDER')),
    source_id VARCHAR(255) NOT NULL,
    source_fingerprint VARCHAR(255) NOT NULL,
    sale_amount NUMERIC(19, 4) NOT NULL,
    commission_rate_snapshot NUMERIC(8, 4),
    commission_amount NUMERIC(19, 4) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'PKR',
    status VARCHAR(32) NOT NULL DEFAULT 'EARNED'
        CHECK (status IN ('CALCULATED', 'EARNED', 'APPROVED', 'PARTIALLY_SETTLED', 'SETTLED', 'PAID', 'VOID', 'REVERSED')),
    approved_amount NUMERIC(19, 4),
    approved_at TIMESTAMPTZ,
    approved_by_user_id UUID REFERENCES app_users(id),
    paid_at TIMESTAMPTZ,
    notes VARCHAR(1000),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_commission_earning_source UNIQUE (business_id, source_fingerprint)
);
CREATE INDEX IF NOT EXISTS idx_commission_earnings_sp_status ON commission_earnings(business_id, salesperson_id, status);

-- 6. Commission Advances
CREATE TABLE IF NOT EXISTS commission_advances (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    salesperson_id UUID NOT NULL REFERENCES salesperson_profiles(id) ON DELETE CASCADE,
    amount NUMERIC(19, 4) NOT NULL,
    applied_amount NUMERIC(19, 4) NOT NULL DEFAULT 0.0000,
    currency VARCHAR(3) NOT NULL DEFAULT 'PKR',
    payment_date DATE NOT NULL,
    financial_account_id UUID REFERENCES financial_accounts(id) ON DELETE RESTRICT,
    financial_transaction_id UUID REFERENCES financial_transactions(id),
    idempotency_key VARCHAR(255),
    created_by_user_id UUID REFERENCES app_users(id),
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'FULLY_APPLIED', 'REFUNDED', 'VOID')),
    notes VARCHAR(1000),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_advance_amounts CHECK (applied_amount >= 0 AND applied_amount <= amount),
    CONSTRAINT uq_advance_idempotency UNIQUE (business_id, idempotency_key)
);
CREATE INDEX IF NOT EXISTS idx_commission_advances_sp ON commission_advances(business_id, salesperson_id, status);

-- 7. Commission Payouts and Advance Applications
CREATE TABLE IF NOT EXISTS commission_payouts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    salesperson_id UUID NOT NULL REFERENCES salesperson_profiles(id) ON DELETE CASCADE,
    earning_id UUID REFERENCES commission_earnings(id) ON DELETE RESTRICT,
    advance_id UUID REFERENCES commission_advances(id) ON DELETE RESTRICT,
    payout_type VARCHAR(32) NOT NULL
        CHECK (payout_type IN ('PAYOUT', 'ADVANCE_APPLICATION')),
    amount NUMERIC(19, 4) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'PKR',
    payout_date DATE NOT NULL,
    financial_account_id UUID REFERENCES financial_accounts(id) ON DELETE RESTRICT,
    financial_transaction_id UUID REFERENCES financial_transactions(id),
    idempotency_key VARCHAR(255),
    status VARCHAR(32) NOT NULL DEFAULT 'SETTLED'
        CHECK (status IN ('SETTLED', 'REVERSED')),
    notes VARCHAR(1000),
    created_by_user_id UUID REFERENCES app_users(id),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_payout_amount_positive CHECK (amount > 0),
    CONSTRAINT uq_commission_payout_idempotency UNIQUE (business_id, idempotency_key)
);
CREATE INDEX IF NOT EXISTS idx_commission_payouts_sp ON commission_payouts(business_id, salesperson_id, status);
CREATE INDEX IF NOT EXISTS idx_commission_payouts_earning ON commission_payouts(business_id, earning_id);
```

---

## 4. Role-Based & Row-Level Authorization Matrix

FinSight enforces dual-layer server-side authorization:
1. **Endpoint Permission Check:** Verified via `BusinessAuthorizationService.requirePermission()`.
2. **Row-Level Scope Resolution:** The server determines the active user's identity and membership. If the caller holds role `SALESPERSON`, their query predicate is locked to `salesperson_profiles.user_id = context.userId()`. Caller-supplied tenant or salesperson IDs intended to widen access are rejected.

| Capability | Allowed Business Roles | Scoping / Predicate |
| :--- | :--- | :--- |
| **Salesperson Directory (List/Create/Update)** | `OWNER`, `ACCOUNTANT`, `MANAGER` | Full business scope |
| **Salesperson Profile (View Self)** | `SALESPERSON`, `OWNER`, `ACCOUNTANT`, `MANAGER` | `SALESPERSON` locked to own profile |
| **Customer Assignment (Manage)** | `OWNER`, `ACCOUNTANT` | Full business scope |
| **Customer Assignment (View)** | `OWNER`, `ACCOUNTANT`, `MANAGER`, `SALESPERSON` | `SALESPERSON` sees only assigned customers |
| **Commission Rules (Manage)** | `OWNER`, `ACCOUNTANT` | Full business scope |
| **Commission Rules (View)** | `OWNER`, `ACCOUNTANT`, `MANAGER`, `SALESPERSON` | `SALESPERSON` sees only active rules applying to self |
| **Commission Earnings (Calculate/View)** | `OWNER`, `ACCOUNTANT`, `MANAGER`, `SALESPERSON` | `SALESPERSON` sees only own earnings |
| **Commission Earnings (Approve/Void)** | `OWNER`, `ACCOUNTANT` | Full business scope |
| **Commission Advances (Create/Pay)** | `OWNER`, `ACCOUNTANT` | Full business scope |
| **Commission Settlement (Payout)** | `OWNER`, `ACCOUNTANT` | Full business scope |

---

## 5. API Interface Specification

### A. Salesperson Profiles
- `POST /api/finance/salespersons`: Create a new salesperson profile (Owner/Accountant).
- `GET /api/finance/salespersons`: List all salesperson profiles in active business.
- `GET /api/finance/salespersons/{id}`: View salesperson profile.
- `GET /api/finance/salespersons/me`: Salesperson self-service profile endpoint.
- `PATCH /api/finance/salespersons/{id}/status`: Activate, deactivate, or suspend a salesperson.

### B. Customer Assignments
- `POST /api/finance/salespersons/{id}/assignments`: Assign a customer (`counterpartyId`) to a salesperson.
- `GET /api/finance/salespersons/{id}/assignments`: List customer assignments for a salesperson.
- `GET /api/finance/salespersons/my-assignments`: Salesperson self-service assigned customer directory.
- `DELETE /api/finance/salespersons/{id}/assignments/{assignmentId}`: Terminate or deactivate an assignment.

### C. Commission Rules
- `POST /api/finance/commissions/rules`: Create an effective-dated commission rule (percentage or fixed amount, global default or salesperson-specific).
- `GET /api/finance/commissions/rules`: List commission rules in active business.
- `PATCH /api/finance/commissions/rules/{id}/status`: Toggle rule status (`ACTIVE` or `ARCHIVED`).

### D. Eligible Collections Discovery & Calculation Preview
- `GET /api/finance/commissions/eligible-collections`: Discovers customer cash collections (`ObligationPaymentAllocation` with `allocationType == CASH` on `RECEIVABLE` obligations) attributed to active salesperson customer assignments, applies authoritative rule precedence, calculates estimated commissions, and flags `alreadyEarned`.
- `POST /api/finance/commissions/preview`: Deterministic calculation preview evaluating commission amount and effective rate based on specified parameters without mutating database state.

### E. Commission Earnings & Approvals
- `POST /api/finance/commissions/earnings`: Authoritatively recognizes a commission earning against a source collection allocation. Enforces deduplication via `(business_id, source_fingerprint)` returning `409 Conflict` on duplicate recognition attempts.
- `GET /api/finance/commissions/earnings`: List commission earnings with optional salesperson filter (salespersons restricted strictly to their own earnings).
- `GET /api/finance/commissions/my-earnings`: Salesperson self-service earnings history.
- `GET /api/finance/commissions/summary`: Authoritative summary cards (Total Earned, Total Approved, Total Paid Out, Total Advances Applied, Net Remaining Payable).
- `POST /api/finance/commissions/earnings/{id}/approve`: Approve earned commission for settlement specifying `approvedAmount` (moves zero cash).
- `GET /api/finance/commissions/earnings/pending-approval`: List earnings awaiting approval (`EARNED` status).

### F. Commission Advances, Payouts & Settlements (F10C)
- `POST /api/finance/commissions/advances`: Issue cash-backed advance payment to a salesperson (requires active PKR account, posts real cash outflow via `FinancialTransaction` and `AccountMovement`).
- `GET /api/finance/commissions/advances`: List advances and unapplied balances.
- `POST /api/finance/commissions/advances/{id}/apply`: Apply unapplied advance against approved commission (moves zero cash, creates `CommissionPayout` with `payoutType = ADVANCE_APPLICATION`).
- `POST /api/finance/commissions/payouts`: Record partial or final cash payout against approved commission (requires active PKR account, posts real cash outflow via `FinancialTransaction` and `AccountMovement`).
- `GET /api/finance/commissions/payouts`: List payouts across business with optional salesperson filter.
- `GET /api/finance/commissions/earnings/{id}/payouts`: List payout and advance application history for a specific earning.
- `POST /api/finance/commissions/payouts/{id}/reverse`: Reverse payout or advance application (posts compensating cash movement for payouts, un-applies advance, restores commission payable balance).
- `GET /api/finance/commissions/my-settlements`: Salesperson self-service settlements and advances history.
- `GET /api/finance/commissions/salespersons/{id}/settlement-summary`: Detailed settlement summary for a salesperson.

---

## 6. Implementation Status & Progress

1. **Slice F10A: Salesperson Identity, Assignments & Restricted Access — COMPLETED**
   - Migration V38 implemented: `business_memberships` role update, `salesperson_profiles`, `sales_assignments`, `commission_rules`, `commission_earnings`, `commission_advances`.
   - Domain entities, repositories, service, REST controller, DTOs.
   - Server-side row-level access control and suspended-membership immediate denial.
   - Frontend workspace: `/finance/commissions` with directory, customer assignment wizard, and salesperson self-view.
   - 26 backend tests + 8 real browser acceptance checks passed.

2. **Slice F10B: Commission Rules, Earnings Engine & Collection-Based Attribution — COMPLETED**
   - Pure `CommissionCalculationService` with exact `BigDecimal` scale-2 rounding (`HALF_UP`) and scale-4 percentage precision.
   - Rule precedence: Salesperson-specific active rule overrides business default rule; effective dating window strictly enforced.
   - Collection attribution: `ObligationPaymentAllocation` where `allocationType == CASH` on `RECEIVABLE` obligations attributed to salesperson if `paymentDate` falls within active customer assignment window.
   - Source fingerprinting deduplication: `OBLIGATION_ALLOCATION:<allocationId>` enforced at database level with unique constraint, surfacing HTTP 409 Conflict.
   - Zero phantom ledger accounts & ledger cash invariance: Earning recognition performs zero cash debits/credits or account movement mutations.
   - 58 backend tests + 389 frontend tests + 5 translation parity tests + 13 real browser HTTP integration journeys passed.

3. **Slice F10C: Commission Approvals, Advances, Payments & Financial Settlement — COMPLETED**
   - V38 schema additions: `commission_earnings.approved_amount`, extended status lifecycle (`APPROVED`, `PARTIALLY_SETTLED`, `SETTLED`, `REVERSED`), `commission_advances` financial linking, `commission_payouts` table with idempotency and constraints.
   - Zero-cash approvals (`approveEarning` with customizable approved amount).
   - Cash-backed advances (`createAdvance`) posting real double-entry cash movements (`OPERATING_EXPENSE_PAYMENT`, `MovementDirection.DECREASE`).
   - Zero-cash advance applications (`applyAdvance`) netting approved earnings without additional cash outflow.
   - Cash payouts (`recordPayout`) posting real double-entry cash movements and enforcing concurrency/overpayment rejection (HTTP 409 Conflict).
   - Compensating reversal workflow (`reversePayout`) restoring bank balance and commission payable amount.
   - Section 8 Mandatory Numerical Acceptance Fixture verified end-to-end.
   - 69 backend tests + 389 frontend tests + 5 translation parity tests + 20 real HTTP browser integration journeys passed.

4. **Slice F10D: Reporting, Bilingual Workspace & Developer B Handoff — NEXT**
   - Historical commission statements, mobile 390x844 responsive verification, Urdu RTL layout.
   - Published read-only query facade for Developer B project margin attribution.
