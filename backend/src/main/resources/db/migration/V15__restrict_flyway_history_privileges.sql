-- Default runtime DML grants also apply when Flyway creates its history table.
-- Runtime requests must never read or rewrite migration checksums/history.
-- This versioned correction covers both existing installations and fresh volumes.
REVOKE ALL PRIVILEGES ON TABLE public.flyway_schema_history FROM PUBLIC;
REVOKE ALL PRIVILEGES ON TABLE public.flyway_schema_history FROM finsight_app;

-- The backup role retains its read-only grant for pg_dump and backup metadata.
-- Future domain tables retain the default runtime DML privileges.
