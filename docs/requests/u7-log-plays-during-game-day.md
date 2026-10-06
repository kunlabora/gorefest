# U7 Log plays during the game day, not only after it

Source: user testing, 2026-10-06. Status: ✅

**Feedback:** Logging plays only works after the game day is over; it should be possible during the game day.

**Wanted:** allow `LogPlay` while the game day is running (from its date, until it ends); check what the decider and the checkpoint UI currently require.

**Criteria**
- [x] Decider test: logging during a running game day is accepted
- [x] The play log is reachable from the hub during the game day
- [x] Still rejected before the day starts (unless that is what blocks today: say so in the PR)

**Done:** Finding: the decider and the hub card already allowed logging on the day itself (only ended game days were refused), so the "only after" report was not reproducible from code. Now: `LogPlayDecider` refuses before the game day's date (`GameDayNotStarted`, judged in its time zone, follows edits of the date), the hub card shows from the date on, and the "Played today" section keeps a "Log a game" link.
