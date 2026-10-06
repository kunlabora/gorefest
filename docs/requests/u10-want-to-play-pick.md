# U10 Indicate which shelf game you really want to play

Source: user testing, 2026-10-06. Status: ✅

**Feedback:** An option to just indicate which game of the shelf you really want to play.

**Today:** `PlayWishChanged` exists for the owner's desire to play their own game. Check whether participants can use it for any shelf game and whether the UI exposes it.

**Wanted:** on the shelf, every member can mark games they really want to play (a heart/star, a few at most); the matcher gives a bonus per person who wants it; suggestions say "2 of you want this".

**Criteria**
- [x] Any member can toggle a wish on a shelf game (decider + test)
- [x] Shelf UI control
- [x] Matcher bonus and a `SuggestionReason` case
- [x] Documented in `CLAUDE.md` matching section

**Done:** `SetPlayWishDecider` now accepts any joined player for any game on the shelf (rejections `NotOnShelf`, `TooManyWishes`, limit `MAX_WISHES` = 3 per player per game day; the wish and the shelf projection `GameDayShelves` keep the same event, `PlayWishChanged`). The bring page has a "What do you want to play?" section listing the whole shelf with a heart per game and a count. `Matcher` adds the wish bonus (0.3) per wisher at the table (3 people at most) and the reason `SuggestionReason.WantedBy` ("Anna and Bram both want to play this."). A game leaving the shelf drops its wishes; an owner un-bringing no longer drops their own wish while someone else still brings the game. Tests: `ShelfTest`, `ShelfRoutesTest`, `MatcherTest`, `SuggestionTextsTest`.

**Not done:** the game day hub (`web.gameday`) has no direct link to the wish list; the shelf section's "Bring games" link leads to the same page. Another agent works in that area, so a "Pick your favourites" link there is left for the coordinator.
