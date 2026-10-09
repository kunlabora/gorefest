# BUG-017: Payoff kicker says "Round done · 1 answers"

- **Severity:** cosmetic (copy)
- **Area:** Taste wizard payoff (`/gamedays/{id}/favourites`)
- **Environment:** desktop; any player who is a member of the game day.

## Steps to reproduce
1. Be a member of a game day.
2. Open `/gamedays/{id}/favourites?answers=1` (this is the URL the wizard redirects to when a round ends after one recorded answer, e.g. a top-up round with a single uncertain pair; I forced the parameter, I did not manage to end a real round on exactly one answer).

## Expected
`Round done · 1 answer`.

## Actual
`Round done · 1 answers`. (Related, the `answers` value is trusted from the URL: `?answers=-5` renders `Round done · -5 answers`; harmless but it is user-editable text.)

## Evidence
```
GET /gamedays/{id}/favourites?answers=1   ->  <p class="gf-kicker">Round done · 1 answers</p>
GET /gamedays/{id}/favourites?answers=-5  ->  Round done · -5 answers
```

## Suspected cause (guess)
`web/taste/TasteViews.kt` line 95: `+"Round done · ${view.answers} answers"` has no singular case (other screens such as readiness do pluralise: "1 answer: picks will be guesses").
