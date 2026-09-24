package gorefest.core.eventsourcing

import gorefest.core.Event

/**
 * A pure decision function with its own consistency boundary.
 *
 * The shell reads the events matching [query], folds them with [evolve] starting from [initialState],
 * calls [decide], and appends the result under an [AppendCondition] built from the same query.
 * Everything a decision needs (ids, "now", the acting player) must be in the command.
 */
interface Decider<in C, S, out E : Event> {
    val initialState: S
    fun query(command: C): Query
    fun evolve(state: S, event: Event): S
    fun decide(command: C, state: S): Decision<E>
}

sealed interface Decision<out E : Event> {
    data class Accepted<out E : Event>(val events: List<E>) : Decision<E> {
        constructor(vararg events: E) : this(events.toList())
    }

    data class Rejected(val reason: String) : Decision<Nothing>
}
