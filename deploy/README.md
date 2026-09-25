# Deploying

One cheap VPS: the fat jar under systemd, Postgres, Caddy in front for TLS, and a nightly `pg_dump` to object storage. GitHub Actions builds, tests and deploys every push to `main`.

**On Scaleway: follow [scaleway.md](scaleway.md).** `provision.sh` does the one-time setup below in one go on Ubuntu 24.04; the manual steps are here for other hosts.

## One-time server setup (Debian/Ubuntu)

1. **Packages**
   ```bash
   sudo apt install openjdk-25-jre-headless postgresql caddy rclone curl fontconfig fonts-dejavu-core
   ```
   `fontconfig` and a font are needed for the invite preview image (Java2D).
2. **User and folders**
   ```bash
   sudo useradd --system --home /opt/gorefest --shell /usr/sbin/nologin gorefest
   sudo mkdir -p /opt/gorefest/releases /etc/gorefest && sudo chown -R gorefest: /opt/gorefest
   sudo cp deploy/backup.sh /opt/gorefest/ && sudo chown gorefest: /opt/gorefest/backup.sh
   ```
3. **Database**
   ```bash
   sudo -u postgres createuser --pwprompt gorefest
   sudo -u postgres createdb --owner gorefest gorefest
   ```
   The app runs its Flyway migrations at startup.
4. **Config**: copy `deploy/env.example` to `/etc/gorefest/env` (root-owned, mode 0640, group `gorefest`) and fill it in. You'll need:
   - a `SECRET_KEY` (`openssl rand -base64 48`)
   - a mail provider account (Postmark or Resend) with a verified sending domain
   - a BGG application token (register on boardgamegeek.com)
   - an rclone remote for backups (`sudo -u gorefest rclone config`)
5. **Services**
   ```bash
   sudo cp deploy/gorefest.service deploy/gorefest-backup.service deploy/gorefest-backup.timer /etc/systemd/system/
   sudo systemctl daemon-reload && sudo systemctl enable gorefest gorefest-backup.timer
   echo 'GOREFEST_DOMAIN=gorefest.example.org' | sudo tee -a /etc/default/caddy
   sudo cp deploy/Caddyfile /etc/caddy/Caddyfile && sudo systemctl reload caddy
   ```
6. **Deploy user**: the GitHub Action logs in as a user that may write `/opt/gorefest` and run `sudo systemctl restart gorefest` (and nothing else) without a password:
   ```
   deploy ALL=(root) NOPASSWD: /usr/bin/systemctl restart gorefest
   ```
7. **GitHub secrets**: `DEPLOY_HOST`, `DEPLOY_USER`, `DEPLOY_SSH_KEY` (private key), `DEPLOY_KNOWN_HOSTS` (`ssh-keyscan your-host`). Without them the deploy job is skipped.

## How a deploy works

CI builds and tests, then copies the jar and `deploy.sh` to the server and runs:
1. The jar becomes a new folder under `/opt/gorefest/releases/`, and `current` points at it.
2. The service restarts, and the script waits up to 90 s for `/health`, which also checks the database.
3. If the new release doesn't come up healthy, `current` goes back to the previous release. The last five releases are kept.

Migrations run when the new jar starts, so a rollback only works if migrations stay backwards compatible (add columns and tables, don't drop or rename them in the same release).

## Backups and restoring

`gorefest-backup.timer` runs `backup.sh` nightly: `pg_dump --format=custom` straight into the rclone remote, keeping 30 days.

To restore:
```bash
rclone copy backups:gorefest/gorefest-2026-10-03T0330Z.dump /tmp/
sudo systemctl stop gorefest
pg_restore --clean --if-exists --no-owner -d "$BACKUP_DATABASE_URI" /tmp/gorefest-2026-10-03T0330Z.dump
sudo systemctl start gorefest
```
The projections rebuild from the event log on start, so nothing else needs restoring. Test this once on a scratch database and write down how long it took.
