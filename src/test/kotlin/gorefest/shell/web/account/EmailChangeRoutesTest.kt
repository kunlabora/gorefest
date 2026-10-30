package gorefest.shell.web.account

import gorefest.contract.Obligation
import gorefest.core.PlayerId
import gorefest.shell.AppConfig
import gorefest.shell.web.TestApp
import gorefest.shell.web.one
import gorefest.shell.web.page
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.parameters
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

class EmailChangeRoutesTest {
    private val test = TestApp(AppConfig.dev().copy(adminEmail = "tim@example.com"))
    private val app = test.app
    private val kimId = PlayerId(Uuid.random())
    private lateinit var kim: String

    @BeforeEach
    fun reset(): Unit = runBlocking {
        test.reset()
        kim = test.loggedIn("Kim", id = kimId)
        test.loggedIn("Bram")
    }

    private val HttpResponse.location get() = headers[HttpHeaders.Location]
    private suspend fun HttpClient.ask(email: String) = submitForm("/account/email", parameters { append("email", email) })
    private suspend fun HttpClient.confirm(code: String) = submitForm("/account/email/confirm", parameters { append("code", code) })
    private fun codeFor(email: String) = test.mailbox.latestTo(email)!!.subject.takeLast(6)
    private fun emailOfKim() = runBlocking { app.db.transaction { app.login.players.find(kimId)!!.email } }

    @Test
    @Obligation("ACC-29")
    fun `a code goes to the new address, entering it changes the email, the old address hears and the session stays`() = test.webTest(session = kim) { client ->
        client.ask("Kim.New@Example.com").location shouldBe "/account/email"
        client.get("/account/email").page().one("strong").text() shouldBe "kim.new@example.com"
        emailOfKim() shouldBe "kim@example.com"

        client.confirm(codeFor("kim.new@example.com")).location shouldBe "/account?emailChanged=1"

        emailOfKim() shouldBe "kim.new@example.com"
        test.mailbox.latestTo("kim@example.com")!!.text shouldContain "kim.new@example.com"
        client.get("/account").page().one("#email-change").text() shouldContain "New email address"
        client.get("/account?emailChanged=1").page().text() shouldContain "That's your address now"
    }

    @Test
    @Obligation("ACC-30")
    fun `a wrong code is refused, five lock the change even for the right one`() = test.webTest(session = kim) { client ->
        client.ask("kim.new@example.com")
        val right = codeFor("kim.new@example.com")
        val wrong = if (right == "000000") "111111" else "000000"

        client.confirm(wrong).let { runBlocking { it.status.value } } shouldBe 200
        repeat(4) { client.confirm(wrong) }
        client.confirm(right).location shouldBe null
        emailOfKim() shouldBe "kim@example.com"
    }

    @Test
    @Obligation("ACC-31")
    fun `an address somebody else has looks the same as any other, and gets no mail`() = test.webTest(session = kim) { client ->
        val before = test.mailbox.all.size

        client.ask("bram@example.com").location shouldBe "/account/email"

        test.mailbox.all.size shouldBe before
        client.get("/account/email").page().one("strong").text() shouldBe "bram@example.com"
        client.confirm("123456").location shouldBe null
        emailOfKim() shouldBe "kim@example.com"
    }

    @Test
    @Obligation("ACC-32")
    fun `a bad or unchanged address is explained, and codes are rationed`() = test.webTest(session = kim) { client ->
        client.ask("nonsense").let { runBlocking { it.status.value } } shouldBe 200
        client.get("/account").page().select("#email-change.gf-field--invalid").size shouldBe 0
        client.ask("kim@example.com").let { runBlocking { it.status.value } } shouldBe 200

        repeat(5) { client.ask("a$it@example.com").location shouldBe "/account/email" }
        val sixth = client.ask("one-more@example.com")
        sixth.location shouldBe null
    }

    @Test
    @Obligation("ACC-29")
    fun `the change page needs a pending change, and a login`() {
        test.webTest(session = kim) { it.get("/account/email").location shouldBe "/account" }
        test.webTest { it.get("/account/email").location shouldStartWith "/login" }
    }

    @Test
    @Obligation("ACC-33")
    fun `the admin can't change the address the admin is known by`() {
        val tim = runBlocking { test.loggedIn("Tim") }
        val before = test.mailbox.all.size
        test.webTest(session = tim) { client ->
            val page = client.ask("tim2@example.com").bodyAsText()
            page shouldContain "This is the admin address"
        }
        test.mailbox.all.size shouldBe before
    }

    @Test
    @Obligation("ACC-30")
    fun `the last try is worded in the singular`() = test.webTest(session = kim) { client ->
        client.ask("kim.new@example.com")
        val right = codeFor("kim.new@example.com")
        val wrong = if (right == "000000") "111111" else "000000"
        repeat(3) { client.confirm(wrong) }

        runBlocking { client.confirm(wrong).let { it.bodyAsText() } } shouldContain "1 try left"
    }
}
