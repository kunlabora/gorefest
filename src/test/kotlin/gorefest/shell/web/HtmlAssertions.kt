package gorefest.shell.web

import gorefest.shell.web.components.full
import gorefest.shell.web.components.hx
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/** Parses a full page and checks the htmx wiring on it. */
suspend fun HttpResponse.page(): Document = Jsoup.parse(bodyAsText()).also {
    it.shouldHaveResolvableHxTargets()
    it.shouldNameEveryControl()
    it.shouldHaveSoundIds(references = PAGE_REFERENCES)
    it.shouldGroupEveryChoice()
    it.shouldHaveSoundStructure()
}

/** Parses an htmx fragment: exactly one top-level element, returned for swapping. */
suspend fun HttpResponse.fragment(): Element {
    val roots = Jsoup.parseBodyFragment(bodyAsText()).body().children()
    withClue("an htmx fragment has exactly one top-level element") { roots shouldHaveSize 1 }
    return roots.single().also {
        it.shouldNameEveryControl()
        it.shouldHaveSoundIds(references = FRAGMENT_REFERENCES)
        it.shouldGroupEveryChoice()
        it.shouldAnnounceItself()
    }
}

/**
 * A swapped-in fragment tells a screen reader what changed: it carries a sentence to announce, or an alert, or says it
 * is a background refresh of a change announced elsewhere (`refreshesQuietly`).
 */
fun Element.shouldAnnounceItself() {
    withClue("fragment <${tagName()} id=${id()}> announces itself (announce or refreshesQuietly) or carries a role=alert") {
        (attr("data-announce").isNotBlank() || attr("data-quiet") == "true" || selectFirst("[role=alert]") != null) shouldBe true
    }
}

/** Attributes that point at an id on the page. A fragment may point outside itself with `aria-controls`, so it checks fewer. */
private val PAGE_REFERENCES = listOf("for", "aria-labelledby", "aria-describedby", "aria-controls")
private val FRAGMENT_REFERENCES = listOf("for", "aria-labelledby", "aria-describedby")

/** Ids are unique, and every label, description and control reference names an id that exists, so it reads the right text. */
fun Element.shouldHaveSoundIds(references: List<String> = PAGE_REFERENCES) {
    val ids = select("[id]").map { it.id() }
    withClue("ids are unique") { ids.groupBy { it }.filterValues { it.size > 1 }.keys shouldHaveSize 0 }
    val dangling = references.flatMap { attr ->
        select("[$attr]").flatMap { el -> el.attr(attr).split(" ").filter { it.isNotBlank() && it !in ids }.map { "$attr=\"$it\" on <${el.tagName()}>" } }
    }
    withClue("references resolve: $dangling") { dangling shouldHaveSize 0 }
}

/**
 * Radios and checkboxes that share a name are one question: they sit in a fieldset with a legend, or a group with a name.
 * Switches are left out: each is a setting of its own and carries its whole name ("Bringing Brass").
 */
fun Element.shouldGroupEveryChoice() {
    val choices = select("input[type=radio][name], input[type=checkbox][name]:not([role=switch])").groupBy { it.attr("name") }.filterValues { it.size > 1 }.values.flatten()
    val ungrouped = choices.filter { input ->
        input.parents().none { p ->
            (p.tagName() == "fieldset" && p.selectFirst("> legend")?.text()?.isNotBlank() == true) ||
                (p.attr("role") in setOf("group", "radiogroup") && (p.attr("aria-label").isNotBlank() || p.hasAttr("aria-labelledby")))
        }
    }
    withClue("every set of choices is a named group: ${ungrouped.map { it.attr("name") }.distinct()}") { ungrouped shouldHaveSize 0 }
}

/**
 * Every button, link and form control has a name a screen reader can say, and every image an alt (empty for decoration).
 * The components guarantee it; this catches what a type can't, such as a label computed to nothing.
 */
fun Element.shouldNameEveryControl() {
    val unnamed = select("button, a[href], input:not([type=hidden]), select, textarea").filter { !it.hasAccessibleName(this) } +
        select("img:not([alt])")
    withClue("every control is named and every image has an alt: ${unnamed.joinToString { it.outerHtml().take(120) }}") { unnamed shouldHaveSize 0 }
}

private fun Element.hasAccessibleName(root: Element): Boolean =
    attr("aria-label").isNotBlank() ||
        attr("aria-labelledby").split(" ").any { it.isNotBlank() && root.getElementById(it)?.text()?.isNotBlank() == true } ||
        (tagName() in setOf("button", "a") && (text().isNotBlank() || select("img[alt]").any { it.attr("alt").isNotBlank() })) ||
        (id().isNotEmpty() && root.select("label[for=${id()}]").any { it.text().isNotBlank() }) ||
        parents().any { it.tagName() == "label" && it.text().isNotBlank() }

/** Every `hx-target="#id"` on the page points at an element that exists. */
fun Document.shouldHaveResolvableHxTargets() {
    select("[hx-target^=#]").forEach { el ->
        val target = el.attr("hx-target")
        withClue("hx-target=\"$target\" on <${el.tagName()}> resolves") { selectFirst(target).shouldNotBeNull() }
    }
}

/** This fragment can replace the element htmx targets with `hx-target="#id" hx-swap="outerHTML"`. */
infix fun Element.shouldReplace(targetId: String) {
    withClue("fragment root replaces #$targetId") { id() shouldBe targetId }
}

fun Element.one(selector: String): Element =
    withClue("exactly one '$selector'") { select(selector).also { it shouldHaveSize 1 }.single() }

fun Element.texts(selector: String): List<String> = select(selector).map { it.text() }

/**
 * The page has a language, one main heading, one main region and named navigation, and its headings don't skip a level
 * on the way down (h1 then h3 leaves a screen reader's heading list with a hole). Visually hidden headings count; headings
 * inside `aria-hidden` decoration don't, since a screen reader never reaches them.
 */
fun Document.shouldHaveSoundStructure() {
    withClue("<html lang> is set") { selectFirst("html")!!.attr("lang").isNotBlank() shouldBe true }
    withClue("exactly one <h1>: ${select("h1").map { it.text() }}") { select("h1") shouldHaveSize 1 }
    withClue("exactly one <main>") { select("main") shouldHaveSize 1 }
    withClue("every <nav> is named") { select("nav").filter { it.attr("aria-label").isBlank() && !it.hasAttr("aria-labelledby") } shouldHaveSize 0 }
    val headings = select("h1, h2, h3, h4, h5, h6").filter { h -> (listOf(h) + h.parents()).none { it.attr("aria-hidden") == "true" } }
    withClue("exactly one <h1> a screen reader reaches") { headings.count { it.tagName() == "h1" } shouldBe 1 }
    val skips = headings.map { it.tagName()[1].digitToInt() to it.text() }.zipWithNext()
        .filter { (a, b) -> b.first > a.first + 1 }.map { (a, b) -> "h${a.first} \"${a.second.take(30)}\" → h${b.first} \"${b.second.take(30)}\"" }
    withClue("headings don't skip a level: $skips") { skips shouldHaveSize 0 }
}
