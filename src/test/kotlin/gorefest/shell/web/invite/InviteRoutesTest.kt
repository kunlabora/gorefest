package gorefest.shell.web.invite

import gorefest.core.GameDayEnded
import gorefest.core.InviteLinkRotated
import gorefest.core.PlayerId
import gorefest.core.PlayerJoined
import gorefest.core.gameday.anna
import gorefest.core.gameday.gameDayCreated
import gorefest.core.gameday.gorefest7
import gorefest.shell.web.TestApp
import gorefest.shell.web.components.facts
import gorefest.shell.web.components.hx
import gorefest.shell.web.components.page
import gorefest.shell.web.fragment
import gorefest.shell.web.gameday.invite
import gorefest.shell.web.htmxPost
import gorefest.shell.web.one
import gorefest.shell.web.page
import gorefest.shell.web.shouldReplace
import gorefest.shell.web.urlEncoded
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.statement.readRawBytes
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import javax.imageio.ImageIO
import kotlin.uuid.Uuid

class InviteRoutesTest {
    private val test = TestApp()
    private val app = test.app
    private val kimId = PlayerId(Uuid.random())
    private lateinit var kim: String
    private lateinit var annaSession: String

    @BeforeEach
    fun reset(): Unit = kotlinx.coroutines.runBlocking {
        test.reset()
        annaSession = test.loggedIn("Anna", id = anna)
        kim = test.loggedIn("Kim", id = kimId)
        app.db.transaction { app.store.append(listOf(gameDayCreated(host = anna), PlayerJoined(gorefest7, anna))) }
        app.catchUp()
    }

    private val token get() = app.invites.token(app.gameDaysById.getValue(gorefest7))
    private val link get() = "/i/$token"

    private suspend fun given(vararg events: gorefest.core.Event) {
        app.db.transaction { app.store.append(events.toList()) }
        app.catchUp()
    }

    @Test
    fun `the landing page shows the game day without logging in`() = test.webTest { client ->
        val page = client.get(link).page()

        page.one("main .gf-muted strong").text() shouldBe "Anna"
        page.one("h1.gf-hero").text() shouldBe "Gorefest #7"
        page.select(".gf-facts li").map { it.ownText() } shouldBe listOf("Sunday 27 September", "Anna's place", "1 player going")
        page.one(".gf-dock form").attr("action") shouldBe "$link/join"
        page.one(".gf-dock button").text() shouldBe "Join Gorefest #7"
    }

    @Test
    fun `the link previews nicely in chat apps`() = test.webTest { client ->
        val page = client.get(link).page()
        fun og(property: String) = page.one("meta[property=og:$property]").attr("content")

        og("title") shouldBe "Gorefest #7"
        og("description") shouldBe "Sunday 27 September · Anna's place · 1 going · 0 games on the shelf"
        og("image") shouldBe "http://localhost:8080$link/og.png"

        val image = client.get("$link/og.png")
        image.headers[HttpHeaders.ContentType] shouldBe "image/png"
        ImageIO.read(image.readRawBytes().inputStream()).let { (it.width to it.height) shouldBe (1200 to 630) }
    }

    @Test
    fun `joining needs a login, which comes back to the invite`() = test.webTest { client ->
        client.submitForm("$link/join", parameters {}).headers[HttpHeaders.Location] shouldBe "/login?returnTo=${"$link?join=1".urlEncoded()}"
    }

    @Test
    fun `a logged-in player joins and lands on the game day`() = test.webTest(session = kim) { client ->
        client.submitForm("$link/join", parameters {}).headers[HttpHeaders.Location] shouldBe "/gamedays/$gorefest7"

        app.gameDaysById.getValue(gorefest7).players shouldBe listOf(anna, kimId)
        client.get(link).headers[HttpHeaders.Location] shouldBe "/gamedays/$gorefest7"
    }

    @Test
    fun `a tampered link doesn't work`() = test.webTest(session = kim) { client ->
        val response = client.get(link.dropLast(2) + "xx")

        response.status shouldBe HttpStatusCode.NotFound
        response.page().one("h1").text() shouldBe "This invite link doesn't work anymore"
        client.submitForm(link.dropLast(2) + "xx/join", parameters {}).status shouldBe HttpStatusCode.NotFound
    }

    @Test
    fun `a new link replaces the old one`() = test.webTest(session = kim) { client ->
        val old = link
        given(InviteLinkRotated(gorefest7, 1))

        client.get(old).status shouldBe HttpStatusCode.NotFound
        client.submitForm("$old/join", parameters {}).status shouldBe HttpStatusCode.NotFound
        client.get(link).status shouldBe HttpStatusCode.OK
    }

    @Test
    fun `an ended game day's link says so`() = test.webTest(session = kim) { client ->
        given(GameDayEnded(gorefest7))

        val response = client.get(link)
        response.status shouldBe HttpStatusCode.Gone
        response.page().one("h1").text() shouldBe "Gorefest #7 has ended"
    }

    @Test
    fun `players who haven't joined can't see the game day page`() = test.webTest(session = kim) { client ->
        client.get("/gamedays/$gorefest7").status shouldBe HttpStatusCode.NotFound
    }

    @Test
    fun `members see the invite link, the host can replace it`() {
        test.webTest(session = annaSession) { client ->
            val page = client.get("/gamedays/$gorefest7").page()
            page.one("#invite .gf-copy__value").text() shouldBe "http://localhost:8080$link"
            val rotate = page.one("#invite button[hx-post]")
            rotate.attr("hx-post") shouldBe "/gamedays/$gorefest7/invite"

            val old = link
            val fresh = client.htmxPost("/gamedays/$gorefest7/invite").fragment()
            fresh shouldReplace "invite"
            fresh.one(".gf-copy__value").text() shouldBe "http://localhost:8080$link"
            (link == old) shouldBe false
        }
    }

    @Test
    fun `other members can copy the link but not replace it`() {
        kotlinx.coroutines.runBlocking { given(PlayerJoined(gorefest7, kimId)) }
        test.webTest(session = kim) { client ->
            val page = client.get("/gamedays/$gorefest7").page()
            page.one("#invite .gf-copy__value").text() shouldBe "http://localhost:8080$link"
            page.select("#invite button[hx-post]").shouldBeEmpty()
            client.htmxPost("/gamedays/$gorefest7/invite").status shouldBe HttpStatusCode.Forbidden
        }
    }


    @Test
    fun `a cancelled game day's link says so, and joining is refused`() = test.webTest(session = kim) { client ->
        val link = link
        given(gorefest.core.GameDayCancelled(gorefest7))

        val response = client.get(link)
        response.status shouldBe HttpStatusCode.Gone
        response.page().one("h1").text() shouldBe "Gorefest #7 was cancelled"
        client.submitForm("$link/join", parameters {}).status shouldBe HttpStatusCode.Gone
        app.gameDaysById.getValue(gorefest7).players.contains(kimId) shouldBe false
    }


    // Signing up from an invite (U1): the invite rides along in the login attempt, so it survives the mail round trip.

    // Kim exists from setup (named, so a returning player); Nina is brand new.
    private var mail = "nina@example.com"
    private fun code() = test.mailbox.latestTo(mail)!!.subject.takeLast(6)
    private fun mailedLink() = Regex("""http://localhost:8080(/login/link\?token=\S+)""").find(test.mailbox.latestTo(mail)!!.text)!!.groupValues[1]

    private suspend fun io.ktor.client.HttpClient.askForCodeFromInvite(): String {
        val toLogin = submitForm("$link/join", parameters {}).headers[HttpHeaders.Location]!!
        toLogin shouldBe "/login?returnTo=${"$link?join=1".urlEncoded()}"
        val returnTo = toLogin.substringAfter("returnTo=").let { java.net.URLDecoder.decode(it, Charsets.UTF_8) }
        get(toLogin).page()
        submitForm("/login", parameters { append("email", mail); append("returnTo", returnTo) })
        return returnTo
    }

    private fun joined(name: String = "Nina") = kotlinx.coroutines.runBlocking {
        app.login.names(app.gameDaysById.getValue(gorefest7).players).values.toList()
    } == listOf("Anna", name)

    @Test
    fun `a new player goes invite, email, code, name and lands joined on the game day`() = test.webTest { client ->
        val returnTo = client.askForCodeFromInvite()

        val loggedIn = client.submitForm("/login/code", parameters { append("attempt", "00000000-0000-0000-0000-000000000001"); append("code", code()) })
        loggedIn.headers[HttpHeaders.Location] shouldBe "/welcome?returnTo=${returnTo.urlEncoded()}"
        val named = client.submitForm("/welcome", parameters { append("name", "Nina"); append("returnTo", returnTo) })
        named.headers[HttpHeaders.Location] shouldBe returnTo

        client.get(returnTo).headers[HttpHeaders.Location] shouldBe "/gamedays/$gorefest7"
        joined() shouldBe true
        client.get("/gamedays/$gorefest7").status shouldBe HttpStatusCode.OK
    }

    @Test
    fun `a returning player who isn't logged in joins straight after the code`() = test.webTest { client ->
        mail = "kim@example.com"
        val returnTo = client.askForCodeFromInvite()

        val loggedIn = client.submitForm("/login/code", parameters { append("attempt", "00000000-0000-0000-0000-000000000001"); append("code", code()) })
        loggedIn.headers[HttpHeaders.Location] shouldBe returnTo

        client.get(returnTo).headers[HttpHeaders.Location] shouldBe "/gamedays/$gorefest7"
        joined("Kim") shouldBe true
    }

    @Test
    fun `the magic link can open in another browser than the invite`() = test.webTest { client ->
        val returnTo = client.askForCodeFromInvite()

        val otherBrowser = createClient {
            followRedirects = false
            install(io.ktor.client.plugins.cookies.HttpCookies)
        }
        otherBrowser.get(mailedLink()).page()
        val token = mailedLink().substringAfter("token=")
        val loggedIn = otherBrowser.submitForm("/login/link", parameters { append("token", token) })
        loggedIn.headers[HttpHeaders.Location] shouldBe "/welcome?returnTo=${returnTo.urlEncoded()}"
        otherBrowser.submitForm("/welcome", parameters { append("name", "Nina"); append("returnTo", returnTo) })

        otherBrowser.get(returnTo).headers[HttpHeaders.Location] shouldBe "/gamedays/$gorefest7"
        joined() shouldBe true
    }

    @Test
    fun `an invite that died while signing up says so instead of joining`() = test.webTest(session = kim) { client ->
        val returnTo = "$link?join=1"
        given(GameDayEnded(gorefest7))

        client.get(returnTo).status shouldBe HttpStatusCode.Gone
        app.gameDaysById.getValue(gorefest7).players shouldBe listOf(anna)
    }
}
