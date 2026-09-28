# FinSight frontend

This frontend integrates Fatima's UI work with Suleman's authenticated backend at `f2ed9bda671404598bd73729563c6e0ceb2b08d1`. Work is isolated on `codex/fatima-secure-frontend`; main is unchanged. The former browser-supplied profile UUID is no longer used in live mode.

All 100 frontend files from `dev/fatima` are accounted for here, with 20 additional files. See the [consolidation check](../docs/CONSOLIDATED_APP_BRANCH.md) for the source inventory and remaining product gaps.

## Run locally

Use Node 20.9+ and the backend's documented PostgreSQL setup. Do not reuse production data for acceptance tests.

```powershell
cd frontend
npm ci
Copy-Item .env.example .env.local
npm run dev
```

Alternatively use `pnpm install --frozen-lockfile` and `pnpm dev`. Dependency versions are unchanged; the imported pnpm workspace disables the optional resolver install script.

Open http://localhost:3000. Register, then sign in. Complete any required password change or MFA step, and create/select a business. Existing users sign in with their account; a profile UUID cannot open a session.

`BACKEND_API_URL` is the server-only Spring origin (default `http://localhost:8080`, without `/api`). Browser calls remain same-origin at `/api/*`. Set the target before building because Next rewrites are baked into the build. Production Nginx routes `/api/` directly to Spring.

`NEXT_PUBLIC_DATA_MODE=live` is the default. `demo` is an explicit build-time preview with a visible sample-data banner; live failures never fall back to sample data. Do not distribute a demo build as the live product. Google Fonts access is needed for the existing `next/font/google` build.

## Integrated behavior

- Cookie sessions, CSRF bootstrap, registration/sign-in, forced password change, MFA challenge/recovery/mandatory enrollment, reset-link confirmation and sign-out.
- Business creation/selection and role-aware navigation/actions. Business transitions discard private data and cancel old requests across tabs. Authorization is always enforced by the backend too.
- Monthly record list/read/upsert; existing-month review and locked edit month; zero and unknown values remain distinct. No caller-supplied user/business identity in finance payloads.
- Dashboard and historical health scores/advice use canonical backend fields and nullable values. Cash-flow disclosure preserves real calendar gaps and shows exact records; projection comes from the backend.
- Search maps supported result types to fixed local destinations. Settings covers language, WhatsApp consent/phone, delivery history and password changes.
- Documents support upload/list/status, protected original retrieval, draft correction, review, explicit one-time confirmation, retry and draft deletion. Extraction never automatically adds financial data.
- Zakat uses backend manual/monthly previews and explicit assessment inputs, including price provenance, haul, inventory, receivables and identified liabilities. Missing information stays unknown; no fixed market price or invented assessment is supplied.

The existing English/Urdu interface, RTL layout and all five teammate UX adjustments are preserved. Some newly added security/document/assessment copy still needs Urdu translation before bilingual release acceptance.

## Security and production configuration

API calls use same-origin cookies, no-store requests and CSRF headers. No authentication token or profile identity is stored in localStorage. Unsafe operations are not automatically retried. Expired sessions and unresolved business context close private views; responses from an old context are discarded.

Production CSP uses a nonce and omits `unsafe-eval`. By default Next generates its own nonce and ignores caller `x-nonce`. Only the private production Compose service enables `TRUST_INGRESS_NONCE=true`, because Nginx overwrites the header and supplies the matching edge CSP. Do not enable this on a directly accessible Next server.

The frontend container uses Next standalone output, runs `node server.js` as UID 10001, and includes static/public assets. Container configuration is source-reviewed; a complete Docker/TLS deployment has not been run locally. Other deployment defects listed in the audit remain open.

## Verification

```powershell
npm test
npx tsc --noEmit --incremental false
npm run lint -- src --quiet
npm run build
```

`tests/phase-one.test.mjs` tests the actual API modules: cookie/CSRF requests, explicit payloads, errors, cancellation/races, roles, document confirmation and Zakat contracts.

`tests/browser.integration.mjs` and `tests/browser-extra.integration.mjs` provide separate browser checks against a synthetic HTTP contract fixture, not Spring/PostgreSQL. They require a provisioned Playwright module and installed Microsoft Edge. Start the built Next server on port 3100 with its API rewrite pointing at `http://127.0.0.1:8080`, then run each script sequentially from `frontend`. Each binds its fixture to port 8080; both ports must be free of other application services. Set `PLAYWRIGHT_MODULE` to an absolute module path if Playwright is not installed locally. `BROWSER_ARTIFACT_DIR` can override the ignored `test-artifacts/browser` output directory. Test accounts use reserved `.test` addresses and synthetic data.

Final checks on 27 September 2026: 26 API contract tests and 29 browser assertions passed; TypeScript, ESLint and the production build passed. A separate standalone runtime check confirmed the trusted edge nonce matches CSP and rendered script tags. No browser exceptions, CSP violations or hydration errors were reported by the two fixture suites. The build reports the upstream Next middleware-convention deprecation warning.

See [the current integration handoff](../docs/frontend-security-integration-2026-09-27.md) for validation scope, remaining backend blockers and team acceptance steps. Passing frontend checks does not close the authentication/recovery/deployment findings from the 26 September audit.
