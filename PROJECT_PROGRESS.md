# FinSight — Project Progress & Development State

**Last Updated:** 2026-09-14  
**Status:** Persistent Development State Document  
**Authority:** Grounded strictly in repository code, Git history, and Design System Specification v1.0.

---

## 1. Project Identity

* **Project Name:** FinSight
* **Tagline / Purpose:** SME Financial Health Platform — Providing financial health scoring, plain-language insights, actionable recommendations, cash flow monitoring, bilingual English/Urdu support, and AI-assisted document extraction for SMEs.
* **Repository Type:** Monorepo (`sme-financial-webapp/`)
  ```text
  sme-financial-webapp/
  ├── ai-service/             # Vision LLM / OCR document processing (Python/FastAPI - planned)
  ├── backend/                # Core business API & persistence (Java 21 / Spring Boot 4.1.1)
  ├── database/               # Database migrations and seed scripts (PostgreSQL)
  ├── docs/                   # Project documentation, API specs, architectural decisions
  ├── frontend/               # Web application (Next.js 16.3.5 App Router, React 19, TypeScript, Tailwind CSS v4)
  ├── docker-compose.yml      # Container orchestration for local PostgreSQL
  ├── README.md               # Monorepo overview
  └── PROJECT_PROGRESS.md     # Persistent state & handoff record (this file)
  ```
* **Current Development Phase:** Phase 1 (Frontend Foundation), Phase 2 (Onboarding Flow), Phase 3 (Financial Health Dashboard Hub), & Phase 4 (Dedicated Manual Financial Data Input) COMPLETE & VERIFIED; Phase 5 (Document Ingestion & Verification UI) READY TO START.

---

## 2. Repository State

* **Current Git Branch:** `dev/suleman`
* **Upstream Status:** Up to date with `origin/dev/suleman`
* **Current Commit / Hash:** `ea21c58` (`ea21c58 test: harden validation and error handling`)
* **Recent Commits:**
  * `ea21c58` — `test: harden validation and error handling`
  * `610090d` — `feat(profile): add business profile and validation handling`
  * `6c28093` — `feat(records): implement monthly record input and validation`
  * `24d86f6` — `chore(database): configure Flyway migrations`
  * `c068000` — `feat(database): add initial PostgreSQL schema`
* **Working Tree State:**
  * Deleted: `frontend/.gitkeep`
  * Untracked: `frontend/` (Next.js application, token definitions, UI primitives, layout shell, i18n infrastructure, API layer)
  * No uncommitted modifications to backend or root configuration files.
* **Known PR / Merge State:** Branch `dev/suleman` is active; `main` branch is 7 commits behind.

---

## 3. Architecture

### Implemented Architecture:
1. **Frontend (`frontend/`)**:
   * **Framework:** Next.js 16.3.5 (App Router, Turbopack, React 19, TypeScript 5).
   * **Styling:** Tailwind CSS v4 configured with CSS Custom Properties in `src/styles/tokens.css`.
   * **Typography:** Google Fonts loaded via `next/font/google`: `Plus_Jakarta_Sans` (Headings/Display), `Inter` (UI/Data), `Noto_Nastaliq_Urdu` (authoritative Urdu font).
   * **Icons:** `lucide-react` directly mapping to specification icon concepts.
   * **Bilingual Engine:** `LanguageProvider` with synchronized document attributes (`lang`, `dir`), English/Urdu dictionaries, and CSS logical properties.
   * **API / Data Layer:** Typed fetch client (`client.ts`) with normalized `ApiError` and a typed mock adapter (`adapter.ts`) matching backend DTO shapes 1:1.
2. **Backend (`backend/`)**:
   * **Framework:** Java 21, Spring Boot 4.1.1, Maven.
   * **Persistence:** Spring Data JPA with PostgreSQL.
   * **Migrations:** Flyway database migrations (`V1__initial_schema.sql`).
   * **Validation:** Jakarta Bean Validation (`@Valid`, `@NotNull`, `@Pattern`, custom error handling).
   * **Controllers:** `BusinessProfileController` (`/api/profile`), `MonthlyRecordController` (`/api/records/monthly`).
3. **Database (`database/` & Docker)**:
   * PostgreSQL container specified via `docker-compose.yml` (Port 5432, DB `sme_health`, User `sme_user`).
   * 4 tables created: `business_profiles`, `monthly_records`, `uploaded_documents`, `score_results`.

### Planned Architecture (Not Yet Implemented):
1. **AI Service (`ai-service/`)**: Document processing microservice (currently empty skeleton).
2. **Backend Scoring Engine**: Scheduled or on-demand service to calculate financial health scores from `monthly_records`.
3. **Authentication Layer**: Spring Security JWT / session authentication (currently endpoints use direct `userId` UUID).
4. **WhatsApp Integration**: Asynchronous delivery service for monthly score dispatch.

---

## 4. Specification Status

| # | Feature Area | Status | Implementation Summary |
|---|---|---|---|
| 1 | **Financial Data Input** | **COMPLETE & VERIFIED** | Full manual financial worksheet implemented at `/records/new` and `/records/[id]`. 5 mandatory core vitals using `CurrencyInput`, optional precision boosters preserving nulls, human-friendly month picker, non-blocking outflow warning confirmation flow (`Dialog`), geometry-matched loading skeletons, and zero client-side scoring. Backend `POST /api/records/monthly` verified in Spring Boot. |
| 2 | **Financial Health Scoring** | **NOT STARTED** | DB schema table `score_results` exists. No Java scoring engine implemented yet. Frontend contract `ScoreResult` and triple-coded `StatusBadge` defined; zero client-side score computation. |
| 3 | **Cash Flow Monitoring** | **PARTIALLY IMPLEMENTED** | Backend records monthly inflows, outflows, revenues, and EOM balances. Frontend chart and analytics widgets pending Phase 3. |
| 4 | **Insight Generation** | **NOT STARTED** | Neither backend rule-based engine nor frontend display card implemented yet. |
| 5 | **Recommendations** | **NOT STARTED** | Neither backend recommendation prioritization nor frontend display card implemented yet. |
| 6 | **Bilingual UI (English & Urdu)** | **COMPLETE (Foundation)** | Backend stores `language_preference` (`en`/`ur`). Frontend has full dynamic RTL switching, Noto Sans Arabic typography, Western numerals preserved in Urdu currency (`2,450,000 روپے`), and translation dictionaries. |
| 7 | **Dashboard** | **COMPLETE & VERIFIED** | Canonical Financial Health Dashboard Hub implemented at `/`. Adheres to Section 8.2 & 8.3 information hierarchy: Score Hero (composite score, triple-coded band, semi-circle meter, provisional notice), Why This Score (plain-language insight), Next Step (single prioritized recommendation), Score Precision (6px progress bar), Cash Flow Trend (inflow vs outflow SVG chart, LTR time axis), Five Health Pillars (null = Pending Information, never 0%), and Recent Monthly Records. Anti-card-everything rule strictly respected. |
| 8 | **WhatsApp Delivery** | **NOT STARTED** | DB schema stores `whatsapp_number` and `whatsapp_opt_in`. Messaging service not built. Frontend opt-in checkbox primitive verified. |
| 9 | **Trend Projection** | **NOT STARTED** | Backend trend algorithm not implemented. Graceful thin-data behavior (<3 months) specified and documented in contracts. |
| 10 | **Sharia / Zakat** | **PARTIALLY IMPLEMENTED** | Backend supports `financing_type` (`none`, `conventional`, `islamic`). Dedicated Zakat calculation UI pending Phase 6. |
| 11 | **OCR / Parser** | **NOT STARTED** | `ai-service/` is empty. DB schema `uploaded_documents` exists with 6 status states. |
| 12 | **Camera / Bulk Upload** | **PARTIALLY IMPLEMENTED** | Frontend mobile bottom sheet contains triggers for "Scan document" and "Upload files". Ingestion screens and file upload endpoints pending Phase 5. |
| 13 | **Global Search** | **PARTIALLY IMPLEMENTED** | Frontend header includes keyword/substring search dialog trigger. Full multi-entity search index pending Phase 6. |

---

## 5. Backend Status

* **Entities & Repositories:**
  * `BusinessProfile` & `BusinessProfileRepository`
  * `MonthlyRecord` & `MonthlyRecordRepository`
* **Database Tables (via Flyway `V1__initial_schema.sql`):**
  * `business_profiles`: `user_id` (PK), `business_type`, `language_preference`, `whatsapp_number`, `whatsapp_opt_in`, `created_at`.
  * `monthly_records`: `id` (PK), `user_id` (FK), `month` (CHAR(7), YYYY-MM), `cash_inflow`, `cash_outflow`, `revenue`, `cogs`, `operating_expenses`, `cash_balance_eom`, `receivables_outstanding`, `payables_outstanding`, `inventory_value`, `loan_outstanding`, `interest_expense`, `financing_type`, `updated_at`, `UNIQUE(user_id, month)`.
  * `uploaded_documents`: `id` (PK), `user_id` (FK), `file_url`, `upload_timestamp`, `document_type_hint`, `processing_status`, `extracted_data`, `confirmed_data`, `linked_month`.
  * `score_results`: `id` (PK), `user_id` (FK), `month`, `composite_score`, `band`, `component_scores`, `weakest_component`, `data_completeness`, `computed_at`, `UNIQUE(user_id, month)`.
* **API Endpoints Implemented:**
  * `POST /api/profile` — Create business profile.
  * `GET /api/profile/{userId}` — Retrieve business profile.
  * `POST /api/records/monthly` — Save or update monthly record with validation.
  * `GET /api/records/monthly/{userId}` — Get all monthly records for a user.
  * `GET /api/records/monthly/{userId}/{month}` — Get single monthly record.
* **Validation Implemented:**
  * Business type constrained to `trade`, `manufacturing`, `services`, `retail`.
  * Financing type constrained to `none`, `conventional`, `islamic`.
  * Month format enforced as `YYYY-MM`.
  * Negative numbers blocked for financial fields.
  * Zero is valid; required fields cannot be null.
* **Test Suites (Passing):**
  * `BusinessProfileControllerTests.java`
  * `BusinessProfileServiceTests.java`
  * `MonthlyRecordControllerTests.java`
  * `MonthlyRecordServiceTests.java`
  * `SmeHealthBackendApplicationTests.java`
* **Error Handling:** Centralized in `GlobalExceptionHandler.java` returning structured JSON error bodies for `ResourceNotFoundException`, `DuplicateResourceException`, and validation field errors.
* **Known Backend Limitations:**
  * No scoring calculation service exists.
  * No authentication / Spring Security layer exists (endpoints accept unauthenticated `userId`).
  * No document upload endpoints exist.

---

## 6. Frontend Status

* **Next.js Setup:** Next.js 16.3.5 App Router with TypeScript 5, React 19, Turbopack, and Tailwind CSS v4.
* **Build & Lint Status:** Clean build (`npm run build` exits 0), zero ESLint errors or warnings (`npm run lint` exits 0).
* **Design Tokens & Styling:**
  * Token stylesheet: `src/styles/tokens.css`.
  * Tailwind theme mapping: `src/app/globals.css`.
* **Typography:**
  * Headings / Display: `Plus Jakarta Sans` (40px, 32px, 24px, 20px).
  * UI / Data: `Inter` (16px, 14px, 13px, 12px).
  * Urdu: `Noto Sans Arabic` (minimum 1.6x line-height).
  * Tabular numbers: `.tabular-nums` applied to financial amounts.
* **Bilingual / RTL Engine:**
  * `LanguageProvider` with persistent state in `localStorage`.
  * Synchronized `<html lang="..." dir="...">`.
  * CSS logical properties (`inset-inline-start`, `ms-`, `me-`, `ps-`, `pe-`, `border-s-`).
  * Western Arabic digits (0–9) preserved in Urdu financial values (`2,450,000 روپے`).
* **Currency Formatting:**
  * Centralized `formatPKR` in `src/lib/utils/currency.ts`.
  * Supports detail (`PKR 2,450,000`), compact (`PKR 2.45M`), and Urdu (`2,450,000 روپے`).
  * Strict prohibition of `Rs.` and `₨`.
* **Foundational UI Components (18 primitives in `src/components/ui/`):**
  * `Button`: Primary, secondary, ghost, destructive with `scale-[0.98]` active depression and loading submission lock.
  * `Input`: Visible label, optional marker, inline error with `aria-invalid` and `aria-describedby`.
  * `CurrencyInput`: `inputMode="decimal"` on mobile, formatted PKR display, non-negative blocking.
  * `Select`: Accessible custom select.
  * `Checkbox`: Accessible custom checkbox.
  * `Switch`: Toggle switch supporting directional RTL flip.
  * `IconButton`: Accessible icon button with min 48px touch target on mobile.
  * `Badge`: Category indicator pill.
  * `StatusBadge`: Triple-coded status (Color + Icon + Explicit text) for Health states (`strong`, `stable`, `attention`, `risk`) and Upload states (`pending`, `processing`, `extracted`, `needs_review`, `confirmed`, `failed`).
  * `FinancialValue`: Tabular currency display with semantic positive/negative tinting.
  * `Card`: 1px border (#E2E8F0), 8–12px radius, Level 0 and Level 1 elevation (no 24px+ roundings, no glassmorphism).
  * `Container`: Max 1280px fluid container.
  * `Section`: Typographic section container avoiding card-everything anti-pattern.
  * `Skeleton`: Geometry-matched skeletons respecting `prefers-reduced-motion`.
  * `EmptyState`: Explains what is missing, why it matters, and next action.
  * `ErrorState`: Human-readable explanation, data safety reassurance, and recovery button.
  * `AlertBanner`: Screen/persistent banner (success, warning, error, info).
  * `Dialog`: Accessible modal dialog with focus trapping and Escape listener.
  * `Sheet`: Mobile bottom sheet / drawer for quick data entry.
  * `Toast` & `ToastProvider`: Concrete confirmation alerts without celebratory confetti.
  * `Tooltip`: Accessible tooltip for plain-language secondary help.
  * `Divider`: Subtle separator.
* **Layout Shell (`src/components/layout/`):**
  * `AppShell`: Coordinates desktop sidebar, header, and mobile bottom nav.
  * `Sidebar`: Persistent desktop navigation (256px expanded, 72px collapsed, LTR left / RTL right, authoritative FinSight logo).
  * `MobileNav`: Mobile bottom navigation bar with 48px touch targets and center elevated `+` action opening a bottom sheet.
  * `PageHeader`: Business context, substring search dialog trigger, and English/Urdu toggle.
* **Data Contracts & Mock Adapter:**
  * `src/types/financial.ts`: Strictly matching backend DTOs (`MonthlyRecordRequest`, `MonthlyRecordResponse`, `ScoreResult`, `BusinessProfile`).
  * `src/lib/api/adapter.ts`: Typed mock adapter providing deterministic sample data without inventing backend contracts.

---

## 7. Current Design System

* **Authoritative Logo Mark:** Supplied FinSight logo retains its authoritative blue growth bars, blue dot, and navy wordmark.
* **UI Palette Authority:** The UI color system is intentionally **Deep Spruce Teal**, independent from the blue logo:
  * Brand Primary: `#0D5C52`
  * Brand Hover: `#0A473F`
  * Brand Active: `#07352F`
  * Brand Surface: `#F0FDFA`
  * Brand Border: `#99F6E4`
  * Brand Navy: `#030E2E`
* **Neutral Surface Dominance:**
  * Canvas: `#F8FAFC`
  * Card: `#FFFFFF`
  * Hover: `#F1F5F9`
  * Text Primary: `#0F172A`
  * Text Secondary: `#334155`
  * Muted Text: `#64748B`
  * Border Default: `#E2E8F0`
  * Border Strong: `#CBD5E1`
* **Anti-Vibe-Code Prohibitions:**
  * NO blue UI merely because the logo is blue.
  * NO UI gradients or glowing halos.
  * NO glassmorphism / frosted glass.
  * NO 24px+ rounded cards as standard dashboard shapes.
  * NO AI sparkle badges or gamified confetti.
  * NO card-everything layouts.
  * Brand teal must NEVER replace semantic health colors.

---

## 8. Feature 2 Scoring Contract

* **Weights:**
  * Cash Flow Stability: 30%
  * Profitability & Efficiency: 25%
  * Repayment & Collection: 20%
  * Trend: 15%
  * Compliance: 10%
* **Health Bands:**
  * 80–100: Strong (`#047857`, `#ECFDF5`, `#A7F3D0`)
  * 60–79.99: Stable (`#0369A1`, `#F0F9FF`, `#BAE6FD`)
  * 40–59.99: Needs Attention (`#B45309`, `#FFFBEB`, `#FDE68A`)
  * 0–39.99: At Risk (`#B91C1C`, `#FEF2F2`, `#FECACA`)
* **Scoring Rules:**
  1. **Backend is Authoritative:** The frontend displays `ScoreResult`; it does NOT calculate or duplicate scoring formulas.
  2. **Renormalization of Missing Data:** Missing optional components are omitted, and available weights are renormalized by the backend. Missing data is NOT treated as zero; it is displayed as "Pending Information".
  3. **Thin-Data State (<3 Months):** For businesses with fewer than 3 months of history, trend is not fabricated. The score is displayed as provisional with copy: *"Building your profile — accuracy improves after 3 months of data."*
  4. **Persistence & Computation Timing:** `ScoreResult` is stored per month in the `score_results` table. Recomputation is triggered after a `MonthlyRecord` is saved, never upon merely opening the dashboard.

---

## 9. Current Frontend Phase

* **Phase 1 — Frontend Foundation:** **COMPLETE & VERIFIED**
  * All tokens, typography, layout shell, primitive components, accessibility features, and bilingual infrastructure are implemented and passing automated build/lint tests.
* **Phase 2 — Onboarding Flow:** **COMPLETE & VERIFIED**
  * Multi-step wizard page (`/onboarding`) fully implemented and verified.
  * 6-step sequence: Language Selection → Business Type (`trade`, `manufacturing`, `services`, `retail`) → WhatsApp Opt-in / Setup → Data Entry Choice (Dual-path) → First Monthly Vitals (5 core inputs) → First Health Baseline Result (Thin-data notice, provisional score, insight, recommendation) → Convergence to Dashboard.
  * Dynamic RTL / LTR switching active, Noto Sans Arabic typography, Western numerals preserved in Urdu values, LTR phone input formatting.
  * Form validation: non-negative enforcement, zero is valid, required field checks, duplicate submission prevention.
  * Zero client-side score computation.
* **Phase 3 — Financial Health Dashboard Hub:** **COMPLETE & VERIFIED**
  * Canonical hub implemented at `/` (`src/app/page.tsx` rendering `DashboardPage`).
  * Strict information hierarchy (Section 8.2 & 8.3):
    1. Primary Health Anchor (`HealthScoreHero.tsx`): 56–60px composite score, `/100`, triple-coded `StatusBadge`, and segmented semi-circle `ScoreMeter.tsx`.
    2. Narrative Insight (`ScoreExplanation.tsx`): Plain-language summary and primary focus drag factor without AI buzzwords.
    3. Actionable Next Step (`NextStep.tsx`): Exactly ONE prioritized recommendation with primary action button.
    4. Profile Sharpening (`ScoreCompleteness.tsx`): 6px progress bar, active pillars counter, and missing data reassurance.
    5. Cash Flow Trend (`CashFlowTrend.tsx`): Multi-month inflow vs outflow paired SVG bars, LTR chronological axis, tooltip, and summary metric tiles (Total Inflow, Total Outflow, Net Surplus/Deficit, Ending Cash).
    6. Five Health Pillars (`HealthComponents.tsx`): Weighted rows for Cash Flow Stability (30%), Profitability (25%), Repayment (20%), Trend (15%), and Compliance (10%). Null components strictly rendered as "Pending Information" (never 0% or red).
    7. Operational History (`RecentRecords.tsx`): Responsive table/card list of verified records with explicit Surplus/Deficit indicators.
    8. Business Header (`DashboardHeader.tsx`): Restrained context with business name, business type badge, evaluated period, quick-add action, and interactive state toggle ("Preview Provisional <3 Mo" vs "Preview Mature 6 Mo").
    9. Loading & Empty Primitives (`DashboardSkeleton.tsx`, `DashboardEmptyState.tsx`).
  * Bilingual & RTL: Full Urdu translation dictionary in `ur.ts`, synchronized RTL direction, Western numerals preserved in currency and numbers.
  * Anti-Card-Everything rule respected (Section 8.4): varied density, typographic hierarchy, and restrained surfaces.
  * Build & Lint: `npm run lint` exits 0 (0 errors, 0 warnings), `npm run build` exits 0 (static prerender of `/`, `/_not-found`, `/onboarding`).

### Phase 4: Dedicated Manual Financial Data Input — COMPLETE & VERIFIED
* **Routes Implemented:**
  * `/records/new` (`frontend/src/app/records/new/page.tsx`): Full manual financial worksheet for capturing new monthly vitals.
  * `/records/[id]` (`frontend/src/app/records/[id]/page.tsx`): Edit route resolving records by ID (e.g. `rec-2026-08`) or by month (`2026-08`), with geometry-matched skeleton and not-found handling.
* **Components Created (`frontend/src/components/records/`):**
  * `MonthSelector.tsx`: Human-friendly selector displaying formatted localized dates (e.g. "August 2026" / "اگست 2026"), quick-pick chips for "This Month", "Last Month", and "2 Months Ago", with machine-readable `YYYY-MM` value.
  * `CoreVitalsSection.tsx`: Visually dominant section with the 5 mandatory vitals (`cashInflow`, `cashOutflow`, `revenue`, `operatingExpenses`, `cashBalanceEom`) using `CurrencyInput`, visible labels, plain-language helpers, and inline validation.
  * `PrecisionBoostersSection.tsx`: Visually secondary section ("Sharpen Your Financial Profile", optional badge) for `cogs`, `receivablesOutstanding`, `payablesOutstanding`, `inventoryValue`, `loanOutstanding`, `interestExpense`, and `financingType` (`none`, `conventional`, `islamic`). Preserves missing optional figures strictly as `null` (never coerced to `0`).
  * `RecordFormSkeleton.tsx`: Geometry-matched structural skeleton preventing full-page spinners during edit record loading.
  * `MonthlyRecordForm.tsx`: Unified worksheet controller (~860px max width) with back link, non-blocking outflow warning confirmation flow, double-submission prevention, and typed API adapter integration.
* **Outflow Warning Confirmation Flow:**
  * Implemented non-blocking sanity check when `cashOutflow > cashInflow * 3`.
  * Inline amber `AlertBanner` alerts user.
  * Clicking "Save Monthly Data" opens an accessible `Dialog` modal:
    - Explains unusually high spending relative to inflow.
    - Provides "Review Figures" (cancels modal and returns to form) and "Continue Saving" (confirms warning and saves to backend/adapter).
* **Backend Verification Findings:**
  * Verified backend Spring Boot contracts:
    - `POST /api/records/monthly` exists in `MonthlyRecordController.java` and accepts `MonthlyRecordRequest`.
    - `GET /api/records/monthly/{userId}` and `GET /api/records/monthly/{userId}/{month}` exist.
    - No direct `GET /by-id/{id}` exists in backend (records are keyed by `(user_id, month)`).
    - Backend `MonthlyRecordService.java` currently validates COGS with `validateNonNegative` rather than `validateOptionalNonNegative`, which makes COGS mandatory in the backend service code, conflicting with the MVP specification.
    - Backend has no rescore trigger or scoring calculation yet (`ScoreResult` and scoring engine are not implemented in Spring Boot).
  * Typed API adapter (`adapter.ts`) provides full in-memory persistence and mock compatibility for both ID and month lookups without client-side score computation.
* **Bilingual & Accessibility:**
  * Authoritative Urdu typography with `Noto Nastaliq Urdu`, generous line-height to prevent clipping, and LTR Western numerals for currency inputs.
  * WCAG 2.2 AA compliance: visible labels, aria-describedby for errors, logical tab order, non-color-only alerts.
* **Verification & Testing:**
  * `npm run lint`: Exits 0 (0 errors, 0 warnings).
  * `npm run build`: Exits 0 (Turbopack static prerender of `/records/new`, dynamic server-render of `/records/[id]`).
  * HTTP verification: Both routes return HTTP 200 OK.

---

## 10. Current Next Steps

1. **Step 1:** Implement Phase 5 — Document Ingestion & Verification UI (`/scan` and `/upload`).
   - Mobile camera capture interface and document scanner.
   - Desktop drag-and-drop document upload (PDF, JPG, PNG).
   - Extracted data review interface with confidence indicators and uncertain field highlighting.
   - Mapping extracted document items to `MonthlyRecordRequest`.
2. **Step 2:** Implement Phase 6 — Sharia / Zakat & Advanced Analytics (`/sharia-zakat` and `/reports`).
3. **Step 3:** Backend Integration — Implement Spring Boot scoring engine (`ScoreResult`), rescore trigger on record save, and fix `MonthlyRecordService` optional COGS validation.

---

## 11. Known Issues / Risks

* **Backend Optional COGS Validation Conflict:** `MonthlyRecordService.java` line 104 checks `validateNonNegative("COGS", record.getCogs())` which rejects `null` COGS, whereas MVP specification defines COGS as an optional precision booster. Backend adjustment required in backend development phase.
* **Backend Scoring Engine Missing:** The backend does not yet contain scoring computation logic, `ScoreResult` entity, or `/api/score` endpoints. The frontend cleanly consumes typed mock adapter data until the scoring service is implemented.
* **Authentication Missing:** Backend endpoints currently accept raw `userId` without JWT or session validation.
* **Untracked Frontend Directory:** The Next.js `frontend/` directory is currently untracked in Git and will be committed when Dev A / Suleman is ready.
* **AI Service Unimplemented:** `ai-service/` is empty; document OCR extraction endpoints are not yet available.
* **Playwright CDN Download 404:** Automated headless browser testing encountered an environment-level Azure Edge CDN 404 when downloading browser driver binaries. Manual HTTP smoke testing verified 200 OK rendering.

---

## 12. Developer Ownership

* **Dev A / Suleman:**
  * Frontend architecture, design system, layout, component library, UI integration.
  * Features 1 (Financial Data Input), 2 (Financial Health Scoring UI), 3 (Cash Flow Monitoring), 9 (Trend Projection).
  * Dashboard lead.
  * Git / GitHub coordination and backend work when assigned.
* **Dev B / Fatima:**
  * Features 4 (Insight Generation), 5 (Recommendations), 6 (Bilingual copy & translation review), 10 (Sharia/Zakat calculation).
  * Later: Features 11 (OCR/parser) & 12 (Camera upload).
  * Backend scoring-related work as assigned.

---

## 13. Development Rules

1. **Read AGENTS.md** before modifying code.
2. **Inspect Existing Implementation** before creating or modifying files.
3. **Zero Frontend Scoring Calculation:** Never calculate financial scores, bands, or weights in React components.
4. **Preserve Shared Backend Contracts:** Never invent or unilaterally alter API contracts.
5. **No AI-Vibe-Code:** Strictly prohibit glassmorphism, neon halos, 24px+ rounded cards, AI sparkles, and card-everything grids.
6. **Mock-First Architecture:** When a dependent backend endpoint is missing, build against the typed mock adapter in `src/lib/api/adapter.ts`.
7. **Bilingual by Design:** Ensure every new screen supports English LTR and Urdu RTL using CSS logical properties.
8. **Verify Before Declaring Done:** Always run `npm run lint` and `npm run build` after meaningful frontend changes.

---

## Resume Here

* **What was last completed:** Phase 4 (Dedicated Manual Financial Data Input) was fully implemented and verified at `/records/new` and `/records/[id]`. The worksheet layout, 5 mandatory core vitals (`CurrencyInput`), optional precision boosters preserving `null`, human-friendly `MonthSelector`, non-blocking outflow warning confirmation flow (`Dialog`), geometry-matched skeleton, and full English / Urdu (`Noto Nastaliq Urdu`) support passed both `npm run lint` and `npm run build` with zero errors.
* **What is currently being worked on:** Ready to start **Phase 5 — Document Ingestion & Verification UI** (`/scan` and `/upload`).
* **What should be done next:**
  1. Build the document scanning / upload experience:
     - `/scan`: Mobile-optimized camera capture screen.
     - `/upload`: Desktop/mobile file uploader supporting bank statements and invoices (PDF, JPG, PNG).
     - Extraction review interface highlighting uncertain fields.
* **What should NOT be touched:**
  * Do NOT modify existing backend Java classes in `backend/` unless explicitly instructed.
  * Do NOT modify `ai-service/` or database migrations.
  * Do NOT calculate financial health scores on the client side.
* **Important files to inspect first:**
  * [frontend/src/app/records/new/page.tsx](file:///c:/Users/PC/Projects/sme-financial-webapp/frontend/src/app/records/new/page.tsx) — New manual record page.
  * [frontend/src/app/records/[id]/page.tsx](file:///c:/Users/PC/Projects/sme-financial-webapp/frontend/src/app/records/[id]/page.tsx) — Edit record page.
  * [frontend/src/components/records/MonthlyRecordForm.tsx](file:///c:/Users/PC/Projects/sme-financial-webapp/frontend/src/components/records/MonthlyRecordForm.tsx) — Manual entry form controller.
  * [frontend/src/types/financial.ts](file:///c:/Users/PC/Projects/sme-financial-webapp/frontend/src/types/financial.ts) — Data contracts (`MonthlyRecordRequest`, `MonthlyRecordResponse`).
  * [PROJECT_PROGRESS.md](file:///c:/Users/PC/Projects/sme-financial-webapp/PROJECT_PROGRESS.md) — Persistent project progress.


