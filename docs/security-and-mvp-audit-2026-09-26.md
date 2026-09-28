# FinSight: security, frontend integration and MVP audit

Reviewed 26 September 2026 for Fatima and Suleman.

**Decision: use Suleman's security architecture as the integration base, but do not treat this commit as ready for real business customers.** There are reproducible authentication and restore defects, production configuration blockers, and a frontend that still uses sample data. The existing upstream report's blanket PASS is not supported by this independent review.

## Scope and evidence

| Item | Reviewed state |
| --- | --- |
| Suleman | `dev/suleman`, exact commit `f2ed9bda671404598bd73729563c6e0ceb2b08d1`, fetched from GitHub |
| Fatima | `dev/fatima`, `9bc8972`; frontend implementation `f4b738a` |
| Main | `9becac3`; unchanged by this audit |
| Method | Source and configuration review, read-only merge preview, existing automated tests, isolated regression proofs, current dependency-advisory lookups |
| Limits | No deployed production site supplied; Docker unavailable locally; full PostgreSQL security suites, actual ingress/TLS, cloud storage encryption and full browser end-to-end operation were not verified |

The source was exported to an ignored audit directory. No product source was patched, no application data was changed, and no branch was merged or published during this review. Findings distinguish confirmed behavior from deployment requirements. An audit reduces risk; it cannot establish that an application has no vulnerabilities.

## What Suleman changed, and why

Compared with the original frontend review at `4202dc1`, his branch now contains:

| Area | What changed | Purpose and current assessment |
| --- | --- | --- |
| Identity | Accounts, Argon2id password hashing, JDBC sessions, CSRF, password lifecycle, MFA, authentication stages | Replaces browser-supplied UUID identity. Keep this direction; fix SEC-01/02 before relying on its guarantees. |
| Business authorization | Active business in server session; membership and Owner/Accountant/Manager/Viewer permissions; tenant checks on resource lookups | Stops callers selecting another business merely by changing a request ID. Keep server-side checks when adapting the UI. |
| Fatima's backend features | Advice/dashboard integration, bilingual insights/recommendations, Zakat and OCR-related code are present | Code presence is confirmed. It does not mean these features are connected to the browser or have passed integrated product acceptance. |
| Records/scoring | Save failures propagate transactionally; historical edits recalculate dependent scores | Addresses the earlier stale-score/failure-swallowing findings. Do not reintroduce the older record hooks. |
| Cash flow | Projection uses actual calendar-month distances | Addresses the earlier gap-handling defect. |
| Documents | Upload, protected retrieval, processing, review, retry, deletion and idempotent confirmation | Provides the lifecycle that the earlier isolated OCR client did not provide. Production wiring still needs repair. |
| WhatsApp | Preferences, persisted delivery state, scheduling, provider abstraction and recovery | Backend workflow exists; production provider setup and frontend connection remain separate acceptance work. |
| Stored data | AES-GCM field encryption/keyring, separated database roles, audit events, encrypted database/keyring backups | Useful controls, with configuration and recovery defects below. This is not encryption of every stored field/file. |
| Deployment | HTTPS ingress, private database/AI networks, trusted-proxy handling, rate limits, container definitions | Intended deployment architecture is reasonable; the supplied production stack is not yet a reproducible working deployment. |
| Frontend security | New CSP middleware and dynamic root layout | Only two frontend files changed since `4202dc1`: 51 additions and one deletion. This does not add authentication screens or real API integration. |

Positive controls verified in source include default-deny API authentication, explicit business permissions, server-resolved tenant context, parameterized repository access in reviewed paths, guarded document access, upload type/size validation, TOTP replay claims, hashed reset tokens, and session-version checks for fully authenticated sessions. The findings below concern gaps between these controls, rather than a recommendation to replace the architecture.

## Security findings requiring fixes

### SEC-01 — High: starting MFA enrollment overwrites an already enabled factor

**Evidence:** [MfaService, lines 90–117](https://github.com/aviongg/sme-financial-webapp/blob/f2ed9bda671404598bd73729563c6e0ceb2b08d1/backend/src/main/java/com/app/sme_health_backend/mfa/service/MfaService.java#L90-L117), reached from [AuthController, line 141](https://github.com/aviongg/sme-financial-webapp/blob/f2ed9bda671404598bd73729563c6e0ceb2b08d1/backend/src/main/java/com/app/sme_health_backend/identity/controller/AuthController.java#L141).

`initiateEnrollment` finds the existing MFA row, replaces its secret and sets its status to `PENDING`. It does not first reject an `ENABLED` row or require the current password/existing factor. A fully authenticated session can call this endpoint. For an ordinary account, later login checks only whether MFA is `ENABLED`, so password-only login becomes possible after this operation.

The attacker needs an existing full victim session to perform this change; this is not an unauthenticated MFA bypass. Platform admins still enter the mandatory enrollment stage on the next login, but their existing factor can still be overwritten. This undermines protection against a stolen/unattended session and silently disables the ordinary user's enrolled factor.

**Proof:** two isolated tests reproduced the ordinary-user downgrade and the distinct platform-admin behavior using the actual controller/service with mocked persistence/password boundary.

**Fix:** reject ordinary enrollment initiation when a factor is enabled. Implement factor replacement as a separate reauthentication flow using the existing factor, preserve the active factor until replacement succeeds, revoke affected sessions and audit/notify the change. Add regression tests for both account types. This is consistent with [OWASP's factor-change guidance](https://cheatsheetseries.owasp.org/cheatsheets/Multifactor_Authentication_Cheat_Sheet.html).

### SEC-02 — High: pending MFA sessions survive credential/security-version changes

**Evidence:** [enterPreAuthStage, line 200](https://github.com/aviongg/sme-financial-webapp/blob/f2ed9bda671404598bd73729563c6e0ceb2b08d1/backend/src/main/java/com/app/sme_health_backend/identity/service/AuthenticationService.java#L200), [AccountStatusValidationFilter, line 41](https://github.com/aviongg/sme-financial-webapp/blob/f2ed9bda671404598bd73729563c6e0ceb2b08d1/backend/src/main/java/com/app/sme_health_backend/security/filter/AccountStatusValidationFilter.java#L41), [MFA completion, lines 164–202](https://github.com/aviongg/sme-financial-webapp/blob/f2ed9bda671404598bd73729563c6e0ceb2b08d1/backend/src/main/java/com/app/sme_health_backend/identity/controller/AuthController.java#L164-L202).

A new pending login stores a user ID, stage and expiry, but not the credential version accepted at password verification. The version-validation filter checks authenticated principals, while these pending sessions are anonymous. Session revocation looks up sessions by authenticated principal email. MFA completion then establishes a full session with the account's current version without rechecking whether its original password stage has been invalidated or a password change is now required.

**Scenario:** password verification starts a pending MFA session; the owner resets the password or an operator changes security state; within the pending-session lifetime, that old session can still complete using a valid second factor/recovery code. The factor remains necessary. A forced-password-change flag can also be bypassed during this promotion.

**Proof:** an isolated test exercised both filters and the recovery controller after changing `authVersion` and `mustChangePassword`; the controller returned `FULLY_AUTHENTICATED` with the password-change flag still true. Factor validation was an explicitly mocked successful boundary.

**Fix:** bind pending authentication to user ID, credential version, expected stage and creation time. Revalidate account status/version/required stage immediately before promotion. Revoke/index pending sessions too. Add PostgreSQL/session tests for resets, forced changes and concurrent security changes. See [OWASP session guidance](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html).

### SEC-03 — High operational integrity risk: restore destroys the target before authenticating the backup

**Evidence:** [restore_database.py, lines 71–122](https://github.com/aviongg/sme-financial-webapp/blob/f2ed9bda671404598bd73729563c6e0ceb2b08d1/scripts/backup/restore_database.py#L71-L122).

The tool validates the private-key format, then executes `DROP SCHEMA ... CASCADE` on an existing target before decrypting/authenticating the supplied backup. A well-formed but wrong owner key, or corrupted backup, can therefore leave the target destroyed even though decryption correctly rejects the artifact. This requires an operator invoking the privileged restore tool; it is not a public HTTP exploit.

**Proof:** a safe mocked SQL/subprocess probe recorded `DROP_SCHEMA → launch_psql → decrypt_failure`. No database was touched.

**Fix:** restore to a new disposable database, authenticate and validate the entire archive and restored state, then perform an explicit controlled cutover. Refuse existing production targets by default. Test that wrong keys, corrupt data and interrupted restores preserve the original database.

### SEC-04 — Medium: restore reports success without enforcing required recovery checks

**Evidence:** [restore validation/result, lines 141–215](https://github.com/aviongg/sme-financial-webapp/blob/f2ed9bda671404598bd73729563c6e0ceb2b08d1/scripts/backup/restore_database.py#L141-L215).

Trigger names and migration counts are collected but not asserted. Keyring recovery errors become a nested `FAILED` object, while the top-level result remains `SUCCESS`. The probe returned success with no required triggers, zero successful migrations and failed keyring recovery.

**Fix:** make expected migrations, required enabled security triggers and zero remaining ephemeral sessions/tokens explicit acceptance conditions. When a keyring is requested, require successful recovery and test decryption of representative restored ciphertext. Return a failure/partial status and nonzero exit code when these conditions are unmet.

### SEC-05 — Dependency maintenance: resolved backend packages match published advisories

OSV lookup covered **436 npm, 42 Python and 131 Maven package/version pairs**, including development/optional/test dependencies where present. It returned no matches for those npm/Python versions, and seven advisory matches across two Maven packages. This is a time-specific database result, not proof that the other dependencies are vulnerability-free.

| Resolved package | Matches | Required action |
| --- | --- | --- |
| `tomcat-embed-core:11.0.24` | `CVE-2026-65905`, `CVE-2026-65182`, `CVE-2026-68525` | Upgrade through a compatible Spring Boot/dependency update. Also review Apache's newer September fixes; stopping at 11.0.25 would miss newer advisories. |
| `bcprov-jdk18on:1.80` | `CVE-2025-14813`, `CVE-2026-0636`, `CVE-2026-8763`, `CVE-2026-13506` | Move to a maintained release covering the listed fixes; the later listed fixes require the standard 1.85 line or later. Recheck Argon2 and crypto tests. |

These are **package-version matches, not demonstrated application exploits**. The reviewed application uses Spring Security JSON/session authentication, rather than Tomcat DIGEST/FORM authentication, and its Bouncy Castle usage does not establish reachability of every affected algorithm. Record applicability explicitly; do not copy a package CVSS rating onto the application without that analysis. Apache's [current security page](https://tomcat.apache.org/security-11.html) additionally lists fixes in 11.0.26. Bouncy Castle's [maintainer release](https://github.com/bcgit/bc-java/releases/tag/r1rv85v2) and the saved advisory records support reviewing the pinned 1.80 dependency. Container OS/image scanning remains outstanding.

## Production blockers and data-protection boundaries

These findings explain why local unit tests or `docker compose config` do not establish that the deployable MVP works.

| ID | Confirmed source/configuration problem | Correction and acceptance check |
| --- | --- | --- |
| OPS-01 | The only reset notifier is an unconditional `@Component` that throws in the `prod` profile. No production implementation exists. Adding SMTP credentials alone cannot resolve it. [Source](https://github.com/aviongg/sme-financial-webapp/blob/f2ed9bda671404598bd73729563c6e0ceb2b08d1/backend/src/main/java/com/app/sme_health_backend/identity/credential/notification/InMemoryPasswordResetNotifier.java#L19-L48) | Implement a production mail adapter and profile/conditional bean selection. Keep the fail-closed behavior until it exists. Prove prod startup and delivered single-use reset links. Isolated constructor/lifecycle proof confirmed the rejection. |
| OPS-02 | Compose mounts `crypto_key_k1`, but the application key property reads `FINSIGHT_CRYPTO_KEY_K1`; the mounted property is not mapped to that configuration. [Compose](https://github.com/aviongg/sme-financial-webapp/blob/f2ed9bda671404598bd73729563c6e0ceb2b08d1/docker-compose.prod.yml#L54-L71) | Wire the config-tree secret into `finsight.crypto.keys.k1`. A config-only Spring probe with a synthetic mounted secret found `crypto_key_k1.present=true` but active key length `0`. Never work around this by disabling encryption. |
| OPS-03 | Backend Dockerfile changes to `/workspace/app/backend`, then copies source into `backend/src`, leaving Maven's expected `src/main` absent. Frontend Dockerfile copies `/app/public`, but the tracked frontend has no `public` directory. [Backend](https://github.com/aviongg/sme-financial-webapp/blob/f2ed9bda671404598bd73729563c6e0ceb2b08d1/docker/Dockerfile.backend#L3-L11), [frontend](https://github.com/aviongg/sme-financial-webapp/blob/f2ed9bda671404598bd73729563c6e0ceb2b08d1/docker/Dockerfile.frontend#L23-L26) | Correct the copy destinations/optional assets and build both images from a fresh checkout. The backend copy-path problem was confirmed by a filesystem simulation; Docker builds were not run here. |
| OPS-04 | Fresh production PostgreSQL has no supplied superuser password/secret, SSL startup settings or mounted role-provisioning bootstrap. Its HBA file is mounted inside fresh PGDATA. [Compose](https://github.com/aviongg/sme-financial-webapp/blob/f2ed9bda671404598bd73729563c6e0ceb2b08d1/docker-compose.prod.yml#L1-L25) | Provision fresh storage/roles explicitly, mount config outside the initialization directory, enable TLS with correct key ownership and test app/migrator/backup permissions. Validate a fresh-volume deploy, not an already initialized developer database. The [official Postgres image](https://hub.docker.com/_/postgres) documents initialization requirements. |
| OPS-05 | Document volume is mounted at `/storage`, but application default resolves to `/app/storage/documents`. Non-root ownership is not provisioned for that path. [Storage service](https://github.com/aviongg/sme-financial-webapp/blob/f2ed9bda671404598bd73729563c6e0ceb2b08d1/backend/src/main/java/com/app/sme_health_backend/documents/storage/LocalFileSystemStorageService.java#L24-L41) | Set the storage directory to the persistent volume and initialize ownership for the runtime UID. Verify upload, restart/recreate, retrieval and backup restore. Otherwise uploads can fail or reside outside the intended persistent mount. |
| OPS-06 | OCR integration is opt-in, but Compose does not set `OCR_INTEGRATION_ENABLED`. It sets unused `APP_OCR_URL`; prod config uses `ai-service.finsight.internal`, while Compose advertises `ai-service`/`ai.finsight.internal`. AI has only an internal network, with no specified Google Vision credential delivery or outbound access. [OCR config](https://github.com/aviongg/sme-financial-webapp/blob/f2ed9bda671404598bd73729563c6e0ceb2b08d1/backend/src/main/java/com/app/sme_health_backend/documents/ocr/OcrIntegrationConfiguration.java#L23-L60), [Compose](https://github.com/aviongg/sme-financial-webapp/blob/f2ed9bda671404598bd73729563c6e0ceb2b08d1/docker-compose.prod.yml#L54-L108) | Use the actual supported properties, one matching DNS/certificate name, enable the integration intentionally, and provide controlled provider credentials/egress. Test a real uploaded document through review and confirmation. |
| OPS-07 | Nginx uses `${APP_DOMAIN:-localhost}` in a directly copied/mounted `.conf`, with no template rendering step configured. Compose also omits the backend `APP_PUBLIC_ORIGIN` setting. [Nginx](https://github.com/aviongg/sme-financial-webapp/blob/f2ed9bda671404598bd73729563c6e0ceb2b08d1/docker/nginx/conf.d/finsight.conf#L22-L43) | Render/set the actual production hostname and align the backend origin. Run `nginx -t`, then browser tests using the real host/certificate; do not assume shell substitution occurs in Nginx config. |

An initial suspicion about `migrate,prod` profile ordering was discarded: the application main method detects migration mode and invokes a dedicated runner before Spring context startup. It is not counted as a defect.

**Data in transit:** public HTTPS, database TLS verification and authenticated HTTPS OCR are designed into the source, but the production wiring above must pass runtime checks. Nginx forwards to Next/Spring over HTTP on its internal web network. The OCR implementation uses server-authenticated TLS plus a shared service key; it does not configure client-certificate authentication, so the documentation's “mutual TLS” claim should be corrected. [Client TLS construction](https://github.com/aviongg/sme-financial-webapp/blob/f2ed9bda671404598bd73729563c6e0ceb2b08d1/backend/src/main/java/com/app/sme_health_backend/documents/ocr/OcrIntegrationConfiguration.java#L62), [AI entrypoint](https://github.com/aviongg/sme-financial-webapp/blob/f2ed9bda671404598bd73729563c6e0ceb2b08d1/docker/entrypoint-ai.sh).

**Data at rest:** selected PII and MFA secrets use application encryption. Uploaded document bytes, financial data and other database fields are not all encrypted by those converters. The project's own storage document correctly requires host/cloud volume encryption. Actual encrypted disks, protected snapshots and key access policies need deployment evidence. The backup tooling covers the database and keyring; an encrypted backup/restore process for uploaded document files is also required for full business recovery. Offsite copies, retention, recovery-time/data-loss targets and a successful restore drill are release work, not implied by source code existing.

## Frontend alignment and conflict findings

**Suleman's current frontend is still a demonstration interface.** Financial screens import `mockApi`, and monthly saves call that in-memory adapter. The HTTP client is unused. There is no completed login/MFA/reset/business-selection flow or live/demo boundary in that branch. Examples: [dashboard](https://github.com/aviongg/sme-financial-webapp/blob/f2ed9bda671404598bd73729563c6e0ceb2b08d1/frontend/src/components/dashboard/DashboardPage.tsx#L9), [record save](https://github.com/aviongg/sme-financial-webapp/blob/f2ed9bda671404598bd73729563c6e0ceb2b08d1/frontend/src/components/records/MonthlyRecordForm.tsx#L240), [mock adapter](https://github.com/aviongg/sme-financial-webapp/blob/f2ed9bda671404598bd73729563c6e0ceb2b08d1/frontend/src/lib/api/adapter.ts#L643).

Fatima's branch improves this with real profile/record operations and explicit demo mode, but its UUID-based development identity and endpoints predate Suleman's security contract. Neither branch, as currently combined by a simple file copy, provides the authenticated MVP.

| User workflow | Backend available on Suleman | Frontend work still needed |
| --- | --- | --- |
| Register/login/logout | `/api/auth/*`, cookies, CSRF and auth stages | Build screens and session bootstrap; handle required password change/MFA, expiry and logout; fix security findings first. |
| Business onboarding/switching | `/api/businesses`, `/active`, memberships/permissions | Replace profile UUID reopening; create/select an authorized business; clear all previous business data when switching. |
| Profile/language/WhatsApp | `/api/profile`, `/language`, `/whatsapp` | Map the current DTO and Owner permissions; preserve English/Urdu behavior and real opt-in. |
| Monthly records | Collection GET/POST, `/query` by month, `/id/{id}` | Remove client-supplied tenant IDs; map `businessId`; preserve zero/null, backend validation, existing-month warning and POST upsert. |
| Score/dashboard/advice | `/api/dashboard`, `/api/scores`, `/api/insights`, `/api/recommendations` | Map canonical fields, nullable scores, completeness, exact month and bilingual advice. Refresh after edits; never synthesize financial results. |
| Cash flow/components | History, projection and score components | Connect Fatima's expandable chart/table and period filter to real values, preserving calendar gaps and missing-data states. |
| Search | Tenant-scoped POST `/api/search` | Connect query/results, permissions, loading/empty/error states. |
| Zakat | Manual and monthly preview endpoints | Collect required declarations/price inputs; show incomplete/unsupported outcomes; remove the mock fixed-threshold assumption. |
| Upload/OCR/review | Document lifecycle APIs | Send multipart with CSRF, poll real status, retrieve protected files and review before idempotent confirmation. Repair OPS-05/06. |
| WhatsApp delivery | Preferences, scheduling and delivery history | Configure/test the real provider, consent and failure visibility; do not present mock delivery as sent. |
| Team roles/admin | Backend role and platform foundations | Provide only the management flows the MVP actually supports; respect server-returned permissions. Do not infer tenant access from platform-admin status. |

The read-only three-way merge preview found **23 frontend files with conflict markers**. Their common Git ancestor (`9becac3`) has only a frontend placeholder: the UI was added independently on the two branches. This creates add/add conflicts even where both versions came from the same earlier UI. A successful textual resolution alone would not resolve the API incompatibility.

Suleman's two later security files are `src/middleware.ts` and `src/app/layout.tsx`. Preserve their nonce-aware/dynamic-rendering intent. His branch does not contain Fatima's five requested changes: full WhatsApp-card click target, no business-selection tick, expandable cash-flow details/filter, record-button placement, and health-weight placement.

**CSP integration checks:** the policy permits same-origin connections, but the unused client defaults to `http://localhost:8080/api`. Connecting it as-is on production would conflict with that policy and HTTPS deployment. Use same-origin `/api`, CSRF handling, and cookie sessions. Middleware accepts an incoming `x-nonce`; the supplied Nginx overwrites it, so no public nonce-bypass exploit is claimed. Preserve that trust boundary or generate a fresh nonce when the sender is not the trusted ingress. Verify nonce consistency, navigation, Urdu fonts, upload previews and browser console errors through the actual production proxy. See [Next.js CSP guidance](https://nextjs.org/docs/app/guides/content-security-policy).

## Recommended integration workflow and ownership

1. **Suleman: stabilize security and deployment.** Fix SEC-01–04; triage/update dependencies; repair production reset delivery, secrets, images, database bootstrap and persistent storage. Add regression tests demonstrating the fixes.
2. **Joint: freeze the working contract.** Review the exact backend commit, API examples, response DTOs, auth-stage transitions and role matrix. Run security/integration suites against a disposable database. Establish a fresh production-like startup procedure.
3. **Fatima: build on that reviewed base.** Create an integration branch from Suleman's reviewed commit. Port visual/UI changes selectively; preserve his security middleware and backend. Rework Fatima's existing API layer for cookie-session/CSRF/active-business identity. Do not copy the UUID selector or blindly merge the whole older development branch.
4. **Fatima: implement vertical user journeys.** Auth/business → records → dashboard/advice/cash flow → remaining agreed MVP features. At each stage, prove save/reload and permission/error behavior against the actual backend. Keep unfinished screens hidden or explicitly labeled demo.
5. **Joint: release acceptance.** Run the scenarios below through HTTPS ingress, review results together, and only then prepare a reviewed MVP merge. The earlier frontend-only draft PR should be reconciled with this newer base before merge.

The old overlay harness was built for an earlier dashboard. Suleman now has the integrated implementation: revise verification around that actual implementation rather than bypassing its hash guard or copying the old dashboard over it.

## What must be true before a real-business MVP

| Gate | Concrete acceptance |
| --- | --- |
| Security regressions | Enabled MFA cannot be silently replaced; pending authentication cannot survive credential revocation; owner/viewer and cross-business denial scenarios pass. |
| One deployable product | Fresh images and empty volumes start using documented secret/cert provisioning; migration uses its own role; only intended public ports are reachable. |
| Real user journey | Register/login → MFA if required → create/select business → enter records → reload → see matching score/advice; switch business and logout with no stale data. |
| Real data features | Every advertised MVP screen uses the backend. Explicitly postpone or hide OCR/WhatsApp/Zakat if they are not accepted end to end. |
| Durable and recoverable data | Correct persistent file volume, encrypted host storage, database + file + keyring backups, offsite retention, and a restore drill that preserves existing data on failure. |
| Operations | Working reset email, provider credentials/quotas, certificate renewal, health/readiness checks, alerting for backup/login/storage failures, and an incident/recovery owner. |
| Release quality | CI for backend/AI/frontend and security regressions; disposable database tests; dependency/image/secret scanning; production-browser CSP and accessibility/mobile/Urdu checks. |
| Business readiness | Agreed scope, consent/data-retention/deletion behavior, financial-calculation acceptance examples and customer support/reset procedures. Record any deferred features honestly. |

## Checks actually performed in this audit

| Check | Result and limitation |
| --- | --- |
| Selected backend tests | 615 executed: **613 passed, 1 skipped, 1 failed**. The failure was `OneShotMigrationExecutionTest` attempting its hard-coded local migrator database, which refused the connection. This is an environment-dependent test failure, not proof of a production migration defect. Full PostgreSQL suites were not run. |
| Additional auth regression proofs | **4 passed**, meaning they reproduced the two auth defects, the distinct admin behavior and production-notifier rejection. Mocked boundaries were explicit; these are not deployed HTTP penetration tests. |
| OCR service | **248 passed** using a disposable environment installed from the snapshot's exact pinned requirements. Tests prohibit real provider/network calls. |
| Restore probes | Confirmed destructive-before-decryption order and false-success result with mocked SQL/processes. No real restore was executed. |
| Config/copy probes | Confirmed unmapped crypto secret and backend Docker source-copy path. Production containers were not executed. |
| Dependency lookup | 609 package/version pairs queried; two Maven packages matched seven OSV advisories; additional current Apache notices reviewed. No container image scan. |
| Frontend comparison | Actual source/route/mock usage reviewed; 23 conflict-bearing frontend files in a read-only merge preview. No fresh complete browser/CSP or production frontend build pass claimed. |

The earlier upstream report's 642/86/backend and browser/restore claims were read as historical claims, not counted as tests rerun here. Its “mutual TLS”, production reset-provider readiness and blanket security PASS wording should be corrected after these findings are resolved.

Local evidence is under `backend/target/security-audit-f2ed9bd/` (source snapshot, Maven/AI logs, dependency results and merge preview), `backend/target/security-proof-f2ed9bd/` (four Java regression proofs), and `backend/target/security-audit-proofs-f2ed9bd/` (safe restore, copy and config probes). These are ignored scratch artifacts; retain the report and copy relevant regression tests into the normal test suites when implementing fixes.
