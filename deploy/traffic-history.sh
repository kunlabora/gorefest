#!/usr/bin/env bash
# Nightly (gorefest-traffic-history.timer): adds Caddy's access lines since the last run to GoAccess's long-term
# database, with IPs masked (IPv4 /24, IPv6 /64), and writes the report (history.html). journald keeps the full lines
# for 14 days; this keeps masked counts for about a year (--keep-last counts days with traffic, which is every day on a
# public server). Installed as /usr/local/bin/gorefest-traffic-history.
set -euo pipefail

state="${STATE_DIRECTORY:-/var/lib/gorefest-traffic}"
db="$state/db"
cursor="$state/journal-cursor"
mkdir -p "$db"

# The lines with full IPs only pass through a private temp file, gone when the script ends.
lines="$(mktemp)"
trap 'rm -f "$lines"' EXIT

# The cursor makes every line count once: journalctl starts after the last line read, and saves where it stopped.
# If GoAccess fails, the old cursor comes back, so the next run reads those lines again.
[ -f "$cursor" ] && cp "$cursor" "$cursor.prev"
journalctl -u caddy -o cat --cursor-file="$cursor" | { grep '"logger":"http.log.access' || true; } > "$lines"

if ! goaccess --log-format=CADDY --anonymize-ip --persist --restore --db-path="$db" --keep-last=365 \
    -o "$state/history.html" - < "$lines"; then # stdin: GoAccess's own "read up to here" is per file, the cursor does that
  if [ -f "$cursor.prev" ]; then mv "$cursor.prev" "$cursor"; else rm -f "$cursor"; fi
  exit 1
fi
rm -f "$cursor.prev"
echo "Added $(wc -l < "$lines") requests to the traffic history"
