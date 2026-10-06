# U7 Log plays during the game day, not only after it

Source: user testing, 2026-10-06. Status: 📋

**Feedback:** Logging plays only works after the game day is over; it should be possible during the game day.

**Wanted:** allow `LogPlay` while the game day is running (from its date, until it ends); check what the decider and the checkpoint UI currently require.

**Criteria**
- [ ] Decider test: logging during a running game day is accepted
- [ ] The play log is reachable from the hub during the game day
- [ ] Still rejected before the day starts (unless that is what blocks today: say so in the PR)
