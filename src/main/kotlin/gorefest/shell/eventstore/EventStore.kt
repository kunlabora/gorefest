package gorefest.shell.eventstore

import gorefest.core.Event
import gorefest.core.type
import gorefest.core.eventsourcing.AppendCondition
import gorefest.core.eventsourcing.Query
import gorefest.core.eventsourcing.Tag
import gorefest.shell.db.Tx
import kotlinx.serialization.json.Json
import java.sql.PreparedStatement
import java.sql.ResultSet

data class StoredEvent(val seq: Long, val event: Event)

data class ReadResult(val events: List<StoredEvent>, val position: Long)

class AppendConditionFailed(val condition: AppendCondition) :
    RuntimeException("Events matching the decision's query were appended after position ${condition.after}")

/**
 * The single global event log (Dynamic Consistency Boundary style: no streams, no aggregates).
 *
 * Every append takes the same advisory lock, so appends are serialized and `seq` values become
 * visible in order. That makes "no matching event after my read position" a sound check, and lets
 * projections catch up with `seq > lastSeen` without missing late commits.
 */
class EventStore {

    context(tx: Tx)
    fun read(query: Query): ReadResult {
        val (where, bind) = query.toSql()
        val events = tx.connection.prepareStatement("select seq, data from events where $where order by seq").use {
            bind(it, 1)
            it.executeQuery().use { rs -> rs.toStoredEvents() }
        }
        return ReadResult(events, events.lastOrNull()?.seq ?: 0)
    }

    context(tx: Tx)
    fun readAll(after: Long): List<StoredEvent> =
        tx.connection.prepareStatement("select seq, data from events where seq > ? order by seq").use {
            it.setLong(1, after)
            it.executeQuery().use { rs -> rs.toStoredEvents() }
        }

    context(tx: Tx)
    fun append(events: List<Event>, condition: AppendCondition? = null) {
        if (events.isEmpty()) return
        tx.connection.prepareStatement("select pg_advisory_xact_lock($APPEND_LOCK)").use { it.execute() }

        if (condition != null) {
            val (where, bind) = condition.query.toSql()
            val conflict = tx.connection.prepareStatement("select exists(select 1 from events where seq > ? and ($where))").use {
                it.setLong(1, condition.after)
                bind(it, 2)
                it.executeQuery().use { rs -> rs.next() && rs.getBoolean(1) }
            }
            if (conflict) throw AppendConditionFailed(condition)
        }

        tx.connection.prepareStatement("insert into events (type, tags, data) values (?, ?, ?::jsonb)").use { stmt ->
            for (event in events) {
                stmt.setString(1, event.type)
                stmt.setArray(2, tx.connection.createArrayOf("text", event.tags.map(Tag::value).toTypedArray()))
                stmt.setString(3, json.encodeToString(Event.serializer(), event))
                stmt.addBatch()
            }
            stmt.executeBatch()
        }
    }

    context(tx: Tx)
    private fun Query.toSql(): Pair<String, (PreparedStatement, Int) -> Unit> {
        if (items.isEmpty()) return "false" to { _, _ -> }
        val sql = items.joinToString(" or ") { "(type = any(?) and tags @> ?)" }
        return sql to { stmt, start ->
            items.forEachIndexed { i, item ->
                stmt.setArray(start + 2 * i, tx.connection.createArrayOf("text", item.types.toTypedArray()))
                stmt.setArray(start + 2 * i + 1, tx.connection.createArrayOf("text", item.tags.map(Tag::value).toTypedArray()))
            }
        }
    }

    private fun ResultSet.toStoredEvents() = buildList {
        while (next()) add(StoredEvent(getLong("seq"), json.decodeFromString(Event.serializer(), getString("data"))))
    }

    companion object {
        private const val APPEND_LOCK = 7_000_001L
        val json = Json { ignoreUnknownKeys = true }
    }
}
