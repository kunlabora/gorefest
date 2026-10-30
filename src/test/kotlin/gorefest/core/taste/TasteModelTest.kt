package gorefest.core.taste

import gorefest.contract.Obligation
import gorefest.core.Rejection
import gorefest.core.Basis
import gorefest.core.BggId
import gorefest.core.ComparisonRecorded
import gorefest.core.ComparisonResult
import gorefest.core.ComparisonResult.A
import gorefest.core.ComparisonResult.B
import gorefest.core.ComparisonResult.BOTH_GREAT
import gorefest.core.ComparisonResult.BOTH_MEH
import gorefest.core.Event
import gorefest.core.GamePlayStatusDeclared
import gorefest.core.eventsourcing.given
import gorefest.core.gameday.anna
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import kotlin.random.Random

class TasteModelTest {
    private val now = Instant.parse("2026-09-24T10:00:00Z")
    private val brass = BggId(1)
    private val azul = BggId(2)
    private val catan = BggId(3)
    private val model = BradleyTerry()

    private fun c(a: BggId, b: BggId, result: ComparisonResult, basis: Basis = Basis.VIBE, daysAgo: Long = 0) =
        ComparisonRecorded(anna, a, b, result, basis, now - Duration.ofDays(daysAgo))

    private fun fit(vararg cs: ComparisonRecorded, basis: Basis = Basis.VIBE) = model.fit(cs.toList(), basis, now)

    @Test
    @Obligation("TA-5")
    fun `a watched answer teaches the enjoyment model, not the vibe model, in both models`() {
        val watched = arrayOf(c(brass, azul, A, Basis.WATCHED), c(brass, azul, A, Basis.WATCHED))
        val features = FeatureModel(games = { null })

        for (m in listOf(model, features)) {
            m.fit(watched.toList(), Basis.PLAYED, now).pPrefers(brass, azul) shouldBeGreaterThan 0.5
            m.fit(watched.toList(), Basis.VIBE, now).pPrefers(brass, azul) shouldBe (0.5 plusOrMinus 1e-9)
        }
    }

    @Test
    @Obligation("TA-5")
    fun `watching a game doesn't make it played`() {
        val history = listOf<Event>(c(brass, azul, A, Basis.WATCHED)).fold(Tastes.initialState, Tastes::evolve).of(anna)

        history.played shouldBe emptyMap()
        PlayerGames(history, emptySet()).hasPlayed(brass) shouldBe false
    }

    @Test
    @Obligation("TA-2")
    fun `the winner scores higher, and is predicted to win again`() {
        val scores = fit(c(brass, azul, A), c(brass, azul, A), c(azul, brass, B))

        scores[brass].mean shouldBeGreaterThan scores[azul].mean
        scores.pPrefers(brass, azul) shouldBeGreaterThan 0.7
    }

    @Test
    @Obligation("TA-2")
    fun `beating the winner puts you on top`() {
        val scores = fit(c(brass, azul, A), c(azul, catan, A), c(brass, azul, A), c(azul, catan, A))

        scores[brass].mean shouldBeGreaterThan scores[azul].mean
        scores[azul].mean shouldBeGreaterThan scores[catan].mean
    }

    @Test
    @Obligation("TA-3")
    fun `both great lifts both above average, both meh sinks both`() {
        val great = fit(c(brass, azul, BOTH_GREAT))
        val meh = fit(c(brass, azul, BOTH_MEH))

        great[brass].mean shouldBeGreaterThan 0.0
        great[azul].mean shouldBeGreaterThan 0.0
        meh[brass].mean shouldBeLessThan 0.0
        great[brass].mean shouldBe (great[azul].mean plusOrMinus 1e-9)
    }

    @Test
    @Obligation("TA-7")
    fun `more comparisons mean less uncertainty, unknown games keep the prior`() {
        val once = fit(c(brass, azul, A))
        val often = fit(*Array(6) { c(brass, azul, A) })

        often[brass].variance shouldBeLessThan once[brass].variance
        once[catan] shouldBe Score(0.0, 1.0)
        once.pPrefers(catan, BggId(99)) shouldBe 0.5
    }

    @Test
    @Obligation("TA-6")
    fun `recent answers outweigh old ones`() {
        val scores = fit(*Array(3) { c(azul, brass, A, daysAgo = 800) }, *Array(3) { c(brass, azul, A, daysAgo = 1) })

        scores[brass].mean shouldBeGreaterThan scores[azul].mean
    }

    @Test
    @Obligation("TA-4")
    fun `vibe and played are separate models`() {
        val cs = arrayOf(c(brass, azul, A, Basis.VIBE), c(azul, brass, A, Basis.PLAYED), c(azul, brass, A, Basis.PLAYED))

        fit(*cs, basis = Basis.VIBE).pPrefers(brass, azul) shouldBeGreaterThan 0.5
        fit(*cs, basis = Basis.PLAYED).pPrefers(brass, azul) shouldBeLessThan 0.5
    }

    @Test
    @Obligation("TA-11")
    fun `the taste history keeps comparisons and play status per player`() {
        val events = listOf<Event>(c(brass, azul, A), GamePlayStatusDeclared(anna, catan, played = false))
        val history = events.fold(Tastes.initialState, Tastes::evolve).of(anna)

        history.comparisons.size shouldBe 1
        history.played shouldBe mapOf(catan to false)
        history.comparedGames shouldBe setOf(brass, azul)
    }

    @Test
    @Obligation("TA-10")
    fun `on a replay, v0 predicts a consistent player far better than a coin flip`() {
        val random = Random(42)
        val truth = (1..20).associate { BggId(it) to random.nextDouble(-2.0, 2.0) }
        val games = truth.keys.toList()
        val log = (0 until 300).map { i ->
            val (x, y) = games.shuffled(random).take(2)
            val xWins = random.nextDouble() < sigmoid(truth.getValue(x) - truth.getValue(y))
            ComparisonRecorded(anna, x, y, if (xWins) A else B, Basis.VIBE, now - Duration.ofHours(300L - i))
        }

        val v0 = Evaluation.evaluate(log, model)
        val coin = Evaluation.evaluate(log, CoinFlip)
        // The best any model could do: always pick the truly preferred game.
        val oracle = log.count { (truth.getValue(it.a) > truth.getValue(it.b)) == (it.result == A) }.toDouble() / log.size

        v0.predictions shouldBe 300
        v0.accuracy shouldBeGreaterThan oracle - 0.1
        v0.logLoss shouldBeLessThan coin.logLoss
    }

    @Test
    @Obligation("TA-1")
    fun `comparing a game with itself is refused`() {
        RecordComparisonDecider.given().whenever(c(brass, brass, A)).thenRejects(Rejection.SameGameTwice)
    }
}
