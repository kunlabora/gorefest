package gorefest.shell.eventstore

import gorefest.core.PlayerId
import gorefest.core.PlayerJoined
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
