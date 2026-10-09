# BUG-016: A one-person table gets "~30 min for the 1 of you"

- **Severity:** cosmetic (copy)
- **Area:** Suggestions (`/gamedays/{id}/table`, `/gamedays/{id}/suggestions`)
- **Environment:** desktop, Tim as host; reproduced for a table of Tim alone, Dan alone and Eve alone.

## Steps to reproduce
1. Have a game day with a shelf (e.g. 10+ games) and Tim, Dan, Eve joined.
2. Open `/gamedays/{id}/table`, untick everyone except one player ("1 at the table").
3. Read the duration chip of the top suggestion.

## Expected
Natural wording for one person: "~30 min solo" / "~30 min for you". (For 2+ the first card reads "for the 2 of you", the next cards "for 2", which is fine.)

## Actual
The first card says `~30 min for the 1 of you`.

## Evidence
```
GET /gamedays/{id}/suggestions?tableSet=1&players={tim}
 ... Agricola | Worker placement | ~30 min for the 1 of you | ...
```

## Suspected cause (guess)
`web/suggestions/SuggestionsViews.kt` line ~70: `+"~${s.minutes} min for the ${view.tableSize} of you"` has no case for `tableSize == 1`. (`SuggestionTexts.kt` has a similar `SafeBet` sentence, `all ${tableSize.inWordsLower()} of you rated it highly`, worth checking for 1.)

## Status

Status: fixed. A table of one now reads "for you" on the suggestion cards, and the SafeBet reason no longer says "all one of you"; commit: BUG-016 fix.
