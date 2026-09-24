package gorefest.shell.web

import gorefest.core.BggId
import gorefest.core.PlayerId
import gorefest.core.PlayerJoined
import gorefest.core.gameday.anna
import gorefest.core.gameday.gameDayCreated
import gorefest.core.gameday.gorefest7
import gorefest.shell.bgg.BggApi
import gorefest.shell.bgg.BggUnavailable
import gorefest.shell.bgg.DevCatalog
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.http.parameters
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

class BringSearchTest {
    private val test = TestApp()
    private val app = test.app
    private val kimId = PlayerId(Uuid.random())
    private lateinit var kim: String
    private val wingspan = BggId(266192)

    @BeforeEach
    fun reset(): Unit = kotlinx.coroutines.runBlocking {
        test.reset()
        kim = test.loggedIn("Kim", id = kimId)
        test.loggedIn("Anna", id = anna)
        // We only know two games locally; the fake BGG knows the whole dev catalogue.
        app.catalog.put(DevCatalog.games.filter { it.name in setOf("Azul", "The Crew: Mission Deep Sea") }, test.clock.instant())
        app.db.transaction { app.store.append(listOf(gameDayCreated(host = anna), PlayerJoined(gorefest7, anna), PlayerJoined(gorefest7, kimId))) }
        app.gameDays.catchUp()
    }

    private suspend fun io.ktor.client.HttpClient.search(q: String) =
        get("/gamedays/$gorefest7/bring/search?q=${q.urlEncoded()}").fragment()

    @Test
    fun `the bring page has a debounced search box`() = test.webTest(session = kim) { client ->
        val input = client.get("/gamedays/$gorefest7/bring").page().one(".gf-search input")

        input.attr("hx-get") shouldBe "/gamedays/$gorefest7/bring/search"
        input.attr("hx-trigger") shouldBe "input changed delay:300ms, search"
        input.attr("hx-target") shouldBe "#search-results"
    }

    @Test
    fun `games we don't know yet come from BGG and are cached`() = test.webTest(session = kim) { client ->
        val results = client.search("wingspan")

        results shouldReplace "search-results"
        results.select(".gf-game__name").map { it.text() } shouldContainExactly listOf("Wingspan")
        test.bgg.searchCalls shouldBe listOf("wingspan")
        app.catalog[wingspan]!!.name shouldBe "Wingspan"
    }

    @Test
    fun `a game found by search can be brought, and stays on my bring page`() = test.webTest(session = kim) { client ->
        client.search("wingspan")

        val row = client.htmxPost("/gamedays/$gorefest7/shelf/$wingspan", parameters { append("bringing", "true"); append("row", "search") }).fragment()

        row shouldReplace "search-$wingspan"
        row.one("input.gf-switch").attr("hx-target") shouldBe "#search-$wingspan"
        app.shelves.state.getValue(gorefest7).map { it.game } shouldBe listOf(wingspan)
        client.get("/gamedays/$gorefest7/bring").page().select("#bring-list .gf-game__name").map { it.text() } shouldContainExactly listOf("Wingspan")
    }

    @Test
    fun `short queries don't search`() = test.webTest(session = kim) { client ->
        client.search("w").select("li").shouldBeEmpty()
        test.bgg.searchCalls.shouldBeEmpty()
    }

    @Test
    suspend fun `enough local matches skip BGG`() {
        app.catalog.put(DevCatalog.games, test.clock.instant())

        app.gameSearch.search("the").games.size shouldBe DevCatalog.games.count { it.name.contains("the", ignoreCase = true) }.coerceAtMost(20)
        test.bgg.searchCalls.shouldBeEmpty()
    }

    @Test
    suspend fun `when BGG is down, local matches still show`() {
        val down = TestApp()
        down.reset()
        down.app.catalog.put(DevCatalog.games.filter { it.name == "Azul" }, down.clock.instant())
        val failing = object : BggApi by down.bgg {
            override suspend fun search(query: String) = throw BggUnavailable("503")
        }
        val search = gorefest.shell.bgg.GameSearch(down.app.catalog, gorefest.shell.bgg.BggGateway(failing, java.time.Duration.ZERO).also { it.start(down.app.scope) }, down.clock)

        val result = search.search("azul")

        result.games.map { it.name } shouldBe listOf("Azul")
        result.bggUnavailable shouldBe true
    }
}
