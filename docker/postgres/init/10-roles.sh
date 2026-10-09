#!/bin/sh
set -eu

# The official entrypoint supplies the DBA's PGPASSWORD for socket-only init.
# Password values are passed in process environment, never argv or generated SQL
# files. psql's literal quoting handles punctuation without SQL interpolation.
for secret in migrator_db_password app_db_password backup_db_password; do
    test -s "/run/secrets/$secret" || { echo "Missing required database secret: $secret" >&2; exit 1; }
done
FINSIGHT_MIGRATOR_DB_PASSWORD=$(cat /run/secrets/migrator_db_password)
FINSIGHT_APP_DB_PASSWORD=$(cat /run/secrets/app_db_password)
FINSIGHT_BACKUP_DB_PASSWORD=$(cat /run/secrets/backup_db_password)
test -n "$FINSIGHT_MIGRATOR_DB_PASSWORD" && test -n "$FINSIGHT_APP_DB_PASSWORD" && test -n "$FINSIGHT_BACKUP_DB_PASSWORD" || {
    echo 'Database passwords must not be empty' >&2
    exit 1
}
export FINSIGHT_MIGRATOR_DB_PASSWORD FINSIGHT_APP_DB_PASSWORD FINSIGHT_BACKUP_DB_PASSWORD

psql --no-psqlrc --no-password --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
    --set=ON_ERROR_STOP=1 --set=ECHO=none --quiet <<'SQL'
-- Do not log password-bearing statements even on provisioning failure.
SET log_statement = 'none';
SET log_min_error_statement = 'panic';
SET password_encryption = 'scram-sha-256';
\getenv migrator_password FINSIGHT_MIGRATOR_DB_PASSWORD
\getenv app_password FINSIGHT_APP_DB_PASSWORD
\getenv backup_password FINSIGHT_BACKUP_DB_PASSWORD
SELECT format('CREATE ROLE %I', role_name)
FROM (VALUES ('finsight_migrator'), ('finsight_app'), ('finsight_backup')) roles(role_name)
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = role_name) \gexec
ALTER ROLE finsight_migrator WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION NOBYPASSRLS PASSWORD :'migrator_password';
ALTER ROLE finsight_app WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION NOBYPASSRLS PASSWORD :'app_password';
ALTER ROLE finsight_backup WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION NOBYPASSRLS PASSWORD :'backup_password';

REVOKE ALL ON DATABASE sme_health FROM PUBLIC;
GRANT CONNECT ON DATABASE sme_health TO finsight_migrator, finsight_app, finsight_backup;
REVOKE ALL ON SCHEMA public FROM PUBLIC;
ALTER SCHEMA public OWNER TO finsight_migrator;
-- V1 needs pgcrypto, whose initial installation requires database CREATE.
-- Install as DBA so the migrator does not need that broader privilege.
CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA public;
GRANT USAGE ON SCHEMA public TO finsight_app, finsight_backup;
ALTER DEFAULT PRIVILEGES FOR ROLE finsight_migrator IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO finsight_app;
ALTER DEFAULT PRIVILEGES FOR ROLE finsight_migrator IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO finsight_app;
ALTER DEFAULT PRIVILEGES FOR ROLE finsight_migrator IN SCHEMA public
    GRANT SELECT ON TABLES TO finsight_backup;
ALTER DEFAULT PRIVILEGES FOR ROLE finsight_migrator IN SCHEMA public
    GRANT SELECT ON SEQUENCES TO finsight_backup;
-- Do not blanket-regrant runtime DML on existing tables: migrations may revoke
-- UPDATE/DELETE intentionally (for example the append-only audit table).
GRANT SELECT ON ALL TABLES IN SCHEMA public TO finsight_backup;
GRANT SELECT ON ALL SEQUENCES IN SCHEMA public TO finsight_backup;
SQL
unset FINSIGHT_MIGRATOR_DB_PASSWORD FINSIGHT_APP_DB_PASSWORD FINSIGHT_BACKUP_DB_PASSWORD
