# G6 Change email, unlink BGG

Source: GDPR analysis, 2026-10-09 ([`docs/gdpr.md`](../gdpr.md)). Status: ✅

**Why:** Art. 16 and the principle of control. Finding 5. Depends on [G2](g2-erasure-event-log.md) for the log side of unlinking.

**Wanted:**
- *Change email*: on `/account`, enter a new address; a code goes to the **new** address; on success the `players.email` is replaced and other sessions stay. Rate limits as for login; the old address gets a notice mail. Reject an address already in use with a neutral message (no account enumeration).
- *Unlink BGG*: a new event `BggAccountUnlinked(player)` (add; never rename old events); the shell deletes `collections` and `collection_syncs` rows; the projection forgets the username. The username in the old `BggAccountLinked` row is removed by the erasure machinery from G2 (expose `EventErasure.forgetBggUsername(player)`).

**Criteria**
- [x] Deciders given/when/then for unlink; projection test
- [x] Web tests: email change happy path, wrong code, taken address, rate limit; unlink removes the collection and the username from the page and from `events.data::text`
- [x] Nothing leaks whether an email exists

**Built 2026-10-12** (the request text above predates the decision to anonymise rather than rewrite the log):
- *Change email*: `/account` has a form; `EmailChange` (`shell.auth`) mails a 6-digit code to the new address (table `email_changes`, code hashed, 15 minutes, five tries, five codes per player per hour, the pure `LoginAttempt` rules), and entering it replaces `players.email`; sessions stay and the old address gets a notice. An address another account has gets no mail but the same screen, so nothing leaks. If the admin changes their own address, `ADMIN_EMAIL` must change with it. Tests: `EmailChangeRoutesTest`.
- *Unlink BGG*: `BggAccountUnlinked` event, `UnlinkBggAccountDecider`, a button on `/bgg`; the shell stops any sync and deletes the collection and sync rows (`CollectionSync.forget`). The username becomes free (`LinkBggAccountDecider` folds links, unlinks and erasures in order). The username in older `BggAccountLinked` rows stays in the log (accepted residue, see G2); links since 2026-10-12 hold only a hash. Tests: `LinkBggAccountTest`, `BggRoutesTest`.
