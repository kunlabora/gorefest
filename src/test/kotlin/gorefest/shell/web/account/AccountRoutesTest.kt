package gorefest.shell.web.account

import gorefest.core.GameDayCreated
import gorefest.core.GameDayId
import gorefest.core.PlayerId
import gorefest.core.PlayerJoined
import gorefest.core.PlayerRegistered
import gorefest.shell.AppConfig
import gorefest.shell.web.TestApp
import gorefest.shell.web.one
import gorefest.shell.web.page
import gorefest.shell.web.texts
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.string.shouldStartWith
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.uuid.Uuid

class AccountRoutesTest {
    private val test = TestApp(AppConfig.dev().copy(adminEmail = "tim@example.com"))
    private val app = test.app
    private val kimId = PlayerId(Uuid.random())
    private lateinit var tim: String
    private lateinit var kim: String

    @BeforeEach
    fun reset(): Unit = runBlocking {
        test.reset()
        tim = test.loggedIn("Tim")
        kim = test.loggedIn("Kim", id = kimId)
        app.db.transaction { app.store.append(listOf(PlayerRegistered(kimId))) }
        app.catchUp()
    }

    private val HttpResponse.location get() = headers[HttpHeaders.Location]
    private fun adminMails() = test.mailbox.all.filter { it.to == "tim@example.com" }

    @Test
    fun `the account page shows who you are, and needs a login`() {
        test.webTest { it.get("/account").location shouldStartWith "/login" }
        test.webTest(session = kim) { client ->
            val page = client.get("/account").page()
            page.one("h1").text() shouldBe "Kim"
            page.one(".gf-facts").text() shouldBe "kim@example.com"
            page.select("form[action=/account/export] button").size shouldBe 1
            page.select("form[action=/account/delete] button").size shouldBe 1
            page.select("a[href=/account].gf-appbar__account").size shouldBe 1
        }
    }

    @Test
    fun `asking tells the admin once, with a name and a link but no email address`() = test.webTest(session = kim) { client ->
        client.submitForm("/account/delete", Parameters.Empty).location shouldBe "/account#request-delete"
        client.submitForm("/account/delete", Parameters.Empty)

        adminMails().size shouldBe 1
        adminMails().single().let {
            it.subject shouldBe "Kim wants to delete their account"
            it.text shouldContain "/admin/requests"
            (it.subject + it.text) shouldNotContain "kim@example.com"
        }
        client.get("/account").page().select("#request-delete form").size shouldBe 0
        client.get("/account").page().one("#request-delete .gf-notice").text() shouldContain "Asked"
        client.get("/account").page().select("#request-export form").size shouldBe 1
    }

    @Test
    fun `a host is warned that their upcoming game days stay`() = test.webTest(session = kim) { client ->
        val day = GameDayId(Uuid.random())
        app.db.transaction {
            app.store.append(listOf(GameDayCreated(day, kimId, "Gorefest #9", LocalDate.now().plusDays(5), "Kim's", "Europe/Brussels"), PlayerJoined(day, kimId)))
        }
        app.catchUp()

        client.get("/account").page().one("#request-delete .gf-small").text() shouldContain "Gorefest #9"
    }

    @Test
    fun `only the admin sees the requests`() {
        test.webTest(session = kim) { client ->
            client.get("/admin/requests").status shouldBe HttpStatusCode.NotFound
            client.post("/admin/requests/$kimId/erase").status shouldBe HttpStatusCode.NotFound
        }
        test.webTest { it.get("/admin/requests").status shouldBe HttpStatusCode.NotFound }
    }

    @Test
    fun `the admin deletes the account, the person is told, and their old session is dead`() {
        test.webTest(session = kim) { it.submitForm("/account/delete", Parameters.Empty) }

        test.webTest(session = tim) { admin ->
            admin.get("/admin/requests").page().texts(".gf-row__title") shouldContain "Kim"
            admin.post("/admin/requests/$kimId/erase").location shouldBe "/admin/requests"
            admin.get("/admin/requests").page().texts(".gf-row__title") shouldNotContain "Kim"
        }

        test.mailbox.latestTo("kim@example.com")!!.subject shouldBe "Your Boardfest account is deleted"
        test.webTest(session = kim) { it.get("/account").location shouldStartWith "/login" }
        runBlocking { app.login.players.let { app.db.transaction { it.find(kimId) } } shouldBe null }
    }

    @Test
    fun `the delete confirm keeps any name as text, a backslash and a quote included`() {
        val zedId = PlayerId(Uuid.random())
        val zed = runBlocking { test.loggedIn("Zed\\", id = zedId) }
        runBlocking { app.db.transaction { app.login.players.rename(zedId, "Z'ed\\") } }
        test.webTest(session = zed) { it.submitForm("/account/delete", Parameters.Empty) }

        test.webTest(session = tim) { admin ->
            val form = admin.get("/admin/requests").page().one("form[action=/admin/requests/$zedId/erase]")
            form.attr("_") shouldBe "on submit if not confirm('Delete Z\\'ed\\\\\\'s account for good?') halt the event"
        }
    }

    @Test
    fun `the admin downloads the person's data as a file, nobody else can`() {
        test.webTest(session = kim) { it.submitForm("/account/export", Parameters.Empty) }

        test.webTest(session = tim) { admin ->
            val response = admin.get("/admin/requests/$kimId/export")
            response.status shouldBe HttpStatusCode.OK
            response.headers[HttpHeaders.ContentDisposition]!! shouldContain "attachment"
            response.headers[HttpHeaders.CacheControl] shouldBe "no-store"
            val text = runBlocking { response.bodyAsText() }
            text shouldContain "kim@example.com"
            text shouldNotContain "tim@example.com"
            admin.get("/admin/requests/${Uuid.random()}/export").status shouldBe HttpStatusCode.NotFound
        }
        test.webTest(session = kim) { it.get("/admin/requests/$kimId/export").status shouldBe HttpStatusCode.NotFound }
    }

    @Test
    fun `a data request stays open until the admin says it was sent`() {
        test.webTest(session = kim) { it.submitForm("/account/export", Parameters.Empty) }

        test.webTest(session = tim) { admin ->
            admin.get("/admin/requests").page().one(".gf-row__sub").text() shouldContain "Send the data"
            admin.post("/admin/requests/$kimId/export/done").location shouldBe "/admin/requests"
            admin.get("/admin/requests").page().texts(".gf-row__title") shouldNotContain "Kim"
        }
        test.webTest(session = kim) { it.get("/account").page().select("#request-export form").size shouldBe 1 }
    }
}
