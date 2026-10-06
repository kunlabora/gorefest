# U16 Cancel a game day

Source: user testing, 2026-10-06. Status: ✅

**Feedback:** New feature: cancelling a game day.

**Decision (2026-10-06):** cancel silently (no mail). New event `GameDayCancelled` (tag `gameday:{id}`), only the host can cancel, not after it ended. A cancelled game day is read-only, its invite link stops working (landing says it was cancelled), and it shows as cancelled in members' lists. Don't rename or change existing events.

**Criteria**
- [x] Decider (host only, not twice, not after end) with given/when/then tests
- [x] Projection and `GameDay` read model carry the cancelled state; auto-close ignores it
- [x] Host UI with a confirm step; members see "Cancelled"
- [x] Event added to the list in `CLAUDE.md`

**Done:** `GameDayCancelled` event, `CancelGameDayDecider`; join/edit/rotate/bring/wish/log reject a cancelled day (`Rejection.GameDayCancelled`, which wins over a stray end); `GameDaySummary.cancelled`/`closed`; auto-close skips it. `memberGameDay` returns nothing (404) for a cancelled day unless `allowCancelled` (only the hub), so log, table, bring, readiness, taste and edit routes are closed. Host confirm page `/gamedays/{id}/cancel` (non-hosts get 404). Hub/list show "Cancelled". A member opening the invite lands on the hub, everyone else gets a 410 saying it was cancelled. The poll page says the picked game day was cancelled.
