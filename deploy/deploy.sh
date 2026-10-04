#!/usr/bin/env bash
# Runs on the server: installs an uploaded jar as a new release, restarts, and waits for /health.
# If the new release doesn't come up healthy, it switches back to the previous one.
# Usage: deploy.sh /tmp/gorefest.jar
set -euo pipefail

upload="$1"
root=/opt/gorefest
release="$root/releases/$(date -u +%Y%m%dT%H%M%SZ)"
previous="$(readlink -f "$root/current" 2>/dev/null || true)"

mkdir -p "$release"
mv "$upload" "$release/gorefest.jar"
# scp may leave the jar 600; the service user (group gorefest) must be able to read it and enter the folder.
chmod 644 "$release/gorefest.jar"
chmod 755 "$release"
ln -sfn "$release" "$root/current"
sudo systemctl restart gorefest

# Startup replays the whole event log, so it slows down as the log grows: HEALTH_WAIT (seconds) is adjustable.
wait="${HEALTH_WAIT:-90}"
started=$SECONDS
while [ $((SECONDS - started)) -lt "$wait" ]; do
  if curl -fsS http://127.0.0.1:8080/health >/dev/null; then
    echo "Deployed $release (healthy after $((SECONDS - started)) s)"
    # Keep the five newest releases for rollbacks.
    ls -1dt "$root"/releases/* | tail -n +6 | xargs -r rm -rf
    exit 0
  fi
  sleep 2
done

echo "Release $release is not healthy after $wait s (HEALTH_WAIT)." >&2
if [ -n "$previous" ]; then
  # Note: migrations already ran. They must stay backwards compatible for this rollback to work.
  echo "Rolling back to $previous" >&2
  ln -sfn "$previous" "$root/current"
  sudo systemctl restart gorefest
fi
exit 1
