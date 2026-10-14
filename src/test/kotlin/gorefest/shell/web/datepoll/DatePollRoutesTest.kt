package gorefest.shell.web.datepoll

import gorefest.core.DatePollAnswered
import gorefest.core.DatePollCreated
import gorefest.core.DatePollId
import gorefest.core.DatePollJoined
import gorefest.core.Event
import gorefest.core.PlayerId
import gorefest.shell.web.TestApp
import gorefest.shell.web.fragment
import gorefest.shell.web.htmxPost
import gorefest.shell.web.one
import gorefest.shell.web.page
import gorefest.shell.web.shouldReplace
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.string.shouldStartWith
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.parameters
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.uuid.Uuid

class DatePollRoutesTest {
    private val test = TestApp()
    private val app = test.app
    private val annaId = PlayerId(Uuid.random())
    private val bramId = PlayerId(Uuid.random())
    private val kimId = PlayerId(Uuid.random())
    private lateinit var anna: String
    private lateinit var bram: String
    private lateinit var kim: String
    private val poll = DatePollId(Uuid.random())

    // The test clock says 24 September 2026.
    private val fri = LocalDate.parse("2026-10-09")
    private val sat = LocalDate.parse("2026-10-10")
    private val sun = LocalDate.parse("2026-10-11")

    @BeforeEach
    fun reset(): Unit = kotlinx.coroutines.runBlocking {
        test.reset()
        anna = test.loggedIn("Anna", id = annaId)
        bram = test.loggedIn("Bram", id = bramId)
        kim = test.loggedIn("Kim", id = kimId)
    }

    private suspend fun given(vararg events: Event) {
        app.db.transaction { app.store.append(events.toList()) }
        app.catchUp()
    }

    /** Anna's poll, with Bram in it. */
    private suspend fun annasPoll() = given(
        DatePollCreated(poll, annaId, "Boardfest #8", "Anna's place", listOf(fri, sat, sun), "Europe/Brussels"),
        DatePollJoined(poll, annaId), DatePollJoined(poll, bramId),
    )

    private val pollPage get() = "/polls/$poll"
    private fun token() = app.invites.token(poll)

    @Test
    fun `the form asks for a title, a place and dates, and can add more dates`() = test.webTest(session = anna) { client ->
        val form = client.get("/polls/new").page().one("#poll-form")

        form.select("input[name=dates][type=date]") shouldHaveSize 3
        form.one("input[name=timeZone][type=hidden]").`val`() shouldBe "Europe/Brussels"
        form.one("button[hx-get]").attr("hx-target") shouldBe "#poll-dates"
        client.get("/polls/new/date").fragment().attr("name") shouldBe "dates"
    }

    @Test
    fun `add another date stops at the most dates a poll takes`() = test.webTest(session = anna) { client ->
        client.get("/polls/new").page().one("button[hx-get]").hasAttr("disabled") shouldBe false
        // The button also disables itself once the form it has grown has that many fields.
        client.get("/polls/new").page().one("button[hx-get]").attr("_") shouldContain "length >= 14"

        val tooMany = client.htmxPost("/polls", parameters {
            append("title", "Boardfest #8"); append("timeZone", "Europe/Brussels")
            repeat(15) { append("dates", fri.plusDays(it.toLong()).toString()) }
        }).fragment()
        tooMany.select("input[name=dates]") shouldHaveSize 15
        tooMany.one("button[hx-get]").hasAttr("disabled") shouldBe true
    }

    @Test
    fun `starting a poll puts the host in it, with the dates in order`() = test.webTest(session = anna) { client ->
        val response = client.htmxPost("/polls", parameters {
            append("title", "Boardfest #8"); append("place", "Anna's place"); append("timeZone", "Europe/Brussels")
            append("dates", sun.toString()); append("dates", fri.toString()); append("dates", ""); append("dates", sat.toString())
        })

        val created = app.datePolls.forPlayer(annaId).single()
        response.headers["HX-Redirect"] shouldBe "/polls/${created.id}"
        created.dates shouldContainExactly listOf(fri, sat, sun)
        created.participants shouldBe listOf(annaId)
    }

    @Test
    fun `a poll with fewer than two dates says so and keeps what was typed`() = test.webTest(session = anna) { client ->
        val form = client.htmxPost("/polls", parameters {
            append("title", "Boardfest #8"); append("dates", fri.toString()); append("dates", "")
        }).fragment()

        form shouldReplace "poll-form"
        form.one("[role=alert]").text() shouldBe "Give at least two dates to choose from"
        form.one("input[name=title]").`val`() shouldBe "Boardfest #8"
    }

    @Test
    fun `the poll page offers my dates, the results, and the invite link`() = test.webTest(session = bram) { client ->
        annasPoll()

        val page = client.get(pollPage).page()

        page.select("#answer-form input[name=dates]").map { it.`val`() } shouldContainExactly listOf("2026-10-09", "2026-10-10", "2026-10-11")
        page.one("#answer-form").attr("hx-post") shouldBe "$pollPage/answer"
        page.one("#answer-form").attr("hx-target") shouldBe "#poll-results"
        page.select("#poll-results .gf-opt") shouldHaveSize 3
        page.select(".gf-opt--best") shouldHaveSize 0 // nobody has answered yet
        page.select(".gf-opt .gf-meter i") shouldHaveSize 6 // one segment per participant, per date
        page.one(".gf-kicker").text() shouldBe "Finding a date · 0 of 2 answered"
        page.one("header .gf-nav a[aria-current=page]").text() shouldBe "Game days"
        page.select("#poll-results button[hx-post]") shouldHaveSize 0 // only the host picks
        page.one(".gf-copy__value").text() shouldBe "http://localhost:8080/p/${token()}"
    }

    @Test
    fun `strangers can't see a poll, only people who joined through the invite`() = test.webTest(session = kim) { client ->
        annasPoll()

        client.get(pollPage).status shouldBe HttpStatusCode.NotFound
        client.htmxPost("$pollPage/answer", parameters { append("dates", fri.toString()) }).status shouldBe HttpStatusCode.NotFound
        client.htmxPost("$pollPage/pick", parameters { append("date", fri.toString()) }).status shouldBe HttpStatusCode.NotFound
    }

    @Test
    fun `the invite shows the poll, and joining takes you to it`() = test.webTest(session = kim) { client ->
        annasPoll()

        val landing = client.get("/p/${token()}").page()
        landing.one("h1").text() shouldBe "Boardfest #8"
        landing.text() shouldNotContain "Anna's place"
        landing.select(".gf-list li").map { it.text() } shouldContainExactly listOf("Fri 9 Oct", "Sat 10 Oct", "Sun 11 Oct")
        landing.one(".gf-narrow form").attr("action") shouldBe "/p/${token()}/join"

        client.submitForm("/p/${token()}/join", Parameters.Empty).headers["Location"] shouldBe pollPage
        app.datePolls.of(poll)!!.participants shouldBe listOf(annaId, bramId, kimId)
        client.get("/p/${token()}").headers["Location"] shouldBe pollPage
        client.get(pollPage).status shouldBe HttpStatusCode.OK
    }

    @Test
    fun `without a session, joining goes through login first, and a bad link is a dead end`() = test.webTest { client ->
        annasPoll()

        client.get("/p/${token()}").page().one("h1").text() shouldBe "Boardfest #8"
        client.submitForm("/p/${token()}/join", Parameters.Empty).headers["Location"]!! shouldStartWith "/login?returnTo="
        client.get("/p/not-a-token").status shouldBe HttpStatusCode.NotFound
        client.get("/p/${token()}x").status shouldBe HttpStatusCode.NotFound
    }

    @Test
    fun `answering shows who can make which date, and the best one so far`() = test.webTest(session = bram) { client ->
        annasPoll()
        given(DatePollAnswered(poll, annaId, listOf(sat, sun)))

        val results = client.htmxPost("$pollPage/answer", parameters { append("dates", sat.toString()); append("dates", fri.toString()) }).fragment()

        results shouldReplace "poll-results"
        results.select(".gf-opt").map { it.one(".gf-opt__title").text() to it.one(".gf-who > span:last-child").text() } shouldContainExactly
            listOf("Friday 9 October" to "1 can · Anna can't", "Saturday 10 October" to "2 can", "Sunday 11 October" to "1 can · Bram can't")
        results.one(".gf-tag--brand").text() shouldBe "Most can come"
        results.one(".gf-opt--best .gf-opt__title").text() shouldBe "Saturday 10 October"
        results.select(".gf-opt--best .gf-meter i.is-yes") shouldHaveSize 2
        app.datePolls.of(poll)!!.answers[bramId] shouldBe setOf(fri, sat)
    }

    @Test
    fun `a date that isn't an option is refused`() = test.webTest(session = bram) { client ->
        annasPoll()

        val response = client.htmxPost("$pollPage/answer", parameters { append("dates", "2026-12-24") })

        response.status shouldBe HttpStatusCode.Conflict
        response.fragment().one("[role=alert]").text() shouldBe "That date isn't one of the options"
    }

    @Test
    fun `the host sees who is still to answer and can pick a date`() = test.webTest(session = anna) { client ->
        annasPoll()
        given(DatePollAnswered(poll, annaId, listOf(sat)))

        val page = client.get(pollPage).page()

        page.one("#poll-results .gf-muted").text() shouldBe "Still to answer: Bram."
        page.select("#poll-results button[hx-post]").map { it.attr("hx-vals") } shouldContainExactly
            listOf("""{"date": "2026-10-09"}""", """{"date": "2026-10-10"}""", """{"date": "2026-10-11"}""")
        page.select("#poll-results button[hx-post]").first()!!.attr("hx-confirm") shouldContain "game day is created"
        // The best date wears the brand: its button is the only primary one on the page.
        page.select("#poll-results .gf-btn--primary").size shouldBe 1
        page.one(".gf-opt--best button").text() shouldBe "Pick this date"
    }

    @Test
    fun `picking a date creates the game day with the host and everyone who can make it`() {
        kotlinx.coroutines.runBlocking {
            annasPoll()
            given(DatePollAnswered(poll, annaId, listOf(sat)), DatePollAnswered(poll, bramId, listOf(fri, sat)))
        }
        test.webTest(session = anna) { client ->
            val response = client.htmxPost("$pollPage/pick", parameters { append("date", sat.toString()) })

            val gameDay = app.datePolls.of(poll)!!.picked!!
            gameDay.date shouldBe sat
            response.headers["HX-Redirect"] shouldBe "/gamedays/${gameDay.gameDay}"
            val created = app.gameDaysById.getValue(gameDay.gameDay!!)
            created.title shouldBe "Boardfest #8"
            created.place shouldBe "Anna's place"
            created.date shouldBe sat
            created.host shouldBe annaId
            created.players shouldBe listOf(annaId, bramId)
        }
        // Bram can go straight to the game day; the poll page says what was picked.
        test.webTest(session = bram) { client ->
            val page = client.get(pollPage).page()
            page.one(".gf-notice--good").text() shouldContain "Saturday 10 October"
            page.one("a.gf-btn--primary").attr("href") shouldStartWith "/gamedays/"
            page.select("#answer-form") shouldHaveSize 0
        }
    }

    @Test
    fun `only the host picks, once, and only from the options`() = test.webTest(session = bram) { client ->
        annasPoll()

        val refused = client.htmxPost("$pollPage/pick", parameters { append("date", sat.toString()) })
        refused.status shouldBe HttpStatusCode.Conflict
        refused.fragment().one("[role=alert]").text() shouldBe "Only the host can pick the date"
        app.datePolls.of(poll)!!.open shouldBe true
    }

    @Test
    fun `a closed poll's invite says the date is picked`() = test.webTest(session = kim) { client ->
        annasPoll()
        test.webTest(session = anna) { host -> host.htmxPost("$pollPage/pick", parameters { append("date", fri.toString()) }) }

        val response = client.get("/p/${token()}")

        response.status shouldBe HttpStatusCode.Gone
        response.bodyAsText() shouldContain "The host already picked a date"
    }

    @Test
    fun `someone in the poll who follows its link after the pick lands on the poll, not on a dead end`() {
        kotlinx.coroutines.runBlocking {
            annasPoll()
            given(DatePollAnswered(poll, bramId, listOf(fri)))
        }
        test.webTest(session = anna) { host -> host.htmxPost("$pollPage/pick", parameters { append("date", fri.toString()) }) }
        test.webTest(session = bram) { client -> client.get("/p/${token()}").headers["Location"] shouldBe pollPage }
    }

    @Test
    fun `a date that has passed can't be changed, but an answer already given for it is kept`() {
        kotlinx.coroutines.runBlocking {
            annasPoll()
            given(DatePollAnswered(poll, bramId, listOf(fri, sat)))
            test.clock.advance(java.time.Duration.ofDays(16)) // 9 October is behind us, 10 October is today
        }
        test.webTest(session = bram) { client ->
            val form = client.get(pollPage).page().one("#answer-form")

            form.select("input[type=checkbox][disabled]").map { it.`val`() } shouldContainExactly listOf("2026-10-09")
            // A disabled checkbox isn't sent, so the ticked past date rides along as a hidden value.
            form.select("input[type=hidden][name=dates]").map { it.`val`() } shouldContainExactly listOf("2026-10-09")
            form.select("input[type=checkbox]:not([disabled])[checked]").map { it.`val`() } shouldContainExactly listOf("2026-10-10")

            // Untick Saturday the way the browser would: the form sends only what's enabled and ticked, plus the hidden one.
            client.htmxPost("$pollPage/answer", parameters { append("dates", fri.toString()) })
            app.datePolls.of(poll)!!.answers[bramId] shouldBe setOf(fri)
        }
    }

    @Test
    fun `a closed poll marks the picked date, and drops off the home page`() {
        kotlinx.coroutines.runBlocking { annasPoll() }
        test.webTest(session = anna) { client -> client.htmxPost("$pollPage/pick", parameters { append("date", sun.toString()) }) }
        test.webTest(session = bram) { client ->
            val results = client.get(pollPage).page().one("#poll-results")

            results.one(".gf-tag--olive").text() shouldBe "Picked"
            results.one(".gf-tag--olive").parent()!!.one(".gf-opt__title").text() shouldBe "Sunday 11 October"
            results.select("button[hx-post]") shouldHaveSize 0
            client.get("/").page().select("a.gf-daycard[href=$pollPage]") shouldHaveSize 0
        }
    }

    @Test
    fun `the home page lists my open polls and offers to start one`() = test.webTest(session = bram) { client ->
        annasPoll()

        val page = client.get("/").page()

        page.one("a.gf-daycard[href=$pollPage] .gf-daycard__title").text() shouldBe "Boardfest #8"
        page.one("a.gf-daycard[href=$pollPage] .gf-daycard__meta").text() shouldBe "0 of 2 answered"
        page.one(".gf-home__aside a[href=/polls/new]").text() shouldBe "Find a date"
    }

    @Test
    fun `a new player signing up from a poll invite lands joined on the poll`() = test.webTest { client ->
        annasPoll()
        val returnTo = "/p/${token()}?join=1"

        client.submitForm("/p/${token()}/join", Parameters.Empty).headers["Location"] shouldBe "/login?returnTo=${java.net.URLEncoder.encode(returnTo, Charsets.UTF_8)}"
        client.submitForm("/login", parameters { append("email", "nina@example.com"); append("returnTo", returnTo) })
        val code = test.mailbox.latestTo("nina@example.com")!!.subject.takeLast(6)
        client.submitForm("/login/code", parameters { append("attempt", "00000000-0000-0000-0000-000000000001"); append("code", code) })
        val consent = client.submitForm("/welcome", parameters { append("name", "Nina"); append("returnTo", returnTo) }).headers["Location"]!!
        consent shouldStartWith "/p/${token()}?join="

        client.get(consent).headers["Location"] shouldBe pollPage
        ("Nina" in app.login.names(app.datePolls.of(poll)!!.participants).values) shouldBe true
    }

    @Test
    fun `a crafted poll link can't join anyone`() = test.webTest(session = kim) { client ->
        annasPoll()

        client.get("/p/${token()}?join=1").status shouldBe HttpStatusCode.OK
        app.datePolls.of(poll)!!.participants shouldBe listOf(annaId, bramId)
    }

    @Test
    fun `if the picked game day was cancelled, the poll says so`() {
        kotlinx.coroutines.runBlocking { annasPoll() }
        test.webTest(session = anna) { client -> client.htmxPost("$pollPage/pick", parameters { append("date", sun.toString()) }) }
        val gameDay = app.datePolls.of(poll)!!.picked!!.gameDay!!
        kotlinx.coroutines.runBlocking { given(gorefest.core.GameDayCancelled(gameDay)) }

        test.webTest(session = anna) { client ->
            val page = client.get(pollPage).page()
            page.select("a.gf-btn--primary") shouldHaveSize 0
            page.select(".gf-notice--good ~ p").text() shouldBe "Boardfest #8 was cancelled by the host."
        }
    }
}
