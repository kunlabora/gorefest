# Deploying

One cheap VPS: the fat jar under systemd, Postgres, Caddy in front for TLS, and a nightly `pg_dump` to object storage. GitHub Actions builds, tests and deploys every push to `main`.

**On Scaleway: follow [scaleway.md](scaleway.md).** `provision.sh` does the one-time setup below in one go on Ubuntu 24.04; the manual steps are here for other hosts.

## One-time server setup (Debian/Ubuntu)

1. **Packages**
   ```bash
   sudo apt install postgresql-common rclone curl gnupg fontconfig fonts-dejavu-core
   sudo /usr/share/postgresql-common/pgdg/apt.postgresql.org.sh -y   # the PostgreSQL project's apt repository
   sudo apt install postgresql-18
   ```
   Postgres 18 is what dev (`compose.yaml`) and the tests run; Ubuntu 24.04's own `postgresql` package is 16, so don't install that one. A server that already has an older cluster needs `pg_upgradecluster` before this.
   Java 25 and Caddy also come from their own apt repositories, because Ubuntu 24.04 ships neither `openjdk-25` nor a current Caddy: Temurin 25 from Adoptium (`temurin-25-jre`) and Caddy from Cloudsmith. `provision.sh` shows the exact repository and key setup.
   `fontconfig` and a font are needed for the invite preview image (Java2D).
2. **Users and folders**: the service user `gorefest` only reads releases; a `deploy` user (member of group `gorefest`) writes them.
   ```bash
   sudo useradd --system --home /opt/gorefest --shell /usr/sbin/nologin gorefest
   sudo useradd --create-home --shell /bin/bash --groups gorefest deploy
   sudo install -d -m 2775 -o deploy -g gorefest /opt/gorefest /opt/gorefest/releases
   sudo install -d -m 750 -o root -g gorefest /etc/gorefest
   sudo install -m 755 -o root -g root deploy/backup.sh /opt/gorefest/backup.sh
   ```
   Add the public key GitHub Actions deploys with to `/home/deploy/.ssh/authorized_keys` (directory mode 700, file mode 600, owned by `deploy`).
3. **Database**
   ```bash
   sudo -u postgres createuser --pwprompt gorefest
   sudo -u postgres createdb --owner gorefest gorefest
   ```
   The app runs its Flyway migrations at startup.
4. **Config**: copy `deploy/env.example` to `/etc/gorefest/env` (root-owned, mode 0640, group `gorefest`) and fill it in. You'll need:
   - a `SECRET_KEY` (`openssl rand -base64 48`)
   - a mail provider account (Scaleway Transactional Email, Postmark or Resend, picked by `MAIL_PROVIDER`) with a verified sending domain
   - a BGG application token (register on boardgamegeek.com)
   - an rclone remote for backups (`sudo -u gorefest rclone config`)
5. **Services**
   ```bash
   sudo cp deploy/gorefest.service deploy/gorefest-backup.service deploy/gorefest-backup.timer /etc/systemd/system/
   sudo systemctl daemon-reload && sudo systemctl enable gorefest gorefest-backup.timer
   echo 'GOREFEST_DOMAIN=gorefest.example.org' | sudo tee -a /etc/default/caddy
   sudo cp deploy/Caddyfile /etc/caddy/Caddyfile && sudo systemctl reload caddy
   ```
6. **Deploy user sudo**: the `deploy` user from step 2 may run `sudo systemctl restart gorefest` (and nothing else) without a password:
   ```
   deploy ALL=(root) NOPASSWD: /usr/bin/systemctl restart gorefest
   ```
   Put that line in `/etc/sudoers.d/gorefest-deploy` (mode 440).
7. **GitHub secrets**: `DEPLOY_HOST`, `DEPLOY_USER`, `DEPLOY_SSH_KEY` (private key), `DEPLOY_KNOWN_HOSTS` (`ssh-keyscan your-host`). Without them the deploy job is skipped.

## How a deploy works

CI builds and tests, then copies the jar and `deploy.sh` to the server and runs:
1. The jar becomes a new folder (jar mode 644, folder 755, so the service user can read it) under `/opt/gorefest/releases/`, and `current` points at it.
2. The service restarts, and the script waits for `/health`, which also checks the database. The wait is 90 s unless `HEALTH_WAIT` (seconds) says otherwise, and the script prints how long startup took. Startup replays the whole event log, so raise `HEALTH_WAIT` if the log grows long.
3. If the new release doesn't come up healthy, `current` goes back to the previous release. The last five releases are kept.

Migrations run when the new jar starts, so a rollback only works if migrations stay backwards compatible (add columns and tables, don't drop or rename them in the same release).

## Backups and restoring

`gorefest-backup.timer` runs `backup.sh` nightly: `pg_dump --format=custom` to a temp file, checks it with `pg_restore --list`, then uploads it to the rclone remote (`BACKUP_REMOTE`), keeping 30 days of `gorefest-*.dump` files. A failed dump never gets uploaded.

To restore, work as the service user, which can read `/etc/gorefest/env`. The env file isn't safe to `source` whole (`MAIL_FROM` has `<>` in it), so load only the backup variables:
```bash
sudo -u gorefest bash
set -a; eval "$(grep -E '^(BACKUP_|RCLONE_)' /etc/gorefest/env)"; set +a
rclone lsf "$BACKUP_REMOTE"                       # list the dumps
rclone copyto "$BACKUP_REMOTE/gorefest-2026-10-03T0330Z.dump" /tmp/restore.dump
exit
sudo systemctl stop gorefest
sudo -u gorefest bash -c 'set -a; eval "$(grep -E "^BACKUP_" /etc/gorefest/env)"; pg_restore --clean --if-exists --no-owner -d "$BACKUP_DATABASE_URI" /tmp/restore.dump'
sudo systemctl start gorefest
sudo rm /tmp/restore.dump
```
The projections rebuild from the event log on start, so nothing else needs restoring. Test this once on a scratch database (point `-d` at it) and write down how long it took.

## After a deploy

The units are hardened (`ProtectHome`, `PrivateDevices`, `RestrictAddressFamilies`, `MemoryMax=1G` and more). After the first deploy with new unit files, or after changing them, check:
- `systemctl status gorefest` is active and `journalctl -u gorefest -n 50` shows no permission errors.
- The invite preview image still renders (open an invite link's `og:image` URL): it uses Java2D and fontconfig.
- `sudo systemctl start gorefest-backup.service` succeeds and a new dump shows up in the bucket.
- If the service is killed for memory, `journalctl -u gorefest` says so; raise `MemoryMax` (and the host size) rather than `-Xmx` first.
