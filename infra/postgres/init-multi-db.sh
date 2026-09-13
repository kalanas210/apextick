#!/bin/bash
# Creates additional databases listed in POSTGRES_MULTIPLE_DATABASES (comma-separated),
# in addition to the primary POSTGRES_DB. The postgres image runs it once, on an empty
# data directory; production's db-init service runs it again on every start, against the
# live server (PGHOST), where it creates only the databases that are missing.
set -euo pipefail
if [ -n "${POSTGRES_MULTIPLE_DATABASES:-}" ]; then
  for db in $(echo "$POSTGRES_MULTIPLE_DATABASES" | tr ',' ' '); do
    echo "init-multi-db: ensuring database '$db'"
    psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
	SELECT 'CREATE DATABASE "$db"' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = '$db')\gexec
	EOSQL
  done
fi
