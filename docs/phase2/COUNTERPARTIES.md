# Counterparty Foundation (Customers & Suppliers) Specification & Contract

**Feature:** `A-F03-COUNTERPARTIES` (Customer & Supplier Foundation, Transaction & Draft Integration)
**Date:** 19 October 2026 (Asia/Karachi)
**Author:** Antigravity (Developer A Backend & Git Coordinator)
**Status:** **INTEGRATION_VERIFIED**
**Allocated Migration:** `V25__create_counterparties.sql`

---

## 1. Feature Purpose & Scope

### 1.1 Objective
Provide an authoritative, tenant-isolated foundation for managing external business entities (Customers, Suppliers, or Both) within FinSight. Counterparties can be selected when recording financial transactions or saving record drafts, establishing clean audit provenance and laying the bedrock for obligation tracking and aging analysis (F03/F04/F05).

### 1.2 Core Domain Principles
1. **Zero Financial Mutation:**
   - Standalone counterparty operations (create, update, archive, restore) **never** post movements, modify account balances, create transactions, or alter monthly records/health scores.
2. **Strict Multi-Tenant Scoping:**
   - All counterparty entities are permanently bound to `business_id` (server-resolved from the active session).
   - Client requests cannot override or specify a foreign business ID.
   - Cross-tenant references in transactions or drafts are rejected with HTTP 400 (`Counterparty does not exist`).
3. **Optimistic Locking & Duplicate Safety:**
   - Updates require a matching `version` integer to prevent lost updates (HTTP 409 on conflict).
   - Non-unique case-insensitive index `idx_counterparties_business_lower_name` on `(business_id, LOWER(display_name))` facilitates fast searches while legitimately allowing identical display names across separate entities within a business.
4. **Soft Archival with Referential Integrity:**
   - Counterparties cannot be hard-deleted via runtime application privileges (`DELETE` is revoked from `finsight_app`); entities transition between `ACTIVE` and `ARCHIVED`.
   - `ARCHIVED` counterparties are excluded from dropdown selectors for new entries, but retain full historical visibility in existing transactions, reports, and search.
   - Database Foreign Key on `financial_transactions` enforces `(counterparty_id, business_id) REFERENCES counterparties(id, business_id) ON DELETE RESTRICT`. Hard deletion is strictly blocked at the database engine level.
   - Database Foreign Key on `record_drafts` enforces composite tenant isolation: `(counterparty_id, business_id) REFERENCES counterparties(id, business_id) ON DELETE RESTRICT` (utilizing `MATCH SIMPLE` where nullable draft counterparty requires tenant match when present).

---

## 2. Normative Database Schema (Flyway V25)

Allocated in coordination with project architecture: `backend/src/main/resources/db/migration/V25__create_counterparties.sql`

```sql
-- 1. Counterparties Table
CREATE TABLE IF NOT EXISTS counterparties (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    tax_registration_number VARCHAR(50),
    email VARCHAR(255),
    phone VARCHAR(50),
    address VARCHAR(500),
    notes VARCHAR(1000),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_counterparties_business FOREIGN KEY (business_id)
        REFERENCES businesses(id) ON DELETE CASCADE,
    CONSTRAINT chk_counterparties_type CHECK (type IN ('CUSTOMER', 'SUPPLIER', 'BOTH')),
    CONSTRAINT chk_counterparties_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT uq_counterparties_id_business UNIQUE (id, business_id)
);

-- Non-unique case-insensitive index for fast name search allowing same-name entities
CREATE INDEX IF NOT EXISTS idx_counterparties_business_lower_name
    ON counterparties (business_id, LOWER(display_name));

-- 2. Transaction Reference Integration (Composite Tenant FK)
ALTER TABLE financial_transactions
    ADD COLUMN IF NOT EXISTS counterparty_id UUID;

ALTER TABLE financial_transactions
    ADD CONSTRAINT fk_fin_tx_counterparty
    FOREIGN KEY (counterparty_id, business_id)
    REFERENCES counterparties(id, business_id)
    ON DELETE RESTRICT;

-- 3. Draft Reference Integration (Composite Tenant FK with RESTRICT)
ALTER TABLE record_drafts
    ADD COLUMN IF NOT EXISTS counterparty_id UUID;

ALTER TABLE record_drafts
    ADD CONSTRAINT fk_record_drafts_counterparty
    FOREIGN KEY (counterparty_id, business_id)
    REFERENCES counterparties(id, business_id)
    ON DELETE RESTRICT;

-- 4. Runtime Database Least-Privilege Grants (SELECT, INSERT, UPDATE only)
REVOKE DELETE ON TABLE counterparties FROM finsight_app;
GRANT SELECT, INSERT, UPDATE ON TABLE counterparties TO finsight_app;
```

---

## 3. Normative REST API Contract

Base URL: `/api/finance/counterparties`

### 3.1 Create Counterparty
- **Endpoint:** `POST /api/finance/counterparties`
- **Role Permissions:** `OWNER`, `ACCOUNTANT` (MANAGER and VIEWER receive 403 Forbidden)
- **Request Body:**
  ```json
  {
    "displayName": "Tariq Traders",
    "type": "CUSTOMER",
    "email": "tariq@example.com",
    "phone": "+92 300 1234567",
    "address": "Shop 4, Bolton Market, Karachi",
    "taxRegistrationNumber": "NTN-1234567-8",
    "notes": "Key wholesale customer"
  }
  ```
- **Response:** `201 Created`
  ```json
  {
    "id": "35301713-ec26-404c-a89c-a42d8baf733d",
    "businessId": "bb916701-6e97-4d1c-98d1-7806238c2180",
    "displayName": "Tariq Traders",
    "type": "CUSTOMER",
    "status": "ACTIVE",
    "taxRegistrationNumber": "NTN-1234567-8",
    "email": "tariq@example.com",
    "phone": "+92 300 1234567",
    "address": "Shop 4, Bolton Market, Karachi",
    "notes": "Key wholesale customer",
    "version": 0,
    "createdAt": "2026-10-19T14:00:00Z",
    "updatedAt": "2026-10-19T14:00:00Z"
  }
  ```
- **Error Responses:**
  - `400 Bad Request`: Missing display name, invalid type enum, or character length exceeded.
  - `409 Conflict`: Display name matches another counterparty in the same business (case-insensitive).

### 3.2 List Counterparties
- **Endpoint:** `GET /api/finance/counterparties?status=ACTIVE&type=CUSTOMER&q=Tariq&page=0&size=20`
- **Role Permissions:** `OWNER`, `ACCOUNTANT`, `MANAGER`, `VIEWER`
- **Query Parameters:**
  - `status`: `ACTIVE` (default) or `ARCHIVED`
  - `type`: `CUSTOMER`, `SUPPLIER`, `BOTH`, or omitted for all
  - `q`: Free-text search matching name, phone, email, or tax registration number
  - `page`: Zero-indexed page number (default 0)
  - `size`: Bounded page size (default 20, max 100)
- **Response:** `200 OK`
  ```json
  {
    "content": [
      {
        "id": "35301713-ec26-404c-a89c-a42d8baf733d",
        "displayName": "Tariq Traders",
        "type": "CUSTOMER",
        "status": "ACTIVE",
        "phone": "+92 300 1234567",
        "email": "tariq@example.com",
        "taxRegistrationNumber": "NTN-1234567-8",
        "version": 0,
        "createdAt": "2026-10-19T14:00:00Z"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
  }
  ```

### 3.3 Get Counterparty Detail
- **Endpoint:** `GET /api/finance/counterparties/{id}`
- **Role Permissions:** `OWNER`, `ACCOUNTANT`, `MANAGER`, `VIEWER`
- **Response:** `200 OK` (Full detail including `address` and `notes`)

### 3.4 Update Counterparty
- **Endpoint:** `PUT /api/finance/counterparties/{id}`
- **Role Permissions:** `OWNER`, `ACCOUNTANT`
- **Request Body:** Requires current `version` integer for optimistic concurrency control.
- **Response:** `200 OK` (version incremented)
- **Error:** `409 Conflict` on version mismatch or duplicate display name.

### 3.5 Archive / Restore Counterparty
- **Endpoint:** `POST /api/finance/counterparties/{id}/archive`
- **Endpoint:** `POST /api/finance/counterparties/{id}/restore`
- **Role Permissions:** `OWNER`, `ACCOUNTANT`
- **Response:** `200 OK`

---

## 4. Financial Transaction & Draft Integration

### 4.1 Transaction Posting Validation
When recording a transaction (`POST /api/finance/transactions`):
1. **Optional Field:** `counterpartyId` is optional. Internal transfers (`INTERNAL_TRANSFER`) disallow counterparty association.
2. **Tenant Scoping:** The referenced counterparty must exist and belong to the active server-resolved `business_id`.
3. **Active Status Requirement:** Counterparties must have `status = 'ACTIVE'` at the time of posting.
4. **Type Compatibility Rule:**
   - `DIRECT_SALE_RECEIPT`: Counterparty type must be `CUSTOMER` or `BOTH`. Selecting a `SUPPLIER` returns HTTP 400 (`Selected counterparty is not registered as a customer`).
   - `OPERATING_EXPENSE_PAYMENT`: Counterparty type must be `SUPPLIER` or `BOTH`. Selecting a `CUSTOMER` returns HTTP 400 (`Selected counterparty is not registered as a supplier`).
5. **Idempotency Hash Inclusion:** `&counterpartyId=` is included in the SHA-256 payload digest calculation to prevent replay confusion.
6. **Persistence & Read Back:** `counterparty_id` is persisted on `financial_transactions`. Both `counterpartyId` and denormalized `counterpartyName` are populated on `TransactionDetailResponse`.

### 4.2 Record Draft Integration
When managing drafts (`POST /api/finance/drafts`, `PUT /api/finance/drafts/{id}`, `POST /api/finance/drafts/{id}/record`):
1. Drafts accept `counterpartyId` and preserve it throughout the draft review lifecycle.
2. Draft validation enforces tenant scoping and type compatibility against active counterparties.
3. Upon calling `recordDraft()`, `counterpartyId` is atomically transferred into the created `FinancialTransaction`.

---

## 5. Frontend Architecture & Usability

### 5.1 Route & Workspace Structure
- **Workspace Route:** `/records/contacts` (also accessible via the `contacts` tab in `/records`).
- **Workspace Component:** `CounterpartiesWorkspace.tsx`
  - Active and Archived tabs.
  - Search query debounce (300ms) across name, phone, email, and NTN.
  - Type filtering (`ALL`, `CUSTOMER`, `SUPPLIER`, `BOTH`).
  - Server-side pagination.
  - Responsive table layout with mobile-friendly badges.
  - Archive and Restore confirmation dialogs.
  - Empty states with distinct action buttons.

### 5.2 Contextual Selector with Inline Creation
- **Component:** `CounterpartySelector.tsx`
  - Contextual dropdown filtering by target type (`CUSTOMER` or `SUPPLIER`).
  - Automatically includes counterparties marked as `BOTH`.
  - Built-in `+ Add New` inline option triggering `CounterpartyModal.tsx`.
  - Integrated into `PostTransactionForm.tsx` (money entry) and `DraftEntryFields.tsx` (record draft review).

### 5.3 Bilingual & RTL Support
- Complete parity between `counterpartyEn` and `counterpartyUr`.
- RTL layout direction verified for Urdu (`dir="rtl"`).
- Type translations: Customer (`گاہک`), Supplier (`سپلائر`), Both (`دونوں`).

---

## 6. Verification & Quality Evidence

| Verification Layer | Target / Command | Result | Details |
| :--- | :--- | :--- | :--- |
| **Backend Unit Tests** | `CounterpartyServiceTest` | **16 / 16 PASSED** | Validates CRUD, type validation, search query filtering, same-name independence, optimistic locking, and active/archive transitions. |
| **Backend WebMvc Tests** | `CounterpartyControllerTest` | **9 / 9 PASSED** | Validates HTTP status codes (200, 201, 400, 409), input validation, and RBAC role checks. |
| **PostgreSQL Integration Tests** | `CounterpartyPostgreSqlIT` | **8 / 8 PASSED** | EmbeddedPostgres 14.22 with Flyway V1-V25. Verifies schema migration, runtime privilege revocation (`DELETE` denied with error 42501), multi-tenant isolation, foreign key `(counterparty_id, business_id) ON DELETE RESTRICT` on transactions and drafts, direct SQL cross-tenant rejection (`fk_record_drafts_counterparty`, `fk_fin_tx_counterparty`), same-name entity independence with separate UUIDs, and zero financial mutation. |
| **Transaction & Draft Integration Tests** | `TransactionPostServiceTest`, `RecordDraftServiceTest`, `RecordDraftPostgreSqlIT` | **98 / 98 PASSED** | Zero regressions on existing transactions and drafts; type compatibility and draft-to-transaction recording verified. |
| **Frontend Unit & Contract Tests** | `node --test tests/counterparties.test.mjs` | **9 / 9 PASSED** | Validates API client, parsers, Urdu unicode handling, copy parity, and contract shapes. |
| **Total Frontend Test Suite** | `npm test` | **198 / 198 PASSED** | All 198 frontend unit and contract tests pass with zero failures. |
| **Real Browser Integration Harness** | `node tests/accounts-live.integration.mjs --counterparties-only` | **PASSED (EXIT 0)** | Disposable PostgreSQL 14.22 (Flyway V25) + Spring Boot (8680) + gateway (3580) + Next.js production build (3581) + Microsoft Edge. 12 check groups passed; blockers=[]; integrated=true. Verified Journeys A–G: customer CRUD, same-name independence, supplier payment with counterparty linkage, customer receipt with idempotency, imported draft workflow with provenance & verbatim amount preservation, archive/restore visibility toggling with historical preservation, RBAC matrix (OWNER/ACCOUNTANT write, MANAGER/VIEWER read-only), multi-tenant cross-business rejection, Urdu RTL layout, mobile 390x844 responsive bounds, and zero unintended financial mutations. |
| **TypeScript Compilation** | `npx tsc --noEmit` | **EXIT 0** | Zero type errors across the entire Next.js codebase. |
| **ESLint Quality Gate** | `npm run lint` | **EXIT 0** | Zero ESLint errors (React 19 `set-state-in-effect` compliant). |
| **Next.js Production Build** | `npm run build` | **EXIT 0** | Turbopack compilation succeeded; routes `/records` and `/records/contacts` generated cleanly. |
