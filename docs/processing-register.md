# Data stores

An internal list of where personal data lives, kept short because the household exemption applies (see [gdpr.md](gdpr.md)); it isn't an Art. 30 record. The public notice is `/privacy` (`web.privacy`). Keep both in step when a store is added.

| Store | What | Why | Who sees it | How long |
|---|---|---|---|---|
| `players` | Email, display name, signup and approval time | Sign-in, mail, showing names to the group | Admin; names to people on the same game day | Until the account is deleted (`AccountEraser`, from `/account` through the admin) or 24 months pass without a sign-in (warning first) |
| `login_attempts` | Email, hashed code and token, return path | Magic-link and code login | Nobody (hashed) | 1 day (`ATTEMPT_RETENTION`) |
| `account_requests` | Who asked to delete their account or get their data, and when | Handling those requests | Admin | Until handled ("Sent it" closes a data request, erasure removes the account and with it the request) |
| `sessions` | Hashed session token, player id, expiry | Staying signed in | Nobody | Until expiry |
| `collection_syncs`, `collections` | BGG username (the only readable copy), collection, sync status | Choosing games to bring | Co-members on the ask screen (the collection) | Until the account is deleted |
| `events` (the log) | Taste answers, "haven't played", wishes, attendance, shelves, plays, bring requests, date-poll answers, game day title, date and place, a keyed hash of the linked BGG username (the older events hold the name itself) | Everything the app does | Members of the same game day, as shown on its pages; the place only after joining | Kept; after an erasure the player ids in it point at nobody (the log is not rewritten); backups 30 days |
| Mail provider (`MAIL_PROVIDER`) | Email, name, mail text (login codes, recaps, "your account is deleted", the file of someone's data if the admin mails it) | Delivery | The provider (Scaleway TEM by default) | The provider's own retention |
| Discord channel (`NOTIFY_DISCORD_WEBHOOK`) | Name and a link for sign-ups and requests; no email | Telling the admin | Admin only | Discord's own |
| The data file (`/admin/requests/{id}/export`) | Everything about one person, in words; no other person's name or id | Answering a request for one's data | Admin, at download (`no-store`); then whoever the admin sends it to (the person) | Not stored by the app; made on demand |
| Backups | Nightly `pg_dump` of everything above, Scaleway Object Storage fr-par | Restore | Admin | 30 days |
| Server logs (journald) | Time, method, status, scrubbed path; no tokens, emails or IPs | Operations | Admin | 14 days (`provision.sh`) |
| Cookies | `gf_session` (sign-in), `gf_theme` (light/dark, set in the browser) | Working at all | — | Session, one year |
