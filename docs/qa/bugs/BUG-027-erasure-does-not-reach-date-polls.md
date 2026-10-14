# BUG-027: Erasing someone doesn't take them out of open date polls, so they end up on the game night (or game day) made from the pick

- **Severity:** major (an erased person is "coming" and "brings" a game on the night; contradicts the criteria and the privacy notice)
- **Area:** erasure x date polls (`DatePolls`, `PickDateDecider`), game nights and game days made from a poll

## Steps to reproduce
1. Tim starts a date poll with "Ask about games as well" on, games CATAN and Dixit, dates 27 and 28 Oct ("ErasePoll"). Anna, Cara, Dan and Eve join via the poll link.
2. Everyone ticks 27 Oct. Eve says yes to CATAN and Dixit and "I'll bring it" for Dixit; Dan says yes to Dixit.
3. Admin erases Eve (`POST /admin/requests/{eve}/erase`) while the poll is still open.
4. Look at the poll as Tim, then pick 27 Oct and open the game night and "Pick a game for my table".
5. Same with a poll without games: Fay hosts "PlainErase", Hana and Dan tick 29 Oct, Hana is erased, Fay picks 29 Oct.
6. Erase the host of an open poll (Gus hosts "GusPoll", Dan joined), then use the poll link as Bram.

## Expected
The criteria (`docs/requests/u11-...`, "Erasure: an erased player leaves the game night (their yeses go, a game they were to bring becomes missing)") and the privacy notice ("you leave every game night") hold whether the person is erased before or after the pick: an erased person's dates, yeses and offers stop counting in an open poll, and they are not put on the game night or game day it makes.

## Actual
- Open poll after the erasure: "5 can" on 27 Oct with a "?" avatar, "Anna, Cara and someone want to play it", Dixit: "Someone brings it", "Someone brings every game".
- After the pick the game night has the erased player on it: "5 coming", Who's coming lists "Someone · Brings Dixit", the plan has "Dixit · Someone brings it · Someone and Dan want to play it" (screenshot, taken after Anna and Cara were erased too, so it says 3 coming). The `GameNightCreated` event lists Eve's id in `players` and as Dixit's `bringer`.
- "Pick a game for my table" ticks Someone by default ("5 at the table", only 4 avatars drawn) and suggests Dixit because "Dan and Someone both want to play this".
- Poll without games: the game day made from the pick has "3 going" with "Someone · Bringing nothing yet" (Hana). Erasing after the pick does work (Anna left "PlainPoll" game day: "1 going").
- A poll whose host is erased stays open for ever: nobody can pick, the link still lets new people join (Bram joined GusPoll after Gus was erased), "Still to answer: someone, Dan."

## Evidence
- `screenshots/BUG-027-erased-player-brings-game-on-game-night.png`
- `GameNightCreated` for ErasePoll: `"players": [tim, anna, cara, dan, "ed68de3e-…" (Eve, erased 1 min before the pick)]`, plan `{"game": 39856, "bringer": "ed68de3e-…"}`.

## Suspected cause (guess)
`DatePolls`/`DatePollSummary.after` and the deciders' `evolvePoll` never see `PlayerErased` (it isn't tagged with the poll, and no projection case handles it), so `coming()`, `planFor()` and `yesFor()` still include the erased id at pick time. `GameNights` only applies erasure to nights that exist when `PlayerErased` arrives, and `GameDays` re-adds the player through the `PlayerJoined` the pick emits.

## Status
Status: fixed. An erased participant leaves open polls with their dates, game answers and offers (`DatePollSummary.after(PlayerErased)`); a poll whose host is erased is over (`hostGone`: no pick, join or answers, its link answers 410 "This poll is over"). The pick, answer and join deciders take erasures into their boundary. Tests: `PollGamesTest`, `PollGamesRoutesTest`.
