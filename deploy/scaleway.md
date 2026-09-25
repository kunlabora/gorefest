# Going live on Scaleway

Everything in one Scaleway project: an Instance (the server), Transactional Email (login mail) and Object Storage (backups). The console steps are yours; the server setup is `provision.sh`.

Replace `gorefest.example.org` with your (sub)domain and `IP` with the instance's address throughout.

## 1. Project

In the console, create a project (e.g. `gorefest`), or use the default one. Copy its **Project ID** (Project settings); it goes into `SCW_PROJECT_ID`.

Use region **Paris (fr-par)** for everything: Transactional Email only exists there.

## 2. SSH keys

- **Your key:** in Project settings → SSH keys, add `~/.ssh/id_ed25519.pub`. Instances get it for `root` at creation.
- **A key for GitHub Actions** to deploy with, only used for that:
  ```bash
  ssh-keygen -t ed25519 -f ~/.ssh/gorefest_deploy -N "" -C gorefest-deploy
  ```

## 3. Instance

Compute → Instances → Create:
- Zone: `fr-par-1` (or 2)
- Type: **DEV1-S** (2 vCPU, 2 GB)
- Image: **Ubuntu 24.04 (Noble)**
- Storage: the default local volume is plenty
- Flexible IPv4: yes

Once it's running, note the **public IPv4**. Security group: the default one lets everything in. Either restrict it to TCP 22, 80 and 443 or rely on the firewall `provision.sh` turns on (it does both).

## 4. DNS

At your DNS provider, add an **A record**: `gorefest.example.org → IP` (and AAAA if you enabled IPv6). Check it before going on, since Caddy needs it to get a certificate:
```bash
dig +short gorefest.example.org
```

## 5. Provision the server

From the repository:
```bash
scp -r deploy root@IP:/root/
```
```bash
ssh root@IP bash /root/deploy/provision.sh gorefest.example.org "$(cat ~/.ssh/gorefest_deploy.pub)"
```
This installs Java 25, Postgres, Caddy and rclone, creates the `gorefest` (app) and `deploy` (CI) users, the database, the systemd units, the firewall and automatic security updates. It also writes `/etc/gorefest/env` with a fresh `SECRET_KEY` and database password.

## 6. Transactional Email

Transactional Email → Add a domain: `gorefest.example.org`. Scaleway then shows DNS records to add at your DNS provider:
- **SPF** (TXT) and **DKIM** (TXT): required, or mail won't be sent.
- **MX** and **DMARC**: recommended, so mail lands in inboxes rather than spam.

Wait until the domain shows as verified. New domains may start with a sending limit; that's plenty for logging in a friend group.

## 7. API key

IAM → Applications → Create application `gorefest-server`. Attach a policy scoped to your project with:
- `TransactionalEmailEmailApiCreate` (or `TransactionalEmailFullAccess`), to send mail
- `ObjectStorageFullAccess`, for the backups

Then Applications → `gorefest-server` → API keys → Generate. You get an **access key** and a **secret key**; the secret is shown once.

## 8. Backup bucket

Object Storage → Create bucket `gorefest-backups` in fr-par, **private**.

## 9. Fill in the config and deploy

On the server:
```bash
ssh root@IP nano /etc/gorefest/env
```
Set:
- `MAIL_API_KEY`: the secret key
- `SCW_PROJECT_ID`: from step 1
- `RCLONE_CONFIG_SCALEWAY_ACCESS_KEY_ID` and `…_SECRET_ACCESS_KEY`: the access and secret key
- `BGG_TOKEN`: once BGG approves your application. Until then, leave it empty: the app runs with BGG switched off.

`MAIL_FROM` already says `hello@gorefest.example.org`; it must be on the domain you verified in step 6.

First deploy, by hand from your machine:
```bash
./gradlew buildFatJar
```
```bash
scp build/libs/gorefest.jar deploy/deploy.sh deploy@IP:/tmp/
```
```bash
ssh deploy@IP bash /tmp/deploy.sh /tmp/gorefest.jar
```

## 10. Check it

- `https://gorefest.example.org/health` says `ok` (Caddy fetches the certificate on the first request; give it a few seconds).
- Log in with your own email: the mail should arrive with the code in the subject.
- Invite yourself from a second account on your phone, via WhatsApp or Signal, to check the preview and the in-app browser login.
- Backups:
  ```bash
  ssh root@IP systemctl start gorefest-backup
  ```
  ```bash
  ssh root@IP journalctl -u gorefest-backup -n 20
  ```
  Then look in the bucket. Try a restore into a scratch database once (`deploy/README.md`).

Logs, if something's off:
```bash
ssh root@IP journalctl -u gorefest -n 100
```

## 11. Automatic deploys

Push the repository to GitHub and add these repository secrets:
- `DEPLOY_HOST`: `IP`
- `DEPLOY_USER`: `deploy`
- `DEPLOY_SSH_KEY`: the contents of `~/.ssh/gorefest_deploy` (the private key)
- `DEPLOY_KNOWN_HOSTS`: the output of `ssh-keyscan -t ed25519 IP`

From then on, every push to `main` builds, tests and deploys; a release that doesn't come up healthy is rolled back.
