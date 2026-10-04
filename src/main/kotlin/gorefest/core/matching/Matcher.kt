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

data class Suggestion(val game: Game, val minutes: Int, val reason: String, val score: Double)

sealed interface Suggestions {
    /** [unknown]: table players with too few answers for their taste to count much; the picks lean on fit for them. */
    data class Found(val top: List<Suggestion>, val unknown: List<PlayerId> = emptyList()) : Suggestions

    /** Nothing passes the filters. [relaxTo] is a time budget at which [fitsThen] games would fit, if any. */
    data class NothingFits(val title: String, val text: String, val relaxTo: Int? = null, val fitsThen: Int = 0) : Suggestions
}

/**
 * `suggest(players, shelf, timeBudget) → top 3 with reasons`.
 *
 * - Hard filters: the table size is a good player count for the game (BGG poll, else the box), and
 *   the estimated duration fits the budget.
 * - Score: the table's mean predicted enjoyment minus λ·spread, so one miserable player costs a lot;
 *   players with few answers weigh less. Bonuses for novelty (nobody played it) and an eager owner.
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
        if (n == 0) return Suggestions.NothingFits("Who's playing?", "Tick the players at your table.")
        val playable = shelf.filter { (game, _) -> n in game.goodPlayerCounts }
        val fitting = playable.filter { (game, _) -> timeBudget == null || estimatedMinutes(game, n, speed) <= timeBudget }
        if (fitting.isEmpty()) return nothingFits(n, timeBudget, playable, speed)

        val predictions = table.associate { p -> p.id to predictor.enjoyment(p.games, fitting.map { it.first }, now) }
        val names = table.associate { it.id to it.name }
        val unknown = table.filter { it.games.history.comparisons.size < KNOWN_AFTER }.map { it.id }
        val nobodyKnown = unknown.size == n
        return Suggestions.Found(
            fitting.map { (game, entry) ->
                val means = table.map { p -> predictions.getValue(p.id).getValue(game.id).mean }
                val weights = table.map { p -> (p.games.history.comparisons.size / 10.0).coerceIn(0.2, 1.0) }
                val mean = means.zip(weights).sumOf { (m, w) -> m * w } / weights.sum()
                val spread = sqrt(means.zip(weights).sumOf { (m, w) -> w * (m - mean) * (m - mean) } / weights.sum())
                val novel = table.none { it.games.hasPlayed(game.id) }
                val eager = entry.eager.firstOrNull { it in names }
                val score = mean - spreadPenalty * spread + (if (novel) noveltyBonus else 0.0) + (if (eager != null) wishBonus else 0.0)
                val minutes = estimatedMinutes(game, n, speed)
                val reason = reason(game, table, means, spread, novel, eager?.let(names::get), minutes, timeBudget, nobodyKnown)
                Suggestion(game, minutes, reason, score)
            }.sortedByDescending { it.score }.take(count),
            unknown,
        )
    }

    private fun nothingFits(n: Int, budget: Int?, playable: List<Pair<Game, ShelfGame>>, speed: Double): Suggestions.NothingFits {
        if (playable.isEmpty()) {
            return Suggestions.NothingFits("Nothing plays well with $n", "The shelf's short on games for $n players. Try splitting into two tables.")
        }
        val relax = BUDGETS.filter { budget == null || it > budget }.firstOrNull { b -> playable.any { estimatedMinutes(it.first, n, speed) <= b } }
        val fits = relax?.let { b -> playable.count { estimatedMinutes(it.first, n, speed) <= b } } ?: 0
        return Suggestions.NothingFits("Nothing fits $n players in ${budget?.let(::duration)}", "Everything on the shelf takes longer. Loosen the time:", relax, fits)
    }

    private fun reason(
        game: Game, table: List<TablePlayer>, means: List<Double>, spread: Double, novel: Boolean,
        eagerOwner: String?, minutes: Int, budget: Int?, nobodyKnown: Boolean,
    ): String {
        val n = table.size
        val everyoneRated = table.all { game.id in it.games.history.comparedGames }
        return when {
            eagerOwner != null -> "$eagerOwner brought it and has been itching to play."
            // Without anyone's taste there is nothing to say about enjoyment: only about fit.
            nobodyKnown -> when {
                n in game.bestPlayers && game.bestPlayers.size <= 2 -> "Best at exactly ${words(n)}, which is you."
                novel -> "Nobody's played it yet: something new for the table."
                budget != null && minutes <= budget - 20 -> "Fits your ${duration(budget)} with room for snacks."
                else -> "Plays well with ${words(n)}."
            }
            novel && means.all { it >= 0 } -> "Nobody's played it and everyone's likely to enjoy it."
            everyoneRated && means.all { it > 0.3 } && spread < 0.5 -> "Safe bet: ${if (n == 2) "both" else "all ${words(n)}"} of you rated it highly."
            n in game.bestPlayers && game.bestPlayers.size <= 2 -> "Best at exactly ${words(n)}, which is you."
            novel -> "Nobody's played it yet: something new for the table."
            budget != null && minutes <= budget - 20 -> "Fits your ${duration(budget)} with room for snacks."
            else -> "A good middle ground for this table."
        }
    }

    companion object {
        /** Answers after which a player's taste counts for something; below it we say the picks are a fit-only guess for them. */
        const val KNOWN_AFTER = 5

        /** Time budgets offered when nothing fits, in minutes. */
        val BUDGETS = listOf(45, 90, 120, 180, 240)

        /** BGG's playtime, interpolated from min to max over the player range, times the group's speed. Rounded to 5 min. */
        fun estimatedMinutes(game: Game, players: Int, speed: Double = 1.0): Int {
            val span = game.maxPlayers - game.minPlayers
            val t = if (span <= 0) 0.5 else ((players - game.minPlayers).toDouble() / span).coerceIn(0.0, 1.0)
            val base = game.minPlaytime + (game.maxPlaytime - game.minPlaytime) * t
            return ((base * speed / 5).roundToInt() * 5).coerceAtLeast(5)
        }

        fun duration(minutes: Int) = when {
            minutes < 120 -> "$minutes minutes"
            minutes % 60 == 0 -> "${minutes / 60} hours"
            else -> "${minutes / 60} h ${minutes % 60} min"
        }

        private fun words(n: Int) = listOf("zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten").getOrElse(n) { "$n" }
    }
}
