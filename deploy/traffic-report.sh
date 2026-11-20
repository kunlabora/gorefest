#!/usr/bin/env bash
# Run on your own machine: GoAccess's HTML report of the site's traffic, opened in your browser, then deleted on both
# sides once you press Enter (it lists visitors' IPs, which stay with the admin for 14 days at most).
#   deploy/traffic-report.sh            # the last 14 days (all journald keeps), from the ssh host "boardfest"
#   deploy/traffic-report.sh 1d         # the last day
#   GOREFEST_SSH=root@IP deploy/traffic-report.sh
set -euo pipefail

since="${1:-14d}"
host="${GOREFEST_SSH:-boardfest}"
remote="gorefest-traffic.html" # in your home directory on the server, so you can copy and delete it without sudo
dir="$(mktemp -d)"
report="$dir/traffic.html"

cleanup() {
  rm -rf "$dir"
  ssh "$host" "rm -f ~/$remote" || echo "Couldn't delete ~/$remote on $host: remove it by hand." >&2
}
trap cleanup EXIT

# One connection with a terminal, so sudo can ask for your password; the report then belongs to you.
ssh -t "$host" "sudo gorefest-traffic $since ~/$remote >/dev/null && sudo chown \"\$(id -u)\" ~/$remote"
scp -q "$host:$remote" "$report"
ssh "$host" "rm -f ~/$remote"

open "$report" 2>/dev/null || xdg-open "$report"
read -r -p "Report open in your browser. Press Enter when you're done to delete it... "
