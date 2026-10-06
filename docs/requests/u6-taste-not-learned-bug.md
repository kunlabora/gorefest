# U6 "We haven't learned your taste" shown right after a taste test

Source: user testing, 2026-10-06. Status: ✅

**Feedback:** A player was told we haven't learned their taste even though they just did the taste test. They had not linked BGG and had pressed the browser back button midway through the test.

**Wanted:** reproduce (back button mid-wizard, no BGG link) and fix. Candidates: the wizard's state lives in the page/URL and back re-submits or skips answers; answers recorded for another game day; the "thin profile" check counts only some bases.

**Criteria**
- [x] A failing test that reproduces it
- [x] Fix, plus a regression test for back-button navigation mid-wizard
- [x] The "haven't learned" message appears only when it is true

**Cause and fix:** the matcher calls a player's taste "known" after 10 recorded comparisons, but the round counted every answer as a step, including "Don't know" and "Skip", which record no comparison. A round that ended after 10 to 15 steps could therefore hold fewer than 10 comparisons (the BGG link and the back button were red herrings: stale forms re-post the same pair, which only adds a duplicate comparison). Now only answers that record a comparison advance the round (`Wizard.advance(learned = ...)`); "haven't played" and "skip" merely retire the pair. Tests: `TasteRoutesTest` (skipped pairs, back button with a stale form).
