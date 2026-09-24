package gorefest.core.bgg

import gorefest.core.BggId
import kotlinx.serialization.Serializable

/**
 * What we know about a game from BoardGameGeek. Cached once per game, not per player.
 * [bestPlayers] and [recommendedPlayers] come from BGG's "suggested number of players" poll.
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
    /** BGG's 1–5 complexity rating; 0 when nobody voted. */
    val weight: Double = 0.0,
    val mechanics: Set<String> = emptySet(),
    val categories: Set<String> = emptySet(),
    /** Overall board game rank, null when unranked. */
    val rank: Int? = null,
) {
    /** Player counts the game plays well at: the poll's recommendations, or the box range if nobody voted. */
    val goodPlayerCounts: Set<Int>
        get() = (bestPlayers + recommendedPlayers).ifEmpty { (minPlayers..maxPlayers).toSet() }

    val weightLabel: String
        get() = when {
            weight == 0.0 -> ""
            weight < 1.8 -> "light"
            weight < 2.6 -> "medium-light"
            weight < 3.4 -> "medium"
            weight < 4.0 -> "medium-heavy"
            else -> "heavy"
        }

    /** "60–90 min · medium · 1–4" */
    val facts: String
        get() = listOf(
            if (minPlaytime == maxPlaytime) "$minPlaytime min" else "$minPlaytime–$maxPlaytime min",
            weightLabel,
            if (minPlayers == maxPlayers) "$minPlayers" else "$minPlayers–$maxPlayers",
        ).filter { it.isNotBlank() }.joinToString(" · ")
}
