# BUG-029: A long single-word title overflows the poll page and the game night page (and scrolls sideways at 375px)

- **Severity:** minor
- **Area:** date poll page (with and without games), game night page; CSS of `gf-h1--landing`

## Steps to reproduce
1. Start a date poll titled `Supercalifragilisticexpialidocious-boardgame-marathon-night-2026-XL` (68 chars, within the 80 limit). Same with a game day of that title for comparison.
2. Open the poll at 1280px, and at 375px. Pick a date on a poll with games and open the game night at 375px.

## Expected
The title wraps or breaks inside its column, as on the game day hub (BUG-006 fix: hub h1 is 528px wide in a 528px column).

## Actual
- Poll page, 1280px: the h1 is 783px wide in a 384px column and runs under the date cards ("Supercalifragilistice…" is cut off by the right column).
- 375px: the page scrolls sideways (document scrollWidth 541 for a 371px viewport) on the poll page (with and without games) and on the game night page; the title is cut at the right edge.
- The game day hub with the same title is fine.

## Evidence
`screenshots/BUG-029-long-title-poll-desktop-light.png`, `screenshots/BUG-029-long-title-gamenight-mobile-dark.png`. Scripted check (iframes at 375px): `/polls/{id}` sw=541, `/gamenights/{id}` sw=541, `/gamedays/{id}` sw=371.

## Suspected cause (guess)
`gf-h1--landing` (used by the poll and game night pages) has no `overflow-wrap: anywhere`/`hyphens`, unlike the hub's h1 after BUG-006. The plain poll page probably had this before round 3; the game night page is new.

## Status
Status: open
