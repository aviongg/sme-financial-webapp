#!/usr/bin/env python3
"""Real PostgreSQL recovery acceptance against a quiescent disposable source DB.

Requires current migrations, representative encrypted rows and their real keyring.
Source data is fingerprinted after every failure/success. Only fresh, random
staging databases created by this run are cleaned up. No credentials are printed.
Run the fixture suite separately with unittest; this drill never falls back to mocks.
"""

import argparse
import hashlib
import json
import os
import sys
import tempfile
import uuid
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from backup.crypto_age import generate_keypair, decrypt_bytes, encrypt_bytes, AgeCryptoError
from backup.backup_database import run_backup
from backup.backup_keyring import run_keyring_backup
from backup.restore_database import run_restore, execute_sql, execute_sql_query, require_password, validate_identifier, RestoreError


def source_fingerprint(query):
    """Fingerprint every public table's data and relevant catalog state; never log rows."""
    tables = json.loads(query("SELECT COALESCE(json_agg(tablename ORDER BY tablename), '[]'::json) FROM pg_tables WHERE schemaname='public';"))
    hashes = {}
    for table in tables:
        validate_identifier(table)
        hashes[table] = query(f'''SELECT md5(COALESCE(string_agg(row_to_json(t)::text,
            E'\\n' ORDER BY row_to_json(t)::text), '')) FROM public."{table}" t;''')
    hashes["catalog"] = query("""SELECT md5(COALESCE(string_agg(row_to_json(c)::text, '' ORDER BY row_to_json(c)::text), ''))
        FROM (SELECT table_name, column_name, data_type, is_nullable, column_default
              FROM information_schema.columns WHERE table_schema='public') c;""")
    hashes["triggers"] = query("""SELECT md5(COALESCE(string_agg(pg_get_triggerdef(t.oid) || t.tgenabled,
        '' ORDER BY t.tgname, c.relname), '')) FROM pg_trigger t JOIN pg_class c ON c.oid=t.tgrelid
        JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public';""")
    return hashlib.sha256(json.dumps(hashes, sort_keys=True).encode()).hexdigest()


def run_drill(args):
    source = validate_identifier(args.source_db)
    container = None if args.no_container else args.container
    admin = validate_identifier(args.admin_user)
    backup_user = validate_identifier(args.backup_user)
    dba_password = require_password(None, args.admin_password_file or os.environ.get("DBA_DB_PASSWORD_FILE"))
    backup_password = require_password(None, args.backup_password_file or os.environ.get("BACKUP_DB_PASSWORD_FILE"), "BACKUP_DB_PASSWORD")
    connection = (container, args.host, args.port)

    def query(database, sql):
        return execute_sql_query(*connection, database, admin, dba_password, sql)

    def original_unchanged():
        if source_fingerprint(lambda sql: query(source, sql)) != original:
            raise RuntimeError("Source database fingerprint changed during the drill")

    def assert_absent(name):
        if query("postgres", f"SELECT 1 FROM pg_database WHERE datname='{name}';"):
            raise RuntimeError("Failed recovery left its staging database behind")

    original = source_fingerprint(lambda sql: query(source, sql))
    recipient, identity = generate_keypair()
    results = {}
    owned = set()
    with tempfile.TemporaryDirectory(prefix="finsight_dr_") as temporary:
        folder = Path(temporary)
        backup = run_backup(recipient, folder / "database", container=container, host=args.host,
                            port=args.port, dbname=source, user=backup_user, password=backup_password)
        backup_path = Path(backup["artifact_path"])
        keyring = run_keyring_backup(recipient, folder / "keyring", secrets_dir=Path(args.secrets_dir))
        keyring_path = Path(keyring["artifact_path"])
        # Only this deliberately small drill decrypts the dump in memory to generate
        # adversarial SQL fixtures. The production restore streams without buffering.
        plaintext = decrypt_bytes(backup_path.read_bytes(), identity)

        def attempt(path=backup_path, owner_identity=identity, bundle=keyring_path, name=None):
            name = name or "finsight_dr_" + uuid.uuid4().hex
            result = run_restore(path, owner_identity, name, container=container, host=args.host,
                                 port=args.port, admin_user=admin, admin_password=dba_password,
                                 keyring_file=bundle)
            owned.add(name)
            return result

        def expect_failure(label, path=backup_path, owner_identity=identity,
                           bundle=keyring_path, error_type=RestoreError, message=None):
            name = "finsight_dr_" + uuid.uuid4().hex
            try:
                attempt(path, owner_identity, bundle, name)
            except error_type as error:
                if message and message not in str(error):
                    raise RuntimeError(f"{label}: failed at a different gate than expected") from error
            else:
                raise RuntimeError(f"{label}: invalid restore unexpectedly succeeded")
            assert_absent(name)
            original_unchanged()
            results[label] = "PASS"

        def sql_fixture(label, suffix):
            path = folder / (label + ".age")
            path.write_bytes(encrypt_bytes(plaintext + b"\n" + suffix.encode(), recipient))
            return path

        try:
            _, wrong_identity = generate_keypair()
            expect_failure("wrong_private_key", owner_identity=wrong_identity, error_type=AgeCryptoError)
            corrupted = bytearray(backup_path.read_bytes())
            corrupted[-1] ^= 1
            corrupt_path = folder / "corrupted.age"
            corrupt_path.write_bytes(corrupted)
            expect_failure("corrupted_backup", path=corrupt_path, error_type=AgeCryptoError)
            expect_failure("partial_sql_error", sql_fixture("partial_sql_error", "CREATE TABLE public.dr_partial (id int); INSERT INTO public.dr_partial VALUES (1); SELECT 1 / 0;"),
                           message="Decrypted SQL could not be completely restored")
            expect_failure("missing_security_trigger", sql_fixture("missing_trigger", "DROP TRIGGER trg_protect_platform_role ON public.app_users;"),
                           message="Required security triggers")
            expect_failure("unexpected_flyway_history", sql_fixture("bad_history", "UPDATE public.flyway_schema_history SET checksum=0 WHERE version='1';"),
                           message="Flyway history")
            blocked_purge = """
                INSERT INTO public.spring_session(primary_id, session_id, creation_time, last_access_time,
                    max_inactive_interval, expiry_time) VALUES (gen_random_uuid()::text, gen_random_uuid()::text, 0, 0, 300, 300);
                CREATE FUNCTION public.dr_block_purge() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RETURN NULL; END $$;
                CREATE TRIGGER dr_block_purge BEFORE DELETE ON public.spring_session FOR EACH ROW EXECUTE FUNCTION public.dr_block_purge();
            """
            expect_failure("nonzero_ephemeral_state", sql_fixture("blocked_purge", blocked_purge),
                           message="Ephemeral authentication state")
            bad_keyring = folder / "bad_keyring.age"
            bad_keyring.write_bytes(b"corrupt keyring")
            expect_failure("corrupted_keyring", bundle=bad_keyring, message="Requested keyring")
            other_recipient, _ = generate_keypair()
            wrong_keyring = folder / "wrong_keyring.age"
            wrong_keyring.write_bytes(encrypt_bytes(b"{}", other_recipient))
            expect_failure("wrong_keyring_recipient", bundle=wrong_keyring, message="Requested keyring")
            try:
                attempt(name=source)
            except RestoreError as error:
                if "Staging database already exists" not in str(error):
                    raise
            else:
                # run_restore refusing an existing source is a mandatory safety gate.
                raise RuntimeError("Existing source database was not refused")
            original_unchanged()
            results["existing_database_refused"] = "PASS"

            verified = attempt()
            if verified["status"] != "VERIFIED_STAGING_RESTORE" or verified["keyring_recovery"]["status"] != "VERIFIED":
                raise RuntimeError("Valid backup was not fully verified")
            if verified["keyring_recovery"]["ciphertext_samples_verified"] < 1:
                raise RuntimeError("Seed representative encrypted source data before running the acceptance drill")
            original_unchanged()
            results["valid_restore_and_keyring"] = "PASS"
            results["verification"] = verified

            # Read succeeds; DML uses zero rows. Unexpected DDL is rolled back, and
            # only insufficient_privilege counts as successful denial evidence.
            probe = "dr_role_probe_" + uuid.uuid4().hex
            role_checks = f"""BEGIN;
                SELECT count(*) FROM public.app_users;
                DO $$ BEGIN
                    BEGIN
                        DELETE FROM public.app_users WHERE false;
                        RAISE EXCEPTION 'Backup role unexpectedly has DELETE privilege';
                    EXCEPTION WHEN insufficient_privilege THEN NULL; END;
                    BEGIN
                        UPDATE public.app_users SET updated_at=updated_at WHERE false;
                        RAISE EXCEPTION 'Backup role unexpectedly has UPDATE privilege';
                    EXCEPTION WHEN insufficient_privilege THEN NULL; END;
                    BEGIN
                        INSERT INTO public.app_users SELECT * FROM public.app_users WHERE false;
                        RAISE EXCEPTION 'Backup role unexpectedly has INSERT privilege';
                    EXCEPTION WHEN insufficient_privilege THEN NULL; END;
                    BEGIN
                        CREATE TABLE public.{probe}(id int);
                        RAISE EXCEPTION 'Backup role unexpectedly has CREATE privilege';
                    EXCEPTION WHEN insufficient_privilege THEN NULL; END;
                END $$;
                ROLLBACK;"""
            rc, _, _ = execute_sql(*connection, source, backup_user, backup_password, role_checks)
            if rc:
                raise RuntimeError("Backup role SELECT/DML/DDL privilege acceptance failed")
            original_unchanged()
            results["backup_role_least_privilege"] = "PASS"
        finally:
            cleanup_errors = []
            for name in owned:
                # Names enter owned only when our new staging restore succeeds.
                if name == source:
                    cleanup_errors.append("Source DB was incorrectly reported as new staging")
                    continue
                rc, _, _ = execute_sql(*connection, "postgres", admin, dba_password,
                                       f'DROP DATABASE "{validate_identifier(name)}" WITH (FORCE);')
                if rc:
                    cleanup_errors.append(name)
            if cleanup_errors:
                raise RuntimeError("Drill staging cleanup failed; manual operator cleanup is required")
    return {"status": "REAL_POSTGRES_DRILL_PASSED", "source_unchanged": True,
            "staging_cleaned": True, "checks": results}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-db", required=True, help="Quiescent disposable DB with current migrations and encrypted fixture records")
    parser.add_argument("--secrets-dir", required=True, help="Matching application ConfigTree key directory")
    parser.add_argument("--container", default="finsight-postgres-prod")
    parser.add_argument("--no-container", action="store_true")
    parser.add_argument("--host", default="postgres.finsight.internal")
    parser.add_argument("--port", type=int, default=5432)
    parser.add_argument("--admin-user", default="finsight_dba")
    parser.add_argument("--backup-user", default="finsight_backup")
    parser.add_argument("--admin-password-file")
    parser.add_argument("--backup-password-file")
    args = parser.parse_args()
    try:
        print(json.dumps(run_drill(args), indent=2))
        return 0
    except Exception as error:
        print(json.dumps({"status": "FAILED", "error_type": type(error).__name__,
                          "message": "Real recovery drill did not pass; inspect configuration and mandatory acceptance gates"}), file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
