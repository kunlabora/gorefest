package gorefest.shell.bgg

import gorefest.core.game.Game
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/** Searches games we know first, and asks BGG only when that comes up short. */
class GameSearch(private val catalog: Catalog, private val bgg: BggGateway, private val clock: Clock) {
    data class Result(val games: List<Game>, val bggUnavailable: Boolean = false)

    suspend fun search(query: String): Result {
        val q = query.trim()
        if (q.length < MIN_LENGTH) return Result(emptyList())
        val local = catalog.search(q)
        if (local.size >= ENOUGH) return Result(local.take(LIMIT))
        val key = q.lowercase()
        val known = misses[key]
        if (known != null && known.until.isAfter(clock.instant())) return Result(local.take(LIMIT), bggUnavailable = known.failed)
        return try {
            val hits = bgg.call(BggPriority.LIVE) { search(q) }
            val missing = catalog.missing(hits.map { it.id }.take(BggApi.THINGS_PER_CALL))
            if (missing.isNotEmpty()) catalog.put(bgg.call(BggPriority.LIVE) { things(missing) }, clock.instant())
            if (hits.isEmpty()) remember(key, failed = false, NO_HITS_TTL)
            val fromBgg = hits.mapNotNull { catalog[it.id] }
            Result((local + fromBgg).distinctBy { it.id }.sortedBy { it.popularityRank ?: Int.MAX_VALUE }.take(LIMIT))
        } catch (_: BggUnavailable) {
            remember(key, failed = true, FAILED_TTL)
            Result(local.take(LIMIT), bggUnavailable = true)
        }
    }

    private class Miss(val until: Instant, val failed: Boolean)

    /** Searches BGG had nothing for, or failed on, so repeating them doesn't cost BGG calls for a while. */
    private val misses = ConcurrentHashMap<String, Miss>()

    private fun remember(key: String, failed: Boolean, ttl: Duration) {
        val now = clock.instant()
        if (misses.size >= MAX_MISSES) misses.values.removeIf { !it.until.isAfter(now) }
        if (misses.size < MAX_MISSES) misses[key] = Miss(now + ttl, failed)
    }

    private companion object {
        val NO_HITS_TTL: Duration = Duration.ofMinutes(10)
        val FAILED_TTL: Duration = Duration.ofMinutes(1)
        const val MAX_MISSES = 1000
        const val MIN_LENGTH = 2
        const val ENOUGH = 5
        const val LIMIT = 20
    }
}
