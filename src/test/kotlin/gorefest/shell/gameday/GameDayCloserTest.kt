package gorefest.shell.gameday

import gorefest.core.GameBrought
import gorefest.core.PlayLogged
import gorefest.core.PlayerId
import gorefest.core.PlayerJoined
import gorefest.core.gameday.anna
import gorefest.core.gameday.gameDayCreated
import gorefest.core.gameday.gorefest7
import gorefest.shell.bgg.DevCatalog
import gorefest.shell.web.TestApp
import gorefest.shell.web.htmxPost
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.uuid.Uuid

class GameDayCloserTest {
    private val test = TestApp()
    private val app = test.app
    private val kimId = PlayerId(Uuid.random())
    private lateinit var kim: String
    private val cascadia = DevCatalog.games.first { it.name == "Cascadia" }.id

    @BeforeEach
    fun reset(): Unit = kotlinx.coroutines.runBlocking {
        test.reset()
        test.loggedIn("Anna", id = anna)
        kim = test.loggedIn("Kim", id = kimId)
        app.catalog.put(DevCatalog.games, test.clock.instant())
        app.db.transaction {
            app.store.append(listOf(
                gameDayCreated(host = anna), PlayerJoined(gorefest7, anna), PlayerJoined(gorefest7, kimId), GameBrought(gorefest7, anna, cascadia),
                PlayLogged(gorefest7, cascadia, listOf(anna, kimId), 40, kimId, Instant.parse("2026-09-27T11:00:00Z")),
            ))
        }
        app.gameDays.catchUp(); app.shelves.catchUp(); app.plays.catchUp()
    }

    @Test
    suspend fun `before midnight nothing closes`() {
        test.clock.now = Instant.parse("2026-09-27T21:59:00Z") // 23:59 in Brussels

        app.closer.closeDue()

        app.gameDays.state.getValue(gorefest7).ended shouldBe false
        test.mailbox.all shouldHaveSize 0
    }

    @Test
    suspend fun `after midnight the game day ends and everyone gets one summary`() {
        test.clock.now = Instant.parse("2026-09-27T22:00:00Z")

        app.closer.closeDue()
        app.closer.closeDue()

        app.gameDays.state.getValue(gorefest7).ended shouldBe true
        test.mailbox.all.map { it.to }.toSet() shouldBe setOf("anna@example.com", "kim@example.com")
        test.mailbox.all shouldHaveSize 2
        val mail = test.mailbox.latestTo("kim@example.com")!!
        mail.subject shouldBe "Gorefest #7: what you played"
        mail.text shouldContain "Hi Kim,"
        mail.text shouldContain "- Cascadia: Anna, Kim · 40 min"
        mail.text shouldContain "1 game, 40 minutes at the table."
    }

    @Test
    fun `after closing, the shelf and play log are closed too`() {
        kotlinx.coroutines.runBlocking {
            test.clock.now = Instant.parse("2026-09-28T08:00:00Z")
            app.closer.closeDue()
        }
        test.webTest(session = kim) { client ->
            client.htmxPost("/gamedays/$gorefest7/shelf/$cascadia", parameters { append("bringing", "true") }).status shouldBe HttpStatusCode.Conflict
            val refused = client.htmxPost("/gamedays/$gorefest7/log", parameters {
                append("game", cascadia.toString()); append("players", kimId.toString()); append("duration", "30m")
            })
            org.jsoup.Jsoup.parse(refused.bodyAsText()).select("[role=alert]").text() shouldBe "This game day has ended"
        }
    }
}
