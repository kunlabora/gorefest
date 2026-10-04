package gorefest.shell.web.components

import gorefest.core.PlayerId
import gorefest.core.game.Game
import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.HtmlBlockTag
import kotlinx.html.InputType
import kotlinx.html.P
import kotlinx.html.a
import kotlinx.html.article
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.h3
import kotlinx.html.id
import kotlinx.html.img
import kotlinx.html.input
import kotlinx.html.label
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

/** The game's page on BoardGameGeek, which we credit wherever its data is shown. */
val Game.bggUrl: String get() = "https://boardgamegeek.com/boardgame/${id.value}"

/** The game's name as a link to its BGG page. For places that aren't buttons or labels themselves. */
fun FlowContent.bggNameLink(game: Game) = a(game.bggUrl, target = "_blank", classes = "gf-bgglink") {
    attributes["rel"] = "noopener noreferrer"
    +game.name
}

/**
 * An image URL that is safe in `src` and inside a CSS `url('...')`: http(s) from BGG, or our own `/static/` files.
 * Quotes, parentheses, backslashes, whitespace and the like are percent-encoded.
 */
fun safeImageUrl(url: String): String? {
    val trimmed = url.trim()
    val ok = trimmed.startsWith("https://") || trimmed.startsWith("http://") || trimmed.startsWith("/static/")
    if (!ok) return null
    return buildString {
        for (c in trimmed) {
            if (c.code <= 0x20 || c in "'\"()\\<>;{}") append('%').append("%02X".format(c.code)) else append(c)
        }
    }
}

/** Box art in its frame: the image letterboxed on a blurred copy, or a monogram when BGG has none. */
fun FlowContent.art(game: Game, extraClasses: String = "", extraStyle: String = "") {
    val image = (game.thumbnail ?: game.image)?.let(::safeImageUrl)
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
        h3("gf-game__name") { bggNameLink(game) }
        if (meta.isNotBlank()) p("gf-game__meta") { +meta }
    }
    trailing()
}

/** A game on a shelf: art, the owner's avatar, name. */
fun FlowContent.gameTile(game: Game, owner: PlayerId?, ownerName: String?) = div("gf-tile") {
    art(game)
    span("gf-tile__owner") { if (owner != null) avatar(owner, ownerName) }
    span("gf-tile__name") { bggNameLink(game) }
}

/** A game tile that's a radio button, for "which game?" pickers. */
fun FlowContent.gameTileChoice(game: Game, name: String, checked: Boolean) = label("gf-tile") {
    input(InputType.radio, name = name) { value = game.id.toString(); this.checked = checked }
    art(game)
    span("gf-tile__name") { +game.name }
}

/**
 * A numbered pick (payoff podium, suggestions): the first one large with wide art.
 * [meta] fills the facts line, [reason] says why.
 */
fun FlowContent.rankedGame(rank: Int, game: Game, reason: String, meta: P.() -> Unit = { +game.facts }) =
    article(if (rank == 1) "gf-game gf-game--large" else "gf-game") {
        span("gf-game__rank") { +"$rank" }
        art(game, if (rank == 1) "gf-art--wide" else "")
        div("gf-game__body") {
            h3("gf-game__name") { bggNameLink(game) }
            p("gf-game__meta") { meta() }
            p("gf-game__reason") { icon("spark"); +reason }
        }
    }

/**
 * "Which of these two?": two big pick buttons (answer A or B) with "or" between them, dealt in.
 * With [keyIds], the picks get ids `pick-a` / `pick-b` for the keyboard shortcuts.
 */
fun FlowContent.duel(a: Game, b: Game, keyIds: Boolean = false) = div("gf-duel gf-deal") {
    pick(a, "A", keyIds)
    span("gf-duel__or") { attributes["aria-hidden"] = "true"; +"or" }
    pick(b, "B", keyIds)
}

/** The three answers under a duel. The first is "haven't played" on played pairs, or [firstValue] / [firstLabel]. */
fun FlowContent.duelAnswers(firstValue: String = "UNPLAYED", firstLabel: String = "Haven't played") = div("gf-answers") {
    answer(firstValue, firstLabel, "unplayed")
    answer("BOTH_MEH", "Both meh", "meh")
    answer("BOTH_GREAT", "Both great", "star", great = true)
}

private fun FlowContent.pick(game: Game, key: String, keyId: Boolean) = button(type = ButtonType.submit, classes = "gf-pick") {
    if (keyId) id = "pick-${key.lowercase()}"
    name = "answer"
    value = key
    span("gf-pick__key") { attributes["aria-hidden"] = "true"; +key }
    art(game)
    span("gf-pick__name") { +game.name }
    span("gf-pick__facts") { +game.facts }
    spinner()
}

private fun FlowContent.answer(value: String, label: String, iconName: String, great: Boolean = false) =
    button(type = ButtonType.submit, classes = if (great) "gf-answer gf-answer--great" else "gf-answer") {
        name = "answer"
        this.value = value
        icon(iconName)
        +label
    }
