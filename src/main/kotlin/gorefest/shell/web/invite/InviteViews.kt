package gorefest.shell.web.invite

import gorefest.core.PlayerId
import gorefest.core.game.Game
import gorefest.core.gameday.GameDaySummary
import gorefest.core.gameday.ShelfGame
import gorefest.shell.web.CurrentPlayer
import gorefest.shell.web.components.*
import gorefest.shell.web.gameday.invite
import kotlinx.html.*

/**
 * The invite landing page and its Open Graph tags. [path] is `/i/{token}`; [me] is set when a named player is logged in
 * (they get the site bar and a plain join button), otherwise the visitor starts signing in right on the page.
 */
data class InviteLanding(
    val gameDay: GameDaySummary,
    val names: Map<PlayerId, String>,
    val shelf: List<Pair<Game, ShelfGame>>,
    val preview: InvitePreview,
    val baseUrl: String,
    val path: String,
    val loggedIn: Boolean,
    val me: CurrentPlayer? = null,
) {
    val link get() = baseUrl + path
}

/** The invite's own page frame: the site bar for a logged-in player, the login's wordmark bar for everyone else. */
private fun HTML.invitePageFrame(title: String, me: CurrentPlayer?, head: HEAD.() -> Unit = {}, content: MAIN.() -> Unit) =
    if (me != null) page(title, head = head, mainClasses = "gf-landing", site = SiteBar(me, null), content = content)
    else page(title, appBar = { wordmark() }, head = head, mainClasses = "gf-landing", wide = true, content = content)

fun HTML.invitePage(view: InviteLanding) {
    val gameDay = view.gameDay
    val shelf = view.shelf
    invitePageFrame(
        "${gameDay.title}: you're invited",
        view.me,
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
    ) {
        wrap {
            split("gf-inv-hero") {
                div("gf-split__copy") {
                    inviterLine(gameDay.host, view.names[gameDay.host], "invites you to a game day")
                    h1("gf-h1 gf-h1--landing") { +gameDay.title }
                    ul("gf-facts") {
                        li { icon("calendar"); +gameDay.date.full() }
                        if (gameDay.place.isNotBlank()) li { icon("pin"); +gameDay.place }
                        li {
                            icon("users")
                            avatarStack(gameDay.players, view.names)
                            +"${gameDay.players.size.of("player")} going"
                        }
                    }
                    if (shelf.isNotEmpty()) div("gf-phone-only gf-inv-fan") {
                        attributes["aria-hidden"] = "true"
                        div("gf-fan") {
                            // Each box tilts a little differently, like a hand of cards.
                            shelf.take(3).zip(listOf(-6, 3, -2)).forEach { (entry, tilt) -> art(entry.first, extraStyle = "--r:${tilt}deg") }
                        }
                        p("gf-small gf-muted") { +"${shelf.size.of("game")} on the shelf so far" }
                    }
                    if (view.loggedIn) joinButton(view.path, gameDay.title) else signInForm(view.path, gameDay.title)
                }
                if (shelf.isNotEmpty()) hand(shelf, view.names)
            }
        }
        band(sunk = true) { whatHappensNext(view.loggedIn) }
    }
}

/** Logged out: the sign-in starts here, with the invite riding along as where to come back to (the same `returnTo` the join button used to produce). */
private fun FlowContent.signInForm(path: String, title: String) =
    form(action = "/login", method = FormMethod.post, classes = "gf-signin gf-join") {
        id = "invite-signin"
        hx("post", "/login")
        hx("swap", "outerHTML")
        hiddenInput("returnTo", "$path?join=1")
        hiddenInput("submitLabel", "Join $title")
        label("gf-label") { htmlFor = "email"; +"Your email" }
        div("gf-signin__row") {
            input(InputType.email, name = "email", classes = "gf-input") {
                id = "email"
                required = true
                placeholder = "you@example.com"
                attributes["inputmode"] = "email"
                attributes["autocomplete"] = "email"
            }
            button(type = ButtonType.submit, classes = buttonClasses(ButtonStyle.PRIMARY, ButtonSize.LARGE)) { spinner(); +"Join $title" }
        }
        p("gf-hint") { +"We'll email a sign-in link and a 6-digit code. No password." }
    }

/** Logged in: one button that joins. */
private fun FlowContent.joinButton(path: String, title: String) =
    form(action = "$path/join", method = FormMethod.post, classes = "gf-join") {
        button(type = ButtonType.submit, classes = buttonClasses(ButtonStyle.PRIMARY, ButtonSize.LARGE)) { +"Join $title" }
    }

/** Four box covers as a tilted hand of cards, each with who brings it. Desktop only; phones get the small fan. */
private fun FlowContent.hand(shelf: List<Pair<Game, ShelfGame>>, names: Map<PlayerId, String>) = div("gf-hand") {
    attributes["aria-hidden"] = "true"
    shelf.take(4).forEach { (game, entry) ->
        div("gf-hand__card") {
            art(game)
            h3("gf-hand__name") { +game.name }
            span("gf-hand__chip") { +broughtBy(entry.owners.mapNotNull { names[it] }) }
        }
    }
    if (shelf.size > 4) p("gf-small gf-muted gf-hand__more") { +"+ ${shelf.size - 4} more on the shelf" }
}

private fun broughtBy(owners: List<String>) = when (owners.size) {
    0 -> "On the shelf"
    1 -> "Brought by ${owners[0]}"
    2 -> "Brought by ${owners[0]} and ${owners[1]}"
    else -> "Brought by ${owners[0]} +${owners.size - 1}"
}

private fun FlowContent.whatHappensNext(loggedIn: Boolean) = div("gf-stack gf-inv-next") {
    h2("gf-h2") { +"What happens next" }
    ol("gf-inv-steps") {
        listOf(
            (if (loggedIn) "Join the guest list" else "Sign in with your email") to
                (if (loggedIn) "One tap and you're on the guest list." else "One tap, no password. You're on the guest list straight away."),
            "Pick between two games" to "A two-minute round of this-or-that teaches Gorefest your taste.",
            "Bring games, get suggestions" to "Tick what you're bringing. On the day, Gorefest picks what suits the table.",
        ).forEachIndexed { i, (title, text) ->
            li("gf-inv-step") {
                span("gf-inv-step__num") { +"${i + 1}" }
                h3("gf-inv-step__title") { +title }
                p("gf-muted gf-inv-step__text") { +text }
            }
        }
    }
}

fun InvitePreview.description() =
    listOf(date, place, "$going going", "${shelf.of("game")} on the shelf").filter { it.isNotBlank() }.joinToString(" · ")

/** Dead, cancelled and ended invites share one hero: what happened, what to do, the way back. */
private fun HTML.statusPage(title: String, kicker: String?, heading: String, text: String, action: (FlowContent.() -> Unit)? = null) =
    page(title, appBar = { wordmark() }, mainClasses = "gf-landing", wide = true) {
        div("gf-hero-band") {
            div("gf-hero-band__copy") {
                if (kicker != null) p("gf-kicker") { +kicker }
                h1("gf-h1 gf-h1--landing") { +heading }
                p("gf-lede gf-muted") { +text }
                action?.invoke(this)
            }
        }
    }

fun HTML.deadInvitePage(reason: String = "This invite link doesn't work anymore") =
    statusPage("Invite link", null, reason, "The host may have made a new one. Ask them for the latest link.")

fun HTML.cancelledPage(gameDay: GameDaySummary) =
    statusPage(gameDay.title, gameDay.date.long(), "${gameDay.title} was cancelled", "The host called this game day off. Ask them what's next.") {
        linkButton("/", "Your game days", ButtonStyle.SECONDARY)
    }

fun HTML.endedPage(gameDay: GameDaySummary) =
    statusPage(gameDay.title, gameDay.date.long(), "${gameDay.title} has ended", "This game day is over. See you at the next one.") {
        linkButton("/", "Your game days", ButtonStyle.SECONDARY)
    }
