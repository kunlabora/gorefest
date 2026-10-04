# Boardgame game-day matcher

Helps a group of boardgamers pick the games they'll enjoy most at a game day, using per-player taste profiles built from pairwise comparisons.

## Vocabulary (use these terms in code)

- **Game day**: an event hosted by one player. Has a free-text title (e.g. "Gorefest #7").
- **Collection**: games a player owns (synced from BGG).
- **Shelf**: games brought to a specific game day.
- **Candidates**: shelf games that fit a table (player count, time budget).
- **Suggestions**: top-ranked candidates shown to a table (top 3, each with a one-line reason).
- **Comparison**: a pairwise answer, with basis `VIBE` (unplayed, "which would you rather try?") or `PLAYED` ("which did you enjoy more?").
- **Checkpoint**: morning / lunch / evening moments when people open the app.
- **Play log**: what was played, by whom, how long.

## Stack

- Kotlin 2.4 (JDK 25), Ktor, kotlinx.html, htmx 2, hyperscript (served as webjars). Server-rendered; no SPA.
- Postgres, plain JDBC + HikariCP, Flyway migrations, kotlinx.serialization for event JSON.
- Tests: JUnit 6 (suspend test functions work), Kotest assertions, Testcontainers for Postgres.
- No SSE / WebSockets in the MVP (usage is checkpoint-based; refresh is enough).
- Deploy: fat jar + systemd on a single cheap VPS, Caddy in front for TLS, nightly `pg_dump` to object storage. GitHub Action: build → scp → restart. Hosted on Scaleway (`deploy/scaleway.md`, `deploy/provision.sh`); general setup, deploy and restore steps in `deploy/README.md`.
- Offline support: out of scope for now.

## Architecture

- **Event sourcing with Dynamic Consistency Boundary (DCB)**: no aggregates, one global event log.
  - Table: `events(seq bigserial, type text, tags text[], data jsonb, at timestamptz)`, GIN index on `tags`.
  - Append: in one transaction, take `pg_advisory_xact_lock`, verify no events matching the decision's query (types + tags) exist after the read position, then insert.
- **Hand-rolled deciders**: sealed interfaces for commands/events; pure `decide(cmd, state)` and `evolve(state, event)`. Each decider declares its DCB query. Test with given/when/then.
- **Projections**: in-memory, rebuilt on startup by replaying the log.
  - Each projection's state is a read-model class in `core` that owns its queries and their defaults (`GameDayShelves.of(gameDay)`, `PlayerTastes.of(player)`, `GameDayPlays.on(gameDay)`, `LinkedBggAccounts.isLinked(player)`). Callers never see a raw `Map` or repeat `?: default`. `App` exposes them as `shelves`, `tastes`, `plays`, `bggAccounts` (and `gameDaysById`); nothing outside `App` touches `Projector.state`. A new query goes on the read-model class.
- **Not events** (plain tables): BGG metadata cache (`fetched_at`), players (email, name), login attempts, sessions.

### Events (initial)

`PlayerRegistered`, `LoggedIn`, `BggAccountLinked`, `GameDayCreated`, `InviteLinkRotated`, `PlayerJoined`, `GameBrought`, `GameUnbrought`, `PlayWishChanged`, `ComparisonRecorded(player, a, b, result, basis, at)`, `GamePlayStatusDeclared`, `PlayLogged(gameday, game, players, minutes)`, `GameDayEnded`.

Tag convention: `player:{id}`, `gameday:{id}`, `game:{bggId}`.

## Code layout and conventions

- **Functional core, imperative shell.**
  - `gorefest.core`: pure code only (events, deciders, projections, and later the taste model and matching). No IO, no clock, no random. Everything a decision needs (ids, today, acting player) is in the command.
  - `gorefest.shell`: DB, event store, command handler, projectors, web. Builds commands and calls the core. The root holds only the wiring (`App`, `AppConfig`, `IdGenerator`); `shell.eventstore` has `EventStore`, `CommandHandler` and `Projector`; `shell.gameday` has `InviteLinks`, `GameDayCloser` and the recap (`Recap`, `RecapMailer`); `shell.auth`, `shell.bgg` and `shell.db` hold the rest.
  - External systems stay in the shell: BGG's XML, HTTP and caching live in `shell.bgg` and translate into core types (`core.game.Game`). Core types don't carry display text; that belongs in `shell.web` views (e.g. `Game.facts`).
- **Shell services take what they use, not `App`.** List each dependency in the constructor (`GameDayCloser(commands, gameDays, recaps, clock, scope)`); `App` wires them once. Pass a lambda (`gameDays = { gameDaysById }`) where the service needs the latest read-model snapshot. Display text (mail bodies, fallback names) is built in pure shell values like `Recap`, away from the IO.
- **Context parameters for cross-cutting concerns.** `context(tx: Tx)` for anything that must run inside `Database.transaction`, `context(me: CurrentPlayer)` for handlers that need the logged-in player, `context(ids: IdGenerator, clock: Clock)` for routes that mint ids or need "now". Prefer them over passing these explicitly. Never call `Uuid.random()` or `now()` directly; tests use `IdGenerator.sequential()` and `TestClock`. Secrets (login tokens, codes, session tokens) are the exception: they come from `SecureRandom` in `Secrets`, and tests read them from the mailbox or cookie.
- **Events** live in `core/Event.kt` as one sealed interface. `@SerialName` is the stored type: never rename it; add a new event instead. Each event declares its `tags`.
- **Event evolution.** Events are stored as JSON and decoded into the current classes on every replay (`EventStore` uses `ignoreUnknownKeys = true`), so stored events are immutable and the class must keep decoding old rows.
  - Safe: add a field **with a default**; remove a field (the old key is ignored).
  - Never: add a field without a default, rename a field, change a field's type or meaning, or rename an event's `@SerialName`. These break startup replay, or silently decode wrong data.
  - Instead of a breaking change, add a new event type, stop emitting the old one, and keep the old class forever with `evolve` still handling it.
  - If a rename is unavoidable, add an upcaster in `shell.eventstore` that transforms the old JSON to the current shape before decoding (version in the JSON as `v`, default 1, so existing rows need no migration). Deciders and projections only ever see the latest shape. Don't rewrite the log with a migration, except for erasure.
  - Rolling back to an older jar after a newer one wrote a new event type fails replay. Deploy forward, or restore.
- **Deciders** are `object`s implementing `Decider`. Keep the query as narrow as the invariant allows, because anything outside it can't conflict (see `JoinGameDayDecider`).
- Deciders and projections end their `when` with `else -> state`, so adding an event doesn't touch unrelated code.
- **Web packages by feature**, named after the vocabulary: `web.auth`, `web.bgg` (linking, collection), `web.gameday` (hub, create/edit, readiness), `web.invite`, `web.shelf` (bringing games), `web.taste` (wizard, favourites), `web.suggestions`, `web.checkpoint`, `web.dev`. Shared plumbing (`Server`, `Responses`, `CurrentPlayer`, `memberGameDay`) sits in `web` itself, components in `web.components`. Tests mirror this.
- **Web layer, three levels.**
  - `XRoutes.kt`: HTTP only. Parse the request, run the decider, gather data into a small view model, and pick the view to respond with.
  - `XViews.kt`: the pages and fragments for that feature, as `HTML.xPage(view)` and `TagConsumer<*>.fragment(...)` functions, built from components.
  - `web/components/`: shared design-system components (`page`, `notice`, `field`, `primaryButton`, `playerChips`, `segmented`, `copyBox`, `nextStep`, `rankedGame`, `duel`…). Reach for these before writing `gf-*` markup by hand. Components that can be a fragment's root have a `TagConsumer<*>` overload too.
- **htmx:** handlers answer htmx requests with a fragment (one top-level element) and plain requests with a full page or redirect.
- **Web tests** use `TestApp` (`WebTest.kt`): `webTest(session = test.loggedIn("Tim")) { client -> … }`. They parse responses with Jsoup (`page()`, `fragment()` in `HtmlAssertions.kt`) and assert with CSS selectors, not substrings. `page()` also checks every `hx-target="#id"` resolves; `shouldReplace` checks an `outerHTML` fragment's root id. HTTP behaviour (status, `HX-Redirect`) is asserted directly.
- **Theme:** every app bar ends with a sun/moon toggle (`themeToggle`). The choice lives in the `gf_theme` cookie, and `static/js/theme.js`, loaded first in `<head>`, applies it before paint. Before anyone picks, `tokens.css` follows the device. It all happens in the browser; the server doesn't read the cookie.
- **Design:** `docs/design/` holds the design system (tokens, `gorefest.css` components, brand book, microcopy) and HTML mockups of every screen. Build pages from those class names and follow the brand book's voice.
- **Backlog:** `docs/backlog.md`. Update story status and tick criteria as work lands.

### Dev commands

- `podman compose up -d`: local Postgres on 5432
- `./gradlew test`: all tests (needs a container runtime for Testcontainers)
- `./gradlew buildFatJar && java --sun-misc-unsafe-memory-access=allow -jar build/libs/gorefest.jar`: run on http://localhost:8080

## BGG integration

- Since 2025 the XML API2 needs a registered application: every request sends `Authorization: Bearer <BGG_TOKEN>`, and unauthenticated calls get 401. boardgamegeek.com blocks scripted access to its docs, so read them in a browser.
- Attribution: every page (`page()`) carries a "Game data and images from BoardGameGeek" footer (`bggCredit`), and game names link to their BGG page (`bggNameLink`). We promised this in the BGG application: keep it.
- The site is private by design: `robots.txt` disallows all, every response sends `X-Robots-Tag: noindex`, and `page()` adds a robots meta tag. Keep it that way, so nobody finds the app by accident and loads BGG through us.
- Without `BGG_TOKEN` the app uses `FakeBggApi` with `DevCatalog` (dev mode and tests). All real calls go through `BggGateway`.
- The `user` endpoint has no free-text bio, so verification looks for the code in any profile field it returns (we suggest "Web address").
- BGG has no API for the top-ranked list; wizard anchors come from game ranks in the metadata cache.
- One in-process coroutine worker, token bucket (~1 req / 5 s), priority queue: live searches and fresh account links jump ahead of routine refreshes.
- Search the local metadata cache first; fall back to the BGG search endpoint.
- Fetch metadata once per game globally, not per user.
- Refresh BGG top-ranked list monthly (not the "hot" list) for wizard anchors.
- BGG has no OAuth: link by username plus a verification step (e.g. code placed in the BGG profile).

## Auth

- Email magic link **plus** a 6-digit code in the same email (in-app browsers of WhatsApp/Signal break cookie-based magic links).
- Tokens: random, stored hashed, single-use, ~15 min expiry. Long-lived session cookie afterwards.
- The code is in the mail subject too. The link opens a confirm page and logs in on POST, because mail scanners pre-fetch GET links.
- Email and display name live in the plain `players` table; events only carry player ids, so a player can be erased.
- `GOREFEST_ENV=dev` (the default for now) sends mail to an in-memory dev mailbox at `/dev/mailbox`. Production needs `SECRET_KEY` and a mail provider (S1).
- Transactional mail: Scaleway TEM (production), Postmark or Resend, picked by `MAIL_PROVIDER`.

## Taste model

- Per player, two layers learned from comparisons:
  1. Per-game scores (Bradley–Terry / Elo).
  2. Feature weights over BGG features (mechanics, categories, weight, playtime, interaction, best-player-count poll) to generalize to unseen games.
- Separate **appeal** (VIBE) from **enjoyment** (PLAYED); separate feature weights.
- Calibrate per player: how well their vibes predict their post-play enjoyment.
- Time decay: half-life 6–12 months; occasionally re-ask old pairs.
- Model is a pure projection of `ComparisonRecorded` events, so algorithm versions can be compared by replay (`Evaluation`). `App.tasteModel` picks the version: `FeatureModel` (v1) since it beat `BradleyTerry` (v0).

## Wizard

- Pairwise choices with answers: A, B, haven't played, both meh, both great.
- Pick pairs with maximum model uncertainty; stop when predictions stabilize.
- Source games in order: shelf → host's collection → participant's collection → BGG top-ranked. Mix ~20% popular anchors even when the shelf is long.
- First-time: 10–15 pairs, then a payoff screen: "Your likely favourites today: X, Y, Z".
- Returning: short top-up round on uncertain shelf games.

## Matching (ad hoc suggestions)

`suggest(players, shelf, timeBudget?) → top 3 with reasons`

- Hard filter: player count within BGG best/recommended; fits time budget.
- Score: mean predicted enjoyment − λ·spread (or least misery), blending appeal and enjoyment for unplayed games.
- Bonuses: novelty (nobody has played), owner's desire to play it.
- Duration: BGG playtime adjusted by player count, plus learned group speed from `PlayLogged`.

## User journeys

**Host**: sign up → link BGG → wizard on own collection → create game day → share invite link (Open Graph preview: title, date, place, shelf size) → tick games bringing → readiness view (who joined, thin profiles with one-tap nudge, shelf size, gaps like "nothing plays well at 6") → on the day same as participants. Only the host edits the game day.

**Participant**: tap invite → landing page (host, date, place, shelf) → email → magic link or code → optional BGG link → wizard seeded from shelf → payoff screen → optionally tick games bringing.

**On the day**: tick who's at the table → suggestions. At each checkpoint: "what did you play, who, how long?" + one comparison → new suggestions. Game day auto-closes at midnight; summary to everyone.

Invite link: signed token (`InviteLinks`: game day id + version, HMAC), valid until the game day ends, host can rotate it (bumps the version). Joining only happens through a valid invite; game day pages are members-only. No email invites. No guests; everyone needs a profile.

## MVP scope

1. Magic-link auth + BGG username linking + collection sync
2. Game day creation + invite link
3. Bringing games (from collection or on-the-day search)
4. Wizard (vibe + played, shelf-seeded, with anchors)
5. Ad hoc suggestions
6. Checkpoint play log

## Later

Round planning (split players into tables, synchronized finish times, party-game filler for early finishers; late joiners wait for the next round), "who'd play my game?" (inverse matching), collaborative filtering, game-day series, offline support.

## Open questions

- Should the payoff screen show taste overlap with other participants ("you and Anna both love heavy euros")?
- Product name.
