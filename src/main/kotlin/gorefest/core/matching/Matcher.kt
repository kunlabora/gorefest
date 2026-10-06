package gorefest.core.matching

import gorefest.core.PlayerId
import gorefest.core.game.Game
import gorefest.core.gameday.ShelfGame
import gorefest.core.taste.PlayerGames
import gorefest.core.taste.Predictor
import java.time.Instant
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class TablePlayer(val id: PlayerId, val name: String, val games: PlayerGames)

data class Suggestion(val game: Game, val minutes: Int, val reason: SuggestionReason, val score: Double)

/** Why a game was suggested. The web layer words it. */
sealed interface SuggestionReason {
    /** An owner at the table brought it and really wants to play it. */
    data class EagerOwner(val name: String) : SuggestionReason

    /** Nobody has played it and everyone's predicted to enjoy it. */
    data object NewAndLiked : SuggestionReason

    /** Everyone rated it highly and they agree. */
    data class SafeBet(val tableSize: Int) : SuggestionReason

    /** The BGG poll says it's best at exactly this table size. */
    data class BestAtTableSize(val tableSize: Int) : SuggestionReason

    /** Nobody at the table has played it. */
    data object NobodyPlayedIt : SuggestionReason

    /** It finishes well inside the time budget. */
    data class FitsWithRoomToSpare(val budget: Int) : SuggestionReason

    /** All we know is that it plays well at this table size. */
    data class PlaysWellWith(val tableSize: Int) : SuggestionReason

    data object GoodMiddleGround : SuggestionReason

    /** These players at the table said they haven't played it; nothing is claimed about their enjoyment. */
    data class NewToSome(val names: List<String>) : SuggestionReason
}

sealed interface Suggestions {
    /** [unknown]: table players with too few answers for their taste to count much; the picks lean on fit for them. */
    data class Found(val top: List<Suggestion>, val unknown: List<PlayerId> = emptyList()) : Suggestions

    /** Nothing passes the filters. */
    sealed interface NothingFits : Suggestions {
        /** Nobody is ticked at the table. */
        data object NoPlayers : NothingFits

        /** No shelf game plays well with [tableSize]. */
        data class NoGameForTableSize(val tableSize: Int) : NothingFits

        /** Games play well with [tableSize] but none fit [budget]. [relaxTo] is a budget at which [fitsThen] games would fit, if any. */
        data class NoGameInTime(val tableSize: Int, val budget: Int, val relaxTo: Int?, val fitsThen: Int) : NothingFits
    }
}

/**
 * `suggest(players, shelf, timeBudget) → top 3 with reasons`.
 *
 * - Hard filters: the table size is a good player count for the game (BGG poll, else the box), and
 *   the estimated duration fits the budget.
 * - Score: the table's mean predicted enjoyment minus λ·spread, so one miserable player costs a lot;
 *   players with few answers weigh less. Bonuses for novelty (nobody played it) and an eager owner.
 * - A game a player declared unplayed (and never compared) counts as neutral for them, not as a guess
 *   from its features; it can only win on novelty, and the reason says it's new to them.
 */
class Matcher(
    private val predictor: Predictor = Predictor(),
    private val spreadPenalty: Double = 0.5,
    private val noveltyBonus: Double = 0.3,
    private val wishBonus: Double = 0.3,
) {
    fun suggest(
        table: List<TablePlayer>,
        shelf: List<Pair<Game, ShelfGame>>,
        timeBudget: Int?,
        now: Instant,
        speed: Double = 1.0,
        count: Int = 3,
    ): Suggestions {
        val n = table.size
        if (n == 0) return Suggestions.NothingFits.NoPlayers
        val playable = shelf.filter { (game, _) -> n in game.goodPlayerCounts }
        val fitting = playable.filter { (game, _) -> timeBudget == null || estimatedMinutes(game, n, speed) <= timeBudget }
        if (fitting.isEmpty()) return nothingFits(n, timeBudget, playable, speed)

        val predictions = table.associate { p -> p.id to predictor.enjoyment(p.games, fitting.map { it.first }, now) }
        val names = table.associate { it.id to it.name }
        val unknown = table.filter { it.games.history.comparisons.size < KNOWN_AFTER }.map { it.id }
        val nobodyKnown = unknown.size == n
        return Suggestions.Found(
            fitting.map { (game, entry) ->
                // A game someone declared unplayed is a stranger to them: no opinion, not a guess from its features.
                val strangers = table.filter { it.games.isUnknownToThem(game.id) }
                val means = table.map { p -> if (p in strangers) 0.0 else predictions.getValue(p.id).getValue(game.id).mean }
                val weights = table.map { p -> (p.games.history.comparisons.size.toDouble() / KNOWN_AFTER).coerceIn(0.2, 1.0) }
                val mean = means.zip(weights).sumOf { (m, w) -> m * w } / weights.sum()
                val spread = sqrt(means.zip(weights).sumOf { (m, w) -> w * (m - mean) * (m - mean) } / weights.sum())
                val novel = table.none { it.games.hasPlayed(game.id) }
                val eager = entry.eager.firstOrNull { it in names }
                val score = mean - spreadPenalty * spread + (if (novel) noveltyBonus else 0.0) + (if (eager != null) wishBonus else 0.0)
                val minutes = estimatedMinutes(game, n, speed)
                val reason = reason(game, table, means, spread, novel, eager?.let(names::get), minutes, timeBudget, nobodyKnown, strangers.map { it.name })
                Suggestion(game, minutes, reason, score)
            }.sortedByDescending { it.score }.take(count),
            unknown,
        )
    }

    private fun nothingFits(n: Int, budget: Int?, playable: List<Pair<Game, ShelfGame>>, speed: Double): Suggestions.NothingFits {
        if (playable.isEmpty()) return Suggestions.NothingFits.NoGameForTableSize(n)
        // Only reached with a budget: without one, every playable game fits.
        val limit = checkNotNull(budget)
        val relax = BUDGETS.filter { it > limit }.firstOrNull { b -> playable.any { estimatedMinutes(it.first, n, speed) <= b } }
        val fits = relax?.let { b -> playable.count { estimatedMinutes(it.first, n, speed) <= b } } ?: 0
        return Suggestions.NothingFits.NoGameInTime(n, limit, relax, fits)
    }

    private fun reason(
        game: Game, table: List<TablePlayer>, means: List<Double>, spread: Double, novel: Boolean,
        eagerOwner: String?, minutes: Int, budget: Int?, nobodyKnown: Boolean, strangers: List<String>,
    ): SuggestionReason {
        val n = table.size
        val everyoneRated = table.all { game.id in it.games.history.comparedGames }
        val bestAtN = n in game.bestPlayers && game.bestPlayers.size <= 2
        val roomFor = budget?.takeIf { minutes <= it - 20 }
        return when {
            eagerOwner != null -> SuggestionReason.EagerOwner(eagerOwner)
            // Never claim enjoyment for someone who said they haven't played it.
            strangers.isNotEmpty() -> SuggestionReason.NewToSome(strangers)
            // Without anyone's taste there is nothing to say about enjoyment: only about fit.
            nobodyKnown -> when {
                bestAtN -> SuggestionReason.BestAtTableSize(n)
                novel -> SuggestionReason.NobodyPlayedIt
                roomFor != null -> SuggestionReason.FitsWithRoomToSpare(roomFor)
                else -> SuggestionReason.PlaysWellWith(n)
            }
            novel && means.all { it >= 0 } -> SuggestionReason.NewAndLiked
            everyoneRated && means.all { it > 0.3 } && spread < 0.5 -> SuggestionReason.SafeBet(n)
            bestAtN -> SuggestionReason.BestAtTableSize(n)
            novel -> SuggestionReason.NobodyPlayedIt
            roomFor != null -> SuggestionReason.FitsWithRoomToSpare(roomFor)
            else -> SuggestionReason.GoodMiddleGround
        }
    }

    companion object {
        /** Answers after which a player's taste counts for something; below it we say the picks are a fit-only guess for them. */
        const val KNOWN_AFTER = 10

        /** Time budgets offered when nothing fits, in minutes. */
        val BUDGETS = listOf(45, 90, 120, 180, 240)

        /** What we assume for a game BGG has no playtime for (min = max = 0), in minutes. */
        const val UNKNOWN_MINUTES = 60

        /** Whether BGG gave a playtime for [game]; without one [estimatedMinutes] falls back to [UNKNOWN_MINUTES]. */
        fun hasPlaytime(game: Game) = game.minPlaytime > 0 || game.maxPlaytime > 0

        /**
         * BGG's playtime, interpolated from min to max over the player range, times the group's speed.
         * Rounded to 5 min. A game without a playtime counts as [UNKNOWN_MINUTES], not as quick; a game
         * with a wild range (campaigns) counts as its typical playing time, whatever the player count.
         */
        fun estimatedMinutes(game: Game, players: Int, speed: Double = 1.0): Int {
            val span = game.maxPlayers - game.minPlayers
            val t = if (span <= 0) 0.5 else ((players - game.minPlayers).toDouble() / span).coerceIn(0.0, 1.0)
            val base = if (!hasPlaytime(game)) UNKNOWN_MINUTES.toDouble()
            else if (game.hasWildPlaytimeRange) game.typicalMinutes.toDouble()
            else game.minPlaytime + (game.maxPlaytime - game.minPlaytime) * t
            return ((base * speed / 5).roundToInt() * 5).coerceAtLeast(5)
        }
    }
}
