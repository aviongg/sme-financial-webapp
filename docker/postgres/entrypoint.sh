#!/bin/sh
set -eu

# Secret mounts can be world-readable on some Docker hosts. PostgreSQL requires
# a private server key; copy it outside PGDATA before the official entrypoint
# drops privileges and initializes an empty volume.
test -s /run/secrets/postgres_server_key
test -s /etc/finsight/certs/postgres-server.crt
install -d -m 0700 -o postgres -g postgres /run/finsight-postgres
install -m 0600 -o postgres -g postgres /run/secrets/postgres_server_key /run/finsight-postgres/server.key
install -m 0644 -o postgres -g postgres /etc/finsight/certs/postgres-server.crt /run/finsight-postgres/server.crt
exec /usr/local/bin/docker-entrypoint.sh "$@"
