#!/usr/bin/env bash
# Restores one of backup.sh's dumps into a new database. Run it inside the backup container:
#
#   docker compose -f docker-compose.prod.yml exec backup \
#     bash /scripts/restore.sh /backups/apextick-20260913T033000Z.dump apextick_restored
#
# The target must not exist: restore.sh creates it and loads the dump into it, so nothing
# live is touched. Putting a live database back is then a swap of names, once the restored
# copy has been checked (docs/runbook.md). It ends by counting every table's rows, to check
# against what the dump should hold.
set -euo pipefail

usage="usage: restore.sh <dump file> <new database name>"
dump="${1:?$usage}"
target="${2:?$usage}"

if [ ! -f "$dump" ]; then
  echo "restore: no such dump: $dump" >&2
  exit 66
fi
if ! [[ "$target" =~ ^[a-z_][a-z0-9_]*$ ]]; then
  echo "restore: name the new database with lowercase letters, digits and _ only" >&2
  exit 64
fi
if [ -n "$(psql --dbname=postgres --tuples-only --no-align \
    --command="SELECT 1 FROM pg_database WHERE datname = '$target'")" ]; then
  echo "restore: database $target already exists; restore into a new name" >&2
  exit 73
fi

started="$(date +%s)"
createdb "$target"
pg_restore --dbname="$target" --no-owner --exit-on-error "$dump"
echo "restore: $(basename "$dump") -> $target in $(( $(date +%s) - started ))s"

counts="$(psql --dbname="$target" --tuples-only --no-align --command="
  SELECT string_agg(format('SELECT %L AS table_name, count(*) AS row_count FROM %I.%I',
                           table_schema || '.' || table_name, table_schema, table_name), ' UNION ALL ')
    FROM information_schema.tables
   WHERE table_schema NOT IN ('pg_catalog', 'information_schema') AND table_type = 'BASE TABLE'")"
if [ -n "$counts" ]; then
  psql --dbname="$target" --command="$counts ORDER BY table_name"
fi
