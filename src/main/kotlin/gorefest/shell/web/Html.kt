package gorefest.shell.web

import kotlinx.html.BODY
import kotlinx.html.HTML
import kotlinx.html.HTMLTag
import kotlinx.html.body
import kotlinx.html.head
import kotlinx.html.lang
import kotlinx.html.link
import kotlinx.html.main
import kotlinx.html.meta
import kotlinx.html.script
import kotlinx.html.title

fun HTML.page(title: String, content: kotlinx.html.MAIN.() -> Unit) {
    lang = "en"
    head {
        meta(charset = "utf-8")
        meta(name = "viewport", content = "width=device-width, initial-scale=1")
        title(title)
        link(rel = "stylesheet", href = "https://cdn.jsdelivr.net/npm/@picocss/pico@2/css/pico.classless.min.css")
        script(src = "/webjars/htmx.org/dist/htmx.min.js") {}
        script(src = "/webjars/hyperscript.org/dist/_hyperscript.min.js") {}
    }
    body { main { content() } }
}

/** htmx attribute, e.g. `hx("post", "/gamedays")` → `hx-post="/gamedays"`. */
fun HTMLTag.hx(name: String, value: String) {
    attributes["hx-$name"] = value
}

/** hyperscript `_` attribute. */
fun HTMLTag.hyperscript(script: String) {
    attributes["_"] = script
}
