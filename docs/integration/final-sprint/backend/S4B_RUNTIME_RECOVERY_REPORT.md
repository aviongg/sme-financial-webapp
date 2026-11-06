# FinSight S4B Isolated Runtime Recovery & Safeguard Report

**Date:** Friday, 6 November 2026
**Task ID:** `FINAL-S4B-ISOLATED-RUNTIME-RECOVERY`
**Status:** `S4B NEW RUNTIME VERIFIED — AWAITING CODEX CUTOVER PAUSE`
**Branch:** `dev/suleman`
**Git Base Commit:** `2fa20c3b6ca59d04504290542fdfef21ef7e1da2`

---

## 1. Executive Summary

Following the P0 incident where the shared 5433 database experienced state loss due to an unsafe Maven Surefire execution of an `@Test`-annotated seeder, Antigravity has:
1. **Permanently eliminated the vulnerability:** Removed `LiveRuntimeHandoffSeeder.java` and replaced it with a strictly guarded standalone tool `LiveRuntimeFixtureInitializer.java`.
2. **Built an isolated, pristine V1–V37 environment:** Initialized PostgreSQL 14.22 on port `5434` with database `sme_health_s4b`, applied Flyway migrations V1–V37 under `finsight_migrator`, and granted strict DML permissions to `finsight_app`.
3. **Safely seeded canonical deterministic fixtures:** Verified all 10 accounting and isolation invariants via non-destructive transactional operations.
4. **Exported dedicated manifest:** Generated `tmp/finsight-live-fixture-s4b.json` without altering the historical incident manifest `tmp/finsight-live-fixture.json`.
5. **Launched dedicated Spring Boot backend on port `8081`:** Verified health, CSRF, authentication, multi-tenant isolation, obligation lifecycles, and C11 AI financial evidence via automated read-only HTTP probes (`scripts/verify_s4b_runtime.mjs`).

The preserved incident environment (PostgreSQL 5433, Spring Boot 8080) remains running and untouched for forensic reference.

---

## 2. Seeder Safeguard Architecture

The new fixture initializer (`LiveRuntimeFixtureInitializer.java`) enforces multiple defense-in-depth safeguards:

| Invariant | Implementation Mechanism | Enforcement |
|:---|:---|:---|
| **Zero Test Runner Discovery** | Plain Java application with `public static void main(String[] args)`; zero `@Test` annotations; filename does not match `*Test`, `Test*`, or `*IT`. | Maven Surefire and Failsafe will never scan or invoke it. |
| **Explicit Operator Authorization** | Requires environment variable `FINSIGHT_DISPOSABLE_INITIALIZE=true`. | Throws `SecurityException` immediately if absent or false. |
| **Strict Target Host Validation** | Accepts only `127.0.0.1` or `localhost`. | Throws `SecurityException` on any remote or unverified host. |
| **Prohibited Port Rejection** | Explicit check: rejects port `5432` (production/default) and port `5433` (preserved forensic runtime). | Throws `SecurityException("PROHIBITED_PORT: Port 5433 is reserved...")`. |
| **Empty Database Assertion** | Pre-flight check: queries `SELECT count(*) FROM app_users` and `SELECT count(*) FROM financial_accounts`. | If count > 0, throws `IllegalStateException` and aborts. Fails closed. |
| **Zero Destructive SQL** | Contains **ZERO** `DELETE` or `TRUNCATE` statements. | Cannot delete or mutate existing database tables under any circumstances. |
| **Credential Hygiene** | Does not log raw passwords to stdout or tracked git files; writes structured JSON to untracked `tmp/finsight-live-fixture-s4b.json`. | Zero secret leakage in build logs or commit history. |

---

## 3. Runtime Infrastructure Topology

### 3.1 Preserved Forensic Environment (DO NOT TOUCH)
* **PostgreSQL:** Port `5433`, Database `sme_health`, PID daemon `task-3444`
* **Spring Boot:** Port `8080`, PID daemon `task-3848`
* **Status:** Preserved as-is for incident review.

### 3.2 New Stabilized Acceptance Runtime (S4B)
* **PostgreSQL:** Port `5434`, Cluster `C:\Users\PC\AppData\Local\Temp\finsight-disposable-db-s4b`, PID daemon `task-4186`
* **Database Name:** `sme_health_s4b`
* **Flyway Head:** Version 37 (All 37 migrations V1–V37 applied cleanly)
* **Spring Boot:** Port `8081` (`http://127.0.0.1:8081`), PID daemon `task-4380`
* **Data Isolation:** Complete process, disk, and port isolation from port 5433 / 8080.

---

## 4. Fixture State & Accounting Invariants (Port 8081 / DB 5434)

All figures verified via automated probe script [scripts/verify_s4b_runtime.mjs](file:///C:/Users/PC/Projects/sme-financial-webapp/scripts/verify_s4b_runtime.mjs):

| Entity / Metric | Target ID / Name | Expected Balance | Verified Actual | Status |
|:---|:---|:---|:---|:---|
| **Account 1** | `7ac2e408-402a-4597-813e-016fb5fd70f2`<br>(Apex Main Operations) | PKR 105,000.00 | PKR 105,000.00 | **MATCH** |
| **Account 2** | `c35841bd-e2fb-47be-8b2d-a3de3b2f7f17`<br>(Apex Reserve Account) | PKR 10,000.00 | PKR 10,000.00 | **MATCH** |
| **Account 3** | `24bf3241-184f-4851-9c37-2b1ae29f946f`<br>(Apex Trade Collections) | PKR 80,000.00 | PKR 80,000.00 | **MATCH** |
| **Apex Cash Subtotal** | Accounts 1 + 2 + 3 | PKR 195,000.00 | PKR 195,000.00 | **MATCH** |
| **Receivable Obligation** | `e044c533-5d2c-4e71-a2d9-bb2263d82ef2`<br>(Customer: Alpha Wholesale) | Original: PKR 100,000.00<br>Paid: PKR 80,000.00<br>Outstanding: PKR 20,000.00 | Original: PKR 100,000.00<br>Paid: PKR 80,000.00<br>Outstanding: PKR 20,000.00 | **MATCH**<br>(Status: `PARTIALLY_PAID`) |
| **Zenith Account** | `ef0c8cb6-8d4a-4855-bbae-a9e56480fc2c`<br>(Zenith Operations) | PKR 88,888.00 | PKR 88,888.00 | **MATCH** |
| **Tenant Isolation** | Apex querying Zenith Account | 404 Not Found | 404 Not Found | **VERIFIED** |
| **Reverse Isolation** | Zenith querying Apex Obligation | 404 Not Found | 404 Not Found | **VERIFIED** |
| **C11 AI Evidence** | `cash_position.PKR.balance` | PKR 195,000.00<br>(`POSTED_ACTUAL`) | PKR 195,000.00<br>(`POSTED_ACTUAL`) | **VERIFIED** |
| **Cash Forecast** | Daily forecast horizon 30d | 31 daily rows | 31 daily rows | **VERIFIED** |
| **Risk Assessment** | Daily risk horizon 30d | CRITICAL severity | CRITICAL severity | **VERIFIED** |

---

## 5. Codex Cutover Coordination

To ensure zero collision during active browser tests, the following cutover procedure is established:

1. **Codex Action:** Codex must pause current browser tests and report:
   ```
   CODEX_PAUSED_FOR_RUNTIME_CUTOVER
   ```
2. **Frontend Redirection:** Update the frontend Next.js rewrite/proxy target from `http://127.0.0.1:8080` to `http://127.0.0.1:8081` (or update `BACKEND_URL=http://127.0.0.1:8081` in frontend test env).
3. **Fixture Manifest Target:** Codex reads test fixtures from `tmp/finsight-live-fixture-s4b.json`:
   - Owner Email: `live.owner@finsight.local`
   - Business A ID: `dd94c43b-279d-4c26-a4ce-1ba6566083e8` (`Apex Industrial Ltd`)
   - Business B ID: `59c34290-9332-46e3-9438-47aeb79118a2` (`Zenith Commercial Ltd`)
   - Account 1 (Main Ops): `7ac2e408-402a-4597-813e-016fb5fd70f2` (PKR 105,000.00)
   - Account 2 (Reserve): `c35841bd-e2fb-47be-8b2d-a3de3b2f7f17` (PKR 10,000.00)
   - Account 3 (Collections): `24bf3241-184f-4851-9c37-2b1ae29f946f` (PKR 80,000.00)
   - Obligation ID: `e044c533-5d2c-4e71-a2d9-bb2263d82ef2` (PKR 20,000.00 outstanding)
   - Zenith Account ID: `ef0c8cb6-8d4a-4855-bbae-a9e56480fc2c` (PKR 88,888.00)
4. **Backend Lead Hand-off Signal:**
   Once Codex confirms the redirection, Antigravity will emit:
   ```
   S4B RUNTIME STABILIZED — CODEX LIVE E2E MAY RESUME
   ```
   and gracefully terminate the preserved legacy 5433/8080 tasks upon user authorization.

---

## 6. Verification Evidence Log

```
=== FINSIGHT S4B READ-ONLY RUNTIME VERIFICATION ===
Connecting to Live Backend at: http://127.0.0.1:8081
Manifest: C:\Users\PC\Projects\sme-financial-webapp\tmp\finsight-live-fixture-s4b.json

[1/10] Fetching CSRF token...
   PASS: CSRF acquired: X-XSRF-TOKEN = cb0709f1-d...

[2/10] Authenticating test user (live.owner@finsight.local)...
   PASS: Authenticated as: live.owner@finsight.local Full Name: Synthetic Live Owner

[3/10] Activating Business A context (Apex Industrial Ltd)...
   PASS: Active Business confirmed: Apex Industrial Ltd (dd94c43b-279d-4c26-a4ce-1ba6566083e8)

[4/10] Verifying Business A Account Balances...
   Account 1 (Apex Main Operations): 105000.00 PKR (Expected: 105000.00)
   Account 2 (Apex Reserve Account): 10000.00 PKR (Expected: 10000.00)
   Account 3 (Apex Trade Collections): 80000.00 PKR (Expected: 80000.00)
   Total Apex Cash (Acc1 + Acc2 + Acc3): 195000.00 PKR (Expected: 195000.00)
   PASS: All 3 account balances and total cash verified exactly!

[5/10] Verifying Receivable Obligation Invariant...
   Obligation Original: 100000.00 PKR
   Obligation Paid: 80000.00 PKR
   Obligation Outstanding: 20000.00 PKR (Expected: 20000.00)
   Settlement Status: PARTIALLY_PAID (Expected: PARTIALLY_PAID)
   PASS: Obligation balance and partial settlement verified!

[6/10] Verifying Cash Forecast API...
   PASS: Daily forecast rows generated: 31

[7/10] Verifying Financial Risk Assessment API...
   PASS: Risk assessment returned. Highest severity: CRITICAL

[8/10] Verifying C11 AI Financial Evidence Integration...
   PASS: C11 AI Evidence confirmed:
     ID: cash_position.PKR.balance
     Value: 195000.00 PKR
     Source Mode: POSTED_ACTUAL

[9/10] Verifying Tenant Isolation (Cross-tenant leak prevention)...
   PASS: Accessing Business B Zenith Account from Business A returned 404 Not Found (Zero Leakage).

[10/10] Activating Business B context & verifying Zenith Account...
   Active Business confirmed: Zenith Commercial Ltd
   Zenith Account Balance: 88888.00 PKR (Expected: 88888.00)
   PASS: Accessing Business A Obligation from Business B returned 404 Not Found (Zero Leakage).

======================================================
ALL 10 VERIFICATION PROBES PASSED PERFECTLY!
S4B Disposable Runtime on Port 8081 / DB 5434 is 100% STABLE.
======================================================
```
