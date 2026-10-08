"""Live PostgreSQL privilege and TLS checks (not mocks).

Run from the DB subnet with psycopg2 and pg_dump installed. Secrets are read from
FINSIGHT_SECRETS_DIR, default /run/secrets; no password defaults are accepted.
A random disposable probe table is created by the migrator and removed in finally.
"""
import os
from pathlib import Path
import socket
import subprocess
import sys
import uuid

import psycopg2
from psycopg2 import sql


def main():
    secret_dir = Path(os.environ.get("FINSIGHT_SECRETS_DIR", "/run/secrets"))
    credentials = {}
    for role, secret in (("finsight_app", "app_db_password"),
                         ("finsight_migrator", "migrator_db_password"),
                         ("finsight_backup", "backup_db_password"),
                         ("finsight_dba", "dba_db_password")):
        password = (secret_dir / secret).read_text().strip()
        if not password:
            raise RuntimeError(f"Required secret is empty: {secret}")
        credentials[role] = password
    host = os.environ.get("DB_HOST", "postgres.finsight.internal")
    common = dict(host=host, port=int(os.environ.get("DB_PORT", "5432")),
                  dbname=os.environ.get("DB_NAME", "sme_health"),
                  sslmode="verify-full", connect_timeout=5,
                  sslrootcert=os.environ.get("DB_SSL_ROOT_CERT", "certs/postgres-ca.crt"))

    def connect(role, **overrides):
        return psycopg2.connect(**(common | overrides), user=role, password=credentials[role])

    def denied_connection(label, expected, role="finsight_app", **overrides):
        try:
            connection = connect(role, **overrides)
        except psycopg2.OperationalError as error:
            # A down server/DNS failure is not proof of authentication enforcement.
            if expected not in str(error).lower():
                raise AssertionError(f"{label}: failed for an unrelated reason") from None
        else:
            connection.close()
            raise AssertionError(f"{label}: unexpected connection success")
        print(f"PASS: {label}")

    def denied_sql(connection, statement):
        try:
            with connection.cursor() as cursor:
                cursor.execute(statement)
        except psycopg2.errors.InsufficientPrivilege:
            connection.rollback()
        else:
            connection.rollback()
            raise AssertionError("Prohibited SQL unexpectedly succeeded")

    # Verify the reachable server and each legitimate role BEFORE negative tests.
    connections = {}
    probe = sql.Identifier("closure_probe_" + uuid.uuid4().hex)
    created = False
    try:
        for role in ("finsight_migrator", "finsight_app", "finsight_backup"):
            connections[role] = connect(role)
            with connections[role].cursor() as cursor:
                cursor.execute("SELECT current_user, ssl FROM pg_stat_ssl WHERE pid = pg_backend_pid()")
                assert cursor.fetchone() == (role, True), "Role identity or TLS mismatch"
            print(f"PASS: {role} verify-full TLS connection")
        migrator, app, backup = (connections[r] for r in ("finsight_migrator", "finsight_app", "finsight_backup"))
        with migrator.cursor() as cursor:
            cursor.execute(sql.SQL("CREATE TABLE public.{} (id integer PRIMARY KEY, value text)").format(probe))
        migrator.commit()
        created = True
        with app.cursor() as cursor:
            cursor.execute(sql.SQL("INSERT INTO public.{} VALUES (1, 'first')").format(probe))
            cursor.execute(sql.SQL("UPDATE public.{} SET value = 'updated' WHERE id = 1").format(probe))
            cursor.execute(sql.SQL("SELECT value FROM public.{} WHERE id = 1").format(probe))
            assert cursor.fetchone() == ("updated",)
        app.commit()
        for statement in (sql.SQL("CREATE TABLE public.{} (id integer)").format(sql.Identifier("denied_" + uuid.uuid4().hex)),
                          sql.SQL("ALTER TABLE public.{} ADD COLUMN forbidden text").format(probe),
                          sql.SQL("DROP TABLE public.{}").format(probe)):
            denied_sql(app, statement)
        print("PASS: app DML permitted; CREATE/ALTER/DROP denied")
        with backup.cursor() as cursor:
            cursor.execute(sql.SQL("SELECT count(*) FROM public.{}").format(probe))
            assert cursor.fetchone() == (1,)
        backup.rollback()
        for statement in (sql.SQL("INSERT INTO public.{} VALUES (2, 'forbidden')").format(probe),
                          sql.SQL("UPDATE public.{} SET value = 'forbidden'").format(probe),
                          sql.SQL("DELETE FROM public.{}").format(probe),
                          sql.SQL("TRUNCATE TABLE public.{}").format(probe)):
            denied_sql(backup, statement)
        print("PASS: backup SELECT permitted; INSERT/UPDATE/DELETE/TRUNCATE denied")
        dump_env = os.environ | {"PGHOST": host, "PGPORT": str(common["port"]),
                                 "PGDATABASE": common["dbname"], "PGUSER": "finsight_backup",
                                 "PGPASSWORD": credentials["finsight_backup"], "PGSSLMODE": "verify-full",
                                 "PGSSLROOTCERT": common["sslrootcert"]}
        dump = subprocess.run(["pg_dump", "--no-password", "--format=custom"],
                              env=dump_env, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
        if dump.returncode:
            raise AssertionError("Backup role pg_dump failed; check server diagnostics")
        print("PASS: backup role pg_dump")
        denied_connection("plaintext rejected", "pg_hba.conf rejects connection", sslmode="disable")
        denied_connection("DBA network access rejected", "pg_hba.conf rejects connection", role="finsight_dba")
        denied_connection("hostname mismatch rejected", "does not match host name",
                          host="wrong.finsight.internal", hostaddr=socket.gethostbyname(host))
        rogue_ca = os.environ.get("DB_ROGUE_CA_CERT", "certs/test-fixtures/rogue-ca.crt")
        if not Path(rogue_ca).is_file():
            raise AssertionError("An existing untrusted CA fixture is required")
        denied_connection("untrusted CA rejected", "certificate verify failed", sslrootcert=rogue_ca)
        with app.cursor() as cursor:
            cursor.execute(sql.SQL("DELETE FROM public.{} WHERE id = 1").format(probe))
        app.commit()
        print("PASS: app DELETE permitted")
    finally:
        for connection in connections.values():
            connection.rollback()
        if created:
            with connections["finsight_migrator"].cursor() as cursor:
                cursor.execute(sql.SQL("DROP TABLE public.{}").format(probe))
            connections["finsight_migrator"].commit()
        for connection in connections.values():
            connection.close()


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        # Do not echo connection strings/passwords from provider exceptions.
        print(f"FAIL: production database checks ({type(error).__name__})", file=sys.stderr)
        sys.exit(1)
