# BUG-034: Poll with games: no cap on ticked games before submit, and copy that still says "game day" or "favourites"

- **Severity:** cosmetic
- **Area:** date poll form, poll invite landing, poll game answers (U11)

## Steps to reproduce and actual
1. **Form, 13+ games:** /polls/new, switch on "Ask about games as well" (hint: "Tick a few to put to the group, up to 12"). All 31 of Tim's games can be ticked; only after "Start the poll" does it say "That's a lot of games: keep it to 12" (the ticks are kept). The same kind of issue as BUG-010 for dates, which got a client-side limit.
2. **Form lede:** with the switch on, the left column still says "You choose the winner; the game day is made for you", while the switch's hint says the pick "makes a small game night instead of a game day".
3. **Invite landing** of a poll with games (`/p/{token}`, signed out): "Tim wants to find a date for a game day … and which games to play … Tim picks the date and the game night appears."
4. **Fourth yes:** answering "I'd play this" on a fourth game says "You can pick 3 favourites: take one off first", while the poll's own hint says "Up to 3 games you'd play" (the wish rejection text reused; "favourites" is the taste-round word).
5. **Hand-made request only:** a non-host `POST /polls/{id}/games/{game}/remove` is refused with "Only the host adds games".

## Expected
A client-side stop at 12 ticks (or a counter); "game night" wherever the poll asks about games; a yes-limit message in the poll's own words ("You can say yes to 3 games: take one back first"); "Only the host changes the games".

## Evidence
curl/DOM text quoted above; the form has no script on the game checkboxes besides moving ticked tiles to `#poll-form-picked`.

## Status
Status: open
