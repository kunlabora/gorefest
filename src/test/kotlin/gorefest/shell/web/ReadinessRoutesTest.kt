package gorefest.shell.web

import gorefest.core.GameBrought
import gorefest.core.PlayerId
import gorefest.core.PlayerJoined
import gorefest.core.gameday.anna
import gorefest.core.gameday.gameDayCreated
import gorefest.core.gameday.gorefest7
import gorefest.shell.bgg.DevCatalog
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

class ReadinessRoutesTest {
    private val test = TestApp()
    private val app = test.app
    private val kimId = PlayerId(Uuid.random())
    private lateinit var host: String
    private lateinit var kim: String
    private fun id(name: String) = DevCatalog.games.first { it.name == name }.id

    @BeforeEach
    fun reset(): Unit = kotlinx.coroutines.runBlocking {
        test.reset()
        host = test.loggedIn("Anna", id = anna)
        kim = test.loggedIn("Kim", id = kimId)
        app.catalog.put(DevCatalog.games, test.clock.instant())
        app.db.transaction {
            app.store.append(listOf(gameDayCreated(host = anna), PlayerJoined(gorefest7, anna), PlayerJoined(gorefest7, kimId), GameBrought(gorefest7, anna, id("Twilight Struggle"))))
        }
        app.gameDays.catchUp(); app.shelves.catchUp()
    }

    private val ready = "/gamedays/$gorefest7/ready"

    @Test
    fun `the host sees numbers, gaps and who to nudge`() = test.webTest(session = host) { client ->
        client.get("/gamedays/$gorefest7").page().one("a[href=$ready]").text() shouldBe "How ready are we?"

        val page = client.get(ready).page()

        page.one("main > .gf-stack > .gf-kicker").text() shouldBe "3 days to go"
        page.select(".gf-stat").map { it.text() } shouldBe listOf("2 joined", "1 on the shelf", "0/2 taste ready")
        page.select(".gf-row") shouldHaveSize 2
        val nudge = page.one(".gf-row button")
        nudge.attr("aria-label") shouldBe "Copy a nudge for Kim"
        nudge.attr("data-message") shouldContain "Hey Kim"
        nudge.attr("data-message") shouldContain "http://localhost:8080/gamedays/$gorefest7/taste"
    }

    @Test
    fun `only the host can see it`() = test.webTest(session = kim) { client ->
        client.get(ready).status shouldBe HttpStatusCode.NotFound
        client.get("/gamedays/$gorefest7").page().select("a[href=$ready]") shouldHaveSize 0
    }
}
