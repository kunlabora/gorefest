# Event log

Prefix: `EV`

Everything that happens is kept in one log; these are the promises that keep it trustworthy.

## Appends and conflicts

- **EV-1** Events are stored and read back exactly as they happened, timestamps and choices included.
- **EV-2** A decision sees exactly the events in its boundary, in the order they happened.
- **EV-3** A decision is not stored if an event in its boundary was added after it looked, so nobody acts on stale facts.
- **EV-4** Events outside a decision's boundary never get in its way.
- **EV-5** A command that meets a conflict is decided again on the new facts, backing off between tries, until it lands.

## Concurrency

- **EV-6** Commands at the same moment land as if one after the other: different players' joins all land, the same join lands once, and limits such as the bring request caps hold.

## Read models

- **EV-7** Once a command is stored it counts as done, even if refreshing the read models afterwards fails.
- **EV-8** An event a read model can't apply is skipped, and the events after it still apply.

## Evolution

- **EV-9** Every event, as it was first stored, still decodes into the current events, so the log replays after any upgrade.
- **EV-10** Every event type has a stored sample that is checked on every build.
