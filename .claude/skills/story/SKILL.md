---
name: story
description: Build a Boardfest backlog story from idea to commit - request doc with design and criteria, mockup and microcopy, core (events, deciders, read model, given/when/then tests), web (routes, views, web tests), CLAUDE.md and backlog, privacy-check, commit. Use when the user asks to build, design, pick up or continue a story (S*, U*, G*), a feature request, or "the next one up".
---

# Story

A story is done when every criterion in its request doc is ticked and covered by a test: a decider given/when/then where there's a decision, a web test where there's a page (`docs/backlog.md`). CLAUDE.md holds the rules; this is the order to apply them in. Read CLAUDE.md's Architecture and conventions sections before starting if they aren't in context.

## 0. Find the story

- From the user's words, or the ⏭ row in a milestone table (`docs/milestone-N.md`, listed in `docs/backlog.md`). A brand-new idea gets the next free number in its group (`U21`, …), a row in the latest milestone's table (📋, Arrived today) and a file `docs/requests/u21-short-slug.md`.
- If the request doc exists, read it whole: decisions and answers are recorded in it with dates (`**Decision (2026-10-14):** …`). Don't re-open what is decided.
- Set the row to 🚧 with Started today.

## 1. Design (stop for the user here)

Write or complete the request doc, in the shape of `docs/requests/u20-ask-someone-to-bring-a-game.md`:

- `# U21 Title`, then `Source: …, YYYY-MM-DD. Status: 📋 todo`
- **Idea**, **Today** (what happens now, with the events involved), **Vocabulary** for any new term
- `## Behaviour`: what people see and can do, in plain words
- `## Design`: **Events** (new types only; tags; ids only, no names, emails, usernames or addresses), **Deciders** (which file, query, rejections as `Rejection` cases), **Read model** (class and its queries, exposed on `App`), **Web** (package, routes, fragments and their ids, components to reuse), **Privacy** (who newly sees what, and what `PlayerErased` does to it), **Matcher** (change or "no change")
- `## Open questions`, each with a recommendation
- `## Criteria` as `- [ ]` lines, each one testable. Always include: event evolution (new types only, `CLAUDE.md` updated), erasure behaviour, members-only/closed-day web tests, and "design mockup in `docs/design/` and the screens use existing components" when there's a screen.

When the product owner has a real choice between shapes, write options instead (`docs/requests/u11-options.html`: two or three options, each with a clickable screen flow; add it to `.claude/launch.json` to preview) and stop.

**Stop and ask** before building when there are open questions, or when the design adds events or shows data to new people. Record the answers in the doc as `**Decided (date):**`. Commit the design on its own: `U21 design: <title>`.

## 2. Mockup and microcopy (screens only)

- A mockup in `docs/design/mockups/screen-<name>.html` built from `docs/design/css/gorefest.css` classes, linked from `mockups/index.html`. Reuse the component mockups (`component-*.html`) rather than new markup.
- New sentences go in `docs/design/microcopy.md` in the brand book's voice (`docs/design/brand-book.md`): plain, warm, "Not this time", never "Rejected".

## 3. Core (`gorefest.core`, pure)

- **Events** in `core/Event.kt`: `@SerialName` is forever; every new field has a default; declare `tags` (`player:`, `gameday:`, `game:`, `datepoll:`, `gamenight:`). Never change an existing event's fields or meaning: add a new type.
- **Deciders** are `object`s with the narrowest query that still guards the invariant. Caps and uniqueness need everything they count inside the query. Everything a decision needs (ids, today, acting player) is in the command.
- **Read model** class owning its queries and defaults (`GameDayBringRequests.openFor(...)`); `App` exposes it; no raw maps outside.
- Every `when` over events ends with `else -> state`.
- **Erasure:** decide what `PlayerErased` does to the new state (on days still to be played: take the person out; closed days keep history). Handle it in the projection and test it.
- **Rejections** get a sentence in `shell/web/Rejections.kt` (`Rejection.message()`), pinned in `RejectionsTest`.
- Tests: given/when/then per decision, one per rejection and per no-op; projection tests for the read model.

Then, in the shell:
- `EventCompatibilityTest`: a stored JSON sample for every new type (the test fails without one).
- `PersonalData.describe`: a case for each event carrying the player's own data. An event naming another player must not echo their name or id.
- Concurrency invariants (caps): an interleaved-append test in `CommandHandlerTest`.

Run `./gradlew test`. Commit: `U21 core: <what>`.

## 4. Web (`gorefest.shell.web.<feature>`)

- `XRoutes.kt`: parse, run the decider, build a small view model, pick the view. htmx requests get one-root fragments, plain requests a page or redirect. Register routes in `Server.kt`.
- `XViews.kt`: `HTML.xPage(view)` and `TagConsumer<*>.fragment(...)`, from `web/components/` (`page(site = SiteBar(me, Nav.X))`, `notice`, `field`, `primaryButton`, `playerChips`, `nextStep`, `rankedGame`, …). New CSS only in the screen's block in `docs/design/css/gorefest.css`; a second use moves it to the shared block.
- Game names link with `bggNameLink`. Members-only pages use `memberGameDay`. Anything new that logs a URL goes through `loggable()`.
- Web tests with `webTest(session = test.loggedIn("Tim"))`, Jsoup + CSS selectors (`page()`, `fragment()`, `shouldReplace`), never substrings. Cover: the happy path, every rejection's wording, outsiders get nothing, closed/cancelled days are read-only.
- Look at it in the browser: `./gradlew buildFatJar`, then `preview_start` with `gorefest` (port 8080, from `.claude/launch.json`), log in via the code at `/dev/mailbox`, walk the flow on desktop and at phone width, light and dark. Check the console for errors.

Run `./gradlew test`. Commit: `U21 web: <what>`.

## 5. Docs

- `CLAUDE.md`: vocabulary for new terms, the events list, the web package list for a new package, matching notes if the matcher changed. Keep it to what a future reader needs.
- Request doc: tick every criterion, set `Status: ✅ built`, add a `Built as designed, with these notes:` list for anything that turned out different.
- The milestone table: row to ✅ with Done today; update the summary line above the table if it lists decisions.

## 6. Privacy check

Run the `privacy-check` agent on the story's commit range (give it the range, e.g. `<design commit>^..HEAD`). Review what it changed and commit it separately: `Privacy check after U21: <what changed>`.

## 7. Commit and report

Commit finished, tested work on `main` without asking; split as above or one `U21: <title>` commit for a small story. Messages are plain, user-facing sentences (see `git log`). Don't push: pushing deploys; the `release` skill does that when the user asks.

Report: what was built, the commits, any "built differently" notes, and anything left unticked or unverified.
