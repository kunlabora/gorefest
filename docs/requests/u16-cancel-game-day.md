# U16 Cancel a game day

Source: user testing, 2026-10-06. Status: 📋

**Feedback:** New feature: cancelling a game day.

**Decision (2026-10-06):** cancel silently (no mail). New event `GameDayCancelled` (tag `gameday:{id}`), only the host can cancel, not after it ended. A cancelled game day is read-only, its invite link stops working (landing says it was cancelled), and it shows as cancelled in members' lists. Don't rename or change existing events.

**Criteria**
- [ ] Decider (host only, not twice, not after end) with given/when/then tests
- [ ] Projection and `GameDay` read model carry the cancelled state; auto-close ignores it
- [ ] Host UI with a confirm step; members see "Cancelled"
- [ ] Event added to the list in `CLAUDE.md`
