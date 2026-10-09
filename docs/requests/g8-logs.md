# G8 Logs and URLs: no secrets, short retention

Source: GDPR analysis, 2026-10-09 ([`docs/gdpr.md`](../gdpr.md)). Status: 📋

**Why:** finding 8. Bearer tokens travel in `/login/link?token=…` and `/i/{token}`; `CallLogging` (Server.kt) logs request lines; journald keeps them by default for a long time. Caddy's access log is off (`deploy/Caddyfile`): keep it off.

**Wanted:**
- Check what `CallLogging` records (path, query, remote host, user agent) with a test that boots the app, calls `/login/link?token=SECRET`, `/i/SECRET2` and `/login/code`, and captures the log output; configure `CallLogging` (a `format`/`filter`) so tokens, emails and codes never appear (log the route template, not the raw URL; no query string).
- Make sure no code logs an email or IP at INFO (grep `log`/`println`); the dev mailbox is dev-only.
- `deploy/provision.sh`: set journald `MaxRetentionSec=14d` (a drop-in under `/etc/systemd/journald.conf.d/`) and document it.

**Criteria**
- [ ] Log-capture test as above, red before / green after
- [ ] No token, email or code in captured logs
- [ ] journald retention in `provision.sh` and `deploy/README.md`
- [ ] Mention log retention in the notice ([G1](g1-privacy-notice.md))
