# U5 Replace "Don't know" in the wizard with clearer "Haven't played it" wording

Source: user testing, 2026-10-06. Status: ✅

**Feedback:** "Don't know" can mean: I don't know either game, I don't know one of them, or I wouldn't know which to pick. We don't want the last one. "Haven't played" is clearer.

**Wanted:** reword the answer buttons so that they only mean "I haven't played this" (per game: one button on each card, or "Haven't played either") and keep the existing "both meh"/"both great" answers. Check `docs/design/microcopy.md` and update it.

**Criteria**
- [x] Button copy changed in the wizard, microcopy doc updated
- [x] Web test pins the wording

**Done:** the wizard's answers are now "Haven't played either", "Both meh", "Both great", plus a "Haven't played this" button under each game (`duelUnplayed`, answers `UNPLAYED_A` / `UNPLAYED_B`). All of them remember the game(s) as not played (`GamePlayStatusDeclared`), which the matcher uses (U2). "Don't know" and the silent `SKIP` are gone. The checkpoint's follow-up keeps its single "Haven't played" (it's about the other game, since the first was just played).
