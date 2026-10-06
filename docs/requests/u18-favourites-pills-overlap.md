# U18 "Your likely favourites today": pills overlap the bottom after scrolling

Source: user testing, 2026-10-06. Status: ✅

**Feedback:** On "Your likely favourites today", after scrolling the 2 and 3 pills overlap the bottom of the screen; they stay visible but everything else is hidden.

**Wanted:** reproduce on a phone viewport (the sticky dock, `.gf-dock`, or the ranked-game pills probably use `position: sticky/fixed` against the wrong container) and fix in the design CSS.

**Criteria**
- [x] Reproduced at 375x812
- [x] Fixed, checked scrolled to the bottom
- [x] A markup/web test where it can be pinned (e.g. rank pills are inside the card)

**Done:** Reproduced at 375x812: `.gf-game__rank` (z-index 1) painted over the sticky `.gf-dock` (z auto). Fixed with `z-index: 2` on the dock and `isolation: isolate` on `.gf-game`; verified by hit-testing a pill under the dock before/after.
