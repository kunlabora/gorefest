package gorefest.core.eventsourcing

import gorefest.core.Event

/** A pure read model: a left fold over the whole event log. The shell keeps it in memory. */
interface Projection<S> {
    val initialState: S
    fun evolve(state: S, event: Event): S
}
