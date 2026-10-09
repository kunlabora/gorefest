# G7 Retention, backups and restore

Source: GDPR analysis, 2026-10-09 ([`docs/gdpr.md`](../gdpr.md)). Status: 📋

**Why:** findings 6 and 7. Data lives forever, and a restore would bring back erased people.

**Wanted:**
- *Retention rules*, decided and written in `docs/gdpr.md` and the notice: login attempts 1 day (done), sessions until expiry (done), accounts inactive for 24 months get a warning mail then are erased through G2 (a job in the existing purge loop, `Login.startPurging`, behind `INACTIVE_ACCOUNT_MONTHS`, default 24, 0 = off), finished game days' free text (place) blanked 12 months after `GameDayEnded` (new `GameDayPlaceForgotten` event + a log rewrite of the old `place`, or simply keep place out of events by design: pick one and say why).
- *Backups*: keep the 30-day rule in `deploy/backup.sh`; document in `deploy/README.md` that erased data leaves backups within 30 days.
- *Restore*: `deploy/README.md` restore section gets a step that re-applies `erasures` (G2 table) after a restore, and a script `deploy/reapply-erasures.sh` or a `--reapply-erasures` startup flag that does it. Never restore without it.

**Criteria**
- [ ] Inactive-account job tested with `TestClock` (warn, wait, erase; an active login resets it)
- [ ] Re-apply-erasures tested: restore-like state (player present, id in `erasures`) → after the step the player is gone
- [ ] `deploy/README.md` and `docs/gdpr.md` updated; privacy notice retention section ([G1](g1-privacy-notice.md)) matches
- [ ] Decision on place retention recorded
