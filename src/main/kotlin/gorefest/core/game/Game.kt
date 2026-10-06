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
     * list a single session or the whole campaign at the extremes. Needs both a max over [WILD_RANGE_RATIO] times
     * the min and a max over [WILD_RANGE_MIN_MAX] minutes, so honest wide ranges (20–120, 30–180) stay ranges.
     */
    val hasWildPlaytimeRange: Boolean
        get() = minPlaytime > 0 && maxPlaytime > WILD_RANGE_MIN_MAX && maxPlaytime > WILD_RANGE_RATIO * minPlaytime

    /**
     * A single time for a game with a wild range. BGG's `playingtime` when it lies inside the range; it usually just
     * repeats the max (the very number that's wrong), and some games have none, so then the geometric mean of min and max.
     */
    val typicalMinutes: Int
        get() = if (playingTime in 1 until maxPlaytime) playingTime else kotlin.math.sqrt(minPlaytime.toDouble() * maxPlaytime).roundToInt()

    companion object {
        /** A max playtime beyond this many times the min is not a range anyone plays to ... */
        const val WILD_RANGE_RATIO = 8

        /** ... when it is also beyond this many minutes. */
        const val WILD_RANGE_MIN_MAX = 240
    }
}
