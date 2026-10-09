# Redesign backlog

Brings the whole app in line with the new login (layout C). The reference is [`design/Gorefest App Redesign.html`](design/Boardfest%20App%20Redesign.html): every item below is one screen drawn there, with its desktop and phone view from one markup.

Status: 📋 todo · 🚧 doing · ✅ done. Tick criteria as they land.

## Rules for every item

- Build from the proposal's markup and class intent, using the shared pieces from R0: `page(site = SiteBar(me, Nav.X))`, `crumb`, `wrap`, `band(sunk)`, `split`, `.gf-phone-only` / `.gf-desk-only`. Reach for components in `web/components/` before hand-writing `gf-*` markup; add new shared components there.
- The CSS for a screen lives in its own block in `docs/design/css/gorefest.css` (`/* ==== Rn ... ==== */`), nothing outside it except a justified shared change.
- Behaviour doesn't change: same routes, htmx targets, form fields and decider calls. Only views, view models and CSS move. The brand book's rules hold (one `brand` fill per screen, sentence case, microcopy voice).
- Tests: web tests keep asserting with Jsoup selectors; update selectors that moved, and add one for each new element that carries behaviour. `./gradlew test` is green before it's done.
- Verify in the browser at desktop width and at 390px, light and dark.
- Update the matching mockup in `docs/design/mockups/` only if it's cheap; the redesign HTML is the source of truth for now.

| #   | Item                                      | Status | Depends on |
|-----|-------------------------------------------|--------|------------|
| R0  | [Foundation: site bar, nav, wrap, band, split](#r0-foundation) | ✅ | |
| R1  | [Home](#r1-home)                          | ✅ | R0 |
| R2  | [Game day hub](#r2-game-day-hub)          | ✅ | R0 |
| R3  | [Invite landing](#r3-invite-landing)      | ✅ | R0 |
| R4  | [Host a game day (create/edit)](#r4-host-a-game-day) | ✅ | R0 |
| R5  | [Taste round](#r5-taste-round)            | ✅ | R0 |
| R6  | [Your favourites (payoff)](#r6-your-favourites) | ✅ | R0 |
| R7  | [Bring games](#r7-bring-games)            | ✅ | R0 |
| R8  | [Pick a game](#r8-pick-a-game)            | ✅ | R0 |
| R9  | [Log a play](#r9-log-a-play)              | ✅ | R0 |
| R10 | [Link BGG](#r10-link-bgg)                 | ✅ | R0 |
| R11 | [Find a date](#r11-find-a-date)           | ✅ | R0 |
| R12 | [Sign-in follow-ups](#r12-sign-in-follow-ups) | ✅ | R0 |
| R13 | [Remaining pages and docs](#r13-remaining-pages-and-docs) | ✅ | R1–R12 |

---

## R0 Foundation

- [x] `page(site = SiteBar(me, current))`: wordmark, nav (Game days, Collection; hidden under 720px), your avatar, theme toggle, wide frame
- [x] `crumb`, `wrap`, `band`, `split` helpers and their CSS; `.gf-phone-only` / `.gf-desk-only`
- [x] A CSS slot per item in `gorefest.css`

## R1 Home

*Next game day is the hero; hosting is a quiet card beside it; everything later sits in a sunk band.*

- [x] Hero: kicker "Hi {name} · next up", the next game day as a raised card (date block, title, place and going, avatars, shelf as a fan of covers, the one step you still owe it as the page's brand button)
- [x] "Host a game day" and "Find a date" as two secondary cards beside it (desktop) or below (phone)
- [x] "Coming up" in a sunk band, two columns on desktop: later game days, open polls (poll date block), the BGG nudge as a dashed row while unlinked
- [x] Empty state when there is no game day or poll; nothing breaks without a shelf
- [x] Dock removed on phone (the cards replace it)

## R2 Game day hub

- [x] Hero split: crumb, title at full size, three facts (date, place, going), "Pick a game for my table"; the dark next-step card beside it
- [x] The shelf in a sunk band: six-up cover grid on desktop (three on phone), owner avatar on each tile, "Bring games" in the band header
- [x] Players and invite share a row on desktop; "How ready are we?" link for the host; the copy box and "Make a new link" unchanged in behaviour
- [x] Played-today list and the cancelled/ended tags keep working; host-only actions (Edit, Cancel) stay host-only

## R3 Invite landing

- [x] Same hero as the login: inviter line, title, three facts, avatars, and the join form
- [x] Logged out: email field inline with "Join {title}" (same flow as `/login?returnTo=` today, incl. the invite redirect); logged in: just the join button
- [x] Desktop: four box covers as a tilted hand, each "Brought by"; phone: a small fan under the facts
- [x] Sunk band "What happens next" with three steps
- [x] Open Graph tags and the `og.png` untouched; dead, cancelled and ended invite pages share the hero pattern

## R4 Host a game day

- [x] Hero split: crumb, kicker, h1, lede, the form (title with icon "Another one" button, date and where side by side, hint, button under the last field)
- [x] Desktop: a tilted preview card of the invite (title, date, place, going), updating as you type (hyperscript; no server round trip)
- [x] Edit reuses the page and the form; editing leaves the time zone as before
- [x] Validation errors re-render the form in place as today

## R5 Taste round

- [x] Desktop: copy left (kicker, pips + count, basis tag, the question, A/B and 1/2 key hints), the pair right (large cards, "Haven't played this" under each, three answers)
- [x] Phone: question on top, pair and answers in thumb reach; same htmx fragment (`main#wizard`) swap as today, one top-level element
- [x] "Done for now" in the bar; keyboard shortcuts keep working; `.gf-deal` motion kept
- [x] The returning top-up variant and the log's "one quick question" page (R9) use the same duel

## R6 Your favourites

- [x] Split hero: kicker "Round done · n answers", headline, lede, "Open {title}" (brand) + "Sharpen it" link; the three favourites on tilted raised cards with real art, rank badge and the reason
- [x] Empty-shelf and not-enough-answers states keep their copy and actions
- [x] No dock; nothing overlaps the bottom (U18 stays fixed)

## R7 Bring games

- [x] Heading and search first; the collection as a five-up tile grid (three on phone) with tick state
- [x] Desktop sidebar: how many games the table has, who brought what, your wishes (up to 3), "Done: n games"; phone: a selection tray with a fan and Done
- [x] Search results (collection, then BGG) land in the same grid; wishes still saved as you tap
- [x] Without BGG linked, the page still works from search alone

## R8 Pick a game

- [x] Desktop: sticky panel (who's at the table as chips, time as a segmented control) beside the results; phone: the collapsed table bar that expands
- [x] The top pick as a large card (cover, reason, time, chips), numbers two and three as compact cards
- [x] htmx live update on change as today (`#suggestions` fragment); nothing-fits states, relax-time button and the unknown-taste notice kept

## R9 Log a play

- [x] Three numbered steps (game, who, how long) with the login's number discs; desktop summary card that updates as you choose and holds the button; phone keeps a dock with a summary label
- [x] Search-for-another-game field kept; error notice in place
- [x] The "one quick question" page uses the redesign shell and the R5 duel

## R10 Link BGG

- [x] Step 1 (username) in the login hero pattern, inline button; step 2 (verify) with the code as a big copy box, "I've added it" under it, and the numbered steps beside the screenshot
- [x] Wrong-username link and error states keep working; Collection pages (`/collection`, status) use the shell with the Collection nav item current

## R11 Find a date

- [x] Poll page: hero left (crumb, answered count, question, share box), one card per date on the right (date block, meter, who can / can't, button); best date gets the orange edge and the page's brand button
- [x] Answering page for participants in the same shell (tick as you go keeps saving); closed polls explain the pick
- [x] Create-poll pages follow R4's form pattern

## R12 Sign-in follow-ups

- [x] Code page in the login hero: kicker "Step 2 of 2", h1, lede with the email, the big code field with the button under it, resend/change links; desktop mail illustration
- [x] Name (welcome) and expired-link pages in the same hero; dev-mailbox notice kept
- [x] The confirm-link page too

## R13 Remaining pages and docs

- [x] Readiness view, collection list, cancel page, ended/cancelled invite pages, recap-related pages and dev pages move to the shell
- [x] Brand book layout rule updated (the 480px column is gone; hero, band, split); `docs/design/mockups/` index points at the redesign; CLAUDE.md layout notes updated
- [x] No page still uses the old app bar title; `.gf-screen` max-width and the dock are phone-only leftovers or removed
