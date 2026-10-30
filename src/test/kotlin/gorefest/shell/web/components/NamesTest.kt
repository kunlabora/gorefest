package gorefest.shell.web.components

import gorefest.contract.Obligation
import gorefest.shell.web.one
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.html.FlowContent
import kotlinx.html.div
import kotlinx.html.stream.createHTML
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.junit.jupiter.api.Test

class NamesTest {
    private fun render(content: FlowContent.() -> Unit): Element = Jsoup.parseBodyFragment(createHTML().div { content() }).body()

    @Test
    @Obligation("A11Y-4")
    fun `a button is named by its label, or by what it says when the label is shared`() {
        val plain = render { actionButton("Sync again", icon = "refresh") }.one("button")
        plain.text() shouldBe "Sync again"
        plain.hasAttr("aria-label") shouldBe false
        plain.attr("type") shouldBe "button"

        val take = render { actionButton("Take off", ButtonStyle.GHOST, ButtonSize.SMALL, submit = true, says = "Take Brass off the poll") }.one("button")
        take.text() shouldBe "Take off"
        take.attr("aria-label") shouldBe "Take Brass off the poll"
        take.attr("type") shouldBe "submit"
    }

    @Test
    @Obligation("A11Y-4")
    fun `an icon-only button says what it does, also as its tooltip`() {
        val reroll = render { iconButton("Another title", "refresh") }.one("button")

        reroll.text() shouldBe ""
        reroll.attr("aria-label") shouldBe "Another title"
        reroll.attr("title") shouldBe "Another title"
    }

    @Test
    @Obligation("A11Y-4")
    fun `a control can't be named with nothing`() {
        shouldThrow<IllegalArgumentException> { render { actionButton(" ") } }
        shouldThrow<IllegalArgumentException> { render { actionButton("Take off", says = "") } }
        shouldThrow<IllegalArgumentException> { render { iconButton("", "refresh") } }
        shouldThrow<IllegalArgumentException> { render { textButton("") } }
    }

    @Test
    @Obligation("A11Y-5")
    fun `icons and decoration are hidden, an informing image says what it shows`() {
        val html = render {
            actionButton("Add", icon = "plus")
            decoration("gf-fan") { +"Box art" }
            image("/static/howto.webp", Alt.Text("BGG's contact form"))
            image("/static/cover.webp", Alt.Decorative)
        }

        html.one("button svg").attr("aria-hidden") shouldBe "true"
        html.one(".gf-fan").attr("aria-hidden") shouldBe "true"
        html.select("img").map { it.attr("alt") } shouldBe listOf("BGG's contact form", "")
        shouldThrow<IllegalArgumentException> { Alt.Text(" ") }
    }

    @Test
    @Obligation("A11Y-6")
    fun `a toggle says whether it is on, and which game it is for`() {
        val off = render { chipToggle("I want to play this", pressed = false, icon = "heart", says = "I want to play Brass") }.one("button")
        val on = render { chipToggle("I really want to play this", pressed = true, icon = "heart", says = "I want to play Brass") }.one("button")

        off.attr("aria-pressed") shouldBe "false"
        on.attr("aria-pressed") shouldBe "true"
        on.attr("aria-label") shouldBe "I want to play Brass"
    }

    @Test
    @Obligation("A11Y-6")
    fun `a switch says it is a switch and whether it is on`() {
        val switch = render { switchRow("games", "Ask about games as well", "Put a few games to the group.", checked = true) }.one("input")

        switch.attr("role") shouldBe "switch"
        switch.hasAttr("checked") shouldBe true
    }

    @Test
    @Obligation("A11Y-6")
    fun `a progress bar tells a screen reader the count the screen shows`() {
        val bar = render { progressSteps(step = 0, total = 12, label = "Taste round") }.one("[role=progressbar]")

        bar.attr("aria-valuenow") shouldBe "1"
        bar.attr("aria-valuetext") shouldBe "Pair 1 of 12"
        bar.one(".gf-progress__count").text() shouldBe "1 / 12"
    }
}
