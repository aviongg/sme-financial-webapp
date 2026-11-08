# C09: Cross-Currency Internal Account Transfers & Dual-Currency Subledger Invariants

**Document Version:** `1.0`
**Slice:** `F11E` (Internal FX Transfers)
**Author:** Google Antigravity (Sole Full-Stack Dev A Engineer & Financial Systems Architect)
**Date:** 8 November 2026
**Status:** IMPLEMENTED & VERIFIED

---

## 1. Domain Purpose & Overview

FinSight SMEs often hold multiple currency accounts (e.g. an exporter holding USD Treasury and PKR Operating accounts, or an overseas importer holding AED and PKR accounts).

`F11E: Cross-Currency Internal Account Transfers` enables SMEs to move funds between different-currency financial accounts owned by the same business without creating commercial customer receivables, vendor payables, or sales revenue.

### Key Financial Invariants:
1. **Zero Commercial Mutation:** An internal FX transfer is strictly an inter-account liquidity rebalancing. It never alters customer invoices, supplier bills, commercial obligations, or salesperson commissions.
2. **Dual-Currency Subledger Movements:** The transaction records atomic postings in the respective account currencies:
   - Source Account: `DECREASE` in `sourceCurrency`.
   - Destination Account: `INCREASE` in `destinationCurrency`.
3. **Explicit Fee Evidence:** Bank friction is recorded explicitly under one of two strict placement modes:
   - Mode A (`DESTINATION_DEDUCTED`): Fee is in destination currency and deducted from proceeds received by destination account.
   - Mode B (`SOURCE_CHARGED`): Fee is in source currency and added to the outflow from source account.
   - Mode C (`NO_FEE`): Gross converted principal equals net destination credit.
4. **Compensating Reversal Symmetry:** A reversed transfer mirrors the original movements exactly in their respective account currencies, completely restoring opening balances without secondary fee deductions.
5. **Strict Concurrency & Balance Simulation:** Row-level locking on `financial_accounts` in deterministic UUID order prevents race conditions and strictly forbids negative cash balances.

---

## 2. Mathematical Model & Wire Contracts

### Case A: Destination-Deducted Fee
- **Source:** USD 100.00
- **Locked Rate:** 278.50 PKR/USD
- **Fee:** PKR 150.00 (`DESTINATION_DEDUCTED`)
- **Gross Converted Destination Principal:**
  $$P_{\text{gross}} = \text{Round}_{\text{dest}}(100.00 \times 278.50) = 27,850.00\text{ PKR}$$
- **Net Destination Credit:**
  $$C_{\text{net}} = P_{\text{gross}} - F_{\text{dest}} = 27,850.00 - 150.00 = 27,700.00\text{ PKR}$$
- **Total Source Debit:**
  $$D_{\text{source}} = 100.00\text{ USD}$$
- **Effective Rate:**
  $$R_{\text{eff}} = \frac{C_{\text{net}}}{D_{\text{source}}} = \frac{27,700.00}{100.00} = 277.000000\text{ PKR/USD}$$

### Case B: Source-Charged Fee
- **Source:** USD 100.00
- **Locked Rate:** 278.50 PKR/USD
- **Fee:** USD 5.00 (`SOURCE_CHARGED`)
- **Net Destination Credit:**
  $$C_{\text{net}} = P_{\text{gross}} = 27,850.00\text{ PKR}$$
- **Total Source Debit:**
  $$D_{\text{source}} = 100.00 + 5.00 = 105.00\text{ USD}$$
- **Effective Rate:**
  $$R_{\text{eff}} = \frac{27,850.00}{105.00} = 265.238095\text{ PKR/USD}$$

---

## 3. Database Schema (Flyway V41)

Migration `V41__add_multicurrency_internal_transfers.sql`:
1. Relaxed `chk_tx_currency` on `financial_transactions` and `chk_mov_currency` on `account_movements` from PKR-only to all 10 supported currencies (`PKR`, `USD`, `EUR`, `GBP`, `AED`, `SAR`, `JPY`, `KWD`, `BHD`, `OMR`).
2. Created table `fx_internal_transfers`:
   - `id UUID PRIMARY KEY DEFAULT gen_random_uuid()`
   - `business_id UUID NOT NULL REFERENCES businesses(id)`
   - `source_account_id UUID NOT NULL REFERENCES financial_accounts(id)`
   - `destination_account_id UUID NOT NULL REFERENCES financial_accounts(id)`
   - `source_amount NUMERIC(19, 4) NOT NULL`
   - `source_currency VARCHAR(3) NOT NULL`
   - `exchange_rate NUMERIC(19, 6) NOT NULL`
   - `converted_destination_principal NUMERIC(19, 4) NOT NULL`
   - `destination_currency VARCHAR(3) NOT NULL`
   - `fee_amount NUMERIC(19, 4) NOT NULL DEFAULT 0`
   - `fee_currency VARCHAR(3) NOT NULL`
   - `fee_placement VARCHAR(30) NOT NULL`
   - `net_destination_amount NUMERIC(19, 4) NOT NULL`
   - `total_source_amount NUMERIC(19, 4) NOT NULL`
   - `effective_rate NUMERIC(19, 6) NOT NULL`
   - `financial_transaction_id UUID NOT NULL REFERENCES financial_transactions(id)`
   - `source_movement_id UUID NOT NULL REFERENCES account_movements(id)`
   - `destination_movement_id UUID NOT NULL REFERENCES account_movements(id)`
   - `transfer_date DATE NOT NULL`
   - `status VARCHAR(20) NOT NULL DEFAULT 'SETTLED'`
   - `reversal_transaction_id UUID REFERENCES financial_transactions(id)`
   - `reversal_reason TEXT`
   - `reversed_at TIMESTAMPTZ`
   - `notes TEXT`
   - `idempotency_hash VARCHAR(64) UNIQUE`
   - `created_by_user_id UUID NOT NULL REFERENCES users(id)`
   - `created_at TIMESTAMPTZ NOT NULL DEFAULT now()`
   - `version BIGINT NOT NULL DEFAULT 0`
3. Least-privilege permissions:
   - `GRANT SELECT, INSERT, UPDATE ON fx_internal_transfers TO finsight_app;`
   - `REVOKE DELETE ON fx_internal_transfers FROM finsight_app;`
   - `GRANT SELECT ON fx_internal_transfers TO finsight_backup;`

---

## 4. REST API Specification

### A. Preview Transfer
- **`POST /api/finance/fx/transfers/preview`**
- **RBAC:** `FINANCIAL_DATA_READ`
- **Request:**
  ```json
  {
    "sourceAccountId": "d8e3b3c4-...",
    "destinationAccountId": "e1a2f3b4-...",
    "sourceAmount": "100.00",
    "exchangeRate": "278.50",
    "feeAmount": "150.00",
    "feePlacement": "DESTINATION_DEDUCTED"
  }
  ```
- **Response (200 OK):**
  ```json
  {
    "sourceAccountId": "d8e3b3c4-...",
    "sourceAccountName": "Meezan USD Treasury",
    "sourceCurrency": "USD",
    "destinationAccountId": "e1a2f3b4-...",
    "destinationAccountName": "HBL PKR Operating",
    "destinationCurrency": "PKR",
    "sourceAmount": 100.00,
    "exchangeRate": 278.50,
    "convertedDestinationPrincipal": 27850.00,
    "feeAmount": 150.00,
    "feeCurrency": "PKR",
    "feePlacement": "DESTINATION_DEDUCTED",
    "netDestinationAmount": 27700.00,
    "totalSourceDebit": 100.00,
    "effectiveRate": 277.000000,
    "explanation": "USD 100.00 @ 278.50 yields PKR 27,850.00 principal. Deducting PKR 150.00 bank fee nets PKR 27,700.00 into HBL PKR Operating at an effective rate of 277.000000 PKR/USD."
  }
  ```

### B. Post Transfer
- **`POST /api/finance/fx/transfers`**
- **Header:** `Idempotency-Key: fx-xfer-<uuid>`
- **RBAC:** `FINANCIAL_TRANSACTION_POST`
- **Response (201 Created):** `FxTransferResponse`

### C. List & Get Transfers
- **`GET /api/finance/fx/transfers?status=SETTLED`**
- **`GET /api/finance/fx/transfers/{id}`**
- **RBAC:** `FINANCIAL_DATA_READ`

### D. Reverse Transfer
- **`POST /api/finance/fx/transfers/{id}/reverse`**
- **RBAC:** `FINANCIAL_TRANSACTION_POST`
- **Request:** `{"reason": "Disputed wire charge"}`
- **Response (200 OK):** `FxTransferResponse` with `status: "REVERSED"`
- **Subsequent Reversals:** `409 Conflict`

---

## 5. Verification Matrix

| Verification Level | Target | Result |
| :--- | :--- | :--- |
| **Unit Suite** | `FxTransferServiceTest` (6 tests) | **6 / 6 PASSED** |
| **PostgreSQL IT** | `FxTransferPostgreSqlIT` (7 tests, including multi-threaded race conditions) | **7 / 7 PASSED** |
| **Full FX Backend Suite** | `mvnw.cmd test -Dtest=com.app.sme_health_backend.fx.**` | **70 / 70 PASSED** |
| **Frontend Unit Suite** | `npm --prefix frontend test` | **408 / 408 PASSED** |
| **Next.js Production Build** | `npm --prefix frontend run build` | **EXIT 0 (SUCCESS)** |
| **Full-Stack Browser Journeys** | `node frontend/tests/fx-browser.integration.mjs` (Journeys 1 to 36) | **36 / 36 PASSED** |
