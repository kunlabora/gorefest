package gorefest.shell.web

import gorefest.core.GameDayEnded
import gorefest.core.PlayerJoined
import gorefest.core.gameday.anna
import gorefest.core.gameday.bram
import gorefest.core.gameday.gameDayCreated
import gorefest.core.gameday.gorefest7
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GameDayRoutesTest {
    private val test = TestApp()
    private val app = test.app
    private lateinit var tim: String

    @BeforeEach
    fun reset(): Unit = kotlinx.coroutines.runBlocking {
        test.reset()
        tim = test.loggedIn("Tim")
        test.loggedIn("Anna", id = anna)
    }

    /** Requests as Tim. */
    private fun webTest(block: suspend io.ktor.server.testing.ApplicationTestBuilder.(HttpClient) -> Unit) = test.webTest(session = tim, block = block)

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
        page.one(".gf-kicker").text() shouldBe "Sun 27 Sep 2026 · Anna's place"
        page.players() shouldContainExactly listOf("Tim You Host")
        page.select("#players button").shouldBeEmpty()
    }

    @Test
    fun `the home page lists game days by date, linking to each`() = webTest { client ->
        client.htmxPost("/gamedays", validForm)

        val card = client.get("/").page().one("a.gf-daycard")

        card.attr("href") shouldBe "/gamedays/00000000-0000-0000-0000-000000000001"
        card.one(".gf-date").text() shouldBe "Sep 27"
        card.one(".gf-daycard__title").text() shouldBe "Gorefest #7"
        card.one(".gf-daycard__meta").text() shouldBe "Anna's place · 1 going"
    }

    @Test
    fun `the home page without game days explains how to get one`() = webTest { client ->
        val page = client.get("/").page()

        page.one(".gf-empty__title").text() shouldBe "No game days yet"
        page.one(".gf-dock a").attr("href") shouldBe "/gamedays/new"
    }

    @Test
    fun `hosting starts from a form that posts with htmx`() = webTest { client ->
        val form = client.get("/gamedays/new").page().one("form")

        form.attr("hx-post") shouldBe "/gamedays"
        form.select("input.gf-input").map { it.attr("name") } shouldContainExactly listOf("title", "date", "place")
        form.one("label[for=title]").text() shouldBe "Title"
    }

    @Test
    fun `the design system stylesheets are served`() = webTest { client ->
        listOf("/static/css/tokens.css", "/static/css/gorefest.css").forEach { url ->
            val response = client.get(url)
            response.status shouldBe HttpStatusCode.OK
            response.headers["Content-Type"]!!.startsWith("text/css") shouldBe true
        }
    }

    @Test
    fun `a rejection re-renders the form with the reason and what was typed`() = webTest { client ->
        val form = client.htmxPost("/gamedays", parameters {
            append("title", "Gorefest #7")
            append("date", "2020-01-01")
        }).fragment()

        form.tagName() shouldBe "form"
        form.one("[role=alert]").text() shouldBe "That date is in the past"
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
        players.players() shouldContainExactly listOf("Anna Host", "Tim You")
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

    @Test
    fun `players show as initials and names`() = webTest { client ->
        given(gameDayCreated(host = anna), PlayerJoined(gorefest7, anna))

        client.get("/gamedays/$gorefest7").page().one("#players .gf-avatar").text() shouldBe "A"
    }

    @Test
    fun `players without a name show as unknown`() = webTest { client ->
        given(gameDayCreated(host = bram), PlayerJoined(gorefest7, bram))

        val page = client.get("/gamedays/$gorefest7").page()

        page.players() shouldContainExactly listOf("Unknown player Host")
        page.one("#players .gf-avatar").text() shouldBe "?"
    }

    @Test
    fun `the home page has a way out`() = webTest { client ->
        client.get("/").page().one("form[action=/logout] button").text() shouldBe "Log out"
    }

    /** One line per player row: name, "You" for the viewer, "Host" tag. */
    private fun org.jsoup.nodes.Element.players() =
        select("#players li").map { row -> row.select(".gf-row__title, .gf-row__sub, .gf-tag").joinToString(" ") { it.text() } }
}
