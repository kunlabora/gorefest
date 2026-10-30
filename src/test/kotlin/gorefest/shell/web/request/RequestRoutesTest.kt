package gorefest.shell.web.request

import gorefest.contract.Obligation
import gorefest.core.BggAccountLinked
import gorefest.core.BggId
import gorefest.core.BringRequestDeclined
import gorefest.core.BringRequested
import gorefest.core.BringRequestsAllowedChanged
import gorefest.core.Event
import gorefest.core.GameBrought
import gorefest.core.GameDayCancelled
import gorefest.core.GameDayEnded
import gorefest.core.PlayerId
import gorefest.core.PlayerJoined
import gorefest.core.gameday.RequestStatus
import gorefest.core.gameday.anna
import gorefest.core.gameday.gameDayCreated
import gorefest.core.gameday.gorefest7
import gorefest.shell.bgg.Collections
import gorefest.shell.bgg.DevCatalog
import gorefest.shell.web.TestApp
import gorefest.shell.web.fragment
import gorefest.shell.web.htmxPost
import gorefest.shell.web.one
import gorefest.shell.web.page
import gorefest.shell.web.shouldReplace
import gorefest.shell.web.texts
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

class RequestRoutesTest {
    private val test = TestApp()
    private val app = test.app
    private val kimId = PlayerId(Uuid.random())
    private val bramId = PlayerId(Uuid.random())
    private val outsiderId = PlayerId(Uuid.random())
    private lateinit var kim: String
    private lateinit var annaSession: String
    private lateinit var outsider: String
    private val brass = BggId(224517)
    private val azul = BggId(230802)
    private val catan = BggId(13)

    private val ask = "/gamedays/$gorefest7/ask"

    @BeforeEach
    fun reset(): Unit = kotlinx.coroutines.runBlocking {
        test.reset()
        kim = test.loggedIn("Kim", id = kimId)
        annaSession = test.loggedIn("Anna", id = anna)
        test.loggedIn("Bram", id = bramId)
        outsider = test.loggedIn("Olga", id = outsiderId)
        app.catalog.put(DevCatalog.games, test.clock.instant())
        given(
            gameDayCreated(host = anna), PlayerJoined(gorefest7, anna), PlayerJoined(gorefest7, kimId), PlayerJoined(gorefest7, bramId),
            BggAccountLinked(anna, "AnnaPlays"), BggAccountLinked(kimId, "KimPlays"),
        )
        app.db.transaction {
            Collections().replace(anna, setOf(brass, azul))
            Collections().replace(bramId, setOf(catan))
            Collections().replace(kimId, setOf(catan))
        }
    }

    private suspend fun given(vararg events: Event) {
        app.db.transaction { app.store.append(events.toList()) }
        app.catchUp()
    }

    private fun asks(game: BggId, owner: PlayerId = anna) = parameters { append("owner", owner.toString()); append("game", game.toString()) }

    @Test
    @Obligation("SH-22")
    fun `the ask screen lists the other members, and links to their collections`() = test.webTest(session = kim) { client ->
        val page = client.get(ask).page()

        page.texts(".gf-list .gf-row__title") shouldContainExactly listOf("Anna", "Bram")
        page.select("a[href=/gamedays/$gorefest7/ask/from/$anna]").size shouldBe 1
    }

    @Test
    @Obligation("SH-22")
    fun `a member's collection is listed with an ask button per game`() = test.webTest(session = kim) { client ->
        val page = client.get("$ask/from/$anna").page()

        page.texts("#ask-list .gf-game__name") shouldContainExactly listOf("Azul", "Brass: Birmingham")
        val button = page.one("#ask-$anna-$brass button")
        button.attr("hx-post") shouldBe ask
        button.attr("hx-target") shouldBe "#ask-$anna-$brass"
    }

    @Test
    @Obligation("SH-22")
    fun `filtering a member's games answers with the list`() = test.webTest(session = kim) { client ->
        val list = client.get("$ask/from/$anna/games?q=bra").fragment()

        list shouldReplace "ask-list"
        list.texts(".gf-game__name") shouldContainExactly listOf("Brass: Birmingham")
    }

    @Test
    @Obligation("SH-21")
    fun `asking puts a request with the owner, and the row says Asked`() = test.webTest(session = kim) { client ->
        val response = client.htmxPost(ask, asks(brass))
        val row = response.fragment()

        row shouldReplace "ask-$anna-$brass"
        row.select("button").shouldBeEmpty()
        row.one(".gf-tag").text() shouldBe "Asked"
        response.headers["HX-Trigger"] shouldBe "requests-changed"
        app.bringRequests.openFor(anna, gorefest7).map { it.asker to it.game } shouldBe listOf(kimId to brass)
    }

    @Test
    @Obligation("SH-23")
    fun `asking twice counts once`() = test.webTest(session = kim) { client ->
        client.htmxPost(ask, asks(brass))
        client.htmxPost(ask, asks(brass)).status shouldBe HttpStatusCode.OK

        app.bringRequests.on(gorefest7).size shouldBe 1
    }

    @Test
    @Obligation("SH-21")
    fun `a game the owner doesn't have can't be asked for`() = test.webTest(session = kim) { client ->
        client.htmxPost(ask, asks(catan)).status shouldBe HttpStatusCode.NotFound

        app.bringRequests.on(gorefest7).shouldBeEmpty()
    }

    @Test
    @Obligation("SH-22")
    fun `searching for a game shows who has it`() = test.webTest(session = kim) { client ->
        val results = client.get("/gamedays/$gorefest7/ask-search?q=catan").fragment()

        results shouldReplace "ask-results"
        results.texts(".gf-game__meta").single() shouldContain "Bram"
        results.select("button").size shouldBe 1
        client.get("/gamedays/$gorefest7/ask-search?q=brass").fragment().texts(".gf-game__meta").single() shouldContain "Anna"
    }

    @Test
    @Obligation("SH-24")
    fun `a game somebody already brings shows as bringing`() = test.webTest(session = kim) { client ->
        given(GameBrought(gorefest7, anna, brass))

        client.get("$ask/from/$anna").page().one("#ask-$anna-$brass .gf-tag").text() shouldBe "Bringing it"
        client.htmxPost(ask, asks(brass)).status shouldBe HttpStatusCode.Conflict
    }

    @Test
    @Obligation("SH-25")
    fun `an asker gets three requests, and the fourth says why not`() = test.webTest(session = kim) { client ->
        given(PlayerJoined(gorefest7, outsiderId))
        app.db.transaction { Collections().replace(outsiderId, setOf(catan, brass, azul)) }
        client.htmxPost(ask, asks(brass))
        client.htmxPost(ask, asks(azul))
        client.htmxPost(ask, asks(catan, outsiderId))

        val response = client.htmxPost(ask, asks(catan, bramId))

        response.status shouldBe HttpStatusCode.Conflict
        response.fragment().one(".gf-error").text() shouldContain "take one back first"
        app.bringRequests.on(gorefest7).size shouldBe 3
    }

    @Test
    @Obligation("SH-25", "SH-30")
    fun `taking a request back frees the slot`() = test.webTest(session = kim) { client ->
        client.htmxPost(ask, asks(brass))
        client.htmxPost(ask, asks(azul))
        client.htmxPost(ask, asks(catan, bramId))

        val mine = client.htmxPost("$ask/withdraw", asks(brass)).fragment()

        mine shouldReplace "my-requests"
        mine.texts(".gf-row__title") shouldContainExactly listOf("Azul", "CATAN")
        app.bringRequests.by(kimId, gorefest7).map { it.game } shouldBe listOf(azul, catan)
        client.htmxPost(ask, asks(brass)).status shouldBe HttpStatusCode.OK
    }

    @Test
    @Obligation("SH-32")
    fun `an owner who turned requests off isn't listed and can't be asked`() = test.webTest(session = kim) { client ->
        given(BringRequestsAllowedChanged(anna, false))

        client.get(ask).page().texts(".gf-list .gf-row__title") shouldContainExactly listOf("Bram")
        client.get("$ask/from/$anna").status shouldBe HttpStatusCode.NotFound
        client.get("/gamedays/$gorefest7/ask-search?q=brass").fragment().select(".gf-game").shouldBeEmpty()
        val response = client.htmxPost(ask, asks(brass))
        response.status shouldBe HttpStatusCode.Conflict
        response.fragment().one(".gf-error").text() shouldBe "They'd rather not be asked to bring games"
    }

    @Test
    @Obligation("SH-30")
    fun `my requests show how they went`() = test.webTest(session = kim) { client ->
        given(
            BringRequested(gorefest7, kimId, anna, brass), BringRequested(gorefest7, kimId, anna, azul), BringRequested(gorefest7, kimId, bramId, catan),
            GameBrought(gorefest7, anna, brass), BringRequestDeclined(gorefest7, anna, azul, kimId),
        )

        val mine = client.get("$ask/mine").fragment()

        mine.texts(".gf-row__sub") shouldContainExactly listOf("Anna is bringing it", "Not this time", "Asked Bram")
        // Only an open request can be taken back; a decline stays quiet, with nothing to do about it.
        mine.select("button").size shouldBe 1
        mine.one("button").attr("hx-vals") shouldContain catan.toString()
    }

    @Test
    @Obligation("SH-28")
    fun `the owner sees who asks on the bring page and brings it with one tap`() = test.webTest(session = annaSession) { client ->
        given(BringRequested(gorefest7, kimId, anna, brass), BringRequested(gorefest7, bramId, anna, brass), BringRequested(gorefest7, kimId, anna, azul))

        val page = client.get("/gamedays/$gorefest7/bring").page()

        page.texts("#bring-requests .gf-row__title") shouldContainExactly listOf(
            "Kim and Bram ask if you can bring Brass: Birmingham", "Kim asks if you can bring Azul",
        )
        val bring = page.one("#request-$brass button[aria-label^=Bring]")
        bring.attr("hx-post") shouldBe "/gamedays/$gorefest7/shelf/$brass"
        bring.attr("hx-target") shouldBe "#bring-requests"

        val response = client.htmxPost("/gamedays/$gorefest7/shelf/$brass", parameters { append("bringing", "true"); append("row", "request") })
        val section = response.fragment()

        section shouldReplace "bring-requests"
        section.texts(".gf-row__title") shouldContainExactly listOf("Kim asks if you can bring Azul")
        response.headers["HX-Trigger"] shouldBe "bring-list-changed"
        app.shelves.of(gorefest7).map { it.game } shouldBe listOf(brass)
        app.bringRequests.on(gorefest7).map { it.status } shouldBe listOf(RequestStatus.ANSWERED, RequestStatus.ANSWERED, RequestStatus.OPEN)
    }

    @Test
    @Obligation("SH-28")
    fun `ticking the game from the collection list answers the request too`() = test.webTest(session = annaSession) { client ->
        given(BringRequested(gorefest7, kimId, anna, brass))

        client.htmxPost("/gamedays/$gorefest7/shelf/$brass", parameters { append("bringing", "true") })

        app.bringRequests.openFor(anna, gorefest7).shouldBeEmpty()
        client.get("/gamedays/$gorefest7/ask/requests").fragment().select("li").shouldBeEmpty()
    }

    @Test
    @Obligation("SH-29")
    fun `not this time declines every asker of the row, quietly`() = test.webTest(session = annaSession) { client ->
        given(BringRequested(gorefest7, kimId, anna, brass), BringRequested(gorefest7, bramId, anna, brass))

        val section = client.htmxPost("$ask/decline", parameters { append("game", brass.toString()) }).fragment()

        section shouldReplace "bring-requests"
        section.select("li").shouldBeEmpty()
        app.bringRequests.on(gorefest7).map { it.status } shouldBe listOf(RequestStatus.DECLINED, RequestStatus.DECLINED)
        app.shelves.of(gorefest7).shouldBeEmpty()
    }

    @Test
    @Obligation("SH-29")
    fun `an owner can't decline for somebody else`() = test.webTest(session = annaSession) { client ->
        given(BringRequested(gorefest7, kimId, bramId, catan))

        client.htmxPost("$ask/decline", parameters { append("game", catan.toString()) }).fragment().select("li").shouldBeEmpty()

        app.bringRequests.openFor(bramId, gorefest7).size shouldBe 1
    }

    @Test
    @Obligation("SH-31")
    fun `the hub's next step tells the owner something is waiting, and the home hero tags it`() = test.webTest(session = annaSession) { client ->
        given(BringRequested(gorefest7, kimId, anna, brass))

        val hub = client.get("/gamedays/$gorefest7").page()
        hub.one(".gf-next__title").text() shouldBe "Can you bring Brass: Birmingham?"
        hub.one(".gf-next__text").text() shouldContain "Kim asked you to bring it"
        hub.one(".gf-next a").attr("href") shouldBe "/gamedays/$gorefest7/bring"

        client.get("/").page().one(".gf-home__hero .gf-tag--accent").text() shouldBe "Asked to bring a game"
    }

    @Test
    @Obligation("SH-31")
    fun `the host's readiness view counts the open requests`() = test.webTest(session = annaSession) { client ->
        client.get("/gamedays/$gorefest7/ready").page().select(".gf-notice").map { it.text() }.none { "bring request" in it } shouldBe true
        given(BringRequested(gorefest7, kimId, bramId, catan), BringRequested(gorefest7, bramId, anna, brass))

        client.get("/gamedays/$gorefest7/ready").page().select(".gf-notice").map { it.text() }.any { it.startsWith("2 bring requests waiting") } shouldBe true
    }

    @Test
    @Obligation("SH-33")
    fun `somebody who didn't join sees nothing and can ask nothing`() = test.webTest(session = outsider) { client ->
        client.get(ask).status shouldBe HttpStatusCode.NotFound
        client.get("$ask/from/$anna").status shouldBe HttpStatusCode.NotFound
        client.get("/gamedays/$gorefest7/ask-search?q=brass").status shouldBe HttpStatusCode.NotFound
        client.get("$ask/mine").status shouldBe HttpStatusCode.NotFound
        client.get("$ask/requests").status shouldBe HttpStatusCode.NotFound
        client.htmxPost(ask, asks(brass)).status shouldBe HttpStatusCode.NotFound
        client.htmxPost("$ask/decline", parameters { append("game", brass.toString()) }).status shouldBe HttpStatusCode.NotFound
        app.bringRequests.on(gorefest7).shouldBeEmpty()
    }

    @Test
    @Obligation("SH-33")
    fun `an ended game day is read-only`() = test.webTest(session = kim) { client ->
        given(BringRequested(gorefest7, kimId, anna, brass), GameDayEnded(gorefest7))

        client.get(ask).status shouldBe HttpStatusCode.NotFound
        val response = client.htmxPost(ask, asks(azul))
        response.status shouldBe HttpStatusCode.Conflict
        response.fragment().one(".gf-error").text() shouldBe "This game day has ended"
        client.htmxPost("$ask/withdraw", asks(brass)).status shouldBe HttpStatusCode.Conflict
        app.bringRequests.openBy(kimId, gorefest7).size shouldBe 1
    }

    @Test
    @Obligation("SH-33")
    fun `a cancelled game day is gone`() = test.webTest(session = kim) { client ->
        given(GameDayCancelled(gorefest7))

        client.get(ask).status shouldBe HttpStatusCode.NotFound
        client.htmxPost(ask, asks(brass)).status shouldBe HttpStatusCode.NotFound
    }

    @Test
    @Obligation("SH-32")
    fun `the collection page has the switch, on by default, and flipping it opts out`() = test.webTest(session = kim) { client ->
        val page = client.get("/collection").page()
        val switch = page.one("#bring-requests-switch input.gf-switch")
        switch.hasAttr("checked") shouldBe true
        page.one("#bring-requests-switch .gf-row__title").text() shouldBe "Let people see my collection to ask for games"
        page.one("#bring-requests-switch").text() shouldContain "Only people on a game day you joined see your collection, and only to ask you for a game"

        val off = client.htmxPost("/collection/bring-requests", parameters { append("allowed", "false") }).fragment()

        off shouldReplace "bring-requests-switch"
        off.one("input.gf-switch").hasAttr("checked") shouldBe false
        app.bringRequests.accepts(kimId) shouldBe false
        val on = client.htmxPost("/collection/bring-requests", parameters { append("allowed", "true") }).fragment()
        on.one("input.gf-switch").hasAttr("checked") shouldBe true
        app.bringRequests.accepts(kimId) shouldBe true
    }
}
