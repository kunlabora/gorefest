package gorefest.core.taste

import gorefest.core.Basis
import gorefest.core.BggId
import gorefest.core.ComparisonResult
import gorefest.core.bgg.Game
import java.time.Instant

/** A question in the taste round. */
data class WizardPair(val a: Game, val b: Game, val basis: Basis)

/** Where the round stands; the shell carries it between requests in the form. */
data class RoundState(val step: Int = 0, val stableSteps: Int = 0, val asked: Set<Set<BggId>> = emptySet(), val top: List<BggId> = emptyList())

/** What the player knows about games, as far as we can tell. */
data class PlayerGames(val history: TasteHistory, val owned: Set<BggId>) {
    /** Declared status wins; otherwise we assume people have played what they own. */
    fun hasPlayed(game: BggId) = history.played[game] ?: (game in owned)
}

/**
 * Picks taste-round pairs. The pool is the game day's games in order (shelf, host's collection,
 * the player's collection); every [anchorEvery]th pair includes a popular anchor from BGG's top ranks,
 * which gives everyone a shared reference even when the shelf is long.
 */
class Wizard(
    private val model: TasteModel = BradleyTerry(),
    private val poolSize: Int = 24,
    private val anchorEvery: Int = 5,
    private val minPairs: Int = 10,
    private val maxPairs: Int = 15,
) {
    val roundLength get() = maxPairs

    fun next(pool: List<Game>, anchors: List<Game>, player: PlayerGames, round: RoundState, now: Instant): WizardPair? {
        val games = pool.distinctBy { it.id }.take(poolSize)
        val extra = anchors.filter { a -> games.none { it.id == a.id } }.take(poolSize)
        val anchorTurn = (round.step % anchorEvery == anchorEvery - 1 && extra.isNotEmpty()) || games.size < 2
        val candidates = if (anchorTurn) games + extra else games
        val anchorIds = extra.map { it.id }.toSet()

        val vibe = player.history.scores(model, Basis.VIBE, now)
        val played = player.history.scores(model, Basis.PLAYED, now)
        return pairs(candidates)
            .filter { (a, b) -> setOf(a.id, b.id) !in round.asked }
            .filter { (a, b) -> !anchorTurn || games.size < 2 || a.id in anchorIds || b.id in anchorIds }
            .map { (a, b) ->
                val basis = if (player.hasPlayed(a.id) && player.hasPlayed(b.id)) Basis.PLAYED else Basis.VIBE
                val scores = if (basis == Basis.PLAYED) played else vibe
                val p = scores.pPrefers(a.id, b.id)
                WizardPair(a, b, basis) to p * (1 - p) * (scores[a.id].variance + scores[b.id].variance)
            }
            .maxByOrNull { it.second }?.first
    }

    fun shouldStop(round: RoundState) = round.step >= maxPairs || (round.step >= minPairs && round.stableSteps >= 3)

    /** After an answer: one step further, and whether the player's top 3 stayed put. */
    fun advance(round: RoundState, pair: WizardPair, newTop: List<BggId>) = round.copy(
        step = round.step + 1,
        stableSteps = if (newTop == round.top) round.stableSteps + 1 else 0,
        asked = round.asked + setOf(setOf(pair.a.id, pair.b.id)),
        top = newTop,
    )

    private fun pairs(games: List<Game>) = games.indices.flatMap { i -> (i + 1 until games.size).map { games[i] to games[it] } }
}

/** A predicted favourite and why. */
data class Favourite(val game: Game, val score: Score, val reason: String)

/**
 * Predicted enjoyment: PLAYED scores for games the player has played (falling back to VIBE when
 * they never compared it after playing), VIBE scores (appeal) for the rest.
 */
class Predictor(private val model: TasteModel = BradleyTerry()) {
    fun enjoyment(player: PlayerGames, games: List<Game>, now: Instant): Map<BggId, Score> {
        val vibe = player.history.scores(model, Basis.VIBE, now)
        val played = player.history.scores(model, Basis.PLAYED, now)
        return games.associate { g ->
            g.id to if (player.hasPlayed(g.id) && g.id in played.byGame) played[g.id] else vibe[g.id]
        }
    }

    fun favourites(player: PlayerGames, games: List<Game>, now: Instant, count: Int = 3): List<Favourite> {
        val scores = enjoyment(player, games, now)
        val byId = games.associateBy { it.id }
        return games.distinctBy { it.id }
            .filter { it.id in player.history.comparedGames }
            .sortedByDescending { scores.getValue(it.id).mean }
            .take(count)
            .map { Favourite(it, scores.getValue(it.id), reason(it, player, byId)) }
    }

    /** In the player's own answers: "You picked it over Azul and CATAN." */
    private fun reason(game: Game, player: PlayerGames, names: Map<BggId, Game>): String {
        val mine = player.history.comparisons.filter { it.a == game.id || it.b == game.id }
        if (mine.any { it.result == ComparisonResult.BOTH_GREAT }) return "You called it great."
        val beaten = mine.mapNotNull { c ->
            when {
                c.a == game.id && c.result == ComparisonResult.A -> c.b
                c.b == game.id && c.result == ComparisonResult.B -> c.a
                else -> null
            }
        }.distinct().mapNotNull { names[it]?.name }
        return when (beaten.size) {
            0 -> "Early days: a few more pairs will sharpen this."
            1 -> "You picked it over ${beaten[0]}."
            else -> "You picked it over ${beaten[0]} and ${beaten[1]}."
        }
    }
}
