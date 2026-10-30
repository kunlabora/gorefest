package gorefest.core.gameday

import gorefest.contract.Obligation
import gorefest.core.Basis
import gorefest.core.ComparisonRecorded
import gorefest.core.ComparisonResult
import gorefest.core.PlayerId
import gorefest.core.PlayerJoined
import gorefest.core.taste.Tastes
import gorefest.shell.bgg.DevCatalog
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.uuid.Uuid

class ReadinessTest {
    private fun g(name: String) = DevCatalog.games.first { it.name == name }
    private val kim = PlayerId(Uuid.random())
    private val jo = PlayerId(Uuid.random())

    private fun tastes(vararg answers: Pair<PlayerId, Int>) = answers.flatMap { (player, n) ->
        List(n) { ComparisonRecorded(player, g("Azul").id, g("Cascadia").id, ComparisonResult.A, Basis.VIBE, Instant.EPOCH) }
    }.fold(Tastes.initialState, Tastes::evolve)

    private val gameDay = listOf(gameDayCreated(), PlayerJoined(gorefest7, anna), PlayerJoined(gorefest7, bram), PlayerJoined(gorefest7, kim), PlayerJoined(gorefest7, jo))
        .fold(GameDays.initialState, GameDays::evolve).getValue(gorefest7)

    private fun shelf(vararg names: String) = names.map { g(it) to ShelfGame(g(it).id, listOf(anna)) }

    @Test
    @Obligation("GD-24")
    fun `players who need a nudge come first, with their answer counts`() {
        val readiness = Readiness.of(gameDay, shelf("Azul"), tastes(anna to 12, bram to 3, kim to 10))

        readiness.players.map { it.status } shouldContainExactly listOf(TasteStatus.NONE, TasteStatus.THIN, TasteStatus.READY, TasteStatus.READY)
        readiness.players.first().player shouldBe jo
        readiness.tasteReady shouldBe 2
        readiness.players.first { it.player == anna }.brought shouldBe 1
    }

    @Test
    @Obligation("GD-23")
    fun `gaps are table sizes that fewer than three shelf games serve`() {
        val readiness = Readiness.of(gameDay, shelf("Azul", "Cascadia", "Brass: Birmingham", "Codenames"), tastes())

        readiness.gaps shouldBe emptyList() // 3 and 4 are fine

        val party = Readiness.of(gameDay, shelf("Azul", "Codenames", "Twilight Struggle"), tastes())
        party.gaps.map { it.players } shouldBe listOf(3, 4)
        party.gaps.last().games.map { it.name } shouldContainExactly listOf("Azul", "Codenames")
    }
}
