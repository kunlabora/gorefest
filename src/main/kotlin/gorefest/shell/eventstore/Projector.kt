package gorefest.shell.eventstore

import gorefest.core.eventsourcing.Projection
import gorefest.shell.db.Database
import gorefest.shell.eventstore.EventStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory

/** Keeps a [Projection] in memory. Replays the whole log on the first [catchUp], then only new events. */
class Projector<S>(
    private val projection: Projection<S>,
    private val db: Database,
    private val store: EventStore,
) {
    @Volatile
    var state: S = projection.initialState
        private set

    private val log = LoggerFactory.getLogger(Projector::class.java)
    private var position = 0L
    private val mutex = Mutex()

    suspend fun catchUp() = mutex.withLock {
        val events = db.transaction { store.readAll(after = position) }
        // A poison event (evolve throws) is logged loudly and skipped, so it can't wedge every later catch-up.
        // Replay is deterministic, so a restart skips it the same way; the read model just lacks that one event.
        var next = state
        for (e in events) {
            next = try {
                projection.evolve(next, e.event)
            } catch (ex: Exception) {
                log.error("Projection failed on event seq={} ({}); skipping it", e.seq, e.event::class.simpleName, ex)
                next
            }
            position = e.seq
        }
        state = next
    }
}
