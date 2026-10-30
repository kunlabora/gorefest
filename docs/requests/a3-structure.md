# A3 Structure: one heading, headings in order, announced swaps

Source: product idea, 2026-10-30. Status: ✅ built. Design: [Accessible by construction](a-accessible-by-construction.md).

**Idea:** `page` renders the one `<h1>`, `section` picks heading levels from nesting, `swapTarget` makes every htmx result region announce itself, and `page()` in the web tests checks the rendered structure on every page.

## Criteria

- [x] A11Y-7: Every page names its language and has one main heading, one main region and named navigation.
- [x] A11Y-8: Headings follow each other without skipping a level.
- [x] A11Y-9: Ids on a page are unique, and every reference to one (labels, descriptions, controls, htmx targets) resolves.
- [x] A11Y-10: A result htmx swaps in is announced to a screen reader; a refusal is announced at once.
- [x] `ArchitectureTest`: no view calls `h1(` … `h6(`.
- [x] `HtmlAssertions.page()` and `fragment()` run the structure checks (the id checks arrived with the A1 review: unique ids, references resolve).
- [x] Screens look as before; no event, route or stored data changes.

Built differently from the design, with these notes:

- **Levels are passed, not derived from nesting.** The design had `section(heading)` pick the level from how sections nest. The pages lay out headings in splits, cards and bands that aren't sections, so that would have meant wrapping most pages anew. Instead `Level` is a parameter: `pageHeading` (h1), `sectionHeading` (h2) and `heading(Level.Hn, …)`, and every component that renders a heading takes its `level` from the page (`emptyState`, `gameRow`, `rankedGame`). `page()` in the web tests checks the outline on every page, and caught fifteen pages that skipped a level (mostly an empty state or a game row's `h3` straight under the `h1`) and four with no `h1` (not found, the refused-form fallback, the watched "nothing to compare" page).
- **Announcing a swap needs a status line that stays, not live regions.** htmx replaces the swapped region (`outerHTML`), so an `aria-live` on it isn't reliably read. The page has one `role=status` line (`#gf-announce`), every fragment carries its sentence (`announce`), and `htmx-config.js` reads it from the response's root after the swap. A background refresh is `refreshesQuietly()`, so it doesn't talk over what you just did (in the browser, the requests section's refresh first overwrote "Bringing Azul"). `fragment()` checks every fragment announces, alerts or is quiet. `liveSection` went; `liveLine` stays for the poll's limit message, which a script writes into a line that stays put.
- **Focus after a swap.** In the browser, toggling a bring switch or a heart dropped focus to the page. They now keep an `id`, and htmx puts focus back on the element with the same id. Rows whose control disappears (Ask, Bring it, Take it back, Not this time) still lose focus: listed under Known gaps.
- The id checks landed with the A1 review (`d321e0b`).

After review (2026-10-30, the same review):

- A refused "Take it back", "Not this time" or "Bring it" (an ended game day) answered with a quiet section and no reason. The request sections now show the refusal as an alert, and `fragment()` fails when a 4xx fragment has no alert or calls itself a quiet refresh. Web tests prove a refusal is read out, an action says what it did, and a background refresh stays quiet.
- The status line skipped a sentence that matched the last one, so a second search that also found 3 games was silent. Only a poll (`hx-trigger` "every …") skips a repeat now; anything else clears the line and fills it again.
