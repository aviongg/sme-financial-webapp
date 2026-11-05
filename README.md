# FinSight — SME Financial Health Platform

**`main` is the canonical branch for the complete current FinSight MVP.** It contains the integrated frontend, backend, security, OCR service and MVP refinements. A single checkout contains the application; no development branches need to be combined.

**Developer B Phase 2 work uses the persistent `dev/fatima` branch**, which already contains integrated main. Read the [Phase 2 master plan](docs/phase2/MASTER_IMPLEMENTATION_PLAN.md), [current implementation state](docs/phase2/IMPLEMENTATION_STATE.md) and [Codex workflow](docs/phase2/CODEX_WORKFLOW.md). The onboarding provides audit, draft contracts, ownership, detailed package designs and setup; it does not implement Phase 2 features or approve financial policies. The generic main-based instructions below describe the MVP baseline and other future work, not a request to move B's work away from dev/fatima.

FinSight provides monthly financial records, an explainable financial health score, deterministic English/Urdu insights and recommendations, cash-flow history and projection, reviewed document extraction, team access, WhatsApp delivery and a versioned Zakat preview. Live mode uses authenticated backend APIs; sample financial data is confined to explicit demo mode.

## Application architecture

| Directory | Responsibility |
| --- | --- |
| `frontend/` | Next.js 16.3.8 / React 19 / TypeScript application, English/Urdu and RTL, authenticated business workflows |
| `backend/` | Java 21 / Spring Boot 4.1.1 API, JDBC sessions, CSRF, MFA, tenant isolation, RBAC, encryption, audit, financial calculations and persistence |
| `ai-service/` | FastAPI document OCR/parsing service with a Google Cloud Vision adapter; authenticated backend transport and user-reviewed extraction |
| `backend/src/main/resources/db/migration/` | Authoritative Flyway migrations **V1–V17**; historical migrations are preserved |
| `docker/`, `docker-compose.prod.yml` | Production ingress, database role/TLS, secret, storage and OCR transport configuration |
| `scripts/` | Deployment checks, dependency auditing and encrypted backup/staging recovery tooling |
| `docs/` | Current setup, security, verification and historical handoffs |

No AI/LLM financial insights API or chatbot is included. No Phase 2 transaction ledger, accrual/double-entry accounting, AR/AP, inventory management or tax module is included. The existing OCR adapter is separate from financial advice, which remains deterministic.

## Start from main

```sh
git clone https://github.com/aviongg/sme-financial-webapp.git
cd sme-financial-webapp
git switch main
git pull --ff-only
```

Use [frontend setup](frontend/README.md) for live/demo configuration and local commands, [OCR setup](ai-service/README.md) for the extraction service, and the [production runbook](docs/PRODUCTION_RUNBOOK.md) for matched services, required secrets, database roles and migrations. Database migration runs separately from the restricted runtime application. A checkout alone does not supply production credentials or verified external providers.

For other future work, start a new branch from an up-to-date `main` when its task instructions require it. Developer B follows the dev/fatima workflow linked above:

```sh
git switch main
git pull --ff-only
git switch -c feature/<future-feature>
```

## Integration and acceptance

The [main integration handoff](docs/MAIN_INTEGRATION_HANDOFF.md) records source commits, PR/merge evidence, exact validation and remaining environment gates. [Project progress](PROJECT_PROGRESS.md) describes the current modules; the [branch guide](docs/CONSOLIDATED_APP_BRANCH.md) explains the preserved historical branches. This branch policy takes effect when the integration PR merges; before that, this is the proposed main documentation.

All old development branches remain available and were not modified or deleted by this integration. They are historical/development references, not additional sources required to run the app. Historical reports remain available and are dated; current status supersedes their older descriptions of missing features.

**Code integration is separate from production acceptance.** Fresh production container/volume bootstrap, PostgreSQL 17 TLS/HBA, deployed browser/ingress behavior, encrypted recovery and real OCR, SMTP and WhatsApp provider delivery require the environments and acceptance steps documented in the handoff.
