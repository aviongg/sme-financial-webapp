# FinSight Architecture & Integration Contract
## C04: Commercial Document Recognition into Financial AR/AP Subledger
**Document Reference:** `docs/integration/dev-a/c04/C04_COMMERCIAL_RECOGNITION_CONTRACT.md`
**Version:** `1.0.0`
**Status:** `ACTIVE / PROPOSED HANDOFF`
**Date:** 10 November 2026
**Authors & Owners:**
- **Developer A (Antigravity):** Financial Subledger, Obligations, Attributions & Commitments Coordinator
- **Developer B (Fatima):** Commercial Documents (F04 Invoices/Bills), Projects (F05) & Inventory Domain Owner

---

## 1. Purpose & Core Philosophy

This contract defines the authoritative boundary and integration protocol for recognizing Developer B's commercial documents (customer `INVOICE` and supplier `BILL` in table `commercial_documents`) into Developer A's financial subledger (`financial_obligations` in AR/AP).

> **"A commercial invoice or bill is a business agreement and documentary record; it is NOT an automatic ledger entry or cash movement."**

### Cardinal Rules:
1. **Explicit Recognition Only:** Finalizing an invoice or bill in F04 remains strictly non-posting (`financial_effect = 'NONE'`). Commercial documents are recognized into financial receivables or payables only through an explicit, authorized user action.
2. **Authoritative Monetary Source:** The recognized financial obligation amount originates strictly from the finalized document's authoritative total in `commercial_documents.total`. Clients cannot supply, override, or discount the recognized amount.
3. **Explicit Counterparty Confirmation:** Because F04 stores encrypted free-text counterparty names (`counterparty_name_ciphertext`), the user must explicitly select or confirm an active, compatible C03 counterparty ID (`CUSTOMER` or `BOTH` for invoices; `SUPPLIER` or `BOTH` for bills).
4. **Zero Cash / Bank Mutation:** Financial recognition generates an AR receivable or AP payable subledger obligation; it creates **zero** bank movements, **zero** cash transactions, and **zero** general ledger accrual journal lines.
5. **No Automatic Invalidation:** Commercial voiding of an already-recognized document does not silently delete the financial obligation or reverse cash settlements.

---

## 2. Ownership & Domain Boundaries

| Feature / Concern | Owner | Responsibilities & Invariants |
| :--- | :--- | :--- |
| **Commercial Invoices & Bills (F04)** | **Developer B (Fatima)** | • Document drafting, line items, pricing, tax/discount calculation.<br>• Number sequence allocation (`INV-xxxxxx`, `BILL-xxxxxx`).<br>• Status lifecycle (`DRAFT` $\to$ `FINALIZED` $\to$ `VOID`).<br>• `financial_effect` fixed to `'NONE'`. |
| **Financial Subledger & AR/AP (C03/C04)** | **Developer A (Antigravity)** | • Authoritative `financial_obligations` records (`RECEIVABLE` / `PAYABLE`).<br>• Unique source binding `(business_id, source_type, source_id)`.<br>• Idempotency tracking (`COMMERCIAL_DOCUMENT_RECOGNITION`).<br>• Payment allocations and settlements (`PaymentAllocationService`).<br>• Multicurrency FX settlement revaluation (`fx_obligation_settlements`). |
| **Project Financial Commitments (C05)** | **Developer A (Antigravity)** | • Transition of project exposure from unposted commercial commitments (`commercialUnpostedExposure`) to authoritative financial commitments (`authoritativeFinancialOutstanding`). |

---

## 3. Commercial Document Eligibility Criteria

A commercial document is eligible for financial recognition if and only if all of the following conditions are satisfied:
1. **Document Exists & Scoped:** The document exists in `commercial_documents` under the active `business_id`.
2. **Supported Kind:** `kind` must be `INVOICE` or `BILL`.
3. **Status Must Be FINALIZED:**
   * `DRAFT` documents are strictly rejected (HTTP 400).
   * `VOID` documents are strictly rejected (HTTP 400).
4. **Document Number Present:** `document_number` must be non-null (e.g. `INV-000042` or `BILL-000031`).
5. **Positive Total:** `total` must be non-null and greater than zero (`total > 0`).
6. **Not Previously Recognized:** No active financial obligation exists in the business with `source_type = CUSTOMER_INVOICE` or `SUPPLIER_BILL` and `source_id = document.id`.
7. **Active & Compatible Counterparty:**
   * Invoice recognition requires counterparty type `CUSTOMER` or `BOTH`.
   * Bill recognition requires counterparty type `SUPPLIER` or `BOTH`.
   * Counterparty status must be `ACTIVE`.
8. **Authorization:** Actor holds `BusinessPermission.FINANCIAL_TRANSACTION_POST`.

---

## 4. Endpoints & REST Contract

### A. Commercial Recognition Endpoint
`POST /api/finance/obligations/recognize-commercial-document`

*   **Permission:** `BusinessPermission.FINANCIAL_TRANSACTION_POST`
*   **Headers:** `Idempotency-Key: <unique-string>` (or passed in body)
*   **Request Body:**
    ```json
    {
      "documentId": "4a71d608-2e06-4b2a-b769-cf634e793d93",
      "documentKind": "INVOICE",
      "counterpartyId": "9b12c8a2-1111-4f1b-8012-789a01234567",
      "idempotencyKey": "recog-inv-42-key",
      "notes": "Recognized Invoice INV-000042 into Accounts Receivable"
    }
    ```
*   **Response Body (HTTP 201 Created):**
    ```json
    {
      "obligationId": "b182d334-9c88-4e12-b345-d890a1234567",
      "businessId": "e291c445-0a1b-4e22-b7b9-edc5491a36a1",
      "direction": "RECEIVABLE",
      "sourceType": "CUSTOMER_INVOICE",
      "sourceId": "4a71d608-2e06-4b2a-b769-cf634e793d93",
      "documentNumber": "INV-000042",
      "counterpartyId": "9b12c8a2-1111-4f1b-8012-789a01234567",
      "counterpartyName": "Ahmed Textiles",
      "currency": "PKR",
      "originalAmount": 115000.0000,
      "outstandingAmount": 115000.0000,
      "allocatedAmount": 0.0000,
      "status": "OPEN",
      "issueDate": "2026-11-10",
      "dueDate": "2026-12-10",
      "recognitionTimestamp": "2026-11-10T05:08:58Z",
      "recognitionProvenance": "F04_COMMERCIAL_RECOGNITION_V1",
      "notes": "Recognized Invoice INV-000042 into Accounts Receivable",
      "obligationDetail": { ... }
    }
    ```

### B. Read-Only Recognition Status Endpoint
`GET /api/finance/obligations/commercial-recognition-status/{documentId}`

*   **Permission:** `BusinessPermission.FINANCIAL_DATA_READ`
*   **Response Body (HTTP 200 OK):**
    ```json
    {
      "documentId": "4a71d608-2e06-4b2a-b769-cf634e793d93",
      "documentKind": "INVOICE",
      "documentNumber": "INV-000042",
      "commercialStatus": "FINALIZED",
      "isRecognized": true,
      "obligationId": "b182d334-9c88-4e12-b345-d890a1234567",
      "direction": "RECEIVABLE",
      "sourceType": "CUSTOMER_INVOICE",
      "originalAmount": 115000.0000,
      "outstandingAmount": 65000.0000,
      "allocatedAmount": 50000.0000,
      "currency": "PKR",
      "obligationStatus": "OPEN",
      "recognitionTimestamp": "2026-11-10T05:08:58Z",
      "allocations": [
        {
          "allocationId": "c490a112-...",
          "obligationId": "b182d334-...",
          "referenceNumber": "REF-100",
          "allocatedAmount": "50000.00",
          "remainingObligationOutstanding": "65000.00"
        }
      ],
      "notes": "Recognized from INVOICE INV-000042"
    }
    ```

---

## 5. Concurrency, Locking & Idempotency Invariants

1. **Pessimistic Row Locking:** Recognition executes `SELECT id, kind, status FROM commercial_documents WHERE id = ? AND business_id = ? FOR UPDATE`, serializing concurrent recognition attempts.
2. **Database Unique Constraint:** PostgreSQL unique index `uq_obligations_business_source` on `(business_id, source_type, source_id)` ensures that even under concurrent race conditions, only one obligation row can be committed.
3. **Idempotency Fingerprint:**
   * Hash: SHA-256 of `businessId | documentId | counterpartyId`.
   * Replay with identical key & payload: returns cached response (`201` status).
   * Replay with identical key & altered payload: returns `HTTP 409 Conflict`.
   * Subsequent call with new key for already recognized document: returns `HTTP 409 Conflict`.

---

## 6. Commercial Voiding & Financial Settlement Invariants

If Developer B's commercial document transitions to `VOID` after recognition:
1. **Settlement Barrier:** If the recognized obligation has received payments (`allocatedAmount > 0`), commercial voiding or correction cannot silently reverse cash transactions or destroy settlement evidence.
2. **Accounting Discrepancy Surfacing:** The read-only status endpoint surfaces both the commercial status (`commercialStatus: "VOID"`) and the financial obligation status (`obligationStatus: "OPEN"`), enabling audit review without silent ledger destruction.
3. **Financial Cancellation Protocol:** An obligation can only be voided through Developer A's explicit financial void endpoint (`POST /api/finance/obligations/{id}/void`) after any associated payment allocations have been explicitly reversed by an authorized accountant.

---

## 7. C05 Project Commitment Transition

When a commercial document is linked to an F05 project via `project_commercial_document_links`:
*   **Prior to Recognition:** The document's allocated amount appears under `commercialUnpostedExposure`.
*   **After Recognition:** The resulting `FinancialObligation` is recognized. In C04A-2/C04A-3, the exposure seamlessly transitions into `authoritativeFinancialOutstanding`.
*   **Deduplication Rule:** A commercial document whose `document_id` matches a recognized obligation source ID must be suppressed from `commercialUnpostedExposure`, preventing duplicate counting in `totalCommittedExposure`.

---

## 8. Handoff Checklist for C04A-2 Frontend

Developer A will implement C04A-2 with:
1. "Recognize into AR" button on finalized Invoices (`/invoices/[id]`).
2. "Recognize into AP" button on finalized Bills (`/bills/[id]`).
3. Explicit Counterparty Selector dialog (showing advisory decrypted name, with searchable dropdown of active counterparties filtered by direction).
4. Recognition Status Badge on commercial document detail views:
   * `NOT RECOGNIZED` (grey)
   * `RECOGNIZED IN LEDGER` (blue/green, displaying linked obligation balance & allocations).
5. All actions gated by `FINANCIAL_TRANSACTION_POST`.

