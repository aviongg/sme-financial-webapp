# Staging / VPS release and rollback

This procedure is a reviewable release plan. Nothing here authorizes a production
cutover or a change to accounting policy/migrations. Start with disposable staging
on a dedicated Linux Docker Engine / Compose v2 host. The fixed production container
names and ports prevent two stacks sharing one Docker host; use a separate staging VPS.

## Release record

Record the reviewed source SHA, integration/reviewer signoffs, required CI run URLs,
resolved dependencies/security findings, image tags **and immutable digests**, effective
non-secret configuration, V1–V52 Flyway history/checksum acceptance, certificate SAN/expiry,
storage identifiers, backup set IDs, operator and time. Keep secret values out of evidence.
Tag each application image with the reviewed source SHA through `FINSIGHT_RELEASE_TAG`;
retain the exact prior images/digests and matching source. Tags alone are not immutable.
Use the [digest-recording build procedure](RELEASE_ACCEPTANCE.md) and promote tested
image bytes/digests without rebuilding between staging and production. Pin the
storage-init helper in the operator override too. Review new vulnerabilities before promotion.
New CI artifacts request 90 days; copy accepted release evidence, checksums and
manifests into controlled long-term release storage before they expire.

Require the normal checks described in [the closure record](PRODUCTION_CLOSURE.md),
then run the full offline product browser acceptance. There is no automated production
deployment workflow; provider and customer-data gates require their actual owners.

## Prepare staging

1. Provision separate DNS, trusted edge certificate/renewal, internal CA certificates,
   operator access, firewall (only public 80/443 and controlled SSH), time synchronization,
   disk/inode alerts and off-host backup storage. Never publish PostgreSQL/Next/OCR ports.
2. Follow [the security bootstrap guide](../PRODUCTION_RUNBOOK.md) for secret files,
   exact TLS names, non-root readability, PostgreSQL roles and storage encryption. File-backed
   Compose secrets are mounts, not an encrypted secret manager. Do not reuse demo secrets.
3. Keep all outbound messaging disabled/mocked and use synthetic tenants in staging unless
   a controlled real-provider acceptance is explicitly configured. The stock Compose enables
   real Google OCR and needs its actual service account even though OpenAI/Meta remain off.
   Do not point staging at customer data or live phone numbers by accident.
4. Set `.env.production` non-secret values including a reviewed `FINSIGHT_RELEASE_TAG`.
   Review provider mappings; passing an env file to Compose does not itself pass every key
   to containers. Do not print expanded Compose configuration with actual environment tokens.

```sh
# From the exact reviewed checkout; these commands target staging only.
export COMPOSE_PROJECT_NAME=finsight-staging
docker compose --env-file .env.production -f docker-compose.prod.yml --profile migration config --quiet
# Build/scan all six images once with scripts/release/build_release_image.py;
# load accepted bytes and retain their digests before these staging-only starts.
docker compose --env-file .env.production -f docker-compose.prod.yml up --no-build --pull never -d --wait --wait-timeout 120 postgres
docker compose --env-file .env.production -f docker-compose.prod.yml --profile migration run --no-build --pull never --rm backend-migration
# Stop here on any migration error. Do not start the new runtime or repair checksums.
docker compose --env-file .env.production -f docker-compose.prod.yml up --no-build --pull never -d --wait --wait-timeout 240
docker compose --env-file .env.production -f docker-compose.prod.yml ps -a
```

Confirm the migration job exited **0**, applied exactly the approved history, and runtime
has only its restricted login. PostgreSQL health is only listener availability. The migration
profile is deliberately separate and not an automatic runtime prerequisite: operators must
enforce this sequence for every upgrade. Do not edit V1–V52, run Flyway repair casually,
or use `down -v`. Existing volumes are not automatically reinitialized or password-rotated.

## Accept the deployed release

Record independent results, not just a green Compose status:

The explicit Java DB acceptance worker needs JDK 21/Maven, the source checkout,
TLS access on the internal DB network and Docker CLI access to the *same disposable*
Docker engine for socket-only DBA checks. A normal host cannot reach the unpublished
database port or resolve its internal name. Keep that privileged acceptance worker
separate from the application and never put its DBA credentials into runtime services.
Supply these existing test settings from controlled fixture configuration:

| Setting | Required meaning |
|---|---|
| `FINSIGHT_TEST_DB_URL` | JDBC URL to the reachable staging DB at its certificate-matching canonical hostname |
| `FINSIGHT_TEST_DB_WRONG_HOST_URL` | Same reachable DB via an alias not in its certificate SAN; an unreachable host is not a hostname-mismatch test |
| `FINSIGHT_TEST_DB_CA` | Absolute trusted CA file path inside the acceptance worker |
| `FINSIGHT_TEST_DB_CONTAINER`, `FINSIGHT_TEST_DB_NAME` | Exact disposable container/database used by Docker-exec DBA probes |
| `FINSIGHT_TEST_APP_PASSWORD`, `FINSIGHT_TEST_MIGRATOR_PASSWORD`, `FINSIGHT_TEST_BACKUP_PASSWORD`, `FINSIGHT_TEST_DBA_PASSWORD` | Fixture role credentials via secret-manager environment injection, never shell arguments or evidence |

The profile retains the existing security/TLS probes and now adds read-only
`V52ProductionPrivilegesAcceptance` for every integrated object/ACL and the real
app/migrator/backup logins. Retain actual PostgreSQL 17 results before claiming
deployment acceptance; embedded catalog coverage is not that evidence.

- Fresh database bootstrap **and** populated previous-release upgrade on disposable copies.
- PostgreSQL 17 `verify-full`, wrong CA/hostname/plaintext denials, runtime/backup grants and
  network DBA denial (`scripts/test_pg_hba.py` and the explicit Java acceptance profile).
- Run backend `./mvnw -B -Pproduction-db-acceptance -DskipTests=false
  -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false verify` with the dedicated fixtures
  specified by `DatabaseRolePrivilegesPostgreSqlIT` / `PostgreSqlTlsConnectionIT`; never
  combine it with `postgres-it` or point it at a customer's DB. Inspect failures/skips.
- Real HTTPS browser journey through Nginx: redirects, session/CSRF, CSP nonce, hostile-origin
  rejection, roles/tenant switching, EN/UR/RTL/mobile, controlled daily activity, derived
  month/source map, late/backdated refresh and persisted score. Manual monthly totals return
  409; OCR review/confirmation remains evidence only. Keep numerical policy acceptance with
  the financial owners; this infrastructure branch supplies no new accounting authority.
- Recreate only backend and retrieve a controlled uploaded document with the same checksum;
  verify original files survive and cross-tenant IDs fail. Test OCR TLS/wrong secret/limits
  and separately an actual controlled Google receipt when authorized.
- Controlled SMTP receipt/reset/revocation. Activate OpenAI/Meta only after the separate
  configuration and external acceptance in the closure record; synthetic AI is not live AI.
- Inject disposable outages (DB, backend, Next, OCR and provider). Check 503/health transitions,
  user-visible failures, recovery and alerts. Edge liveness must not be treated as full readiness.
  Compose startup dependencies do not continuously restart/remove unhealthy containers.
- Execute a reviewed full V52 recovery drill on another host, including the document volume,
  historical keys and new encrypted WhatsApp data; measure recovery time and accepted data loss.

## Backup, promotion and rollback

The [encrypted database/keyring tools](../../scripts/backup/README.md) create a fresh staging
database, verify matching Flyway history, purge sessions/reset/pending-MFA state and test
every encrypted value across nine fields. The [V52 paired recovery tool](V52_RECOVERY.md)
now binds database, document bytes and all historical keys with full catalog/data
verification; it still needs the actual PG17/server/app/rollback drill.

Before an approved production maintenance window:

1. Record target release and last-known-good images, schema/source version, exact rollback
   trigger, operator, maintenance notice, RPO/RTO and maximum downtime. Confirm tested backups
   exist off-host and private recovery identities are actually accessible to the operator.
2. Stop ingress writes and **all writers**: backend replicas, schedulers, inbound webhook
   processing, OCR workers and external integrations. Verify quiescence. A rolling application
   upgrade is permitted only with documented old/new schema and job compatibility.
3. Take encrypted DB backup and matching document-volume snapshot with checksum inventory,
   retain every referenced encryption key and backup manifests, preserve the old volume/images,
   and record the common consistency point. A running DB backup alone is not a consistent
   snapshot of external document bytes. Store encryption keys/identities separately from backups.
4. Promote the exact accepted image digests; run the migrator once and validate it before runtime.
   Run health plus the authenticated ingress journey before reopening traffic. Watch failure,
   latency, queue, disk and delivery metrics; retain the previous release through the observation window.

Rollback must be selected **before** migration:

- **Application-only rollback** is allowed only if reviewed backward compatibility permits the
  old image to use the upgraded schema and persisted data. Redeploy the recorded prior digests,
  verify readiness and authenticated smoke, then reopen traffic. Do not rerun old migrations.
- **Data rollback** requires explicit owner approval of post-backup data loss/reconciliation.
  Keep writers stopped; restore DB into a *new* database using the matching prior source,
  restore its paired document snapshot and historical keys, validate schema/grants/triggers/
  ciphertext/files, then perform a rehearsed connection/volume cutover. Preserve the failed
  database/volume for reconciliation. Do not downgrade by deleting Flyway rows or reversing SQL.

No live database cutover, off-site backup schedule or automated rollback is implemented
by the repository tools. Paired encrypted document capture/restore now exists but is
not production acceptance. Those are explicit server/operator deliverables.
