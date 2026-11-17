package gorefest.core.gameday

import gorefest.contract.Obligation
import gorefest.core.Rejection
import gorefest.core.BggId
import gorefest.core.GameDayDetailsChanged
import gorefest.core.GameDayCancelled
import gorefest.core.GameDayEnded
import gorefest.core.PlayLogged
import gorefest.core.PlayRemoved
import gorefest.core.ComparisonRecorded
import gorefest.core.ComparisonResult
import gorefest.core.Basis
import gorefest.core.GamePlayStatusDeclared
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
        LogPlayDecider.given(*joined).whenever(LogPlay(log(45))).thenEmits(log(45))
    }

    @Test
    @Obligation("PL-2", "PL-4")
    fun `logs need joined players, a real duration and a game day that wasn't cancelled`() {
        val stranger = gorefest.core.PlayerId(kotlin.uuid.Uuid.random())
        LogPlayDecider.given(*joined).whenever(LogPlay(log(45, listOf(anna, stranger)))).thenRejects(Rejection.PlayersNotAtGameDay)
        LogPlayDecider.given(*joined).whenever(LogPlay(log(45, emptyList()))).thenRejects(Rejection.NobodyPlayed)
        LogPlayDecider.given(*joined).whenever(LogPlay(log(0))).thenRejects(Rejection.ImplausibleDuration)
        LogPlayDecider.given(*joined, GameDayCancelled(gorefest7)).whenever(LogPlay(log(45))).thenRejects(Rejection.GameDayCancelled)
        LogPlayDecider.given(gameDayCreated()).whenever(LogPlay(log(45))).thenRejects(Rejection.NotJoined)
    }

    @Test
    @Obligation("PL-4")
    fun `a play can still be logged after the game day ended`() {
        LogPlayDecider.given(*joined, GameDayEnded(gorefest7)).whenever(LogPlay(log(45))).thenEmits(log(45))
    }

    @Test
    @Obligation("PL-5", "PR-9")
    fun `a play never names someone who was erased, also on an ended game day that still shows them`() {
        LogPlayDecider.given(*joined, GameDayEnded(gorefest7), PlayerErased(bram)).whenever(LogPlay(log(45))).thenRejects(Rejection.PlayersNotAtGameDay)
    }

    @Test
    @Obligation("PL-3")
    fun `plays can be logged during the game day, from its date in its own time zone`() {
        fun at(instant: String) = log(45).copy(at = Instant.parse(instant))
        // 27 Sep, Europe/Brussels (UTC+2): the day starts at 26 Sep 22:00 UTC.
        LogPlayDecider.given(*joined).whenever(LogPlay(at("2026-09-27T09:00:00Z"))).thenEmits(at("2026-09-27T09:00:00Z"))
        LogPlayDecider.given(*joined).whenever(LogPlay(at("2026-09-26T22:00:00Z"))).thenEmits(at("2026-09-26T22:00:00Z"))
        LogPlayDecider.given(*joined).whenever(LogPlay(at("2026-09-26T21:59:00Z"))).thenRejects(Rejection.GameDayNotStarted)
    }

    @Test
    @Obligation("PL-3")
    fun `moving the game day moves when logging opens`() {
        val moved = GameDayDetailsChanged(gorefest7, "Gorefest #7", today.plusDays(10), "Anna's place")
        LogPlayDecider.given(*joined, moved).whenever(LogPlay(log(45))).thenRejects(Rejection.GameDayNotStarted)
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

    // A table of one by mistake (U23)

    @Test
    @Obligation("PL-20")
    fun `a play with fewer players than the game needs is refused, naming the minimum`() {
        LogPlayDecider.given(*joined).whenever(LogPlay(log(45, listOf(anna)), minPlayers = 2)).thenRejects(Rejection.TooFewPlayers(2))
        LogPlayDecider.given(*joined).whenever(LogPlay(log(45, listOf(anna, anna)), minPlayers = 2)).thenRejects(Rejection.TooFewPlayers(2))
        LogPlayDecider.given(*joined).whenever(LogPlay(log(45), minPlayers = 2)).thenEmits(log(45))
        // A solo game logs with one.
        LogPlayDecider.given(*joined).whenever(LogPlay(log(45, listOf(anna)), minPlayers = 1)).thenEmits(log(45, listOf(anna)))
    }

    private val later = Instant.parse("2026-09-27T13:00:00Z")
    private fun remove(play: PlayLogged, by: gorefest.core.PlayerId) = RemovePlay(play.key, by, later)
    private fun removed(play: PlayLogged, by: gorefest.core.PlayerId) = PlayRemoved(play.gameDay, play.game, play.loggedBy, play.at, play.players, by, later)

    @Test
    @Obligation("PL-22")
    fun `whoever logged a play, or played in it, can remove it`() {
        val solo = log(45, listOf(anna))
        val forBram = PlayLogged(gorefest7, azul.id, listOf(bram), 45, anna, now)

        RemovePlayDecider.given(*joined, solo).whenever(remove(solo, anna)).thenEmits(removed(solo, anna))
        RemovePlayDecider.given(*joined, forBram).whenever(remove(forBram, bram)).thenEmits(removed(forBram, bram))
        RemovePlayDecider.given(*joined, forBram).whenever(remove(forBram, anna)).thenEmits(removed(forBram, anna))
    }

    @Test
    @Obligation("PL-22")
    fun `nobody else can remove a play, nor one that doesn't exist or is on a cancelled game day, and twice changes nothing`() {
        val solo = log(45, listOf(anna))
        val caro = gorefest.core.PlayerId(kotlin.uuid.Uuid.random())

        RemovePlayDecider.given(*joined, solo).whenever(remove(solo, bram)).thenRejects(Rejection.NotYourPlay)
        RemovePlayDecider.given(*joined, solo).whenever(remove(solo, caro)).thenRejects(Rejection.NotYourPlay)
        RemovePlayDecider.given(*joined).whenever(remove(solo, anna)).thenRejects(Rejection.NoSuchPlay)
        RemovePlayDecider.given(*joined, solo.copy(at = later)).whenever(remove(solo, anna)).thenRejects(Rejection.NoSuchPlay)
        RemovePlayDecider.given(*joined, solo, GameDayCancelled(gorefest7)).whenever(remove(solo, anna)).thenRejects(Rejection.GameDayCancelled)
        RemovePlayDecider.given().whenever(remove(solo, anna)).thenRejects(Rejection.NoSuchGameDay)
        RemovePlayDecider.given(*joined, solo, removed(solo, anna)).whenever(remove(solo, anna)).thenEmitsNothing()
        // After the game day ended a play can still be put right, as it can still be logged.
        RemovePlayDecider.given(*joined, solo, GameDayEnded(gorefest7)).whenever(remove(solo, anna)).thenEmits(removed(solo, anna))
    }

    @Test
    @Obligation("PL-23")
    fun `a removed play is gone from the game day and its group speed`() {
        val games = DevCatalog.games.associateBy { it.id }::get
        val slow = log(60).copy(at = later)
        val plays = listOf(log(45), slow, removed(slow, anna)).fold(Plays.initialState, Plays::evolve)

        plays.on(gorefest7) shouldBe listOf(log(45))
        groupSpeed(plays.on(gorefest7), games, listOf(anna, bram)) shouldBe 1.5
    }

    @Test
    @Obligation("PL-23")
    fun `a removed play gives back the played status from before it, and keeps the comparison`() {
        fun tastes(vararg events: gorefest.core.Event) = events.fold(Tastes.initialState, Tastes::evolve)
        val solo = log(45, listOf(anna))
        val compared = ComparisonRecorded(anna, azul.id, BggId(13), ComparisonResult.A, Basis.PLAYED, now)

        // Played only in that play: unplayed again, so its novelty comes back.
        PlayerGames(tastes(solo, compared, removed(solo, anna)).of(anna), emptySet()).hasPlayed(azul.id) shouldBe false
        tastes(solo, compared, removed(solo, anna)).of(anna).comparisons shouldBe listOf(compared)
        // Said "haven't played" before: that is true again.
        tastes(GamePlayStatusDeclared(anna, azul.id, false), solo, removed(solo, anna)).of(anna).played shouldBe mapOf(azul.id to false)
        // Another play still stands.
        val again = solo.copy(at = later)
        tastes(solo, again, removed(solo, anna)).of(anna).played shouldBe mapOf(azul.id to true)
        // Said "haven't played" after the play: that word still wins once the play is gone.
        tastes(solo, GamePlayStatusDeclared(anna, azul.id, false), removed(solo, anna)).of(anna).played shouldBe mapOf(azul.id to false)
    }
}
