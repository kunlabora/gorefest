# T1 Locks the compiler makes you take

Source: Tim, 2026-11-20, after a LinkedIn post by Bas de Groot (#kotlin #postgresql,
https://lnkd.in/p/ePMEFH_a). Status: 📋 todo. Technical story: players notice nothing.

**Idea (from the post):** a read-check-write that must run under a database lock breaks only under concurrency, so a
forgotten lock passes every test. Make the lock a type: `withLock(…) { }` takes a transaction-scoped Postgres lock and
puts a lock value (`FooLock`) in context; the repository methods that need it declare `context(lock: FooLock)`, so
calling them without the lock doesn't compile. The lock ends with the transaction, so there's no unlock to forget, and
`withLock` itself needs `context(tx: Tx)`, so it can't be taken outside a transaction.

## Are we doing this already?

Half of it.

- **Yes:** everything that touches the database takes `context(tx: Tx)` (`Database.transaction` provides it), so
  "inside a transaction" is already proven by the compiler. Our locks are transaction-scoped
  (`pg_advisory_xact_lock`, `select … for update`), so they already release on commit or rollback.
- **The event store is safe by design:** `EventStore.append` takes the advisory lock itself and checks the DCB
  condition under it. No caller can forget it, and deciders never touch SQL.
- **No:** the plain-table locks are only a convention. Nothing makes a caller take them before the methods they guard:
  - `LoginAttempts.lockEmail(email)` (`Repositories.kt`) must come before `countSince` + `insert` in `Login.request`,
    or parallel requests all pass the per-email rate limit. Only a comment says so.
  - `EmailChange`'s `rows.lock(id)` (`select … for update`) must come before `save` when checking a code, or two
    parallel wrong codes can each count from the same `wrong_codes`.
  - Same shape for the login code check on `login_attempts` (`for update` before saving `wrong_codes`/`used_at`).

## Behaviour

None visible. After this story:

1. A method that is only correct under a lock declares that lock as a context parameter
   (`context(tx: Tx, lock: EmailLock) fun countSince(…)`), and the lock types have private constructors, so the only way
   to get one is the function that takes the lock.
2. That function needs `context(tx: Tx)` and runs the block with the lock in context:
   `context(tx: Tx) fun <T> withEmailLock(email: String, block: context(EmailLock) () -> T): T`. For a row lock, the
   locked row comes back with the lock (`lockedChange(id)` → `Locked<StoredChange>`), and `save` needs it.
3. The three places above use it; their tests still prove the concurrency (the existing rate-limit and wrong-code
   tests, plus one parallel test each where missing).
4. `ArchitectureTest`: `pg_advisory_xact_lock` and `for update` only appear inside a lock function, so a new lock goes
   through the same shape.
5. The convention gets a line in CLAUDE.md (next to `context(tx: Tx)`).

## Open questions

- One generic `Lock<K>` keyed by a type tag, or one small type per lock (`EmailLock`, `EmailChangeRow`)? Small types
  read better in signatures; a generic one is less code.
- Worth it for three call sites? The point is the next one: rate limits and code checks are where a missing lock is a
  security bug, not a glitch.
- The event store's lock stays as it is (taken inside `append`); nothing to gain from exposing it.
