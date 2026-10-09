# GDPR analysis

Status: first pass, 2026-10-09, from the code and `deploy/`. Not legal advice; the legal-basis and controller questions at the end want a real answer (a lawyer, or the Belgian DPA's guidance for small organisations) before launch beyond friends.

The app is private by design (invite-only, `noindex`), small, hosted in the EU (Scaleway fr-par). That keeps most risks low. The gaps are mostly *missing data-subject features* and one structural problem: the event log.

## What personal data we hold

| Data | Where | Identifies? | Notes |
|---|---|---|---|
| Email, display name, signup time | `players` | Directly | Erasable today only by deleting the row by hand |
| Login codes/tokens (hashed), email, `return_to` | `login_attempts` | Email in clear | Purged after 1 day (`ATTEMPT_RETENTION`) |
| Session token (hashed), player id, expiry | `sessions` | Via player id | Purged on expiry |
| BGG username, collection, sync status | `collection_syncs`, `collections`; username also in the `BggAccountLinked` **event** | Username is a public handle, linkable to a person | The event copy is not erasable |
| Taste profile: pairwise answers, "haven't played", wishes | events `ComparisonRecorded`, `GamePlayStatusDeclared`, `PlayWishChanged` | Pseudonymous (player UUID) | Preferences about leisure; profiling in the Art. 4(4) sense, low risk |
| Attendance, bringing, plays, bring requests, date-poll answers | events (`PlayerJoined`, `GameBrought`, `PlayLogged`, `Bring*`, `DatePoll*`) | Pseudonymous | `PlayLogged` lists the players; others' data lives in the same events |
| Title, place (a home address in practice), date | events `GameDayCreated`, `GameDayDetailsChanged`, `DatePollCreated` | Host, indirectly | Free text, immutable once written |
| IP address | in-memory rate limiter for login mails (`Login.perAddress`); Ktor `CallLogging` | Yes | Not persisted by us; check what the logs and journald keep |
| Cookies | `gf_session` (session), `gf_theme` (set client-side) | n/a | Both strictly necessary/functional: no consent banner needed, but name them in the notice |
| Mail content | Scaleway TEM (prod), Postmark/Resend optional | Email, name, recap | Processor |
| Backups | nightly `pg_dump`, 30 days at most (decided), Scaleway Object Storage fr-par | Everything above | Erasure reaches backups only by expiry |

## Processors and transfers

- **Scaleway** (hosting, TEM, Object Storage), all fr-par: EU, DPA via the Scaleway account terms. Good.
- **Postmark / Resend** (selectable via `MAIL_PROVIDER`): US companies, so a transfer needing SCCs/DPF. Keep TEM the documented default.
- **BGG** (boardgamegeek.com): we send the linked username to their API. They are a separate controller, not our processor. Mention it in the notice. The user puts a verification code on their own BGG profile, which is their own act.
- **GitHub Actions**: sees code and deploy secrets, no personal data.

## Findings

Ordered by how much they matter.

1. **No erasure path (Art. 17).** `players` can be deleted, and `collections`/`sessions`/`collection_syncs` cascade, but the log keeps the player's id everywhere plus the BGG username, and free text (place, title). Deleting the row leaves a pseudonymous profile that is still tied together by one UUID. CLAUDE.md already allows rewriting the log "for erasure"; nothing implements it. This is the biggest gap.
2. **No access/portability (Art. 15, 20).** No way to see or download "my data".
3. **No privacy notice (Art. 13).** Nothing says who runs this, what we keep, why, for how long, who the processors are, or how to complain. The sign-in screen collects an email with no notice.
4. **Invite landing page shows the place to anyone with the link** (`/i/{token}`, and the Open Graph image `/i/{token}/og.png`, cached publicly 5 minutes). For a home-hosted game day that is an address exposed to anyone the link reaches, including WhatsApp/Slack link-preview crawlers. Data minimisation (Art. 5(1)(c)): show the place only after joining, or only a town.
5. **Rectification is thin (Art. 16).** Name can be renamed; email can't be changed; the BGG link can't be removed (and its event copy can't be undone).
6. **Retention is undefined (Art. 5(1)(e)).** Accounts, sessions' sibling data and the event log live forever. Nothing says when an unused account goes.
7. **Backups outlive erasure.** A restore would resurrect an erased player. Needs a documented rule (30 days, then gone) and an erasure ledger the restore procedure re-applies.
8. **Secrets in URLs and logs.** `/login/link?token=…` and `/i/{token}` carry bearer tokens in the path/query; `CallLogging` logs request lines. Confirm what lands in the journal, and how long journald keeps it.
9. **Other members see your name, your collection (on the ask screen) and your answers' effects** (suggestions say who wants a game). That is the purpose of the service; say so in the notice. The recap mail names everyone who joined.
10. **No breach procedure (Art. 33).** 72 hours to the DPA; nothing written down.

Fine as is: hashed single-use tokens, `noindex`, no third-party fonts (self-hosted, so no IP leak to Google), no analytics/trackers, EU hosting, rate limiting, no guests, `no-store` caching on pages.

## Open questions (need a human)

- Who is the controller, and does the household/personal-activity exemption apply to a group of friends? If it grows beyond that, assume it doesn't.
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
