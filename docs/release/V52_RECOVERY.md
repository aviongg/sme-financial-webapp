# V52 paired recovery acceptance

This is staging recovery tooling, not permission to cut over. The old
`VERIFIED_STAGING_RESTORE` label is retired. A database-only restore reports
`VERIFIED_DATABASE_ONLY` with `production_recovery_accepted: false`; even a
paired staging restore leaves production acceptance false until the real server
mounting, authenticated application journey and rollback have been rehearsed.

## Coverage

The frozen [catalog](../../scripts/backup/manifests/README.md) requires **all 84
V52 tables, 64 triggers and 18 application functions**, their columns/defaults,
constraints, indexes, function bodies/configuration, ownership and role grants.
This includes the ledger/reversals/allocations, financial obligations and their
allocations, FX/financing inputs, inventory catalog/stock/movements/cost state,
projects/commitments/actuals, estimates/conversions, commercial documents/lines/
recognition/evidence, monthly records and dirty projection state, audit/security
and WhatsApp links/messages/conversations. No V1–V52 SQL is changed.

The paired manifest records row counts and SHA-256 fingerprints of **every
public table**, with only intentionally purged sessions/session attributes/reset
tokens and pending MFA excluded from the restored-data comparison. Enabled MFA
and its recovery codes remain. All other rows, including projection/financing
inputs and document metadata, must match the same captured point. Fingerprints
are diagnostic evidence, not an accounting correctness test. Full-table hashing
is intended for quiescent SME staging/recovery; measure runtime on actual scale.

Every non-null encrypted value in the following nine fields is inspected in
batches of 500 and authenticated against its retained key ID and exact source AAD:

| Field | AAD |
|---|---|
| app_users.full_name | FinSight\|AppUser\|fullName |
| business_profiles.whatsapp_number | FinSight\|BusinessProfile\|whatsappNumber |
| uploaded_documents.original_filename | FinSight\|UploadedDocument\|originalFilename |
| whatsapp_deliveries.destination_number | FinSight\|WhatsAppDelivery\|destinationNumber |
| user_mfa.totp_secret | FinSight\|UserMfa\|totpSecret\|{user_id} |
| commercial_documents.counterparty_name_ciphertext | FinSight\|CommercialDocument\|counterpartyName |
| whatsapp_conversations.turns_ciphertext | whatsapp_inbound |
| whatsapp_inbound_messages.body_ciphertext | whatsapp_inbound |
| whatsapp_inbound_messages.reply_ciphertext | whatsapp_inbound |

Legacy plaintext in the older converter fields is not silently re-encrypted;
strict encryption migration policy remains an owner gate. Malformed envelopes
and plaintext in explicitly named ciphertext fields fail. Historical keys are
never discarded just because a newer active key exists. The encrypted key bundle
also retains `whatsapp_phone_hash_key` from the selected ConfigTree directory;
pending/active WhatsApp links require it. Hash-only stored phone references cannot
prove an arbitrary supplied lookup key is correct: verify the retained deployed
configuration and a synthetic relink/lookup journey. No lookup-key rotation or
application crypto migration is implemented here.

## Capture one consistency point

Install `scripts/backup/requirements.txt`, PostgreSQL client tools (or use the
explicit Docker container), the trusted CA and matching application checkout.
Use a **disposable synthetic target first**. Stop ingress and all writers,
including schedulers/webhooks/OCR/background jobs and key rotation. Independently
verify quiescence and record a maintenance ticket. Before/after DB fingerprints
and document checksums detect drift; they cannot prove absence of transient
writes. An attestation flag alone is insufficient operational evidence.

```sh
export PGSSLMODE=verify-full
export PGSSLROOTCERT=/etc/finsight/certs/postgres-ca.crt
export FINSIGHT_CRYPTO_ACTIVE_KEY_ID=k2 # actual retained deployment value
python scripts/backup/paired_recovery.py capture \
  --container finsight-postgres-prod --host postgres.finsight.internal \
  --admin-password-file secrets/dba_db_password \
  --backup-password-file secrets/backup_db_password --database sme_health \
  --recipient-file /secure/offline-owner/public-recipient.txt \
  --secrets-dir secrets --document-root /secure/snapshot-source/documents \
  --output-parent /secure/off-host-recovery-sets \
  --stopped-writers-ticket SYNTHETIC-DRILL-TICKET
```

The document root must be the actual mounted backend document volume, resolved
and reviewed by the operator. Output must be outside that source volume. The
tool creates a new unique directory; it never merges or prunes other sets. It
streams every document into an individual age-encrypted artifact, and encrypts
the manifest (paths, sizes, plaintext and ciphertext hashes, fingerprints and
consistency ID). SQL and keys are separately age-encrypted. Original filename
PII remains encrypted in the database. Missing/NULL metadata path/size, missing
bytes, links, special files, unsafe relative paths and changed source data fail.
The DB has no original document-content hash; snapshot hashes establish retained
bytes at capture, not proof of the file originally uploaded. Legacy missing-byte
reconciliation requires an explicit owner decision, never manufactured metadata.

The manifest binds the **database + all document artifacts + key bundle** through
their SHA-256 hashes. Keep the entire directory together, owner-controlled and
off-host; keep the private identity separately offline. Checksums alone do not
authenticate the producer. Restore only trusted owner-produced SQL, since the
restore account is privileged. Capture is not recovery acceptance.

## Restore and verify on a separate disposable target

```sh
python scripts/backup/paired_recovery.py restore \
  --container finsight-postgres-prod --host postgres.finsight.internal \
  --admin-password-file secrets/dba_db_password \
  --recovery-set /secure/off-host-recovery-sets/SELECTED/recovery-set.age \
  --identity-file /secure/offline-owner/private-identity.txt \
  --document-output-parent /secure/disposable-restored-volumes
```

The restore provisions a new random database and a new random document directory.
Existing targets are never selected. It authenticates artifact pairing before
creating a DB, validates the full V52 structure/ACLs and history, purges ephemeral
authentication state, assigns migrator ownership **only within its newly created
DB**, verifies every encrypted value/key, compares every retained table and
document metadata, and decrypts/verifies every document byte. Failure cleans
only resources created by this run; cleanup failures are explicit. A successful
paired result is `VERIFIED_PAIRED_STAGING_RESTORE`. Inspect its exact new DB and
document paths, and mount them plus the recovered historical keys into an
isolated application before separate cutover approval.

Never run `REASSIGN OWNED`, delete Flyway rows, edit V1–V52 or use `down -v` to
make recovery pass. A source/catalog mismatch fails closed. Older backups require
their matching checkout first. Keep the previous DB, document volume, keys and
immutable application images together for rollback; do not mix consistency points.
Record RPO/RTO, accepted post-backup loss/reconciliation and the tested connection/
volume switch-back procedure before enabling traffic.

## Evidence and remaining acceptance

Local evidence: a **real fresh PostgreSQL V1–V52 catalog test passes**; **63 Python
recovery tests** use real age/AES-GCM/document bytes with a simulated SQL boundary,
including missing tables/triggers, altered indexes/constraints/grants/function
bodies, missing historical keys, later corrupt ciphertext, WhatsApp AAD/lookup-key
requirements, cross-paired/missing artifacts and fresh-resource cleanup.

A full physical paired pg_dump/psql round trip was **not run on this Windows host**:
Docker, psql and pg_dump are unavailable. `scripts/test_disaster_recovery.py` is
now explicitly a database-only drill and cannot claim whole-system acceptance.
Run the paired procedure on a quiescent synthetic PostgreSQL 17/TLS target with
representative populated ledger/stock/obligation/commercial/projection/evidence
and historical-key rows, remove/mismatch each artifact in copied recovery sets,
confirm failure/cleanup and source fingerprints, then rehearse authenticated
application/file retrieval and rollback on another host. Empty tables prove
schema coverage only. Real provider credentials, server storage/HBA/TLS, off-host
retention and operational RPO/RTO remain external production gates.
