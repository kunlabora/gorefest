package gorefest.shell.web

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
suspend fun HttpResponse.page(): Document = Jsoup.parse(bodyAsText()).also { it.shouldHaveResolvableHxTargets() }

/** Parses an htmx fragment: exactly one top-level element, returned for swapping. */
suspend fun HttpResponse.fragment(): Element {
    val roots = Jsoup.parseBodyFragment(bodyAsText()).body().children()
    withClue("an htmx fragment has exactly one top-level element") { roots shouldHaveSize 1 }
    return roots.single()
}

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
