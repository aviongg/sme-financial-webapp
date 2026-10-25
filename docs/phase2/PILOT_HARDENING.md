# Client-pilot integrity hardening

This is the bounded R01/R02/R03 remediation explicitly requested by the user, starting from `14cc912a078485ef7499c58cb5b76641c436f7f0` on `dev/fatima`. It preserves aggregate monthly snapshots, the existing human document-confirmation gate, `health-score-v1` and point-in-time balance semantics. It does not implement a ledger, another financial engine or a new product feature. Implementation and local validation completed on 24 October 2026. Production acceptance is not claimed.

## Coordinated migration boundary

The initial unpublished hardening migration was locally named V20. Work stopped when a subsequent fetch revealed a remote allocation conflict. The user's explicit continuation assigns **V29** to this remediation and prohibits importing the other developer's work. The task-owned file is now `V29__monthly_record_integrity.sql`; no Developer-B V20 or V21–V28/V30 file is retained or added. V1–V19 remain immutable.

This branch's test sequence is **V1–V19 then V29**, including a populated **V19→V29** upgrade. Combined V20–V29 ordering, compatibility and upgrade acceptance must be validated later during intentional developer-branch integration. Nothing here establishes V28→V29 acceptance or authorizes production/shared database application.

V29 adds `monthly_records.version BIGINT NOT NULL DEFAULT 0 CHECK(version>=0)` and append-only `document_financial_contributions`. Contribution columns are UUID id, business, document, monthly record, target month, classification, cash-flow impact, NUMERIC(14,2) confirmed amount, authenticated actor, creation time, SHA-256 source fingerprint and duplicate-acknowledgement flag. A unique document constraint prevents a second application. Composite foreign keys bind document and record/month to the same business. Business/month and business/fingerprint indexes support floor and duplicate lookups. Runtime receives SELECT/INSERT only; backup receives SELECT only. UPDATE/DELETE/TRUNCATE triggers defend immutability even against accidental owner DML; references do not cascade away source history.

## R03 identity and audit transaction

The legacy `userId` field on financial/document entities still means business ID. It is not renamed. Controllers obtain authenticated human and active business separately from `BusinessAccessContext`; request JSON cannot choose either identity.

Corrected interactive call sites are monthly create/update, single upload, bulk upload and document confirmation. Each records the human AppUser UUID as actor and the active business UUID as business. Scheduled WhatsApp SENT, INDETERMINATE and FAILED outcomes use system event methods with a null actor and the affected business.

OCR failure auditing belongs to the transactional draft store: only a successful ownership-checked failure transition emits the system event, with business derived from the persisted document. A losing worker or rolled-back failure does not create a misleading event for a newer attempt. Confirmation's monthly mutation, contribution insertion, document confirmation and mandatory audit share one transaction; scoring/audit/persistence failure must roll back the whole operation.

The caller inventory also covered account authentication, password reset, MFA, sessions, platform operations, crypto maintenance, business/team, recommendations, document corrections, configuration, workspace and AI. Their account/global or explicit actor/business scope is retained. The separate platform-CLI target-as-actor convention is outside this business-ID remediation and is not changed.

## R01 attempt ownership and stale recovery

`processing_started_at` is the existing attempt identity; no additional OCR migration is introduced. Pending→processing assigns a timestamp and reads it back after clearing the persistence context, preserving exact PostgreSQL timestamp precision. The claim carries that persisted value through remote extraction. Draft success and failure require matching document ID, processing status and timestamp. They also protect existing extracted/reviewed human data. An old worker updates zero rows after a retry has claimed a new attempt.

Retry and startup recovery share a five-minute timeout. A processing attempt is stale only when its timestamp is null or strictly before the cutoff; equality is fresh. Startup preserves fresh work. Terminal transitions clear the processing timestamp. Existing timestamp-without-zone values retain their established application-zone interpretation. OCR provider calls stay outside database transactions. The historical recovery-test failure remains recorded. New deterministic real-PostgreSQL evidence passes using an independently controlled stale document, separate from the asynchronously retried row. All application instances must retain the same JVM zone for existing timestamp-without-zone values.

## R02 aggregate write contract

All monthly writes lock the business parent before the existing monthly row. Confirmation locks business→scoped document→scoped month; its nested monthly call reacquires the already-held business lock. The parent lock serializes initially absent months as well as existing rows, preventing both lost contributions and competing first inserts. Other businesses remain independent. This intentionally serializes all financial month writes for one business during this pilot.

Monthly responses expose `version`. Manual requests carry `expectedVersion`: null/omitted asserts the month was absent, and a number must equal the existing version. Existing rows cannot be blindly overwritten by an absent-state request. Mismatch returns HTTP409 `stale_monthly_record`, with no financial mutation. JPA `@Version` plus flush provides the version returned to the client. The frontend must require an explicit reload and must not automatically replay the stale financial request.

Manual values remain aggregate totals. Under the same lock, the service sums durable document amounts independently for revenue, operating expenses, COGS, cash inflow and cash outflow. An aggregate below any corresponding positive floor returns HTTP409 `confirmed_source_conflict`; null COGS is also rejected when confirmed COGS is positive. Totals at/above floors are accepted with the current version. Sources are not added again to the manually supplied aggregate. Other nullable snapshot fields remain nullable, and an accepted document adds only its chosen period-flow effects. It leaves point-in-time balances unchanged except the existing explicit initial-month balance requirement.

## Potential duplicates and legacy data

The server hashes a versioned canonical JSON array containing business, target month, confirmed date if present, normalized confirmed party, canonical exact amount, classification and cash-flow impact. Party normalization uses Unicode NFKC, whitespace collapse and locale-independent lowercase; amount normalization does not round. The lookup is business scoped and occurs under the parent lock.

A different already-applied document with the same fingerprint returns HTTP409 `potential_duplicate_source`. This is a conservative warning, not proof that equal receipts are identical. Only an explicit human `allowPotentialDuplicate=true` acknowledgement permits the second source, and the flag is stored in provenance. The frontend presents a dedicated confirmation action and never supplies this flag automatically. Editing the reviewed fields invalidates the acknowledgement. Repeating the same document cannot apply it twice regardless of override.

Protection is prospective from V29. Historical `confirmed_data` contains submitted confirmation fields but lacks durable aggregate effect/replay linkage; later manual snapshot overwrites mean that the original contribution cannot be proven from that JSON alone. No provenance backfill is invented and legacy aggregate amounts are preserved. Fingerprint warnings and source floors cover durable post-migration contributions, not unprovable historical sources.

## Numeric and frontend compatibility

Current monthly decimal fields and confirmation amount/initial balance accept at most twelve integer digits and two fractional digits, preserving nonnegative policy. The valid bound is `999999999999.99`. Validation runs at the request boundary and on resulting aggregate additions before persistence, preventing implicit rounding and NUMERIC overflow. No accounting rounding, negative-cash, recognition or score-failure policy is changed.

The existing monthly workflow carries the version from the loaded snapshot. Stale conflicts block resubmission until an intentional reload. Source-floor conflicts retain the editable form for correction. Document duplicate conflicts retain the exact reviewed request until explicit acknowledgement; no retry automatically overrides a warning. The affected messages and actions are available in English and Urdu. Session/business cancellation boundaries and server authorization remain authoritative.

## Preserved features and acceptance limits

F06 calculator/API/UI remain unchanged. Its five publication records are corrected to identify the integrated commit `14cc912a078485ef7499c58cb5b76641c436f7f0`; original tests ran on the exact tree later fast-forwarded and pushed. Full F06 stays PARTIAL_VERIFIED: no persisted estimate, approval/history, F05 conversion, tax, FX or approved accounting rounding, and costing remains future/unavailable. Historical test outcomes are not rewritten.

Validation results are recorded in implementation state and dated verification evidence. Local disposable PostgreSQL, synthetic accounts and production-frontend browser journeys do not establish production/shared DB, Java21/PostgreSQL17/TLS or live-provider acceptance. Nineteen production-target cases remain separate. No partial push is allowed.

## Validation on the final source tree

| Check | Result |
|---|---|
| Targeted hardening | 100 default cases and 44 PostgreSQL cases passed, zero failures/errors/skips |
| Full backend regression | 973 default cases: 972 passed, 1 existing Windows symlink skip; 154 local PostgreSQL cases passed, zero failures/errors |
| Frontend | 86 unit tests passed; TypeScript and ESLint passed (one existing SessionProvider warning); production Next webpack build passed |
| Synthetic browser | 53 checks passed (24 + 10 + 19); eight screenshots visually reviewed |
| Real production-frontend browser | Pilot hardening: 11 checks; F02: 11 checks; F13: 16 checks; F06: 14 checks; all passed with real Spring/session/CSRF/disposable PostgreSQL and synthetic accounts; external providers simulated/disabled |
| Migration/permissions | Fresh V1–V19 then V29 and populated V19→V29 passed; legacy null/negative values and scores preserved; scoped FK, unique document, append-only and runtime/backup privileges verified |
| Preserved boundaries | V1–V19, F06 source, scoring/CashFlowService, AI/F13 and ModuleRegistry unchanged; main untouched; no other developer code imported |

Earlier failed runs are retained as failures: the first targeted default run had two confirmation request errors because an omitted primitive duplicate flag failed JSON deserialization; the fix normalizes omitted/null to false. The next targeted run had six missing business-profile fixture errors and two insufficient-scoring-fixture failures. Fixtures were completed with real required parent rows and known synthetic repayment evidence. The new pilot browser fixture likewise needed sufficient unchanged scoring inputs while preserving its zero/null assertions. F02 mobile width initially failed before the Urdu font/viewport settled; the test now awaits fonts and two animation frames before the unchanged width assertion and records measured layout diagnostics (390px viewport/390px scroll width, no overflowing elements). The first full regression had one older deliberate-edit test omit expectedVersion; it now submits the initial response version and asserts version advancement while retaining all snapshot-replacement/null assertions. A subsequent full run exposed the new migration fixture leaving a plaintext full name in the shared disposable database; the fixture now uses the existing encryption implementation and test key, preserving strict authentication decryption and all privilege assertions. No assertions, score policy, encryption policy or concurrency checks were weakened. The corrected targeted, browser and final full runs pass.

See [dated evidence](verification/2026-10-24-pilot-hardening.json) for exact commands, environment, exclusions and test details. No full F06 completion, combined-branch migration acceptance, production/shared database acceptance or reviewer signoff is implied.
