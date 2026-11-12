package gorefest.shell.web.newcomer

import gorefest.contract.Obligation
import gorefest.core.DatePollCreated
import gorefest.core.DatePollId
import gorefest.core.DatePollJoined
import gorefest.core.PlayerId
import gorefest.core.PlayerJoined
import gorefest.core.PlayerRegistered
import gorefest.core.gameday.anna
import gorefest.core.gameday.gameDayCreated
import gorefest.core.gameday.gorefest7
import gorefest.shell.AppConfig
import gorefest.shell.web.TestApp
import gorefest.shell.web.one
import gorefest.shell.web.page
import gorefest.shell.web.texts
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.string.shouldStartWith
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.uuid.Uuid

class NewcomerRoutesTest {
    private val test = TestApp(AppConfig.dev().copy(adminEmail = "tim@example.com"))
    private val app = test.app
    private val timId = PlayerId(Uuid.random())
    private val newcomerId = PlayerId(Uuid.random())
    private lateinit var tim: String
    private lateinit var newcomer: String

    @BeforeEach
    fun reset(): Unit = runBlocking {
        test.reset()
        tim = test.loggedIn("Tim", id = timId)
        newcomer = test.loggedIn("Newcomer", id = newcomerId)
        app.db.transaction { app.store.append(listOf(PlayerRegistered(newcomerId))) }
        app.catchUp()
    }

    private val HttpResponse.location get() = headers[HttpHeaders.Location]

    /** Signs up through the real flow and returns where the welcome step sends them. */
    private suspend fun HttpClient.signUp(email: String, name: String, returnTo: String = "/", on: TestApp = test): String? {
        val attempt = submitForm("/login", parameters { append("email", email); append("returnTo", returnTo) }).location!!.substringAfter("attempt=")
        val code = on.mailbox.latestTo(email)!!.subject.takeLast(6)
        submitForm("/login/code", parameters { append("attempt", attempt); append("code", code) })
        return submitForm("/welcome", parameters { append("name", name); append("returnTo", returnTo) }).location
    }

    @Test
    @Obligation("ACC-34")
    fun `a new account goes straight where it was headed`() = test.webTest { client ->
        client.signUp("fresh@example.com", "Fresh", returnTo = "/gamedays/new") shouldBe "/gamedays/new"

        client.get("/gamedays/new").status shouldBe HttpStatusCode.OK
        client.get("/account").status shouldBe HttpStatusCode.OK
    }

    @Test
    @Obligation("ACC-34")
    fun `a new account joins a game day and a date poll through an invite straight away`() = test.webTest(session = newcomer) { client ->
        val poll = DatePollId(Uuid.random())
        app.db.transaction {
            app.store.append(
                listOf(
                    gameDayCreated(host = anna), PlayerJoined(gorefest7, anna),
                    DatePollCreated(poll, anna, "Small night", "Anna's", listOf(LocalDate.parse("2026-10-09")), "Europe/Brussels"),
                    DatePollJoined(poll, anna),
                ),
            )
        }
        app.catchUp()

        client.post("/i/${app.invites.token(app.gameDaysById.getValue(gorefest7))}/join").location shouldBe "/gamedays/$gorefest7"
        client.post("/p/${app.invites.token(poll)}/join").location shouldBe "/polls/$poll"

        app.gameDaysById.getValue(gorefest7).players shouldContain newcomerId
        app.datePolls.of(poll)!!.participants shouldContain newcomerId
    }

    @Test
    @Obligation("ACC-35", "PR-19")
    fun `the admin hears about a new sign-up, with the name and a link, but not about their own`() {
        test.webTest { it.signUp("fresh@example.com", "Fresh") }

        val mail = test.mailbox.latestTo("tim@example.com").shouldNotBeNull()
        mail.subject shouldBe "Fresh signed up for Boardfest"
        mail.text shouldContain "/admin/newcomers"
        mail.text shouldNotContain "fresh@example.com"

        val other = TestApp(AppConfig.dev().copy(adminEmail = "boss@example.com"))
        runBlocking { other.reset() }
        other.webTest { it.signUp("boss@example.com", "Boss", on = other) }
        other.mailbox.latestTo("boss@example.com")!!.subject shouldNotContain "signed up"
    }

    @Test
    @Obligation("ACC-36")
    fun `the page lists the latest sign-ups, newest first, without the admin`() {
        test.webTest { it.signUp("fresh@example.com", "Fresh") }

        test.webTest(session = tim) { admin ->
            val names = admin.get("/admin/newcomers").page().texts(".gf-row__title")
            names.first() shouldBe "Fresh"
            names shouldContain "Newcomer"
            names shouldNotContain "Tim"
        }
    }

    @Test
    @Obligation("ACC-36")
    fun `rejecting asks first, erases the account, takes them off game days to come and tells them`() = runBlocking {
        app.db.transaction { app.store.append(listOf(gameDayCreated(host = anna), PlayerJoined(gorefest7, anna), PlayerJoined(gorefest7, newcomerId))) }
        app.catchUp()

        test.webTest(session = tim) { admin ->
            val form = admin.get("/admin/newcomers").page().one("form[action=/admin/newcomers/$newcomerId/reject]")
            form.attr("_") shouldContain "confirm('Reject Newcomer?"
            admin.post("/admin/newcomers/$newcomerId/reject").location shouldBe "/admin/newcomers"
            admin.get("/admin/newcomers").page().texts(".gf-row__title") shouldNotContain "Newcomer"
        }

        test.mailbox.latestTo("newcomer@example.com")!!.text shouldContain "by invitation only"
        app.db.transaction { app.login.players.find(newcomerId) } shouldBe null
        app.gameDaysById.getValue(gorefest7).players shouldNotContain newcomerId
        test.webTest(session = newcomer) { it.get("/").location.shouldNotBeNull() shouldStartWith "/login" }
    }

    @Test
    @Obligation("ACC-36")
    fun `the admin can't reject their own account`() = runBlocking {
        test.webTest(session = tim) { admin -> admin.post("/admin/newcomers/$timId/reject").location shouldBe "/admin/newcomers" }

        app.db.transaction { app.login.players.find(timId) }.shouldNotBeNull()
    }

    @Test
    @Obligation("ACC-18")
    fun `only the admin sees the new accounts, everyone else gets a 404`() {
        test.webTest(session = newcomer) { client ->
            client.get("/admin/newcomers").status shouldBe HttpStatusCode.NotFound
            client.post("/admin/newcomers/$timId/reject").status shouldBe HttpStatusCode.NotFound
        }
        test.webTest { it.get("/admin/newcomers").status shouldBe HttpStatusCode.NotFound }
        runBlocking { app.db.transaction { app.login.players.find(timId) }.shouldNotBeNull() }
    }

    @Test
    fun `old links to the approvals land on the new accounts`() = test.webTest(session = tim) { admin ->
        admin.get("/admin/approvals").location shouldBe "/admin/newcomers"
    }

    @Test
    @Obligation("ACC-21")
    fun `outside dev the admin email is required`() {
        shouldThrow<IllegalStateException> { AppConfig.fromEnv { if (it == "GOREFEST_ENV") "prod" else if (it == "SECRET_KEY") "x" else null } }
        AppConfig.fromEnv { if (it == "GOREFEST_ENV") "prod" else if (it == "SECRET_KEY") "x" else if (it == "ADMIN_EMAIL") " Tim@Example.com " else null }
            .adminEmail shouldBe "tim@example.com"
    }
}
