# FinSight frontend

This is the live Next.js frontend for the complete FinSight MVP on `main`, integrated with the Spring backend, authenticated business context and current MVP refinements. Future work should branch from `main`; no historical branch combining is required. Live mode uses authenticated sessions, never a browser-supplied profile UUID.

The earlier UI and security integration history remains documented in the [consolidation record](../docs/CONSOLIDATED_APP_BRANCH.md). Historical development branches remain available and were left untouched by final integration. See the [main integration handoff](../docs/MAIN_INTEGRATION_HANDOFF.md) for exact source revisions, merge status and current validation evidence.

## Run locally

Use Node 20.9+ and the backend's documented PostgreSQL setup. Do not reuse production data for acceptance tests.

```powershell
cd frontend
npm ci
Copy-Item .env.example .env.local
npm run dev
```

Alternatively use `pnpm install --frozen-lockfile` and `pnpm dev`. The pnpm workspace explicitly disables the optional `unrs-resolver` install script; the locked platform package supplies the resolver. Dependency versions are unchanged by final integration.

Open http://localhost:3000. Register, then sign in. Complete any required password change or MFA step, and create/select a business. Existing users sign in with their account; a profile UUID cannot open a session.

`BACKEND_API_URL` is the server-only Spring origin (default `http://localhost:8080`, without `/api`). Browser calls remain same-origin at `/api/*`. Set the target before building because Next rewrites are baked into the build. Production Nginx routes `/api/` directly to Spring.

`NEXT_PUBLIC_DATA_MODE=live` is the default. `demo` is an explicit build-time preview with a visible sample-data banner; live failures never fall back to sample data. Do not distribute a demo build as the live product. Google Fonts access is needed for the existing `next/font/google` build.

## Integrated behavior

- Cookie sessions, CSRF bootstrap, registration/sign-in, forced password change, MFA challenge/recovery/mandatory enrollment, reset-link confirmation and sign-out.
- Business creation/selection and role-aware navigation/actions. Business transitions discard private data and cancel old requests across tabs. Authorization is always enforced by the backend too.
- Monthly record list/read/upsert; existing-month review and locked edit month; zero and unknown values remain distinct. No caller-supplied user/business identity in finance payloads.
- Dashboard and historical health scores/advice use canonical backend fields and nullable values. Cash-flow disclosure preserves real calendar gaps and shows exact records; projection comes from the backend.
- Score history, methodology version, structured component evidence and recommendation actions (`NEW`, `VIEWED`, `DONE`, `DISMISSED`) use persisted backend values. Score formulas, weights and bands remain server-owned and unchanged.
- Search maps supported result types to fixed local destinations. Settings covers language, WhatsApp consent/phone, delivery history and password changes.
- Documents support upload/list/status, protected original retrieval, draft correction, review, explicit one-time confirmation, retry and draft deletion. Extraction never automatically adds financial data.
- Team settings supports existing-account invitations, acceptance/decline, role changes, suspension/reactivation and removal. Business names are authoritative and owner-editable; membership and financial permissions remain server-enforced.
- Zakat uses backend manual/monthly previews and explicit assessment inputs, including price provenance, haul, inventory, receivables and identified liabilities. Missing information stays unknown; no fixed market price or invented assessment is supplied.

The English/Urdu interface and RTL layout include typed shared copy for authentication, onboarding, score evidence, search, documents, team, settings and Zakat. Financial numerals remain Western digits. Desktop/mobile and Urdu browser checks are included below.

## Security and production configuration

API calls use same-origin cookies, no-store requests and CSRF headers. No authentication token or profile identity is stored in localStorage. Unsafe operations are not automatically retried. Expired sessions and unresolved business context close private views; responses from an old context are discarded.

Production CSP uses a nonce and omits `unsafe-eval`. By default Next generates its own nonce and ignores caller `x-nonce`. Only the private production Compose service enables `TRUST_INGRESS_NONCE=true`, because Nginx overwrites the header and supplies the matching edge CSP. Do not enable this on a directly accessible Next server.

The frontend container uses Next standalone output, runs `node server.js` as UID 10001, and includes static/public assets. Run the matched backend with Flyway V1–V17. Container configuration is source-reviewed; a complete production Docker/TLS deployment and provider acceptance remain separate gates in the [production runbook](../docs/PRODUCTION_RUNBOOK.md) and [security closure report](../docs/SECURITY_PRODUCTION_CLOSURE_2026-10-03.md).

## Verification

```powershell
pnpm test
pnpm exec tsc --noEmit
pnpm run lint
pnpm run build
```

`tests/phase-one.test.mjs` tests the actual API modules: cookie/CSRF requests, explicit payloads, errors, cancellation/races, roles, document confirmation and Zakat contracts.

`tests/browser.integration.mjs` and `tests/browser-extra.integration.mjs` provide separate browser checks against a synthetic HTTP contract fixture, not Spring/PostgreSQL. They require a provisioned Playwright module and installed Microsoft Edge. Start the built Next server on port 3100 with its API rewrite pointing at `http://127.0.0.1:8080`, then run each script sequentially from `frontend`. Each binds its fixture to port 8080; both ports must be free of other application services. Set `PLAYWRIGHT_MODULE` to an absolute module path if Playwright is not installed locally. `BROWSER_ARTIFACT_DIR` can override the ignored `test-artifacts/browser` output directory. Test accounts use reserved `.test` addresses and synthetic data.

Final integration checks on 8 October 2026: **36 API contract tests**, **22 primary browser assertions** and **10 additional browser assertions** passed. TypeScript, ESLint and the production build passed. ESLint retains one existing SessionProvider effect-cleanup ref warning; Next reports its existing middleware-convention deprecation warning. No browser exceptions, CSP violations or hydration errors were reported. The live-component import traversal covered 57 local modules with no `mockApi` references; shared record routes select demo behavior only through the explicit build-time mode.

See the [main integration handoff](../docs/MAIN_INTEGRATION_HANDOFF.md) for actual Spring/PostgreSQL journey results and post-merge verification. Browser fixture checks alone do not establish deployed-stack or external-provider acceptance. The [earlier frontend handoff](../docs/frontend-security-integration-2026-09-27.md) is retained as historical evidence. No AI/LLM financial API or Phase 2 transaction/accounting module is included.
