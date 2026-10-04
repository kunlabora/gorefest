package gorefest.core.taste

import gorefest.core.Basis
import gorefest.core.ComparisonRecorded
import gorefest.core.ComparisonResult
import gorefest.core.gameday.anna
import gorefest.shell.bgg.DevCatalog
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import kotlin.random.Random

class FeatureModelTest {
    private val now = Instant.parse("2026-09-24T10:00:00Z")
    private val catalog = DevCatalog.games.associateBy { it.id }
    private val v1 = FeatureModel(catalog::get)
    private fun g(name: String) = DevCatalog.games.first { it.name == name }

    /** A player who loves deck building and co-ops and dislikes heavy games, plus a little noise per game. */
    private fun trueTaste(random: Random) = DevCatalog.games.associate { game ->
        game.id to (if ("Deck Building" in game.mechanics) 1.5 else 0.0) +
            (if ("Cooperative Game" in game.mechanics) 1.0 else 0.0) -
            0.8 * (game.weight - 2.5) + random.nextDouble(-0.3, 0.3)
    }

    @Test
    fun `v1 beats v0 on a replay of a player whose taste follows game features`() {
        val random = Random(7)
        val truth = trueTaste(random)
        val games = DevCatalog.games
        val log = (0 until 150).map { i ->
            val (x, y) = games.shuffled(random).take(2)
            val xWins = random.nextDouble() < sigmoid(truth.getValue(x.id) - truth.getValue(y.id))
            ComparisonRecorded(anna, x.id, y.id, if (xWins) ComparisonResult.A else ComparisonResult.B, Basis.VIBE, now - Duration.ofHours(150L - i))
        }

        val old = Evaluation.evaluate(log, BradleyTerry())
        val new = Evaluation.evaluate(log, v1)

        new.logLoss shouldBeLessThan old.logLoss
        new.accuracy shouldBeGreaterThan old.accuracy
    }

    @Test
    fun `taste carries over to games never compared`() {
        val answers = listOf("Dominion" to "Power Grid", "Lost Ruins of Arnak" to "Puerto Rico", "Great Western Trail" to "Twilight Struggle")
            .flatMap { (a, b) -> List(3) { ComparisonRecorded(anna, g(a).id, g(b).id, ComparisonResult.A, Basis.VIBE, now) } }

        val scores = v1.fit(answers, Basis.VIBE, now)

        // Dune: Imperium is a deck builder like the winners; Terra Mystica shares a mechanic with a loser. Neither was compared.
        scores[g("Dune: Imperium").id].mean shouldBeGreaterThan scores[g("Terra Mystica").id].mean
        BradleyTerry().fit(answers, Basis.VIBE, now).pPrefers(g("Dune: Imperium").id, g("Terra Mystica").id) shouldBe 0.5
    }

    @Test
    fun `vibe trust grows when vibes predicted enjoyment, and shrinks when they didn't`() {
        val (a, b) = g("Azul") to g("Cascadia")
        val vibe = List(3) { ComparisonRecorded(anna, a.id, b.id, ComparisonResult.A, Basis.VIBE, now) }
        fun played(result: ComparisonResult) = List(4) { ComparisonRecorded(anna, a.id, b.id, result, Basis.PLAYED, now) }

        vibeTrust(TasteHistory(vibe), v1, now) shouldBe 0.5
        vibeTrust(TasteHistory(vibe + played(ComparisonResult.A)), v1, now) shouldBeGreaterThan 0.7
        vibeTrust(TasteHistory(vibe + played(ComparisonResult.B)), v1, now) shouldBeLessThan 0.2
    }

    @Test
    fun `features describe a game's tags, weight, length and best counts`() {
        val f = features(g("Azul"))

        f.keys.filter { it.startsWith("mechanic:") }.size shouldBe 3
        f.getValue("weight") shouldBe ((1.8 - 2.5) plusOrMinus 1e-9)
        f.getValue("best:2") shouldBe 0.5
    }


    @Test
    fun `a game missing from the catalog is not cached as having no features`() {
        val azul = g("Azul")
        val cascadia = g("Cascadia")
        var known = false
        val model = FeatureModel({ id -> if (known) catalog[id] else null })
        val answers = List(3) { ComparisonRecorded(anna, azul.id, cascadia.id, ComparisonResult.A, Basis.VIBE, now) }

        val blind = model.fit(answers, Basis.VIBE, now)
        val blindAzul = blind[azul.id].mean
        known = true
        val sighted = model.fit(answers, Basis.VIBE, now)

        // With real features the weights take part of the credit, so the offsets differ from the featureless fit.
        sighted[azul.id].mean shouldNotBe blindAzul
        sighted.unseen!!(g("Root").id).mean shouldNotBe 0.0
    }

    @Test
    fun `vibes that predict nothing leave trust at one half whatever the answers`() {
        val (a, b) = g("Azul") to g("Cascadia")
        val played = List(4) { ComparisonRecorded(anna, a.id, b.id, ComparisonResult.B, Basis.PLAYED, now) }

        vibeTrust(TasteHistory(played), v1, now) shouldBe 0.5
        vibeTrust(TasteHistory(played.map { it.copy(result = ComparisonResult.A) }), v1, now) shouldBe 0.5
    }
}
