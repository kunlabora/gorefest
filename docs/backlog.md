# Backlog

Status: 📋 todo · 🚧 doing · ✅ done. Move a story by changing its status here; tick acceptance criteria in its section as they land.
A story is done when every criterion is ticked and covered by a test (decider given/when/then where there's a decision, a web test where there's a page).

| #   | Story                                             | Status | Depends on |
|-----|---------------------------------------------------|--------|------------|
| S0  | [Walking skeleton](#s0-walking-skeleton)          | ✅     |            |
| S1  | [Deploy pipeline](#s1-deploy-pipeline)            | 📋     | S0         |
| S2  | [Magic-link login](#s2-magic-link-login)          | 📋     | S0         |
| S3  | [Link BGG account](#s3-link-bgg-account)          | 📋     | S2         |
| S4  | [Collection sync](#s4-collection-sync)            | 📋     | S3         |
| S5  | [Create and edit a game day](#s5-create-and-edit-a-game-day) | 📋 | S2   |
| S6  | [Invite link and landing page](#s6-invite-link-and-landing-page) | 📋 | S5 |
| S7  | [Bring games from my collection](#s7-bring-games-from-my-collection) | 📋 | S4, S6 |
| S8  | [Bring a game by search](#s8-bring-a-game-by-search) | 📋  | S7         |
| S9  | [Taste model v0: per-game scores](#s9-taste-model-v0-per-game-scores) | 📋 | S0 |
| S10 | [First-time wizard](#s10-first-time-wizard)       | 📋     | S7, S9     |
| S11 | [Ad hoc suggestions](#s11-ad-hoc-suggestions)     | 📋     | S10        |
| S12 | [Checkpoint play log](#s12-checkpoint-play-log)   | 📋     | S11        |
| S13 | [Host readiness view](#s13-host-readiness-view)   | 📋     | S10        |
| S14 | [Returning-player top-up](#s14-returning-player-top-up) | 📋 | S10      |
| S15 | [Taste model v1: features, appeal vs enjoyment](#s15-taste-model-v1-features-appeal-vs-enjoyment) | 📋 | S12 |
| S16 | [Auto-close and summary](#s16-auto-close-and-summary) | 📋   | S12        |

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

- [ ] GitHub Action on push to `main`: build, test, `scp` fat jar, restart the systemd unit
- [ ] Caddy terminates TLS and proxies to the app on localhost
- [ ] App config (DB URL, mail API key, signing secret) comes from an env file that isn't in the repo
- [ ] Nightly `pg_dump` uploaded to object storage; restoring it into a fresh database is tested once and written down
- [ ] `/health` checks DB connectivity; a failed deploy leaves the previous jar running

## S2 Magic-link login

*As a player I want to log in with just my email so that there's no password to forget.*

- [ ] Entering an email sends one mail containing both a magic link and a 6-digit code
- [ ] Either one logs me in; both stop working once one is used, and after 15 minutes
- [ ] Tokens and codes are random and stored hashed; after 5 wrong codes that login attempt is dead
- [ ] First login emits `PlayerRegistered`, every login emits `LoggedIn`
- [ ] A long-lived session cookie keeps me logged in; logging out ends the session
- [ ] Login works when the link is opened in WhatsApp/Signal's in-app browser (the code path)
- [ ] The dev-player stub in `CurrentPlayer.kt` is gone; unauthenticated requests go to login
- [ ] **Decision needed:** email and display name live in a plain `players` table, not in events, so a player can be erased (GDPR)

## S3 Link BGG account

*As a player I want to link my BGG username so that the app knows my games.*

- [ ] Before any code: current BGG XML API terms checked, app registered/token obtained if required, findings noted in CLAUDE.md
- [ ] I enter my BGG username and get a short code to put in my BGG profile
- [ ] "Verify" fetches my profile and emits `BggAccountLinked` when the code is there
- [ ] A BGG username can be linked to only one player (DCB query on a `bgg:{username}` tag)
- [ ] Linking is optional; I can skip it and come back later

## S4 Collection sync

*As a player with a linked BGG account I want my collection imported so that I can bring games without typing them.*

- [ ] One in-process worker makes all BGG calls through a token bucket of about 1 request per 5 seconds
- [ ] Priority queue: live searches and fresh account links go before routine refreshes
- [ ] BGG's "202, try again later" collection response is retried with backoff
- [ ] Game metadata (name, image, player counts, best-count poll, playtime, weight, mechanics, categories) is fetched once per game globally and cached with `fetched_at`
- [ ] My collection shows up within a few minutes of linking, with progress visible
- [ ] Collection is refreshed periodically; games removed on BGG disappear from it

## S5 Create and edit a game day

*As a host I want to set up a game day so that I can invite people to it.*

- [x] Title, date, place and time zone; the host auto-joins (in the skeleton)
- [ ] Only logged-in players can create one
- [ ] Only the host can edit title, date and place (`GameDayDetailsChanged`)
- [ ] My game days (hosting or joined) are listed on my home page, upcoming first

## S6 Invite link and landing page

*As a host I want to share one link in the group chat so that friends can join.*

- [ ] The game day page has a copyable invite link (signed token, valid until the game day ends)
- [ ] The host can rotate the link (`InviteLinkRotated`); the old link stops working
- [ ] Pasting the link in WhatsApp/Signal shows an Open Graph preview: title, date, place, shelf size
- [ ] Opening the link shows a landing page (host, date, place, shelf) without logging in
- [ ] "Join" goes through login if needed, then emits `PlayerJoined` and lands me on the game day
- [ ] An ended game day's link shows "this game day has ended"

## S7 Bring games from my collection

*As a participant I want to tick the games I'm bringing so that everyone knows what's on the shelf.*

- [ ] The game day page lists my collection with a "bringing" toggle (`GameBrought` / `GameUnbrought`)
- [ ] The shelf shows every brought game with who brings it
- [ ] Two people bringing the same game is allowed and shown once, with both owners
- [ ] I can mark a brought game "I really want to play this" (owner's desire, used by S11)
- [ ] Only players who joined can bring games; no changes after the game day ended

## S8 Bring a game by search

*As a participant I want to add a game that's not in my BGG collection, e.g. on the day.*

- [ ] Search looks in the local metadata cache first and falls back to BGG search through the worker
- [ ] Results appear as I type (htmx, debounced); picking one brings it
- [ ] A game found via search gets its metadata fetched and cached like a collection game

## S9 Taste model v0: per-game scores

*As a developer I want a replayable taste model so that algorithm changes can be compared.*

- [ ] `ComparisonRecorded(player, winner, loser, basis, at)` plus a tie outcome for "both great" / "both meh"
- [ ] **Decision needed:** how "haven't played" is recorded (proposal: `GamePlayStatusDeclared(player, game, played)`) so the wizard switches to VIBE and stops asking PLAYED for that game
- [ ] Per-player Bradley–Terry scores with uncertainty, separate for VIBE and PLAYED, as a pure projection
- [ ] Time decay with a configurable half-life (default 9 months)
- [ ] A replay harness runs two model versions over the same log and reports how well each predicts held-out comparisons

## S10 First-time wizard

*As a new participant I want to answer a few quick "which one?" questions so that suggestions fit my taste.*

- [ ] Each step shows two games (image, name, one-line facts) with: A, B, haven't played, both meh, both great
- [ ] Games come from shelf → host's collection → my collection → BGG top-ranked, with about 20% popular anchors
- [ ] The next pair is the one the model is most uncertain about
- [ ] Stops after 10–15 pairs, or earlier when predictions stop changing
- [ ] Payoff screen: "Your likely favourites today: X, Y, Z" from this game day's shelf
- [ ] Works one-handed on a phone; each answer is a single tap with no full page reload

## S11 Ad hoc suggestions

*As a table I want three suggestions so that we stop debating and start playing.*

- [ ] I tick who's at the table (joined players only) and optionally a time budget
- [ ] Candidates: shelf games whose BGG best/recommended player count includes the table size and that fit the time budget
- [ ] Top 3 by mean predicted enjoyment minus λ·spread, with novelty and owner-desire bonuses
- [ ] Each suggestion has a one-line reason ("everyone's likely to enjoy it, nobody's played it")
- [ ] Unplayed games blend appeal and enjoyment; players with thin profiles count for less
- [ ] "Nothing fits" is shown clearly, with the constraint to relax

## S12 Checkpoint play log

*As a table I want to log what we played so that later suggestions get better.*

- [ ] At a checkpoint: pick game, players, minutes played (`PlayLogged`)
- [ ] Then one PLAYED comparison against a game I've played before
- [ ] Suggestions refresh right after
- [ ] Learned group speed from `PlayLogged` adjusts duration estimates

## S13 Host readiness view

*As a host I want to see if we're ready so that I can nudge people before the day.*

- [ ] Who joined, and whose profile is thin (few comparisons)
- [ ] Shelf size, and gaps like "nothing plays well at 6"
- [ ] One-tap nudge for thin profiles. **Decision needed:** there are no email invites, so is this a copyable "hey, do the wizard" message, or an email to a player who already joined?

## S14 Returning-player top-up

*As a returning player I want a short round on new shelf games only.*

- [ ] Only shelf games whose prediction for me is uncertain; 3–5 pairs
- [ ] Occasionally re-asks an old pair to keep time decay honest

## S15 Taste model v1: features, appeal vs enjoyment

*As a player I want good suggestions for games I've never compared.*

- [ ] Feature weights over mechanics, categories, weight, playtime, interaction, best-count poll, separate for appeal and enjoyment
- [ ] Per-player calibration: how well my vibes predict my post-play enjoyment
- [ ] Beats v0 on the replay harness before it replaces v0

## S16 Auto-close and summary

*As a player I want a recap after the game day.*

- [ ] `GameDayEnded` at midnight in the game day's time zone
- [ ] Everyone who joined gets a summary email: what was played, by whom, how long
- [ ] No more bringing, joining or play logging after close
