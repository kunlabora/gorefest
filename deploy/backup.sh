#!/usr/bin/env bash
# Nightly: dump the database to a temp file, verify it, upload it with rclone and keep 30 days.
set -euo pipefail
: "${BACKUP_REMOTE:?}" "${BACKUP_DATABASE_URI:?}"

stamp="$(date -u +%Y-%m-%dT%H%MZ)"
name="gorefest-$stamp.dump"
tmp="$(mktemp --suffix=.dump)"
trap 'rm -f "$tmp"' EXIT

# Dump to a file first: piping into rclone could upload a truncated dump that looks valid if pg_dump dies midway.
pg_dump --format=custom --no-owner --file "$tmp" "$BACKUP_DATABASE_URI"
pg_restore --list "$tmp" >/dev/null   # fails on a damaged or incomplete archive
rclone copyto "$tmp" "$BACKUP_REMOTE/$name"
# Only our own dumps: never touch anything else in the bucket.
rclone delete --min-age 30d --include 'gorefest-*.dump' "$BACKUP_REMOTE"
echo "Backed up $name"
