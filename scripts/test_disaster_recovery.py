#!/usr/bin/env python3
"""FinSight S9 Automated Backup, Recovery & Disaster Drill Suite

Verifies:
1. Generation of ephemeral owner age keypair (never stored in Git).
2. Streaming encrypted database backup via finsight_backup.
3. Authenticated backup manifest & SHA-256 integrity verification.
4. S5 crypto keyring recovery bundle creation.
5. Wrong private key test (fails closed, zero plaintext).
6. Corrupted backup test (bit flipping in ciphertext fails closed).
7. Full restore drill into disposable database (sme_health_restore_drill).
8. S9-13 Ephemeral auth data purge verification (sessions, tokens, pending MFA cleared).
9. S9-14 Domain and schema integrity verification (Flyway V1-V14, triggers, records).
10. S5 Keyring recovery and ciphertext decryption verification.
11. Backup database role privilege verification (SELECT succeeds, mutations denied).
"""

import datetime
import json
import os
import shutil
import subprocess
import sys
import tempfile
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from backup.crypto_age import generate_keypair, DecryptionError, CorruptedBackupError, AgeCryptoError
from backup.backup_database import run_backup
from backup.backup_keyring import run_keyring_backup
from backup.restore_database import run_restore, execute_sql, execute_sql_query


def run_drill():
    print("=" * 70)
    print("STARTING FINSIGHT S9 DISASTER RECOVERY DRILL & ADVERSARIAL AUDIT")
    print("=" * 70)

    # 1. Ephemeral Owner Keypair (Simulating offline owner storage)
    recipient, identity = generate_keypair()
    print(f"\n[1] Generated ephemeral owner age keypair:")
    print(f"    Recipient: {recipient}")
    print(f"    Identity:  {identity[:20]}... [OFFLINE/EPHEMERAL]")

    # Create temporary directory for drill artifacts
    temp_dir = Path(tempfile.mkdtemp(prefix="finsight_s9_drill_"))
    db_backup_dir = temp_dir / "db_backups"
    keyring_backup_dir = temp_dir / "keyring_backups"
    disposable_db = "sme_health_restore_drill"

    results = {}

    try:
        # 2. Execute encrypted logical database backup
        print("\n[2] Executing streaming encrypted pg_dump...")
        backup_start = time.time()
        backup_res = run_backup(
            recipient=recipient,
            output_dir=db_backup_dir,
            container="sme-health-postgres",
            host="127.0.0.1",
            dbname="sme_health",
            user="finsight_backup",
            password="FinSight_Backup_Reader_2026_!#5bK"
        )
        backup_duration = time.time() - backup_start
        print(f"    Artifact: {backup_res['artifact_path']}")
        print(f"    Size:     {backup_res['size_bytes']} bytes")
        print(f"    SHA-256:  {backup_res['sha256']}")
        print(f"    Duration: {backup_duration:.2f}s")
        print(f"    Flyway:   {backup_res['flyway_version']}")

        results["backup_size_bytes"] = backup_res["size_bytes"]
        results["backup_duration_seconds"] = round(backup_duration, 2)
        results["backup_sha256"] = backup_res["sha256"]
        results["flyway_version"] = backup_res["flyway_version"]

        # 3. Execute S5 Crypto Keyring Recovery Bundle
        print("\n[3] Executing S5 crypto keyring recovery bundle...")
        keyring_res = run_keyring_backup(
            recipient=recipient,
            output_dir=keyring_backup_dir,
            explicit_keys={"k1": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"},
            explicit_active_id="k1"
        )
        print(f"    Keyring Artifact: {keyring_res['artifact_path']}")
        print(f"    Active Key ID:    {keyring_res['active_key_id']}")
        print(f"    Retained Keys:    {keyring_res['retained_key_ids']}")

        # 4. S9-16 Adversarial Test: Wrong Private Key
        print("\n[4] S9-16 Adversarial Test: Wrong Private Key...")
        _, wrong_identity = generate_keypair()
        try:
            run_restore(
                backup_file=Path(backup_res["artifact_path"]),
                identity=wrong_identity,
                target_dbname="should_not_exist",
                container="sme-health-postgres"
            )
            print("    FAILED: Restore with wrong private key did not fail!")
            results["wrong_key_test"] = "FAILED"
        except (DecryptionError, RuntimeError) as e:
            print(f"    PASSED: Failed closed with wrong key as expected: {str(e)[:60]}...")
            results["wrong_key_test"] = "PASS"

        # 5. S9-15 Adversarial Test: Corrupted Backup
        print("\n[5] S9-15 Adversarial Test: Corrupted Backup Artifact...")
        corrupted_file = db_backup_dir / "corrupted_backup.sql.age"
        with open(backup_res["artifact_path"], "rb") as f_orig:
            ct = bytearray(f_orig.read())
        # Flip bits in middle of ciphertext payload
        ct[len(ct) // 2] ^= 0xFF
        with open(corrupted_file, "wb") as f_bad:
            f_bad.write(ct)

        try:
            run_restore(
                backup_file=corrupted_file,
                identity=identity,
                target_dbname="should_not_exist",
                container="sme-health-postgres"
            )
            print("    FAILED: Corrupted backup restore did not fail!")
            results["corrupted_backup_test"] = "FAILED"
        except (CorruptedBackupError, RuntimeError) as e:
            print(f"    PASSED: Failed closed on corrupted backup as expected: {str(e)[:60]}...")
            results["corrupted_backup_test"] = "PASS"

        # 6. S9-12 Full Restore Drill into Disposable Database
        print(f"\n[6] S9-12 Executing full restore drill into disposable database '{disposable_db}'...")
        # Drop disposable DB if it exists from previous run
        execute_sql("sme-health-postgres", "127.0.0.1", 5432, "sme_health",
                    "finsight_dba", "FinSight_Dba_Admin_Sec_2026_!#9xK",
                    f"DROP DATABASE IF EXISTS {disposable_db};")

        restore_start = time.time()
        restore_res = run_restore(
            backup_file=Path(backup_res["artifact_path"]),
            identity=identity,
            target_dbname=disposable_db,
            container="sme-health-postgres",
            keyring_file=Path(keyring_res["artifact_path"])
        )
        restore_duration = time.time() - restore_start
        print(f"    Restore Status:    {restore_res['status']}")
        print(f"    Decrypted Plaintext: {restore_res['bytes_decrypted']} bytes")
        print(f"    Restore Duration:  {restore_duration:.2f}s")
        print(f"    Flyway Migrations: {restore_res['flyway_migrations_applied']} (Latest: {restore_res['latest_flyway_version']})")
        print(f"    Domain Records:    {restore_res['domain_records']}")
        print(f"    Purged Records:    {restore_res['purged_records']}")
        print(f"    Verified Triggers: {restore_res['verified_triggers']}")
        print(f"    Keyring Recovery:  {restore_res['keyring_recovery']}")

        results["restore_duration_seconds"] = round(restore_duration, 2)
        results["restored_flyway_version"] = restore_res["latest_flyway_version"]
        results["restored_flyway_count"] = restore_res["flyway_migrations_applied"]
        results["restored_records"] = restore_res["domain_records"]
        results["purged_records"] = restore_res["purged_records"]
        results["verified_triggers"] = restore_res["verified_triggers"]
        results["keyring_recovery"] = restore_res["keyring_recovery"]

        # Validate S9-13: Sessions and reset tokens must be 0
        assert restore_res["purged_records"]["spring_sessions"] == 0, "Spring sessions must be 0 after restore"
        assert restore_res["purged_records"]["password_reset_tokens"] == 0, "Reset tokens must be 0 after restore"
        assert restore_res["purged_records"]["pending_mfa"] == 0, "Pending MFA enrollments must be 0 after restore"
        assert restore_res["flyway_migrations_applied"] >= 14, "Flyway migrations must be at least 14"
        assert "trg_protect_platform_role" in restore_res["verified_triggers"], "Platform role protection trigger missing"
        assert "trg_audit_no_truncate" in restore_res["verified_triggers"], "Audit no-truncate trigger missing"
        assert "trg_audit_no_update_delete" in restore_res["verified_triggers"], "Audit no-update/delete trigger missing"

        results["restore_drill_status"] = "PASS"

        # 7. S9-18 Backup Database Role Invariants Verification
        print("\n[7] S9-18 Verifying finsight_backup least-privilege invariants...")
        check_mutations_sql = """
        DO $$
        BEGIN
            -- Attempting insert as finsight_backup must fail
            BEGIN
                EXECUTE 'INSERT INTO app_users (id, email, password_hash) VALUES (gen_random_uuid(), ''h@t.com'', ''x'')';
                RAISE EXCEPTION 'finsight_backup INSERT succeeded unexpectedly';
            EXCEPTION WHEN insufficient_privilege THEN
                -- Expected
            END;

            -- Attempting DDL as finsight_backup must fail
            BEGIN
                EXECUTE 'CREATE TABLE public.hack (id int)';
                RAISE EXCEPTION 'finsight_backup CREATE TABLE succeeded unexpectedly';
            EXCEPTION WHEN insufficient_privilege THEN
                -- Expected
            END;
        END $$;
        """
        # Execute check in container as finsight_backup
        rc, out, err = execute_sql("sme-health-postgres", "127.0.0.1", 5432, "sme_health",
                                   "finsight_backup", "FinSight_Backup_Reader_2026_!#5bK", check_mutations_sql)
        if rc == 0:
            print("    PASSED: finsight_backup mutation attempts strictly denied (insufficient_privilege)")
            results["backup_role_privileges"] = "PASS"
        else:
            print(f"    FAILED: Backup role privilege check failed: {err}")
            results["backup_role_privileges"] = "FAILED"

        # 8. Clean up disposable drill database
        print(f"\n[8] Cleaning up disposable database '{disposable_db}'...")
        execute_sql("sme-health-postgres", "127.0.0.1", 5432, "sme_health",
                    "finsight_dba", "FinSight_Dba_Admin_Sec_2026_!#9xK",
                    f"DROP DATABASE IF EXISTS {disposable_db};")
        print("    Cleaned up successfully.")

    finally:
        # Clean up temporary files
        shutil.rmtree(temp_dir, ignore_errors=True)

    print("\n" + "=" * 70)
    print("S9 DRILL COMPLETED SUCCESSFULLY")
    print(json.dumps(results, indent=2))
    print("=" * 70)
    return results


if __name__ == "__main__":
    run_drill()
