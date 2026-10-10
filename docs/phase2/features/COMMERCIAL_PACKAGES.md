# Developer B commercial package specifications

Design status: **PROPOSED / NOT IMPLEMENTED / NOT CONTRACT-FROZEN**. Prepared from the inspected MVP and final work division, 10 October 2026. This file expands F04, F05, F06, F08 and F09 across all sixteen requested implementation dimensions. It does not authorize unreviewed financial policy or reassign Developer A's responsibilities. The master register, integration contracts and financial-policy decision log control readiness.

## Shared design rules for all five packages

- B is the full-stack lead; A is required reviewer for financial effects. B owns C05 project attribution and C07 inventory/COGS. A owns C01 posting, C02 projection/cutover, C04 counterparties/obligations/payments, C06 cash plans/funding, **C08 commissions/sales authorization**, C09 FX and C11 authorized financial queries. C08 is never a B-owned commission implementation.
- Actual Java root is `backend/src/main/java/com/app/sme_health_backend/`; corresponding tests belong under `backend/src/test/java/com/app/sme_health_backend/`. Proposed package/class paths below extend that root. Existing paths are labeled **reuse**; all other domain classes, tables, routes, permissions and endpoints below are **proposed**.
- Use existing cookie session, CSRF, `ActiveBusinessContext`, `BusinessAuthorizationService`, `SecurityAuditService` and error handling. Derive business ID on the server; do not trust a business UUID in a financial write body. Cross-domain references must resolve in the same authorized tenant. Background jobs receive explicit trusted business and system-actor context.
- New resource APIs are under `/api/v2/`; preserve `/api/records/monthly`, document and other legacy contracts. Existing `frontend/src/lib/api/client.ts` accepts `/v2/...` because it prepends `/api`. Use its cancellation/session-generation protections during business switching and do not automatically replay failed financial writes.
- UUID identifiers, ISO dates, UTC instants and explicit business-effective dates are separate. Money/rates/quantities use JSON decimal **strings**, not JavaScript binary floating point. Null means unknown/unavailable where permitted; zero means a known value. `CurrencyInput.tsx` currently accepts numbers and hardcodes PKR labels: reuse its visual pattern, but add a tested decimal-string/currency component for new contracts without silently changing legacy forms.
- Candidate DB capacities for review: money `NUMERIC(19,4)`, quantity/unit cost `NUMERIC(19,6)`, rate `NUMERIC(24,12)`. These capacities do not approve four-decimal cash settlement or a universal rounding rule. C01/C09/C12 must freeze supported currencies, maximum values, intermediate scale, line/document rounding and residual allocation before implementation. Legacy monthly values remain `NUMERIC(14,2)` until an additive compatibility decision.
- Every mutable draft has `version BIGINT`, tenant FK, created/updated times and actor. Commands that issue/approve/convert/post use an `Idempotency-Key` plus payload hash; same key/same payload returns the original outcome, same key/different payload conflicts. Updates carry the expected version. Status changes occur through dedicated commands, never arbitrary mass assignment. Original issued/posted history is immutable; approved reversal/amendment routes preserve lineage.
- Common response: `{id, version, status, currency, calculatedTotals, sourceRefs, createdAt, updatedAt}` with domain fields. A `sourceRef` is `{type, id, lineId, revision}` as defined by C01 (type includes the domain; lineId is nullable); financial outputs additionally identify authoritative posting/projection revision and availability. List contracts accept bounded page/cursor, date/status filters and return `items,nextCursor`; scoped lookups return 404 for missing or other-tenant IDs. Malformed input returns 400; unauthorized action 403; stale version/state/idempotency conflict 409; valid-but-unapproved financial policy/dependency has an explicit machine code and creates no actual. Exact error envelope is C12 DRAFT.
- Canonical categories come from C01. B may own display labels or project budget groupings mapped to category IDs; no parallel authoritative revenue/expense taxonomy. New financial operational actuals are disabled in snapshot-only businesses until the approved C01/C02 compatibility/cutover path exists. Planning/draft work may be enabled independently if clearly non-posting.
- New migrations must be additive and reserved in `../IMPLEMENTATION_STATE.md` before coding. Candidate table names and groups below are not assigned Flyway numbers. Include named checks, scoped unique indexes and composite `(business_id, id)` keys/FKs where needed to prevent valid-but-cross-tenant references. Preserve V1–V17 checksums and roles/append-only protections.

## F04 — Invoice and Bill Management

### 1. Ownership and scope

B leads the complete schema/services/API/UI/testing slice; A reviews and supplies C04 obligations/payment allocation, C01 posting, C02 monthly projection and C09 money semantics. B owns commercial document lines, totals and lifecycle. **A owns outstanding receivable/payable balances and settlement; B must not persist a competing balance authority.**

### 2. Business objective and workflow

An authorized user selects a customer or supplier, creates a draft invoice or bill with dated lines, previews server-calculated totals, attaches supporting evidence, then issues the invoice or approves the bill. The issued document shows its linked obligation and settlements supplied by A. Partial payment updates the settlement view without rewriting the issued total. Cancellation is allowed only through an approved state/policy path. A customer-facing PDF/export is generated from the immutable document version after F12B integration, not from editable browser totals.

### 3. Existing functionality to reuse

**Reuse:** `identity/service/BusinessAuthorizationService.java`; document upload/review/provenance classes under `documents/`; `audit/service/SecurityAuditService.java`; `i18n/TranslationService.java`; existing frontend `lib/api/client.ts`, `components/integration/LiveDocumentReview.tsx`, `components/ui/{Input,Select,Dialog,StatusBadge,ErrorState,EmptyState}.tsx`. These provide transport, evidence review and UI conventions. `records/service/MonthlyRecordService.java` is a downstream compatibility bridge owned by A, not an invoice service. No invoice/bill models, line totals, numbering, tax policy or obligation authority exist in the inspected migration set.

### 4. Missing capability and deliberately limited first increment

Add document numbering, customer/supplier references, line totals, due dates, draft editing, issue/approve/cancel history, obligation link, payment read model, attachment links and end-to-end screens. First delivery uses the business's approved base currency; FX document/settlement behavior waits for C09. Tax/discount/rounding conventions must be decided, not improvised from UI fields. Credit notes, return documents and issued-document correction must have a reviewed route before releasing cancellation for financially affected documents; arbitrary destructive deletion of issued rows is excluded.

### 5. Exact proposed backend modules/classes

`commercial/entity/CommercialDocument.java`, `CommercialDocumentLine.java`, `CommercialDocumentEvent.java`, `CommercialDocumentAttachment.java`; `commercial/repository/CommercialDocumentRepository.java`, `CommercialDocumentLineRepository.java`; `commercial/service/DocumentTotalsCalculator.java`, `CommercialDocumentService.java`, `CommercialDocumentNumberService.java`, `ObligationBridge.java`; `commercial/dto/CommercialDocumentDraftRequest.java`, `CommercialLineRequest.java`, `CommercialDocumentResponse.java`, `DocumentTransitionRequest.java`, `CommercialDocumentTotals.java`; `commercial/controller/InvoiceController.java`, `BillController.java`.

One internal document model may represent INVOICE/BILL with a checked kind to avoid duplicating line arithmetic; separate API/controller policies preserve different workflow semantics. `ObligationBridge` calls A's approved C04 interface and does not implement balances.

### 6. Tables, relations, indexes and migration units

| Proposed table | Fields and constraints |
| --- | --- |
| `commercial_documents` | UUID id/business FK; kind INVOICE/BILL; counterparty UUID (required at issue/approval, nullable draft); document number nullable draft; issued/effective date and due date; currency; subtotal/discount/tax/grand total as server-owned decimals; status DRAFT/ISSUED/APPROVED/CANCELLED as valid by kind; version; optional source estimate/document UUID; optional A obligation ID; issue/approval/cancel actors/times/reason. No authoritative `outstanding_amount` or editable paid total. Scoped unique `(business_id,id)`, partial unique `(business_id,kind,document_number)` when assigned, index `(business_id,kind,status,due_date,id)`. |
| `commercial_document_lines` | UUID/business/document composite FK, line number unique per document, description, quantity >0, unit price ≥0, optional discount/tax code, canonical category ID, optional project and inventory-item scoped references, immutable calculated net/tax/total at issue. Nullable project means unallocated commercial line. Index `(business_id,project_id,document_id)` for attribution retrieval. |
| `commercial_document_events` | UUID/business/document/version, command kind, actor/time/reason, immutable from/to status and C01/C04 source references; unique command identity/idempotency mapping. Append-only grants/trigger when chosen with audit review. |
| `commercial_document_attachments` | Business/document/uploaded-document scoped linkage, purpose, actor/time; unique `(business_id,document_id,uploaded_document_id,purpose)`. Reuse uploaded bytes; no second binary store. |
| `commercial_number_sequences` | Business/kind/series primary scope, next number, version or row lock. Number allocation atomic; numbers not reused after cancellation. Format/series rollover policy remains reviewable. |

Reserve one additive document schema unit and a separately coordinated bridge migration if A's obligation FK is unavailable. No orphan permanent links or speculative FKs to nonexistent tables: add the FK when its producer migration is integrated, with compatibility fixtures. Cross-tenant FK checks must cover counterparty/project/item/document links.

### 7. Service responsibilities and authoritative calculations

`DocumentTotalsCalculator` is the sole commercial-line arithmetic implementation. Proposed arithmetic is quantity × unit price, less allowed discount, then approved tax treatment; document total is the sum of canonical rounded line totals plus any separately defined adjustment. Tax inclusivity, allocation order and rounding are pending policies, so example amounts below use no tax/discount. Client previews call the server; persisted totals are recomputed on save/issue and reject mismatched or disallowed inputs.

`CommercialDocumentService` validates source/status/permission and freezes issued/approved lines. `ObligationBridge` creates or amends exactly one C04 obligation for the approved source revision and records returned linkage. In the existing single Spring core, prefer one coordinated transaction where interfaces permit; if a later service boundary requires asynchronous handoff, expose PENDING/FAILED integration and an idempotent outbox with no false issued-and-posted success. A alone computes outstanding balance, allocations, cash effect and monthly recognition. Issuance **must not itself be treated as cash receipt**. Revenue/expense recognition at issuance versus delivery/performance remains the signed cross-domain policy.

### 8. API inputs, outputs and command semantics

| Proposed API | Contract and behavior |
| --- | --- |
| `POST /api/v2/invoices`, `/bills` | Draft body: counterpartyId?, issueDate?, dueDate?, currency, lines[{description,quantity,unitPrice,categoryId,projectId?,inventoryItemId?,discount?,taxCode?}], sourceDocumentIds?. Server returns draft, version, totals, missing issue requirements; no financial posting. |
| `POST /api/v2/invoices/preview`, `/bills/preview` | Same calculation inputs, no persistence; returns line totals/document totals, rounding-policy version and validation; never returns a fake payable/receivable balance. |
| `GET /api/v2/invoices`, `/bills`; `GET /{id}` | Bounded list and detail, totals plus C04 settlement state/availability, approved evidence references and accessible attachments. Query filters status/counterparty/date/project. |
| `PUT /api/v2/invoices/{id}`, `/bills/{id}` | Full draft update with version; recalculates totals. Non-draft returns 409; no caller-supplied status/obligation/payment balance accepted. |
| `POST /api/v2/invoices/{id}/issue` | version, effectiveDate, optional policy-specific fields; idempotency header. Returns frozen issued version, document number, obligation/source refs and actual integration status. |
| `POST /api/v2/bills/{id}/approve` | Same guards for supplier payable; actor must have approval permission. Returns approved source/obligation. |
| `POST /api/v2/{invoices|bills}/{id}/cancel` | version, reason, effectiveDate. Pending settlement/reversal prerequisites produce 409 with actionable code; no unallocated deletion. |
| `GET /api/v2/{invoices|bills}/{id}/settlements` | Reads C04 authoritative allocations/payment state, asOf and source version. No generic invoice PATCH that edits paid/outstanding values. |

Example tax-free line request: `{"currency":"PKR","lines":[{"description":"Service work","quantity":"2","unitPrice":"500.00","categoryId":"<canonical-uuid>"}]}`. Draft total is `"1000.00"`; issuing links an obligation according to approved recognition policy; receiving `"400.00"` through A's payment endpoint leaves C04 outstanding `"600.00"`. These fixture amounts do not approve a recognition date.

### 9. Frontend routes, components and user states

Add `frontend/src/app/invoices/page.tsx`, `invoices/new/page.tsx`, `invoices/[id]/page.tsx`, corresponding `bills/` routes; `components/commercial/{CommercialDocumentList,CommercialDocumentEditor,DocumentLineEditor,DocumentTotals,IssueReview,SettlementPanel,DocumentEvidence}.tsx`; `lib/api/commercial.ts`. Shared editor parameterizes document kind, labels and legal transitions. Use decimal strings, server preview with stale-request cancellation, conflict banner/reload, retry only after checking idempotent command outcome, attachment availability, loading/empty/error states and mobile line cards. Add EN/UR labels/validation and RTL-safe numeric/date columns. F02/C03 filters navigation; disabling the module retains data and applies the agreed read/write policy.

### 10. Permissions and tenant isolation

Proposed distinct `COMMERCIAL_READ`, `INVOICE_DRAFT_WRITE`, `INVOICE_ISSUE`, `BILL_DRAFT_WRITE`, `BILL_APPROVE`, `COMMERCIAL_CANCEL`. Extend existing role mapping with explicit review; do not automatically grant MANAGER financial posting because it may edit commercial drafts. OWNER/ACCOUNTANT final permissions and manager limits must be fixture-driven. Current VIEWER must remain read only. Check tenant of counterparty, project, item, uploaded evidence and C04 obligation server-side. SALESPERSON's document visibility/filtering and issue permission follow A's C08; no frontend-only filter or B-created role enum.

### 11. Cross-feature handoffs

C04 provides counterparty/obligation identity and paid/outstanding values. C01/C02 owns actual financial effects and monthly bridge. C05 supplies project attribution; commercial line/source IDs remain stable for margin drilldown. C07 controls inventory issue/COGS; issuing an invoice must not independently consume stock unless an explicitly approved fulfillment command establishes that handoff. C09 freezes money/FX semantics. F12B links documents/export. F13 displays invoice KPIs through authorized query outputs.

### 12. Financial invariants and edge cases

Issued totals do not change on payment; partial/overpayment, advance application, refunds and reversals come from C04. A draft or estimate creates no receivable, revenue or cash. Same issue key cannot create two obligations. Repeated OCR confirmation of an attached invoice is evidence-only after an authoritative effect exists. Cancellation with allocated payments is blocked until defined unallocation/reversal is executed; negative lines/credit notes require a named policy rather than treating a negative amount as a normal invoice. Due dates, zero-price lines, tax-exempt documents, numbering races, stale edits and rounding residuals have explicit fixtures. A manually entered historical monthly total plus its uploaded invoice is not an instruction to post the amount twice.

### 13. Tests and user acceptance

Proposed `commercial/DocumentTotalsCalculatorTests`, `CommercialDocumentServiceTests`, `CommercialObligationContractTests`, `CommercialDocumentPostgreSqlIT`, `CommercialAuthorizationIT`. Cover tax-free totals first, then signed tax/discount/rounding matrix; concurrency in numbering/issue; version conflict; business-crossed references; duplicate issue/refund/cancel request; failing C04 leaves no half-issued financial outcome. UI flow: create→preview→issue→partial settlement→remaining balance→evidence→cancel/credit path as permitted; EN/UR/mobile and business switching. Reconcile issue/payment/source references to exactly one monthly effect, with no cash at issue.

### 14. Dependencies and immediately independent work

Needs stable C03 module identity, C04 obligations, C01/C02 source/cutover and C12 DTO conventions. B can implement reviewed draft DTO/schema/UI and pure totals fixtures after calculation policy review while A builds C04; actual issue/approval must remain disabled until the bridge contract passes. Do not use a production mock settlement service. FX behavior waits for C09. Project/inventory optional links can be added through coordinated additive migrations when their producers exist.

### 15. Complexity and priority

High: financial policy high, integration high, frontend medium-high, migration/security medium. Deliver after W1 foundations as W2 with A's F03. Critical risk is not line-table CRUD but lifecycle/obligation atomicity and preventing duplicate recognition. Split draft/totals and reviewed financial-issue milestones without calling the feature complete after the first.

### 16. Definition of Done

A/B sign recognition/rounding/cancellation contract and fixtures; migrated real PostgreSQL creates and updates drafts safely; authorized invoice issue/bill approval produces the correct single C04 obligation/effect; settlement view reconciles; duplicate/reversal/cross-tenant tests pass; actual UI completes the full journey in EN/UR/mobile; source evidence/export integration is accurate; no direct MonthlyRecord writes from commercial service; developer-owned migration/API/UI/test/docs accepted together.

## F05 — Project, Job and Order Finance

### 1. Ownership and scope

B leads project identity, budgets, attribution and profitability (C05); A reviews financial consistency and supplies C01 actual source effects, C04 payments, C06 cash/funding, C08 commissions and C09 base amounts. Project/job/order are configurable labels on one model, not three separate financial implementations.

### 2. Business objective and workflow

Create a project for a customer, choose dates and planned revenue/cost budget, optionally convert an approved estimate, link commercial lines and actual cost sources, then inspect planned-versus-recognized revenue/cost/margin. Trace any number to its source. View A's expected cash/funding analysis separately from profitability. Close/archive without deleting financial evidence.

### 3. Existing reuse

Reuse tenant/auth/audit/translation services, `documents/` evidence references, frontend `components/ui/{Card,Section,StatusBadge,FinancialValue,EmptyState,ErrorState}.tsx`, `components/integration/useLiveResource.ts`, transport and current dashboard/health presentation patterns. Existing monthly records have aggregate revenue/COGS/OPEX only; they cannot reconstruct customer/project costs. `scoring/service/ScoringService.java` remains business-health owner. No project entity, budget version or project cost ledger exists.

### 4. Missing capability

Project CRUD/lifecycle, customer/order references, planned budgets/revisions, canonical cost-source allocations, commercial source links, profitability summary with completeness/attribution coverage, source drilldown and cash-plan handoff are new. Do not fabricate historic project margins by distributing old aggregate snapshots without reviewed allocation evidence. Profitability by product/customer can later aggregate this source model where coverage is adequate.

### 5. Proposed backend classes

`projects/entity/{Project,ProjectBudgetVersion,ProjectBudgetLine,ProjectAttribution,ProjectDocumentLink}.java`; `projects/repository/{ProjectRepository,ProjectBudgetRepository,ProjectAttributionRepository}.java`; `projects/service/{ProjectService,ProjectBudgetService,ProjectAttributionService,ProjectFinancialService}.java`; `projects/dto/{ProjectRequest,ProjectResponse,ProjectBudgetRequest,ProjectAttributionRequest,ProjectFinancialSummary,ProjectSourceLine}.java`; `projects/controller/ProjectController.java`.

`ProjectAttributionService` validates allocation against A's canonical recognized amount, does not create money. `ProjectFinancialService` is B's one project-margin aggregation; A's funding engine consumes project plan/actual refs instead of reimplementing this margin calculation.

### 6. Tables and migration design

| Proposed table | Data and integrity |
| --- | --- |
| `projects` | UUID/business FK; code unique `(business_id,code)`; name, kind label, optional customer C04 FK, status DRAFT/ACTIVE/COMPLETED/CANCELLED/ARCHIVED, dates, base currency, version, optional owner/authorized salesperson reference supplied by C08. Index `(business_id,status,updated_at,id)` and customer/status. No authoritative cash/outstanding column. |
| `project_budget_versions` | UUID/business/project composite FK; revision unique per project, DRAFT/APPROVED/SUPERSEDED state, planned revenue, currency, source estimate-version ref, approved actor/time; immutable after approval. Only one active approved budget enforced by scoped partial unique index or equivalent consistent command. |
| `project_budget_lines` | Business/budget version FK, line number, canonical category ID, description, quantity, unit cost and computed total; cost timing assumptions may refer to C06 schedule IDs, not actual event IDs. Unique line order within version. |
| `project_attributions` | UUID/business/project FK, source module/type/UUID/version, source component/line ID, canonical category, REVENUE/COST effect kind, allocation amount in authoritative currency/base currency, reversal/source link, version and audit. Unique stable source-component+allocation identity; index `(business_id,project_id,effective_date,id)`, source lookup `(business_id,source_type,source_id,source_version)`. |
| `project_document_links` | Scoped project/uploaded-document links with purpose/source reference and unique pair/purpose. |

Allocation limits across multiple rows/projects cannot be enforced by a simple row CHECK; lock the canonical source allocation scope and verify total allocation ≤ eligible source amount under its sign/reversal policy. Source amount changes invalidate or reverse prior allocation explicitly. Store references/provenance, not a competing mutable copy of C01 actual balances.

### 7. Calculations and service ownership

Approved budget total = sum approved budget lines. Recognized project revenue = sum eligible canonical revenue allocations. Recognized project cost = sum canonical cost allocations, including C07 material/COGS and C08 recognized commission **once**. Margin amount = revenue−cost; marginPct = 100×margin/revenue when positive-revenue semantics apply, otherwise return unavailable with reason. Budget variance = approved cost budget−actual cost (positive means under budget/favorable), and revenue variance = actual revenue−planned revenue; expose signs explicitly in C05.

Commission earning/payment policy is A's; B consumes its recognized allocated cost, never derives commission from salesperson percentage itself. A cash receipt is not automatically extra project revenue. COGS attribution is not both inventory material issue and a second purchase expense. Planned margin must label assumptions and remain separate from actual margin. Optional overhead allocation method and recognition basis require A/B review; no automatic division of all business OPEX by project count. Return attributionCoverage and missing-source warnings rather than a misleading complete profit.

### 8. API contracts

| Proposed endpoint | Input → output |
| --- | --- |
| `POST /api/v2/projects` | name, code?, kind, customerId?, startDate?, targetEndDate?, currency → scoped project/version, no posting. |
| `GET /api/v2/projects`, `GET /{id}`; `PUT /{id}` | Bounded status/customer/date filters; detail and versioned editable metadata. |
| `POST /api/v2/projects/{id}/budgets` | expectedProjectVersion, sourceEstimateVersionId?, plannedRevenue, lines[{categoryId,quantity,unitCost,description}] → draft budget totals/revision. |
| `POST /api/v2/projects/{id}/budgets/{budgetId}/approve` | version, reason → approved immutable budget version; existing budget superseded with history; never posts actual. |
| `POST /api/v2/projects/{id}/attributions` | source:{type,id,lineId,revision}, projectId, categoryId, allocatedBaseAmount, allocationPolicyVersion, version → validated attribution and remaining allocatable amount from shared authority; no ledger entry created. |
| `POST /api/v2/projects/{id}/attributions/{id}/reverse` | version, reason → traceable attribution reversal; does not reverse original payment merely to reassign its project. |
| `GET /api/v2/projects/{id}/financials?asOf=...` | recognized/planned revenue, actual/budget cost, margins/ratios, variances, currency, coverage, source version, unavailable reasons and source refs. |
| `GET /api/v2/projects/{id}/sources` | Paginated recognized actual source drilldown with permitted evidence links; search filters kind/category/date. |
| `POST /api/v2/projects/{id}/transition` | version, targetStatus, reason → permitted lifecycle transition. Closure policy checks unresolved plan/financial state; archive never deletes postings. |

Illustrative known-source fixture: approved cost budget `"800.00"`; recognized revenue `"1200.00"`; material cost `"400.00"`, labor `"200.00"`, A-recognized commission `"60.00"`; actual cost `"660.00"`, margin `"540.00"`, marginPct `"45.00"`, budget variance `"140.00"`. Payment of the same `"60.00"` commission does not add another `"60.00"` cost.

### 9. Frontend paths and components

Add `app/projects/page.tsx`, `projects/new/page.tsx`, `projects/[id]/page.tsx`, `projects/[id]/budget/page.tsx`; `components/projects/{ProjectEditor,ProjectSummary,ProjectBudgetEditor,BudgetVarianceTable,SourceAttributionDrawer,ProjectEvidence,ProjectCashPanel}.tsx`; `lib/api/projects.ts`. Display planned/recognized/cash in separately named panels, one currency per comparison, signed variance explanations, coverage/unavailable states and immutable budget revision selector. ProjectCashPanel consumes C06 via A-approved client contract; it cannot calculate peak funding in the browser. Reuse charts only if appropriate; EN/UR/RTL/mobile numeric layout must be explicit.

### 10. Permissions and tenancy

Proposed `PROJECT_READ`, `PROJECT_WRITE`, `PROJECT_BUDGET_APPROVE`, `PROJECT_ATTRIBUTION_MANAGE`, `PROJECT_CLOSE`. Role matrix must be approved; broad financial read does not automatically permit allocating someone else's source. Every source read/attribution must match active business; client-supplied project/customer IDs never widen scope. SALESPERSON row filters are A C08 predicates used for query and detail/export/AI as well as navigation. Restricted users must not infer business-wide margins through list totals.

### 11. Integrations

F06 supplies approved estimate revision→budget conversion. F04 links invoiced lines but recognized revenue comes through approved C01 policy. C07 issues/COGS and F09 sample/production source references feed costs once. C08 commission output adds recognized cost, with access scope. A's C06 receives project/date/approved-budget/expected-receipt refs and returns funding result; B provides UI controls without another algorithm. F12B reports and C11 queries consume `ProjectFinancialSummary` with evidence.

### 12. Invariants and edges

No sum of project actual allocations exceeds source eligibility; partial allocations leave an explicit unallocated remainder. Cross-currency aggregation uses C09 locked base amounts, not current rates. Refunds and reversals follow signed source effects without breaking source linkage. Zero/negative revenue has an explicit ratio-unavailable rule. Multi-project invoices need line-level allocations. Cancelled projects retain costs; archived projects remain reportable under access policy. Completed project can receive late financial corrections only under approved lifecycle rules. Free samples may be project-attributed or separate sample activity; never charged twice through F09 and F08.

### 13. Test plan

`projects/ProjectFinancialTests`, `ProjectAttributionContractTests`, `ProjectBudgetVersionTests`, `ProjectAttributionConcurrencyPostgreSqlIT`, `ProjectAuthorizationIT`. Check exact example above, unknown versus zero, budget amendment history, source replay/reversal, two concurrent allocations across projects, FX source base amount, hidden sales scope, overdue source refresh and incomplete coverage. UI acceptance: estimate conversion→approved budget→invoice/cost attribution→margin/source drilldown→A funding view→close/archive. Compare business and project sums with unallocated bucket; do not require all historic monthly totals to be attributable.

### 14. Dependencies and parallel work

Can build metadata/planning after C03/C05/C12 draft acceptance; actual attribution waits for frozen C01 source semantics and C04/F04 references. Funding view waits for C06 but project profitability need not be blocked on A's UI. Commission/inventory feeds are optional explicitly unavailable dependencies until C08/C07 accepted; do not fabricate zero actual cost. F06 can develop against a reviewed budget conversion contract while this metadata layer matures.

### 15. Complexity and order

High: aggregation/attribution high, concurrency/security high, frontend high, table CRUD medium. W3 alongside F06/F09 after W2 commercial foundations. Deliver project identity/budget first, then canonical actual attribution, then integrated profitability and funding presentation.

### 16. Definition of Done

C05 signed by B/A; budget and actual source history remain separate; exact source allocations reconcile with ledger/commission/inventory once; concurrent over-allocation and tenant/row-scope denial verified in PostgreSQL; real authorized UI shows planned versus actual with evidence and unavailable states; A's funding result is consumed intact; migration/docs/tests/UI approved together.

## F06 — Pre-Order Costing and Pricing

### 1. Ownership

B owns estimate lines, cost buildup, price/margin scenarios and conversion to F05 budget; A reviews canonical cost meanings, currency/rounding and handoff to C06. Estimate pricing is a deterministic planning calculator, never a posting or financing calculator.

### 2. Workflow

Create estimate before accepting work, enter material/labor/overhead/freight/other costs with units and quantities, compare proposed price or target margin, approve a version, and convert it to one project budget. Revise the estimate without rewriting the approved quotation/budget source. A cash scenario can use its dated assumptions separately.

### 3. Reuse

Reuse existing session/CSRF/tenant/audit/UI/input/i18n infrastructure. F05's proposed budget model is the conversion consumer and C01 canonical categories are the mapping source. Existing MonthlyRecord is actual snapshot input, so it must not store estimate totals. Current frontend number-based CurrencyInput cannot be the precision boundary for quantity×unit-cost arithmetic. No estimate or pricing entity exists.

### 4. Missing capability

Estimate versions/lines, expense breakdown, target-margin versus markup distinction, sensitivity preview, approval history, customer/project links and one-time conversion. Supplier quotations are evidence/input references, not automatically approved purchase bills. Broad procurement/quotation negotiation workflow is outside this package unless separately approved.

### 5. Proposed classes

`estimates/entity/{Estimate,EstimateVersion,EstimateLine,EstimateConversion}.java`; `estimates/repository/{EstimateRepository,EstimateVersionRepository,EstimateConversionRepository}.java`; `estimates/service/{EstimateService,EstimateCostCalculator,PricingCalculator,EstimateConversionService}.java`; `estimates/dto/{EstimateDraftRequest,EstimateLineRequest,PricingPreviewRequest,EstimateResponse,EstimateConversionRequest}.java`; `estimates/controller/EstimateController.java`.

### 6. Schema and indexes

`estimates`: business-scoped UUID/code, optional customer/project, title, currency, lifecycle, current revision, version and audit. Unique `(business_id,code)` and list index `(business_id,status,updated_at,id)`. `estimate_versions`: scoped estimate FK, revision, DRAFT/APPROVED/SUPERSEDED, priced quantity, chosen price, cost total, markup/margin outputs, policy version, assumption JSON with structured validated schema, approval actor/time; unique `(business_id,estimate_id,revision)`. `estimate_lines`: scoped version FK/line number, canonical category, cost grouping MATERIAL/LABOUR/OVERHEAD/FREIGHT/OTHER, quantity/unit/unit cost, derived total, optional inventory-item or quotation evidence source; scoped unique line number and category index. `estimate_conversions`: estimate-version scoped unique conversion identity, target project/budget version, command payload hash and actor/time. No estimate line is a C01 posted source. Migrations reserved after F05 key contracts exist.

### 7. Deterministic calculations

Line planned cost = quantity×unit cost under approved scale; total cost = sum lines plus explicitly modeled contingency/overhead lines. Proposed price P and cost C produce gross planned contribution P−C; planned margin ratio `(P−C)/P` when P>0; markup `(P−C)/C` when C>0. A target margin m implies price `C/(1−m)` for 0≤m<1; target markup u implies price `C×(1+u)` for allowed u. Margin and markup must have distinct input modes and labels. Unit price divides batch total only by positive saleable quantity. Yield/wastage must be explicit approved input, not silently applied twice to quantities and unit cost. Rounding/tax/FX policy is shared with F04/C09, not rewritten here.

Example no-tax plan: cost `"800.00"` at target margin `"0.20"` gives price `"1000.00"`; 20% markup gives `"960.00"` and margin `"0.166666..."` before display rounding. This contrast is a required acceptance fixture. These are planning examples, not guaranteed profitability.

### 8. API behavior

| Proposed API | Fields and result |
| --- | --- |
| `POST /api/v2/estimates`; `PUT /{id}` | version for edits; title, customerId?, currency, saleableQuantity, lines[{categoryId,costGroup,description,quantity,unit,unitCost,sourceRef?}], pricingMode/target → stored draft with server-calculated cost/price/margin/markup. |
| `POST /api/v2/estimates/preview` | Same plan and optional scenario overrides → no-persist baseline/scenario results, assumptions and rounding-policy version; no ledger effect. |
| `GET /api/v2/estimates`, `GET /{id}`, `GET /{id}/versions/{versionId}` | Bounded scoped list, current detail and immutable approved historical version. |
| `POST /api/v2/estimates/{id}/approve` | version, reason → approved immutable revision; fails incomplete/invalid pricing with field errors. |
| `POST /api/v2/estimates/{id}/revise` | sourceVersionId, version → new draft revision, preserves approved source. |
| `POST /api/v2/estimates/{id}/convert` | approvedVersionId, projectId? or newProject{name,...}, version; idempotency header → projectId, budgetVersionId, estimateVersionId and conversion identity. Replay returns same targets. |

Conversion cannot accept a browser override of approved totals. Changed scope requires a new approved version. One source estimate version cannot accidentally create two independent project budgets through retries; intentional reuse requires a separately reviewed clone workflow and new identity.

### 9. Frontend plan

Add `app/estimates/page.tsx`, `estimates/new/page.tsx`, `estimates/[id]/page.tsx`; `components/estimates/{EstimateEditor,CostLineEditor,PricingModeSelector,PricingPreview,EstimateVersionHistory,ConvertEstimateDialog}.tsx`; `lib/api/estimates.ts`. Explain units, margin versus markup and unknown inputs; show cost-group subtotal, per-unit/batch output, assumptions and pending preview. Scenario slider/text edits never commit actuals. Navigation follows C03. Draft unsaved-data warning, server validation, optimistic conflict, EN/UR/RTL/mobile are required.

### 10. Authorization

Proposed `ESTIMATE_READ`, `ESTIMATE_WRITE`, `ESTIMATE_APPROVE`, `ESTIMATE_CONVERT`; integrate existing role map with reviewed manager drafting/approval policy. Price-sensitive data follows C08 row scope for salesperson users; accessible quotation/project/customer/stock references are validated server-side. Conversion needs both estimate permission and target project/budget permission. No special browser-only bypass to help a restricted user create a project.

### 11. Integrations

C05 consumes approved estimate cost categories/line/version/assumptions into an immutable project budget. C06 may consume dated expected costs/receipts through its approved plan handoff; F06 does not compute cash funding. F08 can supply current item cost as a **dated estimate suggestion**; approved estimate snapshots remain locked when future WAC changes. F04 may quote/link approved price but document totals are recalculated through shared commercial arithmetic. C09 supplies currency assumptions with distinction between indicative quote rate and locked posted rate.

### 12. Rules and edges

No actual financial effect for draft/approve/convert. Target margin ≥1 or invalid negative quantities rejected; zero cost/zero price yields explicit unavailable ratio where denominator is zero. Optional cost missing cannot silently become a confident complete estimate. Cost inputs and source rates carry asOf. Free samples, discounts, yield loss and freight have explicit line/category treatment. Approved versions immutable; conversion/retries traceable. Post-conversion budget amendments preserve the originating estimate rather than rewriting it. Returned suggested price should clearly state taxes/exclusions defined by the reviewed configuration.

### 13. Tests

`estimates/PricingCalculatorTests`, `EstimateCostCalculatorTests`, `EstimateConversionContractTests`, `EstimateConversionPostgreSqlIT`, `EstimateAuthorizationIT`; cover 800/20% fixture, zero denominators, fractional units, scale/overflow, discount/tax policy once approved, missing required costs, stale revision, duplicate conversion and cross-tenant refs. Test conversion leaves ledger/monthly/scores unchanged. UI acceptance: draft→compare margin/markup→approve→convert→project budget exact line/total equality, with source trace and business-switch cancellation.

### 14. Dependencies

Needs C03/C05/C12 and approved category/rounding semantics. Pure planning calculator and fixtures can precede A's payment engine; persisted conversion needs F05. Actual stock cost suggestions wait for C07 but manual cost assumptions can exist explicitly. C06 and C09 optional advanced inputs return unavailable until accepted; no fictitious production provider response.

### 15. Complexity and priority

Medium-high: calculator medium, assumption/rounding policy high, conversion integration medium-high, frontend high. W3: after project budget contract, parallel with project UI and A funding engine. It is an early useful B deliverable because well-labeled planning can progress without posting actuals.

### 16. Definition of Done

B/A approve costing/pricing assumptions and conversion contract; server calculation and exact boundary fixtures pass; estimate versions/targets cannot duplicate or cross tenant; UI distinguishes margin/markup and plan/actual; converted project budget reconciles exactly to approved source; no MonthlyRecord/ledger mutation; documentation and full vertical-slice validation complete.

## F08 — Inventory and Manufacturing Finance

### 1. Ownership

B owns C07 inventory movements, weighted-average valuation, material usage, stock value, COGS feed and limited manufacturing-finance flow. A reviews recognition, balance projection and cash/obligation impacts through C01/C02/C04. B does not create a second bank/cash ledger; A does not recalculate inventory WAC from monthly balances.

### 2. Workflow

Create item/unit, establish reviewed opening stock, receive purchased stock with evidence/cost, issue materials to a project or production/sample activity, inspect quantity/value and actual cost trace, and process approved returns/adjustments. Compare a project's consumed material cost with its budget. A bill/payment may be linked, but paying a supplier alone does not consume inventory or create duplicate COGS.

### 3. Existing reuse

Reuse tenant/audit/document infrastructure, source-history patterns and existing monthly `inventoryValue`/`cogs` fields only as downstream summary consumers. `records/entity/MonthlyRecord.java` has no quantities, SKUs, valuation layers or materials trace. `zakat/` inventory preview requires classified valuation and must remain distinct from cost valuation. Frontend reusable cards/table/input/dialog primitives and transport remain suitable.

### 4. Missing scope and boundaries

New item catalog, movement journal, locked valuation state, source reconciliation, returns/adjustments, project material attribution and canonical COGS feed. Minimum manufacturing finance covers raw material consumption and traceable production cost grouping/output only under an approved WIP/output policy. It does not imply full MRP, machine scheduling, BOM explosion, multiwarehouse optimization or sales fulfillment automation. Record any deferred manufacturing depth explicitly instead of promising it through a generic inventory screen.

### 5. Backend classes

`inventory/entity/{InventoryItem,InventoryMovement,InventoryPosition,InventoryCostEvent,ProductionBatch}.java`; `inventory/repository/{InventoryItemRepository,InventoryMovementRepository,InventoryPositionRepository}.java`; `inventory/service/{InventoryService,WeightedAverageCostCalculator,InventoryPostingService,ProductionFinanceService,InventoryReconciliationService}.java`; `inventory/dto/{InventoryItemRequest,InventoryMovementRequest,InventoryPositionResponse,InventoryValuationResponse,ProductionBatchRequest}.java`; `inventory/controller/{InventoryController,ProductionFinanceController}.java`.

`InventoryPostingService` coordinates locked C07 calculation and idempotent C01 cost-event handoff. One WAC implementation serves project/material/sample consumers. `ProductionFinanceService` groups recognized material/labor/overhead sources; it must not recalculate their amounts independently.

### 6. Tables, precision and locking

| Proposed table | Fields / constraints |
| --- | --- |
| `inventory_items` | Business UUID, SKU unique `(business_id,sku)`, name, unit, item kind RAW_MATERIAL/FINISHED_GOOD/RESALE/CONSUMABLE as reviewed, active flag, version. Scoped FK key `(business_id,id)`. Unit changes after movement are restricted. |
| `inventory_positions` | Business/item/(location only if first scope requires it) unique, quantity, inventory value, average unit cost, version, last movement/revision. This is a rebuildable C07 projection; movement journal is its source. Lock the position before valuation. No negative quantity unless a specifically approved alternative is designed. |
| `inventory_movements` | UUID/business/item FKs, kind OPENING/RECEIPT/ISSUE/RETURN_IN/RETURN_OUT/ADJUSTMENT/REVERSAL, positive entered quantity plus explicit direction, effective time, sequence, source module/type/UUID/version, optional project/production/sample refs, unit/base cost and extended value snapshot, currency/rate ref, original movement ref, reason, actor. Unique source-component identity/idempotency and index `(business_id,item_id,effective_at,sequence,id)`, project/source lookup. Append-only actual history. |
| `inventory_cost_events` | Business/movement unique recognized financial effect, category, C01 posting ref/status/source version, amount/currency and policy version. At most one eligible cost contribution per source revision; reversals reference original. Not a separate cash journal. |
| `production_batches` | Business/project refs, code unique scoped, status PLANNED/IN_PROGRESS/COMPLETED/CANCELLED, planned/output quantities, date/version; linked cost sources via C05/C07, not duplicated actual expenses. WIP/output fields deferred until capitalization policy accepted. |

Need row checks for quantities/values and valid kind/sign combinations, scoped compound FKs and indexed immutable source references. Cross-row inventory balance/value conservation is a transactional service invariant verified through rebuild. Backdated movements cannot silently corrupt newer WAC; either reject into a closed valuation range or execute a reviewed replay that revises downstream valuation/events. That choice is an unresolved policy/architecture decision.

### 7. Calculation authority

Proposed perpetual weighted average for approved receipt: new quantity Q'=Q+q, value V'=V+eligible receipt cost c, new average V'/Q'. Issue q consumes q×current average under approved precision/residual treatment, reduces quantity/value and emits **one** canonical material/COGS cost event according to approved recognition policy. WAC pool currency is the business base currency using C09 locked base receipt costs. Freight/duties/tax inclusion, free samples, returns, waste, WIP/labor capitalization, zero-stock residuals and negative stock are pending A/B policies, not defaults implied by this formula.

Fixture assuming approved receipt-cost inclusion: start 10 units at 100, receive 10 at 200 → 20 units/value 3000/average 150; issue 4 → recognized eligible cost 600 and remaining 16/value 2400. Supplier payment for either receipt changes cash/AP under A but never adds another 600 expense. A projection consumes C07 inventory balance as-of and recognized cost feed; it must not derive WAC itself.

### 8. API surface

| Proposed endpoint | Input → output |
| --- | --- |
| `POST /api/v2/inventory/items`; `PUT /items/{id}`; `GET /items` | SKU/name/unit/kind with version → scoped item; no opening stock implicitly created by item creation. |
| `POST /api/v2/inventory/movements/preview` | itemId, kind, quantity, effectiveDate, receipt cost/source fields as relevant → eligible validation/estimated movement effect and policy version; explicitly non-posted and not guaranteed until locked commit. |
| `POST /api/v2/inventory/movements` | idempotency key, itemId, kind, quantity, expectedPositionVersion, effectiveDate, sourceRef, projectId?/sampleId?/productionBatchId?, receiptCost? only for permitted kinds, reason → immutable movement, resulting position/version and financial source status. Issue cost is calculated server-side, never caller-owned. |
| `POST /api/v2/inventory/movements/{id}/reverse` | version, effectiveDate, reason → approved compensating movement and C01 reversal linkage; fails if policy cannot safely value it. |
| `GET /api/v2/inventory/items/{id}/position` | quantity/value/average, base currency, asOf/source sequence and availability. |
| `GET /api/v2/inventory/movements` | Paginated item/project/kind/date-filtered source journal; no unscoped item lookup. |
| `GET /api/v2/inventory/valuation?asOf=...` | Authorized value/quantity by item, total, policy/revision and reconciliation state; no promise of historical valuation if required revisions unavailable. |
| `POST /api/v2/inventory/production-batches`; `GET /production-batches/{id}` | Project/planned-output metadata → batch with planned and canonical actual source cost view. Completion endpoint requires approved WIP/output valuation contract before enabling. |

Stocktake adjustment requires controlled reason/evidence/permission; raw SQL balances or generic PATCH to position are excluded. Return commands identify original movement and eligible remaining returned quantity; no free selection of historical cost to manipulate margin.

### 9. Frontend

Add `app/inventory/page.tsx`, `inventory/items/[id]/page.tsx`, `inventory/movements/new/page.tsx`, `inventory/production/[id]/page.tsx`; `components/inventory/{ItemEditor,InventoryPositionTable,MovementEditor,ValuationPreview,InventoryJournal,ProductionCostSummary,InventoryReconciliation}.tsx`; `lib/api/inventory.ts`. Show quantity unit, cost per unit, total base value, date, source and posted/pending state. Review issue cost before submit, then show actual locked result/conflict if position changed. Separate payment/evidence links from stock issue controls. EN/UR and RTL preserve readable decimal/unit columns; mobile movement forms and conflict/error/retry state are required.

### 10. Authorization

Proposed `INVENTORY_READ`, `INVENTORY_ITEM_WRITE`, `INVENTORY_RECEIVE`, `INVENTORY_ISSUE`, `INVENTORY_ADJUST`, `INVENTORY_VALUE_READ`, `PRODUCTION_FINANCE_MANAGE`. Cost visibility may be narrower than stock quantity visibility. Restrict adjustment/reversal and base-cost disclosure through existing role system. Validate tenant for item, project, original movement, supplier bill and sample. A's C08 determines salesperson visibility; stock possession/role in UI is insufficient authorization.

### 11. Integration handoffs

F04 supplier bills establish payable/recognition only through A policy; receipt references bills without treating bill total as automatically eligible inventory cost. C01 receives C07 cost/source event; C02 reads C07 as-of value and accepted COGS. C05 consumes material cost source; F09 consumes the same source, not another duplicate expense. F06 may read dated average cost for planning. C09 controls locked base rates; F12A inventory-related cash reconciliation is still A. Zakat uses separately classified valuation inputs and returns manual-review where current policy requires it.

### 12. Invariants and edge cases

Movement replay reconstructs quantity/value exactly within approved rounding tolerance; no unexplained residual. Opening quantities/values are cutover inputs, not purchases or revenue. Returns cannot exceed eligible original quantity; no unreviewed negative stock. Cost is not recognized twice at receipt/payment/issue. A blocked downstream financial posting cannot falsely present complete financial integration; use atomic local coordination or visible retryable outbox status. Concurrent issues cannot both spend the same units. Backdating, partially returned receipts, currency conversion, scrap, zero-cost samples, unit conversion and production output must have signed policies/fixtures before their routes are enabled.

### 13. Tests and acceptance

`inventory/WeightedAverageCostCalculatorTests`, `InventoryPostingContractTests`, `InventoryRebuildTests`, `InventoryConcurrencyPostgreSqlIT`, `InventoryAuthorizationIT`; verify 10+10/issue-4 fixture, fractional quantities, zero ending quantity, allowed rounding tolerance, concurrent issue/receipt/replay, over-return, cross-tenant original movement, cutover opening balances and rollback/pending effect. End-to-end manufacturing journey: receive→pay bill through A→issue to project/sample→material cost/source→inventory value and monthly COGS once→reversal/return approved case. Actual policy-supported production completion must reconcile to input/output/WIP values before claiming manufacturing readiness.

### 14. Dependencies and safe parallelism

Requires C07 policy signed by B/A, canonical categories, C01/C02, C05 attribution and C09 base-value semantics even if only base-currency initial implementation. Can build item catalog and pure reviewed WAC fixtures while A develops C08; actual movement posting depends on tested C01 bridge. F09 can consume a frozen source-cost interface while full stock UI develops. No direct monthly fallback if ledger integration is absent.

### 15. Complexity and sequence

Very high: valuation/concurrency/backdating high, integration high, frontend medium-high, database integrity high. W4 after project foundations. This is a large financial slice, not equivalent effort to a settings feature. Start with one approved pool/unit/base currency, then receipts/issues/reversal; explicitly gate further manufacturing depth and FX.

### 16. Definition of Done

Capitalization/WAC/returns/rounding/negative-stock/backdating policies signed; real PostgreSQL locking/replay tests prove stock and cost conservation; C01/C02 and project sources reconcile once; actual user movement journey and permitted production-finance scope work in EN/UR/mobile; row/cost visibility enforced server-side; no duplicate COGS or unexplained residual; additive migration/security/audit and integration evidence reviewed.

## F09 — Sampling and Production Costs

### 1. Ownership

B owns free/paid sample planning, production cost comparison, recovery and profitability presentation. A reviews cash/recognition and supplies actual ledger/settlement data. B consumes C07 material cost and C08 recognized commissions rather than implementing alternative expense/commission calculators.

### 2. Workflow

Create a sample or production-cost activity tied to a customer/project/order, record planned material/labor/overhead/freight costs and whether recovery is expected, link actual material usage and approved financial sources, optionally create/link an invoice for paid recovery, and compare cost/recovery/margin. Conversion to a commercial order links the sample's sources without charging them twice to both sample and order profit.

### 3. Reuse

Reuse F05 proposed canonical project attribution and budget conventions, F06 planning cost line patterns, F08 material cost source and F04 invoice lifecycle. Existing uploaded document review/provenance, tenant/auth/audit/API/UI/i18n are reusable. No existing monthly field can identify sample cost or production batch. Do not infer samples from arbitrary OPEX rows or OCR party names.

### 4. Missing capability and limited scope

Sample/activity identity, planned costs, free/paid/partially recovered status, source links, cost variance, recognized recovery and actual receipt display, conversion attribution decision and production cost grouping. Manufacturing stock/WIP value remains C07. Commission timing remains C08. This package does not add a competing production ledger or payment collection implementation.

### 5. Backend classes

`samples/entity/{SampleActivity,SampleCostPlanVersion,SampleCostPlanLine,SampleSourceLink,SampleRecoveryLink}.java`; `samples/repository/{SampleActivityRepository,SampleSourceLinkRepository}.java`; `samples/service/{SampleActivityService,SampleCostSummaryService,SampleRecoveryService,SampleConversionService}.java`; `samples/dto/{SampleActivityRequest,SamplePlanRequest,SampleSourceRequest,SampleFinancialSummary,SampleConversionRequest}.java`; `samples/controller/SampleController.java`.

`SampleCostSummaryService` aggregates C05/C07 canonical allocated sources and explicit plan lines; `SampleRecoveryService` reads F04/C04 recognized revenue and cash separately. It does not mark invoice paid or post money.

### 6. Schema and indexes

`sample_activities`: UUID/business, code unique tenant, kind SAMPLE/PRODUCTION_COST_ACTIVITY, name, optional project/customer/productionBatch refs, purpose, recovery intent FREE/PAID/PARTIAL (planning label), status DRAFT/ACTIVE/COMPLETED/CANCELLED/ARCHIVED, currency, dates, version/audit. Index `(business_id,status,project_id,updated_at,id)`.

`sample_cost_plan_versions` and `sample_cost_plan_lines`: scoped activity/revision key, status, category/quantity/unit-cost/calculated plan total and approval/version metadata. Approved plans immutable; plan entry never actual. `sample_source_links`: scoped activity plus canonical source component and attribution identity, REVENUE/COST kind, source version and allocation reference; unique stable link prevents replay; amount comes from authoritative allocation, not editable duplicate field. `sample_recovery_links`: scoped activity/invoice-line/obligation reference with role/purpose; unique pair and no stored competing receivable balance. A conversion record preserves original activity/project attribution transfer decision and idempotency; whether separate table or C05 attribution-event extension is chosen at schema review.

### 7. Calculations

Planned cost = sum approved plan lines. Actual eligible sample/production cost = sum canonical allocated recognized costs. Cost variance = actual−planned. Recognized recovery = recognized allocated revenue from approved F04/C01 sources; received recovery = actual allocated cash from C04, displayed separately. Profitability/contribution = recognized recovery−eligible cost. Cost-recovery ratio = recognized recovery/cost where cost>0, otherwise unavailable with reason. A free sample may have zero expected recovery but actual known cost; do not report missing actuals as a zero-cost sample.

Charging/allocating sample cost to a later order, capitalization of development samples, paid-sample revenue timing and cost recovery classification are policy decisions B drafts/A reviews. The default implementation must wait for a named decision rather than hide cost by moving it. Manufacturing material/labor/WIP policy comes from C07, so grouping a production activity cannot trigger another expense.

### 8. API specification

| Proposed API | Inputs and output |
| --- | --- |
| `POST /api/v2/samples`; `PUT /{id}` | version for edit; kind, name, projectId?, customerId?, productionBatchId?, recoveryIntent, currency, dates → activity; no posting. |
| `GET /api/v2/samples`, `GET /{id}` | Bounded scoped status/project/customer list and detail. |
| `POST /api/v2/samples/{id}/plans`; `POST /{id}/plans/{planId}/approve` | Planned category/quantity/unit cost, version → immutable approved cost plan/revision; no actual cost. |
| `POST /api/v2/samples/{id}/sources` | sourceRef, sourceComponentId, C05 attributionId (or reviewed allocation command), version → validated unique canonical source link. Reject arbitrary caller-defined actual totals. |
| `POST /api/v2/samples/{id}/recovery-links` | invoiceId/lineId/obligationId, version → verified recovery source association, no payment or second revenue posting. |
| `GET /api/v2/samples/{id}/financials?asOf=...` | Planned/actual cost, variance, recognized recovery, received cash, contribution, recovery ratio, incomplete reasons, currency/policy/source refs. |
| `POST /api/v2/samples/{id}/convert` | targetProjectId, version, approved cost-attribution treatment and reason; idempotency header → conversion/source lineage and resulting allocation references, never duplicate cost. |
| `POST /api/v2/samples/{id}/transition` | version, targetStatus, reason → lawful state; closed/cancelled activities retain financial history. |

Example reviewed known-source fixture: plan cost `"500.00"`, actual material+labor `"600.00"`, recognized paid recovery `"200.00"`, received cash `"100.00"`; cost variance `"100.00"`, contribution `"-400.00"`, recovery ratio `"0.333333..."`, unpaid amount remains a C04 response, not a new sample balance.

### 9. Frontend routes/components

Add `app/samples/page.tsx`, `samples/new/page.tsx`, `samples/[id]/page.tsx`; `components/samples/{SampleEditor,SampleCostPlan,SampleActualSources,SampleRecoveryPanel,SampleFinancialSummary,SampleConversionReview}.tsx`; `lib/api/samples.ts`. Show free/paid intent separately from actual recovery; distinguish recognized revenue from received cash. Link inventory material issues and invoices through approved workflows. Include unavailable source/partial coverage states, approved plan history, cost transfer review, EN/UR/RTL/mobile and business-switch request cancellation.

### 10. Permissions

Proposed `SAMPLE_READ`, `SAMPLE_PLAN_WRITE`, `SAMPLE_SOURCE_MANAGE`, `SAMPLE_CONVERT`, `SAMPLE_CLOSE`; reuse existing role mechanism and reviewed price/cost visibility policy. Linking a sample does not grant access to its restricted project, invoice, source or salesperson's other rows. Validate C08 row scope and all related source tenant IDs before financial summaries, list totals, exports and AI queries. A manager's planning permission cannot implicitly authorize stock issue or bill payment.

### 11. Dependencies and interactions

C05 provides stable project/source allocation so sample versus order categorization reconciles. C07 supplies material/production cost and inventory implications. F04 handles paid sample invoice, C04 allocation/payment, C01 financial recognition, C09 currency. C08 commission cost is included only if that recognized source is attributed; no sales percentage calculation here. F12B reports and F13 widgets consume one `SampleFinancialSummary` source and expose coverage; C11 queries enforce the same scope.

### 12. Rules and difficult cases

One material issue linked to both a sample and parent project can be displayed in both contexts but only contributes once to roll-up totals; shared allocation identity is essential. Refund/return reverses original recovery/cost according to source. Planned costs remain unchanged as actuals arrive. Cancelled/free sample can still incur cost. A partially recovered sample is not automatically paid in full. Conversion treatment is explicit transfer versus reference-only, with no double-counted cost across aggregate project/company reports. Unknown labor/overhead leaves incomplete profitability. A production summary cannot capitalize or expense the same cost independently of C07.

### 13. Tests and user acceptance

`samples/SampleCostSummaryTests`, `SampleRecoveryContractTests`, `SampleConversionPostgreSqlIT`, `SampleAuthorizationIT`; assert 500/600/200/100 fixture, free sample known expense, missing actuals, partial recovery/refund, material source replay, same source in nested sample/project without duplicate aggregate, conversion rollback/retry, historical version and cross-tenant relationships. Journey: plan sample→approve→material issue→labor source→paid invoice/partial payment→cost and recovery trace→convert to order under chosen policy. Regression confirms business monthly COGS/OPEX/revenue unchanged by mere linking or conversion.

### 14. Prerequisites and parallel work

Can develop activity/planning UI after C03/C05/C12 agree, alongside F05/F06 in W3. Actual material integration waits for F08/C07 W4; paid recovery waits for F04/C04. Label staged readiness clearly: a planning-only slice is not completed F09. Production cost completion also waits for approved C07 scope. Approved source contracts can support fixtures; production routes cannot use fabricated actual cost providers.

### 15. Complexity and priority

Medium-high: calculations medium, attribution/recognition high, integration high, frontend medium. Sequence planning with W3, then actual cost/recovery closure with W4. Avoid forcing F08 fully ahead of useful sample planning, but retain joint financial acceptance until every source feed is real.

### 16. Definition of Done

B/A approve free/paid sample and conversion treatment; planned/actual/recognized recovery/received cash visibly distinct; C05/C07/C04 sources reconcile once into sample/project/company reports; duplicate/reversal/tenant/row-scope tests pass against real PostgreSQL; authorized UI completes the free and paid journeys with partial settlement; source and cost assumptions visible; all migrations/contracts/tests/docs ready as one vertical slice.

## Cross-package review and integration milestones

| Milestone | Reviewable artifact and gate |
| --- | --- |
| Commercial contract readiness | B document/pricing/project schemas and DTO examples; A C01/C02/C04 base contracts; tax/rounding/recognition unresolved decisions recorded. No issued actuals before approved contract fixtures. |
| W2 document-to-obligation | Invoice/bill authorized journey, exactly one obligation, no cash at issue, payment/outstanding from A, evidence-only duplicate handling. |
| W3 planning-to-project | Approved estimate→single project budget; planned source changes never affect actual monthly/score values; project actual attribution and sample recovery labels are truthful. |
| W4 cost integration | Inventory consumption and A commission source→one project/sample cost each; concurrent allocation/stock tests; COGS and inventory projection reconcile. |
| W5 reporting/FX maturity | Base/original currency values retain locked rate/source; exports and dashboards reproduce authorized backend summaries; source corrections/reversals do not leave stale financial reports. |

These milestones are dependencies and acceptance gates, not promised calendar durations. Features remain incomplete until their authorized real UI journey, authoritative financial handoff, PostgreSQL integrity tests and owner/reviewer acceptance all exist.
