#!/usr/bin/env bash
# One-time setup of a fresh Ubuntu 26.04 (or 24.04) server for gorefest (Java 25, PostgreSQL 18, Caddy and CrowdSec from their own repositories). Run as root from a copy of deploy/:
#   bash provision.sh <domain> "<public key GitHub Actions deploys with>"
# Safe to run again: it keeps existing secrets, the database password and /etc/gorefest/env.
set -euo pipefail

domain="${1:?usage: provision.sh <domain> <deploy public key>}"
deploy_key="${2:?usage: provision.sh <domain> <deploy public key>}"
here="$(cd "$(dirname "$0")" && pwd)"
export DEBIAN_FRONTEND=noninteractive

echo "== Packages"
apt-get update -q
apt-get install -yq curl gnupg ufw goaccess postgresql-common rclone fontconfig fonts-dejavu-core unattended-upgrades debian-keyring debian-archive-keyring apt-transport-https

# PostgreSQL 18 from the PostgreSQL project's own repository (what dev and the tests run): the same version on every Ubuntu release (24.04 ships 16).
if ! command -v psql >/dev/null || ! pg_lsclusters 2>/dev/null | grep -q '^18 '; then
  # A server that already runs an older cluster needs an upgrade, not a second install: do that by hand first.
  if pg_lsclusters 2>/dev/null | grep -qE '^[0-9]+ '; then
    echo "Found an existing PostgreSQL cluster:" >&2; pg_lsclusters >&2
    echo "Upgrade it first (pg_upgradecluster <old> main, after installing postgresql-18), then run this again." >&2
    exit 1
  fi
  /usr/share/postgresql-common/pgdg/apt.postgresql.org.sh -y
  apt-get install -yq postgresql-18
fi

# Java 25 from Adoptium (Temurin): Ubuntu 24.04 ships 21; 26.04 has 25 too, but one source keeps releases alike.
if ! command -v java >/dev/null || ! java -version 2>&1 | grep -q '"25'; then
  curl -fsSL https://packages.adoptium.net/artifactory/api/gpg/key/public | gpg --dearmor -o /usr/share/keyrings/adoptium.gpg
  echo "deb [signed-by=/usr/share/keyrings/adoptium.gpg] https://packages.adoptium.net/artifactory/deb $(. /etc/os-release && echo "$VERSION_CODENAME") main" \
    > /etc/apt/sources.list.d/adoptium.list
  apt-get update -q && apt-get install -yq temurin-25-jre
fi

# Caddy from its own repository, for current versions and automatic TLS.
if ! command -v caddy >/dev/null; then
  curl -1sLf https://dl.cloudsmith.io/public/caddy/stable/gpg.key | gpg --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
  curl -1sLf https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt > /etc/apt/sources.list.d/caddy-stable.list
  apt-get update -q && apt-get install -yq caddy
fi

echo "== Users"
id gorefest >/dev/null 2>&1 || useradd --system --home /opt/gorefest --shell /usr/sbin/nologin gorefest
id deploy >/dev/null 2>&1 || useradd --create-home --shell /bin/bash --groups gorefest deploy
install -d -m 700 -o deploy -g deploy /home/deploy/.ssh
grep -qxF "$deploy_key" /home/deploy/.ssh/authorized_keys 2>/dev/null || echo "$deploy_key" >> /home/deploy/.ssh/authorized_keys
chown deploy:deploy /home/deploy/.ssh/authorized_keys && chmod 600 /home/deploy/.ssh/authorized_keys
# The deploy user may restart the app and nothing else.
echo "deploy ALL=(root) NOPASSWD: /usr/bin/systemctl restart gorefest" > /etc/sudoers.d/gorefest-deploy
chmod 440 /etc/sudoers.d/gorefest-deploy

# deploy writes releases; the app (group gorefest) only reads them.
install -d -m 2775 -o deploy -g gorefest /opt/gorefest /opt/gorefest/releases
install -m 755 -o root -g root "$here/backup.sh" /opt/gorefest/backup.sh

echo "== Database"
install -d -m 750 -o root -g gorefest /etc/gorefest
if [ ! -f /etc/gorefest/env ]; then
  db_password="$(openssl rand -hex 24)"
  sed -e "s|^BASE_URL=.*|BASE_URL=https://$domain|" \
      -e "s|^SECRET_KEY=.*|SECRET_KEY=$(openssl rand -base64 48 | tr -d '\n')|" \
      -e "s|^DATABASE_PASSWORD=.*|DATABASE_PASSWORD=$db_password|" \
      -e "s|^MAIL_FROM=.*|MAIL_FROM=Boardfest <hello@$domain>|" \
      -e "s|PASSWORD@localhost|$db_password@localhost|" \
      "$here/env.example" > /etc/gorefest/env
  chown root:gorefest /etc/gorefest/env && chmod 640 /etc/gorefest/env
  echo "Wrote /etc/gorefest/env. Fill in the mail, BGG and backup keys before the first deploy."
else
  echo "Keeping the existing /etc/gorefest/env"
  db_password="$(grep -m1 '^DATABASE_PASSWORD=' /etc/gorefest/env | cut -d= -f2-)"
  [ -n "$db_password" ] || { echo "No DATABASE_PASSWORD in /etc/gorefest/env" >&2; exit 1; }
fi
# Always make sure the role and database exist with the password the env file has: on a fresh cluster
# (after a Postgres upgrade or a wipe) they are missing even though the env file is kept.
sudo -u postgres psql -qc "create role gorefest login password '$db_password'" 2>/dev/null \
  || sudo -u postgres psql -qc "alter role gorefest password '$db_password'"
sudo -u postgres createdb --owner gorefest gorefest 2>/dev/null || true

echo "== Swap"
# On a small box (1 GB) the JVM, Postgres and apt can touch the limit together: a swap file turns an OOM kill into a slow moment.
if [ "$(awk '/MemTotal/ {print int($2/1024)}' /proc/meminfo)" -lt 1800 ] && ! swapon --show | grep -q .; then
  fallocate -l 2G /swapfile && chmod 600 /swapfile && mkswap /swapfile >/dev/null && swapon /swapfile
  grep -q '^/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
  echo 'vm.swappiness=10' > /etc/sysctl.d/99-swappiness.conf && sysctl -q -p /etc/sysctl.d/99-swappiness.conf
fi

echo "== Log retention"
# The app logs request lines (method, status, a scrubbed path: no tokens) and errors. journald would keep them for months: keep two weeks.
mkdir -p /etc/systemd/journald.conf.d
printf '[Journal]\nMaxRetentionSec=14d\n' > /etc/systemd/journald.conf.d/retention.conf
systemctl restart systemd-journald

echo "== Services"
install -m 644 "$here/gorefest.service" "$here/gorefest-backup.service" "$here/gorefest-backup.timer" /etc/systemd/system/
systemctl daemon-reload
systemctl enable gorefest gorefest-backup.timer
systemctl start gorefest-backup.timer
sed "s|{\$GOREFEST_DOMAIN}|$domain|" "$here/Caddyfile" > /etc/caddy/Caddyfile
systemctl reload caddy || systemctl restart caddy

echo "== CrowdSec"
# Reads sshd's log and Caddy's access log (crowdsec-caddy.yaml), and the nftables bouncer drops banned IPs, next to ufw.
# From CrowdSec's own repository (their install script only adds it); the bouncer registers itself with the local agent.
# CrowdSec's local API listens on 127.0.0.1:8080 by default: the app's port. Whoever starts first gets it, so after a
# reboot CrowdSec could take the site down. Its overrides (*.local, merged over the packaged files) move it to 8090,
# and they are written before the packages first start anything.
crowdsec_api=127.0.0.1:8090
install -d /etc/crowdsec/bouncers
printf 'api:\n  server:\n    listen_uri: %s\n' "$crowdsec_api" > /etc/crowdsec/config.yaml.local
printf 'api_url: http://%s/\n' "$crowdsec_api" > /etc/crowdsec/bouncers/crowdsec-firewall-bouncer.yaml.local
if ! command -v cscli >/dev/null; then
  curl -fsSL https://install.crowdsec.net | sh
  apt-get install -yq crowdsec
fi
# cscli reaches the agent through the address in its credentials, written when the package was installed.
sed -i "s|^url:.*|url: http://$crowdsec_api/|" /etc/crowdsec/local_api_credentials.yaml
cscli collections install crowdsecurity/linux crowdsecurity/caddy
install -d /etc/crowdsec/acquis.d
install -m 644 "$here/crowdsec-caddy.yaml" /etc/crowdsec/acquis.d/caddy.yaml
systemctl restart crowdsec
dpkg -s crowdsec-firewall-bouncer-nftables >/dev/null 2>&1 || apt-get install -yq crowdsec-firewall-bouncer-nftables
systemctl restart crowdsec-firewall-bouncer
install -m 755 -o root -g root "$here/traffic.sh" /usr/local/bin/gorefest-traffic

echo "== Firewall and updates"
ufw allow OpenSSH >/dev/null && ufw allow 80/tcp >/dev/null && ufw allow 443/tcp >/dev/null
ufw --force enable >/dev/null
dpkg-reconfigure -f noninteractive unattended-upgrades

echo
echo "Done. Next: edit /etc/gorefest/env, then deploy the jar (deploy/scaleway.md, step 9)."
