# FinSight Independent Black-Box & Product QA Report (`153e94b`)

**Product Under Test:** FinSight SME Financial Web Application  
**Repository:** `aviongg/sme-financial-webapp`  
**Canonical Baseline Branch:** `origin/handoff/finsight-integrated-production-baseline-20261113`  
**Verified Full Commit SHA:** `153e94b214ba24ac1d81a094eff3805334edaad9` (`feat(product): complete end-to-end SME financial workflows and live verification`)  
**Audit Date:** 14 November 2026 (Asia/Karachi)  
**Audit Role:** Independent Black-Box / Product QA (No application, financial, migration, or runtime files altered)

---

## 1. Verified Baseline & Isolated Test Runtime

- **Git Baseline Verification:** Fetched `origin/handoff/finsight-integrated-production-baseline-20261113` and checked out an isolated worktree at exact commit `153e94b214ba24ac1d81a094eff3805334edaad9` (verified not `06bfd3a`). Protected branches (`main`, `dev/fatima`, `dev/suleman`) and existing working directories were untouched.
- **Isolated Runtime Architecture:**
  - **Database:** Disposable embedded PostgreSQL `14.22` (`zonky` binary in isolated temp directory), migrated cleanly through all 52 Flyway migrations (`V1`–`V52`).
  - **Seeded Synthetic Data:** `DemoScenarioSeeder` applied (8 demo/sandbox businesses: *Alpha Garments & Packaging Ltd*, *Apex Digital Solutions*, *Crescent Electronics Trading*, *Indus Textiles Export Corp*, plus their 4 Sandbox counterparts; 4 seeded role accounts: `OWNER`, `ACCOUNTANT`, `SALESPERSON`, `VIEWER`), plus a synthetic `MANAGER` user (`demo.manager@finsight.local`) seeded across all 8 businesses because `DemoScenarioSeeder` omits `MANAGER`.
  - **Backend:** Spring Boot `4.1.1` (`SmeHealthBackendApplication`) running on `http://127.0.0.1:8190` (`NEXT_PUBLIC_DATA_MODE=live`, `OCR_INTEGRATION_ENABLED=false` default, `app.whatsapp.provider=mock` default).
  - **Frontend:** Production Next.js `16.3.8` standalone build (`next build --webpack` + `node .next/standalone/server.js`) running on `http://localhost:3190` proxying `/api/*` to `http://127.0.0.1:8190`.
- **Evidence Summary:**
  - Live browser rendering at `http://localhost:3190/` was host-verified (`1440x900`, English LTR, zero console/page errors, rendering the FinSight PK public landing page with hero *"Financial Clarity for Pakistani SMEs"*, `Sign In` / `Start Free Trial` CTAs, `84 / 100 Healthy` score preview card, and 6 capability cards).
  - All authenticated workflows, API contracts, state machines, role permissions (`OWNER`, `ACCOUNTANT`, `MANAGER`, `SALESPERSON`, `VIEWER`), locales (`en` LTR and `ur` RTL), viewports (`1440x900` desktop, `390x844` mobile, `360x568` short mobile), and database states were verified against the live `153e94b214ba24ac1d81a094eff3805334edaad9` runtime and source tree.

---

## 2. Executive Summary, Severity Counts & Release-Blocking Triage

> [!CAUTION]
> **Production-Readiness Verdict: NOT READY FOR CUSTOMER LAUNCH (`NO-GO`)**  
> While individual accounting engines and database constraints (`V1`–`V52`) are rich, an actual SME customer using commit `153e94b214ba24ac1d81a094eff3805334edaad9` will hit immediate blocking dead-ends in **OCR/Document Upload**, **Module Settings (`/settings`)**, **Dashboard Widget Customization (`/`)**, **Invoice/Bill Inventory Stock Fulfillment (`/invoices`, `/bills`)**, **Stock Customer Returns (`/inventory/stock`)**, and **Complete Financial Pack Reporting (`/reports`)**.

### Defect Counts by Severity

| Severity | Count | Defect IDs |
|---|---:|---|
| **P0 (Critical Blocker / Dead-End Workflow)** | **2** | `DEF-01`, `DEF-02` |
| **P1 (Major Feature Broken / Data Loss / Misleading Financial or Live Status)** | **9** | `DEF-03`, `DEF-04`, `DEF-05`, `DEF-06`, `DEF-07`, `DEF-08`, `DEF-09`, `DEF-10`, `DEF-11` |
| **P2 (Degraded Workflow / Raw UUID Inputs / Route Guard or Contract Gap)** | **7** | `DEF-12`, `DEF-13`, `DEF-14`, `DEF-15`, `DEF-16`, `DEF-17`, `DEF-18` |
| **P3 (Minor Navigation / Mobile Context Polish)** | **2** | `DEF-19`, `DEF-20` |
| **Total Structured Defects** | **20** | Plus **10** separate SME UX/Product issues in Section 4 |

### Release-Blocking Defect IDs (`11` Defects)
- **`DEF-01` (P0):** OCR failure (`unavailable`) locks uploaded documents in `failed` status with no manual review/confirm path, blocking commercial evidence linking on invoices/bills.
- **`DEF-02` (P0):** Invalid seeded module key `"commercial"` in `business_configuration.requested_modules` breaks `Preview`, `Apply recommended modules`, and `Save configuration` (`400 Unknown module key.`) across all 8 seeded businesses.
- **`DEF-03` (P1):** All 18 Phase-2/Phase-3 dashboard widgets (`PRODUCT_WIDGETS`) are omitted from `implementedWidgetKeys`, rendering them permanently locked (`"Not available yet"`) and unreachable on the Dashboard.
- **`DEF-04` (P1):** Disabled modules remain reachable via Sidebar, Mobile `+` Sheet, `FinancialWorkspaceLinks`, direct URLs, and unguarded backend controllers.
- **`DEF-05` (P1):** Invoice & Bill line editor omits `inventoryItemId` — users cannot link lines to inventory items in the UI, and editing any seeded draft invoice/bill silently wipes out its existing `inventory_item_id` in PostgreSQL.
- **`DEF-06` (P1):** Recording a `CUSTOMER_RETURN` on `/inventory/stock` requires typing a raw 36-char `Original issue movement ID` UUID that is never displayed in the Stock UI.
- **`DEF-07` (P1):** Recognized invoices/bills still show a false `"Financial effect: none"` banner and keep the top `Void` button enabled, failing with a generic error on `409 document_has_financial_effect`.
- **`DEF-08` (P1):** Outbound WhatsApp silently uses `MockWhatsAppClient` and shows `SENT` deliveries as if live, while the Inbound WhatsApp card on the same `/settings` page states `"not configured"`.
- **`DEF-09` (P1):** Complete Financial Pack (`/reports`) renders raw camelCase dot-keys (`revenue.recognizedRevenue`) in both English and Urdu and silently drops all nested array breakdowns.
- **`DEF-10` (P1):** Creating or selecting a business directly on `/onboarding` or `/business-setup` without `?returnTo=` redirects back to `/business-setup` in a loop instead of entering `/`.
- **`DEF-11` (P1):** Switching active business in Tab B force-remounts Tab A (`session.epoch`), wiping unsaved forms and turning open detail routes in Tab A into `404` errors.

### Frontend-Only Defect IDs (Candidates for Frontend Assignment)
- **Pure `frontend-only` defects (`13`):**
  - `DEF-03` (P1 — Add `PRODUCT_WIDGETS` to `implementedWidgetKeys` in `layout.ts` and fix `'inventory'` short-circuit in `DashboardWidgets.tsx`)
  - `DEF-05` (P1 — Add `inventoryItemId` selector and state preservation to `CommercialLineEditor.tsx` and `lib/commercial/form.ts`)
  - `DEF-06` (P1 — Replace raw `Original issue movement ID` UUID input in `StockWorkspace.tsx` with a selector of prior `SALES_ISSUE` movements / show movement ID)
  - `DEF-07` (P1 — Hide/disable top record-only `Void` button and update `RecordOnlyBanner` when invoice/bill is recognized; map `document_has_financial_effect` in `form.ts`)
  - `DEF-09` (P1 — Localize `FinancialPackView` `SectionTable` labels in `OperationsViews.tsx` and render nested breakdown arrays instead of dropping them)
  - `DEF-10` (P1 — Change default fallback in `BusinessSetup.tsx` from `'/business-setup'` to `'/'` and disallow `/business-setup` self-return loop in `workspaces.ts`)
  - `DEF-12` (P2 — Replace raw UUID `<Input>` fields in `CommercialLifecyclePanel.tsx`, `ActualsPanels.tsx`, and `StockWorkspace.tsx` with entity dropdown selectors)
  - `DEF-14` (P2 — Fix `href` mapping in `OperationsViews.tsx` `RelationshipsView` so `DOCUMENT` links to `/invoices/{id}` or `/bills/{id}` and payable `OBLIGATION` links to `/records/payables`)
  - `DEF-15` (P2 — Add camera capture `capture="environment"` and multi-file upload support to `LiveDocuments.tsx` on `/scan`)
  - `DEF-16` (P2 — Synchronize `LiveSearch.tsx` with `useSearchParams().get('q')`)
  - `DEF-17` (P2 — Enforce `routePermission(pathname)` in `IntegrationBoundary.tsx` for `/new` write routes)
  - `DEF-19` (P3 — Reset `authRequested` in `src/app/page.tsx` when `searchParams.get('auth')` is cleared on browser Back)
  - `DEF-20` (P3 — Display active business indicator on mobile viewports `< 640px` in `PageHeader.tsx` and link it to business switching)
- **Integration defects with a clear Frontend component (`3`):**
  - `DEF-04` (Frontend portion: complete `routeModule(path)` mapping in `lib/api/configuration.ts`, gate `FinancialWorkspaceLinks.tsx` by module availability, and enforce `configuration.allowsRoute(pathname)` in `IntegrationBoundary.tsx`)
  - `DEF-08` (Frontend portion: display simulated/mock provider badge on the outbound WhatsApp card in `LiveSettings.tsx` when live Meta delivery is not active)
  - `DEF-13` (Frontend portion: streamline `LiveDocumentReview.tsx` confirmation form to match V52 derived monthly model and add post-confirmation navigation to `/invoices/new`, `/bills/new`, or `/finance/transactions`)

---

### Direct Verification of the 5 Highlighted P1 Areas from the User Side

1. **Disabled modules still being reachable:** **CONFIRMED (`DEF-04`, P1 — Frontend + Backend + Integration).**
   - On the frontend, `routeModule(path)` in [`frontend/src/lib/api/configuration.ts`](../../frontend/src/lib/api/configuration.ts#L35-L45) only maps `/scan` $\to$ `'documents'` and `/ask-finsight` $\to$ `'ai'`. It returns `null` (always allowed!) for `/upload`, `/invoices`, `/bills`, `/inventory`, `/inventory/stock`, `/inventory/valuation-preview`, `/projects`, `/samples`, `/estimates`, `/planning`, `/intelligence`, `/operations/calendar`, `/operations/relationships`, `/reports`, and all `/finance/*` routes.
   - [`frontend/src/components/layout/FinancialWorkspaceLinks.tsx`](../../frontend/src/components/layout/FinancialWorkspaceLinks.tsx#L10-L15) never consults `useConfiguration()` at all — all 18 `/finance/*` and `/records/*` links are rendered unconditionally in the desktop sidebar and mobile `+` sheet even when `transactions`, `receivables`, `commissions`, or `multi_currency` are disabled.
   - [`frontend/src/components/layout/IntegrationBoundary.tsx`](../../frontend/src/components/layout/IntegrationBoundary.tsx#L23-L39) never checks `configuration.allowsRoute(pathname)` or `routePermission(pathname)`, so navigating directly by URL to `/scan`, `/ask-finsight`, `/invoices/new`, `/inventory/stock`, etc. renders the workspace even when the module is disabled or the user is a read-only `VIEWER`.
   - On the backend, [`ModuleWebConfiguration.java`](../../backend/src/main/java/com/app/sme_health_backend/configuration/service/ModuleWebConfiguration.java#L11-L30) only intercepts write requests to `/api/documents/**` and `/api/whatsapp/**`, and `AiController`/`CopilotController` check `ai`. None of the controllers for `invoices`, `receivables`, `inventory`, `production`, `projects`, `costing`, `commissions`, `multi_currency`, `operations`, or `reports` enforce `ModuleAccessPolicy.requireEnabled(...)`.
2. **OCR unavailable/stuck states:** **CONFIRMED (`DEF-01`, P0 — Backend + Frontend + Integration).**
   - When the external OCR provider is disabled (`OCR_INTEGRATION_ENABLED=false` default) or unreachable, uploading any PDF/JPG/PNG on `/scan` or `/upload` transitions `pending` $\to$ `processing` $\to$ `failed` (`failureReason = "unavailable"`).
   - Once in `failed` status, [`LiveDocumentReview.tsx`](../../frontend/src/components/integration/LiveDocumentReview.tsx#L90-L128) hides the manual review/edit form and confirmation controls (`editable` requires `extracted` or `needs_review`), and backend [`DocumentReviewService.java`](../../backend/src/main/java/com/app/sme_health_backend/documents/service/DocumentReviewService.java#L33-L35) and [`DocumentConfirmationService.java`](../../backend/src/main/java/com/app/sme_health_backend/documents/service/DocumentConfirmationService.java#L58-L62) reject `PATCH /api/documents/{id}/review` and `POST /api/documents/{id}/confirm` with `409 Conflict`. Because [`CommercialLifecycleService.java`](../../backend/src/main/java/com/app/sme_health_backend/commercial/lifecycle/CommercialLifecycleService.java#L311-L316) requires `confirmed` status to link evidence (`409 evidence_not_reviewed`), **no newly uploaded document can ever be manually reviewed, confirmed, or linked as invoice/bill evidence**.
3. **WhatsApp appearing successful when not actually live:** **CONFIRMED (`DEF-08`, P1 — Backend + Frontend + Integration).**
   - Outbound WhatsApp defaults to `MockWhatsAppClient` (`app.whatsapp.provider=mock` in [`WhatsAppClientConfiguration.java`](../../backend/src/main/java/com/app/sme_health_backend/whatsapp/config/WhatsAppClientConfiguration.java#L38-L41)). On `/settings`, the Owner can enable WhatsApp opt-in, save a phone number (`"Preferences saved. Delivery status appears below."`), and see historical/new messages marked `SENT` in [`LiveSettings.tsx`](../../frontend/src/components/integration/LiveSettings.tsx#L47-L71) with zero indication that delivery is simulated (`mock`). Directly below on the same `/settings` page, `WhatsAppLinkCard` ([`OperationsViews.tsx`](../../frontend/src/components/product/OperationsViews.tsx#L305-L337)) contradicts it by displaying `"WhatsApp inbound is not configured in this environment."`
4. **Document/accounting workflow inconsistencies:** **CONFIRMED (`DEF-05`, `DEF-07`, `DEF-13`, P1/P2 — Frontend + Backend + Integration).**
   - **Missing Inventory Link on Invoices/Bills (`DEF-05`):** [`CommercialLineEditor.tsx`](../../frontend/src/components/commercial/CommercialLineEditor.tsx#L44-L187) and [`form.ts`](../../frontend/src/components/commercial/../../lib/commercial/form.ts#L20-L33) omit `inventoryItemId` completely. Users cannot link any invoice or bill line to an inventory item in the UI, making the `Receive stock from bill` and `Fulfil stock for invoice` buttons in `CommercialLifecyclePanel` unreachable for user-created documents, and editing any seeded draft invoice/bill silently wipes out its existing `inventory_item_id` in PostgreSQL.
   - **Conflicting Void Buttons & False "Financial effect: none" Banners (`DEF-07`):** Recognized invoices/bills still show `"Record-only invoice / Financial effect: none"` at the top of `/invoices/[id]` and keep the top `Void` button enabled; clicking that top `Void` button fails with an unhandled `409 document_has_financial_effect` (`"The request could not be completed. Your entries are kept."`) instead of directing the user to the bottom `Void recognized document` panel.
   - **Legacy Confirmation Form vs. V52 Derived Monthly Model (`DEF-13`):** On `/upload/[id]`, confirming a document still forces the user to select `Financial classification` (`Revenue`/`Operating expenses`/`COGS`), `Cash-flow impact`, and click `"Confirm and add once"` with a duplicate-counting warning, even though V52 (`monthly-projection-v1`) derives monthly records from ledger transactions and document confirmation has zero effect on monthly figures.
5. **Cross-tab logout & business-switch behavior:** **CONFIRMED (`DEF-11`, P1 — Frontend + Backend Session Architecture).**
   - Active business is stored in the shared server-side HTTP session rather than scoped per tab. Switching businesses in Tab B broadcasts on `BroadcastChannel('finsight-session')`, forcing Tab A to increment `session.epoch` and remount `<IntegrationBoundary>` ([`IntegrationBoundary.tsx`](../../frontend/src/components/layout/IntegrationBoundary.tsx#L39)), which **destroys all unsaved form input in Tab A** and turns any open entity detail page in Tab A (`/invoices/[id]`, `/bills/[id]`, `/projects/[id]`, `/samples/[id]`, `/estimates/[id]`, `/upload/[id]`) into an immediate `404 Not Found` because that entity UUID does not belong to Tab B's newly selected business.

---

## 3. Structured Defect Register (`P0` / `P1` / `P2` / `P3`)

### Summary Table

| ID | Severity | Classification | Route(s) | Short Title |
|---|---|---|---|---|
| **DEF-01** | **P0** | Integration (Backend + Frontend) | `/scan`, `/upload`, `/upload/[id]`, `/invoices/[id]`, `/bills/[id]` | OCR failure (`unavailable`) locks documents in `failed` state with no manual review/confirm fallback, blocking commercial evidence linking |
| **DEF-02** | **P0** | Integration (Seeder + Backend + Frontend) | `/settings` (`F02 Business Configuration`) | Invalid seeded module key `"commercial"` breaks `Preview`, `Apply recommended modules`, and `Save configuration` (`400 Unknown module key.`) across all 8 businesses |
| **DEF-03** | **P1** | Frontend-only | `/` (`DashboardWorkspace`, `WidgetPicker`) | All 18 Phase-2/3 product dashboard widgets (`PRODUCT_WIDGETS`) are omitted from `implementedWidgetKeys`, rendering them permanently locked (`"Not available yet"`) and unreachable |
| **DEF-04** | **P1** | Integration (Frontend + Backend) | `/invoices`, `/bills`, `/inventory`, `/projects`, `/samples`, `/estimates`, `/reports`, `/finance/*`, `/scan`, `/ask-finsight` | Disabled modules remain reachable via Sidebar, Mobile `+` Sheet, FinancialWorkspaceLinks, direct URLs, and unguarded backend controllers |
| **DEF-05** | **P1** | Frontend-only (Data Loss on Save) | `/invoices/new`, `/invoices/[id]`, `/bills/new`, `/bills/[id]` | Commercial line editor omits `inventoryItemId`: users cannot link lines to inventory items, and editing a seeded draft wipes existing `inventory_item_id` |
| **DEF-06** | **P1** | Frontend-only | `/inventory/stock` | Recording a `CUSTOMER_RETURN` requires typing a raw 36-char `Original issue movement ID` UUID that is not displayed anywhere in the Stock UI |
| **DEF-07** | **P1** | Frontend-only | `/invoices/[id]`, `/bills/[id]` | Recognized invoices/bills show false `"Financial effect: none"` banner and keep top `Void` button enabled, failing with generic error on `409 document_has_financial_effect` |
| **DEF-08** | **P1** | Integration (Backend + Frontend) | `/settings` | Outbound WhatsApp silently uses `MockWhatsAppClient` and shows `SENT` deliveries as if live, while Inbound WhatsApp card on the same page says `"not configured"` |
| **DEF-09** | **P1** | Frontend-only | `/reports` (`Complete financial pack`) | Financial Pack renders raw camelCase dot-keys (`revenue.recognizedRevenue`) in both English and Urdu and silently drops all nested array tables |
| **DEF-10** | **P1** | Frontend-only | `/onboarding`, `/business-setup` | Creating or selecting a business directly on `/onboarding` or `/business-setup` redirects back to `/business-setup` in a loop instead of entering `/` |
| **DEF-11** | **P1** | Integration (Frontend + Backend Session) | All routes across multiple tabs | Switching active business in Tab B force-remounts Tab A (`session.epoch`), wiping unsaved forms and turning open detail routes in Tab A into `404` errors |
| **DEF-12** | **P2** | Frontend-only | `/invoices/[id]`, `/bills/[id]`, `/samples/[id]`, `/inventory/stock` | Multiple workflows require copy-pasting raw 36-char database UUIDs (`Uploaded document ID`, `Transaction ID`, `Invoice document ID`, `Project ID`, `Sample ID`) instead of selectors |
| **DEF-13** | **P2** | Integration (Frontend + Backend) | `/upload/[id]` | Document review/confirmation UI still enforces legacy `Financial classification` (`Revenue`/`COGS`/`Opex`), `Cash-flow impact`, and `"Confirm and add once"` despite V52 derived monthly model |
| **DEF-14** | **P2** | Frontend-only | `/operations/relationships` | Operational Relationships view renders `DOCUMENT` links (`INV-*`, `BILL-*`) as unclickable plain text (`href = null`) and routes all `OBLIGATION` links (including payables) to `/records/receivables` |
| **DEF-15** | **P2** | Frontend-only | `/scan`, `/upload` | Live `/scan` page renders the exact same `<LiveDocuments />` component as `/upload`, lacking camera capture (`capture="environment"`) and multi-file upload (`multiple`) |
| **DEF-16** | **P2** | Frontend-only | `/search?q=...` | `LiveSearch` ignores the `?q=` URL query parameter, and `PageHeader` bypasses the search dialog in live mode |
| **DEF-17** | **P2** | Frontend-only | `/invoices/new`, `/samples/new`, `/projects/new`, `/estimates/new`, `/records/new` | `IntegrationBoundary` does not enforce `routePermission(pathname)`, allowing `VIEWER` and `SALESPERSON` roles to open `/new` creation routes via direct URL |
| **DEF-18** | **P2** | Environment / Demo Tooling | `scripts/start-demo.ps1`, `scripts/DemoScenarioSeeder.java`, `scripts/FlywayMigratorDemo.java` | `start-demo.ps1` prints wrong password (`FinSight@Demo2026!`), `DemoScenarioSeeder` omits `MANAGER` role, and `FlywayMigratorDemo.java` hardcodes `C:/Users/PC/...` |
| **DEF-19** | **P3** | Frontend-only | `/`, `/?auth=login`, `/?auth=register` | Browser Back button from `/?auth=login` to `/` fails to return to `<LandingPage />` because `authRequested` state in `RootPageContent` never resets |
| **DEF-20** | **P3** | Frontend-only | All routes on mobile (`390x844`, `360x568`) | Active business name badge (`[data-testid="header-business-name"]`) is hidden on mobile (`hidden sm:flex`), leaving multi-business mobile users blind to which business is active |

---

### Detailed Defect Entries

#### DEF-01 — [P0] OCR Failure (`unavailable`) Locks Uploaded Documents in `failed` Status With No Manual Review/Confirm Path, Blocking Commercial Evidence Linking
- **Severity:** `P0`
- **Classification:** `integration` (Backend state machine + Frontend conditional rendering + Environment default)
- **Route(s) & Endpoint(s):** `/scan`, `/upload`, `/upload/[id]`, `/invoices/[id]`, `/bills/[id]`; `POST /api/documents`, `POST /api/documents/{id}/retry`, `PATCH /api/documents/{id}/review`, `POST /api/documents/{id}/confirm`, `POST /api/commercial/documents/{id}/evidence`
- **Role(s):** `OWNER`, `ACCOUNTANT`
- **Language / Device:** English (`en`) & Urdu (`ur` / RTL); Desktop (`1440x900`) & Mobile (`390x844`, `360x568`)
- **Reproduction Steps:**
  1. Sign in as `demo.owner@finsight.local` (`OWNER`) and navigate to `/upload` or `/scan`.
  2. Select a valid PNG/JPG/PDF file (e.g., `sample-invoice.png`) and click **Upload document**.
  3. Observe the redirected detail page `/upload/{id}`.
  4. Click **Retry extraction** on `/upload/{id}`.
  5. Copy the document's UUID `{id}`, navigate to any invoice or bill (`/invoices/{docId}`), paste `{id}` into **Uploaded document ID** in the `Financial lifecycle` panel, and click **Link evidence**.
- **Expected Result:**
  - When automated OCR is disabled (`OCR_INTEGRATION_ENABLED=false`) or fails/times out, the user should be able to manually enter/review the document fields (`Amount`, `Document date`, `Counterparty`, `Category`) and confirm the document as reviewed evidence, OR transition a `failed` document into `needs_review` for manual entry so it can be attached as evidence on `/invoices/[id]` and `/bills/[id]`.
- **Actual Result & Concrete Evidence:**
  - `DocumentProcessingConfiguration` ([`DocumentProcessingConfiguration.java:17-24`](../../backend/src/main/java/com/app/sme_health_backend/documents/config/DocumentProcessingConfiguration.java#L17-L24)) registers a fallback `OcrClient` that throws `OcrClientException(Reason.unavailable)`.
  - `DocumentDraftProcessor` ([`DocumentDraftProcessor.java:29-39`](../../backend/src/main/java/com/app/sme_health_backend/documents/processing/DocumentDraftProcessor.java#L29-L39)) catches `OcrClientException` and marks the document `failed` (`failureReason = "unavailable"`).
  - On `/upload/{id}`, [`LiveDocumentReview.tsx:90-128`](../../frontend/src/components/integration/LiveDocumentReview.tsx#L90-L128) sets `const editable = doc && (doc.processingStatus === 'extracted' || doc.processingStatus === 'needs_review')`, completely hiding the manual entry form and confirmation button for `failed` documents.
  - On the backend, [`DocumentReviewService.java:33-35`](../../backend/src/main/java/com/app/sme_health_backend/documents/service/DocumentReviewService.java#L33-L35) and [`DocumentConfirmationService.java:58-62`](../../backend/src/main/java/com/app/sme_health_backend/documents/service/DocumentConfirmationService.java#L58-L62) throw `IllegalStateException("Only extracted or needs_review documents can be corrected/confirmed")` (`409 Conflict`).
  - Clicking **Link evidence** on `/invoices/[id]` or `/bills/[id]` fails with `409 Conflict` (`evidence_not_reviewed`: `"Evidence documents must have confirmed review status before linking"`) in [`CommercialLifecycleService.java:311-316`](../../backend/src/main/java/com/app/sme_health_backend/commercial/lifecycle/CommercialLifecycleService.java#L311-L316).
  - Clicking **Retry extraction** merely re-runs the unavailable OCR client and returns to `failed`.

---

#### DEF-02 — [P0] F02 Module Configuration Completely Broken on All Seeded Businesses (`400 Unknown module key.` + Unchecked Disabled Required Modules)
- **Severity:** `P0`
- **Classification:** `integration` (Seeder data contract mismatch + Backend `ModuleResolver` + Frontend `BusinessConfigurationPanel`)
- **Route(s) & Endpoint(s):** `/settings`, `/business-setup`; `GET /api/business-configuration`, `POST /api/business-configuration/preview`, `PUT /api/business-configuration`
- **Role(s):** `OWNER`
- **Language / Device:** English (`en`) & Urdu (`ur` / RTL); Desktop & Mobile
- **Reproduction Steps:**
  1. Sign in as `demo.owner@finsight.local` (`OWNER`) on any of the 8 businesses (e.g., *Alpha Garments & Packaging (Demo)*).
  2. Navigate to `/settings` and locate the **Business configuration** panel (`[data-testid="business-configuration-panel"]`).
  3. Toggle any optional module checkbox (e.g., enable `Invoices and billing` or disable `AI explanations`), or click **Apply recommended modules** (`[data-testid="apply-recommended-modules"]`), or click **Preview configuration** / **Save configuration** (`[data-testid="save-configuration"]`).
- **Expected Result:**
  - The Owner should be able to preview, apply recommended modules, and save module configuration changes cleanly. All implemented modules that have seeded data for the business (`transactions`, `receivables`, `invoices`, `costing`, `production`, `commissions`, `multi_currency`, `operations`, `reports`) should have valid keys in `business_configuration.requested_modules`.
- **Actual Result & Concrete Evidence:**
  - [`DemoScenarioSeeder.java:252-254`](../../scripts/DemoScenarioSeeder.java#L252-L254) seeds `business_configuration.requested_modules` via raw SQL as `["core_finance","documents","commercial","inventory","projects","whatsapp","ai"]`.
  - `"commercial"` is **not a valid key** in [`ModuleRegistry.java:16-27`](../../backend/src/main/java/com/app/sme_health_backend/configuration/service/ModuleRegistry.java#L16-L27) (which defines `core_finance`, `documents`, `whatsapp`, `ai`, `transactions`, `receivables`, `invoices`, `projects`, `costing`, `inventory`, `production`, `commissions`, `multi_currency`, `operations`, `reports`), and `"inventory"` is seeded without its required dependency `"transactions"`.
  - In [`BusinessConfigurationPanel.tsx:23-58`](../../frontend/src/components/configuration/BusinessConfigurationPanel.tsx#L23-L58), `draft.requestedModules` initializes from `initial.requestedModules` (containing `"commercial"`). Because no checkbox exists for `"commercial"`, the user can never remove `"commercial"` from `draft.requestedModules`.
  - Clicking **Preview configuration**, **Save configuration**, or even **Apply recommended modules** (which calls `await preview(draft)` on line 39 before replacing `requestedModules`!) sends `"commercial"` to [`ModuleResolver.java:23-25`](../../backend/src/main/java/com/app/sme_health_backend/configuration/service/ModuleResolver.java#L23-L25), which rejects the request with `400 Bad Request` (`"Unknown module key."`). The Owner is completely locked out of updating business configuration on every seeded business.

---

#### DEF-03 — [P1] All 18 Phase-2/Phase-3 Dashboard Widgets (`PRODUCT_WIDGETS`) Are Omitted From `implementedWidgetKeys`, Rendering Them Permanently Locked and Unreachable
- **Severity:** `P1`
- **Classification:** `frontend-only`
- **Route(s) & Endpoint(s):** `/` (`DashboardWorkspace`, `WidgetPicker`, `WidgetRenderer`, `DashboardWidgets`)
- **Role(s):** `OWNER`, `ACCOUNTANT`, `MANAGER`, `SALESPERSON`, `VIEWER`
- **Language / Device:** English (`en`) & Urdu (`ur` / RTL); Desktop & Mobile
- **Reproduction Steps:**
  1. Sign in as `demo.owner@finsight.local` and view the Dashboard at `/`.
  2. Click **Customize dashboard** (`[data-testid="workspace-customize"]`).
  3. Inspect the **Widget library** (`[data-testid="workspace-picker"]`) on the side of the dashboard.
  4. Look for `Budgets`, `Financial goals`, `Profitability`, `Break-even`, `Action center`, `Inventory and materials`, `Active projects`, `Funding gaps`, etc.
- **Expected Result:**
  - Widgets whose backend module is enabled (such as `budgets`, `goals`, `profitability`, `break-even`, `action-center` under `core_finance`, and `inventory`, `projects`, `funding-gap`) and whose frontend renderer `<ProductWidget>` is implemented in [`DashboardWidgets.tsx:13-38`](../../frontend/src/components/product/DashboardWidgets.tsx#L13-L38) should be addable in `WidgetPicker` and renderable on the Dashboard.
- **Actual Result & Concrete Evidence:**
  - While [`WidgetRegistry.java:29-34`](../../backend/src/main/java/com/app/sme_health_backend/dashboard/workspace/WidgetRegistry.java#L29-L34) registers all 18 product widgets as `implemented = true` and [`WidgetRenderer.tsx:24-45`](../../frontend/src/components/workspace/WidgetRenderer.tsx#L24-L45) delegates them to `<ProductWidget>`, [`frontend/src/lib/workspace/layout.ts:30`](../../frontend/src/lib/workspace/layout.ts#L30) defines:
    ```ts
    export const implementedWidgetKeys = new Set([
      'health-score', 'health-components', 'score-completeness', 'cash-flow',
      'cash-projection', 'financial-evidence', 'next-step', 'recommendation',
      'financial-summary', 'ai-explanation', 'document-workflow'
    ]);
    ```
  - Because `PRODUCT_WIDGETS` was never added to `implementedWidgetKeys`:
    1. [`DashboardWorkspace.tsx:58`](../../frontend/src/components/workspace/DashboardWorkspace.tsx#L58) filters out all 18 widgets (`implementedWidgetKeys.has(widget.key)` is `false`).
    2. [`WidgetPicker.tsx:18-20`](../../frontend/src/components/workspace/WidgetPicker.tsx#L18-L20) sets `allowed = widget.available && implementedWidgetKeys.has(widget.key)` to `false`, renders a `<LockKeyhole>` icon (`data-testid="workspace-unavailable-..."`), and displays `"Not available yet"` (`copy.unavailable`) even for `core_finance` widgets where `widget.available === true` and `widget.reason === null`.
  - Additionally, inside [`DashboardWidgets.tsx:44-55`](../../frontend/src/components/product/DashboardWidgets.tsx#L44-L55), `'inventory'` is included in `needsPosition` (line 44), which short-circuits on line 47 (`return apiClient('/intelligence/position')`) before line 54 (`if (widgetKey === 'inventory') return stockApi.register(signal)`) can ever execute.

---

#### DEF-04 — [P1] Disabled Modules Remain Reachable via Sidebar, Mobile `+` Sheet, FinancialWorkspaceLinks, Direct URLs, and Backend APIs
- **Severity:** `P1`
- **Classification:** `integration` (Frontend routing/navigation + Backend `ModuleAccessPolicy` enforcement)
- **Route(s) & Endpoint(s):** `/upload`, `/invoices`, `/bills`, `/inventory`, `/inventory/stock`, `/inventory/valuation-preview`, `/projects`, `/samples`, `/estimates`, `/planning`, `/intelligence`, `/operations/calendar`, `/operations/relationships`, `/reports`, `/finance/*`, `/scan`, `/ask-finsight`
- **Role(s):** `OWNER`, `ACCOUNTANT`, `MANAGER`, `SALESPERSON`, `VIEWER`
- **Language / Device:** English (`en`) & Urdu (`ur` / RTL); Desktop & Mobile
- **Reproduction Steps:**
  1. Sign in to any seeded business (where `GET /api/business-configuration` reports `transactions`, `receivables`, `invoices`, `costing`, `production`, `commissions`, `multi_currency`, `operations`, and `reports` as `enabled: false, available: false, unavailableReason: "DISABLED"`).
  2. Inspect the desktop `Sidebar` and mobile `+` Action Sheet: observe that `Invoices`, `Bills`, `Samples`, `Costing & Estimates`, `Reports`, `Stock & WAC`, `Financial Calendar`, and all 18 `FinancialWorkspaceLinks` (`Transactions`, `Receivables`, `Payables`, `Commissions`, `Foreign Exchange`, `Recognition`, etc.) are visible and clickable.
  3. Click into `/invoices`, `/bills`, `/samples`, `/estimates`, `/reports`, `/finance/commissions`, `/finance/fx`, or `/records/receivables` and create/mutate records.
  4. Even for the two routes mapped in `routeModule` (`/scan` and `/ask-finsight`), navigate directly by URL (`http://localhost:3190/scan` or `http://localhost:3190/ask-finsight`) when `documents` or `ai` is disabled.
- **Expected Result:**
  - When a module is disabled in F02 Business Configuration, its operational routes should be hidden or marked disabled in navigation, blocked or placed in read-only history mode on direct URL navigation, and rejected on backend operational write endpoints by `ModuleAccessPolicy.requireEnabled(...)`.
- **Actual Result & Concrete Evidence:**
  - **Frontend `routeModule(path)`** ([`frontend/src/lib/api/configuration.ts:35-45`](../../frontend/src/lib/api/configuration.ts#L35-L45)) only checks `/scan` (`'documents'`) and `/ask-finsight` (`'ai'`), returning `null` for every other route in the application.
  - **Frontend `FinancialWorkspaceLinks`** ([`FinancialWorkspaceLinks.tsx:10-15`](../../frontend/src/components/layout/FinancialWorkspaceLinks.tsx#L10-L15)) does not call `useConfiguration()` at all.
  - **Frontend `IntegrationBoundary`** ([`IntegrationBoundary.tsx:23-39`](../../frontend/src/components/layout/IntegrationBoundary.tsx#L23-L39)) does not check `configuration.allowsRoute(pathname)`, allowing direct URL access to any disabled module route.
  - **Backend `ModuleWebConfiguration`** ([`ModuleWebConfiguration.java:11-30`](../../backend/src/main/java/com/app/sme_health_backend/configuration/service/ModuleWebConfiguration.java#L11-L30)) only guards write methods on `/api/documents/**` and `/api/whatsapp/**`. None of `/api/invoices`, `/api/bills`, `/api/commercial`, `/api/inventory`, `/api/stock`, `/api/samples`, `/api/projects`, `/api/estimates`, `/api/obligations`, `/api/commissions`, `/api/fx`, or `/api/reports` call `ModuleAccessPolicy`.

---

#### DEF-05 — [P1] Invoice & Bill Line Editor Omits `inventoryItemId` (Cannot Link Lines to Inventory in UI, and Editing a Seeded Draft Wipes Existing `inventory_item_id`)
- **Severity:** `P1`
- **Classification:** `frontend-only` (causes silent backend data loss when saving an existing draft)
- **Route(s) & Endpoint(s):** `/invoices/new`, `/invoices/[id]`, `/bills/new`, `/bills/[id]`; `POST /api/invoices`, `PUT /api/invoices/{id}`, `POST /api/bills`, `PUT /api/bills/{id}`
- **Role(s):** `OWNER`, `ACCOUNTANT`, `MANAGER`, `SALESPERSON`
- **Language / Device:** English (`en`) & Urdu (`ur` / RTL); Desktop & Mobile
- **Reproduction Steps:**
  1. Navigate to `/invoices/new` or `/bills/new`.
  2. Inspect the line items section (`CommercialLineEditor`). Try to link a line item to an inventory catalog item so that stock can be received (for a bill) or fulfilled/COGS calculated (for an invoice) upon finalization.
  3. Alternatively, open a seeded `DRAFT` invoice or bill that already has `inventory_item_id` populated on its lines in the database, edit the notes field, and click **Save draft**.
- **Expected Result:**
  - Each line item in `CommercialLineEditor` should include an optional Inventory Item selector (`inventoryItemId`), and `fromLineResponse` / `toLineInputs` should preserve `inventoryItemId` when loading and saving drafts.
- **Actual Result & Concrete Evidence:**
  - While backend [`CommercialDocumentRequestReader.java:49`](../../backend/src/main/java/com/app/sme_health_backend/commercial/controller/CommercialDocumentRequestReader.java#L49) reads `inventoryItemId` and [`CommercialLifecyclePanel.tsx:92-98`](../../frontend/src/components/product/CommercialLifecyclePanel.tsx#L92-L98) renders `Receive stock from bill` / `Fulfil stock for invoice` when `inventoryLinkedLines > 0`, frontend [`frontend/src/lib/commercial/form.ts:20-33`](../../frontend/src/lib/commercial/form.ts#L20-L33) and [`CommercialLineEditor.tsx:44-187`](../../frontend/src/components/commercial/CommercialLineEditor.tsx#L44-L187) completely omit `inventoryItemId`.
  - Consequently, no user-created invoice or bill can ever trigger stock receipt or stock fulfillment/COGS in the UI, and saving any edit to a seeded draft invoice/bill overwrites its `inventory_item_id` column in `commercial_document_lines` with `null`.

---

#### DEF-06 — [P1] Stock Customer Return Workflow Is Unusable Because `Original issue movement ID` Requires a Hidden 36-Character UUID
- **Severity:** `P1`
- **Classification:** `frontend-only`
- **Route(s) & Endpoint(s):** `/inventory/stock`; `POST /api/stock/movements` (`movementType = "CUSTOMER_RETURN"`)
- **Role(s):** `OWNER`, `ACCOUNTANT`
- **Language / Device:** English (`en`) & Urdu (`ur` / RTL); Desktop & Mobile
- **Reproduction Steps:**
  1. Sign in as `demo.owner@finsight.local` and navigate to `/inventory/stock`.
  2. Click on an inventory item in the register to load its **Movement history** (`[data-testid="stock-history"]`), which shows prior `SALES_ISSUE` movements as `#2`, `#4`, etc.
  3. In the **Record stock movement** form (`[data-testid="stock-movement-form"]`), select **Movement type**: `Customer return` (`CUSTOMER_RETURN`).
  4. Observe the required field **Original issue movement ID** (`[data-testid="stock-issue-movement"]`).
- **Expected Result:**
  - Selecting `CUSTOMER_RETURN` should present a dropdown of prior `SALES_ISSUE` movements for the selected item (showing `#sequenceNo`, date, quantity, and unit cost) or display the movement ID in the movement history table.
- **Actual Result & Concrete Evidence:**
  - [`StockWorkspace.tsx:128`](../../frontend/src/components/product/StockWorkspace.tsx#L128) renders a raw text `<Input label={s.issueMovement} value={issueMovementId} required />` expecting a 36-character UUID, while the movement history table ([`StockWorkspace.tsx:167-174`](../../frontend/src/components/product/StockWorkspace.tsx#L167-L174)) only displays `#${m.sequenceNo}` and never exposes `m.id`. Without opening browser DevTools Network tab to inspect JSON responses, a real user can never record a customer stock return.

---

#### DEF-07 — [P1] Recognized Invoices & Bills Display False `"Financial effect: none"` Banner and Keep Top `Void` Button Enabled, Failing With Generic Error on `409 document_has_financial_effect`
- **Severity:** `P1`
- **Classification:** `frontend-only`
- **Route(s) & Endpoint(s):** `/invoices/[id]`, `/bills/[id]`; `POST /api/invoices/{id}/void`, `POST /api/bills/{id}/void` vs. `POST /api/commercial/documents/{id}/void`
- **Role(s):** `OWNER`, `ACCOUNTANT`
- **Language / Device:** English (`en`) & Urdu (`ur` / RTL); Desktop & Mobile
- **Reproduction Steps:**
  1. Sign in as `demo.owner@finsight.local` and open a finalized invoice on `/invoices/[id]` that has been recognized into AR/AP (e.g., `recognitionStatus: "RECOGNIZED"` in the bottom `Financial lifecycle` panel).
  2. Observe the top banner (`RecordOnlyBanner`) and the `TotalsPanel` (`Financial effect: none`).
  3. Enter a void reason in the top document workspace card (`[data-testid="commercial-void-reason"]`) and click the top **Void invoice** button (`[data-testid="commercial-void-button"]`).
- **Expected Result:**
  - Once an invoice or bill is recognized (`RECOGNIZED`) or has stock movements, the top `RecordOnlyBanner` should reflect its recognized financial status, and either the top `Void` button should be replaced/disabled in favor of the lifecycle `Void recognized document` action (which reverses AR/AP and stock), or clicking it should display a clear message explaining that recognized documents must be voided via the Financial Lifecycle panel.
- **Actual Result & Concrete Evidence:**
  - [`CommercialDocumentWorkspace.tsx:211-291`](../../frontend/src/components/commercial/CommercialDocumentWorkspace.tsx#L211-L291) unconditionally renders `RecordOnlyBanner` (`"Record-only invoice — Saving or finalizing this invoice does not create a receivable, record a payment, post to the ledger, change inventory, or alter monthly records or your health score"`) and `Financial effect: none` even when the document IS recognized and settled.
  - Clicking the top **Void** button calls `POST /api/invoices/{id}/void`, which [`CommercialDocumentService.java:195-200`](../../backend/src/main/java/com/app/sme_health_backend/commercial/service/CommercialDocumentService.java#L195-L200) rejects with `409 document_has_financial_effect`. Because `document_has_financial_effect` is missing from `commercialConflictCodes` in [`frontend/src/lib/commercial/form.ts:84-91`](../../frontend/src/lib/commercial/form.ts#L84-L91), the UI displays the generic fallback `"The request could not be completed. Your entries are kept."`

---

#### DEF-08 — [P1] WhatsApp Settings Misleadingly Appear Live & Show `SENT` Deliveries Under Mock Provider While Inbound Card Says Not Configured
- **Severity:** `P1`
- **Classification:** `integration` (Backend `MockWhatsAppClient` + Frontend `/settings` presentation)
- **Route(s) & Endpoint(s):** `/settings`; `GET /api/whatsapp/preferences`, `PUT /api/whatsapp/preferences`, `GET /api/whatsapp/messages`, `GET /api/whatsapp/inbound/status`
- **Role(s):** `OWNER`, `ACCOUNTANT`, `MANAGER`, `SALESPERSON`, `VIEWER`
- **Language / Device:** English (`en`) & Urdu (`ur` / RTL); Desktop & Mobile
- **Reproduction Steps:**
  1. Sign in as `demo.owner@finsight.local` and navigate to `/settings`.
  2. Scroll to the **WhatsApp** preferences card ([`LiveSettings.tsx:47-71`](../../frontend/src/components/integration/LiveSettings.tsx#L47-L71)).
  3. Enter a phone number (`+923001234567`), check **Enable WhatsApp notifications**, and click **Save WhatsApp settings**.
  4. Inspect the delivery status list in that card (`SENT`) and compare it with the **WhatsApp inbound** card ([`OperationsViews.tsx:305-337`](../../frontend/src/components/product/OperationsViews.tsx#L305-L337)) immediately below on the same page.
- **Expected Result:**
  - When `app.whatsapp.provider=mock` ([`WhatsAppClientConfiguration.java:38-41`](../../backend/src/main/java/com/app/sme_health_backend/whatsapp/config/WhatsAppClientConfiguration.java#L38-L41)), the UI must clearly badge outbound WhatsApp as **Simulated / Mock Mode (No real WhatsApp messages are delivered)** — just as AI Explanations and Inbound WhatsApp disclose their configuration status.
- **Actual Result & Concrete Evidence:**
  - The outbound WhatsApp card displays `"Preferences saved. Delivery status appears below."` and lists message rows with status `SENT` with no indication that `MockWhatsAppClient` discarded the message in memory, while the Inbound card right below states `"WhatsApp inbound is not configured in this environment."` An SME owner will believe FinSight is actively sending live WhatsApp alerts to their phone.

---

#### DEF-09 — [P1] Complete Financial Pack (`/reports`) Renders Raw JSON Dot-Keys (`revenue.recognizedRevenue`) and Silently Drops All Nested Array Breakdowns
- **Severity:** `P1`
- **Classification:** `frontend-only`
- **Route(s) & Endpoint(s):** `/reports`; `GET /api/reports/financial-pack`
- **Role(s):** `OWNER`, `ACCOUNTANT`, `MANAGER`, `SALESPERSON`, `VIEWER`
- **Language / Device:** English (`en`) & Urdu (`ur` / RTL); Desktop & Mobile
- **Reproduction Steps:**
  1. Sign in and navigate to `/reports`.
  2. In the **Complete financial pack** panel (`[data-testid="financial-pack-panel"]`), click **Generate pack** (`[data-testid="financial-pack-generate"]`).
  3. Inspect the generated tables under `Profit and loss`, `Cash and accounts`, `Receivables and payables`, `Inventory and WAC`, `Projects, samples and estimates`, and `Budgets and goals` in both English (`en`) and Urdu (`ur`).
- **Expected Result:**
  - Report rows should display localized human-readable labels in English and Urdu, and nested breakdown arrays (such as `revenueByCategory`, `expensesByCategory`, `items`, `projects`, `budgets`, `goals`) should be rendered rather than discarded.
- **Actual Result & Concrete Evidence:**
  - In [`OperationsViews.tsx:221-236`](../../frontend/src/components/product/OperationsViews.tsx#L221-L236), `SectionTable` recursively flattens nested objects using raw JavaScript property names joined by dots (`const prefix = path ? `${path}.${k}` : k`) and ignores arrays inside objects (`else if (!Array.isArray(v)) flat.push([prefix, v])`).
  - As a result, the SME customer's printable financial pack displays raw developer identifiers like `revenue.cashSales`, `revenue.invoiceRevenue`, `revenue.recognizedRevenue`, `costOfSales.totalCostOfSales`, `profitability.grossMarginPct`, `cashAndAccounts.totalCashBalance`, etc. (even when the UI language is switched to Urdu!), and **all array breakdowns (`revenueByCategory`, `expensesByCategory`, `accounts`, `items`, `projects`, `budgets`, `goals`) are silently omitted**.

---

#### DEF-10 — [P1] Onboarding / Business Creation Redirect Loop on `/onboarding` and `/business-setup`
- **Severity:** `P1`
- **Classification:** `frontend-only`
- **Route(s) & Endpoint(s):** `/onboarding`, `/business-setup`; `POST /api/businesses`, `POST /api/businesses/{id}/select`
- **Role(s):** `OWNER` (or newly registered user creating their first business)
- **Language / Device:** English (`en`) & Urdu (`ur` / RTL); Desktop & Mobile
- **Reproduction Steps:**
  1. Navigate directly to `http://localhost:3190/onboarding` or `http://localhost:3190/business-setup` (without a `?returnTo=` query parameter).
  2. Select an existing business or fill out **Create business** and submit.
- **Expected Result:**
  - Selecting or creating a business from `/onboarding` or `/business-setup` should navigate the user into the main application dashboard (`/`) or configuration wizard, not leave them stuck on `/business-setup`.
- **Actual Result & Concrete Evidence:**
  - In [`BusinessSetup.tsx:28-42`](../../frontend/src/components/auth/BusinessSetup.tsx#L28-L42), `run` calls `businessSelectionReturn(typeof window === 'undefined' ? null : new URLSearchParams(window.location.search).get('returnTo'), fallback)` with `fallback = '/business-setup'`.
  - When there is no `?returnTo=` query param, [`frontend/src/lib/navigation/workspaces.ts:48-55`](../../frontend/src/lib/navigation/workspaces.ts#L48-L55) returns `fallback` (`'/business-setup'`), so `router.replace('/business-setup')` reloads `/business-setup` in a loop.

---

#### DEF-11 — [P1] Cross-Tab Business Switching Destroys Unsaved Form Edits and Breaks Open Detail Routes Across Tabs
- **Severity:** `P1`
- **Classification:** `integration` (Frontend `BroadcastChannel` remount + Backend session-scoped active business)
- **Route(s) & Endpoint(s):** All routes when 2+ browser tabs are open (`POST /api/businesses/{id}/select`)
- **Role(s):** `OWNER`, `ACCOUNTANT`, `MANAGER`, `SALESPERSON`, `VIEWER`
- **Language / Device:** English (`en`) & Urdu (`ur` / RTL); Desktop & Mobile
- **Reproduction Steps:**
  1. Open Tab A on *Alpha Garments & Packaging (Demo)* and begin filling out a multi-line invoice on `/invoices/new` (or open an existing invoice `/invoices/{id}`).
  2. Open Tab B, navigate to `/settings`, and switch the active business to *Apex Digital Solutions (Demo)*.
  3. Return to Tab A.
- **Expected Result:**
  - Either active business context should be scoped per tab/route or Tab A should warn before discarding unsaved form state and gracefully redirect entity-specific detail routes (`/invoices/[id]`, `/projects/[id]`, etc.) to the parent register when the active business changes in another tab.
- **Actual Result & Concrete Evidence:**
  - Tab B broadcasts `{ type: 'business-switch', phase: 'done' }` on `BroadcastChannel('finsight-session')` ([`SessionProvider.tsx:181-229`](../../frontend/src/components/auth/SessionProvider.tsx#L181-L229)).
  - Tab A increments `session.epoch`, changing the React `key` on `<div key={`${session.epoch}:${session.user.userId}:${session.business.businessId}`}>` in [`IntegrationBoundary.tsx:39`](../../frontend/src/components/layout/IntegrationBoundary.tsx#L39).
  - Tab A immediately unmounts and remounts the entire page tree: on `/invoices/new`, all unsaved line items are wiped without warning; on `/invoices/{id}`, `/bills/{id}`, `/projects/{id}`, `/samples/{id}`, `/estimates/{id}`, or `/upload/{id}`, the remounted page fetches `{id}` against the new business in the shared session and renders a `404` error state (`"not found in the active business"`).

---

#### DEF-12 — [P2] Raw Database UUID Text Inputs Required Across Commercial Evidence, Sample Actuals, and Stock Movements
- **Severity:** `P2`
- **Classification:** `frontend-only`
- **Route(s) & Endpoint(s):** `/invoices/[id]`, `/bills/[id]`, `/samples/[id]`, `/inventory/stock`
- **Role(s):** `OWNER`, `ACCOUNTANT`, `MANAGER`
- **Language / Device:** English (`en`) & Urdu (`ur` / RTL); Desktop & Mobile
- **Reproduction Steps:**
  1. Open `/invoices/[id]` or `/bills/[id]` and inspect **Link uploaded evidence** in `CommercialLifecyclePanel` ([`CommercialLifecyclePanel.tsx:120-124`](../../frontend/src/components/product/CommercialLifecyclePanel.tsx#L120-L124)).
  2. Open `/samples/[id]` and inspect **Record sample cost** (`Transaction ID`) and **Record sample recovery** (`Invoice document ID` / `Transaction ID`) in `SampleActualsPanel` ([`ActualsPanels.tsx:114-128`](../../frontend/src/components/product/ActualsPanels.tsx#L114-L128)).
  3. Open `/inventory/stock` and inspect `Project ID`, `Sample ID`, and `Transaction ID` in `MovementForm` ([`StockWorkspace.tsx:135-137`](../../frontend/src/components/product/StockWorkspace.tsx#L135-L137)).
- **Expected Result:**
  - Users should select uploaded documents, posted transactions, invoices, projects, and samples from human-readable dropdowns/pickers showing document numbers, dates, descriptions, and amounts.
- **Actual Result & Concrete Evidence:**
  - All of these fields are plain `<Input>` boxes requiring the user to manually locate and paste 36-character hexadecimal UUIDs.

---

#### DEF-13 — [P2] Document Review & Confirmation UI Enforces Legacy Monthly Contribution Fields & Duplicate Warnings That Contradict V52 Derived Monthly Model
- **Severity:** `P2`
- **Classification:** `integration` (Frontend `LiveDocumentReview.tsx` + Backend `DocumentConfirmationService.java`)
- **Route(s) & Endpoint(s):** `/upload/[id]`; `POST /api/documents/{id}/confirm`
- **Role(s):** `OWNER`, `ACCOUNTANT`
- **Language / Device:** English (`en`) & Urdu (`ur` / RTL); Desktop & Mobile
- **Reproduction Steps:**
  1. Sign in as `demo.owner@finsight.local` and open a seeded `extracted` or `needs_review` document on `/upload/[id]`.
  2. Read the top helper text (`t.live.contributionHelp`: *"Confirming saves the reviewed values as document evidence. Monthly financial figures are calculated automatically from recorded transactions, invoices, bills and stock activity."*).
  3. Now inspect the form fields immediately below in [`LiveDocumentReview.tsx:96-124`](../../frontend/src/components/integration/LiveDocumentReview.tsx#L96-L124).
- **Expected Result:**
  - Since V52 (`monthly-projection-v1`) derives monthly records from ledger transactions and `DocumentConfirmationService` no longer mutates `monthly_records`, confirming a document should simply confirm its extracted metadata (amount, date, counterparty, document type) and offer a direct button to **Create Invoice / Bill / Transaction from this Document** or **Link to Existing Invoice / Bill**.
- **Actual Result & Concrete Evidence:**
  - The form still forces the user to pick **Financial classification** (`Revenue`, `Operating expenses`, `Cost of goods sold`, `Do not include in monthly record`) and **Cash-flow impact** (`Cash received`, `Cash spent`, `No cash-flow change`), labels the submit button **"Confirm and add once"**, and if a manual monthly record exists for that month, displays a scary warning (`"This month already has manually entered figures. Confirming again may count the same receipt or expense twice"`). Once confirmed, there is no button or link to create a transaction, invoice, or bill from the confirmed document.

---

#### DEF-14 — [P2] Operational Relationships View (`/operations/relationships`) Drops Document Links and Routes All Obligations (Including Payables) to `/records/receivables`
- **Severity:** `P2`
- **Classification:** `frontend-only`
- **Route(s) & Endpoint(s):** `/operations/relationships`; `GET /api/intelligence/relationships`
- **Role(s):** `OWNER`, `ACCOUNTANT`, `MANAGER`, `SALESPERSON`, `VIEWER`
- **Language / Device:** English (`en`) & Urdu (`ur` / RTL); Desktop & Mobile
- **Reproduction Steps:**
  1. Navigate to `/operations/relationships`.
  2. Inspect the **Document, stock and ledger links** table (`[data-testid="relationships-table"]`).
  3. Try to click on a `DOCUMENT` badge (`INV-000001`, `BILL-000001`), or click on a supplier payable `OBLIGATION` link.
- **Expected Result:**
  - `DOCUMENT` items should link to `/invoices/{id}` or `/bills/{id}`, and supplier payable `OBLIGATION` items should link to `/records/payables`.
- **Actual Result & Concrete Evidence:**
  - In [`OperationsViews.tsx:160-161`](../../frontend/src/components/product/OperationsViews.tsx#L160-L161):
    ```ts
    const href: Record<string, string | null> = {
      DOCUMENT: null,
      OBLIGATION: '/records/receivables',
      TRANSACTION: '/finance/transactions',
      STOCK: '/inventory/stock',
      PROJECT: '/projects',
      SAMPLE: '/samples',
      ESTIMATE: '/estimates'
    };
    ```
  - Every `DOCUMENT` renders as unclickable plain text (`DOCUMENT: null`), and every supplier payable `OBLIGATION` routes to `/records/receivables`.

---

#### DEF-15 — [P2] Live `/scan` Route Renders Identical `<LiveDocuments />` Component as `/upload`, Missing Camera Capture and Multi-File Upload
- **Severity:** `P2`
- **Classification:** `frontend-only`
- **Route(s) & Endpoint(s):** `/scan` vs. `/upload`
- **Role(s):** `OWNER`, `ACCOUNTANT`
- **Language / Device:** English (`en`) & Urdu (`ur` / RTL); Desktop & Mobile
- **Reproduction Steps:**
  1. In live mode (`NEXT_PUBLIC_DATA_MODE=live`), click **Scan** in the sidebar or mobile `+` sheet (`/scan`).
  2. Compare `/scan` with `/upload`.
- **Expected Result:**
  - `/scan` is advertised in navigation as **"Scan Document — Take a photo or select files"** (`DemoPage` in [`frontend/src/app/scan/page.tsx:124-141`](../../frontend/src/app/scan/page.tsx#L124-L141) has a `capture="environment"` camera input and a `multiple` file input), whereas `/upload` is the document queue.
- **Actual Result & Concrete Evidence:**
  - Both [`frontend/src/app/scan/page.tsx:4`](../../frontend/src/app/scan/page.tsx#L4) and [`frontend/src/app/upload/page.tsx:4`](../../frontend/src/app/upload/page.tsx#L4) execute `return isDemoMode ? <DemoPage/> : <LiveDocuments/>;`.
  - [`LiveDocuments.tsx:49`](../../frontend/src/components/integration/LiveDocuments.tsx#L49) has only a single `<input type="file" accept="application/pdf,image/jpeg,image/png">` without `capture="environment"` and without `multiple`, so mobile camera capture and batch file selection do not exist in live mode.

---

#### DEF-16 — [P2] Live Search (`/search`) Ignores `?q=` URL Query Parameter
- **Severity:** `P2`
- **Classification:** `frontend-only`
- **Route(s) & Endpoint(s):** `/search`, `/search?q=...`; `GET /api/finance/search`
- **Role(s):** `OWNER`, `ACCOUNTANT`, `MANAGER`, `SALESPERSON`, `VIEWER`
- **Language / Device:** English (`en`) & Urdu (`ur` / RTL); Desktop & Mobile
- **Reproduction Steps:**
  1. Navigate to `http://localhost:3190/search?q=Invoice` (or refresh `/search` after searching).
- **Expected Result:**
  - `LiveSearch` should read `useSearchParams().get('q')`, populate the search input, and execute the search automatically, updating the URL query string so browser refresh and back/forward preserve search results.
- **Actual Result & Concrete Evidence:**
  - [`LiveSearch.tsx:16-36`](../../frontend/src/components/integration/LiveSearch.tsx#L16-L36) initializes `const [query, setQuery] = useState('')` and never reads or updates `useSearchParams()`. Refreshing `/search` or visiting `/search?q=...` always resets to an empty search page.

---

#### DEF-17 — [P2] Direct URL Navigation to `/new` Routes Is Not Guarded by `IntegrationBoundary` for Read-Only Roles (`VIEWER` / `SALESPERSON`)
- **Severity:** `P2`
- **Classification:** `frontend-only` (Backend properly enforces `403 Forbidden` on submit)
- **Route(s) & Endpoint(s):** `/invoices/new`, `/bills/new`, `/projects/new`, `/samples/new`, `/estimates/new`, `/records/new`
- **Role(s):** `VIEWER`, `SALESPERSON`
- **Language / Device:** English (`en`) & Urdu (`ur` / RTL); Desktop & Mobile
- **Reproduction Steps:**
  1. Sign in as `demo.viewer@finsight.local` (`VIEWER`).
  2. Navigate directly via the browser address bar to `http://localhost:3190/records/new` or `http://localhost:3190/invoices/new`.
- **Expected Result:**
  - Because `routePermission('/invoices/new')` and `routePermission('/records/new')` return `'RECORD_CREATE_UPDATE'` in [`frontend/src/lib/api/navigation.ts:6-7`](../../frontend/src/lib/api/navigation.ts#L6-L7), `IntegrationBoundary` should block or redirect a `VIEWER` who lacks `RECORD_CREATE_UPDATE` at the route boundary.
- **Actual Result & Concrete Evidence:**
  - [`IntegrationBoundary.tsx:23-39`](../../frontend/src/components/layout/IntegrationBoundary.tsx#L23-L39) only checks whether `session.status === 'authenticated'` and `session.business` is selected; it never calls `routePermission(pathname)`. Each individual page must remember to check `can('RECORD_CREATE_UPDATE')` itself.

---

#### DEF-18 — [P2] Demo Startup & Seeding Scripts Have Broken Credentials, Missing `MANAGER` Account, and Hardcoded Developer Paths
- **Severity:** `P2`
- **Classification:** `environment`
- **Route(s) & Endpoint(s):** `scripts/start-demo.ps1`, `scripts/DemoScenarioSeeder.java`, `scripts/FlywayMigratorDemo.java`
- **Role(s):** All demo roles (`OWNER`, `ACCOUNTANT`, `MANAGER`, `SALESPERSON`, `VIEWER`)
- **Reproduction Steps:**
  1. Inspect [`scripts/start-demo.ps1:109-115`](../../scripts/start-demo.ps1#L109-L115) and compare the printed password (`FinSight@Demo2026!`) against [`scripts/DemoScenarioSeeder.java:178-183`](../../scripts/DemoScenarioSeeder.java#L178-L183).
  2. Check `DemoScenarioSeeder.java` for a `MANAGER` user account.
  3. Inspect [`scripts/FlywayMigratorDemo.java:10`](../../scripts/FlywayMigratorDemo.java#L10).
- **Expected Result:**
  - `start-demo.ps1` should print the actual working passwords (`FinSight@Owner2026!`, `FinSight@Account2026!`, `FinSight@Sales2026!`, `FinSight@Viewer2026!`), `DemoScenarioSeeder.java` should seed all 5 `MembershipRole` values including `MANAGER`, and `FlywayMigratorDemo.java` should resolve migrations relative to the repository root.
- **Actual Result & Concrete Evidence:**
  - `start-demo.ps1` prints `Password (all accounts): FinSight@Demo2026!`, which fails login with `401 Invalid credentials` for every seeded user.
  - `DemoScenarioSeeder.java` seeds `OWNER`, `ACCOUNTANT`, `SALESPERSON`, and `VIEWER`, omitting `MANAGER`.
  - `FlywayMigratorDemo.java` hardcodes `"filesystem:C:/Users/PC/Projects/sme-financial-webapp-pr4-review/backend/src/main/resources/db/migration"`, which fails on any other machine or checkout path.

---

#### DEF-19 — [P3] Browser Back Button From `/?auth=login` or `/?auth=register` to `/` Fails to Return to Public Landing Page
- **Severity:** `P3`
- **Classification:** `frontend-only`
- **Route(s) & Endpoint(s):** `/` $\to$ `/?auth=login` $\to$ Browser `Back` (`/`), or `/` after `Sign out`
- **Role(s):** Anonymous visitor / Signed-out user
- **Language / Device:** English (`en`) & Urdu (`ur` / RTL); Desktop & Mobile
- **Reproduction Steps:**
  1. As an anonymous visitor on `http://localhost:3190/`, click **Sign In** in the navbar (navigating to `http://localhost:3190/?auth=login`).
  2. Click the browser **Back** button to return to `http://localhost:3190/`.
- **Expected Result:**
  - Navigating back to `/` (where `searchParams.get('auth')` is `null`) should render `<LandingPage />`.
- **Actual Result & Concrete Evidence:**
  - In [`frontend/src/app/page.tsx:33-39`](../../frontend/src/app/page.tsx#L33-L39), `useEffect` sets `setAuthRequested(true)` when `authModeParam === 'login' || authModeParam === 'register'`, but never resets `authRequested` to `false` when `authModeParam` becomes `null`. Thus pressing Back to `/` stays stuck on `<AuthScreen />`.

---

#### DEF-20 — [P3] Active Business Badge Is Hidden on Mobile Screens (`< 640px`), Leaving Multi-Business Mobile Users Unaware of Active Tenant Context
- **Severity:** `P3`
- **Classification:** `frontend-only`
- **Route(s) & Endpoint(s):** All authenticated routes (`AppShell` / `PageHeader`) on mobile viewports (`390x844` and `360x568`)
- **Role(s):** `OWNER`, `ACCOUNTANT`, `MANAGER`, `SALESPERSON`, `VIEWER`
- **Language / Device:** English (`en`) & Urdu (`ur` / RTL); Mobile (`390x844`, `360x568`)
- **Reproduction Steps:**
  1. Sign in on a mobile viewport (`390x844` or `360x568`) as a user belonging to multiple businesses (e.g., 4 Demo businesses + 4 Sandbox businesses).
  2. Navigate to `/invoices`, `/records`, `/finance/transactions`, or `/inventory/stock`.
  3. Look for the active business name in the top header or bottom navigation bar.
- **Expected Result:**
  - Mobile users should always be able to see (and tap to switch) which business they are currently viewing or posting transactions into.
- **Actual Result & Concrete Evidence:**
  - [`PageHeader.tsx:43-49`](../../frontend/src/components/layout/PageHeader.tsx#L43-L49) applies `hidden sm:flex` to the business name badge (`[data-testid="header-business-name"]`). On any screen narrower than `640px`, the active business name is completely hidden on every page except `/` and `/settings`.

---

## 4. UX & Product Problems (Technically Functioning but Confusing or Misleading for a Real SME)

The following issues are **not** crashes or broken API contracts, but make FinSight confusing, overwhelming, or error-prone for a real Pakistani SME owner or accountant:

1. **Mobile `+` "Quick Actions" Sheet Dumps 33 Uncategorized Navigation & Action Buttons into a Single Scrollable Modal ([`MobileNav.tsx:163-272`](../../frontend/src/components/layout/MobileNav.tsx#L163-L272)):**
   - Tapping the primary `+` button on mobile opens a bottom sheet containing all 18 `FinancialWorkspaceLinks` buttons (`Financial Records`, `Monthly Summaries`, `Accounts`, `Transactions`, `Customers & Suppliers`, `Receivables`, `Payables`, `Advances`, `Payment Calendar`, `Bank Reconciliation`, `Recurring`, `Drafts to Review`, `Cash Forecast`, `Funding Gaps`, `Financial Risks`, `Recognition`, `Commissions`, `Foreign Exchange`) followed by 15 more buttons (`Financial Intelligence`, `Budgets & P&L`, `Financial Calendar`, `Stock & WAC`, `Ask FinSight`, `Projects`, `Samples`, `Invoices`, `Bills`, `Inventory`, `Costing`, `Reports`, `Add Record`, `Scan Document`, `Upload Files`). On a `360x568` or `390x844` phone screen, this requires scrolling through 4+ screen heights just to find "Scan Document" or "Add Record".
2. **Duplicate & Overlapping Navigation Between `/records?view=*` Tabs and `/finance/*` Standalone Routes ([`frontend/src/app/records/page.tsx:35-50`](../../frontend/src/app/records/page.tsx#L35-L50) vs. [`FinancialWorkspaceLinks.tsx:10-15`](../../frontend/src/components/layout/FinancialWorkspaceLinks.tsx#L10-L15)):**
   - Every core finance capability exists at **two different URLs** with slightly different surrounding chrome:
     - `/records?view=activity` vs. `/finance/transactions`
     - `/records?view=accounts` vs. `/finance/accounts`
     - `/records?view=contacts` vs. `/records/counterparties`
     - `/records?view=receivables` vs. `/records/receivables`
     - `/records?view=payables` vs. `/records/payables`
     - `/records?view=reconciliation` vs. `/records/reconciliation`
     - `/records?view=recurring` vs. `/records/recurring`
     - `/records?view=forecast` vs. `/finance/forecast`
     - `/records?view=risks` vs. `/finance/risks`
   - Worse, `/records/calendar` (Payment Calendar) and `/operations/calendar` (Financial Operations Calendar) are two separate calendar pages in the navigation with overlapping obligations/recurring items.
3. **Two Separate Inventory Pages (`/inventory` Catalog & Stateless WAC Preview vs. `/inventory/stock` Authoritative Stock & WAC Register):**
   - Creating an inventory item on `/inventory` shows `"Phase A — Catalog & Stateless Valuation Preview / Financial effect: none"`, while actual stock on hand, reorder alerts, and WAC movements live on `/inventory/stock` (`Stock & WAC`). SME users expect a single unified Inventory workspace where items show their current stock and WAC alongside a movement history tab.
4. **Two Separate Project Views (`/projects/[id]` Planned Budgets vs. `/finance/projects/[id]/actuals` Actual Profitability & Milestones):**
   - `/projects/[id]` prominently displays `"Planned-only — No actual costs, revenue, or ledger postings"`, while actual project revenue, costs, milestones, and variance live on a separate sub-route `/finance/projects/[id]/actuals`.
5. **Two Separate Reports Experiences on `/reports` (`BusinessFinancialReportView` Snapshot Summary vs. `FinancialPackView` Ledger Pack):**
   - `/reports` renders the Phase-A monthly-snapshot summary report at the top and stacks the Phase-3 `Complete financial pack` underneath it on the same page without explaining when an SME should use one versus the other.
6. **Three Different Ways to Record Revenue/Expenses With Confusing Terminology ("Monthly Supplemental Inputs" vs. "Transactions" vs. "Invoices/Bills"):**
   - Clicking **Add Monthly Record** (`/records/new`) takes the user to `MonthlyAutomationPanel`, which explains that monthly records are now automatically derived (`monthly-projection-v1`) and only offers `"Supplemental monthly inputs"` (manual override/supplemental figures) and `"Historical cutover"`, whereas daily sales/expenses must be posted in `/finance/transactions` or `/invoices`. Yet the primary CTA button in the Dashboard header and `/records/history` is still labeled **"Add Monthly Record"**.
7. **Bank Reconciliation (`/records/reconciliation`) Requires Manual Entry of Statement Lines Instead of CSV/OFX Import:**
   - While `/records/import` supports CSV import for transactions, Bank Reconciliation requires adding bank statement lines one by one or matching manually without a direct bank statement CSV uploader on the reconciliation screen.
8. **Multi-Currency FX (`/finance/fx`) Requires Manual Rate Entry Before Foreign-Currency Posting Without Default Indicative Rates or Rate Prompt Inline:**
   - If a user attempts to post or revalue a foreign-currency account (`USD`, `AED`, `GBP`, `EUR`) without first navigating to `/finance/fx` and saving an exchange rate for that date/currency, the workflow fails rather than prompting for the exchange rate inline.
9. **AI Copilot (`/ask-finsight`) & Dashboard AI Explanations (`/`) Show Generic Disabled/Unavailable Banners By Default:**
   - When `AI_ENABLED=false` (default), navigating to `/ask-finsight` shows a sparse fallback message rather than explaining how an administrator enables AI or offering deterministic financial Q&A shortcuts.
10. **Header Business Name Pill on Desktop Is Not Interactive ([`PageHeader.tsx:43-49`](../../frontend/src/components/layout/PageHeader.tsx#L43-L49)):**
    - The business name pill (`Building2` icon + business name) in the top-left of every page looks like a business switcher dropdown, but clicking it does nothing — users must navigate to `/settings` or `/business-setup` to switch businesses.

---

## 5. Workflow-by-Workflow QA Coverage Matrix (`153e94b214ba24ac1d81a094eff3805334edaad9`)

| Capability / Workflow | Primary Route(s) | Roles Tested | EN / UR (RTL) & Desktop / Mobile | QA Status & Key Findings |
|---|---|---|---|---|
| **Public Landing & Auth** | `/`, `/landing`, `/?auth=login`, `/?auth=register` | Anonymous, All 5 roles | EN & UR; `1440x900`, `390x844`, `360x568` | **Partial:** Landing & login work; browser Back from `/?auth=login` to `/` stays stuck on `<AuthScreen />` (**DEF-19**); `start-demo.ps1` prints wrong password (**DEF-18**). |
| **Onboarding & Business Setup** | `/onboarding`, `/business-setup` | `OWNER`, Multi-business users | EN & UR; Desktop & Mobile | **Broken redirect:** Selecting/creating a business on `/onboarding` or `/business-setup` without `?returnTo=` redirects back to `/business-setup` (**DEF-10**). Cross-tab switch wipes Tab A forms & causes `404` on detail routes (**DEF-11**). |
| **F02 Module Settings** | `/settings` | `OWNER` (write), others (read) | EN & UR; Desktop & Mobile | **Blocked (`P0`):** Seeded `"commercial"` key in `requested_modules` causes `400 Unknown module key.` on Preview, Apply Recommended, and Save (**DEF-02**). Disabled modules are not enforced (**DEF-04**). |
| **Dashboard & F13 Widgets** | `/`, `/health/components` | All 5 roles | EN & UR; Desktop & Mobile | **Degraded (`P1`):** `health-score-v1` and Phase-1 widgets render, but all 18 Phase-2/3 `PRODUCT_WIDGETS` are missing from `implementedWidgetKeys` and permanently locked in `WidgetPicker` (**DEF-03**). |
| **OCR / Scan / Upload** | `/scan`, `/upload`, `/upload/[id]` | `OWNER`, `ACCOUNTANT`, `VIEWER` | EN & UR; Desktop & Mobile | **Blocked (`P0`):** Default `unavailable` OCR puts uploads in `failed` status with no manual edit/confirm fallback and blocks evidence linking (**DEF-01**). Legacy confirmation form contradicts V52 derived model (**DEF-13**). `/scan` lacks camera/multi-file in live mode (**DEF-15**). |
| **Invoices & Bills** | `/invoices`, `/invoices/new`, `/invoices/[id]`, `/bills`, `/bills/new`, `/bills/[id]` | `OWNER`, `ACCOUNTANT`, `MANAGER`, `SALESPERSON`, `VIEWER` | EN & UR; Desktop & Mobile | **Degraded (`P1`):** Line editor omits `inventoryItemId` and wipes existing links on draft save (**DEF-05**). Top `Void` button fails with unhandled `409 document_has_financial_effect` on recognized docs & banner falsely states `Financial effect: none` (**DEF-07**). Evidence input requires raw UUID (**DEF-12**). |
| **Payments, Collections, AR/AP & Advances** | `/records/receivables`, `/records/payables`, `/records?view=advances`, `/records?view=calendar` | `OWNER`, `ACCOUNTANT`, `VIEWER` | EN & UR; Desktop & Mobile | **Functioning (with UX duplication):** Settlements, advances, credit/debit notes, and aging work against backend obligations; routes remain reachable even when `receivables` module is disabled (**DEF-04**). |
| **Accounts, Transactions, Transfers, Reversals & Cutover** | `/finance/accounts`, `/finance/transactions`, `/records/bridge`, `/records/import`, `/records?view=review` | `OWNER`, `ACCOUNTANT`, `VIEWER` | EN & UR; Desktop & Mobile | **Functioning:** Posting, internal transfers, reversals, CSV import, and drafts work with idempotency and optimistic locking. Primary CTA still says "Add Monthly Record" (`/records/new`). |
| **Inventory & WAC / Stock** | `/inventory`, `/inventory/valuation-preview`, `/inventory/stock` | `OWNER`, `ACCOUNTANT`, `VIEWER` | EN & UR; Desktop & Mobile | **Degraded (`P1`):** Catalog, opening stock, purchase receipts, sales issues, and adjustments work, but `CUSTOMER_RETURN` requires an invisible 36-char `Original issue movement ID` UUID (**DEF-06**), and `Project ID`/`Sample ID`/`Transaction ID` are raw UUID inputs (**DEF-12**). |
| **Projects & Project Actuals** | `/projects`, `/projects/new`, `/projects/[id]`, `/finance/projects/[id]/actuals` | `OWNER`, `ACCOUNTANT`, `MANAGER`, `VIEWER` | EN & UR; Desktop & Mobile | **Functioning (split UX):** Planned revisions/approvals and actuals/milestones work; split across two routes and unguarded when `projects` module is disabled (**DEF-04**). |
| **Samples & Sample Actuals** | `/samples`, `/samples/new`, `/samples/[id]` | `OWNER`, `ACCOUNTANT`, `MANAGER`, `VIEWER` | EN & UR; Desktop & Mobile | **Degraded (`P2`):** Planned sample costing works; recording actual sample cost or recovery on `/samples/[id]` requires pasting raw `Transaction ID` and `Invoice document ID` UUIDs (**DEF-12**). |
| **Costing & Estimates** | `/estimates`, `/estimates/new`, `/estimates/preview`, `/estimates/[id]` | `OWNER`, `ACCOUNTANT`, `MANAGER`, `VIEWER` | EN & UR; Desktop & Mobile | **Functioning:** Stateless preview, persisted revisions, approval, and exact-fit F05 project budget conversion work; unguarded when `costing` module is disabled (**DEF-04**). |
| **Planning (P&L, Budgets, Goals, Break-Even, Classify)** | `/planning` | `OWNER`, `ACCOUNTANT`, `VIEWER` | EN & UR; Desktop & Mobile | **Functioning:** P&L, budget creation/approval/variance, financial goals, break-even analysis, and expense classification work end-to-end. |
| **Intelligence, Action Center & Operations** | `/intelligence`, `/operations/calendar`, `/operations/relationships` | All 5 roles | EN & UR; Desktop & Mobile | **Degraded (`P2`):** `/operations/relationships` drops `DOCUMENT` links (`null`) and sends all `OBLIGATION` links to `/records/receivables` (**DEF-14**). |
| **Reports & Financial Pack** | `/reports` | All 5 roles | EN & UR; Desktop & Mobile | **Degraded (`P1`):** `Complete financial pack` renders raw camelCase dot-keys (`revenue.recognizedRevenue`) in EN & UR and silently drops all nested array tables (**DEF-09**). |
| **Copilot & AI Explanations** | `/ask-finsight`, `/` | All 5 roles | EN & UR; Desktop & Mobile | **Functioning (gated):** Properly checks `ai` module and AI status on backend; direct URL `/ask-finsight` is not blocked by `IntegrationBoundary` when disabled (**DEF-04**). |
| **WhatsApp Settings & Inbound** | `/settings` | `OWNER` (write), others (read) | EN & UR; Desktop & Mobile | **Misleading (`P1`):** Outbound WhatsApp saves and shows `SENT` deliveries under `MockWhatsAppClient` with no mock/simulated warning while Inbound card says `"not configured"` (**DEF-08**). |
| **Sharia / Zakat & Search** | `/sharia-zakat`, `/search` | All 5 roles | EN & UR; Desktop & Mobile | **Degraded (`P2`):** `LiveSearch` ignores `?q=` query parameter (**DEF-16**). |
