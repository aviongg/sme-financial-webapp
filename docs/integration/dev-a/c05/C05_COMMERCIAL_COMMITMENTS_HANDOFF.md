# FinSight Integration Contract: C05 Commercial Commitments & Non-Posting Boundary Handoff

**Document Reference:** `docs/integration/dev-a/c05/C05_COMMERCIAL_COMMITMENTS_HANDOFF.md`
**Version:** `1.0.0`
**Date:** 2026-11-09
**Owners:**
- **Developer A (Antigravity):** Financial Subledger, Obligations, Attributions & Commitments Coordinator
- **Developer B (Fatima):** Project Planning (F05) & Commercial Documents (F04) Domain Owner

---

## 1. Domain Ownership & Architectural Boundary

| Domain | Table / Entity | Domain Owner | Authority & Lifecycle Boundary |
|:---|:---|:---|:---|
| **Commercial Documents (F04)** | `commercial_documents`, `commercial_document_lines` | **Developer B** | Commercial draft/finalization lifecycle. Invoices and bills have **NON-POSTING** semantics (`financial_effect = 'NONE'`). Never mutates cash, bank, receivable, or payable ledgers. |
| **Project Planning (F05)** | `projects`, `project_budget_versions`, `project_budget_lines` | **Developer B** | Authoritative for project codes, planned revenue, planned costs, and planning cost categories (`MATERIAL`, `LABOR`, `EQUIPMENT`, `SUBCONTRACTOR`, `OTHER`). Status: `PLANNED_ONLY`. |
| **Project Commercial Document Links** | `project_commercial_document_links` | **Developer A** | Associates Developer B's commercial documents with projects for project commitment tracking. Additive table; Developer B's tables are never altered. |
| **Financial Obligations (C03)** | `financial_obligations`, `obligation_payment_allocations` | **Developer A** | Canonical subledger for receivables and payables, settlement allocations, and outstanding balances. |
| **Project Financial Obligation Links** | `project_financial_obligation_links` | **Developer A** | Associates verified financial obligations with projects, supporting split allocations with database-enforced ceiling triggers. |
| **Project Financial Attributions (C05A)** | `project_financial_attributions` | **Developer A** | Authoritative cash, commission, and FX attributions to projects. Immutable history with append-only reversals (`ACTIVE` $\to$ `REVERSED`). Physical `DELETE` revoked from `finsight_app`. |

---

## 2. Canonical Deduplication Chain

To prevent double counting across commercial documents, obligations, payments, and project attributions, FinSight implements the following economic chain:

$$\text{Commercial Document (F04)} \longrightarrow \text{Financial Obligation (C03)} \longrightarrow \text{Payment Allocation} \longrightarrow \text{Cash Transaction (C01)} \longrightarrow \text{Attribution (C05A)}$$

### Deduplication Rules:
1. **Commercial Document to Obligation Matching:**
   - If an active `financial_obligation` exists referencing a commercial document (via `source_type` and `source_id` matching `document_id` or `document_number`), the commercial document is classified as `MATCHED_TO_OBLIGATION`.
   - Matched commercial documents are excluded from `unpostedCommercialBills` / `unpostedCommercialInvoices` to prevent double-counting against the linked obligation.
   - Unmatched commercial documents remain classified as `COMMERCIAL_UNPOSTED` and are reported separately.
2. **Payment Allocation to Cash Attribution Matching:**
   - When an obligation is partially or fully settled via a financial transaction, that transaction may be attributed to the project under C05A.
   - If the transaction is attributed to the project, the paid amount is already reflected in `attributedCashOutflows`.
   - The obligation commitment reports only the remaining `outstandingAmount`. The paid portion is marked `hasAttributedPayment = true` and is not re-added to project costs.
3. **Cash Headroom Invariant:**
   $$\text{Cash Headroom} = \text{Planned Cost} - \text{Attributed Cash Outflows}$$
   Commitments and unposted documents do not reduce cash headroom until actual cash settlement is posted.

---

## 3. Split Allocation & Multi-Project Association Rules

A single supplier payable or commercial bill may service multiple projects. FinSight supports proportional split allocations under strict invariants:

1. **Obligation Split Limit:**
   $$\sum_{\text{active links}} \text{allocated\_amount} \le \text{obligation.original\_amount}$$
   Enforced at both service layer and PostgreSQL trigger (`trg_chk_project_obligation_limit`).
2. **Commercial Document Split Limit:**
   $$\sum_{\text{active links}} \text{allocated\_amount} \le \text{document.total}$$
   Enforced at service layer.
3. **Proportional Settlement Tracking:**
   For an obligation link where $\text{allocated\_amount} < \text{original\_amount}$, the project's share of settlement is computed proportionally:
   $$\text{paid\_share} = \text{allocated\_amount} \times \left( \frac{\text{total\_paid}}{\text{original\_amount}} \right)$$
   $$\text{project\_outstanding} = \max(0, \text{allocated\_amount} - \text{paid\_share})$$

---

## 4. Append-Only Lifecycle & Audit Evidence

1. **No Physical Deletions:**
   - Physical `DELETE` privilege is revoked from `finsight_app` on `project_financial_attributions`, `project_commercial_document_links`, and `project_financial_obligation_links`.
   - All unlinking operations set `link_status = 'UNLINKED'`, recording `unlinked_at`, `unlinked_by`, and `unlink_reason`.
2. **Multicurrency Provenance:**
   - User-declared rates on manual attributions are explicitly labeled `rate_source = 'DECLARED_USER_ASSUMPTION'` with `rate_verified = FALSE`.
   - Base currency identities (1:1) are labeled `rate_source = 'BASE_CURRENCY_IDENTITY'` with `rate_verified = TRUE`.
3. **Idempotent Link Requests:**
   - Repeated link requests with identical `(business_id, project_id, target_id)` and status `ACTIVE` safely return the existing link without duplicate rows.

---

## 5. Developer B Integration & Future C04 Roadmap

1. **Current State (C05B):**
   - Developer A provides read and association endpoints for commercial documents.
   - Developer B's commercial documents are discovered via `CommercialDocumentRepository`.
   - No ledger postings or receivables/payables are automatically created.
2. **Upcoming C04 Milestone (Invoice/Bill Posting Bridge):**
   - When C04 is scheduled, Developer A and Developer B will establish the formal posting endpoint that transitions a finalized commercial document into an authoritative financial obligation (`FinancialObligation`).
   - At that point, the document link's status will transition smoothly from `COMMERCIAL_UNPOSTED` to `MATCHED_TO_OBLIGATION` without schema changes.
