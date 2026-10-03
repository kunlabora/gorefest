package gorefest.shell.gameday

import gorefest.core.BggId
import gorefest.core.PlayLogged
import gorefest.core.PlayerId
import gorefest.core.gameday.anna
import gorefest.core.gameday.bram
import gorefest.core.gameday.gorefest7
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant

class RecapTest {
    private val azul = BggId(1)
    private val unknown = BggId(2)
    private fun play(game: BggId, minutes: Int, players: List<PlayerId>) =
        PlayLogged(gorefest7, game, players, minutes, anna, Instant.EPOCH)

    @Test
    fun `lines say who played what for how long, and totals add up`() {
        val recap = Recap.of(
            listOf(play(azul, 40, listOf(anna, bram)), play(azul, 25, listOf(anna))),
            gameName = { if (it == azul) "Azul" else null },
            playerName = { if (it == anna) "Anna" else "Bram" },
        )

        recap.lines shouldBe listOf(RecapLine("Azul", listOf("Anna", "Bram"), 40), RecapLine("Azul", listOf("Anna"), 25))
        recap.games shouldBe 2
        recap.minutes shouldBe 65
    }

    @Test
    fun `unknown games and players get a stand-in name`() {
        val recap = Recap.of(listOf(play(unknown, 30, listOf(bram))), gameName = { null }, playerName = { null })

        recap.lines shouldBe listOf(RecapLine("A game", listOf("someone"), 30))
    }
}
