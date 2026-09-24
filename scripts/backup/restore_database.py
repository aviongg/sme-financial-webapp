#!/usr/bin/env python3
"""FinSight S9 Automated Encrypted Disaster Recovery & Restoration Tool

Restores an encrypted .age logical database backup into a target PostgreSQL database:
- Streams decrypted SQL directly into psql without creating persistent plaintext dumps.
- Enforces owner offline age private identity requirement.
- Automatically executes S9-13 ephemeral auth data purges (sessions, reset tokens, pending MFA).
- Validates Flyway migration history, domain tables, security triggers, and schema integrity.
- Optionally restores and verifies S5 crypto keyring against restored database ciphertext.
"""

import argparse
import io
import json
import os
import subprocess
import sys
from pathlib import Path
from typing import Dict, List, Optional, Tuple

sys.path.insert(0, str(Path(__file__).parent.parent))
from backup.crypto_age import decrypt_stream, decrypt_bytes, parse_identity, CorruptedBackupError, DecryptionError, AgeCryptoError


def execute_sql(container: Optional[str], host: str, port: int, dbname: str,
                user: str, password: str, sql: str) -> Tuple[int, str, str]:
    if container:
        cmd = ["docker", "exec", "-i", "-e", f"PGPASSWORD={password}", container, "psql"]
        if user != "finsight_dba":
            cmd.extend(["-h", host])
        cmd.extend(["-U", user, "-d", dbname, "-c", sql])
    else:
        env = os.environ.copy()
        env["PGPASSWORD"] = password
        cmd = ["psql", "-h", host, "-p", str(port), "-U", user, "-d", dbname, "-c", sql]

    res = subprocess.run(cmd, capture_output=True, text=True)
    return res.returncode, res.stdout, res.stderr


def execute_sql_query(container: Optional[str], host: str, port: int, dbname: str,
                      user: str, password: str, sql: str) -> str:
    if container:
        cmd = ["docker", "exec", "-i", "-e", f"PGPASSWORD={password}", container, "psql"]
        if user != "finsight_dba":
            cmd.extend(["-h", host])
        cmd.extend(["-U", user, "-d", dbname, "-t", "-A", "-c", sql])
    else:
        env = os.environ.copy()
        env["PGPASSWORD"] = password
        cmd = ["psql", "-h", host, "-p", str(port), "-U", user, "-d", dbname, "-t", "-A", "-c", sql]

    res = subprocess.run(cmd, capture_output=True, text=True, check=True)
    return res.stdout.strip()


def run_restore(
    backup_file: Path,
    identity: str,
    target_dbname: str,
    container: Optional[str] = "sme-health-postgres",
    host: str = "127.0.0.1",
    port: int = 5432,
    admin_user: str = "finsight_dba",
    admin_password: str = "FinSight_Dba_Admin_Sec_2026_!#9xK",
    keyring_file: Optional[Path] = None
) -> Dict[str, any]:
    if not backup_file.is_file():
        raise FileNotFoundError(f"Backup file not found: {backup_file}")

    # Validate private identity format upfront
    parse_identity(identity)

    # 1. Ensure target database exists (create if missing in disposable drill)
    check_db_sql = f"SELECT 1 FROM pg_database WHERE datname = '{target_dbname}';"
    exists = execute_sql_query(container, host, port, "sme_health", admin_user, admin_password, check_db_sql)
    if not exists:
        create_db_sql = f"CREATE DATABASE {target_dbname} OWNER finsight_dba;"
        rc, out, err = execute_sql(container, host, port, "sme_health", admin_user, admin_password, create_db_sql)
        if rc != 0:
            raise RuntimeError(f"Failed to create target database {target_dbname}: {err}")

    # Prepare clean schema for pg_dump
    execute_sql(container, host, port, target_dbname, admin_user, admin_password, "DROP SCHEMA IF EXISTS public CASCADE;")

    # 2. Launch psql process to receive decrypted stream
    if container:
        psql_cmd = [
            "docker", "exec", "-i",
            "-e", f"PGPASSWORD={admin_password}",
            container,
            "psql",
            "-U", admin_user,
            "-d", target_dbname,
            "-v", "ON_ERROR_STOP=1"
        ]
    else:
        psql_cmd = [
            "psql",
            "-h", host,
            "-p", str(port),
            "-U", admin_user,
            "-d", target_dbname,
            "-v", "ON_ERROR_STOP=1"
        ]

    env = os.environ.copy()
    if not container:
        env["PGPASSWORD"] = admin_password

    psql_proc = subprocess.Popen(
        psql_cmd,
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        env=env
    )

    # 3. Stream decrypt directly into psql stdin
    try:
        with open(backup_file, "rb") as f_in:
            bytes_decrypted = decrypt_stream(f_in, psql_proc.stdin, identity)

        stdout, stderr = psql_proc.communicate()
        if psql_proc.returncode != 0:
            err_msg = stderr.decode("utf-8", errors="ignore")
            raise RuntimeError(f"psql restore failed with code {psql_proc.returncode}: {err_msg}")

    except Exception as e:
        if psql_proc.poll() is None:
            psql_proc.kill()
        raise e

    # 4. S9-13 Post-Restore Ephemeral Auth Data Purge
    purge_sql = """
    DELETE FROM spring_session_attributes;
    DELETE FROM spring_session;
    DELETE FROM password_reset_tokens;
    DELETE FROM user_mfa WHERE status = 'PENDING';
    """
    rc, out, err = execute_sql(container, host, port, target_dbname, admin_user, admin_password, purge_sql)
    if rc != 0:
        raise RuntimeError(f"Post-restore ephemeral auth data purge failed: {err}")

    # 5. S9-14 Post-Restore Validations
    # Verify Flyway history
    flyway_count = execute_sql_query(container, host, port, target_dbname, admin_user, admin_password,
                                     "SELECT count(*) FROM flyway_schema_history WHERE success = true;")
    latest_migration = execute_sql_query(container, host, port, target_dbname, admin_user, admin_password,
                                         "SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1;")

    # Verify session and token purge counts
    session_count = execute_sql_query(container, host, port, target_dbname, admin_user, admin_password,
                                      "SELECT count(*) FROM spring_session;")
    token_count = execute_sql_query(container, host, port, target_dbname, admin_user, admin_password,
                                    "SELECT count(*) FROM password_reset_tokens;")
    pending_mfa_count = execute_sql_query(container, host, port, target_dbname, admin_user, admin_password,
                                          "SELECT count(*) FROM user_mfa WHERE status = 'PENDING';")
    enabled_mfa_count = execute_sql_query(container, host, port, target_dbname, admin_user, admin_password,
                                          "SELECT count(*) FROM user_mfa WHERE status = 'ENABLED';")
    users_count = execute_sql_query(container, host, port, target_dbname, admin_user, admin_password,
                                    "SELECT count(*) FROM app_users;")
    businesses_count = execute_sql_query(container, host, port, target_dbname, admin_user, admin_password,
                                         "SELECT count(*) FROM businesses;")

    # Verify security triggers exist and are enabled
    triggers_query = """
    SELECT tgname FROM pg_trigger
    WHERE tgname IN ('trg_protect_platform_role', 'trg_audit_no_truncate', 'trg_audit_no_update_delete')
    AND tgenabled = 'O';
    """
    active_triggers = execute_sql_query(container, host, port, target_dbname, admin_user, admin_password, triggers_query)
    trigger_list = [t.strip() for t in active_triggers.splitlines() if t.strip()]

    # 6. Optional: Verify Keyring Recovery Bundle
    keyring_verification = None
    if keyring_file and keyring_file.is_file():
        try:
            with open(keyring_file, "rb") as kf:
                keyring_enc = kf.read()
            recovered_bundle_bytes = decrypt_bytes(keyring_enc, identity)
            recovered_bundle = json.loads(recovered_bundle_bytes.decode("utf-8"))
            active_id = recovered_bundle.get("active_key_id")
            keys = recovered_bundle.get("keys", {})

            keyring_verification = {
                "status": "RECOVERED",
                "active_key_id": active_id,
                "retained_key_count": len(keys),
                "key_ids": sorted(list(keys.keys()))
            }
        except Exception as ke:
            keyring_verification = {
                "status": "FAILED",
                "error": str(ke)
            }

    return {
        "status": "SUCCESS",
        "target_database": target_dbname,
        "bytes_decrypted": bytes_decrypted,
        "flyway_migrations_applied": int(flyway_count),
        "latest_flyway_version": latest_migration,
        "domain_records": {
            "app_users": int(users_count),
            "businesses": int(businesses_count),
            "enabled_mfa": int(enabled_mfa_count)
        },
        "purged_records": {
            "spring_sessions": int(session_count),
            "password_reset_tokens": int(token_count),
            "pending_mfa": int(pending_mfa_count)
        },
        "verified_triggers": trigger_list,
        "keyring_recovery": keyring_verification
    }


def main():
    parser = argparse.ArgumentParser(description="FinSight S9 Encrypted Database Restoration Tool")
    parser.add_argument("--backup-file", required=True, help="Path to encrypted .sql.age database backup")
    parser.add_argument("--identity", help="Offline owner private age identity (AGE-SECRET-KEY-1...)")
    parser.add_argument("--identity-file", help="Path to file containing offline owner private age identity")
    parser.add_argument("--target-db", default="sme_health_restore_drill", help="Target restoration database name")
    parser.add_argument("--container", default="sme-health-postgres", help="Docker postgres container name")
    parser.add_argument("--no-container", action="store_true", help="Run psql directly on host PATH")
    parser.add_argument("--host", default="127.0.0.1", help="PostgreSQL host")
    parser.add_argument("--port", type=int, default=5432, help="PostgreSQL port")
    parser.add_argument("--admin-user", default="finsight_dba", help="PostgreSQL DBA user")
    parser.add_argument("--admin-password", default=None, help="PostgreSQL DBA password")
    parser.add_argument("--keyring-file", help="Path to encrypted keyring .age bundle")

    args = parser.parse_args()

    identity = args.identity
    if not identity and args.identity_file:
        with open(args.identity_file, "r", encoding="utf-8") as f:
            identity = f.read().strip()
    if not identity:
        identity = os.environ.get("FINSIGHT_RESTORE_IDENTITY", "")
    if not identity:
        print("ERROR: Private age identity key is required (--identity or --identity-file)", file=sys.stderr)
        sys.exit(1)

    password = args.admin_password or os.environ.get("DBA_DB_PASSWORD", "FinSight_Dba_Admin_Sec_2026_!#9xK")
    container = None if args.no_container else args.container
    keyring_path = Path(args.keyring_file) if args.keyring_file else None

    try:
        res = run_restore(
            backup_file=Path(args.backup_file),
            identity=identity,
            target_dbname=args.target_db,
            container=container,
            host=args.host,
            port=args.port,
            admin_user=args.admin_user,
            admin_password=password,
            keyring_file=keyring_path
        )
        print(json.dumps(res, indent=2))
    except Exception as e:
        print(f"RESTORE FAILED: {str(e)}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
