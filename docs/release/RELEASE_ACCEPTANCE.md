# Release record, immutable image inputs and required checks

Nothing in these commands deploys or authorizes production. Run on disposable
staging first, using synthetic data and disabled/mock paid providers. The
[continuation handoff](CONTINUATION_HANDOFF.md) supersedes older closure counts.

## Require exact CI checks

For the exact proposed integration commit, require these **job check names**:

| Required check | What it gates |
|---|---|
| `release-gate` | backend-unit, all PostgreSQL shards, frontend, browser-smoke, OCR/operations; missing/skipped/failed jobs fail |
| `resolved-dependencies` | strict resolved Maven/PyPI/pnpm OSV matches and lookup errors |
| `current-source-secrets` | current checked-out source secrets, not Git history |

Do not require only the workflow display name or only one matrix shard. Keep
PR checks mandatory, require the reviewed integration SHA to be up to date, and
prohibit bypasses/direct unreviewed updates under the owner's branch policy.
The read-only API audit on 14 November found main protected by ruleset 22558803
against deletion/force updates **only**; no required status checks were configured.
An administrator must configure merge/status requirements. No settings were
changed here. Recheck through the repository UI/API and retain that evidence.

Normal CI calls no live providers. Dedicated `production-db-acceptance`, image
build/scans, full product browser workflow input and actual provider tests are
separate gates. Do not equate green source checks with production acceptance.
Retain per-job XML/logs/screenshots/audit JSON and `validation-result.json`, its
exact `head_sha`, run ID/attempt and URLs. New artifacts request **90 days**;
repository policies may cap retention. Archive accepted records off-platform
before expiry. Existing 14-day artifacts are not retroactively extended.

## Build once and record bases

Use the exact clean reviewed checkout on Linux amd64 with Docker Buildx. The
manual image workflow runs the same helper and a strict HIGH/CRITICAL Trivy gate
plus a separately retained SBOM; it never pushes a registry tag or deploys.

```sh
git status --short --branch
git rev-parse HEAD
python3 scripts/release/build_release_image.py backend
python3 scripts/release/build_release_image.py frontend
python3 scripts/release/build_release_image.py ai
python3 scripts/release/build_release_image.py nginx
python3 scripts/release/build_release_image.py postgres
python3 scripts/release/build_release_image.py ocr-egress
```

Each defaults to `backend/target/release-images/FULL_SHA/IMAGE/`, recording:
resolved `base-images.json` digests, source/epoch/platform/tool versions,
Buildx maximum provenance metadata, local image ID and inspection. Labels and
tags use the full source SHA. Backend JAR output timestamps use the source
commit time. All external Dockerfile stages accept a digest through declared
build arguments; default tags remain unchanged for development builds.

To reproduce the **same base selection**, supply the accepted lock explicitly:

```sh
python3 scripts/release/build_release_image.py backend \
  --base-lock /secure/accepted-release/FULL_SHA/backend/base-images.json \
  --output backend/target/release-rebuild/backend
```

Mutable or missing lock entries fail; a first build resolves current tags and
records them, so a later unqualified build is a new candidate. OS package indexes
(`apk`/`apt`), compiler/runtime/platform differences and build tools still prevent
a bit-identical rebuild guarantee. Fonts are pinned local licensed assets now;
builds no longer fetch Google Fonts. OS/image vulnerability scanning remains
mandatory and is independent of a zero-match source OSV report.

Promote the **already tested image bytes/digests**, never rebuild during promotion.
Local image IDs are not registry manifest digests. Under a separately approved
distribution procedure, record actual registry digests (or signed docker-save
archive checksums plus loaded IDs) and retain previous images for rollback.
The Compose `storage-init` helper still defaults to mutable `alpine:3.22`;
pin its exact reviewed digest in the operator's deployment override as well.
Do not invent a digest or treat an unrecorded pull as accepted.

## Dedicated database acceptance

Use a newly provisioned, clearly synthetic PostgreSQL 17/TLS target and its
reviewed bootstrap/HBA/certificates. Set a credential-free
`FINSIGHT_TEST_DB_URL=jdbc:postgresql://HOST:PORT/DATABASE`, `FINSIGHT_TEST_DB_CA`,
`FINSIGHT_TEST_APP_PASSWORD`, `FINSIGHT_TEST_MIGRATOR_PASSWORD`,
`FINSIGHT_TEST_BACKUP_PASSWORD`, `FINSIGHT_TEST_DBA_PASSWORD`,
`FINSIGHT_TEST_DB_CONTAINER` and `FINSIGHT_TEST_DB_NAME` using the operator's
secret mechanism. Never print the environment or put credentials in a JDBC URL.

```sh
cd backend
./mvnw -B -Pproduction-db-acceptance -DskipTests=false \
  -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false verify
```

The profile includes the existing role/TLS suites and new read-only
`V52ProductionPrivilegesAcceptance`. It checks every frozen V52 object/grant,
real verify-full app/migrator/backup logins, restricted role flags and absence of
indirect memberships. The production catalog may render definitions differently
on PostgreSQL 17 than the frozen PostgreSQL 14 fixture: review an exact diff,
never regenerate the inventory or weaken a grant expectation automatically.
No migration is run by the new acceptance class. Existing profile tests include
controlled authorization probes; never target demo/customer databases.

Then run the [paired recovery procedure](V52_RECOVERY.md) on a representative
populated disposable source. Confirm all-table fingerprints, historical keys,
document bytes and each negative corruption/missing-artifact case, new-resource
cleanup and unchanged source. Mount recovered DB/volume/keys into an isolated
application, verify authenticated and cross-tenant file access, record measured
RPO/RTO and rehearse rollback. A database-only restore is insufficient.

## Release decision

Attach exact CI/scan/artifact records, financial-owner resolution of the three
preserved backend blockers, approved V52 history, deployed digests/configuration,
DB/TLS/HBA/secret-permission and ingress tests, paired recovery/rollback evidence,
monitoring/off-host retention and controlled provider acceptance. Resolve V49
direct lineage DELETE with Claude/domain owners without changing V1–V52.
Only the release owner can record production acceptance after those gates pass.
Current decision remains **RED**; no live credentials/providers/deployment or
merge into another branch was performed by this continuation.
