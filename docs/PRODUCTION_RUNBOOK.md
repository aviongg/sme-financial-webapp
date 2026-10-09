# Production startup and acceptance

This runbook describes the security-production-closure configuration. Configuration
tests are not evidence of working images, TLS, delivery, or a browser journey.
Run the container acceptance commands below on a disposable Linux Docker host
before deploying real data. Do not reuse a developer database volume for that test.
The existing `container_name` settings and public ports require a host without a
second running FinSight production stack.

## Required inputs

Run commands from the repository root with Docker Engine and Docker Compose v2+
available. Use an actual public DNS hostname for `APP_DOMAIN`; no scheme, port,
wildcard, or path. Only Nginx publishes ports, 80 and 443. The frontend remains
private and trusts the nonce supplied by Nginx.

Set these non-secret deployment values in an ignored `.env.production` file:

```dotenv
APP_DOMAIN=finsight.example.com
SMTP_HOST=smtp.example.com
SMTP_PORT=587
SMTP_USERNAME=replace-with-provider-username
SMTP_FROM=noreply@finsight.example.com
```

SMTP must support authenticated STARTTLS with a trusted certificate matching
`SMTP_HOST`. Configure and verify the sender/domain with the provider. The backend
derives its public origin and reset URL from `https://${APP_DOMAIN}`. Do not supply
SMTP passwords, encryption keys, or service credentials in this file.

Create the following secret files, with no extra whitespace:

| Host file | Meaning / consumers |
| --- | --- |
| `secrets/dba_db_password` | Random initial DBA password; PostgreSQL only |
| `secrets/migrator_db_password` | Random migration role password; PostgreSQL and migration job |
| `secrets/app_db_password` | Random runtime role password; PostgreSQL and backend |
| `secrets/backup_db_password` | Random backup role password; PostgreSQL / backup operator |
| `secrets/crypto_key_k1` | Base64 of exactly 32 random bytes; backend |
| `secrets/ocr_service_key` | Random shared Java/FastAPI service secret |
| `secrets/smtp_password` | Real provider SMTP password; backend |
| `secrets/google_vision_credentials.json` | Real least-privilege Google Vision service-account JSON; AI only |

For a new installation, this creates only the locally generated secrets and
refuses to overwrite any existing file. Keep the encryption key backed up; changing
it without retaining the old key makes existing encrypted data unreadable.

```sh
python - <<'PY'
import base64
from pathlib import Path
import os
import secrets

directory = Path('secrets')
directory.mkdir(exist_ok=True)
directory.chmod(0o700)
names = ['dba_db_password', 'migrator_db_password', 'app_db_password',
         'backup_db_password', 'crypto_key_k1', 'ocr_service_key']
if any((directory / name).exists() for name in names):
    raise SystemExit('Existing secrets found; refusing to overwrite')
for name in names:
    value = (base64.b64encode(os.urandom(32)).decode('ascii')
             if name == 'crypto_key_k1' else secrets.token_urlsafe(48))
    with (directory / name).open('x', encoding='ascii') as output:
        output.write(value)
    (directory / name).chmod(0o644)
PY
```

Install the two external-provider files separately from the provider's secure
console or secret manager. Never paste their contents into logs or commit them.
The repository and Docker context both exclude secrets and private keys.

Supply valid TLS certificate chains and matching private keys at:

| Host file(s) | Required certificate identity |
| --- | --- |
| `certs/postgres-server.crt`, `certs/postgres-server.key` | SAN `DNS:postgres.finsight.internal` |
| `certs/postgres-ca.crt` | CA that issued the PostgreSQL server chain |
| `certs/ai-server.crt`, `certs/ai-server.key` | SAN `DNS:ai.finsight.internal` |
| `certs/ai-ca.crt` | CA that issued the AI server chain |
| `certs/edge.crt`, `certs/edge.key` | SAN matching `APP_DOMAIN`; browser-trusted chain for production |

The public certificates already tracked in `certs/` are historical local fixtures;
they do not provide usable production private keys. Replace them for deployment
without committing runtime material. Keep issuing-CA private keys off the host.

Local Compose implements file-backed secrets as read-only mounts and does not
remap host ownership/mode. On a dedicated Linux deployment host, keep `secrets/`
and `certs/` mode 0700, owned by the deployment operator, and the mounted files
mode 0644 so the container users can read them. The parent directories prevent
other host users from reading those files. This applies to provider JSON and TLS
keys as well. On Windows, apply equivalent restrictive directory ACLs. In an
orchestrator with managed secrets, use its UID/mode controls instead.

```sh
chmod 0700 secrets certs
chmod 0644 secrets/* certs/postgres-server.crt certs/postgres-server.key \
  certs/postgres-ca.crt certs/ai-server.crt certs/ai-server.key \
  certs/ai-ca.crt certs/edge.crt certs/edge.key
```

PostgreSQL's entrypoint copies its private key into a private container directory
with PostgreSQL ownership and mode 0600 before running the official image
entrypoint. The AI and Nginx processes read only their individually mounted keys.
The backend and AI run as UID/GID 10001; Nginx uses the image's `nginx` user.
The migration image is also non-root and receives only its migration password.

## Build, initialize, migrate, start

Use a unique project name for the first disposable acceptance run. Named volumes
are project-scoped. The commands below never delete an existing volume. A new
project name must truly be unused, and there must be no other production containers
with the fixed names from this Compose file on the chosen Docker host.

```sh
export COMPOSE_PROJECT_NAME=finsight-closure-acceptance
docker compose --env-file .env.production -f docker-compose.prod.yml --profile migration config --quiet
docker compose --env-file .env.production -f docker-compose.prod.yml --profile migration build --no-cache
docker compose --env-file .env.production -f docker-compose.prod.yml up -d --wait postgres
docker compose --env-file .env.production -f docker-compose.prod.yml --profile migration run --rm backend-migration
docker compose --env-file .env.production -f docker-compose.prod.yml up -d
docker compose --env-file .env.production -f docker-compose.prod.yml ps -a
```

The image build includes backend, frontend, AI, Nginx, PostgreSQL, and the OCR
egress proxy. Nginx uses stable `1.30.5-alpine`, verified against the official image
manifest when this runbook was prepared; image build/runtime validation is still
required. `storage-init` uses the Alpine image and exits successfully after
creating `/storage/documents` with UID/GID 10001. Backend startup waits for that
completion and PostgreSQL health. Migration is a separate explicit deployment
step; never start a new backend revision before successful migration.

On an empty database volume, official PostgreSQL `_FILE` initialization creates
the DBA using SCRAM. `docker/postgres/init/10-roles.sh` then creates restricted
migrator/app/backup logins from secret files, assigns schema ownership to the
migrator, preinstalls `pgcrypto` as DBA for migration V1, and installs default
table/sequence grants. The migrator is not granted database CREATE just to install
that extension. Runtime cannot create objects
or use database TEMP privileges. Backup receives SELECT only. Rerunning role
provisioning preserves table-specific revocations installed by migrations, including
the audit table's append-only grants. Existing volumes are not automatically
reprovisioned; rotating role passwords requires an explicit DBA operation as well
as changing the corresponding mounted secret.

All database TCP access is restricted to the internal DB subnet and TLS. The DBA
is allowed only on the local container socket. The HBA path is outside `PGDATA`,
so it works with an empty named volume. The healthcheck uses the final TCP
listener, not the socket-only temporary initialization server. Use a real authenticated query after
`pg_isready` to prove login and migration acceptance; health alone proves neither.

## Required acceptance checks

Record command output and exact revisions without logging credentials.

1. **Images and edge:** all clean builds above succeed. Nginx's official template
   hook substitutes only `APP_DOMAIN`; its final hook runs `nginx -t` before serving.
   Confirm the generated configuration inside the running edge:

   ```sh
   docker compose --env-file .env.production -f docker-compose.prod.yml exec nginx nginx -t
   docker compose --env-file .env.production -f docker-compose.prod.yml exec nginx \
     sh -c 'grep server_name /etc/nginx/conf.d/finsight.conf'
   ```

   Visit HTTP and verify it redirects to the same HTTPS domain. Check the secure
   cookie, allowed public origin, hostile-origin rejection, CSP nonce, and browser
   console. No literal `${APP_DOMAIN...}` may remain in the generated config.

2. **Database privileges and TLS:** run `scripts/test_pg_hba.py` from a disposable
   Python worker attached to `finsight-db-net`, with `psycopg2`, `pg_dump`, the four
   role secret files, trusted CA, and an unrelated CA available. Set
   `FINSIGHT_SECRETS_DIR`, `DB_HOST=postgres.finsight.internal`,
   `DB_SSL_ROOT_CERT`, and `DB_ROGUE_CA_CERT`. A host process cannot connect to
   the unpublished database port. The script first proves legitimate `verify-full`
   connections, then tests app DML, app DDL denial, backup SELECT/pg_dump, backup
   mutation denial, plaintext denial, network DBA denial, and wrong CA/hostname
   denial. It creates a uniquely named probe table and removes it in `finally`.
   Any failure exits nonzero; an unreachable server does not count as successful
   rejection.

3. **Persistent documents:** upload a supported file through the authenticated
   backend, record its ID and checksum, and retrieve it. Recreate only the backend:

   ```sh
   docker compose --env-file .env.production -f docker-compose.prod.yml up -d --force-recreate backend-runtime
   ```

   Retrieve the same ID and compare bytes. Verify storage is under the named
   volume `/storage/documents`, backend UID is 10001, cross-tenant IDs are denied,
   and path traversal/symlinks remain rejected. Do not delete `document_storage`.

4. **OCR:** backend resolves and verifies `ai.finsight.internal` using `ai-ca.crt`
   and both services use `ocr_service_key`. AI refuses startup when required TLS
   files are absent. Wrong key must be rejected; a controlled local provider fixture
   must complete the HTTPS `/extract` contract. Production AI has no direct egress;
   it uses an isolated CONNECT proxy allowing only `vision.googleapis.com:443` and
   `oauth2.googleapis.com:443`, with end-to-end provider TLS. Both gRPC and Google
   Auth use that proxy. Backend/database are not attached to the new provider
   egress networks. The pre-existing web network remains available for backend
   SMTP. Do not interpret this as complete host-wide outbound firewall enforcement.
   Verify a real receipt using separately supplied Google credentials, then review
   and confirm once; an unconfirmed draft must not post financial records.

5. **SMTP and live product journey:** request reset for a controlled account;
   confirm real delivery, fragment-token URL, one-time use, and session revocation.
   In frontend live mode, register, login, create business, save monthly record,
   inspect persisted score/advice/dashboard, logout/login, and verify persistence.
   Test business switching, cross-business IDs, and Owner versus Viewer/Manager
   mutation restrictions. Local mocked transport tests are not real SMTP or Google
   provider acceptance.

## Local regression commands and evidence boundary

```sh
python -m pip install PyYAML
python -m unittest discover -s scripts -p test_production_configuration.py -v
```

On Windows set `FINSIGHT_TEST_SH` to Git Bash's executable for shell tests. These
checks execute domain-validation and AI TLS argument handling and inspect service
topology, secret scope, HBA, ownership wiring, and preserved nonce directives.
They cannot prove image build, `nginx -t`, PostgreSQL role bootstrap, provider
egress, TLS interoperability, or persistence. On 2 October 2026, all 15 tests passed;
standalone Docker Compose 5.5.1 validated the complete configuration including the
migration profile and rejected a missing `APP_DOMAIN`. The execution workstation
had no Docker engine, so clean builds and the production-container acceptance
checks above remained **BLOCKED**. Real SMTP delivery and Google Vision remained
**EXTERNAL-ACCEPTANCE-PENDING** pending credentials/services.

Gitleaks 8.30.1 scanned a snapshot of 547 current tracked/new non-ignored source
files on 2 October 2026 with redaction enabled: exit 0, no findings. This was a
current-source scan, not a full Git-history scan or a guarantee that every secret
pattern is detectable. No source contents were uploaded to a scanning service.

## Backup and verified staging recovery

Follow [the recovery runbook](../scripts/backup/README.md) for encrypted database
and keyring backups and a verified staging restore. Use the production container
name/ID explicitly, supply the password by secret file, and use the canonical DB
hostname with `PGSSLMODE=verify-full` and
`PGSSLROOTCERT=/etc/finsight/certs/postgres-ca.crt` for the backup process inside
the container. That CA is mounted in PostgreSQL for the Docker-exec backup path.
DBA restore connects to the local Unix socket, not network DBA access.

Restore creates a new staging database and never automatically cuts over the live
database. Keep the original database, volume, application keys, and backup artifacts
until a separately reviewed maintenance/cutover and rollback plan is complete.
Never run `down -v` against real data to test fresh startup or recovery.

## Upstream behavior references

- [Official PostgreSQL image entrypoint](https://github.com/docker-library/postgres/blob/master/17/bookworm/docker-entrypoint.sh)
  supplies `_FILE` initialization and the socket-only bootstrap server.
- [Official Nginx template hook](https://github.com/nginx/docker-nginx/blob/master/entrypoint/20-envsubst-on-templates.sh)
  supports the restricted environment substitution filter.
- [Official Nginx image tags](https://github.com/docker-library/official-images/blob/master/library/nginx)
  list the maintained stable Alpine tag used by the edge image.
- [gRPC HTTP proxy mapping](https://grpc.github.io/grpc/core/md_doc_core_default_http_proxy_mapper.html)
  documents the `grpc_proxy` CONNECT transport.
- [Squid ACL configuration](https://www.squid-cache.org/Doc/config/acl/)
  defines the destination, method, port, and source restrictions used here.
