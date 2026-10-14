# QA report: exploratory testing of Gorefest (dev build on :8099)

Method: curl with cookie jars for ~12 players (Tim, Anna, Bram, Cara, Dan, Eve, Fay, Gus, ...) plus the Browser pane for visuals, theme, mobile (375px) and htmx behaviour. Fake BGG, throwaway DB. The build under test predates the "ask someone to bring a game" feature.

| id | title | severity | area | status |
|---|---|---|---|---|
| [BUG-001](bugs/BUG-001-duplicate-game-day-on-double-tap.md) | Double-tapping "Create game day" creates two identical game days | minor | Game day creation | fixed |
| [BUG-002](bugs/BUG-002-taste-round-progress-lost-on-refresh.md) | Refreshing the taste round resets the progress bar and the payoff undercounts the answers | minor | Taste wizard | fixed |
| [BUG-003](bugs/BUG-003-emoji-name-breaks-avatar-initial.md) | A name that starts with an emoji shows "?" as its avatar initial | minor | Components / avatars | fixed |
| [BUG-004](bugs/BUG-004-search-no-results-is-silent.md) | Searching for a game that doesn't exist shows nothing at all | minor | Bringing games | fixed |
| [BUG-005](bugs/BUG-005-blank-404-pages.md) | Every 404 is a bare, empty white page | minor | Error handling / access control | fixed |
| [BUG-006](bugs/BUG-006-no-title-length-limit.md) | Game day title and place have no length limit; a long word overflows the invite preview image | minor | Game day creation / edit, home, invite Open Graph image | fixed |
| [BUG-007](bugs/BUG-007-missing-date-says-date-in-past.md) | A missing or unparseable date is reported as "That date is in the past" | cosmetic | Game day create/edit | fixed |
| [BUG-008](bugs/BUG-008-game-tile-name-breaks-mid-word.md) | Game names in tiles break in the middle of a word ("Carcassonn / e") | cosmetic | Play log | fixed |
| [BUG-009](bugs/BUG-009-your-collection-count-includes-searched-games.md) | "Your collection · N" counts games found by search that you do not own | cosmetic | Bringing games | fixed |
| [BUG-010](bugs/BUG-010-find-a-date-add-another-date-unlimited.md) | "Add another date" has no limit, the server only complains after submit (max 14) | cosmetic | Date poll form | fixed |
| [BUG-011](bugs/BUG-011-static-assets-have-no-cache-validators.md) | CSS and JS are re-downloaded on every page view; HEAD requests to them 404 | minor | Static serving | fixed |
| [BUG-012](bugs/BUG-012-cancelled-game-day-shows-bring-games-prompt.md) | A cancelled game day still tells you to "Tick the games you're bringing" | cosmetic | Game day hub | fixed |
| [BUG-013](bugs/BUG-013-wizard-havent-played-buttons-indistinguishable.md) | The two "Haven't played this" buttons in the taste round have identical accessible names | minor | Taste wizard duel component | fixed |
| [BUG-014](bugs/BUG-014-authenticated-pages-cacheable-after-logout.md) | After logging out, the Back button shows the previous user's pages | minor | Auth / HTTP caching headers | fixed |
| [BUG-015](bugs/BUG-015-post-without-form-body-returns-500.md) | POSTing without a form body (or with JSON) answers HTTP 500 instead of 400/415 | minor | Web layer, every route that calls `call.receiveParameters | fixed |
| [BUG-016](bugs/BUG-016-solo-table-says-the-1-of-you.md) | A one-person table gets "~30 min for the 1 of you" | cosmetic | Suggestions | fixed |
| [BUG-017](bugs/BUG-017-round-done-1-answers.md) | Payoff kicker says "Round done · 1 answers" | cosmetic | Taste wizard payoff | fixed |
| [BUG-018](bugs/BUG-018-compare-question-asked-to-logger-who-did-not-play.md) | After logging a play, the logger is asked "Which did you enjoy more?" even when they weren't one of the players | minor | Play log / checkpoint | fixed |
| [BUG-019](bugs/BUG-019-plain-form-post-error-returns-bare-fragment.md) | A validation error on a plain (non-htmx) form post returns a bare `<form>` fragment, not a page | minor | Web layer | fixed |

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

---

## Round 2 (v2.1 candidate)

Method: same as round 1 (curl with cookie jars for Tim as admin, Anna, Bram, Cara, Dan, Fay; Browser pane for visuals, dark/light, 375px). Build v2.0.0 plus the privacy/GDPR work (G1 to G8), fat jar run in dev mode on :8099 against a throwaway database (`gorefest_qa2`, dropped afterwards), fake BGG, `ADMIN_EMAIL=tim@example.com`. The server was also restarted once to check event replay with erasure events (clean, sessions survive).

| id | title | severity | area | status |
|---|---|---|---|---|
| [BUG-020](bugs/BUG-020-account-cards-have-no-padding.md) | "Send me my data" / "Delete my account" cards on /account have no padding and touch each other | minor | /account CSS | fixed |
| [BUG-021](bugs/BUG-021-place-hint-and-preview-still-say-shown-on-invite.md) | Game day form says the place is "Shown on the invite"; its invite preview shows the place | minor | Game day create/edit copy | fixed |
| [BUG-022](bugs/BUG-022-changing-the-admin-email-removes-admin-rights.md) | The admin changing their email loses admin rights; the old address becomes admin for a new signup | minor | Email change x ADMIN_EMAIL | fixed |
| [BUG-023](bugs/BUG-023-privacy-notice-claims-discord-even-without-webhook.md) | Privacy notice always claims a Discord channel, even with no webhook | cosmetic | /privacy | fixed |
| [BUG-024](bugs/BUG-024-email-change-copy-and-error-page-details.md) | "1 tries left"; an email error on /account drops the host warning | cosmetic | Email change | fixed |
| [BUG-025](bugs/BUG-025-erased-players-leave-orphans-and-inconsistent-placeholders.md) | After erasure: shelf games and open requests of the erased stay; "?" vs "Someone" | minor | Erasure (partly by design, G2) | open |
| [BUG-026](bugs/BUG-026-mail-and-log-leak-risks.md) | Logs can still carry an address/code (DevMailbox verified; provider error body read from code) | minor | Logs (G8) | open |

Blocking: none found. Nothing in the places where the new features could leak data (place on the invite, other players in the data export, tokens in logs, non-admin access to /admin/requests) failed.

### Verified working (no bug)
- **/privacy**: 200 without a session, `X-Robots-Tag: noindex...` plus no-store, footer link on every page checked (sign-in, welcome, pending, dev mailbox, 404, invalid invite, dead login link, all member pages), sections incl. "What you can ask for", mail provider line (names "no mail provider" in dev), contact from `ADMIN_EMAIL`, 14-day log claim.
- **Invite and place**: `/i/{token}` as anonymous and as a logged-in non-member has no place in HTML, title, og:* or twitter:* tags (only title, date, "N going", shelf size); `og.png` is a valid 1200x630 PNG (viewed: no place on it), also for 80-char title/place; unchanged for members, who see the place on the hub; rotated, cancelled and tampered tokens give 404 (also og.png); the sign-in and name pages reached from an invite show no place; `/p/{token}` and the poll meta tags have no place before joining, members see it, a closed poll answers 410, `/p/{token}/og.png` is 404; after joining the place shows. Un-approved visitor via invite is sent to /pending and joins only after approval.
- **/account**: name/email shown, avatar links to it; "Send me my data" and "Delete my account" each reach the admin exactly once (a second click shows "Asked" and posts nothing; admin mail carries name and link only, no address); host warning lists upcoming (not cancelled) game days; email change: invalid/same address refused, a code goes to the new address, wrong code counts down, five wrong codes lock ("didn't work, ask for a new one"), a code can't be reused, an address another account has gives the identical screen and no mail (also in a different case), 6th code in an hour is refused, old address gets a notice, session stays valid, the old address can sign up as a new person; plain POSTs without a body or with JSON give 400.
- **BGG unlink**: confirm prompt, collection gone, username free for another account (and refused while taken), relink and collection sync work, open bring requests stay.
- **Admin requests**: 404 for logged out and non-admin (GET and POST, incl. approvals); "Download their data" gives `Content-Disposition: attachment; filename=boardfest-my-data.json`, no-store, and for Bram contains no other player's name, id or email (own name, email, collection, history, bring request text "Someone asked you to bring"); "Sent it" clears the request and a later request re-appears; "Delete account" has a confirm prompt, mails the person, mails the admin only the player id, kills their sessions, the same email can sign up again as a new, unapproved person with no old game days and the BGG username free; bad ids are harmless (404/302), erasing twice or an unknown id creates no event.
- **Erasure**: game days keep working for the others (hub, table, favourites, bring, ask, log, readiness): the player leaves upcoming tables, a cancelled game day keeps its head count (3), a play log line stays; host erased with an upcoming game day does not break hub or home; no ERROR/WARN in the log; replay after restart is fine.
- **Logs**: no invite/poll token, login token, query string or address in any request log line (see BUG-026 for the dev mailbox lines).
- **Regression**: login by code and by link (confirm page on GET, login on POST, reuse shows "expired"), new-account approval incl. mails, create/edit (non-host edit refused, script tag escaped), invite join, rotate (old link 404), bring by collection and by search ("nothing found" message), wishes (4th is refused), taste round to the payoff, suggestions for 2 and 4 players, play log (refused before the day, works on the day) and compare step, bring requests (ask, open count), date poll create/join/answer/pick (and the 410 afterwards), cancel, logout/session cookie (HttpOnly, SameSite=Lax, one year), 404s for non-members, theme toggle (`gf_theme` cookie, dark applies on the next page), 375px: no horizontal overflow on 23 pages (scripted via iframes), no console errors.

### Not covered
- Inactive accounts job (`INACTIVE_ACCOUNT_MONTHS`): cannot be driven from outside, skipped.
- Real mail provider and Discord webhook (the webhook URL must be a discord.com URL, so it can't be pointed at a local sink; I did not post to the real Discord): failure paths of both, and the exact text of provider error bodies (BUG-026 point 2 is from reading the code).
- The privacy notice for a server without `ADMIN_EMAIL` (instance started, but the contact paragraph was not read), and with a real `MAIL_PROVIDER`.
- Game days that have really ended (auto-close at midnight): head count of an ended day after erasure was only checked on a cancelled one.
- Observations, not filed: (a) the host's place text stays in the event log after the host is erased (G2 explicitly decided this); (b) the admin can erase or export any player id without an open request, including their own account, there is no server-side guard; (c) "Sent it" sends no mail to the person (the admin mails the file by hand, as the page says); (d) the landing page's mock "inbox" code on /login/code is a static illustration, not the real code; (e) the dev mailbox labels every mail "Open the login link"; (f) the rate limit for login mails was not exhausted this time (13 of 20 used).
- Timing differences between "taken" and "free" addresses on email change (taken skips the mail send), screen readers, offline, large collections.
- Screenshots: only BUG-020 is visual (`screenshots/BUG-020-*.png`); the other bugs carry curl/DOM evidence.
