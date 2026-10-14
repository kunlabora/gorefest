# BUG-033: Date cards list who can't come as "Tim and Anna and Cara and Dan and Eve can't"

- **Severity:** cosmetic
- **Area:** date poll results (`datePollResults`, both kinds of poll)

## Steps to reproduce
1. A poll with five people; nobody ticks 28 Oct (or three people don't tick 22 Oct).
2. Look at the date card.

## Expected
"Tim, Anna, Cara, Dan and Eve can't", like the game cards on the same page ("Anna, Cara and Eve want to play it", `joinAnd()`).

## Actual
"Nobody can · Tim and Anna and Cara and Dan and Eve can't"; "3 can · Tim and Bram and Dan can't". After an erasure the same line reads "Cara and someone can't" while other places say "Someone"; at the start of a line it shows as "Still to answer: someone, Dan."

## Evidence
Poll page text: `Wednesday 28 October | Nobody can · Tim and Anna and Cara and Dan and Eve can't`.

## Suspected cause (guess)
Line 397 of `DatePollViews.kt` uses `declined.joinToString(" and ")` (from 2026-10-08, so the plain poll had it before round 3) instead of `joinAnd()`; the fallback name there is a lowercase "someone" rather than `SOMEONE`.

## Status
Status: fixed. Date cards use the shared `joinAnd()` ("Anna, Bram and Ruben can't") and the "Someone" placeholder.
