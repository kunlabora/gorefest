package gorefest.shell.web.bgg

import gorefest.contract.Obligation
import gorefest.core.BggAccountLinked
import gorefest.core.BggAccountLinkedHashed
import gorefest.core.PlayerId
import gorefest.core.gameday.bram
import gorefest.shell.web.TestApp
import gorefest.shell.web.fragment
import gorefest.shell.web.htmxPost
import gorefest.shell.web.one
import gorefest.shell.web.page
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.ktor.client.request.forms.submitForm
import io.ktor.http.Parameters
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

    private suspend fun io.ktor.client.HttpClient.link(name: String, returnTo: String = "/gamedays/new") =
        htmxPost("/bgg/username", parameters { append("username", name); append("returnTo", returnTo) })

    @Test
    @Obligation("BGG-10")
    fun `the home page suggests linking until it's done`() = test.webTest(session = kim) { client ->
        client.get("/").page().one("#bgg-nudge a").attr("href") shouldBe "/bgg?returnTo=%2F"

        client.link("KimPlays")

        client.get("/").page().select("#bgg-nudge").shouldBeEmpty()
    }

    @Test
    @Obligation("BGG-34")
    fun `the link page asks only for the username, with Collection as the current nav item`() = test.webTest(session = kim) { client ->
        val page = client.get("/bgg?returnTo=%2F").page()

        page.one(".gf-nav a[aria-current=page]").text() shouldBe "Collection"
        page.select("#bgg-form input:not([type=hidden])").map { it.attr("name") } shouldBe listOf("username")
        page.one("#bgg-form .gf-signin__row button[type=submit]").text() shouldBe "Link"
        page.one("#bgg-form").attr("hx-post") shouldBe "/bgg/username"
        page.one("#bgg-form a[href='/']").text() shouldBe "Not now"
        page.select(".gf-copy__value, .gf-stepper, img.gf-howto").shouldBeEmpty()
    }

    @Test
    @Obligation("BGG-2")
    fun `unknown or empty usernames are explained, and nothing is linked`() = test.webTest(session = kim) { client ->
        test.bgg.missingUsers += "nobody"

        client.link("nobody").fragment().one(".gf-error").text() shouldBe "We can't find that BGG username."
        client.link(" ").fragment().one(".gf-error").text() shouldBe "Type your BGG username."
        app.bggAccounts.isLinked(kimId) shouldBe false
    }

    @Test
    @Obligation("BGG-33", "BGG-9")
    fun `a known username is linked at once, and you go back`() = test.webTest(session = kim) { client ->
        client.link("KimPlays").headers["HX-Redirect"] shouldBe "/collection?returnTo=%2Fgamedays%2Fnew"

        app.bggAccounts.isLinked(kimId) shouldBe true
        client.get("/bgg").page().one("h1").text() shouldBe "You're linked"
    }

    @Test
    @Obligation("BGG-6")
    fun `the log holds a hash of the username, never the name`() = test.webTest(session = kim) { client ->
        client.link("KimPlays")

        val log = app.db.transaction {
            contextOf<gorefest.shell.db.Tx>().connection.createStatement().use { st ->
                st.executeQuery("select string_agg(data::text || tags::text, ' ') from events").use { rs -> rs.next(); rs.getString(1) }
            }
        }
        log.lowercase().contains("kimplays") shouldBe false
        log.contains(app.bggUsernameKey.of("kimplays")) shouldBe true
        client.get("/collection/status").status shouldBe io.ktor.http.HttpStatusCode.OK
        client.get("/collection").page().text().contains("KimPlays") shouldBe true
    }

    @Test
    @Obligation("BGG-7")
    fun `unlinking forgets the account and the collection, and the player can link again`() = test.webTest(session = kim) { client ->
        client.link("KimPlays")
        kotlinx.coroutines.withTimeout(5000) { while (app.collectionSync.status(kimId) == null) kotlinx.coroutines.delay(20) }

        client.get("/bgg").page().select("form[action=/bgg/unlink]").size shouldBe 1
        client.submitForm("/bgg/unlink", Parameters.Empty).headers["Location"] shouldBe "/bgg"

        app.bggAccounts.isLinked(kimId) shouldBe false
        app.collectionSync.collection(kimId).isEmpty() shouldBe true
        app.collectionSync.status(kimId) shouldBe null
        client.get("/bgg").page().select("input[name=username]").size shouldBe 1
        client.get("/collection").headers["Location"] shouldBe "/bgg?returnTo=%2Fcollection"
        client.link("OtherPlays").headers["HX-Redirect"] shouldBe "/collection?returnTo=%2Fgamedays%2Fnew"
    }

    @Test
    @Obligation("BGG-5")
    fun `a second account is refused until you unlink`() = test.webTest(session = kim) { client ->
        client.link("KimPlays")

        client.link("OtherPlays").fragment().one(".gf-error").text() shouldBe "You've already linked a BGG account"
    }

    @Test
    @Obligation("BGG-35")
    fun `an account someone else linked can be linked too, whatever the case`() = test.webTest(session = kim) { client ->
        app.db.transaction {
            app.store.append(listOf(BggAccountLinkedHashed(bram, app.bggUsernameKey.of("kimplays")), BggAccountLinked(PlayerId(Uuid.random()), "KimPlays")))
        }

        client.link("kimplays").headers["HX-Redirect"] shouldBe "/collection?returnTo=%2Fgamedays%2Fnew"
        app.bggAccounts.isLinked(kimId) shouldBe true
        app.bggAccounts.isLinked(bram) shouldBe true
    }

    @Test
    @Obligation("BGG-9")
    fun `the collection page offers the way back to where you were going`() = test.webTest(session = kim) { client ->
        client.link("KimPlays")

        val back = client.get("/collection?returnTo=%2Fgamedays%2Fabc%2Fbring").page().one(".gf-continue a.gf-btn--primary")
        back.attr("href") shouldBe "/gamedays/abc/bring"
        back.text() shouldBe "Back to bringing games"
        client.get("/collection").page().select(".gf-continue") shouldHaveSize 0
    }
}
