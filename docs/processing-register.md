# Data stores

An internal list of where personal data lives, kept short because the household exemption applies (see [gdpr.md](gdpr.md)); it isn't an Art. 30 record. The public notice is `/privacy` (`web.privacy`). Keep both in step when a store is added.

| Store | What | Why | Who sees it | How long |
|---|---|---|---|---|
| `players` | Email, display name, signup and approval time | Sign-in, mail, showing names to the group | Admin; names to people on the same game day | Until the account is deleted (G2/G3: by hand through the admin channel) |
| `login_attempts` | Email, hashed code and token, return path | Magic-link and code login | Nobody (hashed) | 1 day (`ATTEMPT_RETENTION`) |
| `sessions` | Hashed session token, player id, expiry | Staying signed in | Nobody | Until expiry |
| `collection_syncs`, `collections` | BGG username, collection, sync status | Choosing games to bring | Co-members on the ask screen (the collection) | Until the account is deleted |
| `events` (the log) | Taste answers, "haven't played", wishes, attendance, shelves, plays, bring requests, date-poll answers, game day title, date and place, the linked BGG username | Everything the app does | Members of the same game day, as shown on its pages; the place only after joining | Forever until erased (G2); backups 30 days |
| Mail provider (`MAIL_PROVIDER`) | Email, name, mail text | Delivery | The provider (Scaleway TEM by default) | The provider's own retention |
| Discord channel (`NOTIFY_DISCORD_WEBHOOK`) | Name and a link for sign-ups and requests; no email | Telling the admin | Admin only | Discord's own |
| Backups | Nightly `pg_dump` of everything above, Scaleway Object Storage fr-par | Restore | Admin | 30 days |
| Server logs (journald) | Time, method, status, scrubbed path; no tokens, emails or IPs | Operations | Admin | 14 days (`provision.sh`) |
| Cookies | `gf_session` (sign-in), `gf_theme` (light/dark, set in the browser) | Working at all | — | Session, one year |
