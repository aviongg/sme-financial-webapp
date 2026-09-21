-- Run with all application instances stopped and after taking a database backup.
-- Set search_path to the application's schema before executing this script.
-- This is an explicit one-time history mapping, NOT an automatic startup repair.
BEGIN;
LOCK TABLE flyway_schema_history IN EXCLUSIVE MODE;
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM flyway_schema_history WHERE NOT success) THEN
        RAISE EXCEPTION 'Failed migration found; investigate before reconciliation';
    END IF;
    IF EXISTS (
        SELECT 1 FROM flyway_schema_history
        WHERE type <> 'SQL' OR NOT (
            (version = '1' AND script = 'V1__initial_schema.sql' AND checksum = 1765198489)
            OR (version = '2' AND script = 'V2__add_compliance_and_repayment_fields.sql' AND checksum = -1244676505)
            OR (version = '3' AND script = 'V3__allow_null_cogs.sql' AND checksum = 369610604)
            OR (version IN ('2','3','4') AND script IN (
                'V2__create_insights_table.sql', 'V3__create_insights_table.sql', 'V4__create_insights_table.sql'
            ) AND checksum = -1230693217)
            OR (version IN ('3','4','5') AND script IN (
                'V3__create_recommendations_table.sql', 'V4__create_recommendations_table.sql', 'V5__create_recommendations_table.sql'
            ) AND checksum = -2022984514)
        ) OR checksum IS NULL
    ) THEN
        RAISE EXCEPTION 'Unknown migration history/checksum; no changes made. Do not use Flyway repair to hide it.';
    END IF;
    IF (SELECT count(*) FROM flyway_schema_history WHERE script LIKE '%create_insights_table.sql') <> 1
       OR (SELECT count(*) FROM flyway_schema_history WHERE script LIKE '%create_recommendations_table.sql') <> 1
       OR (SELECT count(*) FROM flyway_schema_history WHERE version = '1') <> 1 THEN
        RAISE EXCEPTION 'Expected one initial schema, insights migration and recommendations migration';
    END IF;
    IF to_regclass('insights') IS NULL OR to_regclass('recommendations') IS NULL
       OR to_regclass('score_results') IS NULL THEN
        RAISE EXCEPTION 'Expected application tables are missing';
    END IF;
END $$;
UPDATE flyway_schema_history SET version = '5', script = 'V5__create_recommendations_table.sql'
WHERE script IN ('V3__create_recommendations_table.sql', 'V4__create_recommendations_table.sql');
UPDATE flyway_schema_history SET version = '4', script = 'V4__create_insights_table.sql'
WHERE script IN ('V2__create_insights_table.sql', 'V3__create_insights_table.sql');
COMMIT;
-- Then run Flyway once with outOfOrder=true to apply missing V2/V3 and V6.
-- Validate afterwards and remove outOfOrder=true for normal application startup.
