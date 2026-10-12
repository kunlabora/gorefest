# Backlog

Status: 📋 todo · ⏭ next up · 🚧 doing · ✅ done. Move a story by changing its status here; tick acceptance criteria in its section as they land.
A story is done when every criterion is ticked and covered by a test (decider given/when/then where there's a decision, a web test where there's a page).

| #   | Story                                             | Status | Depends on |
|-----|---------------------------------------------------|--------|------------|
| S0  | [Walking skeleton](#s0-walking-skeleton)          | ✅     |            |
| S1  | [Deploy pipeline](#s1-deploy-pipeline)            | ✅     | S0         |
| S2  | [Magic-link login](#s2-magic-link-login)          | ✅     | S0         |
| S3  | [Link BGG account](#s3-link-bgg-account)          | ✅     | S2         |
| S4  | [Collection sync](#s4-collection-sync)            | ✅     | S3         |
| S5  | [Create and edit a game day](#s5-create-and-edit-a-game-day) | ✅ | S2   |
| S6  | [Invite link and landing page](#s6-invite-link-and-landing-page) | ✅ | S5 |
| S7  | [Bring games from my collection](#s7-bring-games-from-my-collection) | ✅ | S4, S6 |
| S8  | [Bring a game by search](#s8-bring-a-game-by-search) | ✅  | S7         |
| S9  | [Taste model v0: per-game scores](#s9-taste-model-v0-per-game-scores) | ✅ | S0 |
| S10 | [First-time wizard](#s10-first-time-wizard)       | ✅     | S7, S9     |
| S11 | [Ad hoc suggestions](#s11-ad-hoc-suggestions)     | ✅     | S10        |
| S12 | [Checkpoint play log](#s12-checkpoint-play-log)   | ✅     | S11        |
| S13 | [Host readiness view](#s13-host-readiness-view)   | ✅     | S10        |
| S14 | [Returning-player top-up](#s14-returning-player-top-up) | ✅ | S10      |
| S15 | [Taste model v1: features, appeal vs enjoyment](#s15-taste-model-v1-features-appeal-vs-enjoyment) | ✅ | S12 |
| S16 | [Auto-close and summary](#s16-auto-close-and-summary) | ✅   | S12        |
| S17 | [Find a date with a date poll](#s17-find-a-date-with-a-date-poll) | ✅ | S2, S5 |
| | **User testing, 2026-10-06** | | |
| U1 | [Joining the game day straight after signing up](requests/u1-join-after-signup.md) | ✅ | |
| U2 | [Don't suggest games a player said they don't know](requests/u2-no-suggestion-for-unknown-game.md) | ✅ | |
| U3 | [Absurd playtimes (7th Continent: 5-1000 min, Sleeping Gods: 60-1200 min)](requests/u3-absurd-playtimes.md) | ✅ | |
| U4 | [Show a game's type/category to help make choices](requests/u4-game-type-in-choices.md) | ✅ | |
| U5 | [Replace "Don't know" in the wizard with clearer "Haven't played it" wording](requests/u5-haven-t-played-wording.md) | ✅ | |
| U6 | ["We haven't learned your taste" shown right after a taste test](requests/u6-taste-not-learned-bug.md) | ✅ | |
| U7 | [Log plays during the game day, not only after it](requests/u7-log-plays-during-game-day.md) | ✅ | |
| U8 | [Does logging a play influence the matching?](requests/u8-does-play-log-influence-matching.md) | ✅ | |
| U9 | [Having seen a table play a game helps form an opinion](requests/u9-seen-it-played.md) | 📋 | |
| U10 | [Indicate which shelf game you really want to play](requests/u10-want-to-play-pick.md) | ✅ | |
| U11 | [Organise a small game evening from people's libraries](requests/u11-small-evening-from-library.md) | 📋 | |
| U12 | [Player avatar circles have bad contrast with list items](requests/u12-avatar-contrast.md) | ✅ | |
| U13 | [Placeholder text is too prominent](requests/u13-placeholder-colour.md) | ✅ | |
| U14 | [Fun default game day titles](requests/u14-game-day-title-generator.md) | ✅ | |
| U15 | [Loading indicator during active search](requests/u15-search-loading-indicator.md) | ✅ | |
| U16 | [Cancel a game day](requests/u16-cancel-game-day.md) | ✅ | |
| U17 | [Link BGG by a message from a Boardfest bot account](requests/u17-bgg-link-via-bot-message.md) | 📋 | |
| U18 | ["Your likely favourites today": pills overlap the bottom after scrolling](requests/u18-favourites-pills-overlap.md) | ✅ | |
| U19 | [Update emails when people add games (and push as an alternative)](requests/u19-update-emails-and-push.md) | 📋 | |
| U20 | [Ask someone to bring a game from their collection](requests/u20-ask-someone-to-bring-a-game.md) | ✅ | |

The screen-by-screen visual redesign has its own list: [redesign-backlog.md](redesign-backlog.md).

S1–S13 is the MVP. S14–S16 make it good.

---

## S0 Walking skeleton

*As a developer I want the architecture wired end to end so that feedback on it is cheap.*

- [x] `events` table with GIN index on tags, created by a Flyway migration
- [x] Append takes a global advisory lock and fails if an event matching the decision's query was appended after the read position
- [x] Deciders are pure (`query`, `evolve`, `decide`) and tested with given/when/then
- [x] Command handler retries a decision when a concurrent append conflicts
- [x] In-memory projection rebuilt from the log on startup and caught up after each append
- [x] Server-rendered page with htmx and hyperscript: create a game day, view it, join it
- [x] Fat jar runs against Postgres from `compose.yaml`; tests use Testcontainers

## S1 Deploy pipeline

*As the host I want the app on a real URL so that friends can use it.*

- [x] GitHub Action on push to `main`: build, test, `scp` fat jar, restart the systemd unit. **Written** (`ci.yml` deploy job, `deploy/deploy.sh`); runs
- [x] Hosting on Scaleway: `deploy/provision.sh` sets up a fresh Ubuntu instance, `deploy/scaleway.md` walks through the console. **Written**, run
- [x] Caddy terminates TLS and proxies to the app on localhost. **Written** (`deploy/Caddyfile`); the app trusts `X-Forwarded-For` because it listens on localhost only
- [x] App config (DB URL, mail API key, signing secret) comes from an env file that isn't in the repo (`deploy/env.example`); outside dev mode the app refuses to start without mail and `SECRET_KEY`; without `BGG_TOKEN` it starts with BGG switched off (`UnavailableBggApi`, logged as a warning)
- [x] Nightly `pg_dump` uploaded to object storage; restoring it into a fresh database is tested once and written down. **Written** (`backup.sh`, systemd timer, restore steps in `deploy/README.md`); restore tested
- [x] `/health` checks DB connectivity; a failed deploy leaves the previous jar running (`deploy.sh` waits for health and switches back)
- [x] A real mail provider sends login mail; logging in from WhatsApp's and Signal's in-app browsers works on a real phone, and invite links show their preview there. **Scaleway TEM, Postmark and Resend clients written** (`MAIL_PROVIDER`); phone check done
- [x] The server has fontconfig and a font installed, for the invite preview image (in the setup steps)
- [x] BGG is credited on every page and game names link to BGG; the site tells search engines to stay away (`robots.txt`, `X-Robots-Tag`, meta tag)
- [x] BGG application registered, `BGG_TOKEN` set, and the XML parsing checked against live responses (fixtures were written from the docs; checked live)
- [x] Login mails are rate-limited: 5 per email and 20 per IP per hour

## S2 Magic-link login

*As a player I want to log in with just my email so that there's no password to forget.*

- [x] Entering an email sends one mail containing both a magic link and a 6-digit code (the code is also in the subject, so it shows in the notification)
- [x] Either one logs me in; both stop working once one is used, and after 15 minutes
- [x] Tokens and codes are random and stored hashed (codes with an HMAC keyed by `SECRET_KEY`); after 5 wrong codes that login attempt is dead
- [x] First login emits `PlayerRegistered`, every login emits `LoggedIn`
- [x] A long-lived session cookie keeps me logged in; logging out ends the session
- [x] The code works in any browser, so login works from WhatsApp/Signal's in-app browser (checked on a real phone in S1)
- [x] The dev-player stub in `CurrentPlayer.kt` is gone; unauthenticated requests go to login and come back afterwards
- [x] **Decided:** email and display name live in a plain `players` table, not in events, so a player can be erased (GDPR)
- [x] Added: first login asks for a display name; the magic link opens a "Let me in" confirm page, so mail scanners that pre-fetch links can't use it up
- [x] Added: in dev mode, mail goes to `/dev/mailbox` instead of being sent

Follow-ups, not blocking:
- Delete expired login attempts and sessions nightly
- Let players change their name later

## S3 Link BGG account

*As a player I want to link my BGG username so that the app knows my games.*

- [x] Before any code: current BGG XML API terms checked (registration + bearer token required since 2025), findings noted in CLAUDE.md. Registering the app is yours: see S1
- [x] I enter my BGG username and get a short code (`GORE-XXXXXX`) to put in my BGG profile
- [x] "Verify" fetches my profile and emits `BggAccountLinked` when the code is there
- [x] A BGG username can be linked to only one player (DCB query on a `bgg:{username}` tag)
- [x] Linking is optional; I can skip it and come back later (home page suggests it until done)
- [x] Added: dev mode without `BGG_TOKEN` uses a fake BGG with a catalogue of ~45 well-known games

## S4 Collection sync

*As a player with a linked BGG account I want my collection imported so that I can bring games without typing them.*

- [x] One in-process worker makes all BGG calls through a token bucket of about 1 request per 5 seconds (burst of 3)
- [x] Priority queue: live searches and fresh account links go before routine refreshes
- [x] BGG's "202, try again later" collection response is retried with backoff
- [x] Game metadata (name, image, player counts, best-count poll, playtime, weight, mechanics, categories, rank) is fetched once per game globally and cached with `fetched_at`
- [x] My collection shows up within a few minutes of linking, with progress visible (`/collection` polls every 2 s)
- [x] Collection is refreshed periodically (daily check, refresh after 7 days); games removed on BGG disappear from it
- Later: refresh cached game metadata (ranks, weight) monthly

## S5 Create and edit a game day

*As a host I want to set up a game day so that I can invite people to it.*

- [x] Title, date, place and time zone; the host auto-joins (in the skeleton)
- [x] Only logged-in players can create one (S2)
- [x] Only the host can edit title, date and place (`GameDayDetailsChanged`); the time zone stays as created
- [x] My game days (hosting or joined) are listed on my home page, upcoming first (soonest first), then past ones

## S6 Invite link and landing page

*As a host I want to share one link in the group chat so that friends can join.*

- [x] The game day page has a copyable invite link (signed token, valid until the game day ends), for everyone who joined
- [x] The host can rotate the link (`InviteLinkRotated`); the old link stops working
- [x] The link has Open Graph tags and a rendered 1200×630 image: title, date, going, shelf size, not the place, which only members see (G5; shelf count comes with S7; checked in real chat apps in S1)
- [x] Opening the link shows a landing page (host, date, place, who's going) without logging in
- [x] "Join" goes through login if needed, then emits `PlayerJoined` and lands me on the game day
- [x] An ended game day's link shows "this game day has ended"
- [x] Added: joining only works through a valid invite; the game day page is for members only

## S7 Bring games from my collection

*As a participant I want to tick the games I'm bringing so that everyone knows what's on the shelf.*

- [x] The bring page lists my collection with a "bringing" switch (`GameBrought` / `GameUnbrought`)
- [x] The shelf shows every brought game with who brings it (game day hub, invite landing, preview image)
- [x] Two people bringing the same game is allowed and shown once, with both owners
- [x] I can mark a brought game "I really want to play this" (`PlayWishChanged`, used by S11)
- [x] Only players who joined can bring games; no changes after the game day ended

## S8 Bring a game by search

*As a participant I want to add a game that's not in my BGG collection, e.g. on the day.*

- [x] Search looks in the local metadata cache first and falls back to BGG search through the worker (LIVE priority) when that finds fewer than 5
- [x] Results appear as I type (htmx, debounced 300 ms); picking one brings it
- [x] A game found via search gets its metadata fetched and cached like a collection game
- [x] Added: when BGG is down, local matches still show, with a note

## S9 Taste model v0: per-game scores

*As a developer I want a replayable taste model so that algorithm changes can be compared.*

- [x] `ComparisonRecorded(player, a, b, result, basis, at)` with result A, B, BOTH_GREAT or BOTH_MEH
- [x] **Decided:** "haven't played" is `GamePlayStatusDeclared(player, game, played)`, so the wizard switches to VIBE and stops asking PLAYED for that game
- [x] Per-player Bradley–Terry scores with uncertainty, separate for VIBE and PLAYED, fitted from the `Tastes` projection; ties also count against a virtual average game, so "both great" lifts both
- [x] Time decay with a configurable half-life (default 9 months)
- [x] A replay harness (`Evaluation`) predicts each comparison from earlier ones only and reports accuracy and log loss per model version; v0 beats the coin flip and gets within 0.1 of the best possible on a synthetic player

## S10 First-time wizard

*As a new participant I want to answer a few quick "which one?" questions so that suggestions fit my taste.*

- [x] Each step shows two games (image, name, one-line facts) with: A, B, haven't played, both meh, both great ("Haven't played either" and "Haven't played this" per game, since U5)
- [x] Games come from shelf → host's collection → my collection → BGG top-ranked, with every fifth pair an anchor
- [x] The next pair is the one the model is most uncertain about (closeness × combined variance)
- [x] Stops after 15 pairs, or from 10 once the top 3 hasn't changed for three answers
- [x] Payoff screen: "Your likely favourites today" from this game day's shelf, with a reason each; an empty shelf suggests games from the host's collection
- [x] Works one-handed on a phone; each answer is a single tap with no full page reload; A/B keys work on a keyboard
- [x] Added: the game day hub's next-step card leads to the taste round, then to the favourites

## S11 Ad hoc suggestions

*As a table I want three suggestions so that we stop debating and start playing.*

- [x] I tick who's at the table (joined players only) and optionally a time budget
- [x] Candidates: shelf games whose BGG best/recommended player count includes the table size and that fit the time budget
- [x] Top 3 by mean predicted enjoyment minus λ·spread (λ = 0.5), with novelty and owner-desire bonuses
- [x] Each suggestion has a one-line reason (owner's wish, novelty, safe bet, best player count, time)
- [x] Unplayed games use appeal (VIBE), played ones enjoyment (PLAYED); players with thin profiles count for less
- [x] "Nothing fits" is shown clearly, with a button that loosens the time budget when that would help

## S12 Checkpoint play log

*As a table I want to log what we played so that later suggestions get better.*

- [x] At a checkpoint: pick game, players, minutes played (`PlayLogged`); on the day the hub leads with it (morning / lunch / evening in the game day's time zone)
- [x] Then one PLAYED comparison against a game I've played before (the one we know least about)
- [x] Suggestions refresh right after, for the table that just played
- [x] Learned group speed from `PlayLogged` adjusts duration estimates (median ratio to box time, 0.6–1.8×)
- [x] Added: the hub lists what was played today; logged games count as played for everyone at the table

## S13 Host readiness view

*As a host I want to see if we're ready so that I can nudge people before the day.*

- [x] Who joined, and whose profile is thin (under 10 answers), those first
- [x] Shelf size, and gaps like "nothing plays well at 6" (table sizes 3 and up that fewer than 3 shelf games serve)
- [x] One-tap nudge for thin profiles. **Decided for now:** it copies a friendly message with a link to the taste round, to paste in the chat (no mail sent). Revisit once mail is live

## S14 Returning-player top-up

*As a returning player I want a short round on new shelf games only.*

- [x] Only shelf games whose prediction for me is uncertain; 3–5 pairs (every pair includes one)
- [x] Occasionally re-asks an old pair to keep time decay honest (the oldest answer over 6 months old, first)
- [x] Added: the hub's next-step card says how many shelf games are still uncertain

## S15 Taste model v1: features, appeal vs enjoyment

*As a player I want good suggestions for games I've never compared.*

- [x] Feature weights over mechanics, categories, weight, playtime and best-count poll, separate for appeal (VIBE) and enjoyment (PLAYED). BGG has no "interaction" field; mechanics stand in for it
- [x] Per-player calibration: how well my vibes predict my post-play enjoyment (`vibeTrust`), used to blend appeal and expected enjoyment for unplayed games
- [x] Beats v0 on the replay harness (lower log loss, higher accuracy on a feature-driven player) and now replaces it in the app

## S16 Auto-close and summary

*As a player I want a recap after the game day.*

- [x] `GameDayEnded` at midnight in the game day's time zone (checked every 5 minutes; idempotent)
- [x] Everyone who joined gets a summary email: what was played, by whom, how long (once: only the run that ends it mails)
- [x] No more bringing, joining or play logging after close

## S17 Find a date with a date poll

*As a host I want to know which date suits the group best before I set up a game day.*

- [x] A host starts a poll with a title, a place and two to fourteen dates in the future (`DatePollCreated`); the host is in it from the start
- [x] People come in through a signed invite link (`/p/{token}`), like game days: no email invites, everyone has a profile (`DatePollJoined`)
- [x] Each participant ticks the dates they can make, yes or no, and can change their answer until the pick (`DatePollAnswered`; saves as you tick)
- [x] Everyone in the poll sees who can make which date, who has yet to answer, and the best date so far (most people; ties go to the earliest)
- [x] Only the host picks. The poll closes and the game day is created with the picked date, the title, the place and the time zone, and the host plus everyone who ticked that date already in it (`DatePollClosed`, `GameDayCreated`, `PlayerJoined`)
- [x] A closed poll's invite says the date is picked; people who weren't available aren't on the game day and are told how to ask for the link
- [x] Open polls show on the home page; "Find a date" is offered next to "Host a game day"
- [ ] Nudge people who haven't answered (needs a way to reach them: only in-app so far)
- [ ] The host can replace a poll's invite link, like a game day's (`InviteLinkRotated` has no poll counterpart yet): until the date is picked, anyone with the link can join, and a leaked one can't be revoked

---

## User testing feedback, 2026-10-06

One request file per item in [`docs/requests/`](requests/); each has its own criteria. Decisions: U16 cancels silently; U9, U11 and U19 are recorded only; U17 needs BGG's permission before any work.

---

## GDPR, 2026-10-09

Analysis in [`gdpr.md`](gdpr.md); one request file per story. G2 is the hard one (rewriting the immutable log); G1, G4, G5 and G8 are independent and small.

| #   | Story | Status | Depends on |
|-----|-------|--------|------------|
| G1 | [Privacy notice and processing register](requests/g1-privacy-notice.md) | ✅ |  |
| G2 | [Erase my account: the event log](requests/g2-erasure-event-log.md) | ✅ |  |
| G3 | [Erase my account: the screen](requests/g3-erasure-ui.md) | 📋 | G2 |
| G4 | [Download my data](requests/g4-data-export.md) | 📋 |  |
| G5 | [Don't show the place to non-members](requests/g5-invite-place.md) | ✅ |  |
| G6 | [Change email, unlink BGG](requests/g6-rectification.md) | 📋 | G2 |
| G7 | [Retention, backups and restore](requests/g7-retention-backups.md) | 📋 | G2 |
| G8 | [Logs and URLs: no secrets, short retention](requests/g8-logs.md) | ✅ |  |
| G9 | Manual approval of new accounts: `/pending`, `/admin/approvals`, `ADMIN_EMAIL` (supports the household-exemption argument; see `gdpr.md`) | ✅ | |
