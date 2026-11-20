#!/usr/bin/env bash
# Run on your own machine: GoAccess's HTML report of the site's traffic, opened in your browser, then deleted on both
# sides once you press Enter. It lists IPs, which stay with the admin: full ones 14 days at most, masked ones about a year.
#   deploy/traffic-report.sh            # the last 14 days (all journald keeps), full IPs, from the ssh host "boardfest"
#   deploy/traffic-report.sh 1d         # the last day
#   deploy/traffic-report.sh history    # the long-term history, IPs masked (built nightly by traffic-history.sh)
#   GOREFEST_SSH=root@IP deploy/traffic-report.sh
set -euo pipefail

what="${1:-14d}"
host="${GOREFEST_SSH:-boardfest}"
remote="gorefest-traffic.html" # in your home directory on the server, so you can copy and delete it without sudo
dir="$(mktemp -d)"
report="$dir/traffic.html"

cleanup() {
  rm -rf "$dir"
  ssh "$host" "rm -f ~/$remote" || echo "Couldn't delete ~/$remote on $host: remove it by hand." >&2
}
trap cleanup EXIT

if [ "$what" = history ]; then
  make="sudo cp /var/lib/gorefest-traffic/history.html ~/$remote"
else
  make="sudo gorefest-traffic $what ~/$remote >/dev/null"
fi
# One connection with a terminal, so sudo can ask for your password; the report then belongs to you.
ssh -t "$host" "$make && sudo chown \"\$(id -u)\" ~/$remote"
scp -q "$host:$remote" "$report"
ssh "$host" "rm -f ~/$remote"

open "$report" 2>/dev/null || xdg-open "$report"
read -r -p "Report open in your browser. Press Enter when you're done to delete it... "
