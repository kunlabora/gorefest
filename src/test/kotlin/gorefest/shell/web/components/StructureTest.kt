package gorefest.shell.web.components

import gorefest.contract.Obligation
import gorefest.shell.web.one
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.html.div
import kotlinx.html.html
import kotlinx.html.stream.createHTML
import org.jsoup.Jsoup
import org.junit.jupiter.api.Test

class StructureTest {
    @Test
    @Obligation("A11Y-7")
    fun `every page names its language and has a main region, a named nav and a status line`() {
        val me = gorefest.shell.web.CurrentPlayer(gorefest.core.PlayerId(kotlin.uuid.Uuid.parse("00000000-0000-0000-0000-000000000001")), "Kim")
        val page = Jsoup.parse(createHTML().html { page("Game days", site = SiteBar(me, Nav.GAME_DAYS)) { pageHeading("Your game days") } })

        page.one("html").attr("lang") shouldBe "en"
        page.one("main h1").text() shouldBe "Your game days"
        page.one("nav").attr("aria-label") shouldBe "Main"
        page.one("#$ANNOUNCER").attr("role") shouldBe "status"
    }

    @Test
    @Obligation("A11Y-8")
    fun `a component's heading takes the level the page gives it`() {
        val html = Jsoup.parseBodyFragment(createHTML().div {
            emptyState("Nothing on the shelf yet", "Tick the games you're bringing.")
            emptyState("No games planned", "Nobody said they'd bring one.", level = Level.H3)
        }).body()

        html.select(".gf-empty__title").map { it.tagName() } shouldBe listOf("h2", "h3")
        Level.H2.below shouldBe Level.H3
        Level.H4.below shouldBe Level.H4
    }

    @Test
    @Obligation("A11Y-10")
    fun `a swapped-in element carries what to announce, and never an empty sentence`() {
        val html = Jsoup.parseBodyFragment(createHTML().div { div { announce("3 games found") }; div { refreshesQuietly() } }).body()

        html.select("[data-announce]").map { it.attr("data-announce") } shouldBe listOf("3 games found")
        html.select("[data-quiet=true]").size shouldBe 1
        shouldThrow<IllegalArgumentException> { createHTML().div { announce(" ") } }
        searchAnnouncement(3, "brass") shouldBe "3 games found"
        searchAnnouncement(0, "zzz") shouldBe "Nothing found for “zzz”"
    }
}
