package gorefest.shell.web.components

import kotlinx.html.ButtonType
import kotlinx.html.DIV
import kotlinx.html.FlowContent
import kotlinx.html.footer
import kotlinx.html.FormMethod
import kotlinx.html.HEAD
import kotlinx.html.HTML
import kotlinx.html.HTMLTag
import kotlinx.html.MAIN
import kotlinx.html.a
import kotlinx.html.body
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.head
import kotlinx.html.header
import kotlinx.html.id
import kotlinx.html.img
import kotlinx.html.lang
import kotlinx.html.link
import kotlinx.html.main
import kotlinx.html.meta
import kotlinx.html.p
import kotlinx.html.script
import kotlinx.html.span
import kotlinx.html.style
import kotlinx.html.title
import kotlinx.html.unsafe
import java.util.concurrent.ConcurrentHashMap

/**
 * The page shell from the design system (docs/design): an app bar, the scrolling body,
 * and an optional dock at the bottom for the screen's primary action.
 */
fun HTML.page(
    title: String,
    appBar: FlowContent.() -> Unit = {},
    dock: (DIV.() -> Unit)? = null,
    head: HEAD.() -> Unit = {},
    mainClasses: String = "",
    mainId: String? = null,
    content: MAIN.() -> Unit,
) {
    lang = "en"
    head {
        meta(charset = "utf-8")
        meta(name = "viewport", content = "width=device-width, initial-scale=1")
        title(title)
        // The app is for one friend group: keep it out of search engines (see also robots.txt and X-Robots-Tag).
        meta(name = "robots", content = "noindex, nofollow, noarchive, noimageindex")
        // First, and blocking: it sets the saved theme before anything paints.
        script(src = "/static/js/theme.js") {}
        // The two fonts every page uses, fetched while the stylesheet still loads (fonts need crossorigin even from the same origin).
        PRELOADED_FONTS.forEach { font ->
            link(rel = "preload", href = "/static/fonts/$font", type = "font/woff2") { attributes["as"] = "font"; attributes["crossorigin"] = "" }
        }
        link(rel = "icon", href = "/static/icons/favicon.svg", type = "image/svg+xml")
        link(rel = "icon", href = "/static/icons/favicon.ico") { attributes["sizes"] = "any" }
        link(rel = "apple-touch-icon", href = "/static/icons/apple-touch-icon.png")
        link(rel = "stylesheet", href = "/static/css/tokens.css")
        link(rel = "stylesheet", href = "/static/css/gorefest.css")
        script(src = "/webjars/htmx.org/dist/htmx.min.js") {}
        script(src = "/static/js/htmx-config.js") {}
        script(src = "/webjars/hyperscript.org/dist/_hyperscript.min.js") {}
        head()
    }
    body {
        div("gf-screen") {
            header("gf-appbar") {
                appBar()
                themeToggle()
            }
            main("gf-body $mainClasses".trim()) {
                if (mainId != null) id = mainId
                content()
                footer("gf-footer") {
                    bggCredit()
                    p("gf-credit gf-credit--version") { +"Gorefest v$APP_VERSION" }
                }
            }
            if (dock != null) div("gf-dock") { dock() }
        }
    }
}

/** Read from `version.properties`, which Gradle fills from the project version, so the footer always shows the built jar's version. */
private val APP_VERSION: String = object {}.javaClass.getResourceAsStream("/version.properties")
    ?.use { java.util.Properties().apply { load(it) }.getProperty("version") } ?: "dev"

private val PRELOADED_FONTS = listOf("atkinson-hyperlegible-next-latin.woff2", "bricolage-grotesque-latin.woff2")

/** BoardGameGeek's data and box art are on every screen, so every screen credits it, in words and with its "Powered by BGG" logo. */
fun FlowContent.bggCredit() = p("gf-credit") {
    +"Game data and images from "
    a("https://boardgamegeek.com", target = "_blank") { attributes["rel"] = "noopener noreferrer"; +"BoardGameGeek" }
    +"."
    a("https://boardgamegeek.com", target = "_blank", classes = "gf-credit__logo") {
        attributes["rel"] = "noopener noreferrer"
        img(src = "/static/poweredbybgg.webp", alt = "Powered by BGG") { width = "120"; height = "35"; attributes["loading"] = "lazy" }
    }
}

fun FlowContent.appBarTitle(text: String) = span("gf-appbar__title") { +text }

fun FlowContent.backLink(href: String, label: String) = a(href, classes = "gf-iconbtn") {
    attributes["aria-label"] = label
    icon("back")
}

/** A small ghost action at the right end of the app bar ("Edit", "Done for now", "Skip"). */
fun FlowContent.appBarAction(href: String, label: String) = a(href, classes = "gf-btn gf-btn--ghost gf-btn--sm gf-appbar__end") { +label }

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

fun FlowContent.logoutButton() = form(action = "/logout", method = FormMethod.post, classes = "gf-appbar__end") {
    button(type = ButtonType.submit, classes = "gf-btn gf-btn--ghost gf-btn--sm") { +"Log out" }
}

/** htmx attribute, e.g. `hx("post", "/gamedays")` → `hx-post="/gamedays"`. */
fun HTMLTag.hx(name: String, value: String) {
    attributes["hx-$name"] = value
}

/** hyperscript `_` attribute. */
fun HTMLTag.hyperscript(script: String) {
    attributes["_"] = script
}

/** An Open Graph `<meta>` for the link preview of a page that is shared (an invite). */
fun HEAD.openGraph(property: String, value: String) = meta {
    attributes["property"] = property
    content = value
}
