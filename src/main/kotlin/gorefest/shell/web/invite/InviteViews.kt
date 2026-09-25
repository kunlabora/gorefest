package gorefest.shell.web.invite

import gorefest.core.PlayerId
import gorefest.core.game.Game
import gorefest.core.gameday.GameDaySummary
import gorefest.shell.web.components.*
import gorefest.shell.web.gameday.invite
import kotlinx.html.*

/** The invite landing page and its Open Graph tags. [path] is `/i/{token}`; [loggedIn] hides the "just your email" hint. */
data class InviteLanding(
    val gameDay: GameDaySummary,
    val names: Map<PlayerId, String>,
    val shelf: List<Game>,
    val preview: InvitePreview,
    val baseUrl: String,
    val path: String,
    val loggedIn: Boolean,
) {
    val link get() = baseUrl + path
}

fun HTML.invitePage(view: InviteLanding) {
    val gameDay = view.gameDay
    val host = view.names[gameDay.host] ?: "Someone"
    page(
        "${gameDay.title}: you're invited",
        head = {
            openGraph("og:title", gameDay.title)
            openGraph("og:description", view.preview.description())
            openGraph("og:image", "${view.link}/og.png")
            openGraph("og:image:width", "1200")
            openGraph("og:image:height", "630")
            openGraph("og:url", view.link)
            openGraph("og:type", "website")
            meta(name = "twitter:card", content = "summary_large_image")
        },
        dock = {
            form(action = "${view.path}/join", method = FormMethod.post) {
                primaryButton("Join ${gameDay.title}")
            }
            if (!view.loggedIn) p("gf-hint") { style = "text-align: center"; +"Just your email. No password, no app to install." }
        },
    ) {
        div {
            style = "display: flex; align-items: center; gap: var(--space-3); padding-top: var(--space-3)"
            avatar(gameDay.host, view.names[gameDay.host], size = "gf-avatar--lg")
            p("gf-muted") {
                style = "margin: 0"
                strong { style = "color: var(--ink)"; +host }
                +" invites you to a game day"
            }
        }
        h1("gf-hero") { +gameDay.title }
        ul("gf-facts") {
            li { icon("calendar"); +gameDay.date.full() }
            if (gameDay.place.isNotBlank()) li { icon("pin"); +gameDay.place }
            li {
                icon("users")
                avatarStack(gameDay.players, view.names)
                +"${gameDay.players.size.of("player")} going"
            }
        }
        if (view.shelf.isNotEmpty()) div("gf-section") {
            h2("gf-h2") { +"On the shelf" }
            div {
                style = "display: flex; align-items: center; gap: var(--space-4)"
                div("gf-fan") {
                    // Each box tilts a little differently, like a hand of cards.
                    view.shelf.take(4).zip(listOf(-6, 3, -2, 5)).forEach { (game, tilt) -> art(game, extraStyle = "--r:${tilt}deg") }
                }
                p {
                    style = "margin: 0; font: 800 30px/30px var(--font-display)"
                    +"${view.shelf.size}"
                    span("gf-muted") { style = "display: block; font: 400 14px/20px var(--font-sans)"; +if (view.shelf.size == 1) "game so far" else "games so far" }
                }
            }
        }
        p("gf-small gf-muted") { +"Answer a few \"which of these two?\" questions and the app picks the games you'll enjoy most on the day." }
    }
}

fun InvitePreview.description() =
    listOf(date, place, "$going going", "${shelf.of("game")} on the shelf").filter { it.isNotBlank() }.joinToString(" · ")

fun HTML.deadInvitePage(reason: String = "This invite link doesn't work anymore") = page("Invite link", appBar = { appBarTitle("Gorefest") }) {
    div("gf-stack") {
        h1("gf-h1") { +reason }
        p("gf-muted") { +"The host may have made a new one. Ask them for the latest link." }
    }
}

fun HTML.endedPage(gameDay: GameDaySummary) = page(gameDay.title, appBar = { appBarTitle("Gorefest") }) {
    div("gf-stack") {
        p("gf-kicker") { +gameDay.date.long() }
        h1("gf-h1") { +"${gameDay.title} has ended" }
        p("gf-muted") { +"This game day is over. See you at the next one." }
        a("/", classes = "gf-link") { +"Your game days" }
    }
}

private fun HEAD.openGraph(property: String, value: String) = meta {
    attributes["property"] = property
    content = value
}
