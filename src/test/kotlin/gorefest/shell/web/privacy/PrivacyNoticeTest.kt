package gorefest.shell.web.privacy

import gorefest.contract.Obligation
import gorefest.shell.AppConfig
import gorefest.shell.web.TestApp
import gorefest.shell.web.one
import gorefest.shell.web.page
import gorefest.shell.web.texts
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PrivacyNoticeTest {
    private val test = TestApp(AppConfig.dev().copy(adminEmail = "tim@example.com", mailProvider = "scaleway"))

    @BeforeEach
    fun reset() = kotlinx.coroutines.runBlocking { test.reset() }

    @Test
    @Obligation("PR-22", "PR-27")
    fun `the notice is public, stays out of search engines and has its sections`() = test.webTest { client ->
        val response = client.get("/privacy")
        response.status shouldBe HttpStatusCode.OK
        response.headers["X-Robots-Tag"]!! shouldContain "noindex"

        val page = response.bodyAsText().let { org.jsoup.Jsoup.parse(it) }
        page.select("meta[name=robots]").attr("content") shouldContain "noindex"
        page.texts(".gf-privacy__section h2") shouldBe listOf("Who runs it", "What you can ask for", "What it keeps", "Who sees what", "Who else handles it", "Cookies", "How long")
    }

    @Test
    @Obligation("PR-24")
    fun `the contact comes from the admin address in config`() = test.webTest { client ->
        val page = client.get("/privacy").page()
        page.one("#who a[href=/account]").text() shouldBe "your account page"
        val link = page.one("#who a[href^=mailto]")
        link.attr("href") shouldBe "mailto:tim@example.com"
        link.text() shouldBe "tim@example.com"
    }

    @Test
    @Obligation("PR-24")
    fun `the notice names the mail provider in use`() = test.webTest { client ->
        client.get("/privacy").page().one("#others").text() shouldContain "Scaleway Transactional Email, in the EU"
        MailProvider.of("postmark").label shouldBe "Postmark"
        MailProvider.of("resend").where shouldBe "in the US"
        MailProvider.of(null) shouldBe MailProvider.NONE
    }

    @Test
    @Obligation("PR-25")
    fun `the notice says what you can change yourself and what the admin channel gets`() = test.webTest { client ->
        val page = client.get("/privacy").page()
        page.one("#ask").text() shouldContain "change your email address yourself"
        page.one("#ask").text() shouldContain "unlink your BGG account"
        page.one("#others").text() shouldContain "I get a mail"
        page.one("#long").text() shouldContain "two years"
        page.one("#long").text() shouldContain "leave game days still to come"
        page.one("#long").text() shouldContain "you leave every game night and date poll"
        page.one("#sees").text() shouldContain "only by people on the same game day"
        page.one("#sees").text() shouldContain "never from yours"
        page.one("#sees").text() shouldNotContain "the host of a date poll you joined"
    }

    @Test
    @Obligation("PR-24")
    fun `without an admin address it says to ask whoever invited you`() {
        val bare = TestApp()
        kotlinx.coroutines.runBlocking { bare.reset() }
        bare.webTest { client ->
            val page = client.get("/privacy").page()
            page.select("#who a[href^=mailto]").size shouldBe 0
            page.one("#who").text() shouldContain "ask whoever invited you"
            page.one("#others").text() shouldContain "No mail goes out: this is a development copy"
        }
    }

    @Test
    @Obligation("PR-23")
    fun `every page links to the notice in its footer, the sign-in page included`() = test.webTest { client ->
        client.get("/login").page().one("footer a[href=/privacy]").text() shouldBe "Privacy"
        client.get("/privacy").page().one("footer a[href=/privacy]").text() shouldBe "Privacy"
    }

    @Test
    @Obligation("PR-26")
    fun `the notice names the cookies and says what it does not keep`() = test.webTest { client ->
        val text = client.get("/privacy").page().text()
        text shouldContain "no banner"
        text shouldNotContain "Gegevensbeschermingsautoriteit"
    }

    @Test
    @Obligation("PR-24")
    fun `with a Discord webhook the notice names the channel, without one it says mail`() {
        val withDiscord = TestApp(AppConfig.dev().copy(adminEmail = "tim@example.com", discordNotifications = true))
        runBlocking { withDiscord.reset() }
        withDiscord.webTest { client ->
            client.get("/privacy").page().one("#others").text() shouldContain "only its number"
        }
    }
}
