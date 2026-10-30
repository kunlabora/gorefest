# The Contract

What Boardfest must do, as numbered obligations an agent is judged against (IMP Rules 13, 20, 28, 29). CLAUDE.md says how the code is built; the Contract says what it promises to the people using it, and to the people it holds data about.

## Shape

One file per area. Each starts with a heading and its id prefix, then the obligations:

```markdown
# Game days

Prefix: `GD`

- **GD-1** Only the host changes a game day's title, date and place.
- **GD-2** …
```

- An obligation is one plain sentence about behaviour someone can see or rely on, in the vocabulary of CLAUDE.md. Not an implementation detail ("uses an advisory lock"), but the promise it keeps ("two people asking at once can't push past the cap").
- Ids are forever. A dropped obligation keeps its number: strike it through (`- ~~**GD-4**~~ …, dropped 2026-11-02: why`) and don't count it. The coverage test only reads lines that start with `- **`.
- New obligations take the next free number in their area.

## Proof

Every obligation is proven by at least one test, which names it:

```kotlin
@Test
@Obligation("GD-1")
fun `only the host can edit`() { … }
```

`ContractCoverageTest` fails the build when an obligation has no test, when a test names an id that doesn't exist, or when ids repeat. It writes the coverage per area to `build/reports/contract/coverage.md`.

## When it changes

- **A story** writes its criteria as obligations here first (the `story` skill), and the story is done when they are all proven.
- **A bug** in live production is a gap in the Contract: add the missing obligation, then a test that fails on the bug, then the fix (`fix:` commit). Anything else is `refine:`.
- **Before changing old code**, find the obligations it keeps. If there are none, pin its current behaviour as obligations first.

## Areas

| File | Prefix | Covers |
|---|---|---|
| [accounts.md](accounts.md) | `ACC` | Sign-in, sessions, approval, account page, email change |
| [bgg.md](bgg.md) | `BGG` | Linking a BGG account, collections, metadata, search, attribution |
| [game-days.md](game-days.md) | `GD` | Creating, editing, inviting, joining, cancelling and ending a game day, the recap |
| [shelf.md](shelf.md) | `SH` | Bringing games, wishes, bring requests |
| [plays.md](plays.md) | `PL` | Play log, checkpoints |
| [taste.md](taste.md) | `TA` | Comparisons, the wizard, the taste model, favourites |
| [suggestions.md](suggestions.md) | `SU` | Candidates, suggestions and their reasons |
| [polls.md](polls.md) | `DP` | Date polls, games on a poll, game nights |
| [privacy.md](privacy.md) | `PR` | Erasure, export, retention, logs, the notice, staying private |
| [event-log.md](event-log.md) | `EV` | The event log: appends, conflicts, replay, evolution |
| [web.md](web.md) | `WEB` | Pages in general: members-only, htmx, theme, layout, static files |
| [a11y.md](a11y.md) | `A11Y` | Accessibility: labels, names, structure, contrast, focus, motion |

## Known gaps

Promises stated in CLAUDE.md or a request doc that no test proves yet, found when the Contract was seeded (2026-10-30). Each becomes an obligation once a test proves it; drop it here when it does.

- **Accounts:** `email_changes` rows are purged by `Login.purge` (only sessions and attempts are tested); the session cookie's lifetime; approval times come from the database's `now()` rather than the injected clock (`shell/auth/Repositories.kt`), so tests can't pin them.
- **Privacy:** the notice's wording on logs (kept 14 days, no links or addresses); inactive accounts are warned once only; G3 and G4 request docs still have unticked criteria for work that is built.
- **Accessibility:** the status line really being read out (`htmx-config.js` runs in a browser, not in the tests; checked by hand in the in-app browser, 2026-10-30); focus after a swap where the control goes away ("Ask" becomes "Asked", "Bring it", "Take it back", "Not this time") falls back to the page. The manual pass stays out of CI: VoiceOver and the keyboard alone through sign-in, the wizard, bringing games and a suggestion, in both themes and at phone width, before a release that changes a screen.
- **BGG:** game names link to their BGG page (`bggNameLink`); the monthly top-ranked refresh; fresh account links jump ahead of routine refreshes; the collection refresh loop; every request sends the bearer token (`XmlBggApi`).
- **Game days:** no guests and no email invites; the Open Graph image's input holds no place; a cancelled game day sends no recap (only indirectly: it is never due).
- **Shelf and plays:** checkpoint names and timing (morning, lunch, evening); the matcher ignores bring requests; bring requests send no mail or push.
- **Taste and suggestions:** members-only on the taste, favourites, table and suggestions pages; the wizard's source order (shelf, host's collection, participant's, BGG top-ranked); a top-up round stopping early; the 6–12 month half-life.
- **Polls:** "no" answers are shown to the host only; the host's own games are listed likely favourites first.
- **Event log:** projections rebuilt by replay at startup (no restart test); upcasters (none exist yet).
- **Web:** layout (no phone column, dock phone-only, every page through `page(site = SiteBar(…))`); the server never reads the theme cookie.
