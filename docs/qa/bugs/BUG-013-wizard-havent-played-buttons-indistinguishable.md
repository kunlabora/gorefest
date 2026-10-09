# BUG-013: The two "Haven't played this" buttons in the taste round have identical accessible names

- **Severity:** minor (accessibility)
- **Area:** Taste wizard duel component (`/gamedays/{id}/taste`)
- **Environment:** desktop, any theme, Tim in a top-up round; same markup in first-time rounds.

## Steps to reproduce
1. Log in as Tim, open any game day and `/gamedays/{id}/taste`.
2. In the page's accessibility tree (e.g. the Browser pane's `find "Haven't played this"` / `read_page`), or with a screen reader / voice control ("click haven't played this"), look at the two links under the cards.

## Expected
Each button says which game it is about ("Haven't played King of Tokyo"), as the brand book asks for labelled controls and says selection is never colour/position only.

## Actual
Two `button[name=answer]` elements both read `Haven't played this` with no `aria-label`; only their value differs (`UNPLAYED_A` vs `UNPLAYED_B`). A screen reader user hears the same label twice and cannot tell A from B; voice control cannot target one.

## Evidence
```
find "Haven't played this" ->
- button "Haven't played this" [ref_31] type="submit"
- button "Haven't played this" [ref_32] type="submit"
DOM: {t:"Haven't played this", aria:null, v:"UNPLAYED_A"}, {t:"Haven't played this", aria:null, v:"UNPLAYED_B"}
```
No screenshot (visually it looks right: each sits under its card).

## Suspected cause (guess)
`TasteViews.kt` / the `duel` component renders the button text as a fixed string; add `aria-label="Haven't played ${game.name}"` (visible text can stay).
