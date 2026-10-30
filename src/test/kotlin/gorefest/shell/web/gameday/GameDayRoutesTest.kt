package gorefest.shell.web.gameday

import gorefest.contract.Obligation
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
import gorefest.shell.web.shouldReplace
import gorefest.shell.gameday.TitleGenerator
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.collections.shouldBeIn
import gorefest.shell.web.page
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
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
    @Obligation("GD-1")
    fun `creating a game day redirects htmx to its page`() = webTest { client ->
        val response = client.htmxPost("/gamedays", validForm)

        response.status shouldBe HttpStatusCode.NoContent
        response.headers["HX-Redirect"] shouldBe "/gamedays/00000000-0000-0000-0000-000000000001"
    }

    @Test
    @Obligation("GD-2", "GD-3")
    fun `a missing or garbled date asks for a date, not a later one`() = webTest { client ->
        val noDate = parameters { append("title", "T"); append("place", "x") }
        client.htmxPost("/gamedays", noDate).fragment().one("[role=alert]").text() shouldContain "Pick a date"
        client.htmxPost("/gamedays", parameters { append("title", "T"); append("date", "garbage") }).fragment().one("[role=alert]").text() shouldContain "Pick a date"
    }

    @Test
    @Obligation("GD-2", "GD-3")
    fun `a title or place that is too long is refused in the form`() = webTest { client ->
        val long = Parameters.build { appendAll(validForm); set("title", "A".repeat(5000)) }
        client.htmxPost("/gamedays", long).fragment().one("[role=alert]").text() shouldContain "title is a bit long"
        val longPlace = Parameters.build { appendAll(validForm); set("place", "A".repeat(5000)) }
        client.htmxPost("/gamedays", longPlace).fragment().one("[role=alert]").text() shouldContain "place is a bit long"
        client.get("/gamedays/new").page().one("input#title").attr("maxlength") shouldBe "80"
    }

    @Test
    @Obligation("GD-3")
    fun `a rejected plain form post answers with a full page, not a bare form`() = webTest { client ->
        val page = client.submitForm("/gamedays", Parameters.Empty).page()

        page.select("link[rel=stylesheet]").shouldHaveSize(2)
        page.one("form[action=/gamedays] [role=alert]")
    }

    @Test
    fun `a post without a form body is a bad request, not a server error`() = webTest { client ->
        client.post("/gamedays").status shouldBe HttpStatusCode.BadRequest
        client.post("/gamedays") {
            header("Content-Type", "application/json")
            setBody("{}")
        }.status.value shouldBeIn listOf(400, 415)
    }

    @Test
    @Obligation("GD-1")
    fun `creating a game day without htmx redirects the browser`() = webTest { client ->
        val response = client.submitForm("/gamedays", validForm)

        response.status shouldBe HttpStatusCode.Found
        response.headers["Location"] shouldBe "/gamedays/00000000-0000-0000-0000-000000000001"
    }

    @Test
    @Obligation("GD-1", "GD-13", "GD-20")
    fun `the new game day shows its details, with the host as only player`() = webTest { client ->
        client.htmxPost("/gamedays", validForm)

        val page = client.get("/gamedays/00000000-0000-0000-0000-000000000001").page()

        page.one("h1").text() shouldBe "Gorefest #7"
        page.select(".gf-hubfacts li").map { it.text() } shouldContainExactly listOf("Sun 27 Sep 2026", "Anna's place", "1 going")
        page.players() shouldContainExactly listOf("Tim Bringing nothing yet You Host")
        page.select("#players button").shouldBeEmpty()
    }

    @Test
    @Obligation("GD-21")
    fun `the home page makes the next game day the hero, with the step I still owe it as the one brand button`() = webTest { client ->
        client.htmxPost("/gamedays", validForm)

        val page = client.get("/").page()
        val hero = page.one(".gf-homeday")

        hero.one(".gf-homeday__title a").attr("href") shouldBe "/gamedays/00000000-0000-0000-0000-000000000001"
        hero.one(".gf-homeday__title").text() shouldBe "Gorefest #7"
        hero.one(".gf-date").text() shouldBe "Sep 27"
        hero.one(".gf-homeday__meta").text() shouldBe "Anna's place · 1 going"
        hero.one(".gf-tag--accent").text() shouldBe "Taste round not done"
        page.one(".gf-btn--primary").attr("href") shouldBe "/gamedays/00000000-0000-0000-0000-000000000001/taste"
        page.select(".gf-btn--primary") shouldHaveSize 1
        page.one(".gf-home__aside a[href=/gamedays/new]").text() shouldBe "Host a game day"
        page.select("a.gf-daycard").shouldBeEmpty()
    }

    @Test
    @Obligation("GD-21")
    fun `later game days wait in the coming up band, linking to each`() = webTest { client ->
        client.htmxPost("/gamedays", validForm)
        client.htmxPost("/gamedays", Parameters.build {
            append("title", "Boardfest #8")
            append("date", "2026-10-25")
            append("place", "Bram's attic")
            append("timeZone", "Europe/Brussels")
        })

        val page = client.get("/").page()

        page.one(".gf-homeday__title").text() shouldBe "Gorefest #7"
        val card = page.one(".gf-home__list a.gf-daycard")
        card.attr("href") shouldBe "/gamedays/00000000-0000-0000-0000-000000000002"
        card.one(".gf-date").text() shouldBe "Oct 25"
        card.one(".gf-daycard__title").text() shouldBe "Boardfest #8"
        card.one(".gf-daycard__meta").text() shouldBe "Bram's attic · 1 going"
    }

    @Test
    @Obligation("GD-26")
    fun `a cancelled game day sits under Past, not Coming up`() = webTest { client ->
        client.htmxPost("/gamedays", validForm)
        client.htmxPost("/gamedays", Parameters.build {
            append("title", "Boardfest #8")
            append("date", "2026-10-25")
            append("place", "Bram's attic")
            append("timeZone", "Europe/Brussels")
        })
        val first = "00000000-0000-0000-0000-000000000001"
        client.submitForm("/gamedays/$first/cancel", parameters {})

        val page = client.get("/").page()

        page.one(".gf-homeday__title").text() shouldBe "Boardfest #8"
        page.select(".gf-home__coming a.gf-daycard").shouldBeEmpty()
        page.one(".gf-home__past a.gf-daycard").one(".gf-tag").text() shouldBe "Cancelled"
    }

    @Test
    @Obligation("GD-22")
    fun `the home page without game days explains how to get one`() = webTest { client ->
        val page = client.get("/").page()

        page.one(".gf-empty__title").text() shouldBe "No game days yet"
        page.one(".gf-home__aside a.gf-btn--primary").attr("href") shouldBe "/gamedays/new"
        page.one(".gf-home__aside a[href=/polls/new]").text() shouldBe "Find a date"
    }

    @Test
    fun `hosting starts from a form that posts with htmx`() = webTest { client ->
        val form = client.get("/gamedays/new").page().one("form")

        form.attr("hx-post") shouldBe "/gamedays"
        form.select("input.gf-input").map { it.attr("name") } shouldContainExactly listOf("title", "date", "place")
        form.one("label[for=title]").text() shouldBe "Title"
        form.one("button[type=submit]").text() shouldBe "Create game day"
    }

    @Test
    @Obligation("GD-5")
    fun `the invite preview mirrors the fields, in the browser`() = webTest { client ->
        val page = client.get("/gamedays/new").page()

        val form = page.one("form")
        form.attr("_") shouldContain "put t into #pv-title"
        page.one("#pv-title").text() shouldBe page.one("#title").`val`()
        page.one("#pv-date").text() shouldBe "Pick a date"
        page.select("#pv-place-row").size shouldBe 0
        page.one(".gf-newform__hint").text() shouldContain "Only people who have joined see it"
        page.one(".gf-pv").parents().any { it.hasClass("gf-desk-only") } shouldBe true
    }

    @Test
    @Obligation("WEB-6")
    fun `the saved theme is applied before anything else loads`() = webTest { client ->
        val head = client.get("/").page().head()

        head.select("script, link[rel=stylesheet]").first()!!.attr("src") shouldBe "/static/js/theme.js"
        client.get("/static/js/theme.js").let { (it.status to it.headers["Content-Type"]!!.startsWith("text/javascript")) shouldBe (HttpStatusCode.OK to true) }
    }

    @Test
    @Obligation("WEB-7")
    fun `every page ends its app bar with the light and dark toggle`() = webTest { client ->
        client.htmxPost("/gamedays", validForm)
        val pages = listOf("/", "/gamedays/new", "/gamedays/00000000-0000-0000-0000-000000000001", "/gamedays/00000000-0000-0000-0000-000000000001/edit")

        pages.forEach { url ->
            val toggle = client.get(url).page().one(".gf-appbar .gf-theme-toggle")
            toggle.nextElementSibling() shouldBe null
            toggle.attr("_") shouldBe "on click call gorefestToggleTheme()"
            toggle.attr("aria-label") shouldBe "Dark mode"
            toggle.select(".gf-theme-toggle__moon svg, .gf-theme-toggle__sun svg").size shouldBe 2
        }
    }

    @Test
    @Obligation("WEB-7")
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
    @Obligation("GD-3")
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
    @Obligation("WEB-5")
    fun `a not found answers with a page that says nothing about why, and a way back`() = webTest { client ->
        for (path in listOf("/gamedays/00000000-0000-0000-0000-00000000beef", "/nonexistent")) {
            val response = client.get(path)
            response.status shouldBe HttpStatusCode.NotFound
            response.headers["X-Robots-Tag"]!! shouldContain "noindex"
            val page = response.page()
            page.one("meta[name=robots]").attr("content") shouldContain "noindex"
            page.one(".gf-empty__title").text() shouldBe "That page isn't here"
            page.one(".gf-empty a[href=/]")
        }
    }

    @Test
    @Obligation("GD-19")
    fun `unknown game days are not found`() = webTest { client ->
        client.get("/gamedays/00000000-0000-0000-0000-00000000beef").status shouldBe HttpStatusCode.NotFound
        client.get("/gamedays/not-a-uuid").status shouldBe HttpStatusCode.NotFound
    }

    @Test
    @Obligation("GD-20")
    fun `players show as initials and names`() = webTest { client ->
        given(gameDayCreated(host = anna), PlayerJoined(gorefest7, anna), PlayerJoined(gorefest7, timId))

        client.get("/gamedays/$gorefest7").page().select("#players .gf-avatar").map { it.text() } shouldContainExactly listOf("A", "T")
    }

    @Test
    @Obligation("GD-20", "PR-9")
    fun `players without a name show as someone`() = webTest { client ->
        given(gameDayCreated(host = bram), PlayerJoined(gorefest7, bram), PlayerJoined(gorefest7, timId))

        val page = client.get("/gamedays/$gorefest7").page()

        page.players() shouldContainExactly listOf("Someone Bringing nothing yet Host", "Tim Bringing nothing yet You")
        page.select("#players .gf-avatar").first()!!.text() shouldBe "?"
    }

    @Test
    fun `the home page has a way out`() = webTest { client ->
        client.get("/").page().one("form[action=/logout] button").text() shouldBe "Log out"
    }

    @Test
    @Obligation("GD-21")
    fun `the home page only shows game days I host or joined`() = webTest { client ->
        given(gameDayCreated(host = anna), PlayerJoined(gorefest7, anna))

        client.get("/").page().select("a.gf-daycard").shouldBeEmpty()
    }

    @Test
    @Obligation("GD-6")
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
        after.select(".gf-hubfacts li").map { it.text() }.take(2) shouldContainExactly listOf("Mon 28 Sep 2026", "Bram's attic")
    }

    @Test
    @Obligation("GD-3")
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
    @Obligation("GD-6")
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
    @Obligation("WEB-4")
    fun `every page configures htmx right after loading it, so rejections get swapped in`() = webTest { client ->
        val scripts = client.get("/").page().head().select("script[src]").map { it.attr("src") }
        scripts.indexOf("/static/js/htmx-config.js") shouldBe scripts.indexOfFirst { it.contains("htmx") } + 1
        val js = client.get("/static/js/htmx-config.js").bodyAsText()
        js shouldContain "responseHandling"
        js shouldContain "40[39]"
    }

    @Test
    @Obligation("GD-19")
    fun `strangers get a 404 for editing or inviting, not a rejection that confirms the game day exists`() = webTest { client ->
        given(gameDayCreated(host = anna), PlayerJoined(gorefest7, anna))

        client.htmxPost("/gamedays/$gorefest7/edit", validForm).status shouldBe HttpStatusCode.NotFound
        client.htmxPost("/gamedays/$gorefest7/invite").status shouldBe HttpStatusCode.NotFound
    }


    @Test
    @Obligation("GD-25", "GD-26")
    fun `the host cancels after a confirm step, and members see Cancelled`() = webTest { client ->
        given(gameDayCreated(host = timId), PlayerJoined(gorefest7, timId), PlayerJoined(gorefest7, anna))

        client.get("/gamedays/$gorefest7").page().one("a[href=/gamedays/$gorefest7/cancel]")
        val confirm = client.get("/gamedays/$gorefest7/cancel").page()
        confirm.one("form[action=/gamedays/$gorefest7/cancel] button").text() shouldBe "Yes, cancel the game day"
        app.gameDaysById.getValue(gorefest7).cancelled shouldBe false

        client.submitForm("/gamedays/$gorefest7/cancel", parameters {}).headers["Location"] shouldBe "/gamedays/$gorefest7"

        val hub = client.get("/gamedays/$gorefest7").page()
        hub.one("h1 ~ span.gf-tag").text() shouldBe "Cancelled"
        hub.select("a[href=/gamedays/$gorefest7/cancel], a[href=/gamedays/$gorefest7/edit], a[href=/gamedays/$gorefest7/bring]").shouldBeEmpty()
        // An empty shelf asks for nothing on a day that can't be changed anymore.
        hub.one(".gf-hub__shelf .gf-empty__text").text() shouldBe "No games were brought."
        client.get("/").page().one("a[href=/gamedays/$gorefest7]").text() shouldContain "Cancelled"
    }

    @Test
    @Obligation("GD-25")
    fun `only the host can cancel`() = webTest { client ->
        given(gameDayCreated(host = anna), PlayerJoined(gorefest7, anna), PlayerJoined(gorefest7, timId))

        client.get("/gamedays/$gorefest7").page().select("a[href=/gamedays/$gorefest7/cancel]").shouldBeEmpty()
        client.get("/gamedays/$gorefest7/cancel").status shouldBe HttpStatusCode.NotFound
        client.submitForm("/gamedays/$gorefest7/cancel", parameters {}).status shouldBe HttpStatusCode.NotFound
        app.gameDaysById.getValue(gorefest7).cancelled shouldBe false
    }

    @Test
    @Obligation("GD-4")
    fun `the create form is prefilled with a fun title the host can re-roll`() = webTest { client ->
        val page = client.get("/gamedays/new").page()
        val title = page.one("#title").`val`()
        TitleGenerator.ENDS shouldContain title.split(" ").last()
        val reroll = page.one("#title-field button")
        reroll.attr("hx-get") shouldBe "/gamedays/title"
        reroll.attr("hx-include") shouldBe "#title"
        reroll.attr("hx-target") shouldBe "#title-field"

        val fragment = client.get("/gamedays/title?title=${title.replace(" ", "+")}").fragment()
        fragment shouldReplace "title-field"
        fragment.one("#title").`val`() shouldNotBe title
        fragment.one("button").attr("aria-label") shouldBe "Another title"
    }

    @Test
    @Obligation("GD-4")
    fun `editing keeps the host's own title and has no re-roll`() = webTest { client ->
        given(gameDayCreated(host = timId), PlayerJoined(gorefest7, timId))

        val page = client.get("/gamedays/$gorefest7/edit").page()

        page.one("#title").`val`() shouldBe "Gorefest #7"
        page.select("#title-field button").shouldBeEmpty()
    }

    @Test
    @Obligation("GD-25")
    fun `a non-host answers a cancel like a stranger, with no form leaked`() = webTest { client ->
        given(gameDayCreated(host = anna), PlayerJoined(gorefest7, anna), PlayerJoined(gorefest7, timId))

        client.submitForm("/gamedays/$gorefest7/cancel", parameters {}).status shouldBe HttpStatusCode.NotFound
        app.gameDaysById.getValue(gorefest7).cancelled shouldBe false
    }

    @Test
    @Obligation("GD-19", "GD-25")
    fun `someone who didn't join can't cancel`() = webTest { client ->
        given(gameDayCreated(host = anna), PlayerJoined(gorefest7, anna))

        client.get("/gamedays/$gorefest7/cancel").status shouldBe HttpStatusCode.NotFound
        client.submitForm("/gamedays/$gorefest7/cancel", parameters {}).status shouldBe HttpStatusCode.NotFound
        app.gameDaysById.getValue(gorefest7).cancelled shouldBe false
    }

    @Test
    @Obligation("GD-27")
    fun `a cancelled game day is read-only, only the hub is reachable`() = webTest { client ->
        given(gameDayCreated(host = timId), PlayerJoined(gorefest7, timId), gorefest.core.GameDayCancelled(gorefest7))

        client.get("/gamedays/$gorefest7").status shouldBe HttpStatusCode.OK
        for (path in listOf("edit", "cancel", "log", "log/compare", "table", "bring", "ready", "taste", "favourites")) {
            withClue("GET $path") { client.get("/gamedays/$gorefest7/$path").status shouldBe HttpStatusCode.NotFound }
        }
        client.submitForm("/gamedays/$gorefest7/log", parameters {}).status shouldBe HttpStatusCode.NotFound
        client.submitForm("/gamedays/$gorefest7/edit", parameters {}).status shouldBe HttpStatusCode.NotFound
    }
}
