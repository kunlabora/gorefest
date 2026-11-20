#!/usr/bin/env bash
# Traffic at a glance: GoAccess over Caddy's access log in journald (14 days at most). Installed as /usr/local/bin/gorefest-traffic.
#   sudo gorefest-traffic                 # the terminal dashboard, last 7 days
#   sudo gorefest-traffic 1d              # the last day
#   sudo gorefest-traffic 14d report.html # an HTML report to scp home; don't serve it from the site
set -euo pipefail

since="${1:-7d}"
out="${2:-}"
log() { journalctl -u caddy --since "-$since" -o cat | grep '"logger":"http.log.access'; }

if [ -n "$out" ]; then
  log | goaccess --log-format=CADDY -o "$out" -
  echo "Wrote $out"
else
  log | goaccess --log-format=CADDY -
fi
