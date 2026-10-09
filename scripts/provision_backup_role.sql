-- Existing databases only, run as the local-socket DBA. New volumes use
-- docker/postgres/init/10-roles.sh. Export FINSIGHT_BACKUP_DB_PASSWORD from its
-- secret file; never put the password in psql argv. psql 15+ is required.
\set ON_ERROR_STOP on
\set ECHO none
SET log_statement = 'none';
SET log_min_error_statement = 'panic';
\getenv backup_password FINSIGHT_BACKUP_DB_PASSWORD
\if :{?backup_password}
\else
    \echo 'Required backup password environment variable is missing'
    DO $$ BEGIN RAISE EXCEPTION 'Backup password is missing'; END $$;
\endif
SELECT length(:'backup_password') > 0 AS password_present \gset
\if :password_present
\else
    \echo 'Required backup password is empty'
    DO $$ BEGIN RAISE EXCEPTION 'Backup password is empty'; END $$;
\endif
SET password_encryption = 'scram-sha-256';
SELECT 'CREATE ROLE finsight_backup'
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'finsight_backup') \gexec
ALTER ROLE finsight_backup WITH LOGIN PASSWORD :'backup_password'
    NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION NOBYPASSRLS;
REVOKE ALL ON DATABASE sme_health FROM finsight_backup;
GRANT CONNECT ON DATABASE sme_health TO finsight_backup;
REVOKE ALL ON SCHEMA public FROM finsight_backup;
GRANT USAGE ON SCHEMA public TO finsight_backup;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO finsight_backup;
GRANT SELECT ON ALL SEQUENCES IN SCHEMA public TO finsight_backup;
ALTER DEFAULT PRIVILEGES FOR ROLE finsight_migrator IN SCHEMA public
    GRANT SELECT ON TABLES TO finsight_backup;
ALTER DEFAULT PRIVILEGES FOR ROLE finsight_migrator IN SCHEMA public
    GRANT SELECT ON SEQUENCES TO finsight_backup;
REVOKE INSERT, UPDATE, DELETE, TRUNCATE ON ALL TABLES IN SCHEMA public FROM finsight_backup;
REVOKE USAGE, UPDATE ON ALL SEQUENCES IN SCHEMA public FROM finsight_backup;
REVOKE CREATE ON SCHEMA public FROM finsight_backup;
