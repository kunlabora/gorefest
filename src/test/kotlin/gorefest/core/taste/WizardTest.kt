package gorefest.core.taste

import gorefest.core.Basis
import gorefest.core.BggId
import gorefest.core.ComparisonRecorded
import gorefest.core.ComparisonResult
import gorefest.core.GamePlayStatusDeclared
import gorefest.core.game.Game
import gorefest.core.gameday.anna
import gorefest.shell.bgg.DevCatalog
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant

class WizardTest {
    private val now = Instant.parse("2026-09-24T10:00:00Z")
    private val games = DevCatalog.games
    private fun g(name: String) = games.first { it.name == name }
    private val shelf = listOf(g("Azul"), g("Cascadia"), g("Brass: Birmingham"), g("Root"))
    private val anchors = games.filter { it.popularityRank != null }.sortedBy { it.popularityRank }.take(10).filter { it !in shelf }
    private val wizard = Wizard()
    private val nobody = PlayerGames(TasteHistory(), owned = emptySet())

    @Test
    fun `pairs come from the game day's games, never twice`() {
        var round = RoundState()
        val seen = mutableSetOf<Set<BggId>>()
        repeat(4) {
            val pair = wizard.next(shelf, anchors, nobody, round, now).shouldNotBeNull()
            (pair.a in shelf && pair.b in shelf) shouldBe true
            seen.add(setOf(pair.a.id, pair.b.id)) shouldBe true
            round = wizard.advance(round, pair, emptyList())
        }
    }

    @Test
    fun `every fifth pair includes a popular anchor`() {
        val pair = wizard.next(shelf, anchors, nobody, RoundState(asked = listOf(0 to 1, 0 to 2, 0 to 3, 1 to 2).map { (i, j) -> setOf(shelf[i].id, shelf[j].id) }.toSet()), now).shouldNotBeNull()

        (pair.a in anchors || pair.b in anchors) shouldBe true
    }

    @Test
    fun `a tiny shelf is topped up with anchors`() {
        val pair = wizard.next(shelf.take(1), anchors, nobody, RoundState(), now).shouldNotBeNull()

        listOf(pair.a, pair.b).count { it in anchors } shouldBe (if (pair.a in shelf || pair.b in shelf) 1 else 2)
    }

    @Test
    fun `owned games count as played, unless the player said otherwise`() {
        val owner = PlayerGames(TasteHistory(), owned = shelf.map { it.id }.toSet())
        wizard.next(shelf, anchors, owner, RoundState(), now)!!.basis shouldBe Basis.PLAYED
        wizard.next(shelf, anchors, nobody, RoundState(), now)!!.basis shouldBe Basis.VIBE

        val declared = PlayerGames(TasteHistory(played = shelf.associate { it.id to false }), owned = shelf.map { it.id }.toSet())
        wizard.next(shelf, anchors, declared, RoundState(), now)!!.basis shouldBe Basis.VIBE
    }

    @Test
    fun `the most uncertain pair goes first`() {
        val (azul, cascadia) = shelf
        val known = PlayerGames(TasteHistory(List(6) { ComparisonRecorded(anna, azul.id, cascadia.id, ComparisonResult.A, Basis.VIBE, now) }), emptySet())

        val pair = wizard.next(shelf, anchors, known, RoundState(), now)!!

        setOf(pair.a, pair.b) shouldContainExactly setOf(shelf[2], shelf[3])
    }

    @Test
    fun `when every pair was asked, the round is over`() {
        val two = shelf.take(2)
        wizard.next(two, emptyList(), nobody, RoundState(asked = setOf(two.map { it.id }.toSet())), now).shouldBeNull()
    }

    @Test
    fun `a focused round only asks pairs with an uncertain game in them`() {
        val focus = setOf(shelf[3].id)
        var round = RoundState()
        repeat(3) {
            val pair = wizard.next(shelf, anchors, nobody, round, now, mustInclude = focus)!!
            (pair.a.id in focus || pair.b.id in focus) shouldBe true
            round = wizard.advance(round, pair, emptyList())
        }
        // The shelf pairs with it are used up: it's asked against an anchor, not dropped early.
        val last = wizard.next(shelf, anchors, nobody, round, now, mustInclude = focus).shouldNotBeNull()
        (last.a.id in focus || last.b.id in focus) shouldBe true
        (last.a in anchors || last.b in anchors) shouldBe true
    }

    @Test
    fun `well-compared games are no longer uncertain`() {
        val (azul, cascadia) = shelf
        val known = PlayerGames(TasteHistory(List(8) { ComparisonRecorded(anna, azul.id, cascadia.id, ComparisonResult.A, Basis.VIBE, now) }), emptySet())

        wizard.uncertain(known, shelf, now) shouldBe setOf(shelf[2].id, shelf[3].id)
    }

    @Test
    fun `an old answer that wasn't asked again recently is stale`() {
        val (azul, cascadia) = shelf
        val old = ComparisonRecorded(anna, azul.id, cascadia.id, ComparisonResult.A, Basis.VIBE, now - java.time.Duration.ofDays(200))
        val lookup = shelf.associateBy { it.id }::get

        wizard.stalePair(PlayerGames(TasteHistory(listOf(old)), emptySet()), lookup, now) shouldBe WizardPair(azul, cascadia, Basis.VIBE)
        wizard.stalePair(PlayerGames(TasteHistory(listOf(old, old.copy(at = now))), emptySet()), lookup, now).shouldBeNull()
    }

    @Test
    fun `the round stops at 15, or from 10 once the top 3 settled`() {
        wizard.shouldStop(RoundState(step = 15)) shouldBe true
        wizard.shouldStop(RoundState(step = 10, stableSteps = 3)) shouldBe true
        wizard.shouldStop(RoundState(step = 10, stableSteps = 2)) shouldBe false
        wizard.shouldStop(RoundState(step = 9, stableSteps = 5)) shouldBe false
        wizard.shouldStop(RoundState(step = 0, asked = (1..30).map { setOf(BggId(it), BggId(it + 100)) }.toSet())) shouldBe true
    }

    @Test
    fun `a player who never learned anything still finishes, and never ends early while pairs remain`() {
        var round = RoundState()
        var asked = 0
        while (!wizard.shouldStop(round)) {
            val pair = wizard.next(shelf, anchors, nobody, round, now)
            if (pair == null) break
            round = wizard.advance(round, pair, emptyList(), learned = false)
            asked++
        }
        asked shouldBe 30
        round.step shouldBe 0
    }

    @Test
    fun `advancing counts steps and how long the top stayed the same`() {
        val pair = WizardPair(shelf[0], shelf[1], Basis.VIBE)
        val top = listOf(shelf[0].id)

        val once = wizard.advance(RoundState(top = top), pair, top)
        once.stableSteps shouldBe 1
        wizard.advance(once, pair, listOf(shelf[1].id)).stableSteps shouldBe 0
        once.asked shouldContain setOf(shelf[0].id, shelf[1].id)
    }

    @Test
    fun `favourites are the best predicted compared games, with a reason from the answers`() {
        val (azul, cascadia, brass, root) = shelf
        val history = TasteHistory(listOf(
            ComparisonRecorded(anna, brass.id, azul.id, ComparisonResult.A, Basis.VIBE, now),
            ComparisonRecorded(anna, brass.id, cascadia.id, ComparisonResult.A, Basis.VIBE, now),
            ComparisonRecorded(anna, cascadia.id, azul.id, ComparisonResult.A, Basis.VIBE, now),
            ComparisonRecorded(anna, root.id, azul.id, ComparisonResult.BOTH_GREAT, Basis.VIBE, now),
        ))

        val favourites = Predictor().favourites(PlayerGames(history, emptySet()), shelf, now)

        favourites.first().game shouldBe brass
        favourites.first().reason shouldBe FavouriteReason.PickedOver(listOf("Azul", "Cascadia"))
        favourites.first { it.game == root }.reason shouldBe FavouriteReason.CalledItGreat
    }

    @Test
    fun `played games are predicted from how much they were enjoyed`() {
        val (azul, cascadia) = shelf
        val history = TasteHistory(
            listOf(
                ComparisonRecorded(anna, azul.id, cascadia.id, ComparisonResult.A, Basis.VIBE, now),
                ComparisonRecorded(anna, cascadia.id, azul.id, ComparisonResult.A, Basis.PLAYED, now),
                ComparisonRecorded(anna, cascadia.id, azul.id, ComparisonResult.A, Basis.PLAYED, now),
            ),
            played = mapOf(azul.id to true, cascadia.id to true),
        )

        Predictor().favourites(PlayerGames(history, emptySet()), listOf(azul, cascadia), now).first().game shouldBe cascadia
        GamePlayStatusDeclared(anna, azul.id, true).played shouldBe true
    }


    @Test
    fun `a stale pair that was re-answered doesn't hide the next stale pair`() {
        val (azul, cascadia, brass, root) = shelf
        val lookup = shelf.associateBy { it.id }::get
        fun answer(a: Game, b: Game, days: Long, result: ComparisonResult = ComparisonResult.A) =
            ComparisonRecorded(anna, a.id, b.id, result, Basis.VIBE, now - java.time.Duration.ofDays(days))
        val history = TasteHistory(listOf(
            answer(azul, cascadia, 300), answer(cascadia, azul, 10),   // oldest pair, asked again since
            answer(brass, root, 250),
            answer(azul, root, 280, ComparisonResult.BOTH_GREAT),      // not decisive: never re-asked
        ))

        wizard.stalePair(PlayerGames(history, emptySet()), lookup, now) shouldBe WizardPair(brass, root, Basis.VIBE)
    }
}
