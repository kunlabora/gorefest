package gorefest.shell.bgg

import gorefest.core.BggId
import gorefest.core.game.Game

/** A BGG user as the XML API shows them. [profileText] is every profile field it returns, joined. */
data class BggUser(val id: Int, val name: String, val profileText: String)

data class SearchHit(val id: BggId, val name: String, val year: Int?)

sealed interface CollectionResult {
    data class Ready(val games: Set<BggId>) : CollectionResult

    /** BGG answers 202 while it builds a collection export; ask again later. */
    data object Queued : CollectionResult

    data object UnknownUser : CollectionResult
}

/** BGG said "slow down" (429) or is unavailable (5xx). */
class BggUnavailable(message: String) : RuntimeException(message)

/** The BGG XML API2 calls we use. Implementations don't rate-limit; [BggGateway] does. */
interface BggApi {
    suspend fun user(name: String): BggUser?
    suspend fun collection(username: String): CollectionResult

    /** At most [THINGS_PER_CALL] ids per call. */
    suspend fun things(ids: Collection<BggId>): List<Game>
    suspend fun search(query: String): List<SearchHit>

    companion object {
        const val THINGS_PER_CALL = 20
    }
}
