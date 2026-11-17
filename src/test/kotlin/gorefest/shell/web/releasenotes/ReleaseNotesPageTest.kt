package gorefest.shell.web.releasenotes

import gorefest.contract.Obligation
import gorefest.shell.web.TestApp
import gorefest.shell.web.components.APP_VERSION
import gorefest.shell.web.one
import gorefest.shell.web.page
import gorefest.shell.web.texts
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ReleaseNotesPageTest {
    private val test = TestApp()

    @BeforeEach
    fun reset() = kotlinx.coroutines.runBlocking { test.reset() }

    @Test
    @Obligation("WEB-17")
    fun `the footer's version links to the release notes, signed in and signed out`() {
        test.webTest { client ->
            listOf("/login", "/privacy", "/release-notes").forEach { path ->
                val link = client.get(path).page().one(".gf-footer .gf-credit--version a")
                link.attr("href") shouldBe "/release-notes"
                link.text() shouldBe "Boardfest v$APP_VERSION"
            }
        }
        test.webTest(session = kotlinx.coroutines.runBlocking { test.loggedIn("Tim") }) { client ->
            listOf("/", "/account").forEach { path ->
                client.get(path).page().one(".gf-footer .gf-credit--version a").attr("href") shouldBe "/release-notes"
            }
        }
    }

    @Test
    @Obligation("WEB-20")
    fun `the release notes are readable signed out`() = test.webTest { client ->
        val response = client.get("/release-notes")
        response.status shouldBe HttpStatusCode.OK
        response.page().one("h1").text() shouldBe "What's new"
    }

    @Test
    @Obligation("WEB-18")
    fun `every version shows newest first with its date, and only the kinds it has`() = test.webTest { client ->
        val page = client.get("/release-notes").page()
        val releases = ReleaseNotes.bundled.releases

        page.select(".gf-release").map { it.id() } shouldBe releases.map { "v${it.version}" }

        val fixOnly = releases.first { it.new.isEmpty() && it.changed.isEmpty() }
        val section = page.one("#v${fixOnly.version.replace(".", "\\.")}")
        section.one("time").attr("datetime") shouldBe fixOnly.date.toString()
        section.texts("h3") shouldBe listOf("Fixed")
        section.texts("li") shouldBe fixOnly.fixed

        val all = releases.first { it.new.isNotEmpty() && it.changed.isNotEmpty() && it.fixed.isNotEmpty() }
        page.one("#v${all.version.replace(".", "\\.")}").texts("h3") shouldBe listOf("New", "Changed", "Fixed")
    }
}
