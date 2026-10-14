# BUG-035: Every game card on a poll has buttons with the same accessible names ("I'd play this", "I'll bring it", "Take off")

- **Severity:** minor (screen readers)
- **Area:** poll page games section (U11)

## Steps to reproduce
1. Open a poll that asks about 5 games.
2. List the buttons in the accessibility tree (or with a screen reader's buttons list).

## Expected
Each button says which game it is about, e.g. "I'd play CATAN" / "Take CATAN off" (as done for the taste round's "Haven't played this" buttons in BUG-013).

## Actual
Five "I'd play this", five "Not for me", five "I'll bring it", five "Not this time" and five "Take off" buttons, with no `aria-label` and no labelled group; the game name is only in the card's heading.

## Evidence
`find "I'd play this"` on `/polls/{id}` returns 5 identical matches; the card markup has `<button … name="yes" value="true"><span>I'd play this</span></button>` with no label.

## Status
Status: open
