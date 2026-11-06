# S5B Critical Coverage — 6 November 2026

**CODEX S5B CRITICAL COVERAGE COMPLETE — EDITING_PAUSED**

Bounded result: **6 passed, 0 application failures, 3 blocked role scenarios**. These are new grouped acceptance checks, not individual assertions. The earlier accepted S5 24/24 checkpoint remains separate and was not rerun or added to this count. Evidence: frontend/test-artifacts/s5b-live/summary.json, with original attempts retained alongside it.

## Runtime and isolation

Existing shared checkout C:\Users\PC\Projects\sme-financial-webapp; dev/suleman; HEAD 2fa20c3b6ca59d04504290542fdfef21ef7e1da2. Real Edge -> production Next http://127.0.0.1:3100 -> Spring http://127.0.0.1:8081 -> disposable PostgreSQL 5434 / sme_health_s4b, V37 per accepted runtime handoff. S4B manifest used privately. Normal login, cookie session and real CSRF used; no synthetic financial responses. No backend startup, database reset, fixture initializer or smoke script executed.

Inspected PAYMENT_CYCLES_AND_ADVANCES.md, C01_POSTING_DRAFT.md idempotency rules, published advances/payments/calendar adapters, TransactionSubmission, backend authorization definitions and relevant service behavior read-only. OWNER/ACCOUNTANT are the write roles; MANAGER/VIEWER financial read presentation is not proof of server enforcement.

## Passed checks and numerical evidence

1. **Customer advance lifecycle — real authenticated HTTP plus browser inspection.** Dedicated BANK opening 10000.00; customer advance 1500.00 -> balance 11500.00 and one transaction. Applied 500.00 -> receivable/unapplied both 1000.00. Identical-key application retry returned 201 without a second cash transaction. Excess application 1000.01 returned 409. Applied remaining 1000.00 -> outstanding/unapplied both 0.00. New-key repeat application returned 409. Balance stayed 11500.00 and transaction count stayed one. Endpoints: POST /api/finance/advances, POST /api/finance/advances/{id}/applications, GET advance/obligation/account/history.
2. **Supplier payable/settlement — real authenticated HTTP plus browser inspection.** Dedicated BANK opening 10000.00; declared payable 3000.00 did not move cash. PUT payment-plan created PLANNED installment 3000.00 with zero transactions. Partial cash settlement 1000.00 -> account 9000.00, outstanding 2000.00. Same-key replay returned the same payment ID and kept one transaction. Attempt 2000.01 returned 409 with balance unchanged. Final 2000.00 -> account 7000.00, outstanding 0.00, settlementStatus FULLY_PAID and two transactions. Browser reload did not add cash movements. General obligation status remains OPEN; fully paid is the separate settlementStatus, not an invented CLOSED enum.
3. **Posting UI replay protection — browser forms and actual persisted API results.** Double click generated one POST and one 25.00 receipt. Refresh/back/forward added nothing. For a second 75.00 receipt, route.fetch actually completed the Spring request (201), then only the browser acknowledgement was deliberately aborted. UI exposed explicit Retry original request and locked amount editing. Retry reused identical key/payload, returned the original transaction ID with 201, and left two total transactions and balance 10100.00. Changed canonical amount 76.00 under that key returned 409 with no extra transaction. This is simulated transport loss against a real successful backend write, not a mocked API success; no concurrent backend stress claim.
4. **OWNER and tenant context — real browser/HTTP.** OWNER sees account creation and successfully performed authorized mutations above. Actual business switch to Zenith makes Apex account and seeded obligation reads 404; restored Apex afterward. This proves the exercised OWNER boundary, not the unavailable role matrix.
5. **Financial fingerprints/reference fixtures.** Monthly records and score-history response fingerprints unchanged. Original Main/Reserve/Collections balances remain 105000.00 / 10000.00 / 80000.00; original receivable outstanding remains 20000.00. No claim that the seeded 195000.00 subtotal equals full-business cash after isolated test account creation. Advice-specific fingerprints were not independently rerun in this focused batch.
6. **Calendar and bilingual labels — real browser/read-only HTTP.** Fully paid payable is absent from remaining calendar exposure; payment-plan outstanding is 0.00. A stored installment may remain PLANNED: cash completion is determined by actual settlement, and remaining calendar exposure excludes the satisfied obligation. Actual EN and UR advance heading/unapplied-credit labels verified, with dir=rtl. Screenshots advances-en.png, advances-ur.png and payables.png are in the evidence directory.

## Blocked roles — not application failures

- ACCOUNTANT: **BLOCKED — ROLE_FIXTURES_UNAVAILABLE**.
- MANAGER: **BLOCKED — ROLE_FIXTURES_UNAVAILABLE**.
- VIEWER: **BLOCKED — ROLE_FIXTURES_UNAVAILABLE**.

Runtime owner confirmed one identity with OWNER membership in both businesses. No other credentials exist. No users created, memberships changed, MFA/CSRF bypassed or initializer rerun. Other-role navigation, protected direct URLs, mutation denial and session changes after role updates remain live-unverified. Backend RBAC source was inspected, but no new backend tests were run and historical test counts are not promoted to fresh browser evidence.

## Execution and corrections

New runner: frontend/tests/s5b-critical.live.mjs. Commands use existing dependencies and pnpm_config_verify_deps_before_run=false, FINSIGHT_LIVE_FIXTURE=<approved S4B manifest>. Main invocation: pnpm exec node tests/s5b-critical.live.mjs. S5B_ONLY selects focused prefixes to avoid repeating passed mutations.

- Initial run exit 1: advance passed; payable/replay setup reused an account name and correctly received 409. Fixed runner-only unique account naming.
- Second focused run exit 1: payable passed; replay waited for nonexistent Recorded text after an actual successful POST. Replaced that brittle text assertion with persisted financial assertions.
- Third focused run exit 1: back/forward automation raced Next navigation. Added navigation-settling waits; no application change.
- Fourth focused C/fingerprint run: **exit 0**.
- Read-only calendar/label run: **exit 0**.
- Read-only complete tagged ledger-ID inventory: **exit 0**.
- pnpm exec node --check tests/s5b-critical.live.mjs: **exit 0**.
- pnpm exec node --test tests/payment-cycles.test.mjs tests/transactions.test.mjs: **36 passed, zero failed, exit 0**.

All earlier attempts remain recorded, including their actual writes. They are not counted as extra successful acceptance journeys. No application implementation defect demonstrated; only test automation corrected. No backend financial/security violation found in the exercised cases. No new TypeScript/lint/build run required for application source: none changed. Accepted prior 372-test/typecheck/lint/build evidence remains historical and separate. Mocked browser suites executed: zero.

## Scope limits and release risks

Advances/payable creation and application used real authenticated HTTP followed by browser views, not every modal form interaction. Lost-response UI recovery was exercised on ordinary transaction posting; advance/payment form transport-loss recovery is not established by this evidence. Non-owner live scenarios remain blocked. No claim of backend simultaneous-request concurrency certification, full accounting coverage, production acceptance or external-provider verification. F04/F05/F06/F08/F09 non-posting/planning/catalog boundaries retain their earlier accepted S5 evidence; no duplicate rerun.

## Exact changed paths

- frontend/tests/s5b-critical.live.mjs — new focused live acceptance runner.
- docs/integration/final-sprint/frontend/S5B_CRITICAL_COVERAGE_REPORT.md — this report.
- docs/integration/final-sprint/frontend/S3B_LIVE_ACCEPTANCE_REPORT.md — appended S5B checkpoint.
- Generated artifacts: frontend/test-artifacts/s5b-live/ (JSON and screenshots; historical attempts retained).

No production frontend files changed during S5B. No backend, Flyway, dependencies, manifests/lockfiles, environment files or Git mutations. Antigravity work and backend/.cp.txt preserved. Review application remains http://127.0.0.1:3100/records.

Created entity and transaction IDs, including failed-run residue, follow in the exact machine-readable evidence and compact account inventory below. These isolated test records are deliberately retained; no manual ledger cleanup or balance rewrite.
- Account  ():  PKR; transaction IDs: 
