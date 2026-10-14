package gorefest.shell.web.checkpoint

import gorefest.core.Basis
import gorefest.core.ComparisonRecorded
import gorefest.core.ComparisonResult
import gorefest.core.GameBrought
import gorefest.core.GamePlayStatusDeclared
import gorefest.core.PlayLogged
import gorefest.core.PlayerId
import gorefest.core.PlayerJoined
import gorefest.core.gameday.anna
import gorefest.core.gameday.gameDayCreated
import gorefest.core.gameday.gorefest7
import gorefest.shell.bgg.DevCatalog
import gorefest.shell.web.TestApp
import gorefest.shell.web.components.notice
import gorefest.shell.web.components.page
import gorefest.shell.web.fragment
import gorefest.shell.web.htmxPost
import gorefest.shell.web.one
import gorefest.shell.web.page
import gorefest.shell.web.shouldReplace
import gorefest.shell.web.suggestions.suggestions
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.uuid.Uuid

class CheckpointRoutesTest {
    private val test = TestApp()
    private val app = test.app
    private val kimId = PlayerId(Uuid.random())
    private lateinit var kim: String
    private fun id(name: String) = DevCatalog.games.first { it.name == name }.id
    private val azul = id("Azul")
    private val cascadia = id("Cascadia")

    @BeforeEach
    fun reset(): Unit = kotlinx.coroutines.runBlocking {
        test.reset()
        kim = test.loggedIn("Kim", id = kimId)
        test.loggedIn("Anna", id = anna)
        app.catalog.put(DevCatalog.games, test.clock.instant())
        given(gameDayCreated(host = anna), PlayerJoined(gorefest7, anna), PlayerJoined(gorefest7, kimId), GameBrought(gorefest7, anna, azul), GameBrought(gorefest7, anna, cascadia))
        // The game day is on 27 September; it's now 12:00 in Brussels that day.
        test.clock.advance(Duration.ofDays(3))
    }

    private suspend fun given(vararg events: gorefest.core.Event) {
        app.db.transaction { app.store.append(events.toList()) }
        app.catchUp()
    }

    private val log = "/gamedays/$gorefest7/log"

    @Test
    fun `on the day, someone with no taste yet is still offered the taste round, with the log one tap away`() = test.webTest(session = kim) { client ->
        val page = client.get("/gamedays/$gorefest7").page()

        page.one(".gf-next a").attr("href") shouldBe "/gamedays/$gorefest7/taste"
        page.one(".gf-hub__hero a[href=$log]").text() shouldBe "Log a game"
    }

    @Test
    fun `on the day, once there is a taste the hub asks what you played`() = test.webTest(session = kim) { client ->
        given(*Array(10) { ComparisonRecorded(kimId, azul, cascadia, ComparisonResult.A, Basis.VIBE, test.clock.instant()) })

        val next = client.get("/gamedays/$gorefest7").page().one(".gf-next")

        next.one(".gf-kicker").text() shouldBe "Lunch checkpoint"
        next.one("a").attr("href") shouldBe log
    }

    @Test
    fun `the log form offers the shelf, me at the table and a duration`() = test.webTest(session = kim) { client ->
        val form = client.get(log).page().one("#log-form")

        form.select(".gf-tile .gf-tile__name").map { it.text() } shouldBe listOf("Azul", "Cascadia")
        form.one("input[name=game][checked]").`val`() shouldBe azul.toString()
        form.select("input[name=players][checked]").map { it.`val`() } shouldBe listOf(kimId.toString())
        form.one("input[name=duration][checked]").`val`() shouldBe "45m"
    }

    @Test
    fun `the log page has three numbered steps, a summary card and a dock that submit the same form`() = test.webTest(session = kim) { client ->
        val page = client.get(log).page()

        page.one(".gf-nav a[aria-current=page]").text() shouldBe "Game days"
        page.select("#log-form .gf-stepper__item .gf-stepper__title").map { it.text() } shouldBe listOf("The game", "Who played?", "Roughly how long?")
        val summary = page.one("#log-form .gf-log__summary")
        summary.one("[data-log=game]").text() shouldBe "Azul"
        summary.one("[data-log=about]").text() shouldBe "1 player · about 45 min"
        summary.one("button[type=submit]").text() shouldBe "Log it"
        // On a phone the dock holds the button and says what will be logged; the hyperscript keeps both current.
        page.one(".gf-dock.gf-phone-only button").attr("form") shouldBe "log-form"
        page.one(".gf-dock [data-log=dock]").text() shouldBe "Log Azul · 1 player · 45m"
        page.one("#log-form").attr("_").contains("on change call update()") shouldBe true
    }

    @Test
    fun `the quick question page keeps the shell and the duel`() = test.webTest(session = kim) { client ->
        val logged = client.htmxPost(log, parameters { append("game", azul.toString()); append("players", kimId.toString()); append("duration", "45m") })
        given(GamePlayStatusDeclared(kimId, cascadia, played = true))

        val page = client.get(logged.headers["HX-Redirect"]!!).page()

        page.one(".gf-nav a[aria-current=page]").text() shouldBe "Game days"
        page.select(".gf-pick__name").map { it.text() } shouldBe listOf("Azul", "Cascadia")
    }

    @Test
    fun `a game that isn't on the shelf can be found by search and logged`() = test.webTest(session = kim) { client ->
        val form = client.get(log).page().one("#log-form")
        form.one("input[type=search]").attr("hx-get") shouldBe "$log/search"
        form.one("input[type=search]").attr("hx-target") shouldBe "#log-search-results"
        form.one(".gf-search .gf-spin.htmx-indicator")

        // Shelf games are tiles already, so the results only add others.
        val results = client.get("$log/search?q=brass").fragment()
        results shouldReplace "log-search-results"
        results.select(".gf-tile .gf-tile__name").map { it.text() } shouldBe listOf("Brass: Birmingham")
        // ...and the shelf's own games never repeat as results.
        client.get("$log/search?q=azul").fragment().select(".gf-tile") shouldHaveSize 0
        // A search that finds nothing says so; a shelf game that was left out is not "nothing".
        client.get("$log/search?q=zzzzzz").fragment().one(".gf-search-empty").text() shouldContain "Nothing found for"
        client.get("$log/search?q=azul").fragment().select(".gf-search-empty").shouldBeEmpty()

        val brass = id("Brass: Birmingham")
        val logged = client.htmxPost(log, parameters { append("game", brass.toString()); append("players", kimId.toString()); append("duration", "90m") })
        logged.headers["HX-Redirect"]!! shouldStartWith "/gamedays/$gorefest7/log/compare?game=$brass"
        app.plays.on(gorefest7).single().game shouldBe brass
    }

    @Test
    fun `a rejected log keeps the off-shelf game's tile`() = test.webTest(session = kim) { client ->
        val brass = id("Brass: Birmingham")

        val form = client.htmxPost(log, parameters { append("game", brass.toString()); append("duration", "45m") }).fragment()

        form.select("[role=alert]") shouldHaveSize 1
        form.select(".gf-tile .gf-tile__name").map { it.text() } shouldBe listOf("Azul", "Cascadia", "Brass: Birmingham")
        form.one("input[name=game][checked]").`val`() shouldBe brass.toString()
    }

    @Test
    fun `logging a game asks one comparison, then shows fresh suggestions for that table`() = test.webTest(session = kim) { client ->
        given(GamePlayStatusDeclared(kimId, cascadia, played = true))

        val logged = client.htmxPost(log, parameters {
            append("game", azul.toString()); append("players", kimId.toString()); append("players", anna.toString()); append("duration", "45m")
        })
        val compare = logged.headers["HX-Redirect"]!!
        compare shouldStartWith "/gamedays/$gorefest7/log/compare?game=$azul"
        app.plays.on(gorefest7).single().let { (it.players to it.minutes) shouldBe (listOf(kimId, anna) to 45) }

        val page = client.get(compare).page()
        page.one(".gf-notice--good").text() shouldBe "Logged Azul: 2 players, 45 min."
        page.select(".gf-pick__name").map { it.text() } shouldBe listOf("Azul", "Cascadia")

        val done = client.submitForm("/gamedays/$gorefest7/log/compare", parameters {
            page.select("form input[type=hidden]").forEach { append(it.attr("name"), it.`val`()) }
            append("answer", "A")
        })
        done.headers["Location"] shouldBe "/gamedays/$gorefest7/table?players=$kimId&players=$anna"
        app.tastes.of(kimId).comparisons.single().let { (it.basis to it.a) shouldBe (Basis.PLAYED to azul) }
    }

    @Test
    fun `logging a table you weren't at asks you nothing and records nothing`() = test.webTest(session = kim) { client ->
        given(GamePlayStatusDeclared(kimId, cascadia, played = true))

        val compare = client.htmxPost(log, parameters { append("game", azul.toString()); append("players", anna.toString()); append("duration", "45m") })
            .headers["HX-Redirect"]!!
        client.get(compare).headers["Location"] shouldBe "/gamedays/$gorefest7/table?players=$anna"

        // A hand-made answer for a game you didn't play is ignored too.
        client.submitForm("/gamedays/$gorefest7/log/compare", parameters {
            append("a", azul.toString()); append("b", cascadia.toString()); append("answer", "A")
        }).status shouldBe HttpStatusCode.Found
        app.tastes.of(kimId).comparisons shouldHaveSize 0
    }

    @Test
    fun `with nothing to compare against, it goes straight to suggestions`() = test.webTest(session = kim) { client ->
        client.htmxPost(log, parameters { append("game", azul.toString()); append("players", kimId.toString()); append("duration", "30m") })

        client.get("/gamedays/$gorefest7/log/compare?game=$azul&minutes=30&players=$kimId").headers["Location"] shouldBe
            "/gamedays/$gorefest7/table?players=$kimId"
    }

    @Test
    fun `a log without players is refused with the form kept`() = test.webTest(session = kim) { client ->
        val form = client.htmxPost(log, parameters { append("game", cascadia.toString()); append("duration", "1h") }).fragment()

        form shouldReplace "log-form"
        form.one("[role=alert]").text() shouldBe "Tick who played"
        form.one("input[name=game][checked]").`val`() shouldBe cascadia.toString()
        form.one("input[name=duration][checked]").`val`() shouldBe "1h"
    }

    @Test
    fun `the hub lists what was played today`() = test.webTest(session = kim) { client ->
        given(PlayLogged(gorefest7, cascadia, listOf(kimId, anna), 40, kimId, test.clock.instant()))

        val rows = client.get("/gamedays/$gorefest7").page().select("section:has(h2:containsOwn(Played today)) .gf-game")

        rows shouldHaveSize 1
        rows.single().one(".gf-game__meta").text() shouldBe "Kim, Anna · 40 min"
    }

    @Test
    fun `the compare link ignores strangers, junk and out-of-range minutes`() = test.webTest(session = kim) { client ->
        val stranger = PlayerId(Uuid.random())
        client.htmxPost(log, parameters { append("game", azul.toString()); append("players", kimId.toString()); append("duration", "30m") })

        client.get("/gamedays/$gorefest7/log/compare?game=$azul&minutes=x&players=$kimId&players=$stranger&players=%22%3E%3Cscript%3E").headers["Location"] shouldBe
            "/gamedays/$gorefest7/table?players=$kimId"
    }

    @Test
    fun `minutes out of range show as zero`() = test.webTest(session = kim) { client ->
        given(GamePlayStatusDeclared(kimId, cascadia, played = true))

        val page = client.get("/gamedays/$gorefest7/log/compare?game=$azul&minutes=999999&players=$kimId").page()
        page.one(".gf-notice--good").text() shouldBe "Logged Azul: 1 player, 0 min."
    }

    @Test
    fun `during the game day a play can be logged, and the hub keeps the log one tap away`() = test.webTest(session = kim) { client ->
        given(*Array(10) { ComparisonRecorded(kimId, azul, cascadia, ComparisonResult.A, Basis.VIBE, test.clock.instant()) })
        val response = client.htmxPost(log, parameters { append("game", azul.toString()); append("players", kimId.toString()); append("duration", "30m") })
        response.status shouldBe io.ktor.http.HttpStatusCode.NoContent
        app.plays.on(gorefest7) shouldHaveSize 1

        val section = client.get("/gamedays/$gorefest7").page().one("section:has(h2:containsOwn(Played today))")
        section.one("a.gf-link").attr("href") shouldBe log
    }

    @Test
    fun `before the game day nothing can be logged`() = test.webTest(session = kim) { client ->
        test.clock.advance(Duration.ofDays(-3))

        val response = client.htmxPost(log, parameters { append("game", azul.toString()); append("players", kimId.toString()); append("duration", "30m") })

        response.fragment().one(".gf-notice").text() shouldBe "The game day hasn't started yet"
        app.plays.on(gorefest7) shouldHaveSize 0
        client.get("/gamedays/$gorefest7").page().select(".gf-next a[href=$log]").size shouldBe 0
    }
}
