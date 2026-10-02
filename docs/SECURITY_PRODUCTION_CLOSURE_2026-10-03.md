# FinSight security and production closure — 3 October 2026

Implementation and local verification are complete for the changes below. **Production acceptance is not complete.** This workstation has no Docker engine; container builds, fresh production-volume bootstrap, the real PostgreSQL recovery drill, Nginx runtime checks, and container restart/storage checks remain **BLOCKED**. Real SMTP delivery and Google Vision require credentials/services and remain **EXTERNAL-ACCEPTANCE-PENDING**. These are distinct from passing local fixtures.

Repository: `aviongg/sme-financial-webapp`. Branch: `codex/security-production-closure`. Base: `codex/fatima-secure-frontend`, verified at `59c92ed4a86dc6a40652908430e3ea9b479ba208` before work and again before the first push. Work used an isolated clone; the existing developer checkout and historical audits were preserved. No financial scoring, advice, cash-flow, Zakat, tenant permission, or document confirmation rules were changed.

## Issue disposition

| Issue | Status and implementation | Evidence / remaining gate |
| --- | --- | --- |
| SEC-01 | **CLOSED.** Parent-user and factor locks serialize enrollment, including first enrollment when no factor row exists. An ENABLED factor returns a stable 409, records a rejection audit event, and retains ciphertext/status/recovery state. Restarting PENDING enrollment refreshes its TTL. | Unit and real PostgreSQL enrollment tests, including concurrent requests, pass. |
| SEC-02 | **CLOSED.** Password acceptance binds the pending session to authVersion and the Spring Session principal index. One validator checks identity, TTL, active account, version and authoritative stage; challenge/recovery validate before factor verification and immediately before promotion. | JDBC pending-session lookup/revocation, real password reset, stale state, expiry, valid TOTP, fixation and existing authentication regression tests pass. |
| SEC-03 | **IMPLEMENTED; real recovery acceptance BLOCKED.** Restore creates only a fresh validated staging name, refuses existing names, requires explicit credentials, streams into that new DB, and cleans up only the DB created by that invocation. There is no automatic cutover. | Real age encryption with a simulated PostgreSQL/process boundary passes failure/cleanup tests. The supplied real PostgreSQL drill has not run here. |
| SEC-04 | **IMPLEMENTED; real recovery acceptance BLOCKED.** Exact current Flyway versions/scripts/checksums, domain tables, correctly attached/enabled security triggers, and zero ephemeral-state counts are mandatory. Requested keyring failures fail the entire restore. Historical IDs and representative Java-format AES-GCM ciphertext/AAD are authenticated. | 44 recovery tests pass; no fixture result is represented as real database recovery. Success is `VERIFIED_STAGING_RESTORE`, only after all requested gates. |
| SEC-05 | **CLOSED for the recorded package-version advisory scope.** Bouncy Castle 1.86; Tomcat 11.0.26; aligned Jackson BOM 3.1.7; Next/eslint-config-next 16.3.8; patched brace-expansion lockfile overrides. | OSV: 619 exact package versions queried, no matches/errors on 2 October. npm audit: zero reported vulnerabilities. Final backend and frontend regressions pass within the stated environment limits. This is not a claim about container images or all possible vulnerabilities. |
| OPS-01 | **EXTERNAL-ACCEPTANCE-PENDING.** Production selects SMTP; in-memory notification is limited to non-production. Mandatory SMTP/auth/STARTTLS/hostname verification/from/public HTTPS URL settings fail closed. Reset tokens stay in URL fragments; transport failure logs contain no recipient, token or message. | 12 notifier configuration/transport tests pass, plus reset lifecycle tests. Actual SMTP delivery/domain acceptance remains pending. |
| OPS-02 | **CLOSED.** Required ConfigTree secret `crypto_key_k1` directly supplies the production keyring; historical key support is retained. | Five actual production ConfigTree tests pass: valid round trip, missing/blank/malformed/wrong-size key rejection. Test-scope Maven keys do not rescue a missing production secret. |
| OPS-03 | **IMPLEMENTED; image-build acceptance BLOCKED.** Backend source copies to `./src` under the Maven workdir. Frontend Dockerfile is unchanged; `frontend/public/.gitkeep` exists. Docker context excludes secrets and generated dependencies. | Maven packages the backend and Next builds the frontend locally. No Docker image build is claimed. |
| OPS-04 | **IMPLEMENTED; fresh production-stack acceptance BLOCKED.** PostgreSQL uses DBA `_FILE` initialization, idempotent SCRAM role setup, restricted runtime/backup grants, external HBA path, TLS key ownership, and TCP health probing after bootstrap. V15 removes all runtime/PUBLIC access to Flyway history. Migration/operator credential fallbacks are removed. | All 85 local PostgreSQL ITs pass, including five actual-role privilege tests and V1–V15 migrations. Production PostgreSQL 17 container/HBA/TLS/pg_dump acceptance still requires Docker. |
| OPS-05 | **IMPLEMENTED; volume/restart acceptance BLOCKED.** Production stores documents at `/storage/documents`; one-shot initialization assigns UID/GID 10001 before backend startup. Backend remains non-root. | Existing upload/storage/tenant tests pass; one pre-existing symlink test is skipped because Windows cannot create its symlink. Named-volume persistence after container recreation is unverified. |
| OPS-06 | **LOCAL HTTPS CONTRACT PASSED; Docker acceptance BLOCKED; Google EXTERNAL-ACCEPTANCE-PENDING.** Canonical `ai.finsight.internal`, enabled Java client, authoritative shared secret file, CA validation, provider credential secret, and AI-only access to a restricted CONNECT proxy are wired. | Four real Java→uvicorn/FastAPI HTTPS checks pass; only OCR provider output is a fixture. Docker DNS/certificates/proxy egress and real Google Vision invoice processing remain pending. |
| OPS-07 | **IMPLEMENTED; rendered-runtime acceptance BLOCKED.** Nginx uses its runtime template hook with substitution restricted to APP_DOMAIN, validates the domain and runs nginx -t at startup. Compose derives HTTPS public/reset origin consistently and retains the ingress nonce boundary. | 15 shell/configuration tests and actual Compose config validation pass. Runtime nginx -t, rendered site, browser cookies/CSP/redirect/CORS checks still require the production stack. |

The V15 privilege correction came from an actual failed test: the runtime role initially inherited DML/SELECT on Flyway history through migration defaults. The fix is a new migration for both existing and fresh installations. Historical migrations were not rewritten. Re-running bootstrap does not regrant prohibited audit-table DML.

## Verification recorded on 2 October 2026

The workstation used Maven 3.9.16, JDK 26 compiling Java release 21, and real disposable PostgreSQL 14.22. Production images target Java 21 and PostgreSQL 17 and require their own acceptance. The Maven executable was invoked directly because the Windows wrapper environment could not resolve its runtime; commands below use the equivalent repository wrapper for portability.

| Check | Command / result |
| --- | --- |
| Backend + local PostgreSQL | From `backend`: `./mvnw -B -Ppostgres-it '-Dit.test=*IT,!DatabaseRolePrivilegesPostgreSqlIT,!PostgreSqlTlsConnectionIT' verify`. **BUILD SUCCESS**: Surefire 703 tests, 0 failures, 0 errors, 1 existing OS-dependent skip (702 passed); Failsafe 85 tests, all passed. Backend executable JAR packaged. |
| Production DB acceptance | The unrestricted `-Ppostgres-it verify` attempt did not pass: the two production-target suites reported 19 errors because the explicitly required disposable stack/secrets/CA were unavailable. A separate real Flyway-history privilege failure from that attempt was fixed by V15 and passes in the final local run. Those 19 cases are not counted as passed or silently disabled. |
| Real HTTPS OCR | From `backend`, set `FINSIGHT_OCR_CONTRACT_PYTHON` to Python with AI requirements plus cryptography; `./mvnw -Dtest=OcrIntegrationConfigurationTests,OcrFastApiClosureProbe test`. 11 configuration + 4 real cross-language HTTPS tests passed. See [probe instructions](../ai-service/tests/HTTPS_CONTRACT.md). |
| Frontend | From `frontend`: `pnpm run build`, `pnpm exec tsc --noEmit`, `pnpm run lint`, `pnpm test`: all exit 0; 26 tests passed. Lint retains two existing warnings (effect-cleanup ref and unused test variable), no errors. |
| AI service | From `ai-service`: `python -m pytest -q -p no:cacheprovider --basetemp=<new-disposable-directory>`: 248 passed, two upstream deprecation warnings. A first rerun hit a cross-user Windows temp-directory permission error; the final fresh-temp run passed without disabling tests. |
| Recovery | `python -m unittest discover -s scripts/backup -p 'test_*.py' -v`: 44 passed. Actual age/AES-GCM, simulated PostgreSQL boundary. |
| Production configuration | `python -m unittest discover -s scripts -p test_production_configuration.py -v`: 15 passed. Git Bash supplied the shell on Windows. |
| Compose | Standalone Docker Compose 5.5.1: `docker compose -f docker-compose.prod.yml --profile migration config --quiet` with required synthetic environment settings: exit 0. Missing APP_DOMAIN: exit 1. This does not build/start containers. Docker's local engine pipe was absent. |
| Dependency lookup | `python scripts/audit_dependencies.py`: exit 0; 136 Maven + 42 PyPI + 441 npm package versions, 0 advisory matches, 0 lookup errors. `npm audit --json`: exit 0, 0 vulnerabilities. See [recorded OSV result](closure-evidence/dependency-audit-2026-10-02.json) and [npm result](closure-evidence/npm-audit-2026-10-02.json). |
| Source secrets / whitespace | Gitleaks 8.30.1 redacted directory scan over 547 current tracked/new non-ignored source files: exit 0, no findings. Generated dependencies, runtime secrets and ignored artifacts excluded. This was not a Git-history scan. `git diff --check`: passed. |

`CoreJourneyClosureIT` uses actual HTTP requests, cookies and CSRF against a real random-port Spring server and PostgreSQL under `finsight_app`. It verifies registration/login, business creation, saved monthly record, score/advice/dashboard persistence, logout/login persistence, switching, cross-business denial, and Viewer/Manager write restrictions. No application service is mocked in that journey. It is API-level evidence; a browser run through the deployed frontend/Nginx remains outstanding. Existing document review/idempotency/tenant tests remain in the regression suite.

The one skipped test is `LocalFileSystemStorageServiceTests.symlinkEscapeOutsideStorageRootIsPrevented`, which already assumes OS permission to create a symbolic link. It must run on a host with that capability. No new test disable annotation was added.

## Dependencies and upstream references

The initial and final resolved trees are retained as [before](closure-evidence/dependencies-before.txt) and [after](closure-evidence/dependencies-final.txt). Boot 4.1.1 was retained after checking available compatible patches. Its BOM supplied Tomcat 11.0.24; the coordinated `tomcat.version` override supplies 11.0.26 until the Boot BOM catches up. The Jackson override likewise keeps modules aligned at 3.1.7. Bouncy Castle was previously explicitly pinned to 1.80.

Additional advisories appeared during the multi-day work. The final patch covers the maintained Jackson 3.1 fixes documented in the upstream [parser error-bound advisory](https://github.com/FasterXML/jackson-core/security/advisories/GHSA-7hhh-6rmp-j9qf), [number coercion advisory](https://github.com/FasterXML/jackson-core/security/advisories/GHSA-p6pp-m3f8-5c89), [forward-reference advisory](https://github.com/FasterXML/jackson-databind/security/advisories/GHSA-cxp5-3px4-pw24), and [type-ID cache advisory](https://github.com/FasterXML/jackson-databind/security/advisories/GHSA-wv8q-qhhj-9h54). Next was updated following its [upstream security advisory](https://github.com/vercel/next.js/security/advisories/GHSA-vcvr-r3jv-pc5j). These are package-version matches; no claim of confirmed application exploitability is made.

## Handoff and outstanding acceptance

Use [the production runbook](PRODUCTION_RUNBOOK.md) to supply ignored secrets/certificates and APP_DOMAIN/SMTP settings, build all images from the repository root, initialize a genuinely new disposable project volume, run the migration container through V15, then start runtime services. Never delete an existing real-data volume to test bootstrap. The runbook includes exact startup and verification commands.

Before production approval, record all of the following on a Docker-capable host:

1. Clean backend/frontend/AI/Nginx/PostgreSQL/proxy image builds and fresh-volume migrations; real app/migrator/backup privileges and TLS/HBA rejection/acceptance. The production test classes require explicit `FINSIGHT_TEST_DB_URL`, `FINSIGHT_TEST_APP_PASSWORD`, `FINSIGHT_TEST_DB_CA`, role credentials/container identity, and wrong-host TLS fixture as documented by their setting errors.
2. [Real encrypted PostgreSQL recovery drill](../scripts/backup/README.md), proving live/original fingerprints remain unchanged for each rejected archive/key/query/validation case and successful staged recovery authenticates representative ciphertext.
3. Upload/retrieve/recreate backend/retrieve again against the persistent document volume; run the symlink-escape test where OS privileges allow it.
4. Nginx rendered domain and nginx -t; browser redirect, secure cookies, CSP nonce, hostile origin rejection; the full frontend live-data journey.
5. Canonical OCR DNS, production CA and proxy connectivity, then controlled real Google invoice/receipt extraction, review and confirmation. Unconfirmed OCR output must not post financial records.
6. Real SMTP reset delivery, correct fragment URL, single use, expiry and session revocation.

Only the last provider deliveries/accuracy checks are external-provider pending. Missing Docker/real recovery/browser evidence is an environment acceptance blocker, not an external-provider exception. The specification's overall Definition of Done remains unmet until these gates pass.

## Commits and exact change inventory

| Commit | Change |
| --- | --- |
| `eac387ffbf43ffdb2a6fb4cd7f3d28b9334624cb` | Dependency patches, lockfiles and reproducible advisory lookup. |
| `a31048ffd71563f79f2cfd2b1bae8e61e9f1f943` | MFA protection and pending-session state/version enforcement. |
| `952bec216002ae395f0a2c92a3fa1acbdb6ac94d` | Non-destructive validated staging recovery and tests/runbook. |
| `460212328e49cd09be4a74c78435c8f7bd0553bc` | Production SMTP and ConfigTree crypto mapping. |
| `a524dff15bc425d68556ee582a2fdd399212b504` | Shared OCR secret-file handling and real HTTPS contract probe. |
| `b05147178c7528564fabc07f64796b91eadb1f8e` | Real PostgreSQL/HTTP verification, V15 privilege correction, explicit operator credentials. |
| `f1535a69c1d64ca36084f8083ae8533e9f365cfe` | Fresh production bootstrap, storage, controlled OCR egress and domain rendering. |

The final documentation commit contains this report, sanitized dependency evidence and the [exact changed-file manifest](closure-evidence/changed-files.txt). Its hash is obtained with `git log -1 --format=%H -- docs/SECURITY_PRODUCTION_CLOSURE_2026-10-03.md`; it cannot be embedded in its own contents without changing that hash. Inspect all changes with `git diff 59c92ed4a86dc6a40652908430e3ea9b479ba208..HEAD`. Runtime secrets, temporary fixtures, downloaded tools and generated dependency/build directories are not deliverables and were not committed.
