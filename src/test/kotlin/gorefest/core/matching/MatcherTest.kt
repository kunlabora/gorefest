package gorefest.core.matching

import gorefest.core.Basis
import gorefest.core.BggId
import gorefest.core.ComparisonRecorded
import gorefest.core.ComparisonResult
import gorefest.core.PlayerId
import gorefest.core.game.Game
import gorefest.core.gameday.ShelfGame
import gorefest.core.taste.PlayerGames
import gorefest.core.taste.TasteHistory
import gorefest.shell.bgg.DevCatalog
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.uuid.Uuid

class MatcherTest {
    private val now = Instant.parse("2026-09-24T10:00:00Z")
    private val matcher = Matcher()
    private fun g(name: String) = DevCatalog.games.first { it.name == name }
    private val azul = g("Azul")                    // 2–4, 30–45
    private val brass = g("Brass: Birmingham")      // 2–4, 60–120
    private val codenames = g("Codenames")          // 2–8, 15
    private val ti4 = g("Twilight Imperium: Fourth Edition") // 3–6, 240–480

    private val owner = PlayerId(Uuid.random())
    private fun shelf(vararg games: Game, eager: Map<Game, PlayerId> = emptyMap()) =
        games.map { it to ShelfGame(it.id, listOf(owner), eager[it]?.let(::setOf).orEmpty()) }

    /** A player who prefers games earlier in [likes] over later ones, answered [times] times. */
    private fun player(name: String, vararg likes: Game, times: Int = 3, played: Boolean = false, id: PlayerId = PlayerId(Uuid.random())): TablePlayer {
        val comparisons = likes.toList().zipWithNext().flatMap { (a, b) ->
            List(times) { ComparisonRecorded(id, a.id, b.id, ComparisonResult.A, if (played) Basis.PLAYED else Basis.VIBE, now) }
        }
        val playedGames = if (played) likes.associate { it.id to true } else emptyMap()
        return TablePlayer(id, name, PlayerGames(TasteHistory(comparisons, playedGames), emptySet()))
    }

    private fun Suggestions.names() = (this as Suggestions.Found).top.map { it.game.name }

    @Test
    fun `games must suit the table size and the time budget`() {
        val table = List(3) { player("P$it") }

        matcher.suggest(table, shelf(azul, brass, codenames, ti4), timeBudget = 60, now).names().toSet() shouldBe setOf("Azul", "Codenames")
        matcher.suggest(table + player("P4") + player("P5"), shelf(azul, codenames), null, now).names() shouldBe listOf("Codenames")
    }

    @Test
    fun `one miserable player sinks a game that others love`() {
        val table = listOf(player("Anna", brass, azul), player("Bram", brass, azul), player("Kim", azul, brass, times = 6))

        val top = matcher.suggest(table, shelf(azul, brass), null, now).names()

        top.first() shouldBe "Azul"
    }

    @Test
    fun `an eager owner at the table gets their game, with a reason naming them`() {
        val kim = player("Kim")
        val result = matcher.suggest(listOf(kim, player("Bram")), shelf(azul, brass, eager = mapOf(brass to kim.id)), null, now) as Suggestions.Found

        result.top.first().game shouldBe brass
        result.top.first().reason shouldBe "Kim brought it and has been itching to play."
    }

    @Test
    fun `games nobody played get a novelty bonus and say so`() {
        val table = listOf(player("Anna", azul, brass, played = true), player("Bram"))

        val result = matcher.suggest(table, shelf(azul, codenames), null, now) as Suggestions.Found

        result.top.first { it.game == codenames }.reason shouldBe "Nobody's played it and everyone's likely to enjoy it."
    }

    @Test
    fun `when everyone who played it rated it highly, it's a safe bet`() {
        val table = List(4) { player("P$it", azul, brass, codenames, played = true) }

        val result = matcher.suggest(table, shelf(azul), null, now) as Suggestions.Found

        result.top.single().reason shouldBe "Safe bet: all four of you rated it highly."
    }

    @Test
    fun `nothing fitting in time suggests a longer budget`() {
        val result = matcher.suggest(List(4) { player("P$it") }, shelf(brass), timeBudget = 45, now)

        result.shouldBeInstanceOf<Suggestions.NothingFits>()
        result.title shouldBe "Nothing fits 4 players in 45 minutes"
        (result.relaxTo to result.fitsThen) shouldBe (120 to 1)
    }

    @Test
    fun `nothing for this many players says so`() {
        val result = matcher.suggest(List(7) { player("P$it") }, shelf(azul, brass), null, now)

        result.shouldBeInstanceOf<Suggestions.NothingFits>()
        result.title shouldBe "Nothing plays well with 7"
        result.relaxTo shouldBe null
    }

    @Test
    fun `duration follows the player count, rounded to 5 minutes, scaled by group speed`() {
        Matcher.estimatedMinutes(brass, 2) shouldBe 60
        Matcher.estimatedMinutes(brass, 3) shouldBe 90
        Matcher.estimatedMinutes(brass, 4) shouldBe 120
        Matcher.estimatedMinutes(brass, 4, speed = 1.25) shouldBe 150
        Matcher.estimatedMinutes(g("Twilight Struggle"), 2) shouldBe 150
        BggId(1).value shouldBe 1
    }
}
