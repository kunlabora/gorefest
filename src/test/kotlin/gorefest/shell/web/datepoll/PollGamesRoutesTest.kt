package gorefest.shell.web.datepoll

import gorefest.core.BggId
import gorefest.core.DatePollAnswered
import gorefest.core.DatePollBringAnswered
import gorefest.core.DatePollCreated
import gorefest.core.DatePollGameAnswered
import gorefest.core.DatePollGameProposed
import gorefest.core.DatePollId
import gorefest.core.DatePollJoined
import gorefest.core.Event
import gorefest.core.PlayerId
import gorefest.shell.bgg.Collections
import gorefest.shell.bgg.DevCatalog
import gorefest.shell.web.TestApp
import gorefest.shell.web.htmxPost
import gorefest.shell.web.one
import gorefest.shell.web.page
import gorefest.shell.web.fragment
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.uuid.Uuid

/** U11: a date poll that asks about games, and the game night its pick makes. */
class PollGamesRoutesTest {
    private val test = TestApp()
    private val app = test.app
    private val annaId = PlayerId(Uuid.random())
    private val bramId = PlayerId(Uuid.random())
    private val rubenId = PlayerId(Uuid.random())
    private lateinit var anna: String
    private lateinit var bram: String
    private lateinit var ruben: String
    private val poll = DatePollId(Uuid.random())
    private fun id(name: String) = DevCatalog.games.first { it.name == name }.id
    private val brass = id("Brass: Birmingham")
    private val azul = id("Azul")
    private val humanity = id("Wingspan") // stands in for Humanity: tests keep to the dev catalog

    // The test clock says 24 September 2026.
    private val fri = LocalDate.parse("2026-10-09")
    private val sat = LocalDate.parse("2026-10-10")

    @BeforeEach
    fun reset(): Unit = kotlinx.coroutines.runBlocking {
        test.reset()
        anna = test.loggedIn("Anna", id = annaId)
        bram = test.loggedIn("Bram", id = bramId)
        ruben = test.loggedIn("Ruben", id = rubenId)
        app.catalog.put(DevCatalog.games, test.clock.instant())
        given(
            DatePollCreated(poll, annaId, "Small night", "Anna's", listOf(fri, sat), "Europe/Brussels", asksAboutGames = true),
            DatePollJoined(poll, annaId), DatePollJoined(poll, bramId), DatePollJoined(poll, rubenId),
        )
        app.db.transaction {
            Collections().replace(annaId, setOf(azul))
            Collections().replace(bramId, setOf(brass))
            Collections().replace(rubenId, setOf(humanity))
        }
    }

    private suspend fun given(vararg events: Event) {
        app.db.transaction { app.store.append(events.toList()) }
        app.catchUp()
    }

    private val pollPage get() = "/polls/$poll"

    @Test
    fun `starting a poll can switch on the games`() = test.webTest(session = anna) { client ->
        client.get("/polls/new").page().one("#poll-form input[name=games][role=switch]").attr("value") shouldBe "true"

        val response = client.htmxPost("/polls", parameters {
            append("title", "Games night"); append("dates", fri.toString()); append("dates", sat.toString()); append("timeZone", "Europe/Brussels"); append("games", "true")
        })

        val created = app.datePolls.forPlayer(annaId).first { it.title == "Games night" }
        created.asksAboutGames shouldBe true
        response.headers["HX-Redirect"] shouldBe "/polls/${created.id}"
    }

    @Test
    fun `the host adds one of their own games, or any game by search, and never sees friends' collections`() = test.webTest(session = anna) { client ->
        val page = client.get(pollPage).page()
        page.select(".gf-pollgames__pool .gf-game__name").map { it.text() } shouldBe listOf("Azul")

        val found = client.get("$pollPage/games/search?q=Wingspan") { header("HX-Request", "true") }.fragment()
        found.select(".gf-game__name").map { it.text() } shouldBe listOf("Wingspan")

        client.submitForm("$pollPage/games", parameters { append("game", humanity.toString()) }).headers["Location"] shouldBe "$pollPage#poll-games"
        app.datePolls.of(poll)!!.proposals shouldBe listOf(humanity)
    }

    @Test
    fun `the poll form suggests the host's own games to tick, and the ticked ones go in with the poll`() = test.webTest(session = anna) { client ->
        val form = client.get("/polls/new").page().one("#poll-form")
        form.select(".gf-poll-form__gamepick input[name=game][type=checkbox]").map { it.`val`() } shouldBe listOf(azul.toString())
        form.select(".gf-poll-form__gamepick").text().contains("Brass") shouldBe false

        val found = client.get("/polls/new/games/search?q=Wingspan") { header("HX-Request", "true") }.fragment()
        found.select("input[name=game][type=checkbox]").map { it.`val`() } shouldBe listOf(humanity.toString())
        // Anna's own Azul is a tile on the form already: the search points there instead of repeating it.
        val own = client.get("/polls/new/games/search?q=Azul") { header("HX-Request", "true") }.fragment()
        own.select("input[name=game]") shouldHaveSize 0
        own.text() shouldContain "Azul is in your games above."

        client.htmxPost("/polls", parameters {
            append("title", "Games night"); append("dates", fri.toString()); append("dates", sat.toString()); append("timeZone", "Europe/Brussels")
            append("games", "true"); append("game", azul.toString()); append("game", humanity.toString())
        })
        app.datePolls.forPlayer(annaId).first { it.title == "Games night" }.proposals shouldBe listOf(azul, humanity)
    }

    @Test
    fun `a refused form keeps the games ticked, the searched ones too`() = test.webTest(session = anna) { client ->
        val refused = client.htmxPost("/polls", parameters {
            append("title", " "); append("dates", fri.toString()); append("dates", sat.toString()); append("timeZone", "Europe/Brussels")
            append("games", "true"); append("game", humanity.toString())
        }).fragment()

        refused.select("input[name=game][checked]").map { it.`val`() } shouldBe listOf(humanity.toString())
        refused.one("input[name=games]").hasAttr("checked") shouldBe true
    }

    @Test
    fun `the host's add buttons take them to the search, which is right there when there are no games yet`() = test.webTest(session = anna) { client ->
        val empty = client.get(pollPage).page()
        empty.one(".gf-poll-signpost a[href=#add-game-q]").text() shouldBe "Add games"
        empty.select("#poll-games .gf-sechead a") shouldHaveSize 0
        empty.one("#poll-games h2").text() shouldBe "Which games would you play?"
        empty.one("input#add-game-q[type=search]")

        given(DatePollGameProposed(poll, azul))
        val one = client.get(pollPage).page()
        one.one("#poll-games h2").text() shouldBe "Which games would you play? · 1"
        one.one("#poll-games .gf-sechead a[href=#add-game-q]").attr("_") shouldContain "focus()"
    }

    @Test
    fun `only the host adds games`() = test.webTest(session = bram) { client ->
        client.submitForm("$pollPage/games", parameters { append("game", humanity.toString()) }).status shouldBe HttpStatusCode.Conflict
        client.get(pollPage).page().select(".gf-pollgames__add") shouldHaveSize 0
        client.get("$pollPage/games/search?q=Wingspan").status shouldBe HttpStatusCode.NotFound
    }

    @Test
    fun `a friend who has a proposed game is asked to bring it, and anyone can offer`() {
        kotlinx.coroutines.runBlocking { given(DatePollGameProposed(poll, humanity), DatePollGameProposed(poll, brass)) }
        test.webTest(session = ruben) { client ->
            val page = client.get(pollPage).page()
            page.one("#bring-prompt-$humanity").text() shouldContain "You have Wingspan. Bring it if we play?"
            page.select("#bring-prompt-$brass") shouldHaveSize 0

            client.submitForm("$pollPage/games/$humanity/bring", parameters { append("bringing", "true") })
            client.get(pollPage).page().select("#bring-prompt-$humanity") shouldHaveSize 0
        }
        test.webTest(session = anna) { client ->
            // Brass isn't in Anna's collection, but she can still offer to bring it.
            client.submitForm("$pollPage/games/$brass/bring", parameters { append("bringing", "true") })
        }
        app.datePolls.of(poll)!!.bringers(humanity) shouldBe listOf(rubenId)
        app.datePolls.of(poll)!!.bringers(brass) shouldBe listOf(annaId)
    }

    @Test
    fun `not this time answers the prompt without offering`() = test.webTest(session = ruben) { client ->
        given(DatePollGameProposed(poll, humanity))

        client.submitForm("$pollPage/games/$humanity/bring", parameters { append("bringing", "false") })

        client.get(pollPage).page().select("#bring-prompt-$humanity") shouldHaveSize 0
        app.datePolls.of(poll)!!.bringers(humanity) shouldBe emptyList()
    }

    @Test
    fun `a yes is a heart, three at most, and the refusal says so`() = test.webTest(session = bram) { client ->
        val games = listOf(brass, azul, humanity, id("Cascadia"))
        given(*games.map { DatePollGameProposed(poll, it) }.toTypedArray(), *games.take(3).map { DatePollGameAnswered(poll, bramId, it, true) }.toTypedArray())

        val refused = client.submitForm("$pollPage/games/${games[3]}/answer", parameters { append("yes", "true") })

        refused.status shouldBe HttpStatusCode.Conflict
        refused.page().one("#poll-games [role=alert]").text() shouldContain "take one off first"
    }

    @Test
    fun `each game says who brings it, or that nobody can yet`() = test.webTest(session = anna) { client ->
        given(DatePollGameProposed(poll, humanity), DatePollGameProposed(poll, azul), DatePollBringAnswered(poll, rubenId, humanity, true), DatePollGameAnswered(poll, bramId, humanity, true))

        val page = client.get(pollPage).page()

        page.one("#poll-game-$humanity .gf-pollgame__bring").text() shouldBe "Ruben brings it"
        page.one("#poll-game-$humanity").text() shouldContain "Bram wants to play it"
        page.one("#poll-game-$azul .gf-pollgame__bring").text() shouldBe "Nobody brings it yet"
    }

    @Test
    fun `a date card says which games that day would lose, and why`() = test.webTest(session = anna) { client ->
        given(
            DatePollGameProposed(poll, humanity), DatePollGameProposed(poll, azul), DatePollBringAnswered(poll, rubenId, humanity, true),
            DatePollAnswered(poll, rubenId, listOf(sat)), DatePollAnswered(poll, bramId, listOf(fri, sat)),
        )

        val cards = client.get(pollPage).page().select("#poll-results .gf-opt")

        // Azul has nobody to bring it on any date: its own card says so, the dates don't repeat it.
        cards[0].select(".gf-opt__games").map { it.text() } shouldBe listOf("No Wingspan: Ruben can't come")
        cards[1].select(".gf-opt__games") shouldHaveSize 0

        given(DatePollBringAnswered(poll, annaId, azul, true))
        client.get(pollPage).page().select("#poll-results .gf-opt")[1].one(".gf-opt__games").text() shouldBe "Someone brings every game"
    }

    @Test
    fun `the games are signposted next to the dates, so nobody misses them`() = test.webTest(session = bram) { client ->
        given(DatePollGameProposed(poll, humanity), DatePollGameProposed(poll, azul), DatePollGameAnswered(poll, bramId, azul, true))

        val signpost = client.get(pollPage).page().one(".gf-poll-signpost")

        signpost.text() shouldContain "2 games to choose from · you answered 1 · 2 yeses left"
        signpost.one("a[href=#poll-games]").text() shouldBe "Answer the games"
    }

    @Test
    fun `an answer shows as a pressed chip`() = test.webTest(session = bram) { client ->
        given(DatePollGameProposed(poll, azul), DatePollGameAnswered(poll, bramId, azul, true))

        val card = client.get(pollPage).page().one("#poll-game-$azul")

        card.select("button[name=yes][aria-pressed=true]").map { it.text() } shouldBe listOf("I'd play this")
        card.one("button[name=bringing]").attr("aria-pressed") shouldBe "false"
    }

    @Test
    fun `picking the date makes the game night, with its plan, and the poll links to it`() {
        kotlinx.coroutines.runBlocking {
            given(
                DatePollGameProposed(poll, humanity), DatePollGameProposed(poll, azul), DatePollBringAnswered(poll, rubenId, humanity, true),
                DatePollGameAnswered(poll, bramId, humanity, true),
                DatePollAnswered(poll, rubenId, listOf(sat)), DatePollAnswered(poll, bramId, listOf(fri, sat)),
            )
        }
        lateinit var nightPage: String
        test.webTest(session = anna) { client ->
            nightPage = client.htmxPost("$pollPage/pick", parameters { append("date", sat.toString()) }).headers["HX-Redirect"]!!
            nightPage shouldStartWith "/gamenights/"
        }
        test.webTest(session = bram) { client ->
            val night = client.get(nightPage).page()
            night.one("h1").text() shouldBe "Small night"
            night.select("#plan > .gf-list .gf-game").map { it.one(".gf-game__name").text() to it.one(".gf-game__meta").text() } shouldBe
                listOf("Wingspan" to "Ruben brings it · you want to play it")
            night.select("#missing .gf-game__name").map { it.text() } shouldBe listOf("Azul")
            night.one("a[href=$nightPage/table]").text() shouldContain "Pick a game for my table"

            client.get(pollPage).page().one("a[href=$nightPage]").text() shouldBe "Open Small night"
            client.get("/").page().select("a.gf-daycard[href=$nightPage]") shouldHaveSize 1
        }
    }

    @Test
    fun `the game night's table suggests from its plan, and wishes come from the yeses`() {
        kotlinx.coroutines.runBlocking {
            given(
                DatePollGameProposed(poll, humanity), DatePollBringAnswered(poll, rubenId, humanity, true), DatePollGameAnswered(poll, bramId, humanity, true),
                DatePollAnswered(poll, rubenId, listOf(sat)), DatePollAnswered(poll, bramId, listOf(sat)),
            )
        }
        test.webTest(session = anna) { client -> client.htmxPost("$pollPage/pick", parameters { append("date", sat.toString()) }) }
        val night = app.gameNights.forPlayer(bramId).single()

        test.webTest(session = bram) { client ->
            val table = client.get("/gamenights/${night.id}/table").page()
            table.select(".gf-ranked .gf-game__name, .gf-rank__name, h3").map { it.text() }.joinToString() shouldContain "Wingspan"
            table.one("#table-form").attr("hx-get") shouldBe "/gamenights/${night.id}/suggestions"
        }
    }

    @Test
    fun `a game night is for the people on it`() {
        kotlinx.coroutines.runBlocking { given(DatePollAnswered(poll, rubenId, listOf(sat))) }
        test.webTest(session = anna) { client -> client.htmxPost("$pollPage/pick", parameters { append("date", sat.toString()) }) }
        val night = app.gameNights.forPlayer(annaId).single()

        test.webTest(session = bram) { client ->
            // Bram didn't say he could come on Saturday.
            client.get("/gamenights/${night.id}").status shouldBe HttpStatusCode.NotFound
            client.get(pollPage).page().body().text() shouldContain "You weren't down as available that day"
        }
    }

    @Test
    fun `a poll whose host's account was deleted says it's over, and its link takes nobody in`() {
        kotlinx.coroutines.runBlocking { given(gorefest.core.PlayerErased(annaId)) }
        test.webTest(session = bram) { client ->
            client.get(pollPage).page().body().text() shouldContain "This poll is over: the host is no longer on Boardfest"
            client.get("/").page().select("a.gf-daycard[href=$pollPage]") shouldHaveSize 0
        }
        test.webTest(session = test.app.let { kotlinx.coroutines.runBlocking { test.loggedIn("Kim") } }) { client ->
            client.get("/p/${app.invites.token(poll)}").status shouldBe HttpStatusCode.Gone
        }
    }
}

