package gorefest.shell.web

import gorefest.core.bgg.Game
import kotlinx.html.FlowContent
import kotlinx.html.HtmlBlockTag
import kotlinx.html.div
import kotlinx.html.h3
import kotlinx.html.img
import kotlinx.html.p
import kotlinx.html.span
import kotlinx.html.style

/** Box art in its frame: the image letterboxed on a blurred copy, or a monogram when BGG has none. */
fun FlowContent.art(game: Game, extraClasses: String = "", extraStyle: String = "") {
    val image = game.thumbnail ?: game.image
    if (image != null) {
        span("gf-art $extraClasses".trim()) {
            style = "--art:url('$image');$extraStyle"
            img(src = image, alt = "") { attributes["loading"] = "lazy" }
        }
    } else {
        span("gf-art gf-art--missing $extraClasses".trim()) {
            if (extraStyle.isNotBlank()) style = extraStyle
            attributes["data-hue"] = (game.id.value % 3).toString()
            span("gf-art__mono") { attributes["aria-hidden"] = "true"; +initials(game.name) }
        }
    }
}

/** A compact game row: art, name, facts, and whatever goes on the right (a switch, a time). */
fun FlowContent.gameRow(game: Game, meta: String = game.facts, trailing: HtmlBlockTag.() -> Unit = {}) = div("gf-game gf-game--compact") {
    art(game)
    div("gf-game__body") {
        h3("gf-game__name") { +game.name }
        if (meta.isNotBlank()) p("gf-game__meta") { +meta }
    }
    trailing()
}
