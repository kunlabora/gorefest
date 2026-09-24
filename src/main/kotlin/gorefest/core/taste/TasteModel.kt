package gorefest.core.taste

import gorefest.core.Basis
import gorefest.core.BggId
import gorefest.core.ComparisonRecorded
import gorefest.core.ComparisonResult
import java.time.Duration
import java.time.Instant
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sqrt

/** A game's score for one player: [mean] on a logistic scale around an average game at 0, and how unsure we are. */
data class Score(val mean: Double, val variance: Double) {
    val sd get() = sqrt(variance)
}

/**
 * One player's scores for one basis. Games never compared get [unseen] (a model that generalises,
 * like one on game features) or else the prior.
 */
data class Scores(val byGame: Map<BggId, Score>, val prior: Score, val unseen: ((BggId) -> Score)? = null) {
    operator fun get(game: BggId): Score = byGame[game] ?: unseen?.invoke(game) ?: prior

    /** Probability that the player prefers [a] over [b], accounting for uncertainty. */
    fun pPrefers(a: BggId, b: BggId): Double {
        val sa = get(a)
        val sb = get(b)
        return sigmoid((sa.mean - sb.mean) / sqrt(1 + PI / 8 * (sa.variance + sb.variance)))
    }
}

/** A versioned way to turn comparisons into scores. Versions are compared by replaying the log. */
interface TasteModel {
    val name: String
    fun fit(comparisons: List<ComparisonRecorded>, basis: Basis, now: Instant): Scores
}

/**
 * v0: Bradley–Terry per player and basis, fitted by a few rounds of per-game Newton steps
 * towards the MAP estimate under a N(0, [priorVariance]) prior.
 *
 * - "A"/"B": the winner beats the loser.
 * - "Both great"/"Both meh": the two tie with each other, and both beat (or lose to) a virtual
 *   average game fixed at 0. That keeps the absolute signal a pure win/loss would drop.
 * - Every comparison is weighted by `0.5^(age / halfLife)`, so tastes can drift.
 */
class BradleyTerry(
    private val halfLife: Duration = Duration.ofDays(270),
    private val priorVariance: Double = 1.0,
    private val rounds: Int = 25,
) : TasteModel {
    override val name = "bradley-terry-v0"

    /** i beat j with weight w; `null` is the virtual average game. y = 1 win, 0.5 tie. */
    private data class Outcome(val i: BggId?, val j: BggId?, val y: Double, val w: Double)

    override fun fit(comparisons: List<ComparisonRecorded>, basis: Basis, now: Instant): Scores {
        val outcomes = comparisons.filter { it.basis == basis }.flatMap { c ->
            val w = decay(c.at, now)
            when (c.result) {
                ComparisonResult.A -> listOf(Outcome(c.a, c.b, 1.0, w))
                ComparisonResult.B -> listOf(Outcome(c.b, c.a, 1.0, w))
                ComparisonResult.BOTH_GREAT -> listOf(Outcome(c.a, c.b, 0.5, w), Outcome(c.a, null, 1.0, w), Outcome(c.b, null, 1.0, w))
                ComparisonResult.BOTH_MEH -> listOf(Outcome(c.a, c.b, 0.5, w), Outcome(null, c.a, 1.0, w), Outcome(null, c.b, 1.0, w))
            }
        }
        val games = outcomes.flatMap { listOfNotNull(it.i, it.j) }.toSet()
        val mean = games.associateWith { 0.0 }.toMutableMap()
        val precision = games.associateWith { 1 / priorVariance }.toMutableMap()
        fun s(g: BggId?) = if (g == null) 0.0 else mean.getValue(g)

        repeat(rounds) {
            for (g in games) {
                var grad = -mean.getValue(g) / priorVariance
                var hess = 1 / priorVariance
                for (o in outcomes) {
                    val sign = when (g) {
                        o.i -> 1.0
                        o.j -> -1.0
                        else -> continue
                    }
                    val p = sigmoid(s(o.i) - s(o.j))
                    grad += sign * o.w * (o.y - p)
                    hess += o.w * p * (1 - p)
                }
                mean[g] = mean.getValue(g) + grad / hess
                precision[g] = hess
            }
        }
        return Scores(games.associateWith { Score(mean.getValue(it), 1 / precision.getValue(it)) }, Score(0.0, priorVariance))
    }

    private fun decay(at: Instant, now: Instant): Double {
        val ageDays = Duration.between(at, now).toHours().coerceAtLeast(0) / 24.0
        return Math.pow(0.5, ageDays / halfLife.toDays())
    }
}

fun sigmoid(x: Double) = 1 / (1 + exp(-x))
