package gorefest.shell.web.approval

import gorefest.core.PlayerId
import gorefest.core.PlayerJoined
import gorefest.core.gameday.anna
import gorefest.core.gameday.gameDayCreated
import gorefest.core.gameday.gorefest7
import gorefest.shell.AppConfig
import gorefest.shell.web.TestApp
import gorefest.shell.web.page
import gorefest.shell.web.texts
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotStartWith
import io.kotest.matchers.string.shouldStartWith
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

class ApprovalRoutesTest {
    private val test = TestApp(AppConfig.dev().copy(adminEmail = "tim@example.com"))
    private val app = test.app
    private val newcomerId = PlayerId(Uuid.random())
    private lateinit var tim: String
    private lateinit var newcomer: String

    @BeforeEach
    fun reset(): Unit = kotlinx.coroutines.runBlocking {
        test.reset()
        tim = test.loggedIn("Tim")
        newcomer = test.loggedIn("Newcomer", id = newcomerId, approved = false)
    }

    private val HttpResponse.location get() = headers[HttpHeaders.Location]

    private suspend fun HttpClient.signUp(email: String, name: String) {
        val attempt = submitForm("/login", parameters { append("email", email); append("returnTo", "/") }).location!!.substringAfter("attempt=")
        val code = test.mailbox.latestTo(email)!!.subject.takeLast(6)
        submitForm("/login/code", parameters { append("attempt", attempt); append("code", code) })
        submitForm("/welcome", parameters { append("name", name); append("returnTo", "/") })
    }

    @Test
    fun `an unapproved account is sent to wait, wherever it goes`() = test.webTest(session = newcomer) { client ->
        client.get("/").location shouldStartWith "/pending"
        client.get("/gamedays/new").location shouldStartWith "/pending"
    }

    @Test
    fun `the waiting page says so, and an approved player doesn't see it`() {
        test.webTest(session = newcomer) { client ->
            client.get("/pending").page().select("h1").text() shouldContain "Waiting"
        }
        test.webTest(session = tim) { client -> client.get("/pending?returnTo=%2Fcollection").location shouldBe "/collection" }
    }

    @Test
    fun `a new sign-up waits, the admin gets a mail, approves, and the newcomer is in`() {
        test.webTest { client ->
            client.signUp("fresh@example.com", "Fresh")
            client.get("/").location shouldStartWith "/pending"
        }
        val mail = test.mailbox.latestTo("tim@example.com")!!
        mail.subject shouldContain "Fresh"
        mail.text shouldContain "/admin/approvals"

        test.webTest(session = tim) { admin ->
            val waiting = admin.get("/admin/approvals").page().texts(".gf-row__title")
            waiting shouldContain "Fresh"
            val id = admin.get("/admin/approvals").page().select(".gf-row:contains(Fresh) form[action$=/approve]").first()!!.attr("action")
            admin.post(id).location shouldBe "/admin/approvals"
            admin.get("/admin/approvals").page().texts(".gf-row__title") shouldNotContain "Fresh"
        }
        test.mailbox.latestTo("fresh@example.com")!!.subject shouldContain "in on Boardfest"
        test.webTest { client ->
            client.signUp("fresh@example.com", "Fresh")
            (client.get("/").location ?: "") shouldNotStartWith "/pending"
        }
    }

    @Test
    fun `rejecting removes the account and says so`() = test.webTest(session = tim) { admin ->
        admin.post("/admin/approvals/$newcomerId/reject").location shouldBe "/admin/approvals"

        admin.get("/admin/approvals").page().texts(".gf-row__title") shouldNotContain "Newcomer"
        test.mailbox.latestTo("newcomer@example.com")!!.text shouldContain "wasn't approved"
    }

    @Test
    fun `only the admin sees the approvals, everyone else gets a 404`() {
        test.webTest(session = newcomer) { it.get("/admin/approvals").status shouldBe HttpStatusCode.NotFound }
        test.webTest(session = kotlinx.coroutines.runBlocking { test.loggedIn("Kim") }) { kim ->
            kim.get("/admin/approvals").status shouldBe HttpStatusCode.NotFound
            kim.post("/admin/approvals/$newcomerId/approve").status shouldBe HttpStatusCode.NotFound
        }
        test.webTest { it.get("/admin/approvals").status shouldBe HttpStatusCode.NotFound }
        kotlinx.coroutines.runBlocking { app.login.waiting().map { it.id } shouldContain newcomerId }
    }

    @Test
    fun `the admin's own account is approved on first login`() = test.webTest { client ->
        client.signUp("tim2@example.com", "Tim") // not the admin: waits
        client.get("/").location shouldStartWith "/pending"

        val other = TestApp(AppConfig.dev().copy(adminEmail = "tim2@example.com"))
        other.reset()
        other.webTest { c ->
            c.signUp("tim2@example.com", "Tim")
            (c.get("/").location ?: "") shouldNotStartWith "/pending"
        }
    }

    @Test
    fun `joining through an invite waits for approval and doesn't join`() = test.webTest(session = newcomer) { client ->
        app.db.transaction { app.store.append(listOf(gameDayCreated(host = anna), PlayerJoined(gorefest7, anna))) }
        app.catchUp()
        val token = app.invites.token(app.gameDaysById.getValue(gorefest7))

        client.post("/i/$token/join").location shouldStartWith "/pending"

        app.gameDaysById.getValue(gorefest7).players shouldNotContain newcomerId
    }

    @Test
    fun `outside dev the admin email is required`() {
        shouldThrow<IllegalStateException> { AppConfig.fromEnv { if (it == "GOREFEST_ENV") "prod" else if (it == "SECRET_KEY") "x" else null } }
        AppConfig.fromEnv { if (it == "GOREFEST_ENV") "prod" else if (it == "SECRET_KEY") "x" else if (it == "ADMIN_EMAIL") " Tim@Example.com " else null }
            .adminEmail shouldBe "tim@example.com"
        AppConfig.fromEnv { null }.approvalRequired shouldBe false
    }
}
