package gorefest.shell.eventstore

import gorefest.core.PlayerId
import gorefest.core.PlayerJoined
import gorefest.core.GameDayId
import gorefest.core.eventsourcing.Query
import gorefest.core.Event
import gorefest.core.eventsourcing.Decision
import gorefest.core.gameday.GameDays
import gorefest.core.gameday.JoinGameDay
import gorefest.core.gameday.JoinGameDayDecider
import gorefest.core.gameday.gameDayCreated
import gorefest.core.gameday.gorefest7
import gorefest.shell.TestDatabase
import gorefest.shell.TestDatabase.db
import gorefest.shell.eventstore.EventStore
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

class CommandHandlerTest {
    private val store = EventStore()
    private val gameDays = Projector(GameDays, db, store)
    private val commands = CommandHandler(db, store, afterAppend = { gameDays.catchUp() })

    @BeforeEach
    fun reset() = kotlinx.coroutines.runBlocking {
        TestDatabase.reset()
        db.transaction { store.append(listOf(gameDayCreated())) }
    }

    /** Wraps [JoinGameDayDecider] but sneaks a conflicting event in before each of the first [conflicts] decisions. */
    private inner class Conflicting(var conflicts: Int) : gorefest.core.eventsourcing.Decider<JoinGameDay, JoinGameDayDecider.State, PlayerJoined> by JoinGameDayDecider {
        override fun decide(command: JoinGameDay, state: JoinGameDayDecider.State): Decision<PlayerJoined> {
            if (conflicts-- > 0) kotlinx.coroutines.runBlocking { db.transaction { store.append(listOf(gameDayCreated())) } }
            return JoinGameDayDecider.decide(command, state)
        }
    }

    @Test
    suspend fun `keeps retrying through several conflicts, backing off between them`() {
        val backoffs = mutableListOf<Int>()
        val handler = CommandHandler(db, store, afterAppend = { gameDays.catchUp() }, backoff = { backoffs += it })
        val bram = PlayerId(Uuid.random())

        val decision = handler.handle(Conflicting(6), JoinGameDay(gorefest7, bram))

        decision shouldBe Decision.Accepted(PlayerJoined(gorefest7, bram))
        backoffs shouldBe listOf(0, 1, 2, 3, 4, 5)
    }

    @Test
    suspend fun `a failing catch-up does not make the stored command look failed`() {
        val handler = CommandHandler(db, store, afterAppend = { error("projection down") }, backoff = {})
        val bram = PlayerId(Uuid.random())

        val decision = handler.handle(JoinGameDayDecider, JoinGameDay(gorefest7, bram))

        decision shouldBe Decision.Accepted(PlayerJoined(gorefest7, bram))
        db.transaction { store.readAll(after = 0) }.count { it.event is PlayerJoined } shouldBe 1
    }

    @Test
    suspend fun `a poison event is skipped by the projector and later events still apply`() {
        val poison = object : gorefest.core.eventsourcing.Projection<Int> {
            override val initialState = 0
            override fun evolve(state: Int, event: Event) = when (event) {
                is PlayerJoined -> if (event.player == poisoned) error("poison") else state + 1
                else -> state
            }
        }
        val projector = Projector(poison, db, store)
        db.transaction { store.append(listOf(PlayerJoined(gorefest7, poisoned), PlayerJoined(gorefest7, PlayerId(Uuid.random())))) }

        projector.catchUp()
        db.transaction { store.append(listOf(PlayerJoined(gorefest7, PlayerId(Uuid.random())))) }
        projector.catchUp()

        projector.state shouldBe 2
    }

    private val poisoned = PlayerId(Uuid.random())

    @Test
    suspend fun `concurrent joins by different players all land`() {
        val players = List(8) { PlayerId(Uuid.random()) }

        coroutineScope {
            players.map { async { commands.handle(JoinGameDayDecider, JoinGameDay(gorefest7, it)) } }.awaitAll()
        }

        gameDays.state.getValue(gorefest7).players shouldContainExactlyInAnyOrder players
    }

    @Test
    suspend fun `concurrent duplicate joins land once`() {
        val bram = PlayerId(Uuid.random())

        val decisions = coroutineScope {
            List(8) { async { commands.handle(JoinGameDayDecider, JoinGameDay(gorefest7, bram)) } }.awaitAll()
        }

        decisions.filter { it == Decision.Accepted(PlayerJoined(gorefest7, bram)) } shouldHaveSize 1
        db.transaction { store.readAll(after = 0) }.count { it.event is PlayerJoined } shouldBe 1
    }
}
