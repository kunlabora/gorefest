# G7 Retention, backups and restore

Source: GDPR analysis, 2026-10-09 ([`docs/gdpr.md`](../gdpr.md)). Status: ✅

**Why:** findings 6 and 7. Data lives forever, and a restore would bring back erased people.

**Wanted:**
- *Retention rules*, decided and written in `docs/gdpr.md` and the notice: login attempts 1 day (done), sessions until expiry (done), accounts inactive for 24 months get a warning mail then are erased through G2 (a job in the existing purge loop, `Login.startPurging`, behind `INACTIVE_ACCOUNT_MONTHS`, default 24, 0 = off), finished game days' free text (place) blanked 12 months after `GameDayEnded` (new `GameDayPlaceForgotten` event + a log rewrite of the old `place`, or simply keep place out of events by design: pick one and say why).
- *Backups*: 30 days is the **maximum**, decided. `deploy/backup.sh` prunes before dumping so a failing dump can't leave old data past it (done); add a check that fails loudly if any `gorefest-*.dump` in the bucket is older than 30 days (e.g. a second line in the backup service or a monitoring note), and make sure object versioning/object lock stay off (`deploy/scaleway.md` says so) since they would keep deleted dumps; document in `deploy/README.md` that erased data leaves backups within 30 days.
- *Restore*: `deploy/README.md` restore section gets a step that re-applies `erasures` (G2 table) after a restore, and a script `deploy/reapply-erasures.sh` or a `--reapply-erasures` startup flag that does it. Never restore without it.

**Criteria**
- [x] Inactive-account job tested with `TestClock` (warn, wait, erase; an active login resets it)
- [x] Re-apply-erasures tested: restore-like state (player present, id in `erasures`) → after the step the player is gone
- [x] `deploy/README.md` and `docs/gdpr.md` updated; privacy notice retention section ([G1](g1-privacy-notice.md)) matches
- [x] Decision on place retention recorded

**Built 2026-10-12**
- *Inactive accounts*: `players.last_login_at` and `inactive_warned_at` (V8); `InactiveAccounts` (`shell.account`) sweeps daily from `startBackgroundJobs`: after `INACTIVE_ACCOUNT_MONTHS` (default 24, 0 = off) without a sign-in a warning mail, 30 days later `AccountEraser` and a closing mail; signing in resets it; the admin and hosts of a game day still to come are skipped. Tests: `InactiveAccountsTest` (`TestClock`).
- *Place retention, decided*: the place stays in the events (not blanked, not moved out). Why: it is only shown to people who have joined; after an erasure the host's id points at nobody; blanking old places means rewriting the log, and moving them out means a plain table that every view looks up. Revisit if a host asks for it, or if the group grows beyond friends. The residue is recorded in `docs/gdpr.md`.
- *Backups*: `backup.sh` fails the unit if anything older than 30 days is left after the prune (versioning or object lock on); `deploy/README.md` says erased data leaves backups within 30 days.
- *Restore*: there is no `erasures` table (a restore would replace it too). Every erasure is announced to the admin's channel by id (`AccountEraser`), and after a restore `REAPPLY_ERASURES=id,id` runs them again at startup (`AccountEraser.reapply`; steps in `deploy/README.md`). Tested in `InactiveAccountsTest`.
