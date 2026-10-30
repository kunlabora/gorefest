package gorefest.core.gameday

import gorefest.contract.Obligation
import gorefest.core.Rejection
import gorefest.core.BggId
import gorefest.core.GameDayDetailsChanged
import gorefest.core.GameDayCancelled
import gorefest.core.GameDayEnded
import gorefest.core.PlayLogged
import gorefest.core.PlayerErased
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
    @Obligation("PL-1")
    fun `a joined player logs a play for joined players`() {
        LogPlayDecider.given(*joined).whenever(log(45)).thenEmits(log(45))
    }

    @Test
    @Obligation("PL-2", "PL-4")
    fun `logs need joined players, a real duration and a game day that wasn't cancelled`() {
        val stranger = gorefest.core.PlayerId(kotlin.uuid.Uuid.random())
        LogPlayDecider.given(*joined).whenever(log(45, listOf(anna, stranger))).thenRejects(Rejection.PlayersNotAtGameDay)
        LogPlayDecider.given(*joined).whenever(log(45, emptyList())).thenRejects(Rejection.NobodyPlayed)
        LogPlayDecider.given(*joined).whenever(log(0)).thenRejects(Rejection.ImplausibleDuration)
        LogPlayDecider.given(*joined, GameDayCancelled(gorefest7)).whenever(log(45)).thenRejects(Rejection.GameDayCancelled)
        LogPlayDecider.given(gameDayCreated()).whenever(log(45)).thenRejects(Rejection.NotJoined)
    }

    @Test
    @Obligation("PL-4")
    fun `a play can still be logged after the game day ended`() {
        LogPlayDecider.given(*joined, GameDayEnded(gorefest7)).whenever(log(45)).thenEmits(log(45))
    }

    @Test
    @Obligation("PL-5", "PR-9")
    fun `a play never names someone who was erased, also on an ended game day that still shows them`() {
        LogPlayDecider.given(*joined, GameDayEnded(gorefest7), PlayerErased(bram)).whenever(log(45)).thenRejects(Rejection.PlayersNotAtGameDay)
    }

    @Test
    @Obligation("PL-3")
    fun `plays can be logged during the game day, from its date in its own time zone`() {
        fun at(instant: String) = log(45).copy(at = Instant.parse(instant))
        // 27 Sep, Europe/Brussels (UTC+2): the day starts at 26 Sep 22:00 UTC.
        LogPlayDecider.given(*joined).whenever(at("2026-09-27T09:00:00Z")).thenEmits(at("2026-09-27T09:00:00Z"))
        LogPlayDecider.given(*joined).whenever(at("2026-09-26T22:00:00Z")).thenEmits(at("2026-09-26T22:00:00Z"))
        LogPlayDecider.given(*joined).whenever(at("2026-09-26T21:59:00Z")).thenRejects(Rejection.GameDayNotStarted)
    }

    @Test
    @Obligation("PL-3")
    fun `moving the game day moves when logging opens`() {
        val moved = GameDayDetailsChanged(gorefest7, "Gorefest #7", today.plusDays(10), "Anna's place")
        LogPlayDecider.given(*joined, moved).whenever(log(45)).thenRejects(Rejection.GameDayNotStarted)
    }

    @Test
    @Obligation("PL-10")
    fun `a logged play means everyone at the table has played it`() {
        val tastes = listOf(log(45)).fold(Tastes.initialState, Tastes::evolve)

        tastes.of(bram).played shouldBe mapOf(azul.id to true)
    }

    @Test
    @Obligation("PL-11")
    fun `group speed is the median ratio to the box time, for mostly the same table`() {
        val games = DevCatalog.games.associateBy { it.id }::get
        val slow = listOf(log(45), log(60), log(40))   // ÷ 30 → 1.5, 2.0, 1.33

        groupSpeed(emptyList(), games, listOf(anna, bram)) shouldBe 1.0
        groupSpeed(slow, games, listOf(anna, bram)) shouldBe 1.5
        groupSpeed(slow, games, listOf(gorefest.core.PlayerId(kotlin.uuid.Uuid.random()))) shouldBe 1.0
        groupSpeed(listOf(log(300)), games, listOf(anna, bram)) shouldBe 1.8
    }

    @Test
    @Obligation("PL-12")
    fun `after a play, compare it with a played game we know least about`() {
        val cascadia = DevCatalog.games.first { it.name == "Cascadia" }
        val brass = DevCatalog.games.first { it.name == "Brass: Birmingham" }
        val player = PlayerGames(listOf(log(45)).fold(Tastes.initialState, Tastes::evolve).of(anna), owned = setOf(cascadia.id))

        Wizard().partner(player, azul.id, listOf(azul, cascadia, brass), now) shouldBe cascadia
        Wizard().partner(player, azul.id, listOf(azul, brass), now) shouldBe null
    }


    @Test
    @Obligation("PL-11")
    fun `games without a playtime don't skew the group's speed`() {
        val unknown = azul.copy(id = BggId(999_001), minPlaytime = 0, maxPlaytime = 0)
        val games = (DevCatalog.games + unknown).associateBy { it.id }::get

        // Estimated at 5 minutes, a 60 minute log would read as 12x and clamp the speed to 1.8.
        groupSpeed(listOf(log(60, game = unknown.id)), games, listOf(anna, bram)) shouldBe 1.0
        groupSpeed(listOf(log(60, game = unknown.id), log(45)), games, listOf(anna, bram)) shouldBe 1.5
    }
}
