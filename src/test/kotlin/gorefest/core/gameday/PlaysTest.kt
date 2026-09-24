package gorefest.core.gameday

import gorefest.core.BggId
import gorefest.core.GameDayEnded
import gorefest.core.PlayLogged
import gorefest.core.PlayerJoined
import gorefest.core.eventsourcing.given
import gorefest.core.taste.PlayerGames
import gorefest.core.taste.Tastes
import gorefest.core.taste.Wizard
import gorefest.shell.bgg.DevCatalog
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant

class PlaysTest {
    private val now = Instant.parse("2026-09-27T12:00:00Z")
    private val azul = DevCatalog.games.first { it.name == "Azul" }   // 30–45 min, 2–4 → 2p ≈ 30 min
    private fun log(minutes: Int, players: List<gorefest.core.PlayerId> = listOf(anna, bram), game: BggId = azul.id) =
        PlayLogged(gorefest7, game, players, minutes, anna, now)
    private val joined = arrayOf(gameDayCreated(), PlayerJoined(gorefest7, anna), PlayerJoined(gorefest7, bram))

    @Test
    fun `a joined player logs a play for joined players`() {
        LogPlayDecider.given(*joined).whenever(log(45)).thenEmits(log(45))
    }

    @Test
    fun `logs need joined players, a real duration and a running game day`() {
        val stranger = gorefest.core.PlayerId(kotlin.uuid.Uuid.random())
        LogPlayDecider.given(*joined).whenever(log(45, listOf(anna, stranger))).thenRejects("Only players at this game day can be in a log")
        LogPlayDecider.given(*joined).whenever(log(45, emptyList())).thenRejects("Tick who played")
        LogPlayDecider.given(*joined).whenever(log(0)).thenRejects("That doesn't look like a real duration")
        LogPlayDecider.given(*joined, GameDayEnded(gorefest7)).whenever(log(45)).thenRejects("This game day has ended")
        LogPlayDecider.given(gameDayCreated()).whenever(log(45)).thenRejects("Join the game day first")
    }

    @Test
    fun `a logged play means everyone at the table has played it`() {
        val tastes = listOf(log(45)).fold(Tastes.initialState, Tastes::evolve)

        tastes.getValue(bram).played shouldBe mapOf(azul.id to true)
    }

    @Test
    fun `group speed is the median ratio to the box time, for mostly the same table`() {
        val games = DevCatalog.games.associateBy { it.id }::get
        val slow = listOf(log(45), log(60), log(40))   // ÷ 30 → 1.5, 2.0, 1.33

        groupSpeed(emptyList(), games, listOf(anna, bram)) shouldBe 1.0
        groupSpeed(slow, games, listOf(anna, bram)) shouldBe 1.5
        groupSpeed(slow, games, listOf(gorefest.core.PlayerId(kotlin.uuid.Uuid.random()))) shouldBe 1.0
        groupSpeed(listOf(log(300)), games, listOf(anna, bram)) shouldBe 1.8
    }

    @Test
    fun `after a play, compare it with a played game we know least about`() {
        val cascadia = DevCatalog.games.first { it.name == "Cascadia" }
        val brass = DevCatalog.games.first { it.name == "Brass: Birmingham" }
        val player = PlayerGames(listOf(log(45)).fold(Tastes.initialState, Tastes::evolve).getValue(anna), owned = setOf(cascadia.id))

        Wizard().partner(player, azul.id, listOf(azul, cascadia, brass), now) shouldBe cascadia
        Wizard().partner(player, azul.id, listOf(azul, brass), now) shouldBe null
    }
}
