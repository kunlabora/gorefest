# G2 Erase my account: the event log

Source: GDPR analysis, 2026-10-09 ([`docs/gdpr.md`](../gdpr.md)). Status: ✅ (anonymisation); the follow-ups below are open.

**Why:** Art. 17. The player's id is all over the immutable log (finding 1).

**Decided 2026-10-12: anonymise, don't rewrite.** Events only carry player ids; the email and name live in `players`. Erasing deletes that link, so the ids left in the log point at nobody. The log is never edited.

**Done**
- [x] `PlayerErased(player)` event; `ErasePlayerDecider` (idempotent; a player who never registered is rejected with `Rejection.NoSuchPlayer`)
- [x] `AccountEraser.erase(player)` in `shell.auth`: records the event first, then deletes the `players` row (sessions, collection and sync rows cascade) and the login attempts for the email. Safe to run again after a failure halfway
- [x] Projections: `GameDays` drops the player from tables still to be played (over and cancelled ones keep their head count); `LinkedBggAccounts` forgets the link; `LinkBggAccountDecider` no longer counts an erased player's link, so the BGG username is free again
- [x] Tests: decider given/when/then; integration test on Testcontainers (no email or name left in the plain tables, other players untouched, the log only grows by one event, erasing twice changes nothing, the same email can sign up again as a new person); `EventCompatibilityTest` has a sample
- [x] Old events still decode; CLAUDE.md event list and architecture note updated; `docs/gdpr.md` finding 1 marked fixed

**Residue we accept for now** (the log still holds it)
- `BggAccountLinked` events written before 2026-10-12 hold the BGG username (a public handle). New links store a keyed hash (`BggAccountLinkedHashed`).
- Free-text `place` and `title` in `GameDayCreated`, `GameDayDetailsChanged` and `DatePollCreated`.
- Other projections (tastes, plays, and the shelf and bring requests of closed game days) still list the erased id: it shows as "Someone" and counts as before. Their data is pseudonymous and the taste model keeps learning from it. On game days still to be played, the shelf drops the erased player's games and wishes and their open bring requests both ways (BUG-025, 2026-10-14). Date polls drop the erased player's dates, game answers and offers, and an erased host's poll is over (BUG-027/028); game nights drop them and hand a game they'd bring to the next one who offered.

**Done 2026-10-12 (b, username):** new links write `BggAccountLinkedHashed` (HMAC of the lowercased username, key `SECRET_KEY`, used as the uniqueness tag; the decider also still honours old plain links). The readable name is read from `collection_syncs`. `Rejection.AlreadyLinked` no longer carries a name.

**Follow-ups, if we want them**
- Keep the place out of new events (decided to leave it for now): a plain table for `place` per game day and date poll, written by the shell, deleted with the host. Costs the pure read model its `place`; every view that shows it looks it up.
- A way to run it: G3 wires the admin to `AccountEraser`.
