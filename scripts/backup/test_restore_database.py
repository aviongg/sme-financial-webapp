"""SEC-03/04 regression tests: real age/AES-GCM, simulated PostgreSQL boundary.

Run: python -m unittest discover -s scripts/backup -p 'test_*.py' -v
These fixtures do not claim a real PostgreSQL/Docker recovery drill passed.
"""

import base64
import copy
import io
import json
import os
import re
import subprocess
import sys
import tempfile
import unittest
from contextlib import ExitStack, redirect_stderr, redirect_stdout
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from backup import restore_database as restore
from backup.backup_keyring import run_keyring_backup, collect_keyring
from backup import backup_database
from backup.crypto_age import generate_keypair, encrypt_bytes, AgeCryptoError
from backup.keyring_validation import validate_keyring
from cryptography.hazmat.primitives.ciphers.aead import AESGCM


class FakePipe(io.BytesIO):
    def close(self):
        self.captured = self.getvalue()
        super().close()


class FakeProcess:
    def __init__(self, returncode=0):
        self.stdin = FakePipe()
        self.pipe = self.stdin
        self.returncode = None
        self.final_returncode = returncode
        self.killed = False

    def wait(self):
        self.returncode = self.final_returncode
        return self.returncode

    def poll(self):
        return self.returncode

    def kill(self):
        self.killed = True
        self.final_returncode = -9


class PostgresFixture:
    def __init__(self):
        self.databases = {"live": {"sentinel": "must-survive", "sessions": 9}}
        self.original = copy.deepcopy(self.databases["live"])
        self.mutations = []
        self.tables = sorted(restore.REQUIRED_TABLES)
        self.migrations = restore.expected_migrations()
        self.triggers = sorted(restore.REQUIRED_TRIGGERS)
        self.counts = dict.fromkeys(("spring_sessions", "spring_session_attributes", "password_reset_tokens", "pending_mfa"), 0)
        self.samples = []
        self.malformed = 0
        self.purge_fails = False
        self.creation_fails = False
        self.cleanup_fails = False
        self.query_fails = False

    def execute(self, container, host, port, database, user, password, sql):
        self.mutations.append((database, sql))
        name = re.search(r'"([a-z_0-9]+)"', sql)
        if sql.startswith("CREATE DATABASE"):
            if self.creation_fails:
                return 1, "", "fixture-create-failure"
            self.databases[name[1]] = {}
        elif sql.startswith("DROP DATABASE"):
            if self.cleanup_fails:
                return 1, "", "fixture-cleanup-failure"
            del self.databases[name[1]]
        elif "DELETE FROM" in sql and self.purge_fails:
            return 1, "", "fixture-purge-failure"
        else:
            assert database != "live", "Mutation must never target live database"
        return 0, "", ""

    def query(self, container, host, port, database, user, password, sql):
        if "FROM pg_database" in sql:
            name = re.search(r"datname = '([^']+)'", sql)[1]
            return "1" if name in self.databases else ""
        if self.query_fails:
            raise restore.RestoreError("Fixture validation connection failure")
        assert database != "live", "Only staging is validated"
        if "FROM pg_tables" in sql:
            return json.dumps(self.tables)
        if "FROM public.flyway_schema_history" in sql:
            return json.dumps(self.migrations)
        if "FROM pg_trigger" in sql:
            return json.dumps(self.triggers)
        if "'spring_sessions'" in sql:
            return json.dumps(self.counts)
        if "'app_users'" in sql:
            return json.dumps({"app_users": 2, "businesses": 1, "enabled_mfa": 1})
        if "ciphertext !~" in sql:
            return str(self.malformed)
        if "json_agg(samples)" in sql:
            return json.dumps(self.samples)
        raise AssertionError("Unexpected validation query")


class RestoreTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.path = Path(self.temp.name)
        self.recipient, self.identity = generate_keypair()
        self.backup = self.path / "valid.sql.age"
        self.backup.write_bytes(encrypt_bytes(b"CREATE SCHEMA public;\nSELECT 1;\n", self.recipient))
        self.key = os.urandom(32)
        self.bundle = {"active_key_id": "k2", "keys": {"k2": base64.b64encode(self.key).decode()}}
        self.keyring = self.path / "keyring.age"
        self.write_keyring()
        self.server = PostgresFixture()
        nonce = os.urandom(12)
        aad = "FinSight|AppUser|fullName"
        payload = nonce + AESGCM(self.key).encrypt(nonce, b"Fixture User", aad.encode())
        self.server.samples = [{"field": "app_users.full_name", "aad": aad,
                                "ciphertext": "enc:v1:k2:" + base64.b64encode(payload).decode()}]
        self.process = FakeProcess()
        self.stack = ExitStack()
        self.addCleanup(self.stack.close)
        self.stack.enter_context(patch.object(restore, "execute_sql", self.server.execute))
        self.stack.enter_context(patch.object(restore, "execute_sql_query", self.server.query))
        self.popen = self.stack.enter_context(patch.object(restore.subprocess, "Popen", return_value=self.process))

    def write_keyring(self):
        self.keyring.write_bytes(encrypt_bytes(json.dumps(self.bundle).encode(), self.recipient))

    def run_restore(self, **changes):
        args = {"backup_file": self.backup, "identity": self.identity, "target_dbname": "staging",
                "admin_password": "ephemeral-fixture-password", "keyring_file": self.keyring}
        args.update(changes)
        return restore.run_restore(**args)

    def assert_live_unchanged(self):
        self.assertEqual(self.server.original, self.server.databases["live"])
        self.assertNotIn("staging", self.server.databases)
        self.assertFalse(any(database == "live" for database, _ in self.server.mutations))

    def fails_closed(self, exception=Exception, **changes):
        with self.assertRaises(exception):
            self.run_restore(**changes)
        self.assert_live_unchanged()

    def test_wrong_private_key_removes_only_created_staging(self):
        _, wrong = generate_keypair()
        self.fails_closed(AgeCryptoError, identity=wrong)
        self.assertTrue(self.process.killed)

    def test_corrupted_late_age_chunk_removes_partial_staging(self):
        data = bytearray(encrypt_bytes(b"SELECT 1;\n" * 20000, self.recipient))
        data[-1] ^= 1
        self.backup.write_bytes(data)
        self.fails_closed(AgeCryptoError)
        self.assertGreater(len(self.process.pipe.captured), 0, "Exercise failure after authenticated chunks stream")
        self.assertTrue(self.process.killed)

    def test_partial_sql_failure_removes_staging(self):
        self.process.final_returncode = 3
        self.fails_closed(restore.RestoreError)
        self.assertIn(b"SELECT 1", self.process.pipe.captured)

    def test_broken_pipe_removes_staging(self):
        with patch.object(self.process.stdin, "write", side_effect=BrokenPipeError):
            self.fails_closed()

    def test_process_start_failure_removes_staging(self):
        self.popen.side_effect = OSError("fixture missing psql")
        self.fails_closed(OSError)

    def test_missing_trigger_fails_verification(self):
        self.server.triggers.pop()
        self.fails_closed(restore.RestoreError)

    def test_missing_document_provenance_table_fails_verification(self):
        self.server.tables.remove("document_corrections")
        self.fails_closed(restore.RestoreError)

    def test_missing_document_provenance_guard_fails_verification(self):
        self.server.triggers.remove(("uploaded_documents", "trg_document_extraction_immutable"))
        self.fails_closed(restore.RestoreError)

    def test_trigger_on_wrong_table_does_not_satisfy_gate(self):
        self.server.triggers = [("wrong_table", name) for _, name in self.server.triggers]
        self.fails_closed(restore.RestoreError)

    def test_missing_domain_table_fails_verification(self):
        self.server.tables.remove("monthly_records")
        self.fails_closed(restore.RestoreError)

    def test_unexpected_flyway_version_fails_verification(self):
        self.server.migrations[-1]["version"] = "99"
        self.fails_closed(restore.RestoreError)

    def test_modified_flyway_checksum_fails_verification(self):
        self.server.migrations[-1]["checksum"] += 1
        self.fails_closed(restore.RestoreError)

    def test_failed_flyway_migration_fails_verification(self):
        self.server.migrations[-1]["success"] = False
        self.fails_closed(restore.RestoreError)

    def test_purge_error_fails_verification(self):
        self.server.purge_fails = True
        self.fails_closed(restore.RestoreError)

    def test_nonzero_each_ephemeral_count_fails_verification(self):
        for field in self.server.counts:
            with self.subTest(field=field):
                self.process = FakeProcess()
                self.popen.return_value = self.process
                self.server.counts[field] = 1
                self.fails_closed(restore.RestoreError)
                self.server.counts[field] = 0

    def test_query_failure_fails_verification(self):
        self.server.query_fails = True
        self.fails_closed(restore.RestoreError)

    def test_wrong_keyring_recipient_fails_whole_restore(self):
        other, _ = generate_keypair()
        self.keyring.write_bytes(encrypt_bytes(json.dumps(self.bundle).encode(), other))
        self.fails_closed(restore.RestoreError)

    def test_corrupt_keyring_fails_whole_restore(self):
        self.keyring.write_bytes(b"corrupt age bundle")
        self.fails_closed(restore.RestoreError)

    def test_missing_requested_keyring_fails_before_creating_database(self):
        self.keyring.unlink()
        self.fails_closed(FileNotFoundError)
        self.assertEqual([], self.server.mutations)

    def test_invalid_json_keyring_fails_whole_restore(self):
        self.keyring.write_bytes(encrypt_bytes(b"invalid json", self.recipient))
        self.fails_closed(restore.RestoreError)

    def test_missing_active_key_fails_whole_restore(self):
        self.bundle["active_key_id"] = "absent"
        self.write_keyring()
        self.fails_closed(restore.RestoreError)

    def test_invalid_key_length_fails_whole_restore(self):
        self.bundle["keys"]["k2"] = base64.b64encode(os.urandom(16)).decode()
        self.write_keyring()
        self.fails_closed(restore.RestoreError)

    def test_missing_historical_key_fails_whole_restore(self):
        self.server.samples[0]["ciphertext"] = self.server.samples[0]["ciphertext"].replace(":k2:", ":k1:")
        self.fails_closed(restore.RestoreError)

    def test_valid_key_length_but_wrong_material_fails_ciphertext_authentication(self):
        self.bundle["keys"]["k2"] = base64.b64encode(os.urandom(32)).decode()
        self.write_keyring()
        self.fails_closed(restore.RestoreError)

    def test_wrong_aad_fails_ciphertext_authentication(self):
        self.server.samples[0]["aad"] = "FinSight|DifferentField"
        self.fails_closed(restore.RestoreError)

    def test_malformed_envelope_fails_verification(self):
        self.server.malformed = 1
        self.fails_closed(restore.RestoreError)

    def test_valid_restore_and_real_ciphertext_authentication_succeed(self):
        result = self.run_restore()
        self.assertEqual("VERIFIED_STAGING_RESTORE", result["status"])
        self.assertEqual("VERIFIED", result["keyring_recovery"]["status"])
        self.assertEqual(1, result["keyring_recovery"]["ciphertext_samples_verified"])
        self.assertFalse(result["live_cutover_performed"])
        self.assertIn("staging", self.server.databases)
        self.assertEqual(self.server.original, self.server.databases["live"])
        self.assertNotIn("ephemeral-fixture-password", self.popen.call_args.args[0])
        self.assertIn("--single-transaction", self.popen.call_args.args[0])
        self.assertEqual(["--single-transaction", "-f", "-"], self.popen.call_args.args[0][-3:])

    def test_existing_live_database_is_never_modified(self):
        self.fails_closed(restore.RestoreError, target_dbname="live")
        self.assertEqual([], self.server.mutations)

    def test_existing_staging_is_never_dropped(self):
        self.server.databases["staging"] = {"precious": True}
        with self.assertRaises(restore.RestoreError):
            self.run_restore()
        self.assertEqual({"precious": True}, self.server.databases["staging"])
        self.assertEqual([], self.server.mutations)

    def test_concurrent_create_failure_never_drops_database(self):
        self.server.creation_fails = True
        self.fails_closed(restore.RestoreError)
        self.assertFalse(any(sql.startswith("DROP DATABASE") for _, sql in self.server.mutations))

    def test_cleanup_failure_is_explicit_never_success(self):
        self.server.cleanup_fails = True
        self.server.purge_fails = True
        with self.assertRaisesRegex(restore.RestoreError, "cleanup.*also failed"):
            self.run_restore()
        self.assertEqual(self.server.original, self.server.databases["live"])

    def test_strict_identifier_allowlist_before_any_sql(self):
        for name in ("x; DROP DATABASE live", "x'", "db-name", "X", "x" * 64, "db\n", "postgres"):
            with self.subTest(name=name):
                self.fails_closed(ValueError, target_dbname=name)
        self.assertEqual([], self.server.mutations)

    def test_missing_password_fails_before_any_sql(self):
        with patch.dict(os.environ, {}, clear=True):
            self.fails_closed(ValueError, admin_password=None)
        self.assertEqual([], self.server.mutations)

    def test_cli_failure_returns_nonzero_and_safe_status(self):
        self.server.purge_fails = True
        argv = ["restore", "--backup-file", str(self.backup), "--identity", self.identity,
                "--staging-db", "staging", "--admin-password", "ephemeral-fixture-password"]
        output = io.StringIO()
        with patch.object(sys, "argv", argv), redirect_stderr(output):
            self.assertEqual(1, restore.main())
        self.assertEqual("FAILED", json.loads(output.getvalue())["status"])
        self.assertNotIn(self.identity, output.getvalue())
        self.assert_live_unchanged()

    def test_cli_success_only_after_all_gates(self):
        argv = ["restore", "--backup-file", str(self.backup), "--identity", self.identity,
                "--staging-db", "staging", "--admin-password", "ephemeral-fixture-password",
                "--keyring-file", str(self.keyring)]
        output = io.StringIO()
        with patch.object(sys, "argv", argv), redirect_stdout(output):
            self.assertEqual(0, restore.main())
        self.assertEqual("VERIFIED_STAGING_RESTORE", json.loads(output.getvalue())["status"])


class ConfigurationTests(unittest.TestCase):
    def test_password_secret_file_and_environment(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "dba-password"
            path.write_text("fixture-value\n")
            self.assertEqual("fixture-value", restore.require_password(None, str(path)))
        with patch.dict(os.environ, {"DBA_DB_PASSWORD": "fixture-env"}, clear=True):
            self.assertEqual("fixture-env", restore.require_password(None))

    def test_native_psql_receives_password_environment_and_stops_on_errors(self):
        with patch.object(restore.subprocess, "run", return_value=subprocess.CompletedProcess([], 0, "", "")) as run:
            restore.execute_sql(None, "db.example", 6543, "staging", "operator", "fixture-value", "SELECT 1")
        self.assertEqual("fixture-value", run.call_args.kwargs["env"]["PGPASSWORD"])
        self.assertIn("ON_ERROR_STOP=1", run.call_args.args[0])
        self.assertIn("-X", run.call_args.args[0])
        self.assertNotIn("fixture-value", run.call_args.args[0])

    def test_keyring_backup_rejects_invalid_or_missing_active_key(self):
        recipient, _ = generate_keypair()
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaises(ValueError):
                run_keyring_backup(recipient, Path(directory), explicit_keys={"k1": "invalid"})
            with self.assertRaises(ValueError):
                run_keyring_backup(recipient, Path(directory), explicit_keys={"k2": base64.b64encode(os.urandom(32)).decode()}, explicit_active_id="k1")
            self.assertEqual([], list(Path(directory).glob("*.age")))

    def test_exact_key_size_and_base64_enforced(self):
        for value in ("invalid!?", "", base64.b64encode(os.urandom(31)).decode(), base64.b64encode(os.urandom(33)).decode()):
            with self.subTest(value_length=len(value)), self.assertRaises(ValueError):
                validate_keyring({"active_key_id": "k1", "keys": {"k1": value}})

    def test_configtree_secret_overrides_legacy_environment_key(self):
        secret_key = base64.b64encode(os.urandom(32)).decode()
        stale_key = base64.b64encode(os.urandom(32)).decode()
        with tempfile.TemporaryDirectory() as directory:
            Path(directory, "crypto_key_k1").write_text(secret_key)
            with patch.dict(os.environ, {"FINSIGHT_CRYPTO_KEY_K1": stale_key, "FINSIGHT_CRYPTO_ACTIVE_KEY_ID": "k1"}, clear=True):
                bundle = collect_keyring(Path(directory))
            self.assertEqual(secret_key, bundle["keys"]["k1"])

    def test_explicit_missing_secret_directory_never_falls_back(self):
        with tempfile.TemporaryDirectory() as directory:
            with patch.dict(os.environ, {"FINSIGHT_CRYPTO_KEY_K1": base64.b64encode(os.urandom(32)).decode()}, clear=True):
                with self.assertRaises(ValueError):
                    collect_keyring(Path(directory, "missing"))

    def test_blank_secret_never_falls_back_to_environment(self):
        with tempfile.TemporaryDirectory() as directory:
            Path(directory, "crypto_key_k1").write_text("")
            with patch.dict(os.environ, {"FINSIGHT_CRYPTO_KEY_K1": base64.b64encode(os.urandom(32)).decode()}, clear=True):
                with self.assertRaises(ValueError):
                    collect_keyring(Path(directory))

    def test_missing_active_key_is_not_silently_replaced(self):
        with tempfile.TemporaryDirectory() as directory:
            Path(directory, "crypto_key_k2").write_text(base64.b64encode(os.urandom(32)).decode())
            with patch.dict(os.environ, {"FINSIGHT_CRYPTO_ACTIVE_KEY_ID": "k1"}, clear=True):
                with self.assertRaises(ValueError):
                    collect_keyring(Path(directory))

    def test_tcp_tls_cannot_be_downgraded(self):
        for mode in ("disable", "allow", "prefer", "require", "verify-ca"):
            with self.subTest(mode=mode), self.assertRaises(ValueError):
                restore.verified_tls_env({"PGSSLMODE": mode})
        self.assertEqual("verify-full", restore.verified_tls_env({})["PGSSLMODE"])

    def test_backup_forwards_tls_and_password_names_to_docker(self):
        recipient, _ = generate_keypair()
        process = unittest.mock.Mock()
        process.stdout = io.BytesIO(b"CREATE SCHEMA public;\n")
        process.returncode = 0
        process.communicate.return_value = (b"", None)
        with tempfile.TemporaryDirectory() as directory, patch.dict(os.environ, {
                "PGSSLROOTCERT": "/certs/test-ca.crt", "PGSSLMODE": "verify-full"}, clear=True), \
                patch.object(backup_database.subprocess, "Popen", return_value=process) as popen, \
                patch.object(backup_database, "get_flyway_version", return_value="14"), \
                patch.object(backup_database, "get_pg_version", return_value="PostgreSQL fixture"), \
                patch.object(backup_database, "get_git_sha", return_value="fixture"):
            result = backup_database.run_backup(recipient, Path(directory), password="fixture-secret")
            self.assertEqual("SUCCESS", result["status"])
        command = popen.call_args.args[0]
        environment = popen.call_args.kwargs["env"]
        self.assertIn("PGSSLROOTCERT", command)
        self.assertIn("PGSSLMODE", command)
        self.assertIn("PGPASSWORD", command)
        self.assertNotIn("fixture-secret", command)
        self.assertEqual("fixture-secret", environment["PGPASSWORD"])
        self.assertEqual("verify-full", environment["PGSSLMODE"])
        self.assertEqual("/certs/test-ca.crt", environment["PGSSLROOTCERT"])

    def test_backup_has_no_password_fallback(self):
        recipient, _ = generate_keypair()
        with tempfile.TemporaryDirectory() as directory, patch.dict(os.environ, {}, clear=True):
            with self.assertRaises(ValueError):
                backup_database.run_backup(recipient, Path(directory))


if __name__ == "__main__":
    unittest.main()
