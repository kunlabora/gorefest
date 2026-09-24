package gorefest.shell.web

import gorefest.core.PlayerId
import kotlinx.html.DIV
import kotlinx.html.FlowContent
import kotlinx.html.HTML
import kotlinx.html.HTMLTag
import kotlinx.html.MAIN
import kotlinx.html.a
import kotlinx.html.body
import kotlinx.html.div
import kotlinx.html.h3
import kotlinx.html.head
import kotlinx.html.header
import kotlinx.html.lang
import kotlinx.html.link
import kotlinx.html.main
import kotlinx.html.meta
import kotlinx.html.p
import kotlinx.html.script
import kotlinx.html.span
import kotlinx.html.title
import kotlinx.html.unsafe
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * The page shell from the design system (docs/design): an app bar, the scrolling body,
 * and an optional dock at the bottom for the screen's primary action.
 */
fun HTML.page(
    title: String,
    appBar: FlowContent.() -> Unit = {},
    dock: (DIV.() -> Unit)? = null,
    content: MAIN.() -> Unit,
) {
    lang = "en"
    head {
        meta(charset = "utf-8")
        meta(name = "viewport", content = "width=device-width, initial-scale=1")
        title(title)
        link(rel = "stylesheet", href = "/static/css/tokens.css")
        link(rel = "stylesheet", href = "/static/css/gorefest.css")
        script(src = "/webjars/htmx.org/dist/htmx.min.js") {}
        script(src = "/webjars/hyperscript.org/dist/_hyperscript.min.js") {}
    }
    body {
        div("gf-screen") {
            header("gf-appbar") { appBar() }
            main("gf-body") { content() }
            if (dock != null) div("gf-dock") { dock() }
        }
    }
}

fun FlowContent.appBarTitle(text: String) = span("gf-appbar__title") { +text }

fun FlowContent.backLink(href: String, label: String) = a(href, classes = "gf-iconbtn") {
    attributes["aria-label"] = label
    icon("back")
}

/** An icon from docs/design/icons, inlined so it takes the text colour. */
fun HTMLTag.icon(name: String) = unsafe { +Icons.svg(name) }

object Icons {
    private val cache = ConcurrentHashMap<String, String>()

    fun svg(name: String): String = cache.getOrPut(name) {
        val file = Icons::class.java.getResource("/static/icons/$name.svg")?.readText()
            ?: error("No icon '$name' in docs/design/icons")
        file.trim()
            .replace("stroke=\"#30362f\"", "stroke=\"currentColor\"")
            .replaceFirst("<svg ", "<svg class=\"gf-icon\" aria-hidden=\"true\" ")
    }
}

/** Initials disc; players have no names yet, so it shows the start of the id. */
fun FlowContent.avatar(player: PlayerId, size: String = "") = span("gf-avatar $size".trim()) {
    attributes["aria-hidden"] = "true"
    attributes["data-hue"] = player.toString().sumOf { it.code }.mod(4).toString()
    +player.toString().take(2).uppercase()
}

fun FlowContent.emptyState(title: String, text: String, action: FlowContent.() -> Unit = {}) = div("gf-empty") {
    unsafe { +EMPTY_ART }
    h3("gf-empty__title") { +title }
    p("gf-empty__text") { +text }
    action()
}

private const val EMPTY_ART = """<svg class="gf-empty__art" viewBox="0 0 96 72" aria-hidden="true"><rect class="box" x="6" y="30" width="56" height="36" rx="6"/><rect class="box" x="30" y="16" width="56" height="36" rx="6"/><rect class="lid" x="18" y="4" width="40" height="40" rx="8" transform="rotate(-8 38 24)"/><circle class="pip" cx="30" cy="16" r="3.5"/><circle class="pip" cx="38" cy="24" r="3.5"/><circle class="pip" cx="46" cy="32" r="3.5"/></svg>"""

private val longDate = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH)
private val shortMonth = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)

/** "Sun 27 Sep 2026" */
fun LocalDate.long(): String = format(longDate)

fun LocalDate.monthShort(): String = format(shortMonth)

/** htmx attribute, e.g. `hx("post", "/gamedays")` → `hx-post="/gamedays"`. */
fun HTMLTag.hx(name: String, value: String) {
    attributes["hx-$name"] = value
}

/** hyperscript `_` attribute. */
fun HTMLTag.hyperscript(script: String) {
    attributes["_"] = script
}
