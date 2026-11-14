# Production closure continuation — 14 November 2026

**Release decision: RED.** Dependency closure and infrastructure verification do
not waive the three core/backend failures or external production acceptance.
Worktree: `C:/Users/Hp/FYP/sme-financial-webapp/tmp/production-closure-infra-tests`.
Branch: `codex/production-closure-infra-tests`. Protected starting commit remains
`153e94b214ba24ac1d81a094eff3805334edaad9`; no merge/rebase/reset/force push.

## Recovered publication

After the reported shutdown, the requested read-only status/log/diff/fetch audit
found a clean tree with **both local and remote** at
`bfeca63fb51fe81705622b8dfc37ee1ef4bc15f0`. The six scoped commits and their push
had completed before shutdown; nothing needed reconstruction or discarding.
The canonical baseline still pointed to the exact original SHA. These commits
are descendants of the independently reviewed `28bc6ba6fd49e35ebc0626e698a1a803e337c8b5`:

| Commit | Scope |
|---|---|
| 3b30215126e70102e8530123d0054e95fb888be3 | Individual OSV triage, affected/fixed ranges, reachability and compatibility |
| 1b8f6e6273cfc114174099bd0821f99bcc6ad37b | Reviewed frontend runtime/build dependency upgrades and deterministic pnpm lock |
| eda89670dd0b7227527e6ac8cba3d114ca3d192b | Reviewed OCR/PDF/image/trust/auth upgrades and exact tested constraints |
| 030a3d74b7f6c85b0daf3ebeacdc45bf5eea6050 | Frozen V52 catalog, independently reviewed table grants and additive acceptance tests |
| 85b53bd3885541f263fadda4ad7dbb790553abe1 | Paired V52 DB/document/key capture and fresh-staging verification |
| bfeca63fb51fe81705622b8dfc37ee1ef4bc15f0 | Ninety-day evidence, fail-preserving logs and digest-locked candidate image inputs |

Follow-up commits finish the same release/reproducibility scope: font assets from
a pinned upstream commit after a hosted remote-font build failure, image evidence
output separated by SHA/image, current runbooks and this persistent handoff. Use
`git log --format='%H %s' 28bc6ba..HEAD` for their exact final identities.

## Dependency closure

[Every original record](security/OSV_TRIAGE.md) is individually retained with its
exact advisory/aliases, affected intervals, withdrawal status, actual installed
range match, selected fix, conditional runtime/build/test reachability and risk.
All 95 original records genuinely matched; none was withdrawn or suppressed.
Aliases do not represent distinct vulnerabilities. Disabled code paths were
documented, never used to suppress an installed affected package.

Reviewed minimum groups:

- Next.js/eslint-config-next 16.3.8 → **16.4.1**; sharp 0.35.4 → **0.35.5** and
  its libvips 1.3.3 → **1.3.4**; narrowly scoped vulnerable-version overrides for
  minimatch 10.2.6 → **11.0.1**, source-map-js 1.2.1 → **1.2.2**, zod 4.6.2 → **4.6.6**.
- Pillow 12.3.0 → **13.0.0**, pypdf 6.19.0 → **6.21.4**, certifi 2026.7.22 →
  **2026.10.18**, pyasn1 0.6.4 → **0.6.5**, opentelemetry-api 1.44.0 → **1.45.2**.

Pillow/minimatch required major changes to reach the first fixed versions; used
APIs, supported Python >=3.11/Node 22 and complete affected gates were reviewed.
Unrelated package versions and financial/OCR semantics remain unchanged. Certifi's
trust-root removal still requires real provider TLS acceptance; no bypass added.
See [upgrade evidence](security/UPGRADE_LOG.md) and [strict scan summary](security/SCAN_CLOSURE.json).

After each group: frontend **460/460**, typecheck, lint **0 errors/51 warnings**,
production webpack build and full real offline browser **20/20**; OCR **248/248**,
`pip check`, backend OCR HTTPS/configuration contracts **15/15**, then real smoke
**6/6**. Strict final inventory **620 versions** (440 npm / 44 PyPI / 136 Maven),
**zero matches, zero lookup errors**. Hosted security run
[43601102261](https://github.com/aviongg/sme-financial-webapp/actions/runs/43601102261)
at `bfeca63` independently passed both dependency and current-source-secret jobs.
This says nothing about Git-history secrets or final image OS vulnerabilities.

## Recovery and grants

[V52 recovery](V52_RECOVERY.md) and the [84-table grant inventory](V52_GRANT_INVENTORY.md)
cover the integrated schema: ledger/stock/obligations/allocations, commercial and
project/evidence state, financing inputs, monthly projections, document metadata
and encrypted WhatsApp state. **84 tables, 64 triggers, 18 application functions**
must match the frozen reviewed structure/ACL/ownership manifest. Flyway source
hashes, scripts, versions and checksums are mandatory. Migration SQL is untouched.

Paired capture binds encrypted database, every encrypted document artifact and
encrypted historical-key/WhatsApp lookup-key bundle to one stopped-writers
consistency point. Before/after all-table fingerprints and file inventories detect
drift; independent quiescence evidence remains required. Restore compares every
retained table, verifies metadata and every document hash, and authenticates all
encrypted rows in **nine fields**, in bounded batches. Missing historical keys,
NULL/missing document paths/bytes and mismatched sets fail. Recovery never changes
accounting rows or repairs historical migrations to pass.

Only newly created random staging resources may be cleaned up. Migration ownership
is restored inside that DB; no global `REASSIGN OWNED`. Database-only status is now
`VERIFIED_DATABASE_ONLY`; paired staging success is
`VERIFIED_PAIRED_STAGING_RESTORE`. Both explicitly leave production acceptance false.

Local real disposable PostgreSQL catalog test **1/1 passed**. The **63 recovery
tests** use actual age/AES-GCM/document bytes and a simulated PostgreSQL boundary;
they are not a physical DB restore claim. Hosted OCR/operations independently
passed those 63, OCR 248, configuration 17 and release tooling 13 at `bfeca63`.
Normal PG discovery includes the new catalog class; the explicit production
profile adds `V52ProductionPrivilegesAcceptance` for read-only full-catalog and
real app/migrator/backup verify-full logins, restricted flags and no indirect role
memberships. It needs `FINSIGHT_TEST_BACKUP_PASSWORD` in addition to existing role
credentials. No production-target test is counted as passed on embedded fixtures.

**V49 owner question only:** `estimate_commercial_links` retains direct DELETE
through default runtime DML; V49 revokes UPDATE/TRUNCATE and mentions draft-delete
cascades. Claude/domain review must settle direct lineage deletion before any
V53+ proposal. The migration/default ACL inventory agrees with observed grants;
no confirmed grant defect or SQL change is claimed.

## CI, release images and blockers

Hosted [validation 43601102295](https://github.com/aviongg/sme-financial-webapp/actions/runs/43601102295)
at `bfeca63` preserves the same core failures: Linux storage-path unit assertion;
recurring optimistic-lock/concurrency error; monthly-record count **1 → 3** during
the read-only invariant test. No retry, skip, widened assertion or suppression was
added. [Core owner handoff](CORE_BACKEND_FINDINGS.md) remains authoritative. No
Claude core fixes were integrated and the `ec7323c` probes remain unchanged.

That run's separate browser build also exposed a `next/font/google` URL-extension
parser failure, while its frontend build passed. The follow-up uses the same font
families/weights/variables via pinned licensed local assets, removing the live
font-service build dependency. Follow-up local validation: **462/462 frontend
tests**, TypeScript, lint **0 errors/51 warnings**, production webpack build and
full unchanged real offline Chromium product gate **20/20** all pass. Original
failure logs remain on that run. No full local backend regression was duplicated.

CI retains XML, logs, browser artifacts, audit output and exact-SHA aggregate
results for **90 days**. Bash pipefail preserves gate failures through `tee`;
newer pushes do not cancel earlier evidence runs. Retention cannot be extended
retroactively on existing artifacts; archive accepted evidence off-platform.
Manual image builds record resolved base digests, source SHA, platform, source
timestamp, build provenance/ID/inspection and SBOM plus a strict HIGH/CRITICAL
scan. They never push or deploy. See [image/acceptance procedure](RELEASE_ACCEPTANCE.md).

**P1 protection gap verified read-only:** the active
[main ruleset](https://github.com/aviongg/sme-financial-webapp/rules/22558803)
only prohibits deletion and non-fast-forward updates; required status contexts
are empty. An administrator must require `release-gate`, `resolved-dependencies`
and `current-source-secrets` on integration PRs and prohibit bypasses. Source
workflow files alone do not enforce merge protection. No settings were changed.

Remaining external gates: full populated **PostgreSQL 17** paired pg_dump/psql
restore and deliberate corruption/missing-artifact failures, actual Docker images/
OS scans, verify-full TLS/HBA/real grants, restored app/volume/key mounting and
cross-tenant file retrieval, measured off-host RPO/RTO and rollback rehearsal,
secret/certificate permissions/renewal/monitoring and explicit real OCR/OpenAI/
Meta/SMTP acceptance. This Windows host has no Docker/psql/pg_dump. Fonts/assets
are pinned but OS package indexes are not snapshot-locked: promote tested image
digests without rebuilding. No provider was activated, paid call made or service
deployed. No branch was merged into another branch.
