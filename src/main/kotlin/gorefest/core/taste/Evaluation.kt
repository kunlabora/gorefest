package gorefest.core.taste

import gorefest.core.ComparisonRecorded
import gorefest.core.ComparisonResult
import gorefest.core.Event
import kotlin.math.ln

/** How well a model predicted comparisons it hadn't seen yet. */
data class EvaluationReport(val model: String, val predictions: Int, val accuracy: Double, val logLoss: Double)

/**
 * Replays the log in order and, for every decisive (A or B) comparison, asks the model what the
 * player would pick given only their earlier comparisons. Ties are learned from but not scored.
 */
object Evaluation {
    fun evaluate(events: List<Event>, model: TasteModel): EvaluationReport {
        val seen = mutableMapOf<gorefest.core.PlayerId, MutableList<ComparisonRecorded>>()
        var n = 0
        var correct = 0
        var loss = 0.0
        events.filterIsInstance<ComparisonRecorded>().sortedBy { it.at }.forEach { c ->
            val history = seen.getOrPut(c.player) { mutableListOf() }
            if (c.result == ComparisonResult.A || c.result == ComparisonResult.B) {
                val pA = model.fit(history, c.basis.model, c.at).pPrefers(c.a, c.b).coerceIn(1e-6, 1 - 1e-6)
                val aWon = c.result == ComparisonResult.A
                n++
                if ((pA > 0.5) == aWon) correct++
                loss -= ln(if (aWon) pA else 1 - pA)
            }
            history += c
        }
        return EvaluationReport(model.name, n, if (n == 0) 0.0 else correct.toDouble() / n, if (n == 0) 0.0 else loss / n)
    }
}

/** The baseline every model must beat: always 50/50. */
object CoinFlip : TasteModel {
    override val name = "coin-flip"
    override fun fit(comparisons: List<ComparisonRecorded>, basis: gorefest.core.Basis, now: java.time.Instant) =
        Scores(emptyMap(), Score(0.0, 1.0))
}
