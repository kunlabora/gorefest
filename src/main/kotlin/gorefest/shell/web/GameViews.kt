package gorefest.shell.web

import gorefest.core.game.Game
import kotlinx.html.FlowContent
import kotlinx.html.HtmlBlockTag
import kotlinx.html.div
import kotlinx.html.h3
import kotlinx.html.img
import kotlinx.html.p
import kotlinx.html.span
import kotlinx.html.style

/** BGG's complexity in words, as the design uses it. */
val Game.weightLabel: String
    get() = when {
        weight == 0.0 -> ""
        weight < 1.8 -> "light"
        weight < 2.6 -> "medium-light"
        weight < 3.4 -> "medium"
        weight < 4.0 -> "medium-heavy"
        else -> "heavy"
    }

/** "60–90 min · medium · 1–4" */
val Game.facts: String
    get() = listOf(
        if (minPlaytime == maxPlaytime) "$minPlaytime min" else "$minPlaytime–$maxPlaytime min",
        weightLabel,
        if (minPlayers == maxPlayers) "$minPlayers" else "$minPlayers–$maxPlayers",
    ).filter { it.isNotBlank() }.joinToString(" · ")

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
