package gorefest.shell.web.suggestions

import gorefest.shell.web.components.idSlug
import gorefest.core.GameBrought
import gorefest.core.PlayerId
import gorefest.core.PlayerJoined
import gorefest.core.gameday.anna
import gorefest.core.gameday.gameDayCreated
import gorefest.core.gameday.gorefest7
import gorefest.core.matching.Matcher
import gorefest.shell.bgg.DevCatalog
import gorefest.shell.web.TestApp
import gorefest.shell.web.components.of
import gorefest.shell.web.components.page
import gorefest.shell.web.fragment
import gorefest.shell.web.one
import gorefest.shell.web.page
import gorefest.shell.web.shouldReplace
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.get
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

class SuggestionsRoutesTest {
    private val test = TestApp()
    private val app = test.app
    private val kimId = PlayerId(Uuid.random())
    private lateinit var kim: String
    private fun id(name: String) = DevCatalog.games.first { it.name == name }.id

    @BeforeEach
    fun reset(): Unit = kotlinx.coroutines.runBlocking {
        test.reset()
        kim = test.loggedIn("Kim", id = kimId)
        test.loggedIn("Anna", id = anna)
        app.catalog.put(DevCatalog.games, test.clock.instant())
        val shelf = listOf("Azul", "Cascadia", "Brass: Birmingham", "Codenames", "Twilight Imperium: Fourth Edition").map { GameBrought(gorefest7, anna, id(it)) }
        app.db.transaction { app.store.append(listOf(gameDayCreated(host = anna), PlayerJoined(gorefest7, anna), PlayerJoined(gorefest7, kimId)) + shelf) }
        app.catchUp()
    }

    private val table = "/gamedays/$gorefest7/table"

    @Test
    fun `the hub has a way to pick a game`() = test.webTest(session = kim) { client ->
        client.get("/gamedays/$gorefest7").page().select("a[href=$table]") shouldHaveSize 1
    }

    @Test
    fun `everyone who joined starts at the table, and suggestions show right away`() = test.webTest(session = kim) { client ->
        val page = client.get(table).page()

        page.select(".gf-chip input[checked]").map { it.`val`() }.toSet() shouldBe setOf(anna.toString(), kimId.toString())
        page.one("#table-form").attr("hx-get") shouldBe "/gamedays/$gorefest7/suggestions"
        page.one("#table-form").attr("hx-trigger") shouldBe "change"
        page.one("input[name=time][checked]").`val`() shouldBe "Any"
        page.select("#suggestions article") shouldHaveSize 3
        page.select("#suggestions article .gf-game__meta").first()!!.text() shouldContain "for the 2 of you"
    }

    @Test
    fun `a suggestion says what kind of game it is`() = test.webTest(session = kim) { client ->
        val cards = client.get("/gamedays/$gorefest7/suggestions?players=$kimId&players=$anna&time=45m").fragment().select("article")

        cards.associate { it.one(".gf-game__name").text() to it.one(".gf-game__type").text() } shouldBe
            mapOf("Azul" to "Abstract · Tile placing", "Cascadia" to "Tile placing · Drafting", "Codenames" to "Party")
    }

    @Test
    fun `changing the table asks for new suggestions`() = test.webTest(session = kim) { client ->
        val fragment = client.get("/gamedays/$gorefest7/suggestions?players=$kimId&players=$anna&time=45m").fragment()

        fragment shouldReplace "suggestions"
        fragment.select(".gf-game__name").map { it.text() }.toSet() shouldBe setOf("Azul", "Cascadia", "Codenames")
    }

    @Test
    fun `when nothing fits, a button loosens the time`() = test.webTest(session = kim) { client ->
        val fragment = client.get("/gamedays/$gorefest7/suggestions?players=$kimId&players=$anna&time=45m&players=${PlayerId(Uuid.random())}").fragment()
        fragment.select("article") shouldHaveSize 3 // strangers are ignored

        app.db.transaction { app.store.append(listOf(gorefest.core.GameUnbrought(gorefest7, anna, id("Azul")), gorefest.core.GameUnbrought(gorefest7, anna, id("Cascadia")), gorefest.core.GameUnbrought(gorefest7, anna, id("Codenames")))) }
        app.catchUp()
        val tight = client.get("/gamedays/$gorefest7/suggestions?players=$kimId&players=$anna&time=45m").fragment()
        tight.one(".gf-empty__title").text() shouldBe "Nothing fits 2 players in 45 minutes"
        val relax = tight.one(".gf-empty button")
        relax.text() shouldBe "Try 90 minutes: 1 game fits"
        relax.attr("_") shouldContain "#time-90m"
    }

    @Test
    fun `without anyone's taste, the page says what the picks go by and offers the taste round`() = test.webTest(session = kim) { client ->
        val fragment = client.get("/gamedays/$gorefest7/suggestions").fragment()

        fragment.one(".gf-notice").text() shouldContain "Nobody at this table has done a taste round yet"
        fragment.one(".gf-notice").text() shouldContain "player count, playing time"
        fragment.one("a[href=/gamedays/$gorefest7/taste]").text() shouldBe "Take the 2-minute taste round"
    }

    @Test
    fun `when only others are unknown, it names them and leaves the viewer's own round out`() = test.webTest(session = kim) { client ->
        val answers = List(Matcher.KNOWN_AFTER) { gorefest.core.ComparisonRecorded(kimId, id("Azul"), id("Brass: Birmingham"), gorefest.core.ComparisonResult.A, gorefest.core.Basis.VIBE, test.clock.instant()) }
        app.db.transaction { app.store.append(answers) }
        app.catchUp()

        val fragment = client.get("/gamedays/$gorefest7/suggestions").fragment()

        fragment.one(".gf-notice").text() shouldBe "Picks for Anna go by player count and playing time: we haven't learned their taste yet."
        fragment.select("a[href=/gamedays/$gorefest7/taste]") shouldHaveSize 0
    }

    @Test
    fun `unticking everybody means nobody, not everybody`() = test.webTest(session = kim) { client ->
        val page = client.get(table).page()
        page.one("#table-form input[type=hidden][name=tableSet]").`val`() shouldBe "1"

        val nobody = client.get("/gamedays/$gorefest7/suggestions?tableSet=1&time=Any").fragment()
        nobody.one(".gf-empty__title").text() shouldBe "Who's playing?"
        nobody.select("article") shouldHaveSize 0

        client.get("/gamedays/$gorefest7/suggestions?time=Any").fragment().select("article") shouldHaveSize 3
    }

    @Test
    fun `every time option has an id a selector can use, and the relax button targets it`() = test.webTest(session = kim) { client ->
        val page = client.get(table).page()
        val ids = page.select("input[name=time]").map { it.id() }

        ids shouldBe TIMES.map { "time-" + it.first.idSlug() }
        ids.forEach { page.select("#$it") shouldHaveSize 1 }
        "time-3h-plus" shouldBe ids.last()
    }
}
