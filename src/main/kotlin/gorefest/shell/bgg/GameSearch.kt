package gorefest.shell.bgg

import gorefest.core.game.Game
import java.time.Clock

/** Searches games we know first, and asks BGG only when that comes up short. */
class GameSearch(private val catalog: Catalog, private val bgg: BggGateway, private val clock: Clock) {
    data class Result(val games: List<Game>, val bggUnavailable: Boolean = false)

    suspend fun search(query: String): Result {
        val q = query.trim()
        if (q.length < MIN_LENGTH) return Result(emptyList())
        val local = catalog.search(q)
        if (local.size >= ENOUGH) return Result(local.take(LIMIT))
        return try {
            val hits = bgg.call(BggPriority.LIVE) { search(q) }
            val missing = catalog.missing(hits.map { it.id }.take(BggApi.THINGS_PER_CALL))
            if (missing.isNotEmpty()) catalog.put(bgg.call(BggPriority.LIVE) { things(missing) }, clock.instant())
            val fromBgg = hits.mapNotNull { catalog[it.id] }
            Result((local + fromBgg).distinctBy { it.id }.sortedBy { it.popularityRank ?: Int.MAX_VALUE }.take(LIMIT))
        } catch (_: BggUnavailable) {
            Result(local.take(LIMIT), bggUnavailable = true)
        }
    }

    private companion object {
        const val MIN_LENGTH = 2
        const val ENOUGH = 5
        const val LIMIT = 20
    }
}
