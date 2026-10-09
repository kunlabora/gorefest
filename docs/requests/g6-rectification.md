# G6 Change email, unlink BGG

Source: GDPR analysis, 2026-10-09 ([`docs/gdpr.md`](../gdpr.md)). Status: 📋

**Why:** Art. 16 and the principle of control. Finding 5. Depends on [G2](g2-erasure-event-log.md) for the log side of unlinking.

**Wanted:**
- *Change email*: on `/account`, enter a new address; a code goes to the **new** address; on success the `players.email` is replaced and other sessions stay. Rate limits as for login; the old address gets a notice mail. Reject an address already in use with a neutral message (no account enumeration).
- *Unlink BGG*: a new event `BggAccountUnlinked(player)` (add; never rename old events); the shell deletes `collections` and `collection_syncs` rows; the projection forgets the username. The username in the old `BggAccountLinked` row is removed by the erasure machinery from G2 (expose `EventErasure.forgetBggUsername(player)`).

**Criteria**
- [ ] Deciders given/when/then for unlink; projection test
- [ ] Web tests: email change happy path, wrong code, taken address, rate limit; unlink removes the collection and the username from the page and from `events.data::text`
- [ ] Nothing leaks whether an email exists
