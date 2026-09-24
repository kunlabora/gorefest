package gorefest.shell.bgg

import gorefest.core.BggId
import gorefest.core.PlayerId
import gorefest.core.bgg.Game
import gorefest.shell.db.Database
import gorefest.shell.db.Tx
import kotlinx.serialization.json.Json
import java.sql.Timestamp
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.uuid.toJavaUuid

/**
 * Game metadata from BGG, fetched once per game for everyone. Kept in memory (it's small) and
 * in the `games` table so a restart doesn't refetch.
 */
class Catalog(private val db: Database) {
    private val games = ConcurrentHashMap<BggId, Game>()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun load() = db.transaction {
        games.clear()
        contextOf<Tx>().connection.prepareStatement("select data from games").use { st ->
            st.executeQuery().use { rs -> while (rs.next()) json.decodeFromString(Game.serializer(), rs.getString(1)).let { games[it.id] = it } }
        }
    }

    operator fun get(id: BggId): Game? = games[id]

    fun all(): Collection<Game> = games.values

    /** Cached games whose name contains [query], best ranked first. */
    fun search(query: String): List<Game> =
        games.values.filter { it.name.contains(query, ignoreCase = true) }.sortedBy { it.rank ?: Int.MAX_VALUE }

    fun missing(ids: Collection<BggId>): List<BggId> = ids.filterNot(games::containsKey)

    suspend fun put(fetched: Collection<Game>, at: Instant) {
        if (fetched.isEmpty()) return
        db.transaction {
            contextOf<Tx>().connection.prepareStatement(
                "insert into games (bgg_id, data, fetched_at) values (?, ?::jsonb, ?) on conflict (bgg_id) do update set data = excluded.data, fetched_at = excluded.fetched_at",
            ).use { st ->
                fetched.forEach { game ->
                    st.setInt(1, game.id.value)
                    st.setString(2, json.encodeToString(Game.serializer(), game))
                    st.setTimestamp(3, Timestamp.from(at))
                    st.addBatch()
                }
                st.executeBatch()
            }
        }
        fetched.forEach { games[it.id] = it }
    }
}

enum class SyncStatus { QUEUED, DONE, FAILED }

data class CollectionSyncState(val username: String, val status: SyncStatus, val message: String?, val syncedAt: Instant?)

/** Owned games per player, as last synced from BGG. */
class Collections {
    context(tx: Tx)
    fun replace(player: PlayerId, games: Set<BggId>) {
        tx.connection.prepareStatement("delete from collections where player_id = ?").use {
            it.setObject(1, player.value.toJavaUuid())
            it.executeUpdate()
        }
        tx.connection.prepareStatement("insert into collections (player_id, bgg_id) values (?, ?)").use { st ->
            games.forEach {
                st.setObject(1, player.value.toJavaUuid())
                st.setInt(2, it.value)
                st.addBatch()
            }
            st.executeBatch()
        }
    }

    context(tx: Tx)
    fun of(player: PlayerId): Set<BggId> =
        tx.connection.prepareStatement("select bgg_id from collections where player_id = ?").use {
            it.setObject(1, player.value.toJavaUuid())
            it.executeQuery().use { rs -> buildSet { while (rs.next()) add(BggId(rs.getInt(1))) } }
        }

    context(tx: Tx)
    fun setStatus(player: PlayerId, username: String, status: SyncStatus, message: String?, now: Instant) {
        tx.connection.prepareStatement(
            """
            insert into collection_syncs (player_id, username, status, message, updated_at, synced_at) values (?, ?, ?, ?, ?, ?)
            on conflict (player_id) do update set username = excluded.username, status = excluded.status, message = excluded.message,
              updated_at = excluded.updated_at, synced_at = coalesce(excluded.synced_at, collection_syncs.synced_at)
            """.trimIndent(),
        ).use {
            it.setObject(1, player.value.toJavaUuid())
            it.setString(2, username)
            it.setString(3, status.name)
            it.setString(4, message)
            it.setTimestamp(5, Timestamp.from(now))
            it.setTimestamp(6, if (status == SyncStatus.DONE) Timestamp.from(now) else null)
            it.executeUpdate()
        }
    }

    context(tx: Tx)
    fun status(player: PlayerId): CollectionSyncState? =
        tx.connection.prepareStatement("select username, status, message, synced_at from collection_syncs where player_id = ?").use {
            it.setObject(1, player.value.toJavaUuid())
            it.executeQuery().use { rs ->
                if (!rs.next()) null
                else CollectionSyncState(rs.getString(1), SyncStatus.valueOf(rs.getString(2)), rs.getString(3), rs.getTimestamp(4)?.toInstant())
            }
        }

    /** Players whose last sync is unfinished, or older than [before]. */
    context(tx: Tx)
    fun due(before: Instant): List<Pair<PlayerId, String>> =
        tx.connection.prepareStatement("select player_id, username from collection_syncs where status = 'QUEUED' or synced_at is null or synced_at < ?").use {
            it.setTimestamp(1, Timestamp.from(before))
            it.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) add(PlayerId(kotlin.uuid.Uuid.parse(rs.getString(1))) to rs.getString(2))
                }
            }
        }
}
