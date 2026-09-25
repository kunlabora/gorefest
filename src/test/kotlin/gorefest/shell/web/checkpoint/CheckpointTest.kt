package gorefest.shell.web.checkpoint

import gorefest.core.Basis
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
import io.kotest.matchers.string.shouldStartWith
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.http.parameters
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.uuid.Uuid

class CheckpointTest {
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
        app.gameDays.catchUp(); app.shelves.catchUp(); app.tastes.catchUp(); app.plays.catchUp()
    }

    private val log = "/gamedays/$gorefest7/log"

    @Test
    fun `on the day, the hub asks what you played`() = test.webTest(session = kim) { client ->
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
    fun `logging a game asks one comparison, then shows fresh suggestions for that table`() = test.webTest(session = kim) { client ->
        given(GamePlayStatusDeclared(kimId, cascadia, played = true))

        val logged = client.htmxPost(log, parameters {
            append("game", azul.toString()); append("players", kimId.toString()); append("players", anna.toString()); append("duration", "45m")
        })
        val compare = logged.headers["HX-Redirect"]!!
        compare shouldStartWith "/gamedays/$gorefest7/log/compare?game=$azul"
        app.plays.state.getValue(gorefest7).single().let { (it.players to it.minutes) shouldBe (listOf(kimId, anna) to 45) }

        val page = client.get(compare).page()
        page.one(".gf-notice--good").text() shouldBe "Logged Azul: 2 players, 45 min."
        page.select(".gf-pick__name").map { it.text() } shouldBe listOf("Azul", "Cascadia")

        val done = client.submitForm("/gamedays/$gorefest7/log/compare", parameters {
            page.select("form input[type=hidden]").forEach { append(it.attr("name"), it.`val`()) }
            append("answer", "A")
        })
        done.headers["Location"] shouldBe "/gamedays/$gorefest7/table?players=$kimId&players=$anna"
        app.tastes.state.getValue(kimId).comparisons.single().let { (it.basis to it.a) shouldBe (Basis.PLAYED to azul) }
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
}
