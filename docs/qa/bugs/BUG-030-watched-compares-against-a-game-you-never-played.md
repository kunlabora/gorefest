# BUG-030: "Now that you've seen it" can hold the watched game up against a game you've never played

- **Severity:** minor
- **Area:** U9 watched a game (`/gamedays/{id}/watched/compare`)

## Steps to reproduce
1. Bram has a linked BGG collection and has never played or compared anything.
2. On today's game day, Bram opens "Watched a game?", picks Dixit, Next.

## Expected
U9: "One question … against a game you played; with nothing played to compare, it says so." Bram has played nothing, so he should get the "Nothing to compare yet" page (as Dan, without a collection, does).

## Actual
"You watched Dixit · Now that you've seen it, which would you rather play? A Dixit or B ito", where ito is a game in Bram's collection that he never played. The answers are "Can't tell yet / Both meh / Both great" plus A/B; there is no "haven't played this one". Picking A or B records a `WATCHED` comparison, which the taste model trains like `PLAYED` for both games, so ito gets an enjoyment signal without having been played. The same happens after CATAN and Carcassonne were logged as played by Bram: for Dixit, Concordia and Power Grid the partner was still ito, never a game he played.

## Evidence
`GET /gamedays/{id}/watched/compare?game=39856` as Bram: hidden inputs `a=39856`, `b=327778` (ito). Dan (no collection, no plays) gets "Nothing to compare yet".

## Suspected cause (guess)
The watched route reuses the play-log candidates `player.history.played + player.owned + player.history.comparedGames`; "owned" and "compared" (vibe) games aren't games you played.

## Status
Status: open
