# U9 Having seen a table play a game helps form an opinion

Source: user testing, 2026-10-06. Status: ✅

**Feedback:** Having watched a table play a game sometimes also helps a player decide whether they'd like it.

**Idea:** a third comparison basis `WATCHED` (between VIBE and PLAYED in weight): at the checkpoint, "did you watch anyone play X?" then "would you rather play it than Y?".

**Decision (2026-10-06):** record only, build later.

**Open questions:** weighting of WATCHED vs VIBE and PLAYED; do we infer it from the play log's player list (people at the table next to it)? New event or a new basis on `ComparisonRecorded` (a default-valued enum case is a safe addition).

**Decision (2026-10-14):** a watched game counts as if it was played. Don't infer who watched: propose games from the game day's shelf and let the person say whether they watched one.

**Decided (2026-10-14):**
- "Counts as played" is for the taste model only (the enjoyment layer, like a `PLAYED` comparison). Watching doesn't make it "played": the novelty bonus and the "new to some" reason stay.
- Propose the whole shelf of the game day, not just games logged as played: the log may not be filled in yet when someone marks a game as watched.
- A game that isn't on the shelf can be found with a BGG search (like bringing a game on the day).

**Built (2026-10-14)**
- [x] A third basis `WATCHED` on `ComparisonRecorded`; the taste models train it as `PLAYED` (`Basis.model`), and it doesn't mark the game played (`TasteModelTest`)
- [x] A watched game you never played gets an enjoyment guess and keeps the novelty bonus (`MatcherTest`)
- [x] On the day, the hub offers "Watched a game?" when the shelf has games; `/gamedays/{id}/watched` shows the whole shelf minus what you played, with a BGG search for anything else
- [x] One question, "Now that you've seen it, which would you rather play?", against a game you played; "Can't tell yet" records nothing; with nothing played to compare, it says so
- [x] Closed once the game day ends or is cancelled (`CheckpointRoutesTest`)

