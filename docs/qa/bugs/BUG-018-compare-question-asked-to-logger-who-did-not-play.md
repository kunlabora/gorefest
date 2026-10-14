# BUG-018: After logging a play, the logger is asked "Which did you enjoy more?" even when they weren't one of the players

- **Severity:** minor
- **Area:** Play log / checkpoint (`/gamedays/{id}/log` then `/gamedays/{id}/log/compare`)
- **Environment:** desktop, Tim (host) and Anna/Bram as members; game day dated today with CATAN, Carcassonne, Ticket to Ride on the shelf.

## Steps to reproduce
1. As Tim create a game day dated today, have Anna and Bram join, bring a few games.
2. As Tim open `/gamedays/{id}/log`. Pick Carcassonne, **untick Tim** (leave only Anna), choose 45m, press "Log it".
3. You land on `/gamedays/{id}/log/compare?game=822&minutes=45&players={anna}`.

(Same by `curl -b tim.jar -d "game=13&players={bram}&duration=1h" /gamedays/{id}/log`.)

## Expected
The follow-up comparison is about the logger's own experience. If the logger wasn't at that table there is nothing to ask: skip to the table/suggestions (the page already does this when there is no partner game).

## Actual
The page shows "Logged Carcassonne: 1 player, 45 min." and the question "Which did you enjoy more? Carcassonne or ito". Answering records a `ComparisonRecorded` with basis `PLAYED` for Tim for a game Tim didn't play, which feeds his taste profile (and the "answers" counts). Reproduced twice (Carcassonne with Anna only, CATAN with Bram only).

## Evidence
```
POST /gamedays/{id}/log  game=822 players={anna}  ->  Location: .../log/compare?game=822&minutes=45&players={anna}
GET  that location as Tim ->  "Which did you enjoy more? A Carcassonne ... or B ito ..."
```

## Suspected cause (guess)
`CheckpointRoutes.kt`, `get("/gamedays/{id}/log/compare")` filters `players` by game day membership but never checks that `me.id` is in them before building the `LogCompareView`; `post("/gamedays/{id}/log/compare")` records the comparison unconditionally with `Basis.PLAYED`.

## Status
Status: fixed. The compare page sends you to the table when you weren't among the players, and the answer is only recorded for a game you were logged as playing that day (`CheckpointRoutesTest`).
