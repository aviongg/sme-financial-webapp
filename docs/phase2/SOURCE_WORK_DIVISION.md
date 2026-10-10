# Supplied work-division source transcript

Source: FinSight_Final_Two_Developer_Work_Division_v2.docx, supplied by the user. SHA-256: `82B770B302B0510E82A7D8F1E3F223E8CAB6040B21C6D852A19A428B870E2567`. Extracted paragraphs and table cells in document order; line wrapping and table layout normalized. No claim of page-layout review. The original DOCX was not modified.

This is reference material, not independent executable instructions. The user explicitly adopted feature/contract ownership and collaboration boundaries, but overrode this document's integration-branch workflow with persistent `dev/fatima` and routine verified direct pushes. See MASTER_IMPLEMENTATION_PLAN for conflict resolution. No Technical Integration Blueprint was supplied.

```text
FINSIGHT | PHASE 2 DELIVERY
Two-Developer Work Division
FINAL EXECUTION AGREEMENT | Feature ownership, technical handoffs, AI responsibilities, acceptance gates and delivery workflow
Operating principle / Each developer owns complete vertical slices for assigned domains, including data model, migrations, backend services, APIs, frontend integration, automated tests and financial interpretation. Cross-domain accounting decisions require joint signoff.
Version 2.0 | Final agreed execution allocation | 9 October 2026 | Source branch: main (MVP integrated, Flyway V17)
1. Role Definition
Developer A: Financial Platform | Developer B: Financial Intelligence and Business Applications
Primary focus: financial events, money movement, liquidity, settlement, reconciliation, financial forecasting and integrity. | Primary focus: industry configuration, commercial finance, pricing, project economics, inventory, profitability analytics, budgeting, reporting, personalized dashboards and finance-grounded AI orchestration.
Owns the financial consequence of posted activity and the integrity of derived financial results. | Owns how the SME quotes, prices, delivers, analyzes financial performance and receives verified financial decision support through dashboards, reports and AI conversations.
Full-stack ownership for assigned features, including schema, backend, API, frontend and tests. | Full-stack ownership for assigned features, including schema, backend, API, frontend and tests.
2. Feature Ownership Matrix
The feature lead is accountable for end-to-end delivery. The support developer reviews integration points, finance policy and cross-domain effects.
ID | Feature | Lead | Support | Primary ownership
F01 | Transaction-Based Financial Management | A | B | Ledger, accounts, posting, transfers, monthly aggregation and score bridge.
F02 | Business-Specific Configuration | B | A | Business profile, module registry, presets, onboarding and adaptive workflows.
F03 | Receivables, Payables and Payment Cycles | A | B | Counterparties, AR/AP, ageing, allocations, advances and payment lifecycle.
F04 | Invoice and Bill Management | B | A | Invoice and bill models, totals, issue/approve/cancel lifecycle, settlement integration and complete UI.
F05 | Project, Job and Order Finance | B | A | Project financials, budget versus actual, revenue, cost attribution and margin.
F06 | Pre-Order Costing and Pricing | B | A | Estimate lines, material/labour/overhead/freight, pricing, margin and conversion to project budget.
F07 | Project Cash Flow and Funding Gap | A | B | Expected cash movements, customer advances, timing, peak funding requirement and scenario calculations.
F08 | Inventory and Manufacturing Finance | B | A | Inventory movements, weighted-average cost, material usage, stock value, COGS and manufacturing finance.
F09 | Sampling and Production Costs | B | A | Free and paid samples, planned versus actual cost, recovery and profitability.
F10 | Commission and Salesperson Role | A | B | Commission lifecycle, salesperson financial scope, posting timing, role restrictions and related reporting.
F11 | Multi-Currency, FX and International Payments | A | B | Rates, locked historical rates, settlements, realized FX, gateway fees and foreign-currency flows.
F12A | Financial Operations | A | B | Bank reconciliation, recurring transactions, import processing, source integrity and audit linkage.
F12B | Business Operations and Reporting | B | A | Financial calendar, document vault integration, automated reports, export and reporting experience.
F13 | Dashboard Presets and Customization | B | A | Widget registry, presets, user preferences, personalization and business-specific dashboards.
3. Supporting Finance and Intelligence Responsibilities
Developer A
Financing simulator and business-wide scenario calculation engine.
Advanced cash-flow forecasting using AR/AP, projects, recurring obligations and historical behavior.
Financial health alerts, anomaly rules and risk calculations where deterministic financial logic is required.
FinancialQueryFacade, authorization, evidence provenance and read-only finance query tools for AI and WhatsApp.
Posting integrity, financial regression fixtures, idempotency, concurrency and numerical invariants.
Developer B
Budget management, financial goals and break-even analysis tied to canonical categories and project economics.
Expense intelligence, revenue intelligence and profitability analytics, including customer and product/service profitability views where source data is available.
Action center, financial health history, explainability and financial health journey presentation.
Hands-on AI engineering: LLM/provider integration, approved financial-tool calling, structured prompts and tool schemas, orchestration, response grounding/citations, evaluation datasets and tests, safe fallbacks, English/Urdu responses, copilot UI and read-only two-way WhatsApp conversational delivery. AI cannot independently calculate or post authoritative financial values.
Business-specific reporting, dashboard intelligence, scenario controls and operational KPI presentation.
Joint finance rule: Each developer owns the finance interpretation of their assigned features. Any decision changing recognition, valuation, cash, AR/AP, inventory, commissions, FX, profitability, health-score inputs or AI financial tool semantics requires documented cross-review before merge. Neither developer may introduce an independent financial calculation of the same metric.
4. Shared Contract Ownership
Contracts are frozen before dependent implementation. Each owner provides fields, semantics, example request/response, permissions and a contract test or fixture.
Contract | Owner | Scope
C01 | A | Transaction event and posting
C02 | A | Monthly projection and cutover
C03 | B | Business configuration and module resolver
C04 | A | Counterparty, obligation and payment
C05 | B | Project cost attribution
C06 | A | Cash plan and funding gap
C07 | B | Inventory and COGS
C08 | A | Commission lifecycle, sales attribution and salesperson authorization/row scope (B reviews; B consumes approved commission cost in project profitability)
C09 | A | FX and settlement
C10 | B | Dashboard workspace
C11 | A | FinancialQueryFacade
C12 | B | API and i18n conventions
5. Technical Ownership and Git Workflow
Each feature owner owns the migration, entities, repositories, services, DTOs, APIs, frontend route or components, tests and documentation for that feature.
Migration numbers are reserved before implementation in the shared implementation-state document. The author owns the migration and the other developer reviews it before merge.
Neither developer acts as a universal database gatekeeper. Each developer is accountable for database quality inside their own domains.
Shared files such as navigation, dashboard shell, onboarding, i18n and security configuration have one PR owner at a time to avoid merge conflicts.
Every feature is developed on a short-lived issue-focused branch from the shared Phase 2 integration branch, created from the current integrated main (PR #3). PRs are reviewed and merged to phase2/integration, then promoted to main by accepted wave/release PR; no direct feature commits to main.
No feature is complete when only the backend or only the UI exists. Completion requires a real authorized user journey with tests.
6. Financial Policy and ADR Ownership
Decision area | Draft owner | Required reviewer
Ledger, opening balances, cutover and duplicate prevention | A | B
Receivables, payables, advances, refunds and payment allocation | A | B
Invoice and bill recognition policy, including issue versus cash receipt | B | A
Project revenue/cost attribution and profitability formula | B | A
Inventory capitalization, WAC, freight, returns and COGS | B | A
Sampling cost treatment and paid/free recovery | B | A
Commission earning, accrual and payment timing | A | B
FX locked rate, realized difference and gateway fee classification | A | B
Dashboard module/role precedence and restricted views | B | A
7. Testing and Acceptance Ownership
Developer A acceptance focus
Ledger posting, transfers, reversals and monthly aggregation.
AR/AP, partial allocations, advances and payment settlement.
Funding gap, financing scenarios and advanced cash-flow forecasting.
Commission posting, FX settlement, reconciliation and import integrity.
Cross-business authorization, financial security, concurrency and numerical reconciliation.
Developer B acceptance focus
Business configuration, module enablement and adaptive navigation.
Invoices/bills, project costing, pricing, inventory valuation and sample economics.
Budgets, goals, break-even, profitability analytics and business-specific reports.
Dashboard presets, customization, EN/UR/RTL, mobile behavior and role/module visibility.
Commercial and operational end-to-end journeys, including invoice, project and manufacturing workflows.
Joint gate: At the end of each implementation wave, both developers run the combined end-to-end journey and verify that operational events reconcile to the financial source of truth without double counting.
8. Production and Release Responsibilities
Developer A | Developer B
PostgreSQL integrity, migration verification, backup/restore validation, financial regression, source reconciliation and finance/security checks. | Application and Docker deployment, HTTPS/browser integration, role/module behavior, mobile and Urdu/RTL verification, UAT and release documentation.
Production checks for A-owned services and financial invariants. | Production checks for B-owned services, workflows, reporting and customer-facing experience.
9. Final FYP Demonstration Ownership
Developer A demonstration
Financial platform flow: transaction or payment -> AR/AP -> accounts -> MonthlyRecord -> score/forecast -> reconciliation or FX/funding analysis.
Developer B demonstration
Commercial and operations flow: business setup -> estimate/project -> inventory/sample -> invoice/bill -> profitability/reporting -> customized dashboard.
Combined demonstration: The final FinSight walkthrough connects both flows so operational events create traceable financial consequences and the financial engine returns intelligence back into the business workflow.
10. Working Agreement
One lead per feature and one reviewer. No vague shared ownership without a named driver.
Both developers participate in finance policy decisions and technical implementation, not only presentation or review.
Each PR includes the user story, financial/data invariants, schema changes, API contract, tests, UX evidence and release notes.
Changes to financial semantics require explicit review before code is merged.
Implementation progress, blockers, contract changes and migration reservations are recorded in one shared Phase 2 implementation-state file.
At each wave end, work can be reassigned if one queue becomes materially heavier or a dependency creates an avoidable critical path.
11. Verified Baseline, Scope and Start Conditions
Current code baseline — source of truth / GitHub repository: aviongg/sme-financial-webapp. On 9 October 2026, main is at b51e27696cccc59b94aa9d9c9388ad0faa5b4176, merged by PR #3. The source includes codex/mvp-core-refinement at da62a4164f3ed90a9d1a75f6a3e75da5c881c4a1; existing Flyway V1–V17 migrations are retained. Begin all Phase 2 work from the current main, never from the older dev branches. The merged code does not mean production/provider acceptance is complete.

Before Phase 2 PRs: both developers fetch main, read docs/MAIN_INTEGRATION_HANDOFF.md and the master Phase 2 Technical Integration Blueprint, and record the exact initial main SHA in docs/PHASE2_IMPLEMENTATION_STATE.md.
Create phase2/integration from verified main. Protect main and phase2/integration from force-push; preserve dev/fatima, dev/suleman and historic codex branches.
Run the existing baseline test suite on an environment able to run it; distinguish local unit/contract checks from actual PostgreSQL, Docker and external-provider acceptance.
Treat 13 agreed additions (F01–F13; F12 split into F12A and F12B) as the committed scope. The roadmap beyond those additions is classified in Section 17; deferred items require a separate decision.
Use approved architecture: Next.js 16 / TypeScript frontend; Spring Boot Java / PostgreSQL / Flyway financial core; existing FastAPI OCR kept intact. AI integration may add an isolated module/service only after an ADR and financial query contracts are stable.
12. Hard Ownership Boundaries and Cross-Team Handoffs
These rules are mandatory: one lead owns the full vertical slice (schema, business rules, services, endpoints, UI, tests and documentation); the other developer is the required reviewer at handoff points. “Support” does not mean responsibility disappears.
Boundary / contract | Owner | Input -> output / mandatory rule | Reviewer
C01 Posting and transaction event | A | Posted actual financial event, currency/rate/category/account movements, linked source IDs and idempotency; single posting API/ledger. | B
C02 Month projection and cutover | A | Posted source events -> deterministic MonthlyRecord fields -> existing ScoringService; preserve historical snapshot mode. | B
C03 Business and module configuration | B | Business operating answers -> enabled modules, labels, feature availability; no deletion when module turned off. | A
C04 Counterparty, obligation, settlement | A | Customer/supplier obligations and payments; partial allocation, advances, refunds and aging; invoice/bill consumers must not own a second outstanding balance. | B
C05 Project cost attribution | B | Estimate/project/budget plus linked actual source refs -> revenue/cost/margin/budget variance; no planned data posted as actual. | A
C06 Project cash plan and funding gap | A | Planned dated receipts/outflows, advances and actual settlements -> peak cash requirement, recovery timing and assumptions. | B
C07 Inventory valuation and COGS | B | Inventory movements, weighted-average cost, issue/usage events -> stock valuation, project material costs and canonical COGS feed. | A
C08 Sales attribution, commissions, row scope | A | Salesperson ownership and commission earning/paid rules; server-side access checks. B consumes authorized commission costs and order views. | B
C09 FX and settlements | A | Locked rates, original/base amounts, realized settlement differences and gateway-fee treatment. | B
C10 Dashboard workspace | B | Preset registry + enabled modules + authenticated role + user overrides -> allowed dashboard layout; no unauthorized widgets. | A
C11 FinancialQueryFacade | A | Authorized, business-scoped read-only tools -> deterministic amounts, units, evidence/time and reason for unavailable output. | B
C12 API and i18n conventions | B | Shared DTO naming, field conventions, English/Urdu/RTL standards and UI validation; each feature owner still authors its own endpoints and translations. | A

12.1 Specific overlap prevention
Invoice/bill recognition (F04 B) -> financial obligation/payment (F03 A): B owns document status and line totals; A owns the obligation ledger, outstanding balance and allocation. Both sign an ADR defining what issuance versus payment changes in revenue, expense and cash.
Project costing (F05/F06/F09 B) -> funding gap (F07 A): B owns project, estimate and budget identifiers and cost categories; A reads approved plans and authoritative posted events, never reimplements project cost aggregation.
Inventory (F08 B) -> monthly COGS (F01 A): B produces signed or traceable financial cost events; A owns mapping into MonthlyRecord. Confirm goods capitalization/returns/freight policy before implementation.
Commissions (F10 A) -> project profitability (F05 B): A owns commission rule, earning and payment; B receives the recognized/allocated cost with an immutable source reference and does not create a second commission record.
OCR (existing system + F12B B) -> ledger posting (F01 A): B owns reviewed/confirmed document mapping and navigation; A owns ledger-mode posting and idempotent financial contribution. Snapshot-mode contribution remains compatible; no OCR auto-post without review.
AI (C11 A) -> copilot orchestration (B): A produces authorized functions and audited deterministic results; B owns provider integration, tool schemas, agent orchestration, refusal/fallback, evaluation and readable bilingual explanations. No AI writing to books without separate signoff.
13. Implementation Waves and Mandatory Release Gates
All dates and durations are intentionally unassigned. “Wave” is a dependency and acceptance unit—not a promise that equal calendar time is required. Work can run concurrently only after the specified contract is frozen.
Wave / prerequisite | Developer A lead | Developer B lead | Wave exit / integration gate
W0. Baseline and contracts | Record main SHA; verify financial baseline tests; draft C01/C02/C04/C09/C11 policy contracts. | Record frontend baseline; draft C03/C05/C07/C10/C12; reserve migration numbers and implementation-state doc. | Both approve ADRs, C01–C12 interface stubs, reference fixture values and PR conventions. No source changes to old migrations.
W1. Foundation (none) | F01: accounts, posted ledger, transfers, source fingerprints, monthly projection/cutover, score/advice bridge. | F02: operating profile, module registry/onboarding; F13: preset registry/framework and dynamic navigation (core widgets only). | Post/void/transfer trace to MonthRecord/score; old snapshots safe; module visibility/roles verified.
W2. Money owed (W1) | F03: counterparties, AR/AP, partial receipts, aging, allocation, payment cycles. | F04: invoice/bill lifecycle, validation, totals, document UI linked to A obligations. | Issue -> obligation; payment -> posted movement -> outstanding/month projection once. No cash at invoice issue.
W3. Project differentiator (W2) | F07: project cash plans, advance timing, funding gaps and scenario engine using B project contracts. | F05/F06/F09: projects, budgets, pre-order pricing/estimate conversion, paid/free samples, actual attribution. | Estimate -> project without actual posting; planned and real cash clearly separate; funding gap and margin reconcile.
W4. Manufacturing + sales (W3) | F10: SALESPERSON row scope and commission lifecycle; commission feed for project economics. | F08: inventory purchases/issues, WAC valuation, project material usage, stock/COGS integration. | Posting integrity and restricted salesperson routes pass; material usage and commission cost each count once.
W5. Settlement and operations (W2/W4) | F11: rate locking and realized FX/gateway settlements; F12A: reconciliation/import staging, recurring finance rules. | F12B: financial calendar, document links/vault, reporting/export and operational workflows; F13 preset widgets mature. | Foreign-currency settlement, import duplicates, reconciled balances and reporting values match canonical sources.
W6. Financial intelligence (W1–W5) | Financing simulator, business-wide forecasts, financial risk/anomaly rules, authorized C11 FinancialQueryFacade. | Budgets/goals/break-even, cost/revenue/profitability analytics, action center and health presentation, business-specific dashboard analytics. | Fixed data fixtures support all analytics and alerts; stale or unavailable evidence cannot be presented as fact.
W7. AI + WhatsApp (W6) | Harden approved finance tools, permissions, evidence IDs/timestamps and rate/usage limits; review every financial tool. | LLM tool integration, agent/conversation orchestration, grounding, structured outputs, evaluations, copilot UI, read-only inbound WhatsApp, bilingual delivery. | Adversarial/cross-tenant/prompt-injection tests pass; no fabricated balance; safe unavailable states; no unauthorized actions.
W8. Integrated acceptance (all) | Database/financial tests, migration upgrade, live posting, production-backup/restore evidence, financial security and release signoff. | Real frontend/browser + backend journeys, role/module/EN/UR/mobile, Docker/HTTPS acceptance, pilot UAT, documentation and AI/provider checks. | Both sign combined end-to-end report; merged PRs, documented outstanding production/provider gates and customer-safe release decision.

13.1 Early design decisions that avoid rework
Design original/base currency, locked rates and payment allocation semantics during W0/W1 even though full FX implementation is W5; do not bolt currency onto invoicing after the fact.
Define the project identifier and cost category contract in W0; A can implement funding-gap calculators against fixed fixtures while B completes project persistence.
Dashboard framework can ship in W1 with existing score/cash widgets; do not block it until every later module has a widget.
Developer A owns numerical finance rules and financial query tools; Developer B owns analysis UI and AI orchestration. Both remain full-stack developers on their assigned vertical slices.
Use W8 as a real release gate. A completed code merge does not prove Docker/TLS/backup restore, SMTP, OCR provider or WhatsApp live provider acceptance.
14. Feature Deliverables and Definition of Done Register
A lead may not mark a package DONE until all listed outcomes are demonstrated against the real authorized backend and the required regression suite; screenshots or UI-only mock tests are insufficient.
Package and lead | Minimum implementation deliverable | Independent acceptance evidence
F01 — A | Financial accounts; posted transactions; movements/transfers/void; ledger mode and month projection; score/advice bridge. | Recomputed month is idempotent; transfer never adds revenue; old monthly records unaffected; tenant & concurrent posting tests.
F02 — B | Operating profile, module dependencies/toggles, industry onboarding and dynamic menu/workflows. | Toggle preserves data; unauthorized modules hidden and blocked; old users safe-default.
F03 — A | Customers/suppliers, receivable/payable obligations, advances, credit terms, aging, partial payments and allocations. | Overpayment/duplicate settlement handled; aging balances and cash projection reconcile.
F04 — B | Invoice/bill line items, issue/approve/cancel, document templates and payment status; C04 links. | Issued bill/invoice creates correct obligation once; receipt/payment updates status and cash once; template does not change figures.
F05 — B | Project/job/order model; linked actuals, status, budgets, profit/margin by work unit. | Project financials reconcile to linked transaction/invoice/inventory/commission sources.
F06 — B | Pre-order estimates, cost categories, unit pricing, margin model, versions, convert to project. | Estimates/scenarios never post actuals; conversion is idempotent.
F07 — A | Dated cash plan, customer advance, peak funding requirement, recovery date, project scenarios. | Fixture-based funding-gap outputs; actual vs planned differentiated; project linkage tenant-safe.
F08 — B | SKU/material categories, stock purchase/use/adjust, weighted-average valuation, project inventory cost, financial COGS. | Weighted average matches fixtures; negative-stock policy; month/project postings reconcile.
F09 — B | Free/paid sample lines, planning, unrecovered costs and profitability impact. | Free sample charges zero revenue; actual sample cost not duplicated in order costing.
F10 — A | Salesperson role, row-level access, assigned sales, commission earning/approval/payment and reporting source. | Cross-salesperson ID denied; retried events never earn twice; B project margin uses source cost.
F11 — A | Rate history/lock, foreign invoice/payment settlement, FX differences, payment gateway fees. | Historical posted values immutable; realized FX and fees reconcile.
F12A — A | Statement import preview/confirm, reconciliations, recurring rule drafts, audit/source idempotency. | No post at preview; duplicate imports blocked; reconciled totals match posted ledger.
F12B — B | Calendar, document links/vault, reporting/exports, business report UI. | Calendar uses underlying due events; OCR draft remains unposted; exports equal canonical calculations.
F13 — B | Industry preset library, allowed-widget registry, user hide/add/reorder/resize/reset and saved preferences. | Four starter presets pass; reset and business switch isolation; role/module filter wins over user override.

14.1 Additional intelligence packages (explicitly in scope)
Owner | Committed subwork | Acceptance
A | Financing simulator; advanced cash forecast from AR/AP, projects, recurring rules and history; deterministic alert/anomaly/risk engine. | Forecast and risk show source period/evidence/assumptions; no invented history; thresholds tested.
A | FinancialQueryFacade with read-only authorized operations and evidence metadata; query-tool tests. | Cross-tenant denial; metrics exactly equal ordinary backend financial endpoints; no raw SQL generated by LLM.
B | Budgets/goals, break-even, expense/revenue/profitability intelligence (customer, product/service or project only where attribution exists). | All ratios reconcile to source values; missing data is visibly unavailable; business types receive appropriate advice.
B | Health journey, score explanations, action center and dashboard KPIs built by extending existing score/history/recommendation lifecycle. | Existing score methodology and statuses reused, not reimplemented; evidence and actions remain current.
B | Financial copilot and WhatsApp conversational AI: LLM adapter, controlled tools, orchestration, prompt controls, structured response validation, evaluations, bilingual UI. | Grounded read-only financial answers, correct refusal/no-data, auth/consent isolation, no autonomous posting, provider acceptance recorded.

15. Dashboard Preset Specification (F13 — B)
Presets are curated, business-aware starting dashboards. Business type determines the recommended default, but a user can add/remove/reorder/resize permitted widgets and reset to the latest default. B owns the registry, persistence of overrides and user experience; A reviews data authority and restricted financial widgets.
Starter preset | Mandatory priority widgets | Customization constraints
Manufacturing | Stock/material value, COGS, order margin, receivables/payables, cash, health score. | No inventory widget if module disabled or no data; no fabricated stock.
Retail / trade | Sales trend, gross margin, inventory/stock, cash position, payables, health score. | Business-category configuration determines relevance.
Services | Client profitability, invoices/overdue AR, monthly expense, cash, health score. | No project margin without a linked project data basis.
Project / order | Active jobs, estimate vs actual, project funding gap, deadlines/cash plan, project margin, health score. | Show unavailable project fields as pending until cost/revenue links exist.

Resolution precedence (non-negotiable): authenticated role/row scope -> enabled modules -> allowed-widget registry -> industry preset -> individual user overrides. Permissions can never be overridden by customization.
Widgets need stable keys and backend-calculated summary DTOs; no AI-created financial figures. Store overrides per business + user and preserve them if a module is disabled.
UX acceptance: preview recommended preset during onboarding; Customize mode supports add/hide/reorder/size and Save/Cancel; Reset applies current approved business preset; desktop/mobile EN/UR/RTL all tested.
Performance: batch KPI summaries through a backend workspace endpoint; avoid one network request per widget. Default health-score/action/score-evidence widgets remain accessible.
16. AI Engineering Agreement (A data tools ↔ B orchestration)
Bounded first AI release / AI must be explanatory and read-only in the first release. It can answer questions and suggest user-facing next actions using authorized, deterministic financial service results; it must not autonomously change accounts, invoices, project budgets, commissions or payment records. Do not substitute LLM arithmetic for the authoritative financial engine.

Developer A — financial tools and trust boundary
Publish tool catalog with typed JSON schemas: getCashPosition, getOverdueReceivables, getUpcomingObligations, getProjectFinancials, getProjectFundingGap, getMarginDrivers and getFxSettlementImpact (only where modules implemented).
All tools resolve tenant and role on the server, enforce row-level restrictions and return structured value, currency, asOf/date interval, completeness, source/evidence IDs and safe unavailable/restricted result.
Implement business scoped rate limits / query caps / audit records and prohibit arbitrary SQL/search-as-SQL from LLM output.
Own fixture-based tool accuracy and permission regression testing; review all financial question-to-tool mappings.
Developer B — actual AI engineering and user experience
Choose an LLM provider adapter only after approved ADR (cost/privacy/deployment/security) and maintain configurable model/provider selection without hard-coding secrets in clients.
Implement query interpretation, deterministic tool-selection/router, bounded multi-step agent orchestration, maximum tool/turn limits, structured response schemas, timeouts/retries and safe fallback states.
Implement grounding/provenance: disclose evaluated period and source basis; do not invent a number or imply causal certainty. Display source/evidence context for the answer.
Create tests and evaluation dataset: numerical accuracy, wrong-tool selection, fabricated facts, missing data, stale evidence, unauthorized data, prompt injection, adversarial instruction, English/Urdu and low-confidence fallback.
Implement in-app copilot interface, conversation history/retention policy decision, optional safe clarification questions and role-aware response display.
Extend existing WhatsApp integration only with verified inbound webhook, identity + opt-in mapping, read-only tool path, bilingual content, audit/logging minimization and provider-specific acceptance. Preserve scheduled-summary flow.
AI acceptance signoff: B owns the AI integration PR and evaluation report; A signs off tool data integrity and authorization. Both must approve enabled production provider routing and data-handling policy.
17. Included Enhancements, Deferred Features and Change Control
Classification | Items | Decision
Committed Phase 2 | F01–F13 including F12A/F12B; advanced cash forecasting, financing/scenarios, budget/goals/break-even, financial alerts, profitability analysis, reporting, action center, business-aware advice, AI/WhatsApp tool explanation. | Assigned to A/B above; scope controlled by explicit DoD.
Reuse existing MVP | Authentication/MFA/session/CSRF, business switching, team invitation fundamentals, scoring, health history/explanations, recommendation status, OCR lifecycle, WhatsApp consent/delivery, Zakat, audit, search. | Extend existing code only; no replacement systems.
Deferred / separate approval | Multi-branch operating hierarchy, regulatory tax filing automation, external benchmarking data service, full ERP/production routing, full double-entry accounting, autonomous money movement or AI write actions. | Not silently required in these waves; raise approved ADR/change request with revised estimate/owner.
Environment acceptance separate | Docker/HTTPS/browser against deployed stack, production PostgreSQL/TLS/grants, real encrypted restore, SMTP delivery, Google OCR provider and WhatsApp provider. | Still required for release where applicable; tests using fixtures do not count as live-provider acceptance.

18. GitHub, Migration and PR Operating Procedure
1. Both fetch main and verify SHA; create/refresh phase2/integration from main only. Record every phase PR and migration reservation in docs/PHASE2_IMPLEMENTATION_STATE.md.
2. For each issue create phase2/<wave>-<feature>-<short-name> from phase2/integration; implement one complete vertical slice. Merge to integration only by reviewed PR, never direct push to main.
3. Owner drafts ADR/contract before schema/API; reviewer approves both before expensive implementation. Reserve next Flyway migration number in the shared file to avoid collisions.
4. Implement migration, models, repositories, service calculations, DTO/API permissions, frontend navigation/workflows, translation, unit/integration/UI tests, and user-facing documentation in the same task scope.
5. Run affected tests plus full shared regression at the wave gate. Reviewer verifies cross-team numeric contracts, tenant/role access and no duplicate source of truth.
6. Merge accepted PR to phase2/integration; both run integrated journeys and record evidence; promote a tested release/wave PR into main. Preserve older branches and migration history.
7. If blocked by a dependency, implement mock-free contract fixtures and tests in isolation; do not invent a divergent API or use live UI mock fallback.
8. A financial semantic change (AR/AP recognition, cash, COGS, commission, FX, revenue, budget vs actual) requires an ADR plus approval from both A and B; an unreviewed change cannot merge.
18.1 Minimum PR checklist
User story and acceptance fixtures identified by Fxx / Cxx IDs.
Explicit business/tenant authorization and row scope; no client-provided financial business ID.
New Flyway number and both fresh + upgrade-path migration tests (if schema changed).
Authoritative financial semantics and no accidental impact on existing MonthlyRecord/scoring/Zakat.
API requests/responses, status transitions and idempotency documented.
Live frontend path with loading/empty/error state and EN/UR/RTL/mobile validation.
Automated tests: unit + relevant PostgreSQL + frontend contract + combined flow; no hidden skips.
Financial/AI security review from supporting developer, plus wave report entry.
Rollback by disabled module/feature gating where feasible; no destructive data rollback.
19. Test Ownership and Final Acceptance Journeys
Developer A signs off
Posted transactions/transfers/reversals; account balance; monthly projection; legacy record cutover and unchanged scoring method.
Receivable/payable outstanding, partial payments/advances, invoice settlement, FX and fee treatment.
Project cash flow and funding gap, commission earning/payment, financing scenarios, forecast and risk integrity.
PostgreSQL tenant isolation, role permissions, race/idempotency, migrations, real backup/restore where available.
FinancialQueryFacade authorization, freshness/evidence and exact numerical results.
Developer B signs off
Operating profile, modules/onboarding/navigation; invoice/bill creation and role constraints.
Pre-order estimate -> project conversion -> budget/actual -> inventory/sample allocation -> profitability/reporting.
Four dashboard presets, per-user customization and role-allowed rendering across mobile and EN/UR/RTL.
Business-aware insights, goals, break-even, action center and prior score/history reuse.
AI tool orchestration and evaluation report; in-app/WhatsApp read-only answers with provider consent and failure handling.
Both run these combined end-to-end journeys
Manufacturing: setup/preset -> estimate with samples -> project/order -> supplier bill/raw-material issue -> customer invoice/advance -> project margin + funding gap + stock value -> dashboard.
Service/project: business setup -> quoted job -> actual operating cost -> customer invoice -> overdue AR -> cash projection -> risk recommendation -> report.
Exporter: foreign-currency invoice -> partial international settlement + gateway fee -> realized FX difference -> project/base currency margin -> account balance.
Salesperson: invitation -> own order/customer/commission view -> forbidden access to other salesperson/owner cash -> suspension cuts access.
Existing customer: historical monthly records -> selects ledger cutover -> new daily transactions -> derived monthly score; no duplicate cash or invented historical rows.
AI: asks “why did profit fall?” -> B tool planner -> A authorized finance tools plus B profitability service -> grounded explanation with source date/amount -> correct unavailable/refusal/cross-tenant behavior.
20. Delivery Governance and Final Approval
Mandatory meeting rhythm (team can agree timings) / At the start of each wave: freeze required financial policy ADRs and cross-domain contracts. During implementation: update one shared state file after each substantive PR, with blocked dependencies. At wave end: both review the integrated financial journey and re-estimate the next wave. Do not silently change feature ownership or redefine completion.

Lead owns delivery and defect fixing of the assigned package; reviewer owns review response, integration acceptance and cross-domain correctness signoff.
If one developer becomes the critical-path blocker, split the next slice only by approved reallocation recorded in the same work agreement; do not let the non-lead independently implement a competing calculation.
Any additional feature beyond the Section 17 committed scope needs change request stating rationale, financial semantics, dependencies, rough effort and proposed owner; both must approve.
Production release requires both developers to approve the release notes, test evidence, known limitations, backup/restore readiness and any external-provider acceptance gaps.
No PR is DONE solely because files are merged: feature behavior must be verifiable through actual authorized user/API flow and regression tests.
FINAL WORK DIVISION TO FOLLOW / Developer A = Financial Platform and Trusted Financial Query Tools. Developer B = Financial Intelligence, Commercial Applications and AI Orchestration. The F01–F13 assignments, F12A/F12B split and C01–C12 contract owners in this document are authoritative. The current merged main is the starting baseline; Sections 11–20 define the mandatory integration sequence and acceptance procedure. Developer B is the recommended role for the contributor prioritizing financial analysis and future AI engineering.
```
