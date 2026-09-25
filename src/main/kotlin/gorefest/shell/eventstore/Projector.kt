package gorefest.shell.eventstore

import gorefest.core.eventsourcing.Projection
import gorefest.shell.db.Database
import gorefest.shell.eventstore.EventStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Keeps a [Projection] in memory. Replays the whole log on the first [catchUp], then only new events. */
class Projector<S>(
    private val projection: Projection<S>,
    private val db: Database,
    private val store: EventStore,
) {
    @Volatile
    var state: S = projection.initialState
        private set

    private var position = 0L
    private val mutex = Mutex()

    suspend fun catchUp() = mutex.withLock {
        val events = db.transaction { store.readAll(after = position) }
        state = events.fold(state) { s, e -> projection.evolve(s, e.event) }
        position = events.lastOrNull()?.seq ?: position
    }
}
