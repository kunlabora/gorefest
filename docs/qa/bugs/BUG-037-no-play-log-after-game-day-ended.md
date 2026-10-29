# BUG-037: A play can't be logged once the game day has ended

- **Severity:** minor
- **Area:** play log (`LogPlayDecider`), game day hub

## Steps to reproduce
1. Play a game on the day without logging it.
2. After midnight the game day closes. Open its hub.

## Expected
A way to log the forgotten play, so the game day shows what was played (and the taste model and group speed learn from it).

## Actual
The hub has no "Log a game" link, and `POST /gamedays/{id}/log` answers "This game day has ended".

## Status
Status: fixed (2026-10-29). Plays can be logged after the game day ended (not after it was cancelled). The ended hub offers "Log a game", the log page says "After the game day", the "Which did you enjoy more?" step goes back to the hub instead of the table, and the hub's list is "Played", without a time for plays logged after the date. A play logged after closing misses the recap mail, which went out at midnight. Closed game days keep erased players (as "Someone"), so the decider now also has every `PlayerErased` in its boundary: a new play can't name someone who was erased.
