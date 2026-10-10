# Feature-specific prompt format

No F01–F13 implementation prompt is issued by this onboarding. When the user asks for one, fetch and inspect the current dev/fatima source and state, then save one current `Fxx_<name>.md` here. A prompt must be copy-ready and self-contained, tied to the inspected commit, and state which details must be refreshed before execution. Do not use stale migration numbers or request rebuilding already implemented slices.

Use this structure, filling concrete feature facts instead of leaving placeholders in the delivered prompt:

1. **Assignment:** exact feature/slice, business outcome and real final user journey; owner B and A review boundaries. For A-owned requests, explicitly identify ownership and obtain any necessary reassignment rather than silently treating it as B work.
2. **Repository:** aviongg/sme-financial-webapp, persistent dev/fatima, current inspected remote SHA/date, fetch/status/divergence rules, preserved user work, no main PR/force push.
3. **Required reading:** AGENTS, current state/register/workflow, relevant package sections, Cxx versions, Pxx approved decisions, security/source audit and existing implementation references.
4. **Readiness:** exact implemented producer services/commits, frozen schemas/approval evidence, missing dependencies and independent allowed work; state a required approval/dependency stop before any blocked financial consequence.
5. **Existing code:** verified concrete reusable controllers/services/DTOs/entities/frontend paths and partial implementation; explain what to extend and preserve.
6. **Backend/schema:** exact proposed/required classes, relations/indexes/constraints/grants, transaction/locking/idempotency/audit, migration reservation procedure and fresh/upgrade fixtures.
7. **API:** methods/paths, typed request/response examples, money/IDs/nullability, status transitions, error codes, pagination/compatibility; derive tenant from session.
8. **Frontend:** actual routes/components/forms/navigation, permissions/modules, no-data/error/loading/partial states, EN/UR/RTL/mobile and accessibility.
9. **Financial rules:** authoritative owner per metric, approved policy versions, equations with expected numeric examples, rounding/currency, reversal/source/dedup, actual-versus-plan and month source mode.
10. **Security/integration:** role and row scope, cross-tenant references, producers/consumers, source evidence, CSRF, business switch/revocation, storage/privacy if relevant.
11. **Verification:** named unit/contract/security/PostgreSQL race/migration tests, current build/type/lint, meaningful browser test and exact manual actual-backend journeys; current known baseline failures and separately pending production/provider gates.
12. **DoD/handoff:** complete vertical slice requirements, reviewer signoff, state/register/contracts updates, exact files/commits/test evidence/limitations, explicit staged diff review, fetch/commit/push and remote SHA verification.

Prompt acceptance check: a new chat can execute without this conversation; no unapproved financial assumptions, fake producer services, ignored existing code or future migration number presumed free. If prerequisites changed since prompt generation, the new chat revalidates and revises scope before editing.
