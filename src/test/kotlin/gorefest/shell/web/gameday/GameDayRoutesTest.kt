package gorefest.shell.web.gameday

import gorefest.core.PlayerId
import gorefest.core.PlayerJoined
import gorefest.core.gameday.anna
import gorefest.core.gameday.bram
import gorefest.core.gameday.gameDayCreated
import gorefest.core.gameday.gorefest7
import gorefest.shell.web.TestApp
import gorefest.shell.web.components.avatar
import gorefest.shell.web.components.icon
import gorefest.shell.web.components.initials
import gorefest.shell.web.components.page
import gorefest.shell.web.fragment
import gorefest.shell.web.htmxPost
import gorefest.shell.web.one
import gorefest.shell.web.page
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

class GameDayRoutesTest {
    private val test = TestApp()
    private val app = test.app
    private val timId = PlayerId(Uuid.random())
    private lateinit var tim: String

    @BeforeEach
    fun reset(): Unit = kotlinx.coroutines.runBlocking {
        test.reset()
        tim = test.loggedIn("Tim", id = timId)
        test.loggedIn("Anna", id = anna)
    }

    /** Requests as Tim. */
    private fun webTest(block: suspend io.ktor.server.testing.ApplicationTestBuilder.(HttpClient) -> Unit) = test.webTest(session = tim, block = block)

    private suspend fun given(vararg events: gorefest.core.Event) {
        app.db.transaction { app.store.append(events.toList()) }
        app.catchUp()
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
        page.one("main > .gf-stack > .gf-kicker").text() shouldBe "Sun 27 Sep 2026 · Anna's place"
        page.players() shouldContainExactly listOf("Tim Bringing nothing yet You Host")
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
    fun `the saved theme is applied before anything else loads`() = webTest { client ->
        val head = client.get("/").page().head()

        head.select("script, link[rel=stylesheet]").first()!!.attr("src") shouldBe "/static/js/theme.js"
        client.get("/static/js/theme.js").let { (it.status to it.headers["Content-Type"]!!.startsWith("text/javascript")) shouldBe (HttpStatusCode.OK to true) }
    }

    @Test
    fun `every page ends its app bar with the light and dark toggle`() = webTest { client ->
        client.htmxPost("/gamedays", validForm)
        val pages = listOf("/", "/gamedays/new", "/gamedays/00000000-0000-0000-0000-000000000001", "/gamedays/00000000-0000-0000-0000-000000000001/edit")

        pages.forEach { url ->
            val toggle = client.get(url).page().one(".gf-appbar > :last-child")
            toggle.hasClass("gf-theme-toggle") shouldBe true
            toggle.attr("_") shouldBe "on click call gorefestToggleTheme()"
            toggle.attr("aria-label") shouldBe "Dark mode"
            toggle.select(".gf-theme-toggle__moon svg, .gf-theme-toggle__sun svg").size shouldBe 2
        }
    }

    @Test
    fun `logged-out pages have the toggle too, and the CSS picks the right icon`() = test.webTest { client ->
        client.get("/login").page().select(".gf-appbar .gf-theme-toggle").size shouldBe 1
        client.get("/static/css/gorefest.css").bodyAsText() shouldContain "[data-theme=\"dark\"] .gf-theme-toggle__sun"
    }

    @Test
    fun `health checks the database`() = webTest { client ->
        client.get("/health").let { (it.status to it.bodyAsText()) shouldBe (HttpStatusCode.OK to "ok") }
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
    fun `unknown game days are not found`() = webTest { client ->
        client.get("/gamedays/00000000-0000-0000-0000-00000000beef").status shouldBe HttpStatusCode.NotFound
        client.get("/gamedays/not-a-uuid").status shouldBe HttpStatusCode.NotFound
    }

    @Test
    fun `players show as initials and names`() = webTest { client ->
        given(gameDayCreated(host = anna), PlayerJoined(gorefest7, anna), PlayerJoined(gorefest7, timId))

        client.get("/gamedays/$gorefest7").page().select("#players .gf-avatar").map { it.text() } shouldContainExactly listOf("A", "T")
    }

    @Test
    fun `players without a name show as unknown`() = webTest { client ->
        given(gameDayCreated(host = bram), PlayerJoined(gorefest7, bram), PlayerJoined(gorefest7, timId))

        val page = client.get("/gamedays/$gorefest7").page()

        page.players() shouldContainExactly listOf("Unknown player Bringing nothing yet Host", "Tim Bringing nothing yet You")
        page.select("#players .gf-avatar").first()!!.text() shouldBe "?"
    }

    @Test
    fun `the home page has a way out`() = webTest { client ->
        client.get("/").page().one("form[action=/logout] button").text() shouldBe "Log out"
    }

    @Test
    fun `the home page only shows game days I host or joined`() = webTest { client ->
        given(gameDayCreated(host = anna), PlayerJoined(gorefest7, anna))

        client.get("/").page().select("a.gf-daycard").shouldBeEmpty()
    }

    @Test
    fun `the host can edit the details`() = webTest { client ->
        client.htmxPost("/gamedays", validForm)
        val id = "00000000-0000-0000-0000-000000000001"

        val page = client.get("/gamedays/$id").page()
        page.one(".gf-appbar a[href=/gamedays/$id/edit]").text() shouldBe "Edit"

        val form = client.get("/gamedays/$id/edit").page().one("form")
        form.attr("hx-post") shouldBe "/gamedays/$id/edit"
        form.one("input[name=title]").`val`() shouldBe "Gorefest #7"
        form.select("input[name=timeZone]").shouldBeEmpty()

        val saved = client.htmxPost("/gamedays/$id/edit", parameters {
            append("title", "Gorefest #7½")
            append("date", "2026-09-28")
            append("place", "Bram's attic")
        })
        saved.headers["HX-Redirect"] shouldBe "/gamedays/$id"
        val after = client.get("/gamedays/$id").page()
        after.one("h1").text() shouldBe "Gorefest #7½"
        after.one("main > .gf-stack > .gf-kicker").text() shouldBe "Mon 28 Sep 2026 · Bram's attic"
    }

    @Test
    fun `an edit rejection keeps what was typed`() = webTest { client ->
        client.htmxPost("/gamedays", validForm)

        val form = client.htmxPost("/gamedays/00000000-0000-0000-0000-000000000001/edit", parameters {
            append("title", "")
            append("date", "2026-09-28")
        }).fragment()

        form.one("[role=alert]").text() shouldBe "Give the game day a title"
        form.one("input[name=date]").`val`() shouldBe "2026-09-28"
    }

    @Test
    fun `other players can't edit`() = webTest { client ->
        given(gameDayCreated(host = anna), PlayerJoined(gorefest7, anna), PlayerJoined(gorefest7, timId))

        client.get("/gamedays/$gorefest7").page().select(".gf-appbar a[href$=/edit]").shouldBeEmpty()
        client.get("/gamedays/$gorefest7/edit").status shouldBe HttpStatusCode.NotFound
        client.htmxPost("/gamedays/$gorefest7/edit", validForm).fragment().one("[role=alert]").text() shouldBe
            "Only the host can change this game day"
    }

    /** One line per player row: name, how many games they bring, "You" for the viewer, "Host" tag. */
    private fun org.jsoup.nodes.Element.players() =
        select("#players li").map { row -> row.select(".gf-row__title, .gf-row__sub, .gf-tag").joinToString(" ") { it.text() } }

    @Test
    fun `every page configures htmx right after loading it, so rejections get swapped in`() = webTest { client ->
        val scripts = client.get("/").page().head().select("script[src]").map { it.attr("src") }
        scripts.indexOf("/static/js/htmx-config.js") shouldBe scripts.indexOfFirst { it.contains("htmx") } + 1
        val js = client.get("/static/js/htmx-config.js").bodyAsText()
        js shouldContain "responseHandling"
        js shouldContain "40[39]"
    }

    @Test
    fun `strangers get a 404 for editing or inviting, not a rejection that confirms the game day exists`() = webTest { client ->
        given(gameDayCreated(host = anna), PlayerJoined(gorefest7, anna))

        client.htmxPost("/gamedays/$gorefest7/edit", validForm).status shouldBe HttpStatusCode.NotFound
        client.htmxPost("/gamedays/$gorefest7/invite").status shouldBe HttpStatusCode.NotFound
    }
}
