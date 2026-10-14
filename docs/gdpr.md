# GDPR analysis

Status: first pass, 2026-10-09, from the code and `deploy/`. Not legal advice; the legal-basis and controller questions at the end want a real answer (a lawyer, or the Belgian DPA's guidance for small organisations) before launch beyond friends.

The app is private by design (invite-only, `noindex`), small, hosted in the EU (Scaleway fr-par). That keeps most risks low. The gaps are mostly *missing data-subject features* and one structural problem: the event log.

## What personal data we hold

| Data | Where | Identifies? | Notes |
|---|---|---|---|
| Email, display name, signup time | `players` | Directly | Erased by `AccountEraser` (G2), run by the admin on request (G3) |
| Login codes/tokens (hashed), email, `return_to` | `login_attempts` | Email in clear | Purged after 1 day (`ATTEMPT_RETENTION`) |
| A pending change of email: the new address (in clear), hashed code | `email_changes` (G6) | Directly | One row per player; purged a day after it expires, and with the account |
| Last sign-in, inactivity warning time | `players.last_login_at`, `inactive_warned_at` (G7) | Via the account | Drives the inactive-account sweep; erased with the account. Not in the data file |
| Session token (hashed), player id, expiry | `sessions` | Via player id | Purged on expiry |
| BGG username, collection, sync status | `collection_syncs`, `collections`; username also in the `BggAccountLinked` **event** | Username is a public handle, linkable to a person | The event copy is not erasable |
| Taste profile: pairwise answers, "haven't played", wishes | events `ComparisonRecorded`, `GamePlayStatusDeclared`, `PlayWishChanged` | Pseudonymous (player UUID) | Preferences about leisure; profiling in the Art. 4(4) sense, low risk |
| Attendance, bringing, plays, bring requests, date-poll answers (dates, games yes/no, offers to bring), game nights | events (`PlayerJoined`, `GameBrought`, `PlayLogged`, `Bring*`, `DatePoll*`, `GameNightCreated`) | Pseudonymous | `PlayLogged` and `GameNightCreated` list the players (the night also who brings and who wants each game); others' data lives in the same events |
| Title, place (a home address in practice), date | events `GameDayCreated`, `GameDayDetailsChanged`, `DatePollCreated`, `GameNightCreated` (a copy of the poll's) | Host, indirectly | Free text, immutable once written |
| IP address | in-memory rate limiter for login mails (`Login.perAddress`); Ktor `CallLogging` | Yes | Not persisted by us: the request log doesn't record it (G8) |
| Cookies | `gf_session` (session), `gf_theme` (set client-side) | n/a | Both strictly necessary/functional: no consent banner needed, but name them in the notice |
| Open requests to delete or get one's data | `account_requests` (G3) | Via player id | Cascades with the account; the Discord/mail message to the admin holds a name and a link, no email |
| Mail content | Scaleway TEM (prod), Postmark/Resend optional | Email, name, recap | Processor |
| Erasure announcements | Discord channel (`AccountEraser`) | Player id only | After an erasure the id points at nobody, but Discord keeps the message as long as it likes and the log still holds that id's history; it is the list `REAPPLY_ERASURES` needs after a restore. Needs a human decision (see G7) |
| Backups | nightly `pg_dump`, 30 days at most (decided), Scaleway Object Storage fr-par | Everything above | Erasure reaches backups only by expiry |

## Processors and transfers

- **Scaleway** (hosting, TEM, Object Storage), all fr-par: EU, DPA via the Scaleway account terms. Good.
- **Postmark / Resend** (selectable via `MAIL_PROVIDER`): US companies, so a transfer needing SCCs/DPF. Keep TEM the documented default.
- **BGG** (boardgamegeek.com): not a recipient of our data. It's the other way round: we read BGG's public data (collection, game metadata) for the username the person links; the only thing sent is that username, in the request. Not a processor. The notice says where the collection comes from. The user puts a verification code on their own BGG profile, which is their own act.
- **GitHub Actions**: sees code and deploy secrets, no personal data.

## Findings

Ordered by how much they matter.

1. **No erasure path (Art. 17).** (Now reachable from `/account`: "Delete my account" asks the admin, who erases at `/admin/requests`; the person gets a confirmation mail.) `players` can be deleted, and `collections`/`sessions`/`collection_syncs` cascade, but the log keeps the player's id everywhere plus the BGG username, and free text (place, title). Deleting the row leaves a pseudonymous profile that is still tied together by one UUID. CLAUDE.md already allows rewriting the log "for erasure"; nothing implements it. This was the biggest gap. **Fixed (G2, 2026-10-12), by anonymising rather than rewriting the log:** erasing deletes the rows that say who a player was (`AccountEraser`) and appends `PlayerErased`; the player ids left in the events point at nobody. New BGG links store only a keyed hash of the username in the log (`BggAccountLinkedHashed`). What the log still holds is residue we accept for now: the BGG username in the older `BggAccountLinked` events (a public handle), and free-text `place`/title in game day, date poll and game night events (`GameNightCreated` copies the poll's). On game days still to be played, the erased player also leaves the table, the shelf (their games and wishes) and the bring requests both ways (BUG-025, in the projections, no log edit); closed game days keep their history, shown as "Someone". A game night (U11) has no end yet, so an erased player leaves every one (the people, the wishes; a game they'd bring becomes missing); an erased host's night stays, under an id that points at nobody. Date polls keep the erased player's answers under their id, as before. The group can also recognise a pseudonym from what it brought and played, which is why this leans on the household exemption.
2. **No access/portability (Art. 15, 20).** **Fixed (G3/G4, 2026-10-12), as a request to the admin:** `/account` has "Send me my data"; the admin downloads a JSON file at `/admin/requests/{id}/export` (`PersonalData`: profile, BGG username, collection, a history in words) and sends it to the person's own address. Other people never appear (a bring request says "someone", a play gives a head count). Open: the file names game days by title and place, free text that can mention others; and the admin has to be sure it goes to the right person (it goes to the account's address). No instant self-serve download.
3. **No privacy notice (Art. 13).** Nothing says who runs this, what we keep, why, for how long, who the processors are, or how to complain. The sign-in screen collects an email with no notice.
4. **Invite landing page shows the place to anyone with the link** (`/i/{token}`, and the Open Graph image `/i/{token}/og.png`, cached publicly 5 minutes). For a home-hosted game day that is an address exposed to anyone the link reaches, including WhatsApp/Slack link-preview crawlers. Data minimisation (Art. 5(1)(c)): show the place only after joining, or only a town.
5. **Rectification is thin (Art. 16).** Name can be renamed; email can't be changed; the BGG link can't be removed (and its event copy can't be undone). **Fixed (G6, 2026-10-12):** the email changes with a code sent to the new address (the old one gets a notice; an address another account has gets no mail but the same screen, so nothing leaks), and the BGG account can be unlinked (`BggAccountUnlinked`; collection and sync rows deleted). The username in older `BggAccountLinked` events stays (accepted residue, finding 1).
6. **Retention is undefined (Art. 5(1)(e)).** Accounts, sessions' sibling data and the event log live forever. Nothing says when an unused account goes. **Fixed (G7, 2026-10-12):** an account with no sign-in for `INACTIVE_ACCOUNT_MONTHS` (24) gets a warning, and is erased 30 days later (`InactiveAccounts`; the admin and hosts of a coming game day are skipped). **Place retention, decided:** the place stays in the events (game days, date polls, and the game night a poll makes), because it is shown only to people who joined, an erased host's id points at nobody, and blanking it means rewriting the log. Revisit if a host asks or the group grows. The event log itself is kept; its ids are anonymised on erasure.
7. **Backups outlive erasure.** A restore would resurrect an erased player. Needs a documented rule (30 days, then gone) and an erasure ledger the restore procedure re-applies. **Fixed (G7, 2026-10-12):** backups last 30 days at most and `backup.sh` ends failed if an older dump is left. The ledger is the admin's Discord channel (a table would be replaced by the restore): every erasure posts its player id, and `REAPPLY_ERASURES=id,id` re-runs them at startup (`deploy/README.md`). Weak spot: it depends on the channel (or the deletion mails) being kept; the webhook falling back to mail loses nothing, but a Discord failure with no mail fallback would.
8. **Secrets in URLs and logs.** `/login/link?token=…` and `/i/{token}` carry bearer tokens in the path/query; `CallLogging` logs request lines. Confirm what lands in the journal, and how long journald keeps it. **Fixed (G8):** request lines go through `loggable()` (method, scrubbed path, status; no query, no `/i/` or `/p/` token, no IP), and journald keeps 14 days. **BUG-026 (2026-10-14):** the dev mailbox no longer logs the address or the subject (which holds the code), a refused mail logs only the provider's status (its body can echo the recipient), BGG sync errors name the player id instead of the BGG username, and a BGG error names the endpoint without its query. Pinned by `LogSafetyTest` and `HttpMailerTest`.
9. **Other members see your name, your collection (on the ask screen) and your answers' effects** (suggestions say who wants a game). A date poll that asks about games (U11) does not widen this: the host proposes games from their own collection or BGG search only, on the poll form and the poll page. (Briefly, U11 let the host browse every poll member's collection; that was taken back on 2026-10-14, and the switch, `BringRequestsAllowedChanged`, again speaks of game days only.) Poll members see who would play a game and who brings it; only the host sees who said no. That is the purpose of the service; say so in the notice. The recap mail names everyone who joined.
10. **No breach procedure (Art. 33).** 72 hours to the DPA; nothing written down.

Fine as is: hashed single-use tokens, `noindex`, no third-party fonts (self-hosted, so no IP leak to Google), no analytics/trackers, EU hosting, rate limiting, no guests, `no-store` caching on pages.

## Household exemption: approval flow (G9, done)

New accounts are approved by hand by the admin (`ADMIN_EMAIL`) before they can do anything. This makes the closed, personal circle visible and demonstrable, which is what the Art. 2(2)(c) argument rests on. It does **not** settle it: Recital 18 says people providing the *means* for others' personal activity can still be controllers, and a link to Kunlabora (hosting, billing, work time) undermines "no professional connection". Keep G1 to G8 regardless.

## Open questions (need a human)

- Who is the controller, and does the household/personal-activity exemption apply to a group of friends? If it grows beyond that, assume it doesn't.
  - **Decided (2026-10-12):** we apply the household/personal-activity exemption. The privacy notice (G1) says so, and still keeps the transparency, erasure and export promises. If the circle grows beyond friends, revisit. The notice is the light version, and "delete me" / "send me my data" are buttons that post to the admin's Discord channel (G3, G4), with mail as the fallback.
- Legal basis: Art. 6(1)(b) (the service the person asked for) fits the account and game-day data; taste profiling is arguably the service itself, not a separate consent. Confirm.
- Is a records-of-processing entry (Art. 30) required at this size? Cheap to keep anyway: G1 produces it.

## Stories

All in the [backlog](backlog.md#gdpr-2026-10-09). Suggested order: G1 and G2 first (G2 is the hard one); G3 to G8 are independent of each other.

| # | Story | Size | Depends on |
|---|---|---|---|
| G1 | [Privacy notice and processing register](requests/g1-privacy-notice.md) | S | |
| G2 | [Erase my account: the event log](requests/g2-erasure-event-log.md) | L | |
| G3 | [Erase my account: the screen](requests/g3-erasure-ui.md) | M | G2 |
| G4 | [Download my data](requests/g4-data-export.md) | M | |
| G5 | [Don't show the place to non-members](requests/g5-invite-place.md) | S | |
| G6 | [Change email, unlink BGG](requests/g6-rectification.md) | M | G2 |
| G7 | [Retention, backups and restore](requests/g7-retention-backups.md) | M | G2 |
| G8 | [Logs and URLs: no secrets, short retention](requests/g8-logs.md) | S | |
