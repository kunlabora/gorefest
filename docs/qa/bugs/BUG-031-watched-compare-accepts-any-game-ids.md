# BUG-031: The watched answer accepts any pair of game ids, including games that don't exist

- **Severity:** minor (needs a hand-made request; writes junk into the permanent log)
- **Area:** U9 `POST /gamedays/{id}/watched/compare`

## Steps to reproduce
As Dan, a member of today's game day:
```
curl -b dan -X POST -d 'a=-7&b=99999999&answer=A' /gamedays/{id}/watched/compare   -> 302
curl -b dan -X POST -d 'a=13&b=822&answer=A'      /gamedays/{id}/watched/compare   -> 302
```

## Expected
Like the play-log answer (which checks that you played `a` on this game day), only a game from the watched list (shelf minus what you played, or a searched game) against the partner the page offered is accepted; unknown ids are refused.

## Actual
Both are recorded as `ComparisonRecorded(basis = WATCHED)`: `{"a": -7, "b": 99999999, …}` and CATAN over Carcassonne (neither was offered). Effects seen:
- The data export lists "Compared two games · a: BGG game -7 · b: BGG game 99999999 · basis WATCHED".
- Dan's next taste round starts at "3 / 15" with `asked="-7-99999999,13-822"` in its hidden state, and his favourites say "CATAN: You picked it over Carcassonne".
(`a == b` is refused by `RecordComparisonDecider`, silently: the redirect is the same.)

## Suspected cause (guess)
The POST handler only checks `watchOpen`, that `a`/`b` parse as ints and the answer; it doesn't check the ids against the catalog, the watched list, or the partner.

## Status
Status: open
