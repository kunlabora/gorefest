package gorefest.shell.web.taste

import io.kotest.matchers.string.shouldContain
import gorefest.core.BggId
import gorefest.core.ComparisonRecorded
import gorefest.core.ComparisonResult
import gorefest.core.GameBrought
import gorefest.core.GamePlayStatusDeclared
import gorefest.core.PlayerId
import gorefest.core.PlayerJoined
import gorefest.core.gameday.anna
import gorefest.core.gameday.gameDayCreated
import gorefest.core.gameday.gorefest7
import gorefest.shell.bgg.Collections
import gorefest.shell.bgg.DevCatalog
import gorefest.shell.web.TestApp
import gorefest.shell.web.components.duel
import gorefest.shell.web.components.long
import gorefest.shell.web.components.page
import gorefest.shell.web.fragment
import gorefest.shell.web.htmxPost
import gorefest.shell.web.one
import gorefest.shell.web.page
import gorefest.shell.web.redirect
import gorefest.shell.web.shouldReplace
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.http.parameters
import org.jsoup.nodes.Element
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

class TasteRoutesTest {
    private val test = TestApp()
    private val app = test.app
    private val kimId = PlayerId(Uuid.random())
    private lateinit var kim: String
    private val shelf = listOf("Azul", "Cascadia", "Brass: Birmingham", "Root", "Wingspan", "Heat: Pedal to the Metal")
        .map { name -> DevCatalog.games.first { it.name == name }.id }

    @BeforeEach
    fun reset(): Unit = kotlinx.coroutines.runBlocking {
        test.reset()
        kim = test.loggedIn("Kim", id = kimId)
        test.loggedIn("Anna", id = anna)
        app.catalog.put(DevCatalog.games, test.clock.instant())
        given(gameDayCreated(host = anna), PlayerJoined(gorefest7, anna), PlayerJoined(gorefest7, kimId), *shelf.map { GameBrought(gorefest7, anna, it) }.toTypedArray())
    }

    private suspend fun given(vararg events: gorefest.core.Event) {
        app.db.transaction { app.store.append(events.toList()) }
        app.catchUp()
    }

    private val taste = "/gamedays/$gorefest7/taste"

    /** Posts [answer] for the pair currently shown in [wizard], with the round's hidden fields. */
    private suspend fun HttpClient.answer(wizard: Element, answer: String) = htmxPost(taste, parameters {
        wizard.select("input[type=hidden]").forEach { append(it.attr("name"), it.`val`()) }
        append("answer", answer)
    })

    @Test
    fun `the game day points newcomers to the taste round`() = test.webTest(session = kim) { client ->
        client.get("/gamedays/$gorefest7").page().one(".gf-next a").attr("href") shouldBe taste
    }

    @Test
    fun `the round shows two games and five ways to answer`() = test.webTest(session = kim) { client ->
        val page = client.get(taste).page()
        val wizard = page.one("main#wizard")

        wizard.one("h1").text() shouldBe "Which would you rather try?"
        wizard.select(".gf-pick").map { it.attr("value") } shouldBe listOf("A", "B")
        wizard.select(".gf-answer").map { it.attr("value") } shouldBe listOf("SKIP", "BOTH_MEH", "BOTH_GREAT")
        wizard.one("form").attr("hx-target") shouldBe "#wizard"
        wizard.one(".gf-progress__count").text() shouldBe "1 / 15"
        page.one(".gf-appbar a").attr("href") shouldBe "/gamedays/$gorefest7/favourites"
    }

    @Test
    fun `an answer is recorded and the next pair is dealt`() = test.webTest(session = kim) { client ->
        val first = client.get(taste).page().one("#wizard")
        val a = BggId(first.one("input[name=a]").`val`().toInt())

        val next = client.answer(first, "A").fragment()

        next shouldReplace "wizard"
        next.one(".gf-progress__count").text() shouldBe "2 / 15"
        next.one(".gf-duel").hasClass("gf-deal") shouldBe true
        val recorded = app.tastes.of(kimId).comparisons.single()
        (recorded.a to recorded.result) shouldBe (a to ComparisonResult.A)
        setOf(next.one("input[name=a]").`val`(), next.one("input[name=b]").`val`()) shouldBe
            setOf(next.one("input[name=a]").`val`(), next.one("input[name=b]").`val`()).also {
                (it == setOf(first.one("input[name=a]").`val`(), first.one("input[name=b]").`val`())) shouldBe false
            }
    }

    @Test
    fun `played pairs ask about enjoyment, and 'haven't played' is remembered`() {
        kotlinx.coroutines.runBlocking { app.db.transaction { Collections().replace(kimId, shelf.toSet()) } }
        test.webTest(session = kim) { client ->
            val first = client.get(taste).page().one("#wizard")
            first.one("h1").text() shouldBe "Which did you enjoy more?"

            client.answer(first, "UNPLAYED")

            val history = app.tastes.of(kimId)
            history.comparisons shouldHaveSize 0
            history.played.values.toSet() shouldBe setOf(false)
            history.played.keys shouldBe setOf(first.one("input[name=a]").`val`(), first.one("input[name=b]").`val`()).map { BggId(it.toInt()) }.toSet()
        }
    }

    @Test
    fun `after the last pair the payoff shows favourites from the shelf`() = test.webTest(session = kim) { client ->
        var wizard = client.get(taste).page().one("#wizard")
        var redirect: String? = null
        repeat(15) {
            if (redirect != null) return@repeat
            val response = client.answer(wizard, "A")
            redirect = response.headers["HX-Redirect"]
            if (redirect == null) wizard = response.fragment()
        }

        redirect!! shouldStartWith "/gamedays/$gorefest7/favourites?answers="
        val payoff = client.get(redirect!!).page()
        payoff.one("h1").text() shouldBe "Your likely favourites today"
        payoff.select(".gf-podium li") shouldHaveSize 3
        payoff.select(".gf-game__reason").forEach { (it.text().startsWith("You picked it over") || it.text() == "Just a guess so far: it hasn't won a pair yet.") shouldBe true }
        payoff.one(".gf-kicker").text() shouldStartWith "Round done ·"
    }

    @Test
    fun `with an empty shelf the payoff says so`() {
        val other = kotlinx.coroutines.runBlocking {
            val id = PlayerId(Uuid.random())
            val session = test.loggedIn("Jo", id = id)
            val empty = gorefest.core.GameDayId(Uuid.random())
            given(gameDayCreated(gameDay = empty, host = id), PlayerJoined(empty, id))
            session to empty
        }
        test.webTest(session = other.first) { client ->
            val payoff = client.get("/gamedays/${other.second}/favourites").page()
            payoff.one("h1").text() shouldBe "Nice taste. Now we need games."
            payoff.one(".gf-empty a").attr("href") shouldBe "/gamedays/${other.second}/bring"
        }
    }

    @Test
    fun `after a round, uncertain shelf games lead to a short top-up`() = test.webTest(session = kim) { client ->
        given(*List(10) { ComparisonRecorded(kimId, shelf[0], shelf[1], ComparisonResult.A, gorefest.core.Basis.VIBE, test.clock.instant()) }.toTypedArray())

        val next = client.get("/gamedays/$gorefest7").page().one(".gf-next")
        next.one("h2").text() shouldBe "Sharpen your picks"
        next.one(".gf-next__text").text() shouldStartWith "Four games on the shelf we're not sure about"
        next.one("a").attr("href") shouldBe taste

        val round = client.get(taste).page().one("#wizard")
        round.one("input[name=mode]").`val`() shouldBe "topup"
        round.one(".gf-progress__count").text() shouldBe "1 / 5"
        val pair = setOf(round.one("input[name=a]").`val`(), round.one("input[name=b]").`val`()).map { BggId(it.toInt()) }
        pair.any { it in shelf.drop(2) } shouldBe true
    }

    @Test
    fun `when the whole shelf is well known, the game day points to the favourites`() = test.webTest(session = kim) { client ->
        val answers = (0 until 4).flatMap { round -> shelf.zipWithNext().map { (a, b) -> ComparisonRecorded(kimId, a, b, ComparisonResult.A, gorefest.core.Basis.VIBE, test.clock.instant()) } }
        given(*answers.toTypedArray())

        client.get("/gamedays/$gorefest7").page().one(".gf-next a").attr("href") shouldBe "/gamedays/$gorefest7/favourites"
        GamePlayStatusDeclared(kimId, shelf[0], true).played shouldBe true
    }

    @Test
    fun `a top-up re-asks an answer from long ago first`() = test.webTest(session = kim) { client ->
        val longAgo = test.clock.instant() - java.time.Duration.ofDays(400)
        given(
            ComparisonRecorded(kimId, shelf[4], shelf[5], ComparisonResult.A, gorefest.core.Basis.VIBE, longAgo),
            *List(10) { ComparisonRecorded(kimId, shelf[0], shelf[1], ComparisonResult.A, gorefest.core.Basis.VIBE, test.clock.instant()) }.toTypedArray(),
        )

        val round = client.get(taste).page().one("#wizard")

        (round.one("input[name=a]").`val`() to round.one("input[name=b]").`val`()) shouldBe (shelf[4].toString() to shelf[5].toString())
    }

    @Test
    fun `the A and B keys ignore shortcuts, key repeat and typing in fields`() = test.webTest(session = kim) { client ->
        val script = client.get(taste).page().one("main#wizard form").attr("_")

        listOf("event.repeat", "event.metaKey", "event.ctrlKey", "event.altKey", "matches('input, textarea").forEach { script shouldContain it }
        script shouldContain "#pick-a.click()"
    }

    @Test
    fun `the BGG credit survives an answer`() = test.webTest(session = kim) { client ->
        val first = client.get(taste).page().one("#wizard")
        first.select(".gf-credit .gf-credit__logo img") shouldHaveSize 1

        val next = client.answer(first, "A").fragment()
        next.select(".gf-credit .gf-credit__logo img") shouldHaveSize 1
    }
}
