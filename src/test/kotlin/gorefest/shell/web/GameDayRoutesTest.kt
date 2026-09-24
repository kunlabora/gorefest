package gorefest.shell.web

import gorefest.shell.App
import gorefest.shell.TestDatabase
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class GameDayRoutesTest {
    private val clock = Clock.fixed(Instant.parse("2026-09-24T10:00:00Z"), ZoneOffset.UTC)

    @BeforeEach
    fun reset() = kotlinx.coroutines.runBlocking { TestDatabase.reset() }

    @Test
    fun `host creates a game day and lands on its page`() = testApplication {
        val app = App(TestDatabase.db, clock).also { it.start() }
        application { web(app) }
        val client = createClient { followRedirects = false }

        val created = client.submitForm("/gamedays", parameters {
            append("title", "Gorefest #7")
            append("date", "2026-09-27")
            append("place", "Anna's place")
            append("timeZone", "Europe/Brussels")
        }) { header("HX-Request", "true") }

        created.status shouldBe HttpStatusCode.NoContent
        val location = created.headers["HX-Redirect"]!!
        val page = client.get(location).bodyAsText()
        page shouldContain "Gorefest #7"
        page shouldContain "Anna's place"
        page shouldContain "(you)"

        client.get("/").bodyAsText() shouldContain "Gorefest #7 — 2026-09-27"
    }

    @Test
    fun `rejections re-render the form with the reason`() = testApplication {
        val app = App(TestDatabase.db, clock).also { it.start() }
        application { web(app) }

        val response = client.submitForm("/gamedays", parameters {
            append("title", "Gorefest #7")
            append("date", "2020-01-01")
        }) { header("HX-Request", "true") }

        response.status shouldBe HttpStatusCode.OK
        response.bodyAsText() shouldContain "That date is in the past"
    }
}
