#!/usr/bin/env python3
"""Restore a trusted owner backup into a NEW staging database; never cut over live.

Only a database created by this invocation is eligible for cleanup. No decrypted
SQL or recovered keys are written to disk. Use the matching application checkout:
every Flyway version, script and checksum must match that checkout.
"""

import argparse
import base64
import json
import os
import re
import subprocess
import sys
import uuid
import zlib
from pathlib import Path
from typing import Dict, Optional, Tuple

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from backup.crypto_age import decrypt_stream, decrypt_bytes, parse_identity
from backup.keyring_validation import validate_keyring

IDENTIFIER = re.compile(r"[a-z_][a-z0-9_]{0,62}\Z")
MIGRATIONS = Path(__file__).resolve().parents[2] / "backend/src/main/resources/db/migration"
REQUIRED_TABLES = frozenset({
    "flyway_schema_history", "app_users", "businesses", "business_memberships",
    "business_profiles", "monthly_records", "uploaded_documents", "score_results",
    "insights", "recommendations", "whatsapp_deliveries", "security_audit_events",
    "password_reset_tokens", "spring_session", "spring_session_attributes",
    "user_mfa", "user_mfa_recovery_codes",
})
REQUIRED_TRIGGERS = frozenset({
    ("app_users", "trg_protect_platform_role"),
    ("security_audit_events", "trg_audit_no_truncate"),
    ("security_audit_events", "trg_audit_no_update_delete"),
})
ENVELOPE = re.compile(r"enc:v1:([A-Za-z0-9_-]{1,32}):([A-Za-z0-9+/=]+)\Z")
ENCRYPTED_FIELDS = """
    SELECT 'app_users.full_name' AS field, full_name AS ciphertext,
           'FinSight|AppUser|fullName' AS aad FROM public.app_users
    UNION ALL SELECT 'business_profiles.whatsapp_number', whatsapp_number,
           'FinSight|BusinessProfile|whatsappNumber' FROM public.business_profiles
    UNION ALL SELECT 'uploaded_documents.original_filename', original_filename,
           'FinSight|UploadedDocument|originalFilename' FROM public.uploaded_documents
    UNION ALL SELECT 'whatsapp_deliveries.destination_number', destination_number,
           'FinSight|WhatsAppDelivery|destinationNumber' FROM public.whatsapp_deliveries
    UNION ALL SELECT 'user_mfa.totp_secret', totp_secret,
           'FinSight|UserMfa|totpSecret|' || user_id::text FROM public.user_mfa
"""


class RestoreError(RuntimeError):
    """A mandatory restoration gate failed; the result is never verified."""


def validate_identifier(value: str) -> str:
    if not isinstance(value, str) or not IDENTIFIER.fullmatch(value):
        raise ValueError("Database/role identifiers must match [a-z_][a-z0-9_]{0,62}")
    return value


def require_password(password: Optional[str], file: Optional[str] = None,
                     env_name: str = "DBA_DB_PASSWORD") -> str:
    if password is None and file:
        password = Path(file).read_text(encoding="utf-8").rstrip("\r\n")
    if password is None:
        password = os.environ.get(env_name)
    if not password or not password.strip():
        raise ValueError(f"A database password is required via CLI, secret file or {env_name}")
    return password


def psql_command(container, host, port, dbname, user, password):
    env = os.environ.copy()
    env["PGPASSWORD"] = require_password(password)
    if not container or user != "finsight_dba":
        env = verified_tls_env(env)
    if container:
        # Pass the variable by name: credentials must not appear in process arguments.
        command = ["docker", "exec", "-i", "-e", "PGPASSWORD"]
        for setting in ("PGSSLMODE", "PGSSLROOTCERT", "PGSSLCERT", "PGSSLKEY"):
            if setting in env:
                command += ["-e", setting]
        command += [container, "psql"]
        # Production HBA permits the DBA on the local socket only.
        if user != "finsight_dba":
            command += ["-h", host, "-p", str(port)]
    else:
        command = ["psql", "-h", host, "-p", str(port)]
    command += ["-X", "-w", "-U", user, "-d", dbname, "-v", "ON_ERROR_STOP=1"]
    return command, env


def verified_tls_env(env):
    """Require certificate and hostname validation for every TCP backup connection."""
    env = env.copy()
    env.setdefault("PGSSLMODE", "verify-full")
    if env["PGSSLMODE"] != "verify-full":
        raise ValueError("TCP recovery/backup connections require PGSSLMODE=verify-full")
    return env


def execute_sql(container: Optional[str], host: str, port: int, dbname: str,
                user: str, password: str, sql: str) -> Tuple[int, str, str]:
    command, env = psql_command(container, host, port, dbname, user, password)
    result = subprocess.run(command + ["-c", sql], capture_output=True, text=True, env=env)
    return result.returncode, result.stdout, result.stderr


def execute_sql_query(container: Optional[str], host: str, port: int, dbname: str,
                      user: str, password: str, sql: str) -> str:
    command, env = psql_command(container, host, port, dbname, user, password)
    result = subprocess.run(command + ["-t", "-A", "-c", sql], capture_output=True, text=True, env=env)
    if result.returncode:
        # Driver diagnostics may contain data values: never print them or credentials.
        raise RestoreError("A mandatory PostgreSQL validation query failed")
    return result.stdout.strip()


def expected_migrations():
    expected = []
    for migration in MIGRATIONS.glob("V*__*.sql"):
        version = migration.name.split("__", 1)[0][1:].replace("_", ".")
        # Flyway CRC32 ignores line endings and the optional UTF-8 BOM.
        body = "".join(migration.read_text(encoding="utf-8-sig").splitlines())
        checksum = zlib.crc32(body.encode("utf-8"))
        if checksum >= 2 ** 31:
            checksum -= 2 ** 32
        expected.append({"version": version, "script": migration.name,
                         "checksum": checksum, "type": "SQL", "success": True})
    if not expected:
        raise RestoreError("Matching application migration sources are required for validation")
    return sorted(expected, key=lambda row: tuple(int(n) for n in row["version"].split(".")))


def validate_staging(query):
    tables = set(json.loads(query("""SELECT COALESCE(json_agg(tablename), '[]'::json)
        FROM pg_tables WHERE schemaname = 'public';""")))
    if not REQUIRED_TABLES.issubset(tables):
        raise RestoreError("Required restored domain/security tables are missing")
    expected = expected_migrations()
    actual = json.loads(query("""SELECT COALESCE(json_agg(h ORDER BY installed_rank), '[]'::json)
        FROM (SELECT installed_rank, version, script, checksum, type, success
              FROM public.flyway_schema_history) h;"""))
    actual = [{key: row.get(key) for key in expected[0]} for row in actual]
    if actual != expected:
        raise RestoreError("Flyway history does not match current migration versions/scripts/checksums")
    triggers = json.loads(query("""SELECT COALESCE(json_agg(json_build_array(c.relname, t.tgname)), '[]'::json)
        FROM pg_trigger t JOIN pg_class c ON c.oid = t.tgrelid
        JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'public' AND NOT t.tgisinternal AND t.tgenabled IN ('O', 'A');"""))
    if not REQUIRED_TRIGGERS.issubset({tuple(t) for t in triggers}):
        raise RestoreError("Required security triggers are missing or disabled on their expected tables")
    counts = json.loads(query("""SELECT json_build_object(
        'spring_sessions', (SELECT count(*) FROM public.spring_session),
        'spring_session_attributes', (SELECT count(*) FROM public.spring_session_attributes),
        'password_reset_tokens', (SELECT count(*) FROM public.password_reset_tokens),
        'pending_mfa', (SELECT count(*) FROM public.user_mfa WHERE status = 'PENDING'));"""))
    if set(counts) != {"spring_sessions", "spring_session_attributes", "password_reset_tokens", "pending_mfa"} or any(
            type(value) is not int or value != 0 for value in counts.values()):
        raise RestoreError("Ephemeral authentication state was not completely purged")
    domain = json.loads(query("""SELECT json_build_object(
        'app_users', (SELECT count(*) FROM public.app_users),
        'businesses', (SELECT count(*) FROM public.businesses),
        'enabled_mfa', (SELECT count(*) FROM public.user_mfa WHERE status = 'ENABLED'));"""))
    return {"flyway_migrations_applied": len(expected), "latest_flyway_version": expected[-1]["version"],
            "domain_records": domain, "purged_records": counts,
            "verified_triggers": sorted(name for table, name in REQUIRED_TRIGGERS)}


def verify_keyring(keyring_file, identity, query):
    try:
        bundle = json.loads(decrypt_bytes(Path(keyring_file).read_bytes(), identity).decode("utf-8"))
        active_id, keys = validate_keyring(bundle)
    except Exception as error:
        raise RestoreError("Requested keyring could not be decrypted, parsed or validated") from error
    # Every retained ID and envelope shape is checked, not merely the active key.
    malformed = int(query(f"""WITH encrypted AS ({ENCRYPTED_FIELDS}) SELECT count(*) FROM encrypted
        WHERE ciphertext LIKE 'enc:%' AND ciphertext !~ '^enc:v1:[A-Za-z0-9_-]{{1,32}}:[A-Za-z0-9+/=]+$';"""))
    if malformed:
        raise RestoreError("Restored data contains malformed/unsupported encryption envelopes")
    samples = json.loads(query(f"""WITH encrypted AS ({ENCRYPTED_FIELDS}), samples AS (
        SELECT DISTINCT ON (field, split_part(ciphertext, ':', 3)) field, ciphertext, aad
        FROM encrypted WHERE ciphertext LIKE 'enc:%'
        ORDER BY field, split_part(ciphertext, ':', 3))
        SELECT COALESCE(json_agg(samples), '[]'::json) FROM samples;"""))
    try:
        from cryptography.hazmat.primitives.ciphers.aead import AESGCM
        for sample in samples:
            match = ENVELOPE.fullmatch(sample["ciphertext"])
            if not match or match.group(1) not in keys:
                raise ValueError("Missing historical encryption key")
            payload = base64.b64decode(match.group(2), validate=True)
            if len(payload) < 28:
                raise ValueError("Invalid encryption payload length")
            # Same 12-byte IV, 16-byte tag and field/row AAD as the Java cipher.
            AESGCM(keys[match.group(1)]).decrypt(payload[:12], payload[12:], sample["aad"].encode("utf-8"))
    except Exception as error:
        raise RestoreError("Recovered keys could not authenticate representative restored ciphertext") from error
    return {"status": "VERIFIED", "active_key_id": active_id,
            "key_ids": sorted(keys), "retained_key_count": len(keys),
            "ciphertext_samples_verified": len(samples)}


class CountingWriter:
    def __init__(self, stream):
        self.stream = stream
        self.count = 0

    def write(self, data):
        size = self.stream.write(data)
        self.count += size
        return size

    def flush(self):
        self.stream.flush()

    def fileno(self):
        return self.stream.fileno()


def run_restore(backup_file: Path, identity: str, target_dbname: Optional[str] = None,
                container: Optional[str] = "sme-health-postgres", host: str = "127.0.0.1",
                port: int = 5432, admin_user: str = "finsight_dba",
                admin_password: Optional[str] = None, keyring_file: Optional[Path] = None,
                maintenance_db: str = "postgres") -> Dict[str, object]:
    backup_file = Path(backup_file)
    if not backup_file.is_file():
        raise FileNotFoundError("Encrypted database backup file does not exist")
    if keyring_file is not None and not Path(keyring_file).is_file():
        raise FileNotFoundError("Requested encrypted keyring file does not exist")
    parse_identity(identity)
    password = require_password(admin_password)
    staging = validate_identifier(target_dbname or f"finsight_restore_{uuid.uuid4().hex}")
    validate_identifier(admin_user)
    validate_identifier(maintenance_db)
    if staging in {maintenance_db, "postgres", "template0", "template1"}:
        raise ValueError("A separate fresh staging database is required")
    expected_migrations()
    connection = (container, host, port)

    def execute(database, sql):
        result, _, _ = execute_sql(*connection, database, admin_user, password, sql)
        if result:
            raise RestoreError("PostgreSQL restore/provisioning operation failed")

    def query(sql):
        return execute_sql_query(*connection, staging, admin_user, password, sql)

    exists = execute_sql_query(*connection, maintenance_db, admin_user, password,
                              f"SELECT 1 FROM pg_database WHERE datname = '{staging}';")
    if exists:
        raise RestoreError("Staging database already exists; refusing to overwrite it")
    created = False
    process = None
    try:
        # CREATE is atomic: on a concurrent name collision we never clean up that DB.
        execute(maintenance_db, f'CREATE DATABASE "{staging}" OWNER "{admin_user}" TEMPLATE template0;')
        created = True
        # pg_dump includes CREATE SCHEMA public. This is ONLY our newly created DB.
        execute(staging, 'DROP SCHEMA public;')
        command, env = psql_command(container, host, port, staging, admin_user, password)
        # OS sinks avoid output-pipe deadlocks and sensitive diagnostic logging.
        # psql requires -c or -f with --single-transaction; explicitly read stdin.
        process = subprocess.Popen(command + ["--single-transaction", "-f", "-"], stdin=subprocess.PIPE,
                                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, env=env)
        writer = CountingWriter(process.stdin)
        with backup_file.open("rb") as source:
            decrypt_stream(source, writer, identity)
        writer.flush()
        process.stdin.close()
        process.stdin = None
        process.wait()
        if process.returncode:
            raise RestoreError("Decrypted SQL could not be completely restored")
        execute(staging, """BEGIN;
            DELETE FROM public.spring_session_attributes;
            DELETE FROM public.spring_session;
            DELETE FROM public.password_reset_tokens;
            DELETE FROM public.user_mfa WHERE status = 'PENDING';
            COMMIT;""")
        verification = validate_staging(query)
        keyring = verify_keyring(keyring_file, identity, query) if keyring_file is not None else None
        return {"status": "VERIFIED_STAGING_RESTORE", "staging_database": staging,
                "target_database": staging, "live_cutover_performed": False,
                "bytes_decrypted": writer.count, **verification, "keyring_recovery": keyring}
    except BaseException as error:
        if process is not None and process.poll() is None:
            process.kill()
            process.wait()
        if process is not None and process.stdin is not None:
            try:
                process.stdin.close()
            except (OSError, ValueError):
                pass
        if created:
            try:
                execute(maintenance_db, f'DROP DATABASE "{staging}" WITH (FORCE);')
            except Exception as cleanup_error:
                raise RestoreError(f"Restore failed; cleanup of staging database {staging} also failed. "
                                   "Live database was not selected for restore or cleanup.") from cleanup_error
        raise error


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--backup-file", required=True)
    parser.add_argument("--identity")
    parser.add_argument("--identity-file")
    parser.add_argument("--staging-db", "--target-db", dest="target_db", help="NEW database only; default is a random name")
    parser.add_argument("--container", default="sme-health-postgres")
    parser.add_argument("--no-container", action="store_true")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=5432)
    parser.add_argument("--admin-user", default="finsight_dba")
    parser.add_argument("--admin-password")
    parser.add_argument("--admin-password-file", help="Docker secret or other local secret file")
    parser.add_argument("--maintenance-db", default="postgres")
    parser.add_argument("--keyring-file")
    args = parser.parse_args()
    try:
        identity = args.identity
        if not identity and args.identity_file:
            identity = Path(args.identity_file).read_text(encoding="utf-8").strip()
        identity = identity or os.environ.get("FINSIGHT_RESTORE_IDENTITY", "")
        password_file = args.admin_password_file or os.environ.get("DBA_DB_PASSWORD_FILE")
        password = require_password(args.admin_password, password_file)
        result = run_restore(Path(args.backup_file), identity, args.target_db,
                             None if args.no_container else args.container, args.host, args.port,
                             args.admin_user, password, Path(args.keyring_file) if args.keyring_file else None,
                             args.maintenance_db)
        print(json.dumps(result, indent=2))
        return 0
    except Exception as error:
        # No driver/decryption exception details: these can expose secrets or row data.
        print(json.dumps({"status": "FAILED", "error_type": type(error).__name__,
                          "message": str(error) if isinstance(error, RestoreError) else "Restore input or encrypted artifact validation failed"}), file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
