package gorefest.shell.web.bgg

import gorefest.core.BggAccountLinked
import gorefest.core.PlayerId
import gorefest.core.gameday.bram
import gorefest.shell.web.TestApp
import gorefest.shell.web.components.page
import gorefest.shell.web.fragment
import gorefest.shell.web.htmxPost
import gorefest.shell.web.one
import gorefest.shell.web.page
import gorefest.shell.web.shouldReplace
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import io.ktor.client.request.get
import io.ktor.http.parameters
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

class BggRoutesTest {
    private val test = TestApp()
    private val app = test.app
    private val kimId = PlayerId(Uuid.random())
    private lateinit var kim: String

    @BeforeEach
    fun reset(): Unit = kotlinx.coroutines.runBlocking {
        test.reset()
        kim = test.loggedIn("Kim", id = kimId)
    }

    private suspend fun io.ktor.client.HttpClient.username(name: String) =
        htmxPost("/bgg/username", parameters { append("username", name); append("returnTo", "/") }).fragment()

    private suspend fun io.ktor.client.HttpClient.verify(name: String) =
        htmxPost("/bgg/verify", parameters { append("username", name); append("returnTo", "/gamedays/new") })

    private val code get() = app.bggVerification.code(kimId, "KimPlays")

    @Test
    fun `the home page suggests linking until it's done`() = test.webTest(session = kim) { client ->
        client.get("/").page().one(".gf-next a").attr("href") shouldBe "/bgg?returnTo=%2F"

        test.bgg.putInProfile("KimPlays", code)
        client.username("KimPlays")
        client.verify("KimPlays")

        client.get("/").page().select(".gf-next").shouldBeEmpty()
    }

    @Test
    fun `a known username gets a code to put in the profile`() = test.webTest(session = kim) { client ->
        val step = client.username("KimPlays")

        step shouldReplace "bgg-form"
        step.one("input[name=username]").`val`() shouldBe "KimPlays"
        step.one(".gf-copy__value").text() shouldBe code
        code shouldMatch Regex("GORE-[A-Z2-9]{6}")
        step.attr("hx-post") shouldBe "/bgg/verify"
    }

    @Test
    fun `unknown or empty usernames are explained`() = test.webTest(session = kim) { client ->
        test.bgg.missingUsers += "nobody"

        client.username("nobody").one(".gf-error").text() shouldBe "We can't find that BGG username."
        client.username(" ").one(".gf-error").text() shouldBe "Type your BGG username."
    }

    @Test
    fun `verifying before the code is on the profile says so`() = test.webTest(session = kim) { client ->
        client.verify("KimPlays").fragment().one(".gf-error").text() shouldBe
            "We can't see the code on your profile yet. Saved it? Then try again in a minute."
        app.bggUsernames[kimId] shouldBe null
    }

    @Test
    fun `with the code on the profile, the account is linked and you go back`() = test.webTest(session = kim) { client ->
        test.bgg.putInProfile("KimPlays", "My site: https://example.com $code")

        client.verify("KimPlays").headers["HX-Redirect"] shouldBe "/collection"

        app.bggUsernames[kimId] shouldBe "KimPlays"
        client.get("/bgg").page().one("h1").text() shouldBe "You're linked"
    }

    @Test
    fun `someone else's BGG account can't be taken`() = test.webTest(session = kim) { client ->
        app.db.transaction { app.store.append(listOf(BggAccountLinked(bram, "KimPlays"))) }
        test.bgg.putInProfile("KimPlays", code)

        client.verify("KimPlays").fragment().one(".gf-error").text() shouldBe "That BGG account is already linked to another player"
    }

    @Test
    fun `in dev mode the fake profile can be filled in from the page`() = test.webTest(session = kim) { client ->
        val step = client.username("KimPlays")
        val button = step.one("#dev-bgg button")

        button.attr("hx-post") shouldBe "/dev/bgg/profile"
        client.htmxPost("/dev/bgg/profile", parameters { append("username", "KimPlays"); append("code", code) })
        client.verify("KimPlays").headers["HX-Redirect"] shouldBe "/collection"
    }
}
