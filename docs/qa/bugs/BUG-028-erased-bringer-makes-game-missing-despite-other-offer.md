# BUG-028: When a game night's bringer is erased, the game becomes "missing" even though someone else coming offered to bring it

- **Severity:** minor
- **Area:** game night erasure (`GameNightSummary.erased`)

## Steps to reproduce
1. Tim's poll "ErasePoll" with CATAN; Anna and Cara both say "I'll bring it" for CATAN (Anna first). Both tick 27 Oct.
2. Tim picks 27 Oct: the game night plan has "CATAN · Anna brings it".
3. Admin erases Anna.
4. Open the game night.

## Expected
CATAN stays in the plan with Cara as the bringer: she offered and is coming. The criteria say a game becomes missing when the erased player was to bring it, and the plan's own rule is "the first one who offered and can come".

## Actual
CATAN moves to "Nobody can bring" ("These were in the poll, but nobody coming said they'd bring them"), and Cara shows "Bringing nothing from the plan". Same on the first night ("QA Night"): after Anna's erasure its plan is empty although only Anna was the planned bringer there, which is right in that case.

## Evidence
Game night page as Tim after the erasure: `The plan · 1 | Dixit … | Nobody can bring | … | CATAN | … | C | Cara | Bringing nothing from the plan`.

## Suspected cause (guess)
`PlannedGame` stores only the first bringer, so `erased()` has nobody to fall back on and moves the game to `missing`. The other offers live in the poll, not on the night.

## Status
Status: open
