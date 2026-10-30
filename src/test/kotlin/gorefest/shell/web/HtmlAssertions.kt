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
suspend fun HttpResponse.page(): Document = Jsoup.parse(bodyAsText()).also { it.shouldHaveResolvableHxTargets(); it.shouldNameEveryControl() }

/** Parses an htmx fragment: exactly one top-level element, returned for swapping. */
suspend fun HttpResponse.fragment(): Element {
    val roots = Jsoup.parseBodyFragment(bodyAsText()).body().children()
    withClue("an htmx fragment has exactly one top-level element") { roots shouldHaveSize 1 }
    return roots.single().also { it.shouldNameEveryControl() }
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
