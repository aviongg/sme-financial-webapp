# Phase 2 feature and supporting-work register

Updated 10 October 2026. Baseline MVP exists; **none of the fourteen Phase 2 packages is implemented**. DRAFT means designed for review, not approved/frozen. This register is the readiness authority; detailed designs and DoD live in linked package files. A is financial platform lead, B is the current contributor. Support means required cross-domain reviewer, not a replacement implementation owner.

## Headline packages

| ID / title | Lead / support | Reuse / actual gap | Contracts / prerequisites | Wave / relative load | Current status / precise completion reference |
|---|---|---|---|---|---|
| F01 Transaction-Based Financial Management | A / B | Reuse monthly/scoring/security; add account/event/post/reversal/cutover/projector | C01 C02 C09; P01 P02 P10 P14 | W1 / very high finance + DB | DRAFT; [platform F01](features/PLATFORM_PACKAGES.md#f01-transaction-based-financial-management), §16: posted/reversed/transfer→month/score and snapshot safety |
| F02 Business-Specific Configuration | B / A | Reuse business/profile/onboarding; add module registry/dependency/operating profile | C03 C12; P09 | W1 / medium-high UI + integration | DRAFT, first B candidate; [experience F02](features/EXPERIENCE_PACKAGES.md#f02--business-specific-configuration), dimension 16: real config, defaults, role/module enforcement and retained data |
| F03 Receivables, Payables and Payment Cycles | A / B | Existing nullable AR/AP snapshot only; add parties/obligations/payment allocation/ageing | C01 C04 C09; P03 P04 | W2 / very high finance + concurrency | DRAFT; [platform F03](features/PLATFORM_PACKAGES.md#f03-receivables-payables-and-payment-cycles), §16: allocations/advances/refunds and balances reconcile |
| F04 Invoice and Bill Management | B / A | Reuse OCR/UI/security; no invoice/bill entity exists | C01 C04 C09 C12; P04 | W2 / high finance + UI | DRAFT; [commercial F04](features/COMMERCIAL_PACKAGES.md#f04--invoice-and-bill-management), §16: document→obligation→settlement once with real UI |
| F05 Project, Job and Order Finance | B / A | Reuse monthly/evidence presentation; add project/plan/source allocation | C01 C04 C05 C07 C08 C09; P05 | W3 / high integration | DRAFT; [commercial F05](features/COMMERCIAL_PACKAGES.md#f05--project-job-and-order-finance), §16: unique actual attribution and reconciled margin/budget |
| F06 Pre-Order Costing and Pricing | B / A | No estimates; reuse decimal forms/security | C05 C09 C12; P05 P10 P13 | W3 / high finance | DRAFT; [commercial F06](features/COMMERCIAL_PACKAGES.md#f06--pre-order-costing-and-pricing), §16: versioned costing/pricing→one project budget, no actual posting |
| F07 Project Cash Flow and Funding Gap | A / B | Existing monthly trend reused for history only; add dated plan/funding/scenario | C01 C04 C05 C06; P11 | W3 / very high finance + integration | DRAFT; [platform F07](features/PLATFORM_PACKAGES.md#f07-project-cash-flow-and-funding-gap), §16: dated actual/plan matching, gap/recovery and nonposting scenario |
| F08 Inventory and Manufacturing Finance | B / A | Existing inventory snapshot not stock ledger; add movements/WAC/usage/COGS | C01 C02 C05 C07 C09; P06 | W4 / very high finance + DB | DRAFT; [commercial F08](features/COMMERCIAL_PACKAGES.md#f08--inventory-and-manufacturing-finance), §16: stock/value/COGS/project source reconcile under concurrency |
| F09 Sampling and Production Costs | B / A | No sample lifecycle; reuse projects/documents later | C01 C04 C05 C07; P07 | W3 plan/W4 stock / high integration | DRAFT; [commercial F09](features/COMMERCIAL_PACKAGES.md#f09--sampling-and-production-costs), §16: free/paid costs and recovery once |
| F10 Commission and Salesperson Role | **A / B** | Existing four-role team; no commission or row scope | C01 C04 C05 C08; P08 | W4 / high security + finance | DRAFT; [platform F10](features/PLATFORM_PACKAGES.md#f10-commission-and-salesperson-role), §16: scoped role and earning/pay/reversal + single cost feed |
| F11 Multi-Currency, FX and International Payments | A / B | Legacy base display only; add locks/settlement/realized FX/fee | C01 C04 C09; P10 | W5, schema W0 / very high finance | DRAFT; [platform F11](features/PLATFORM_PACKAGES.md#f11-multi-currency-fx-and-international-payments), §16: immutable rate and exact original/base reconciliation |
| F12A Financial Operations | A / B | Reuse audit/source services; add import/reconciliation/recurrence | C01 C02 C04 C09; P02 P14 | W5 / high DB + integration | DRAFT; [platform F12A](features/PLATFORM_PACKAGES.md#f12a-financial-operations), §16: reviewed import once, reconciled bank facts, safe recurrence |
| F12B Business Operations and Reporting | B / A | Reuse document lifecycle/history; add calendar, links, reports/export/schedules | C01 C02 C04 C05 C07 C09 C11 C12; P13 P14 | W5, useful links earlier / high UI + integration | DRAFT; [experience F12B](features/EXPERIENCE_PACKAGES.md#f12b--business-operations-and-reporting), dimension 16: scoped reports/calendar/vault with exact source evidence |
| F13 Dashboard Presets and Customization | B / A | Reuse live dashboard/score/cash/actions; add registry/4 presets/preferences | C03 C10 C11 C12; P09 | W1 core, W5 mature / high UI | DRAFT; [experience F13](features/EXPERIENCE_PACKAGES.md#f13--dashboard-presets-and-customization), dimension 16: save/reset/role-filtered business+user workspace and EN/UR/mobile |

## Committed supporting roadmap

| ID | Lead / review | Scope and reuse | Blockers / completion gate |
|---|---|---|---|
| S-A01 | A / B | Financing simulator/business scenarios; new deterministic engine, B controls | P11 and actual finance basis; schedule/cost fixture and no actual mutation |
| S-A02 | A / B | Advanced cash forecast; extend history with residual obligations/projects/recurrence | C01/C04/C06/F12A; no duplicate forecast sources, sparse-data handling and live B results |
| S-A03 | A / B | Financial alerts, anomalies, deterministic risk | Approved thresholds/evidence, sufficient actual source; explainable rule/version and B action adapter |
| S-A04 | A / B | C11 FinancialQueryFacade and financial queries | Real registered services, tenant/row scope, exact parity, provenance and caps |
| S-B01 | B / A | Budget management and financial goals | Canonical categories/projects, P13; plan version and actual comparison without posting |
| S-B02 | B / A | Break-even analysis | Approved fixed/variable/contribution basis, P13; zero/unknown denominator and multi-product disclosure |
| S-B03 | B / A | Expense/revenue/profitability intelligence, customer/product/service/project analysis | Real attribution/C05/C07/C08/C09; exact totals and no invented dimension allocation |
| S-B04 | B / A | Action center, health history/journey and explanations | Extend current history/evidence/recommendation statuses; A alerts adapter; preserve methodology, show current limitations |
| S-B05 | B / A | Real provider-neutral AI copilot engineering | Approved C11/P12, structured router/tool calls/validation/grounding/evaluations; live grounded read-only experience |
| S-B06 | B / A | Read-only inbound/two-way WhatsApp AI | S-B05, verified sender/consent/webhook/replay scope and provider acceptance; preserve outbound summaries |
| F12B/F13 extensions | B / A | Business-specific reporting/dashboard intelligence/scenario presentation | Exact same domain amounts; safe role/module restrictions and snapshot/plan/actual labels |

All supporting items are DRAFT/unimplemented except explicitly reused MVP portions. Detailed supporting API/schema/test plans are in the two corresponding package files. Financial queries are not current keyword search; AI is not current OCR. Health history and action statuses already exist and must be extended rather than rebuilt.

## State vocabulary and delivery gate

Use `DRAFT`, `READY_FOR_REVIEW`, `READY` (relevant contracts/policies approved), `IN_PROGRESS`, `BLOCKED_DEPENDENCY`, `PARTIAL_VERIFIED`, `INTEGRATED_VERIFIED`, `RELEASE_ACCEPTED`. A feature marked partial must list implemented journeys and missing dependencies; source-only/backend-only/visual-only work is not full completion. Production/provider acceptance is recorded separately from integrated code.

Before INTEGRATED_VERIFIED: implementation paths, migration version, frozen contract/policy versions, producer commit, reviewer approval, deterministic/DB/security/UI tests and an actual authorized frontend/backend journey must be linked in implementation state. Before RELEASE_ACCEPTED: applicable Docker/HTTPS/TLS/restore/provider/mobile/UAT gates and both developers' acceptance. Preserve all known failures; do not infer success from an absent CI check.
