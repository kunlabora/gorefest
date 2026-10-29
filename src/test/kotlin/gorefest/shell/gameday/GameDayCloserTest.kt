package gorefest.shell.gameday

import gorefest.core.GameBrought
import gorefest.core.GameDayId
import gorefest.core.Tags
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
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.string.shouldContain
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
        app.catchUp()
    }

    @Test
    suspend fun `before midnight nothing closes`() {
        test.clock.now = Instant.parse("2026-09-27T21:59:00Z") // 23:59 in Brussels

        app.closer.closeDue()

        app.gameDaysById.getValue(gorefest7).ended shouldBe false
        test.mailbox.all shouldHaveSize 0
    }

    @Test
    suspend fun `after midnight the game day ends and everyone gets one summary`() {
        test.clock.now = Instant.parse("2026-09-27T22:00:00Z")

        app.closer.closeDue()
        app.closer.closeDue()

        app.gameDaysById.getValue(gorefest7).ended shouldBe true
        test.mailbox.all.map { it.to }.toSet() shouldBe setOf("anna@example.com", "kim@example.com")
        test.mailbox.all shouldHaveSize 2
        val mail = test.mailbox.latestTo("kim@example.com")!!
        mail.subject shouldBe "Gorefest #7: what you played"
        mail.text shouldContain "Hi Kim,"
        mail.text shouldContain "- Cascadia: Anna, Kim · 40 min"
        mail.text shouldContain "1 game, 40 minutes at the table."
    }

    @Test
    suspend fun `one recipient failing does not stop the others getting their recap`() {
        val delivered = mutableListOf<String>()
        val flaky = gorefest.shell.auth.Mailer { mail ->
            if (mail.to == "anna@example.com") error("mailbox full")
            delivered += mail.to
        }
        val recaps = RecapMailer(app.db, app.login.players, { app.plays }, app.catalog, flaky, app.config, test.clock)

        recaps.send(app.gameDaysById.getValue(gorefest7))

        delivered shouldBe listOf("kim@example.com")
    }

    @Test
    suspend fun `one game day failing to close does not block the others`() {
        val broken = GameDayId(Uuid.random())
        app.db.transaction {
            contextOf<gorefest.shell.db.Tx>().connection.prepareStatement("insert into events(type, tags, data, at) values ('GameDayCreated', ?, '{}'::jsonb, now())").use {
                it.setArray(1, contextOf<gorefest.shell.db.Tx>().connection.createArrayOf("text", arrayOf(Tags.gameDay(broken))))
                it.executeUpdate()
            }
        }
        val real = app.gameDaysById.getValue(gorefest7)
        val closer = GameDayCloser(app.commands, { linkedMapOf(broken to real.copy(id = broken), gorefest7 to real) }, RecapMailer(app.db, app.login.players, { app.plays }, app.catalog, test.mailbox, app.config, test.clock), test.clock, app.scope)
        test.clock.now = Instant.parse("2026-09-28T08:00:00Z")

        closer.closeDue()

        // The recap only goes out once gorefest7 was ended (the broken row also stalls the read model's replay).
        test.mailbox.all shouldHaveSize 2
    }

    @Test
    fun `after closing, the shelf is closed too, while a forgotten play can still be logged`() {
        kotlinx.coroutines.runBlocking {
            test.clock.now = Instant.parse("2026-09-28T08:00:00Z")
            app.closer.closeDue()
        }
        test.webTest(session = kim) { client ->
            client.htmxPost("/gamedays/$gorefest7/shelf/$cascadia", parameters { append("bringing", "true") }).status shouldBe HttpStatusCode.Conflict
            client.htmxPost("/gamedays/$gorefest7/log", parameters {
                append("game", cascadia.toString()); append("players", kimId.toString()); append("duration", "30m")
            }).headers["HX-Redirect"]!! shouldStartWith "/gamedays/$gorefest7/log/compare"
        }
    }
}
