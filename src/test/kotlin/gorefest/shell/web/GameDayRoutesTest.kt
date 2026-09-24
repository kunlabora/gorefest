package gorefest.shell.web

import gorefest.core.GameDayEnded
import gorefest.core.PlayerJoined
import gorefest.core.gameday.anna
import gorefest.core.gameday.gameDayCreated
import gorefest.core.gameday.gorefest7
import gorefest.shell.App
import gorefest.shell.IdGenerator
import gorefest.shell.TestDatabase
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.parameters
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class GameDayRoutesTest {
    private val clock = Clock.fixed(Instant.parse("2026-09-24T10:00:00Z"), ZoneOffset.UTC)
    private val app = App(TestDatabase.db, clock, IdGenerator.sequential())

    @BeforeEach
    fun reset() = kotlinx.coroutines.runBlocking {
        TestDatabase.reset()
        app.start()
    }

    private fun webTest(block: suspend ApplicationTestBuilder.(HttpClient) -> Unit) = testApplication {
        application { web(app) }
        block(createClient { followRedirects = false })
    }

    private suspend fun given(vararg events: gorefest.core.Event) {
        app.db.transaction { app.store.append(events.toList()) }
        app.gameDays.catchUp()
    }

    private val validForm = parameters {
        append("title", "Gorefest #7")
        append("date", "2026-09-27")
        append("place", "Anna's place")
        append("timeZone", "Europe/Brussels")
    }

    private suspend fun HttpClient.htmxPost(url: String, form: Parameters = Parameters.Empty) =
        submitForm(url, form) { header("HX-Request", "true") }

    @Test
    fun `creating a game day redirects htmx to its page`() = webTest { client ->
        val response = client.htmxPost("/gamedays", validForm)

        response.status shouldBe HttpStatusCode.NoContent
        response.headers["HX-Redirect"] shouldBe "/gamedays/00000000-0000-0000-0000-000000000001"
    }

    @Test
    fun `creating a game day without htmx redirects the browser`() = webTest { client ->
        val response = client.submitForm("/gamedays", validForm)

        response.status shouldBe HttpStatusCode.Found
        response.headers["Location"] shouldBe "/gamedays/00000000-0000-0000-0000-000000000001"
    }

    @Test
    fun `the new game day shows its details, with the host as only player`() = webTest { client ->
        client.htmxPost("/gamedays", validForm)

        val page = client.get("/gamedays/00000000-0000-0000-0000-000000000001").page()

        page.one("h1").text() shouldBe "Gorefest #7"
        page.one("main > p").text() shouldBe "2026-09-27 · Anna's place"
        page.texts("#players li") shouldContainExactly listOf("00000000 host (you)")
        page.select("#players button").shouldBeEmpty()
    }

    @Test
    fun `the home page lists game days by date, linking to each`() = webTest { client ->
        client.htmxPost("/gamedays", validForm)

        val link = client.get("/").page().one("ul li a")

        link.text() shouldBe "Gorefest #7 — 2026-09-27"
        link.attr("href") shouldBe "/gamedays/00000000-0000-0000-0000-000000000001"
    }

    @Test
    fun `a rejection re-renders the form with the reason and what was typed`() = webTest { client ->
        val form = client.htmxPost("/gamedays", parameters {
            append("title", "Gorefest #7")
            append("date", "2020-01-01")
        }).fragment()

        form.tagName() shouldBe "form"
        form.one("p").text() shouldBe "That date is in the past"
        form.one("input[name=title]").`val`() shouldBe "Gorefest #7"
        form.one("input[name=date]").`val`() shouldBe "2020-01-01"
    }

    @Test
    fun `a player who hasn't joined sees a join button that swaps the player list`() = webTest { client ->
        given(gameDayCreated(host = anna), PlayerJoined(gorefest7, anna))

        val page = client.get("/gamedays/$gorefest7").page()

        val join = page.one("#players button")
        join.attr("hx-post") shouldBe "/gamedays/$gorefest7/join"
        join.attr("hx-target") shouldBe "#players"
        join.attr("hx-swap") shouldBe "outerHTML"
    }

    @Test
    fun `joining returns the updated player list`() = webTest { client ->
        given(gameDayCreated(host = anna), PlayerJoined(gorefest7, anna))

        val players = client.htmxPost("/gamedays/$gorefest7/join").fragment()

        players shouldReplace "players"
        players.texts("li") shouldContainExactly listOf("${anna.toString().take(8)} host", "00000000 (you)")
        players.select("button").shouldBeEmpty()
    }

    @Test
    fun `joining an ended game day explains why`() = webTest { client ->
        given(gameDayCreated(host = anna), PlayerJoined(gorefest7, anna), GameDayEnded(gorefest7))

        val response = client.htmxPost("/gamedays/$gorefest7/join").fragment()

        response shouldReplace "players"
        response.text() shouldBe "This game day has ended"
    }

    @Test
    fun `unknown game days are not found`() = webTest { client ->
        client.get("/gamedays/00000000-0000-0000-0000-00000000beef").status shouldBe HttpStatusCode.NotFound
        client.get("/gamedays/not-a-uuid").status shouldBe HttpStatusCode.NotFound
        client.post("/gamedays/not-a-uuid/join").status shouldBe HttpStatusCode.NotFound
    }

    @Test
    fun `joining an unknown game day explains why`() = webTest { client ->
        val response = client.htmxPost("/gamedays/00000000-0000-0000-0000-00000000beef/join").fragment()

        response shouldReplace "players"
        response.text() shouldBe "No such game day"
    }
}
