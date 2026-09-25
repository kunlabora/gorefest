package gorefest.shell.bgg

import gorefest.core.BggId
import gorefest.core.game.Game

/**
 * Production before a BGG application token exists: every call fails as "unavailable", which the
 * pages already handle (linking waits, collection sync retries, search uses only cached games).
 */
object UnavailableBggApi : BggApi {
    private fun off(): Nothing = throw BggUnavailable("BGG isn't configured: set BGG_TOKEN")
    override suspend fun user(name: String): BggUser? = off()
    override suspend fun collection(username: String): CollectionResult = off()
    override suspend fun things(ids: Collection<BggId>): List<Game> = off()
    override suspend fun search(query: String): List<SearchHit> = off()
}
