# QA report: exploratory testing of Gorefest (dev build on :8099)

Method: curl with cookie jars for ~12 players (Tim, Anna, Bram, Cara, Dan, Eve, Fay, Gus, ...) plus the Browser pane for visuals, theme, mobile (375px) and htmx behaviour. Fake BGG, throwaway DB. The build under test predates the "ask someone to bring a game" feature.

| id | title | severity | area | status |
|---|---|---|---|---|
| [BUG-001](bugs/BUG-001-duplicate-game-day-on-double-tap.md) | Double-tapping "Create game day" creates two identical game days | minor | Game day creation | open |
| [BUG-002](bugs/BUG-002-taste-round-progress-lost-on-refresh.md) | Refreshing the taste round resets the progress bar and the payoff undercounts the answers | minor | Taste wizard | fixed |
| [BUG-003](bugs/BUG-003-emoji-name-breaks-avatar-initial.md) | A name that starts with an emoji shows "?" as its avatar initial | minor | Components / avatars | open |
| [BUG-004](bugs/BUG-004-search-no-results-is-silent.md) | Searching for a game that doesn't exist shows nothing at all | minor | Bringing games | open |
| [BUG-005](bugs/BUG-005-blank-404-pages.md) | Every 404 is a bare, empty white page | minor | Error handling / access control | open |
| [BUG-006](bugs/BUG-006-no-title-length-limit.md) | Game day title and place have no length limit; a long word overflows the invite preview image | minor | Game day creation / edit, home, invite Open Graph image | open |
| [BUG-007](bugs/BUG-007-missing-date-says-date-in-past.md) | A missing or unparseable date is reported as "That date is in the past" | cosmetic | Game day create/edit | open |
| [BUG-008](bugs/BUG-008-game-tile-name-breaks-mid-word.md) | Game names in tiles break in the middle of a word ("Carcassonn / e") | cosmetic | Play log | open |
| [BUG-009](bugs/BUG-009-your-collection-count-includes-searched-games.md) | "Your collection · N" counts games found by search that you do not own | cosmetic | Bringing games | open |
| [BUG-010](bugs/BUG-010-find-a-date-add-another-date-unlimited.md) | "Add another date" has no limit, the server only complains after submit (max 14) | cosmetic | Date poll form | open |
| [BUG-011](bugs/BUG-011-static-assets-have-no-cache-validators.md) | CSS and JS are re-downloaded on every page view; HEAD requests to them 404 | minor | Static serving | open |
| [BUG-012](bugs/BUG-012-cancelled-game-day-shows-bring-games-prompt.md) | A cancelled game day still tells you to "Tick the games you're bringing" | cosmetic | Game day hub | open |
| [BUG-013](bugs/BUG-013-wizard-havent-played-buttons-indistinguishable.md) | The two "Haven't played this" buttons in the taste round have identical accessible names | minor | Taste wizard duel component | open |
| [BUG-014](bugs/BUG-014-authenticated-pages-cacheable-after-logout.md) | After logging out, the Back button shows the previous user's pages | minor | Auth / HTTP caching headers | open |
| [BUG-015](bugs/BUG-015-post-without-form-body-returns-500.md) | POSTing without a form body (or with JSON) answers HTTP 500 instead of 400/415 | minor | Web layer, every route that calls `call.receiveParameters | open |
| [BUG-016](bugs/BUG-016-solo-table-says-the-1-of-you.md) | A one-person table gets "~30 min for the 1 of you" | cosmetic | Suggestions | fixed |
| [BUG-017](bugs/BUG-017-round-done-1-answers.md) | Payoff kicker says "Round done · 1 answers" | cosmetic | Taste wizard payoff | fixed |
| [BUG-018](bugs/BUG-018-compare-question-asked-to-logger-who-did-not-play.md) | After logging a play, the logger is asked "Which did you enjoy more?" even when they weren't one of the players | minor | Play log / checkpoint | open |
| [BUG-019](bugs/BUG-019-plain-form-post-error-returns-bare-fragment.md) | A validation error on a plain (non-htmx) form post returns a bare `<form>` fragment, not a page | minor | Web layer | open |

## Verified working (no bug)
Magic link and code login (wrong code lockout, expiry after 15 min, link reuse), invite join incl. rotate/cancel/tampered tokens, non-member access (404, no leaks), date poll create/join/answer/pick/closed, play wishes (limit 3), readiness gaps, suggestions for 1 to 8 players and all time budgets, home grouping (cancelled and ended days land in "Past" and "Next up" moves on after midnight; recap mail sent), light/dark contrast (scripted check on the main pages), no horizontal overflow at 375px, no console errors or failing requests.

## Not covered
- Real BGG behaviour (fake only), collection "queued" state, BGG unavailable states.
- Real mail provider, WhatsApp/Signal in-app browsers, Caddy/TLS, deploy.
- The new "ask someone to bring a game" feature (/ask, "People are asking", opt-out on /collection).
- Login resend flow and "newest code vs older mail" behaviour: my IP hit the per-address login limit (20 mails/hour), so I could not create new sessions late in the run.
- Offline, screen-reader runs (only DOM/accessibility-tree checks), timezone other than Europe/Brussels, very large collections.

## Screenshots
The Browser tool returns images inline only, so each screenshot was taken from the tool's saved JPEG under the session tool-results folder, converted with `sips -s format png`, and copied to `screenshots/BUG-00N-*.png`. Bugs without a screenshot (001, 002, 007, 010, 011, 013 to 019) are non-visual and carry curl/DOM evidence instead.
