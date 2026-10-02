# Encrypted backup and staging recovery

Install the recovery dependencies in an operator-managed Python 3.10+ environment:

```sh
python -m pip install -r scripts/backup/requirements.txt
python -m unittest discover -s scripts/backup -p 'test_*.py' -v
```

The fixture suite uses real age encryption and AES-256-GCM with a simulated
PostgreSQL boundary. It is not evidence that Docker, production roles, TLS or a
real database recovery drill have passed.

## Backup

Use the owner's **public** age recipient for backup. Keep their private identity
offline; never copy it into the application, database image or Git. Passwords have
no built-in fallback. Prefer secret files to CLI passwords (which are visible in
shell history). `BACKUP_DB_PASSWORD` and `DBA_DB_PASSWORD` are supported for
explicit environment-based operation; `*_PASSWORD_FILE` variables are supported
by the CLIs as alternatives to their file arguments.

The example uses the production PostgreSQL container and its mounted CA. Host
mode (`--no-container`) requires PostgreSQL client tools, the correct CA path on
that host, network access and a certificate matching `--host`. Every TCP
connection requires `PGSSLMODE=verify-full`; a Docker DBA restore uses the local
socket allowed by production HBA. libpq TLS settings are forwarded into Docker
by variable name, along with the password, without putting secret values in the
child-process argument list.

```sh
export PGSSLMODE=verify-full
export PGSSLROOTCERT=/etc/finsight/certs/postgres-ca.crt
python scripts/backup/backup_database.py \
  --container finsight-postgres-prod --host postgres.finsight.internal \
  --dbname sme_health --password-file secrets/backup_db_password \
  --recipient-file /secure/offline-owner/public-recipient.txt \
  --output-dir /secure/encrypted-backups/database
python scripts/backup/backup_keyring.py \
  --recipient-file /secure/offline-owner/public-recipient.txt \
  --secrets-dir secrets --output-dir /secure/encrypted-backups/keyring
```

The keyring collector reads the selected ConfigTree directory (`--secrets-dir`,
then `FINSIGHT_SECRETS_DIR`, then `/run/secrets` or `./secrets`) and takes those
`crypto_key_*` files over legacy `FINSIGHT_CRYPTO_KEY_*` environment variables,
matching the production secret mapping. When no secrets directory exists it
supports the non-production environment-only workflow. Set
`FINSIGHT_CRYPTO_ACTIVE_KEY_ID` to the application's current active key ID
(default `k1`). Include every retained historical key. Missing active keys,
unreadable files, invalid Base64 and keys not exactly 32 decoded bytes fail.

Database and keyring artifacts are separate age-encrypted files. The JSON
manifest's SHA-256 checksum is an **unkeyed integrity checksum**, not proof of
who produced the archive. Use trusted owner-controlled backups. Never restore
untrusted SQL with DBA privileges.

## Staging restore

Run from the matching application source checkout. Current Flyway versions,
filenames and checksums are mandatory; do not edit old migrations to bypass a
failure. A backup from an older app release must first be recovered using its
matching checkout and then upgraded under a separately reviewed migration plan.

```sh
python scripts/backup/restore_database.py \
  --container finsight-postgres-prod \
  --admin-password-file secrets/dba_db_password \
  --backup-file /secure/encrypted-backups/database/selected.sql.age \
  --identity-file /secure/offline-owner/private-identity.txt \
  --keyring-file /secure/encrypted-backups/keyring/selected.age
```

The default database name is random. `--staging-db NAME` (legacy alias
`--target-db`) requests a specific **new** database and refuses an existing name.
Only lowercase PostgreSQL identifiers matching `[a-z_][a-z0-9_]{0,62}` are
accepted. `postgres`, `template0`, `template1` and the maintenance database are
never targets. The maintenance database defaults to `postgres`.

Decrypted SQL streams only into the database created by this run, with psql
startup files disabled, `ON_ERROR_STOP=1` and a single transaction. Any decryption,
IO, SQL, purge, query or validation failure removes only that staging database;
cleanup failure is reported explicitly and never produces success. A failed
database creation does not authorize dropping a concurrent creator's database.

Success is exactly `VERIFIED_STAGING_RESTORE`: all expected domain tables and
Flyway history match, platform-role/audit triggers are enabled on the correct
tables, and sessions/session attributes/reset tokens/pending MFA are zero. A
requested keyring must decrypt and validate; all referenced historical key IDs
must exist. Representative ciphertext for every field/key combination is
authenticated with the Java `enc:v1` AES-GCM format and field/row AAD. The report
includes the sample count; an empty database cannot prove key/data compatibility.
Omitting `--keyring-file` leaves `keyring_recovery` null; it does not assert that
encrypted application data is usable.

No live cutover is implemented. The verified staging database remains for
operator inspection. Before any separately planned cutover, stop writers,
retain the original database and document-store snapshot, restore and verify the
matching retained application keys, and rehearse connection/ownership changes
and rollback. Application ciphertext/key recovery alone does not recover uploaded
document bytes: preserve the document volume separately. Delete a staging DB
only after explicitly verifying its name and that no inspection process uses it.

## Real disposable PostgreSQL drill

Provision a quiescent disposable production-like database with current Flyway
migrations and representative encrypted fixture data. Supply its matching
application key directory and configured DBA/backup credentials. Stop application
writers for the drill so its before/after data fingerprints are meaningful.

```sh
export PGSSLMODE=verify-full
export PGSSLROOTCERT=/etc/finsight/certs/postgres-ca.crt
python scripts/test_disaster_recovery.py \
  --source-db sme_health --container finsight-postgres-prod \
  --host postgres.finsight.internal --secrets-dir secrets \
  --admin-password-file secrets/dba_db_password \
  --backup-password-file secrets/backup_db_password
```

This performs a real encrypted pg_dump, wrong-key/corrupt-backup/partial-SQL
failures, missing-trigger/incorrect-Flyway/blocked-purge failures, wrong/corrupt
keyring failures, existing-name refusal, successful recovery with ciphertext
authentication, and backup-role SELECT/DML/DDL permission checks. Each failure
must occur at its expected gate, remove its fresh staging DB and leave source
data/catalog fingerprints unchanged. Successful staging DBs are also cleaned up
by the drill. Any failure exits nonzero; only the entire real drill may report
`REAL_POSTGRES_DRILL_PASSED`. No external provider is needed for this drill.
