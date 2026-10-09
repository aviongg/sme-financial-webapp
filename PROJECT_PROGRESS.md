# FinSight — Current project progress

Updated 8 October 2026. Canonical development baseline: **`main`**.

The complete current FinSight MVP is integrated in one application tree. Future development starts from `main`; no historical branch combining is needed. The exact integration/merge and validation status is recorded in [MAIN_INTEGRATION_HANDOFF.md](docs/MAIN_INTEGRATION_HANDOFF.md). Production/provider acceptance remains separate from code integration.

## Current implementation

| Area | Implemented behavior |
| --- | --- |
| Authentication and security | Registration, login/logout, JDBC-backed sessions, CSRF, MFA enrollment/challenge/recovery, password change/reset and session invalidation, active-business authorization, encrypted sensitive fields and append-only audit |
| Businesses | Named creation, listing, active-business selection/switching, owner rename and scoped settings; business context is resolved on the server |
| Teams | Existing-account invitations, self-only acceptance/decline, membership listing, role changes, suspend/reactivate and removal; OWNER, ACCOUNTANT, MANAGER and VIEWER permissions remain server-enforced |
| Monthly records | Tenant-scoped persistence, optional-input handling and automatic score recalculation |
| Financial health | Canonical 30/25/20/15/10 components, missing-component normalization, completeness/provisional states, `health-score-v1`, persisted structured explanation and score history |
| Insights and recommendations | Deterministic evidence-linked English/Urdu rules, prioritized actions and NEW/VIEWED/DONE/DISMISSED lifecycle |
| Cash flow | Recorded inflows/outflows/net/ending cash, 3/6/12/all period UI and backend projection/confidence |
| Documents | Single/bulk upload APIs (single-file live UI), persistent metadata, async OCR states, immutable original extraction, separate reviewed draft, correction history and explicit atomic/idempotent confirmation; unconfirmed data does not affect records |
| Search | Tenant-scoped records, scores, insights, recommendations and permission-filtered document metadata; no raw OCR-text search |
| WhatsApp | Opt-in/phone settings, scheduled summaries, provider abstraction, persisted delivery states/history and duplicate protection |
| Zakat | Versioned rule profile, Nisab/Haul and asset/liability inputs, warnings and disclaimer; no formal Sharia certification claim |
| Frontend | Live authenticated onboarding, dashboard, records, score evidence/history, advice/actions, cash flow/projection, documents/review, search, team, settings, WhatsApp and Zakat; English/Urdu, RTL and responsive layouts |

Live requests use the backend and surface failures. Explicit demo mode retains sample data and earlier designs. Live mode does not silently replace unavailable financial data with mocks.

## Architecture and migration level

- `frontend/`: Next.js 16.3.8, React 19, TypeScript and Tailwind CSS.
- `backend/`: Java 21 / Spring Boot 4.1.1, Spring Security/Session, JPA and PostgreSQL.
- `ai-service/`: FastAPI authenticated document extraction with the existing Google Cloud Vision OCR provider adapter.
- `docker/` and production Compose: Nginx, separated database roles, TLS/secrets, persistent document storage and constrained OCR transport.
- `scripts/`: dependency/configuration checks and encrypted backup/staging restore.
- Database: **Flyway V17**, with all V1–V17 scripts retained. V16 adds score explanation/action state; V17 adds business names and document provenance. Current team workflows use the membership schema introduced in V9. Historical migrations are not edited by integration.

## Financial model and scope

The canonical financial model is unchanged: cash-flow stability 30%, profitability 25%, repayment/collection 20%, trend 15%, compliance 10%. Existing bands, business-type thresholds, formulas and missing-input normalization remain authoritative on the backend. The frontend displays persisted/calculated results without recomputing them.

Existing model limitations are documented rather than redesigned: missing COGS follows the current margin formula; stable negative movement can receive a high stability score; repayment/compliance declarations are self-reported. Legacy score evidence is absent until recalculation and legacy OCR provenance cannot be reconstructed.

**NO AI/LLM FINANCIAL API WAS ADDED.** No chatbot or generated financial advice is included. **NO PHASE 2 TRANSACTION/ACCOUNTING MODULE WAS ADDED.** Monthly records remain the financial model; ledger, invoices/bills, accrual/double-entry, AR/AP, inventory management and tax features are outside this integration.

## Remaining acceptance and limitations

Complete the production runbook's actual container, PostgreSQL 17 TLS/HBA/privilege, persistent volume, ingress/browser, encrypted restore, SMTP, Google Vision and WhatsApp acceptance on provisioned infrastructure. Local automated suites and synthetic provider/browser fixtures are not substitutes for these gates; see the handoff for exact passed, skipped and blocked checks.

Team invitations require an existing registered account. The normal settings UI does not provide every MFA factor-management operation, and no platform-admin audit viewer is included. Search retains its MVP in-memory ranking and twenty-result cap; the health page lists twelve recent persisted scores, with older months directly queryable. These are documented limitations, not functionality stranded on another branch.

## Branches and historical records

**ALL OLD BRANCHES WERE LEFT UNTOUCHED.** Old development branches are retained for history/reference. The integration branch carries the current refinement source plus integration/setup documentation fixes. New work starts from `main` after the integration PR merges.

The original progress document is preserved, with trailing whitespace normalized, as [the 14 September snapshot](docs/PROJECT_PROGRESS_2026-09-14.md). Its mock-first architecture, missing-backend descriptions, old branches and development instructions are historical, superseded by this document and the actual current code. No tracked `AGENTS.md` exists in the integration source.

Additional historical evidence: [frontend integration](docs/frontend-security-integration-2026-09-27.md), [security closure](docs/SECURITY_PRODUCTION_CLOSURE_2026-10-03.md), [MVP refinement](docs/MVP_CORE_REFINEMENT_HANDOFF.md), and [current branch guide](docs/CONSOLIDATED_APP_BRANCH.md).
