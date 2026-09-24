package gorefest.shell.eventstore

import gorefest.core.GameDayCreated
import gorefest.core.GameDayId
import gorefest.core.PlayerJoined
import gorefest.core.Tags
import gorefest.core.eventsourcing.AppendCondition
import gorefest.core.eventsourcing.Query
import gorefest.core.gameday.anna
import gorefest.core.gameday.bram
import gorefest.core.gameday.gameDayCreated
import gorefest.core.gameday.gorefest7
import gorefest.core.typeName
import gorefest.shell.TestDatabase
import gorefest.shell.TestDatabase.db
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

class EventStoreTest {
    private val store = EventStore()
    private val otherGameDay = GameDayId(Uuid.random())

    private val gorefest7Query = Query.of(
        Query.Item(setOf(typeName<GameDayCreated>(), typeName<PlayerJoined>()), setOf(Tags.gameDay(gorefest7))),
    )

    @BeforeEach
    fun reset() = kotlinx.coroutines.runBlocking { TestDatabase.reset() }

    @Test
    suspend fun `reads back only events matching the query, in order`() {
        db.transaction {
            store.append(listOf(gameDayCreated(), gameDayCreated(gameDay = otherGameDay), PlayerJoined(gorefest7, bram)))
        }

        val result = db.transaction { store.read(gorefest7Query) }

        result.events.map { it.event } shouldContainExactly listOf(gameDayCreated(), PlayerJoined(gorefest7, bram))
        result.position shouldBe 3
    }

    @Test
    suspend fun `a query item needs every tag`() {
        db.transaction { store.append(listOf(PlayerJoined(gorefest7, anna), PlayerJoined(gorefest7, bram))) }

        val bramInGorefest7 = Query.of(
            Query.Item(setOf(typeName<PlayerJoined>()), setOf(Tags.gameDay(gorefest7), Tags.player(bram))),
        )

        db.transaction { store.read(bramInGorefest7) }.events.map { it.event } shouldContainExactly
            listOf(PlayerJoined(gorefest7, bram))
    }

    @Test
    suspend fun `append fails when a matching event was appended after the read position`() {
        db.transaction { store.append(listOf(gameDayCreated())) }
        val (_, position) = db.transaction { store.read(gorefest7Query) }

        db.transaction { store.append(listOf(PlayerJoined(gorefest7, anna))) }

        shouldThrow<AppendConditionFailed> {
            db.transaction { store.append(listOf(PlayerJoined(gorefest7, bram)), AppendCondition(gorefest7Query, position)) }
        }
        db.transaction { store.readAll(after = 0) }.size shouldBe 2
    }

    @Test
    suspend fun `events outside the boundary don't conflict`() {
        db.transaction { store.append(listOf(gameDayCreated())) }
        val (_, position) = db.transaction { store.read(gorefest7Query) }

        db.transaction { store.append(listOf(PlayerJoined(otherGameDay, anna))) }
        db.transaction { store.append(listOf(PlayerJoined(gorefest7, bram)), AppendCondition(gorefest7Query, position)) }

        db.transaction { store.readAll(after = 0) }.size shouldBe 3
    }
}
