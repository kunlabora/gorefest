package gorefest.shell.auth

import gorefest.core.GameDayCreated
import gorefest.core.PlayerId
import gorefest.core.PlayerJoined
import gorefest.core.GameDayId
import gorefest.core.GameBrought
import gorefest.core.BggAccountLinked
import gorefest.core.BggId
import gorefest.core.PlayerErased
import gorefest.shell.TestDatabase
import gorefest.shell.db.Tx
import gorefest.shell.web.TestApp
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.uuid.Uuid

class AccountEraserTest {
    private val test = TestApp()
    private val app = test.app
    private val kim = PlayerId(Uuid.random())
    private val anna = PlayerId(Uuid.random())
    private val day = GameDayId(Uuid.random())

    @BeforeEach
    fun reset() = runBlocking { test.reset() }

    private suspend fun playersAtATable() {
        test.loggedIn("Kim", kim)
        test.loggedIn("Anna", anna)
        app.login.request("kim@example.com", "/", "a")
        app.db.transaction {
            app.store.append(
                listOf(
                    gorefest.core.PlayerRegistered(kim), gorefest.core.PlayerRegistered(anna),
                    BggAccountLinked(kim, "KimPlays"),
                    GameDayCreated(day, anna, "Gorefest #7", LocalDate.now().plusDays(3), "Anna's place", "Europe/Brussels"),
                    PlayerJoined(day, anna), PlayerJoined(day, kim), GameBrought(day, kim, BggId(1)),
                ),
            )
        }
        app.catchUp()
    }

    private suspend fun rows(sql: String): List<String> = TestDatabase.db.transaction {
        contextOf<Tx>().connection.createStatement().use { st ->
            st.executeQuery(sql).use { rs -> buildList { while (rs.next()) add(rs.getString(1)) } }
        }
    }

    @Test
    fun `erasing removes every row that says who they were and keeps everyone else's`() = runBlocking<Unit> {
        playersAtATable()

        app.accountEraser.erase(kim) shouldBe AccountEraser.Result.Erased

        rows("select email from players") shouldBe listOf("anna@example.com")
        rows("select count(*)::text from sessions where player_id = '$kim'") shouldBe listOf("0")
        rows("select count(*)::text from login_attempts where email = 'kim@example.com'") shouldBe listOf("0")
        rows("select count(*)::text from sessions where player_id = '$anna'") shouldBe listOf("1")
        // Nothing in the plain tables or the log spells out the email or the person's name.
        val everything = buildList { listOf("players", "sessions", "login_attempts", "collection_syncs", "collections").forEach { addAll(rows("select t::text from $it t")) } }
            .joinToString("\n")
        everything shouldNotContain "kim@example.com"
        everything shouldNotContain "Kim"
    }

    @Test
    fun `the log is not rewritten, an erasure is one more event`() = runBlocking<Unit> {
        playersAtATable()
        val before = rows("select data::text from events order by seq")

        app.accountEraser.erase(kim)

        val after = rows("select data::text from events order by seq")
        after.take(before.size) shouldBe before
        after.size shouldBe before.size + 1
        after.last() shouldBe """{"type": "PlayerErased", "player": "$kim"}"""
    }

    @Test
    fun `the erased player leaves the game day and the others stay`() = runBlocking<Unit> {
        playersAtATable()

        app.accountEraser.erase(kim)

        app.gameDaysById.getValue(day).players shouldBe listOf(anna)
        app.bggAccounts.isLinked(kim) shouldBe false
    }

    @Test
    fun `erasing again, or somebody unknown, changes nothing`() = runBlocking<Unit> {
        playersAtATable()

        app.accountEraser.erase(kim) shouldBe AccountEraser.Result.Erased
        val events = rows("select count(*)::text from events")
        app.accountEraser.erase(kim) shouldBe AccountEraser.Result.Erased
        rows("select count(*)::text from events") shouldBe events
        app.accountEraser.erase(PlayerId(Uuid.random())) shouldBe AccountEraser.Result.NoSuchPlayer
    }

    @Test
    fun `the same email can sign up again afterwards as a new person`() = runBlocking<Unit> {
        playersAtATable()
        app.accountEraser.erase(kim)

        test.loggedIn("Kim", PlayerId(Uuid.random()))

        rows("select count(*)::text from players where email = 'kim@example.com'") shouldBe listOf("1")
    }
}
