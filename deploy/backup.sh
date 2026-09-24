#!/usr/bin/env bash
# Nightly: dump the database to object storage through rclone and keep 30 days.
set -euo pipefail
: "${BACKUP_REMOTE:?}" "${BACKUP_DATABASE_URI:?}"

stamp="$(date -u +%Y-%m-%dT%H%MZ)"
pg_dump --format=custom --no-owner "$BACKUP_DATABASE_URI" | rclone rcat "$BACKUP_REMOTE/gorefest-$stamp.dump"
rclone delete --min-age 30d "$BACKUP_REMOTE"
echo "Backed up gorefest-$stamp.dump"
