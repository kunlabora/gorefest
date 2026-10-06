package gorefest.core.game

import gorefest.core.BggId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/**
 * A board game as the taste model and matching see it. The data comes from BoardGameGeek (the
 * shell's BGG adapter translates it), and BGG's id is the game's identity throughout.
 * [bestPlayers] and [recommendedPlayers] come from the community poll on player counts.
 */
@Serializable
data class Game(
    val id: BggId,
    val name: String,
    val year: Int? = null,
    val thumbnail: String? = null,
    val image: String? = null,
    val minPlayers: Int,
    val maxPlayers: Int,
    val bestPlayers: Set<Int> = emptySet(),
    val recommendedPlayers: Set<Int> = emptySet(),
    val minPlaytime: Int,
    val maxPlaytime: Int,
    /** BGG's typical playing time in minutes; 0 when BGG has none. */
    val playingTime: Int = 0,
    /** BGG's 1–5 complexity rating; 0 when nobody voted. */
    val weight: Double = 0.0,
    val mechanics: Set<String> = emptySet(),
    val categories: Set<String> = emptySet(),
    /** How popular the game is (1 = most), for anchors everyone knows; null when unranked. Stored as "rank" in the cache. */
    @SerialName("rank") val popularityRank: Int? = null,
) {
    /** Player counts the game plays well at: the poll's recommendations, or the box range if nobody voted. */
    val goodPlayerCounts: Set<Int>
        get() = (bestPlayers + recommendedPlayers).ifEmpty { (minPlayers..maxPlayers).toSet() }

    /**
     * Whether BGG's min-max playtime is too wide to mean anything: campaign games (The 7th Continent: 5–1000 min)
     * list a single session or the whole campaign at the extremes. Past [WILD_RANGE_RATIO] times the minimum.
     */
    val hasWildPlaytimeRange: Boolean
        get() = minPlaytime > 0 && maxPlaytime > WILD_RANGE_RATIO * minPlaytime

    /**
     * BGG's typical playing time; without one (a game cached before we kept it) the geometric mean of min and max.
     * Meant for wild ranges, where min and max themselves are no use.
     */
    val typicalMinutes: Int
        get() = if (playingTime > 0) playingTime else kotlin.math.sqrt(minPlaytime.toDouble() * maxPlaytime).roundToInt()

    companion object {
        /** A max playtime beyond this many times the min is not a range anyone plays to. */
        const val WILD_RANGE_RATIO = 5
    }
}
