# G2 Erase my account: the event log

Source: GDPR analysis, 2026-10-09 ([`docs/gdpr.md`](../gdpr.md)). Status: 📋

**Why:** Art. 17. The player's id and BGG username are all over the immutable log (finding 1). This is the core, with no UI yet (G3 adds it).

**Design (decided, change only with reason):**
- A new event `PlayerErased(player)` (never rename it; add with `@SerialName("PlayerErased")`). It is the *record* that an erasure happened, not a way to hide data: projections drop everything about that player on `evolve`.
- In one transaction, under the same `pg_advisory_xact_lock` as appends, `EventErasure.erase(player)` in `shell.eventstore`: (a) delete the player's own taste events (`ComparisonRecorded`, `GamePlayStatusDeclared`, `PlayWishChanged`, `BggAccountLinked`, `BringRequestsAllowedChanged`, `LoggedIn`, `PlayerRegistered`); (b) in every other event, replace the player's UUID with the fixed tombstone id `PlayerId.ERASED` in `data` **and** in `tags` (GIN index, so rebuild the `tags` array too); (c) delete `players`, `sessions`, `collections`, `collection_syncs`, `login_attempts` rows for that email/id; (d) append `PlayerErased`.
- Games hosted by the erased player: the game day stays for the others, host shown as "A former player". Free-text `place` in the erased host's `GameDayCreated`/`GameDayDetailsChanged`/`DatePollCreated` is blanked.
- Keep a plain table `erasures(player_id uuid, erased_at)` (no email) so a restore from backup can re-apply (see G7).

**Criteria**
- [ ] Decider `ErasePlayerDecider` given/when/then (idempotent; unknown player rejected with a typed `Rejection`)
- [ ] `EventErasure` integration test on Testcontainers: after erasure no event row, tag or table row contains the player's id, email or BGG username (assert with a text search over `data::text` and `tags`)
- [ ] Replay test: the log replays on startup with tombstoned ids; `GameDayShelves`, `PlayerTastes`, `GameDayPlays`, `LinkedBggAccounts` and bring requests all handle `PlayerId.ERASED` (no crash, no "ghost" player in counts, matcher ignores them; a play logged with the erased player still counts toward the group's speed)
- [ ] Old events still decode (CLAUDE.md event evolution rules); `core/Event.kt` list and the CLAUDE.md event list updated
- [ ] Other players' data is untouched (test with two players on one game day)
- [ ] `docs/gdpr.md` finding 1 marked fixed

**Agent notes:** the hard part is the rewrite being atomic with the lock; do it in a single transaction and add a test that a concurrent append waits. Read `EventStore`, `CommandHandler` and `Projector` first. Don't add UI.
