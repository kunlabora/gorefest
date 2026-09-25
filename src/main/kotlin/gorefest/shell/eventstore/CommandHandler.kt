package gorefest.shell.eventstore

import gorefest.core.Event
import gorefest.core.eventsourcing.AppendCondition
import gorefest.core.eventsourcing.Decider
import gorefest.core.eventsourcing.Decision
import gorefest.shell.db.Database
import gorefest.shell.eventstore.AppendConditionFailed
import gorefest.shell.eventstore.EventStore

/**
 * The imperative shell around a [Decider]: read → fold → decide → conditional append.
 * On a concurrent conflicting append it simply re-runs the decision on fresh state.
 */
class CommandHandler(
    private val db: Database,
    private val store: EventStore,
    private val afterAppend: suspend () -> Unit,
) {
    suspend fun <C, S, E : Event> handle(decider: Decider<C, S, E>, command: C): Decision<E> {
        val query = decider.query(command)
        repeat(MAX_ATTEMPTS) {
            val (events, position) = db.transaction { store.read(query) }
            val state = events.fold(decider.initialState) { s, e -> decider.evolve(s, e.event) }
            val decision = decider.decide(command, state)
            if (decision !is Decision.Accepted || decision.events.isEmpty()) return decision
            try {
                db.transaction { store.append(decision.events, AppendCondition(query, position)) }
            } catch (_: AppendConditionFailed) {
                return@repeat
            }
            afterAppend()
            return decision
        }
        error("Gave up after $MAX_ATTEMPTS conflicting attempts")
    }

    private companion object {
        const val MAX_ATTEMPTS = 3
    }
}
