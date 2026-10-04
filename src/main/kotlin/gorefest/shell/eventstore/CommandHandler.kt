package gorefest.shell.eventstore

import gorefest.core.Event
import gorefest.core.eventsourcing.AppendCondition
import gorefest.core.eventsourcing.Decider
import gorefest.core.eventsourcing.Decision
import gorefest.shell.db.Database
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory
import kotlin.random.Random

/**
 * The imperative shell around a [Decider]: read → fold → decide → conditional append.
 * On a concurrent conflicting append it simply re-runs the decision on fresh state.
 */
class CommandHandler(
    private val db: Database,
    private val store: EventStore,
    private val afterAppend: suspend () -> Unit,
    private val backoff: suspend (attempt: Int) -> Unit = ::jitteredBackoff,
) {
    private val log = LoggerFactory.getLogger(CommandHandler::class.java)

    suspend fun <C, S, E : Event> handle(decider: Decider<C, S, E>, command: C): Decision<E> {
        val query = decider.query(command)
        repeat(MAX_ATTEMPTS) { attempt ->
            val (events, position) = db.transaction { store.read(query) }
            val state = events.fold(decider.initialState) { s, e -> decider.evolve(s, e.event) }
            val decision = decider.decide(command, state)
            if (decision !is Decision.Accepted || decision.events.isEmpty()) return decision
            try {
                db.transaction { store.append(decision.events, AppendCondition(query, position)) }
            } catch (_: AppendConditionFailed) {
                backoff(attempt)
                return@repeat
            }
            // The events are committed: a failing catch-up must not make the command look failed (a retry would
            // duplicate it). The next catch-up heals the read models.
            try {
                afterAppend()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.error("Catch-up after append failed; read models are stale until the next catch-up", e)
            }
            return decision
        }
        error("Gave up after $MAX_ATTEMPTS conflicting attempts")
    }

    private companion object {
        const val MAX_ATTEMPTS = 10
    }
}

/** Waits 5 ms growing to ~80 ms plus random jitter, so contending commands spread out. */
private suspend fun jitteredBackoff(attempt: Int) {
    val base = 5L shl attempt.coerceAtMost(4)
    delay(base + Random.nextLong(base))
}
