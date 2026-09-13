#!/usr/bin/env bash
# Dumps ApexTick's databases. The backup service in docker-compose.prod.yml runs it:
#
#   backup.sh schedule   every night at BACKUP_AT (UTC): dump, then prune
#   backup.sh once       dump and prune now
#
# Each database becomes BACKUP_DIR/<name>-<UTC timestamp>.dump, in pg_dump's custom
# format: compressed, and restorable a table at a time. A dump is written under a
# .partial name and renamed only once pg_restore has read it back, so a file with the
# final name is always a complete one. Old dumps are pruned only after a run in which
# every database dumped cleanly, so a string of failed nights never deletes the last
# good dumps. scripts/backup/restore.sh puts one back.
#
# Environment:
#   BACKUP_DATABASES   comma-separated database names (required)
#   BACKUP_DIR         /backups
#   BACKUP_AT          03:30 (HH:MM, UTC)
#   BACKUP_KEEP_DAYS   7
#   PGHOST, PGUSER, PGPASSWORD   the server, as for any libpq client
set -euo pipefail

BACKUP_DIR="${BACKUP_DIR:-/backups}"
BACKUP_AT="${BACKUP_AT:-03:30}"
BACKUP_KEEP_DAYS="${BACKUP_KEEP_DAYS:-7}"

log() {
  printf '%s backup: %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*"
}

# Dumps every database. Returns non-zero when any of them failed, having still tried the rest.
dump_all() {
  local stamp db file failed=0
  local -a databases
  stamp="$(date -u +%Y%m%dT%H%M%SZ)"
  mkdir -p "$BACKUP_DIR"
  IFS=',' read -r -a databases <<< "$BACKUP_DATABASES"
  for db in "${databases[@]}"; do
    db="${db//[[:space:]]/}"
    [ -n "$db" ] || continue
    file="$BACKUP_DIR/$db-$stamp.dump"
    if ! pg_dump --format=custom --dbname="$db" --file="$file.partial"; then
      log "$db: pg_dump failed"
      rm -f "$file.partial"
      failed=1
      continue
    fi
    # a dump that pg_restore cannot read back is not a backup
    if ! pg_restore --list "$file.partial" > /dev/null; then
      log "$db: the dump does not read back"
      rm -f "$file.partial"
      failed=1
      continue
    fi
    if ! mv "$file.partial" "$file"; then
      log "$db: could not move the dump into place"
      failed=1
      continue
    fi
    log "$db -> $(basename "$file") ($(du -h "$file" | cut -f1))"
  done
  return "$failed"
}

# Deletes dumps older than BACKUP_KEEP_DAYS, and any .partial a crash left behind.
prune() {
  local old
  find "$BACKUP_DIR" -maxdepth 1 -type f \( -name '*.dump' -o -name '*.dump.partial' \) \
    -mmin +"$((BACKUP_KEEP_DAYS * 24 * 60))" -print0 |
    while IFS= read -r -d '' old; do
      rm -f "$old"
      log "removed $(basename "$old"), older than $BACKUP_KEEP_DAYS days"
    done
}

run() {
  if dump_all; then
    prune
  else
    log "FAILED: every older dump in $BACKUP_DIR was kept"
    return 1
  fi
}

# Seconds from now until the next BACKUP_AT, UTC.
seconds_until_next() {
  local now next
  now="$(date -u +%s)"
  next="$(date -u -d "today $BACKUP_AT" +%s)"
  if [ "$next" -le "$now" ]; then
    next="$(date -u -d "tomorrow $BACKUP_AT" +%s)"
  fi
  echo "$((next - now))"
}

: "${BACKUP_DATABASES:?BACKUP_DATABASES must list the databases to dump}"
if ! [[ "$BACKUP_AT" =~ ^([01][0-9]|2[0-3]):[0-5][0-9]$ ]]; then
  echo "backup: BACKUP_AT must be HH:MM in UTC, not '$BACKUP_AT'" >&2
  exit 64
fi
if ! [[ "$BACKUP_KEEP_DAYS" =~ ^[1-9][0-9]*$ ]]; then
  echo "backup: BACKUP_KEEP_DAYS must be a whole number of days, not '$BACKUP_KEEP_DAYS'" >&2
  exit 64
fi

case "${1:-}" in
  once)
    run
    ;;
  schedule)
    # docker stop sends TERM; sleeping in the background keeps the wait interruptible
    trap 'log "stopping"; exit 0' TERM INT
    log "dumping $BACKUP_DATABASES every day at $BACKUP_AT UTC into $BACKUP_DIR, keeping $BACKUP_KEEP_DAYS days"
    while true; do
      sleep "$(seconds_until_next)" &
      wait "$!"
      # one failed night must not stop the next
      run || true
    done
    ;;
  *)
    echo "usage: backup.sh once|schedule" >&2
    exit 64
    ;;
esac
