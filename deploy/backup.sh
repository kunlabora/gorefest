#!/usr/bin/env bash
# Nightly: dump the database to a temp file, verify it, upload it with rclone and keep 30 days at most.
set -euo pipefail
: "${BACKUP_REMOTE:?}" "${BACKUP_DATABASE_URI:?}"

# Prune first: a failing dump must never keep old data past the 30-day maximum (GDPR retention, docs/gdpr.md).
# Only our own dumps: never touch anything else in the bucket.
rclone delete --min-age 30d --include 'gorefest-*.dump' "$BACKUP_REMOTE"
# The prune must have left nothing older than that (bucket versioning or object lock would keep deleted dumps). Today's
# backup still runs, but the unit ends failed, so `systemctl --failed` and the logs show it.
stale="$(rclone lsf --min-age 30d --include 'gorefest-*.dump' "$BACKUP_REMOTE")"

stamp="$(date -u +%Y-%m-%dT%H%MZ)"
name="gorefest-$stamp.dump"
tmp="$(mktemp --suffix=.dump)"
trap 'rm -f "$tmp"' EXIT

# Dump to a file first: piping into rclone could upload a truncated dump that looks valid if pg_dump dies midway.
pg_dump --format=custom --no-owner --file "$tmp" "$BACKUP_DATABASE_URI"
pg_restore --list "$tmp" >/dev/null   # fails on a damaged or incomplete archive
rclone copyto "$tmp" "$BACKUP_REMOTE/$name"
echo "Backed up $name"
if [ -n "$stale" ]; then
  echo "Dumps older than 30 days are still in the bucket (is versioning or object lock on?):" >&2
  echo "$stale" >&2
  exit 1
fi
