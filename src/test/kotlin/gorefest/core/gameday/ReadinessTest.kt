package gorefest.core.gameday

import gorefest.core.Basis
import gorefest.core.ComparisonRecorded
import gorefest.core.ComparisonResult
import gorefest.core.PlayerId
import gorefest.core.PlayerJoined
import gorefest.core.taste.TasteHistory
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

    private fun answers(player: PlayerId, n: Int) = TasteHistory(List(n) {
        ComparisonRecorded(player, g("Azul").id, g("Cascadia").id, ComparisonResult.A, Basis.VIBE, Instant.EPOCH)
    })

    private val gameDay = listOf(gameDayCreated(), PlayerJoined(gorefest7, anna), PlayerJoined(gorefest7, bram), PlayerJoined(gorefest7, kim), PlayerJoined(gorefest7, jo))
        .fold(GameDays.initialState, GameDays::evolve).getValue(gorefest7)

    private fun shelf(vararg names: String) = names.map { g(it) to ShelfGame(g(it).id, listOf(anna)) }

    @Test
    fun `players who need a nudge come first, with their answer counts`() {
        val readiness = Readiness.of(gameDay, shelf("Azul"), mapOf(anna to answers(anna, 12), bram to answers(bram, 3), kim to answers(kim, 10)))

        readiness.players.map { it.status } shouldContainExactly listOf(TasteStatus.NONE, TasteStatus.THIN, TasteStatus.READY, TasteStatus.READY)
        readiness.players.first().player shouldBe jo
        readiness.tasteReady shouldBe 2
        readiness.players.first { it.player == anna }.brought shouldBe 1
    }

    @Test
    fun `gaps are table sizes that fewer than three shelf games serve`() {
        val readiness = Readiness.of(gameDay, shelf("Azul", "Cascadia", "Brass: Birmingham", "Codenames"), emptyMap())

        readiness.gaps shouldBe emptyList() // 3 and 4 are fine

        val party = Readiness.of(gameDay, shelf("Azul", "Codenames", "Twilight Struggle"), emptyMap())
        party.gaps.map { it.players } shouldBe listOf(3, 4)
        party.gaps.last().games.map { it.name } shouldContainExactly listOf("Azul", "Codenames")
    }
}
