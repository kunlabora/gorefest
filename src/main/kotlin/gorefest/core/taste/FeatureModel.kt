package gorefest.core.taste

import gorefest.core.Basis
import gorefest.core.BggId
import gorefest.core.ComparisonRecorded
import gorefest.core.ComparisonResult
import gorefest.core.game.Game
import java.time.Duration
import java.time.Instant
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sqrt

/** A game's features for the taste model, sparse: name → value. */
typealias Features = Map<String, Double>

/**
 * Game features from BGG metadata: mechanics and categories (one-hot, scaled so a game with many
 * tags doesn't shout), complexity, length and the player counts it's best at.
 */
fun features(game: Game): Features = buildMap {
    val tags = game.mechanics.map { "mechanic:$it" } + game.categories.map { "category:$it" }
    if (tags.isNotEmpty()) tags.forEach { put(it, 1 / sqrt(tags.size.toDouble())) }
    if (game.weight > 0) put("weight", game.weight - 2.5)
    val minutes = if (game.hasWildPlaytimeRange) game.typicalMinutes.toDouble() else (game.minPlaytime + game.maxPlaytime) / 2.0
    if (minutes > 0) put("length", ln(minutes / 60.0) / ln(2.0))
    game.bestPlayers.filter { it <= 6 }.forEach { put("best:$it", 0.5) }
}

/**
 * v1: Bradley–Terry where a game's score is a per-game offset plus learned weights on its
 * features: `s(g) = θ(g) + w · f(g)`. The weights carry taste over to games the player never
 * compared ("you like deck builders"). Fitted like v0 (MAP, Newton steps), alternating θ and w.
 */
class FeatureModel(
    private val games: (BggId) -> Game?,
    private val halfLife: Duration = Duration.ofDays(270),
    private val offsetVariance: Double = 0.5,
    private val weightVariance: Double = 0.5,
    private val rounds: Int = 20,
) : TasteModel {
    override val name = "features-v1"

    private data class Outcome(val i: BggId?, val j: BggId?, val y: Double, val w: Double)

    private val cache = java.util.concurrent.ConcurrentHashMap<BggId, Features>()
    /** Features of a game; a game the catalog doesn't know yet is not cached, so it gets real features once it is. */
    private fun f(id: BggId?): Features {
        if (id == null) return emptyMap()
        cache[id]?.let { return it }
        val game = games(id) ?: return emptyMap()
        return features(game).also { cache[id] = it }
    }

    override fun fit(comparisons: List<ComparisonRecorded>, basis: Basis, now: Instant): Scores {
        val outcomes = comparisons.filter { it.basis == basis }.flatMap { c ->
            val w = Math.pow(0.5, Duration.between(c.at, now).toHours().coerceAtLeast(0) / 24.0 / halfLife.toDays())
            when (c.result) {
                ComparisonResult.A -> listOf(Outcome(c.a, c.b, 1.0, w))
                ComparisonResult.B -> listOf(Outcome(c.b, c.a, 1.0, w))
                ComparisonResult.BOTH_GREAT -> listOf(Outcome(c.a, c.b, 0.5, w), Outcome(c.a, null, 1.0, w), Outcome(c.b, null, 1.0, w))
                ComparisonResult.BOTH_MEH -> listOf(Outcome(c.a, c.b, 0.5, w), Outcome(null, c.a, 1.0, w), Outcome(null, c.b, 1.0, w))
            }
        }
        val compared = outcomes.flatMap { listOfNotNull(it.i, it.j) }.toSet()
        val theta = compared.associateWith { 0.0 }.toMutableMap()
        val thetaPrecision = compared.associateWith { 1 / offsetVariance }.toMutableMap()
        val weights = mutableMapOf<String, Double>()
        val featureNames = compared.flatMap { f(it).keys }.toSet()

        fun dot(id: BggId?) = f(id).entries.sumOf { (k, v) -> v * (weights[k] ?: 0.0) }
        fun s(id: BggId?) = if (id == null) 0.0 else theta.getValue(id) + dot(id)

        repeat(rounds) {
            for (g in compared) {
                var grad = -theta.getValue(g) / offsetVariance
                var hess = 1 / offsetVariance
                for (o in outcomes) {
                    val sign = when (g) { o.i -> 1.0; o.j -> -1.0; else -> continue }
                    val p = sigmoid(s(o.i) - s(o.j))
                    grad += sign * o.w * (o.y - p)
                    hess += o.w * p * (1 - p)
                }
                theta[g] = theta.getValue(g) + grad / hess
                thetaPrecision[g] = hess
            }
            for (k in featureNames) {
                var grad = -(weights[k] ?: 0.0) / weightVariance
                var hess = 1 / weightVariance
                for (o in outcomes) {
                    val delta = (f(o.i)[k] ?: 0.0) - (f(o.j)[k] ?: 0.0)
                    if (delta == 0.0) continue
                    val p = sigmoid(s(o.i) - s(o.j))
                    grad += o.w * (o.y - p) * delta
                    hess += o.w * p * (1 - p) * delta * delta
                }
                weights[k] = (weights[k] ?: 0.0) + grad / hess
            }
        }
        val prior = Score(0.0, offsetVariance + weightVariance)
        return Scores(
            byGame = compared.associateWith { Score(s(it), 1 / thetaPrecision.getValue(it)) },
            prior = prior,
            // Never compared: the features alone say something, with the offset's full uncertainty.
            unseen = { id -> Score(dot(id), offsetVariance + if (f(id).isEmpty()) weightVariance else weightVariance / 2) },
        )
    }
}

/**
 * How much a player's vibes predict their enjoyment: of the decisive PLAYED answers, how often the
 * VIBE model would have picked the same winner. Mapped to 0 (vibes tell nothing) … 1 (vibes are
 * spot on), starting at 0.5 with no evidence.
 */
fun vibeTrust(history: TasteHistory, model: TasteModel, now: Instant): Double {
    val played = history.comparisons.filter { it.basis == Basis.PLAYED && (it.result == ComparisonResult.A || it.result == ComparisonResult.B) }
    if (played.isEmpty()) return 0.5
    val vibe = history.scores(model, Basis.VIBE, now)
    // A prediction of (nearly) 0.5 says nothing: it counts neither as right nor wrong.
    val informative = played.filter { c -> abs(vibe.pPrefers(c.a, c.b) - 0.5) > 0.01 }
    if (informative.isEmpty()) return 0.5
    val agree = informative.count { c -> (vibe.pPrefers(c.a, c.b) > 0.5) == (c.result == ComparisonResult.A) }
    val accuracy = (agree + 1.5) / (informative.size + 2)
    return ((accuracy - 0.5) * 2).coerceIn(0.0, 1.0)
}
