package gorefest.shell.web

import gorefest.core.BggAccountLinked
import gorefest.core.BggId
import gorefest.core.GameBrought
import gorefest.core.GameDayEnded
import gorefest.core.PlayerId
import gorefest.core.PlayerJoined
import gorefest.core.gameday.anna
import gorefest.core.gameday.gameDayCreated
import gorefest.core.gameday.gorefest7
import gorefest.shell.bgg.Collections
import gorefest.shell.bgg.DevCatalog
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

class BringRoutesTest {
    private val test = TestApp()
    private val app = test.app
    private val kimId = PlayerId(Uuid.random())
    private lateinit var kim: String
    private val brass = BggId(224517)
    private val azul = BggId(230802)

    @BeforeEach
    fun reset(): Unit = kotlinx.coroutines.runBlocking {
        test.reset()
        kim = test.loggedIn("Kim", id = kimId)
        test.loggedIn("Anna", id = anna)
        app.catalog.put(DevCatalog.games, test.clock.instant())
        given(gameDayCreated(host = anna), PlayerJoined(gorefest7, anna), PlayerJoined(gorefest7, kimId), BggAccountLinked(kimId, "KimPlays"))
        app.db.transaction { Collections().replace(kimId, setOf(brass, azul)) }
    }

    private suspend fun given(vararg events: gorefest.core.Event) {
        app.db.transaction { app.store.append(events.toList()) }
        app.gameDays.catchUp(); app.shelves.catchUp(); app.bggAccounts.catchUp()
    }

    private val bringPage = "/gamedays/$gorefest7/bring"

    @Test
    fun `my collection is listed with a switch per game`() = test.webTest(session = kim) { client ->
        val page = client.get(bringPage).page()

        page.select("#bring-list .gf-game__name").map { it.text() } shouldContainExactly listOf("Azul", "Brass: Birmingham")
        val switch = page.one("#bring-$brass input.gf-switch")
        switch.attr("hx-post") shouldBe "/gamedays/$gorefest7/shelf/$brass"
        switch.attr("hx-target") shouldBe "#bring-$brass"
        switch.hasAttr("checked") shouldBe false
    }

    @Test
    fun `ticking a game puts it on the shelf and offers the wish toggle`() = test.webTest(session = kim) { client ->
        val row = client.htmxPost("/gamedays/$gorefest7/shelf/$brass", parameters { append("bringing", "true") }).fragment()

        row shouldReplace "bring-$brass"
        row.one("input.gf-switch").hasAttr("checked") shouldBe true
        row.one("button[hx-post]").attr("hx-post") shouldBe "/gamedays/$gorefest7/shelf/$brass/wish"
        app.shelves.state.getValue(gorefest7).map { it.game } shouldBe listOf(brass)
    }

    @Test
    fun `unticking takes it off again`() = test.webTest(session = kim) { client ->
        client.htmxPost("/gamedays/$gorefest7/shelf/$brass", parameters { append("bringing", "true") })

        val row = client.htmxPost("/gamedays/$gorefest7/shelf/$brass").fragment()

        row.one("input.gf-switch").hasAttr("checked") shouldBe false
        row.select("button").shouldBeEmpty()
        app.shelves.state.getValue(gorefest7).shouldBeEmpty()
    }

    @Test
    fun `owners can say they really want to play it`() = test.webTest(session = kim) { client ->
        client.htmxPost("/gamedays/$gorefest7/shelf/$brass", parameters { append("bringing", "true") })

        val row = client.htmxPost("/gamedays/$gorefest7/shelf/$brass/wish", parameters { append("wants", "true") }).fragment()

        row.one("button").attr("aria-pressed") shouldBe "true"
        app.shelves.state.getValue(gorefest7).single().eager shouldBe setOf(kimId)
    }

    @Test
    fun `the game day shows the shelf with who brings what`() = test.webTest(session = kim) { client ->
        given(GameBrought(gorefest7, kimId, brass), GameBrought(gorefest7, anna, azul))

        val page = client.get("/gamedays/$gorefest7").page()

        page.one(".gf-sechead h2").text() shouldBe "The shelf · 2"
        page.select(".gf-tile").map { it.one(".gf-tile__name").text() to it.one(".gf-tile__owner").text() } shouldContainExactly
            listOf("Brass: Birmingham" to "K", "Azul" to "A")
        page.one(".gf-sechead a").attr("href") shouldBe bringPage
    }

    @Test
    fun `the invite shows the shelf size`() = test.webTest { client ->
        given(GameBrought(gorefest7, kimId, brass), GameBrought(gorefest7, anna, azul))
        val link = "/i/" + app.invites.token(app.gameDays.state.getValue(gorefest7))

        val page = client.get(link).page()

        page.select(".gf-fan .gf-art").size shouldBe 2
        page.one("meta[property=og:description]").attr("content") shouldBe "Sunday 27 September · Anna's place · 2 going · 2 games on the shelf"
    }

    @Test
    fun `without BGG, the page says how to get the collection`() {
        val bram = kotlinx.coroutines.runBlocking {
            val id = PlayerId(Uuid.random())
            test.loggedIn("Bram", id = id).also { given(PlayerJoined(gorefest7, id)) }
        }
        test.webTest(session = bram) { client ->
            val page = client.get(bringPage).page()
            page.one(".gf-notice a").attr("href") shouldBe "/bgg?returnTo=${bringPage.urlEncoded()}"
            page.select("#bring-list li").shouldBeEmpty()
        }
    }

    @Test
    fun `after the game day the shelf is read-only`() = test.webTest(session = kim) { client ->
        given(GameBrought(gorefest7, kimId, brass), GameDayEnded(gorefest7))

        client.get(bringPage).page().one("#bring-$brass input").hasAttr("disabled") shouldBe true
        client.htmxPost("/gamedays/$gorefest7/shelf/$azul", parameters { append("bringing", "true") }).status shouldBe HttpStatusCode.Conflict
    }

    @Test
    fun `players who didn't join can't bring`() {
        val stranger = kotlinx.coroutines.runBlocking { test.loggedIn("Stranger") }
        test.webTest(session = stranger) { client ->
            client.get(bringPage).status shouldBe HttpStatusCode.NotFound
            client.htmxPost("/gamedays/$gorefest7/shelf/$brass", parameters { append("bringing", "true") }).status shouldBe HttpStatusCode.NotFound
        }
    }
}
