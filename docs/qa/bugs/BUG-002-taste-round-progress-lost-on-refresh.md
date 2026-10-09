# BUG-002: Refreshing the taste round resets the progress bar and the payoff undercounts the answers

- **Severity:** minor
- **Area:** Taste wizard (`/gamedays/{id}/taste`)
- **Environment:** desktop, dark theme, new player Dan (first-time round, mode `full`). Also reproduced with player Tim in the browser (reload showed `1 / 15` with one answer already saved).

## Steps to reproduce
1. As Tim: link BGG (`timbgg`), create a game day and bring about 10 games (shelf of 10+).
2. As Dan (new email, set a name): join through Tim's invite link.
3. Open `http://localhost:8099/gamedays/{id}/taste` and answer 6 pairs (the counter reads `6 / 15`).
4. Reload the page (or leave and come back through "Start the taste round").
5. Keep answering with "A" until the round ends.
6. As Tim open `/gamedays/{id}/ready`.

## Expected
Progress continues where it was (`7 / 15`), or at least the round is sized by the answers already recorded; the payoff says how many answers the player gave.

## Actual
- After the reload the counter is back at `1 / 15` and the round runs its whole length again. Dan needed 6 + 12 = 18 answers.
- The payoff page says `Round done · 12 answers` (`/favourites?answers=12`) although 18 were recorded.
- Readiness for Dan: `18 answers · brought 0`.
- Tim saw the same on a single answer: reload showed `1 / 15` although one comparison was saved.

## Evidence
```
after refresh: 1 / 15
finished after 18 answers ['HX-Redirect: .../favourites?answers=12']
/ready: "D Dan 18 answers · brought 0 Ready"
```
(Driven with a script that POSTs the hidden fields back, as the page does.)

## Suspected cause (guess)
`TasteRoutes.kt`, `get("/gamedays/{id}/taste")` always builds a fresh `RoundState` (step 0, nothing asked). Round state lives only in hidden form fields, so it dies with the page; the answers already in `app.tastes` are not used to seed `step`.
