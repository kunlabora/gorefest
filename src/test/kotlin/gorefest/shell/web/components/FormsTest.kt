package gorefest.shell.web.components

import gorefest.contract.Obligation
import gorefest.core.PlayerId
import gorefest.shell.web.one
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import kotlin.uuid.Uuid
import kotlinx.html.FlowContent
import kotlinx.html.InputType
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.h2
import kotlinx.html.id
import kotlinx.html.stream.createHTML
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.junit.jupiter.api.Test

class FormsTest {
    private fun render(content: FlowContent.() -> Unit): Element = Jsoup.parseBodyFragment(createHTML().div { content() }).body()

    /** The ids this control's `aria-describedby` names, each resolved to its text on [root]. */
    private fun Element.descriptions(root: Element) = attr("aria-describedby").split(" ").filter { it.isNotEmpty() }.map { root.one("#$it").text() }

    @Test
    @Obligation("A11Y-1")
    fun `a field's label points at its control`() {
        val html = render { field("title", "Title") { fieldInput("title") { value = "Gorefest #7" } } }

        html.one("label").attr("for") shouldBe "title"
        html.one("input").id() shouldBe "title"
        html.one("input").attr("name") shouldBe "title"
    }

    @Test
    @Obligation("A11Y-2")
    fun `a field's hint and error describe its control, and an error marks it invalid`() {
        val plain = render { field("place", "Where", hint = "Only people who joined see it.") { fieldInput("place") } }
        plain.one("input").descriptions(plain) shouldBe listOf("Only people who joined see it.")
        plain.one("input").hasAttr("aria-invalid") shouldBe false

        val refused = render { field("place", "Where", hint = "Only people who joined see it.", error = "That's too long.") { fieldInput("place") } }
        refused.one("input").descriptions(refused) shouldBe listOf("Only people who joined see it.", "That's too long.")
        refused.one("input").attr("aria-invalid") shouldBe "true"
    }

    @Test
    @Obligation("A11Y-1", "A11Y-2")
    fun `the sign-in layout ties label, error and hint to the control beside its button`() {
        val html = render {
            signinField("code", "6-digit code", hint = "The code is quicker.", error = "That code has expired.", action = { button { +"Let me in" } }) { codeInput() }
        }

        html.one("label").attr("for") shouldBe "code"
        val code = html.one(".gf-signin__row > input#code")
        html.one(".gf-signin__row > button").text() shouldBe "Let me in"
        code.descriptions(html) shouldBe listOf("The code is quicker.", "That code has expired.")
        code.attr("aria-invalid") shouldBe "true"
    }

    @Test
    @Obligation("A11Y-1", "A11Y-3")
    fun `a switch is named, says it is a switch, and its row's line describes it`() {
        val row = render { switchRow("games", "Ask about games as well", "Put a few games to the group.", checked = true) }
        val switch = row.one("label input[type=checkbox]")
        switch.attr("role") shouldBe "switch"
        switch.attr("aria-label") shouldBe "Ask about games as well"
        switch.descriptions(row) shouldBe listOf("Put a few games to the group.")

        val tile = render { switchTile("bringing", "Bringing Brass", checked = false, classes = "gf-tile", id = "bring-1-switch") { +"Brass" } }
        tile.one("label.gf-tile > input").attr("aria-label") shouldBe "Bringing Brass"
    }

    @Test
    @Obligation("A11Y-3")
    fun `chips and segmented controls sit in a fieldset that names the question`() {
        val player = PlayerId(Uuid.parse("00000000-0000-0000-0000-000000000001"))
        val html = render {
            playerChips(listOf(player), mapOf(player to "Anna"), checked = { true }, legend = "Who's at the table?")
            dateChips(listOf(LocalDate.of(2026, 11, 7)), checked = { false }, legend = "Which dates can you make?")
            segmented("time", "Time", listOf("1h", "2h"), checked = "1h")
        }

        html.select("fieldset").map { it.one("legend").text() } shouldBe listOf("Who's at the table?", "Which dates can you make?", "Time")
        html.select("fieldset input").filter { it.parent()?.tagName() != "label" }.shouldBeEmpty()
    }

    @Test
    @Obligation("A11Y-1", "A11Y-3")
    fun `a list of like inputs is a group named by its heading, each input named on its own`() {
        val html = render {
            h2 { id = "dates-heading"; +"Which dates could work?" }
            labelledGroup("dates", headingId = "dates-heading") { consumer.listedInput("dates", "Date", InputType.date) }
        }

        val group = html.one("#dates")
        group.attr("role") shouldBe "group"
        html.one("#${group.attr("aria-labelledby")}").text() shouldBe "Which dates could work?"
        group.one("input").attr("aria-label") shouldBe "Date"
    }
}
