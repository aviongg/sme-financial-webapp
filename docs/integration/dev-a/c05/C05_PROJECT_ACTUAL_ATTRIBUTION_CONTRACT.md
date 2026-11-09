# C05 Project Actual Financial Attribution & Cost Evidence Contract

**Version:** `1.0.0`
**Status:** `ACTIVE / AUTHORITATIVE`
**Date:** 9 November 2026
**Author:** Google Antigravity (Sole Full-Stack Developer A Engineer & Financial Systems Architect)
**Collaborating Domains:** Developer B (Project Planning F05, Commercial Documents F04, Cost Estimation F06/F09)

---

## 1. Domain Boundary & Core Principles

FinSight maintains a strict separation of concerns between project planning and authoritative financial accounting:

| Responsibility | Developer B (F05 / Commercial) | Developer A (C05 / Financial Core) |
| :--- | :--- | :--- |
| **Domain Scope** | Project definition, codes, metadata, and planning lifecycle.<br>Budget revisions (`DRAFT`, `APPROVED`, `SUPERSEDED`) and budget lines.<br>Commercial pre-order estimates and customer quotation context. | Real posted financial transactions and account movements.<br>Financial obligations (receivables/payables) and allocations.<br>Salesperson commission cost evidence and FX settlement evidence.<br>Authoritative attribution of financial source evidence to projects.<br>Actual cash outflow/inflow totals, headroom, and financial costing summaries. |
| **Financial Nature** | **STRICTLY PLANNED ONLY.** No actual cash movement, ledger posting, or health score mutation. | **POSTED FINANCIAL EVIDENCE.** Real subledger movements, cash disbursements, and recognized costs. |
| **Immutability Invariant** | `project_budget_versions` and `project_budget_lines` are never mutated by actual transactions. | Project attributions reference immutable financial events. Attributions can be reversed/offset but never silently erased. |

### Strict Non-Duplication Boundaries
1. **No Silent Mutation of Budgets:** Actual transactions never overwrite or adjust planned line amounts in `project_budget_lines`.
2. **Cash Outflow vs. Economic Cost:** Cash payments are recorded as `POSTED_CASH_ACTUAL`. Recognized salesperson commissions are recorded as `RECOGNIZED_ECONOMIC_COST`. Realized FX differences are recorded as `NONCASH_FINANCIAL_EVIDENCE`. These categories are never conflated into a single ambiguous number.
3. **No Fabricated Profitability:** Project net margin / gross profit is displayed **only** when both verified recognized revenue and adequate recognized cost basis exist. Missing cost categories are never assumed to be zero.
4. **Split Allocation Authority:** One financial source (e.g. a PKR 100,000 supplier payment) may be allocated across multiple projects (e.g. PKR 60,000 to Project A, PKR 40,000 to Project B), provided the sum of active attributions does not exceed the source's eligible amount.

---

## 2. Financial Source Eligibility Policy

Only verified, posted financial records may be attributed to a project:

### 2.1 Eligible Financial Sources
*   **Posted Operating Expense Payments (`OPERATING_EXPENSE_PAYMENT`):** Direct cash payments for materials, labor, freight, or services.
*   **Posted Supplier Payable Settlements (`PAYABLE_SETTLEMENT`):** Cash payments settling commercial supplier bills.
*   **Posted Customer Sales Receipts (`DIRECT_SALE_RECEIPT`):** Cash receipts from direct customer sales.
*   **Posted Receivable Collections (`RECEIVABLE_COLLECTION`):** Cash collections against customer invoices.
*   **Recognized Commission Costs (`COMMISSION_RECOGNITION`):** Approved commission earnings published via `CommissionCostEvidenceFacade`.
*   **Realized FX Differences & Settlement Fees (`FX_DIFFERENCE`):** Bank fees and realized currency differences published via `FxFinancialEvidenceFacade`.

### 2.2 Ineligible Financial Sources
*   **Internal Transfers (`INTERNAL_TRANSFER`):** Bank-to-bank and cross-currency liquidity movements.
*   **Reversed Transactions:** Transactions whose `reversed_transaction_id` is populated or which have been reversed.
*   **Unposted Commercial Drafts:** Invoices or bills in `DRAFT` or pending review states.
*   **Planned Project Budget Items:** Hypothetical or forecasted costs.
*   **Advance Applications:** Deductions from customer or supplier deposit balances that do not represent new cash flows.
*   **Cross-Tenant Records:** Any source belonging to a different `business_id`.

---

## 3. Financial Attribution Data Model

Attributions are stored in the additive table `project_financial_attributions` (Flyway migration `V42`):

```sql
CREATE TABLE project_financial_attributions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    project_id UUID NOT NULL,
    source_type VARCHAR(32) NOT NULL
        CHECK (source_type IN ('TRANSACTION', 'OBLIGATION_SETTLEMENT', 'COMMISSION_RECOGNITION', 'FX_DIFFERENCE')),
    source_id UUID NOT NULL,
    source_direction VARCHAR(16) NOT NULL CHECK (source_direction IN ('INFLOW', 'OUTFLOW')),
    source_currency VARCHAR(3) NOT NULL,
    source_eligible_amount NUMERIC(19, 4) NOT NULL CHECK (source_eligible_amount > 0),
    attributed_amount NUMERIC(19, 4) NOT NULL CHECK (attributed_amount > 0),
    attributed_currency VARCHAR(3) NOT NULL,
    cost_category VARCHAR(16) NOT NULL
        CHECK (cost_category IN ('MATERIAL', 'LABOUR', 'OVERHEAD', 'FREIGHT', 'OTHER', 'COMMISSION', 'REVENUE')),
    economic_classification VARCHAR(32) NOT NULL
        CHECK (economic_classification IN ('POSTED_CASH_ACTUAL', 'RECOGNIZED_ECONOMIC_COST', 'NONCASH_FINANCIAL_EVIDENCE')),
    source_event_date DATE NOT NULL,
    attribution_date DATE NOT NULL,
    notes VARCHAR(500),
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'REVERSED', 'OFFSET')),
    reversal_reason VARCHAR(255),
    reversed_at TIMESTAMPTZ,
    reversed_by UUID REFERENCES app_users(id),
    idempotency_key VARCHAR(128),
    source_provenance VARCHAR(255),
    exchange_rate NUMERIC(19, 6),
    base_amount NUMERIC(19, 4) NOT NULL CHECK (base_amount > 0),
    created_by_user_id UUID NOT NULL REFERENCES app_users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_project_attributions_project
        FOREIGN KEY (business_id, project_id) REFERENCES projects (business_id, id) ON DELETE RESTRICT,
    CONSTRAINT uq_proj_attribution_biz_idempotency UNIQUE (business_id, idempotency_key)
);
```

### 3.1 Split Allocation Invariant
For any given financial source `(business_id, source_type, source_id)`:
$$\sum_{\text{status} = \text{'ACTIVE'}} \text{attributed\_amount} \le \text{source\_eligible\_amount}$$

*   **Concurrency Control:** The attribution service locks the source entity or attribution ledger during creation to prevent race conditions.
*   **Database Enforcement:** A PostgreSQL trigger (`chk_source_attribution_total`) raises an exception if an `INSERT` or `UPDATE` would cause the active allocated sum to exceed `source_eligible_amount`.

---

## 4. Financial Reporting & Budget Comparison Semantics

### 4.1 Comparison Measures
*   **Approved Budget Planned Cost:** Sum of lines from the single active `APPROVED` budget version for the project.
*   **Attributed Cash Outflow:** Sum of active `POSTED_CASH_ACTUAL` outflows attributed to the project.
*   **Cash-to-Plan Headroom:**
    $$\text{Cash Headroom} = \text{Planned Cost} - \text{Attributed Cash Outflows}$$
    *Note:* This indicates liquidity remaining under the planned budget, **not** net accounting profit.
*   **Recognized Commission Cost:** Sum of approved commission earnings attributed from `CommissionCostEvidenceFacade`.
*   **Non-Cash FX Impact:** Sum of realized FX gains/losses and settlement fees attributed from `FxFinancialEvidenceFacade`.

### 4.2 Data Completeness Policy
*   If a project has no approved budget: Planned amounts are marked `UNAVAILABLE` (`reason = NO_APPROVED_BUDGET`).
*   If recognized costs are missing or incomplete: Project profit and margin are marked `UNAVAILABLE` (`reason = INCOMPLETE_COST_EVIDENCE`).
*   Zero missing costs: An unspent category in cash outflows is reported with actual = `0.0000`, but headroom is explicitly qualified as cash-based.

---

## 5. Security & Tenant Isolation

1.  **Strict Business Scoping:** Every query and mutation checks `business_id` from the server-resolved `ActiveBusinessContext`.
2.  **Role-Based Access Control:**
    *   `OWNER`: Full attribution management and reporting.
    *   `ACCOUNTANT`: Full attribution management and reporting.
    *   `MANAGER`: Read-only access to costing summaries and attributions (`BusinessPermission.FINANCIAL_DATA_READ`).
    *   `VIEWER`: Read-only access to costing summaries and attributions.
    *   `SALESPERSON`: **Denied** (HTTP 403 Forbidden). No business-wide financial costing access.

---

## 6. C05B Project Financial Commitments & Commercial Document Linkage

### 6.1 Domain Boundary & Non-Posting Discipline
*   **Commercial Documents (Invoices/Bills):** Belong to Developer B's F04 module. They carry `financial_effect = 'NONE'`. Linking a commercial bill to a project confers **commercial visibility only**. It does **NOT** post journal entries, ledger debits/credits, or mutate account balances.
*   **Financial Obligations (Receivables/Payables):** Belong to Developer A's C03/C04 subledger. Linking an obligation provides **binding financial commitment evidence**.
*   **Soft Append-Only Unlinking:** Physical `DELETE` privileges are explicitly revoked for `finsight_app` on `project_financial_attributions`, `project_financial_obligation_links`, and `project_commercial_document_links`. Unlinking sets `link_status = 'UNLINKED'`, recording `unlinked_at`, `unlinked_by`, and `unlink_reason`.

### 6.2 Data Models (Flyway Migration `V43`)

```sql
-- Commercial Document Linkage (F04 visibility)
CREATE TABLE project_commercial_document_links (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    project_id UUID NOT NULL,
    document_id UUID NOT NULL,
    document_kind VARCHAR(32) NOT NULL CHECK (document_kind IN ('INVOICE', 'BILL')),
    document_number VARCHAR(64) NOT NULL,
    counterparty_name VARCHAR(255),
    document_date DATE NOT NULL,
    due_date DATE,
    document_total NUMERIC(19, 4) NOT NULL CHECK (document_total >= 0),
    allocated_amount NUMERIC(19, 4) NOT NULL CHECK (allocated_amount > 0),
    currency VARCHAR(3) NOT NULL,
    link_status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' CHECK (link_status IN ('ACTIVE', 'UNLINKED')),
    link_provenance VARCHAR(255),
    notes VARCHAR(500),
    created_by_user_id UUID NOT NULL REFERENCES app_users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    unlinked_at TIMESTAMPTZ,
    unlinked_by UUID REFERENCES app_users(id),
    unlink_reason VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_pcdl_project FOREIGN KEY (business_id, project_id) REFERENCES projects(business_id, id) ON DELETE RESTRICT
);

-- Financial Obligation Linkage (C03/C04 commitments)
CREATE TABLE project_financial_obligation_links (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    project_id UUID NOT NULL,
    obligation_id UUID NOT NULL,
    direction VARCHAR(16) NOT NULL CHECK (direction IN ('RECEIVABLE', 'PAYABLE')),
    original_amount NUMERIC(19, 4) NOT NULL CHECK (original_amount > 0),
    allocated_amount NUMERIC(19, 4) NOT NULL CHECK (allocated_amount > 0),
    currency VARCHAR(3) NOT NULL,
    link_status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' CHECK (link_status IN ('ACTIVE', 'UNLINKED')),
    link_provenance VARCHAR(255),
    notes VARCHAR(500),
    created_by_user_id UUID NOT NULL REFERENCES app_users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    unlinked_at TIMESTAMPTZ,
    unlinked_by UUID REFERENCES app_users(id),
    unlink_reason VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_pfol_project FOREIGN KEY (business_id, project_id) REFERENCES projects(business_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_pfol_obligation FOREIGN KEY (obligation_id) REFERENCES financial_obligations(id) ON DELETE RESTRICT
);
```

### 6.3 Commitments & Cost Deduplication Invariants

1.  **Non-Duplication Rule:**
    When an obligation has partial or full settlements already attributed as cash outflows under C05A:
    $$\text{Total Commitments} = \text{Outstanding Amount of Linked Payables} + \text{Unposted Commercial Bills}$$
    The paid settlement amount ($\text{Settled Amount}$) is already included in $\text{Attributed Cash Outflows}$. It must **NOT** be added to commitments or double-deducted from headroom.
2.  **Matched Commercial Document Deduplication:**
    If a linked commercial bill is matched to a posted `financial_obligation` (matching `source_id`), it is flagged as `MATCHED_TO_OBLIGATION` and excluded from `unpostedCommercialBills` to prevent double-counting between commercial documents and subledger payables.
3.  **Headroom Calculation Invariant:**
    $$\text{Cash Headroom} = \text{Approved Planned Cost} - \text{Attributed Cash Outflows}$$
    $$\text{Total Commitments} = \text{Outstanding Payables} + \text{Unposted Commercial Bills}$$
    $$\text{Net Uncommitted Margin} = \text{Approved Planned Cost} - (\text{Cash Outflows} + \text{Total Commitments})$$

---

## 7. OCR Expense Evidence Integration (C05C)

See dedicated integration contract: [`C05_OCR_PROJECT_EVIDENCE_CONTRACT.md`](./C05_OCR_PROJECT_EVIDENCE_CONTRACT.md).

### 7.1 Cardinal Rule
> **"OCR evidence is documentary proof, NOT cash authority."**
> Linking or matching OCR receipts never mutates cash accounts, creates ledger entries, or modifies payables/receivables. Cash actuals and headroom are dictated solely by posted financial transactions (`financial_transactions`).

### 7.2 Database Schema (Flyway V44)
- Table: `project_ocr_evidence_links`
- Statuses: `EXTRACTED_UNREVIEWED`, `REVIEWED_UNMATCHED`, `MATCHED_TO_POSTED_SOURCE`, `ATTRIBUTED`, `INVALIDATED`, `REVERSED`
- Security: `REVOKE DELETE ON project_ocr_evidence_links FROM finsight_app` (soft append-only invalidation).

### 7.3 Multi-Project Split & Discrepancy Warnings
- Proportional / multi-project linking is tracked with `alreadyLinkedProjectCount` and duplicate warning badges.
- Amount discrepancies between OCR receipts and posted transactions are flagged non-mutatingly (`hasDiscrepancy = true`, `discrepancyAmount = |ocr - tx|`). Neither amount is altered.
- Reversal of an underlying financial transaction automatically transitions linked OCR evidence to `REVERSED`.
