# Frozen V52 recovery catalog

`v52.json` records the exact integrated V1–V52 schema from a fresh disposable
PostgreSQL 14.22 instance: 84 tables (including Flyway), 64 non-internal triggers,
18 non-extension functions, constraints, indexes, columns/defaults, schema/object
owners and effective app/migrator/backup ACLs. It contains no domain rows/secrets.
The normalized source SHA-256 map detects changes to any V1–V52 SQL.

`catalog.sql` is a read-only acceptance query, **not a migration**. Both Java and
Python use this same inventory. `review_v52_inventory.py` independently derives
table ACLs from bootstrap DML/SELECT defaults and explicit migration GRANT/REVOKE
statements (including multiple recipients). Its complete table report lives in
`docs/release/V52_GRANT_INVENTORY.md`.

`ReleaseCatalogExporter` accepts no external database URL and always provisions
`DisposablePostgres`. It can produce a review candidate; tests never overwrite
the frozen reference or automatically accept a diff. PostgreSQL 17 deparser
formatting differences must be reviewed explicitly on the dedicated target;
do not weaken structural or privilege expectations to bypass a failure.

Restore first checks structure/runtime/backup ACLs, then assigns migration
ownership **only inside the newly created restore DB**, and checks the full
catalog again. It never runs `REASSIGN OWNED` or modifies global roles/default
privileges. Production bootstrap roles/default privileges must already exist;
production acceptance also checks real verify-full TLS logins, restricted role
flags and absence of indirect role memberships. No V1–V52 SQL was edited.
