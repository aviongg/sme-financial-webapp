-- ===================================================================
-- FinSight Phase S9: Dedicated Backup Database Role Provisioning
-- Executed by finsight_dba (Superuser / Database Owner)
-- ===================================================================

SET password_encryption = 'scram-sha-256';

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'finsight_backup') THEN
        CREATE ROLE finsight_backup WITH LOGIN PASSWORD 'FinSight_Backup_Reader_2026_!#5bK' NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
    ELSE
        ALTER ROLE finsight_backup WITH PASSWORD 'FinSight_Backup_Reader_2026_!#5bK' NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
    END IF;
END $$;

-- 1. Database Connection and Schema Usage
REVOKE ALL ON DATABASE sme_health FROM finsight_backup;
GRANT CONNECT ON DATABASE sme_health TO finsight_backup;

REVOKE ALL ON SCHEMA public FROM finsight_backup;
GRANT USAGE ON SCHEMA public TO finsight_backup;

-- 2. Read-Only Grants for pg_dump
GRANT SELECT ON ALL TABLES IN SCHEMA public TO finsight_backup;
GRANT SELECT ON ALL SEQUENCES IN SCHEMA public TO finsight_backup;

-- 3. Default Privileges for future tables/sequences created by finsight_migrator
ALTER DEFAULT PRIVILEGES FOR ROLE finsight_migrator IN SCHEMA public
    GRANT SELECT ON TABLES TO finsight_backup;
ALTER DEFAULT PRIVILEGES FOR ROLE finsight_migrator IN SCHEMA public
    GRANT SELECT ON SEQUENCES TO finsight_backup;

-- 4. Explicit Prohibitions: Strictly Deny All Mutations
REVOKE INSERT, UPDATE, DELETE, TRUNCATE ON ALL TABLES IN SCHEMA public FROM finsight_backup;
REVOKE CREATE ON SCHEMA public FROM finsight_backup;
