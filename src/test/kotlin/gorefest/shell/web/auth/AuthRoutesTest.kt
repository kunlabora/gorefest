package gorefest.shell.web.auth

import gorefest.core.LoggedIn
import gorefest.core.PlayerRegistered
import gorefest.core.auth.LoginAttempt
import gorefest.shell.AppConfig
import gorefest.shell.db.Tx
import gorefest.shell.web.SESSION_COOKIE
import gorefest.shell.web.TestApp
import gorefest.shell.web.components.notice
import gorefest.shell.web.components.of
import gorefest.shell.web.components.page
import gorefest.shell.web.fragment
import gorefest.shell.web.gameday.invite
import gorefest.shell.web.htmxPost
import gorefest.shell.web.one
import gorefest.shell.web.page
import gorefest.shell.web.shouldReplace
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldMatch
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.string.shouldStartWith
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration

class AuthRoutesTest {
    private val test = TestApp()
    private val mailbox = test.mailbox

    @BeforeEach
    fun reset() = kotlinx.coroutines.runBlocking { test.reset() }

    private val firstAttempt = "/login/code?attempt=00000000-0000-0000-0000-000000000001"

    private suspend fun HttpClient.askForCode(email: String = "kim@example.com", returnTo: String = "/") =
        submitForm("/login", parameters { append("email", email); append("returnTo", returnTo) })

    private suspend fun HttpClient.enterCode(code: String, attempt: String = "00000000-0000-0000-0000-000000000001") =
        submitForm("/login/code", parameters { append("attempt", attempt); append("code", code) })

    private fun code(email: String = "kim@example.com") = mailbox.latestTo(email)!!.subject.takeLast(6)
    private fun link(email: String = "kim@example.com") = Regex("""http://localhost:8080(/login/link\?token=\S+)""").find(mailbox.latestTo(email)!!.text)!!.groupValues[1]
    private fun token(email: String = "kim@example.com") = link(email).substringAfter("token=")

    private suspend fun HttpClient.nameMyself(name: String, returnTo: String = "/") =
        submitForm("/welcome", parameters { append("name", name); append("returnTo", returnTo) })

    private val HttpResponse.location get() = headers[HttpHeaders.Location]

    @Test
    fun `pages send you to log in, remembering where you were going`() = test.webTest { client ->
        client.get("/gamedays/new").location shouldBe "/login?returnTo=%2Fgamedays%2Fnew"
    }

    @Test
    fun `the login page explains the flow, unless you came through an invite`() = test.webTest { client ->
        client.get("/login?returnTo=%2F").page().select(".gf-how__col") shouldHaveSize 3
        client.get("/login?returnTo=%2Fgamedays%2Fnew").page().select(".gf-how__col") shouldHaveSize 0
    }

    @Test
    fun `htmx requests go to login from the page they were made on`() = test.webTest { client ->
        val response = client.htmxPost("/gamedays/abc/invite", currentUrl = "http://localhost/gamedays/abc")

        response.headers["HX-Redirect"] shouldBe "/login?returnTo=%2Fgamedays%2Fabc"
    }

    @Test
    fun `asking for a code sends one mail with a 6-digit code and a link`() = test.webTest { client ->
        val response = client.askForCode(email = "  Kim@Example.com ")

        response.location shouldBe firstAttempt
        mailbox.all shouldHaveSize 1
        val mail = mailbox.all.single()
        mail.to shouldBe "kim@example.com"
        mail.subject shouldMatch Regex("""Your Gorefest code: \d{6}""")
        mail.text shouldContain "Your code is ${code()}."
        mail.text shouldContain "http://localhost:8080/login/link?token="
    }

    @Test
    fun `the code page shows where the mail went and posts the code with htmx`() = test.webTest { client ->
        client.askForCode()

        val page = client.get(firstAttempt).page()

        page.one("main .gf-muted strong").text() shouldBe "kim@example.com"
        val form = page.one("form#code-form")
        form.attr("hx-post") shouldBe "/login/code"
        form.one("input[name=code]").attr("autocomplete") shouldBe "one-time-code"
        form.one("button[type=submit]").text() shouldBe "Let me in"
        page.select(".gf-dock").size shouldBe 0
        page.one(".gf-hero-band__copy > .gf-kicker").text() shouldBe "Step 2 of 2"
        page.one(".gf-mailcard").attr("aria-hidden") shouldBe "true"
    }

    @Test
    fun `an invalid email re-renders the form with what was typed`() = test.webTest { client ->
        val form = client.htmxPost("/login", parameters { append("email", "kim-at-home") }).fragment()

        form shouldReplace "email-form"
        form.one(".gf-error").text() shouldBe "That doesn't look like an email address."
        form.one("input[name=email]").`val`() shouldBe "kim-at-home"
        mailbox.all shouldHaveSize 0
    }

    @Test
    fun `an invite's sign-in keeps its own button label when the email is rejected`() = test.webTest { client ->
        val form = client.htmxPost("/login", parameters { append("email", "kim-at-home"); append("submitLabel", "Join Gorefest #7") }).fragment()

        form.one("button[type=submit]").text() shouldBe "Join Gorefest #7"
    }

    @Test
    fun `the code logs a new player in, asks their name, then goes where they were going`() = test.webTest { client ->
        client.askForCode(returnTo = "/gamedays/new")

        val loggedIn = client.enterCode(code())
        loggedIn.location shouldBe "/welcome?returnTo=%2Fgamedays%2Fnew"
        val cookie = loggedIn.headers[HttpHeaders.SetCookie].shouldNotBeNull()
        cookie shouldStartWith "$SESSION_COOKIE="
        cookie shouldContain "HttpOnly"
        cookie shouldContain "SameSite=Lax"

        client.get("/gamedays/new").location shouldBe "/welcome?returnTo=%2Fgamedays%2Fnew"
        client.nameMyself("Kim", returnTo = "/gamedays/new").location shouldBe "/gamedays/new"
        client.get("/gamedays/new").status shouldBe HttpStatusCode.OK
    }

    @Test
    fun `the welcome step needs a name`() = test.webTest { client ->
        client.askForCode()
        client.enterCode(code())

        val form = client.htmxPost("/welcome", parameters { append("name", "  ") }).fragment()

        form shouldReplace "name-form"
        form.one(".gf-error").text() shouldBe "Pick a name your friends will recognise."
    }

    @Test
    fun `the first login registers the player, later ones only log in`() = test.webTest { client ->
        client.askForCode()
        client.enterCode(code())
        client.nameMyself("Kim")
        client.submitForm("/logout", parameters {})

        client.askForCode(returnTo = "/gamedays/new")
        client.enterCode(code(), attempt = "00000000-0000-0000-0000-000000000003").location shouldBe "/gamedays/new"

        val events = test.app.db.transaction { test.app.store.readAll(after = 0) }.map { it.event }
        events.filterIsInstance<PlayerRegistered>() shouldHaveSize 1
        events.filterIsInstance<LoggedIn>() shouldHaveSize 2
    }

    @Test
    fun `the link asks for a tap before logging in, so mail scanners can't use it up`() = test.webTest { client ->
        client.askForCode()

        client.get(link()).page().one("main .gf-muted strong").text() shouldBe "kim@example.com"
        val confirm = client.get(link()).page()
        confirm.one("form#link-form input[name=token]").`val`() shouldBe token()

        val loggedIn = client.submitForm("/login/link", parameters { append("token", token()) })
        loggedIn.location shouldBe "/welcome?returnTo=%2F"
    }

    @Test
    fun `once the link is used, the code is dead too, and the other way round`() = test.webTest { client ->
        client.askForCode()
        client.submitForm("/login/link", parameters { append("token", token()) })

        client.htmxPost("/login/code", parameters { append("attempt", "00000000-0000-0000-0000-000000000001"); append("code", code()) })
            .fragment().one(".gf-error").text() shouldBe "That code was already used. Send it again for a fresh one."

        client.askForCode(email = "bram@example.com")
        client.enterCode(code("bram@example.com"), attempt = "00000000-0000-0000-0000-000000000003")
        client.get(link("bram@example.com")).page().one("h1").text() shouldBe "This link has expired"
    }

    @Test
    fun `codes and links expire after 15 minutes`() = test.webTest { client ->
        client.askForCode()
        test.clock.advance(LoginAttempt.TTL)

        client.get(link()).page().one("h1").text() shouldBe "This link has expired"
        client.htmxPost("/login/code", parameters { append("attempt", "00000000-0000-0000-0000-000000000001"); append("code", code()) })
            .fragment().one(".gf-error").text() shouldBe "That code has expired. Codes work for 15 minutes, so send it again for a fresh one."
    }

    @Test
    fun `five wrong codes lock the attempt, even for the right code`() = test.webTest { client ->
        client.askForCode()
        val wrong = if (code() == "000000") "111111" else "000000"
        suspend fun tryCode(c: String) =
            client.htmxPost("/login/code", parameters { append("attempt", "00000000-0000-0000-0000-000000000001"); append("code", c) })
                .fragment().one(".gf-error").text()

        tryCode(wrong) shouldBe "That code doesn't match. Four more tries, then you'll need a fresh code."
        repeat(2) { tryCode(wrong) }
        tryCode(wrong) shouldBe "That code doesn't match. One more try, then you'll need a fresh code."
        tryCode(wrong) shouldBe "Too many wrong codes. Send it again for a fresh one."
        tryCode(code()) shouldBe "Too many wrong codes. Send it again for a fresh one."
    }

    @Test
    fun `tokens, codes and sessions are only stored hashed`() = test.webTest { client ->
        client.askForCode()
        val session = client.enterCode(code()).headers[HttpHeaders.SetCookie]!!.substringAfter("=").substringBefore(";")

        val stored = test.app.db.transaction {
            val c = contextOf<Tx>().connection
            listOf("select * from login_attempts", "select * from sessions").joinToString("\n") { sql ->
                c.createStatement().use { st ->
                    st.executeQuery(sql).use { rs ->
                        buildString {
                            while (rs.next()) for (i in 1..rs.metaData.columnCount) {
                                append(rs.getObject(i)).append(' ')
                                rs.getBytes(i)?.let { append(String(it, Charsets.ISO_8859_1)).append(' ') }
                            }
                        }
                    }
                }
            }
        }
        stored shouldNotContain code()
        stored shouldNotContain token()
        stored shouldNotContain session
    }

    @Test
    fun `logging out ends the session, also for a copied cookie`() = test.webTest { client ->
        client.askForCode()
        val session = client.enterCode(code()).headers[HttpHeaders.SetCookie]!!.substringAfter("=").substringBefore(";")
        client.nameMyself("Kim")

        client.submitForm("/logout", parameters {}).location shouldBe "/login"

        client.get("/").location shouldBe "/login?returnTo=%2F"
        client.get("/") { header(HttpHeaders.Cookie, "$SESSION_COOKIE=$session") }.location shouldBe "/login?returnTo=%2F"
    }

    @Test
    fun `logged-in players skip the login page`() {
        val kim = kotlinx.coroutines.runBlocking { test.loggedIn("Kim") }
        test.webTest(session = kim) { client -> assertSkipsLogin(client) }
    }

    private suspend fun assertSkipsLogin(client: HttpClient) {
        client.get("/login?returnTo=%2Fgamedays%2Fnew").location shouldBe "/gamedays/new"
    }

    @Test
    fun `returnTo can't point at another site`() = test.webTest { client ->
        client.askForCode(returnTo = "//evil.example/steal")
        client.enterCode(code()).location shouldBe "/welcome?returnTo=%2F"
    }

    @Test
    fun `send it again mails a fresh code for the same email and destination`() = test.webTest { client ->
        client.askForCode(returnTo = "/gamedays/new")

        val resend = client.get(firstAttempt).page().one("form[action=/login]")

        resend.one("input[name=email]").`val`() shouldBe "kim@example.com"
        resend.one("input[name=returnTo]").`val`() shouldBe "/gamedays/new"
    }

    @Test
    fun `in dev mode the code page points at the dev mailbox, which shows the mail`() = test.webTest { client ->
        client.askForCode()

        client.get(firstAttempt).page().one(".gf-notice a").attr("href") shouldBe "/dev/mailbox"
        val mailboxPage = client.get("/dev/mailbox").page()
        mailboxPage.one(".gf-row__title").text() shouldBe mailbox.all.single().subject
        mailboxPage.one(".gf-row a").attr("href") shouldBe "http://localhost:8080${link()}"
    }

    @Test
    fun `at most five codes per email per hour`() = test.webTest { client ->
        repeat(5) { client.askForCode().location shouldStartWith "/login/code" }

        val refused = client.htmxPost("/login", parameters { append("email", "kim@example.com"); append("returnTo", "/") }).fragment()
        refused.one(".gf-error").text() shouldBe "That's a lot of codes. Wait a bit, then try again."
        mailbox.all shouldHaveSize 5

        test.clock.advance(Duration.ofMinutes(61))
        client.askForCode().location shouldStartWith "/login/code"
    }

    @Test
    fun `at most twenty codes per address per hour, whatever the email`() = test.webTest { client ->
        suspend fun ask(i: Int, from: String) = client.submitForm("/login", parameters { append("email", "p$i@example.com"); append("returnTo", "/") }) {
            header("X-Forwarded-For", from)
        }
        repeat(20) { ask(it, "203.0.113.7").location shouldStartWith "/login/code" }

        ask(20, "203.0.113.7").status shouldBe HttpStatusCode.OK // the form again, with the error
        ask(21, "198.51.100.1").location shouldStartWith "/login/code"
    }

    @Test
    fun `outside dev mode there is no dev mailbox`() {
        val prod = TestApp(AppConfig.dev().copy(dev = false))
        prod.webTest { client ->
            client.get("/dev/mailbox").status shouldBe HttpStatusCode.NotFound
        }
    }

    @Test
    fun `the resend form replaces itself, so a refusal doesn't nest a form inside it`() = test.webTest { client ->
        client.askForCode()
        val resend = client.get(firstAttempt).page().one("form#resend")
        resend.attr("hx-target") shouldBe "this"
        resend.attr("hx-swap") shouldBe "outerHTML"

        repeat(4) { client.askForCode() }
        val refused = client.htmxPost("/login", parameters {
            append("email", "kim@example.com"); append("returnTo", "/"); append("resend", "1")
        }).fragment()
        refused shouldReplace "resend"
        refused.hasClass("gf-notice--error") shouldBe true
        refused.select("form") shouldHaveSize 0
    }
}
