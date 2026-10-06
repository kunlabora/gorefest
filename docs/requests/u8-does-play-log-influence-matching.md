# U8 Does logging a play influence the matching?

Source: user testing, 2026-10-06. Status: ✅

**Feedback (question):** Does logging a play have influence on the matching?

**Wanted:** answer it from the code and write the answer in this file (and README of the taste model in `CLAUDE.md` if it differs). Known candidates: group speed from `PlayLogged` for duration; "already played today" exclusion; checkpoint comparison after play.

**Criteria**
- [x] The answer is written below, with the code that backs it
- [x] If the answer is "not enough", list concrete follow-ups here (don't build them)

**Answer:** Yes, in three ways, but only indirectly, and not in the way people might expect.

1. **Duration (group speed).** `groupSpeed` (`core/gameday/Plays.kt`) takes every `PlayLogged` from every game day, keeps the logs where at least half of the current table played, and takes the median of actual minutes / estimated minutes (clamped 0.6-1.8). `App.groupSpeed(table)` passes it to `Matcher.suggest(speed = ...)`, which scales `estimatedMinutes`. That moves the "~N min" shown on each suggestion and decides which games pass a time budget. So a group that plays fast or slow sees different games fit.
2. **"Played" status.** The `Tastes` projection sets `played[game] = true` for every player in a `PlayLogged`. `PlayerGames.hasPlayed` is then true, which: (a) switches the taste model to the PLAYED scores for that game when the player has compared it after playing (`Predictor.enjoyment`), (b) removes the novelty bonus and the "nobody's played it" reasons for it (`Matcher`: `novel`), and (c) makes the wizard ask "which did you enjoy more?" instead of "which would you rather try?" for pairs including it (`Wizard.next`).
3. **The follow-up comparison.** After logging, the checkpoint asks one PLAYED comparison (`Wizard.partner`). That `ComparisonRecorded` is what actually teaches the model how much they enjoyed it; the log alone says nothing about enjoyment.

What it does **not** do: a game just played today is not excluded or down-weighted (it can come out on top again); how often a game gets played is not a signal; per-game durations don't feed the estimate (only the group-wide ratio does); and if the follow-up is skipped, the only effect on taste is "played, no opinion", which falls back to the appeal (VIBE) score.

**Follow-ups (not built):**
- Down-weight (or flag as "played earlier today") games already in today's play log, so the next suggestion isn't the game we just finished. Needs the day's plays passed into `Matcher.suggest`.
- Learn the speed per game (median of that game's logs at that player count) before falling back to the group-wide ratio.
- Treat "played often" as a weak positive signal, and "logged but compare skipped" as neutral.
- Show the effect: "Your group usually plays 20% quicker than the box" next to the time options.
