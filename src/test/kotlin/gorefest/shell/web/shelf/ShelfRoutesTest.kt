package gorefest.shell.web.shelf

import gorefest.core.BggAccountLinked
import gorefest.core.BggId
import gorefest.core.GameBrought
import gorefest.core.GameDayEnded
import gorefest.core.PlayWishChanged
import gorefest.core.PlayerId
import gorefest.core.PlayerJoined
import gorefest.core.gameday.anna
import gorefest.core.gameday.gameDayCreated
import gorefest.core.gameday.gorefest7
import gorefest.shell.bgg.Collections
import gorefest.shell.bgg.DevCatalog
import gorefest.shell.web.TestApp
import gorefest.shell.web.bgg.collection
import gorefest.shell.web.components.art
import gorefest.shell.web.components.hx
import gorefest.shell.web.components.notice
import gorefest.shell.web.components.page
import gorefest.shell.web.fragment
import gorefest.shell.web.gameday.invite
import gorefest.shell.web.htmxPost
import gorefest.shell.web.invite.description
import gorefest.shell.web.one
import gorefest.shell.web.page
import gorefest.shell.web.shouldReplace
import gorefest.shell.web.urlEncoded
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

class ShelfRoutesTest {
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
        app.catchUp()
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
    fun `ticking a game puts it on the shelf`() = test.webTest(session = kim) { client ->
        val row = client.htmxPost("/gamedays/$gorefest7/shelf/$brass", parameters { append("bringing", "true") }).fragment()

        row shouldReplace "bring-$brass"
        row.one("input.gf-switch").hasAttr("checked") shouldBe true
        row.select("button").shouldBeEmpty()
        app.shelves.of(gorefest7).map { it.game } shouldBe listOf(brass)
    }

    @Test
    fun `unticking takes it off again`() = test.webTest(session = kim) { client ->
        client.htmxPost("/gamedays/$gorefest7/shelf/$brass", parameters { append("bringing", "true") })

        val row = client.htmxPost("/gamedays/$gorefest7/shelf/$brass").fragment()

        row.one("input.gf-switch").hasAttr("checked") shouldBe false
        row.select("button").shouldBeEmpty()
        app.shelves.of(gorefest7).shouldBeEmpty()
    }

    @Test
    fun `the bring page lists the whole shelf under 'What do you want to play', with a heart per game`() = test.webTest(session = kim) { client ->
        given(GameBrought(gorefest7, anna, azul), GameBrought(gorefest7, kimId, brass), PlayWishChanged(gorefest7, anna, azul, true))

        val page = client.get(bringPage).page()

        page.select("h2").map { it.text() } shouldContain "What do you want to play?"
        page.select("#wish-list li .gf-game__name").map { it.text() } shouldContainExactly listOf("Azul", "Brass: Birmingham")
        val azulRow = page.one("#wish-$azul")
        azulRow.one("button").attr("hx-post") shouldBe "/gamedays/$gorefest7/shelf/$azul/wish"
        azulRow.one("button").attr("aria-pressed") shouldBe "false"
        azulRow.one(".gf-wish .gf-small").text() shouldBe "1 person wants this"
        page.one("#wish-$brass button").attr("aria-pressed") shouldBe "false"
        page.one("#wish-list").attr("hx-trigger") shouldBe "bring-list-changed from:body, shelf-changed from:body"
    }

    @Test
    fun `any member can heart a game somebody else brought, and take it back`() = test.webTest(session = kim) { client ->
        given(GameBrought(gorefest7, anna, azul))

        val onResponse = client.htmxPost("/gamedays/$gorefest7/shelf/$azul/wish", parameters { append("wants", "true") })
        onResponse.headers["HX-Trigger"] shouldBe "shelf-changed"
        val on = onResponse.fragment()

        on shouldReplace "wish-$azul"
        on.one("button").attr("aria-pressed") shouldBe "true"
        on.one("button").attr("hx-vals") shouldContain "\"wants\": \"false\""
        on.one(".gf-wish .gf-small").text() shouldBe "1 person wants this"
        app.shelves.of(gorefest7).single().eager shouldBe setOf(kimId)

        val off = client.htmxPost("/gamedays/$gorefest7/shelf/$azul/wish", parameters { append("wants", "false") }).fragment()
        off.one("button").attr("aria-pressed") shouldBe "false"
        app.shelves.of(gorefest7).single().eager shouldBe emptySet()
    }

    @Test
    fun `a fourth heart is refused, saying why`() = test.webTest(session = kim) { client ->
        val games = listOf("Azul", "Brass: Birmingham", "CATAN", "Root").map { name -> DevCatalog.games.first { it.name == name }.id }
        given(*games.map { GameBrought(gorefest7, anna, it) }.toTypedArray(), *games.take(3).map { PlayWishChanged(gorefest7, kimId, it, true) }.toTypedArray())

        val refused = client.htmxPost("/gamedays/$gorefest7/shelf/${games[3]}/wish", parameters { append("wants", "true") })

        refused.status shouldBe HttpStatusCode.Conflict
        refused.fragment().one(".gf-error").text() shouldBe "You can pick 3 favourites: take one off first"
        app.shelves.of(gorefest7).first { it.game == games[3] }.eager shouldBe emptySet()
    }

    @Test
    fun `a game nobody brought can't be hearted`() = test.webTest(session = kim) { client ->
        val response = client.htmxPost("/gamedays/$gorefest7/shelf/$azul/wish", parameters { append("wants", "true") })

        response.status shouldBe HttpStatusCode.Conflict
        response.fragment().one(".gf-error").text() shouldBe "Nobody's bringing that game"
    }

    @Test
    fun `the wish list reloads from the server, and ticking in the list asks for it`() = test.webTest(session = kim) { client ->
        client.htmxPost("/gamedays/$gorefest7/shelf/$brass", parameters { append("bringing", "true") }).headers["HX-Trigger"] shouldBe "shelf-changed"

        val list = client.get("$bringPage/wishes").fragment()

        list shouldReplace "wish-list"
        list.select(".gf-game__name").map { it.text() } shouldContainExactly listOf("Brass: Birmingham")
    }

    @Test
    fun `the game day shows the shelf with who brings what`() = test.webTest(session = kim) { client ->
        given(GameBrought(gorefest7, kimId, brass), GameBrought(gorefest7, anna, azul), GameBrought(gorefest7, anna, BggId(13)))

        val page = client.get("/gamedays/$gorefest7").page()
        page.select("#players li").map { it.one(".gf-row__title").text() to it.one(".gf-row__sub").text() } shouldContainExactly
            listOf("Anna" to "Bringing 2 games", "Kim" to "Bringing 1 game")

        page.select(".gf-sechead h2").first()!!.text() shouldBe "The shelf · 3"
        page.select(".gf-tile").map { it.one(".gf-tile__name").text() to it.one(".gf-tile__owner").text() } shouldContainExactly
            listOf("Brass: Birmingham" to "K", "Azul" to "A", "CATAN" to "A")
        page.one(".gf-sechead a").attr("href") shouldBe bringPage
    }

    @Test
    fun `the invite shows the shelf size`() = test.webTest { client ->
        given(GameBrought(gorefest7, kimId, brass), GameBrought(gorefest7, anna, azul))
        val link = "/i/" + app.invites.token(app.gameDaysById.getValue(gorefest7))

        val page = client.get(link).page()

        page.select(".gf-fan .gf-art").size shouldBe 2
        page.one("meta[property=og:description]").attr("content") shouldBe "Sunday 27 September · Anna's place · 2 going · 2 games on the shelf"
    }

    @Test
    fun `a game brought from a search result shows up in the list below`() {
        val catan = BggId(13)
        val bram = kotlinx.coroutines.runBlocking {
            val id = PlayerId(Uuid.random())
            test.loggedIn("Bram", id = id).also { given(PlayerJoined(gorefest7, id)) }
        }
        test.webTest(session = bram) { client ->
            client.get(bringPage).page().one("#bring-list").attr("hx-trigger") shouldBe "bring-list-changed from:body"

            val fromSearch = client.htmxPost("/gamedays/$gorefest7/shelf/$catan", parameters { append("bringing", "true"); append("row", "search") })
            fromSearch.headers["HX-Trigger"] shouldBe "bring-list-changed"
            fromSearch.fragment() shouldReplace "search-$catan"

            val list = client.get("$bringPage/list").fragment()
            list shouldReplace "bring-list"
            list.select(".gf-game__name").map { it.text() } shouldContainExactly listOf("CATAN")

            client.htmxPost("/gamedays/$gorefest7/shelf/$catan", parameters { append("row", "search") })
            client.get("$bringPage/list").fragment().select("li").shouldBeEmpty()
        }
    }

    @Test
    fun `ticking a game in the list itself doesn't reload that list, only the wish list`() = test.webTest(session = kim) { client ->
        val response = client.htmxPost("/gamedays/$gorefest7/shelf/$brass", parameters { append("bringing", "true") })

        response.headers["HX-Trigger"] shouldBe "shelf-changed"
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
