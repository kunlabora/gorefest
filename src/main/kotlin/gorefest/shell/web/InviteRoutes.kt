package gorefest.shell.web

import gorefest.core.eventsourcing.Decision
import gorefest.core.gameday.GameDaySummary
import gorefest.core.gameday.JoinGameDay
import gorefest.core.gameday.JoinGameDayDecider
import gorefest.shell.App
import gorefest.shell.AppConfig
import gorefest.shell.InviteLinks
import gorefest.shell.auth.Login
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.html.respondHtml
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.html.FormMethod
import kotlinx.html.HTML
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h1
import kotlinx.html.li
import kotlinx.html.meta
import kotlinx.html.p
import kotlinx.html.span
import kotlinx.html.strong
import kotlinx.html.style
import kotlinx.html.ul
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What an invite token resolves to. */
private sealed interface Invite {
    data class Valid(val gameDay: GameDaySummary) : Invite
    data class Ended(val gameDay: GameDaySummary) : Invite
    data object Dead : Invite
}

private val fullDate = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ENGLISH)

context(login: Login, config: AppConfig)
fun Route.inviteRoutes(app: App, invites: InviteLinks) {
    fun resolve(token: String?): Invite {
        val (id, version) = token?.let(invites::parse) ?: return Invite.Dead
        val gameDay = app.gameDays.state[id] ?: return Invite.Dead
        return when {
            gameDay.ended -> Invite.Ended(gameDay)
            gameDay.inviteVersion != version -> Invite.Dead
            else -> Invite.Valid(gameDay)
        }
    }

    get("/i/{token}") {
        val token = call.parameters["token"]!!
        val gameDay = when (val invite = resolve(token)) {
            is Invite.Valid -> invite.gameDay
            is Invite.Ended -> return@get call.respondHtml(HttpStatusCode.Gone) { endedPage(invite.gameDay) }
            Invite.Dead -> return@get call.respondHtml(HttpStatusCode.NotFound) { deadInvitePage() }
        }
        val me = sessionPlayer()
        if (me != null && me.id in gameDay.players) return@get redirect("/gamedays/${gameDay.id}")

        val names = login.names(gameDay.players)
        val host = names[gameDay.host] ?: "Someone"
        val preview = gameDay.preview(host, shelf = 0)
        call.respondHtml {
            page(
                "${gameDay.title}: you're invited",
                head = {
                    meta { attributes["property"] = "og:title"; content = gameDay.title }
                    meta { attributes["property"] = "og:description"; content = preview.description() }
                    meta { attributes["property"] = "og:image"; content = "${config.baseUrl}/i/$token/og.png" }
                    meta { attributes["property"] = "og:image:width"; content = "1200" }
                    meta { attributes["property"] = "og:image:height"; content = "630" }
                    meta { attributes["property"] = "og:url"; content = "${config.baseUrl}/i/$token" }
                    meta { attributes["property"] = "og:type"; content = "website" }
                    meta(name = "twitter:card", content = "summary_large_image")
                },
                dock = {
                    form(action = "/i/$token/join", method = FormMethod.post) {
                        button(classes = "gf-btn gf-btn--primary gf-btn--lg gf-btn--block") {
                            span("gf-spin") { attributes["aria-hidden"] = "true" }
                            +"Join ${gameDay.title}"
                        }
                    }
                    if (me == null) p("gf-hint") { style = "text-align: center"; +"Just your email. No password, no app to install." }
                },
            ) {
                div {
                    style = "display: flex; align-items: center; gap: var(--space-3); padding-top: var(--space-3)"
                    avatar(gameDay.host, names[gameDay.host], size = "gf-avatar--lg")
                    p("gf-muted") {
                        style = "margin: 0"
                        strong { style = "color: var(--ink)"; +host }
                        +" invites you to a game day"
                    }
                }
                h1("gf-hero") { +gameDay.title }
                ul("gf-facts") {
                    li { icon("calendar"); +gameDay.date.format(fullDate) }
                    if (gameDay.place.isNotBlank()) li { icon("pin"); +gameDay.place }
                    li {
                        icon("users")
                        span("gf-avatars") { gameDay.players.take(5).forEach { avatar(it, names[it], size = "gf-avatar--sm") } }
                        +"${gameDay.players.size} ${if (gameDay.players.size == 1) "player" else "players"} going"
                    }
                }
                p("gf-small gf-muted") {
                    +"Answer a few \"which of these two?\" questions and the app picks the games you'll enjoy most on the day."
                }
            }
        }
    }

    post("/i/{token}/join") {
        val token = call.parameters["token"]!!
        val gameDay = when (val invite = resolve(token)) {
            is Invite.Valid -> invite.gameDay
            is Invite.Ended -> return@post call.respondHtml(HttpStatusCode.Gone) { endedPage(invite.gameDay) }
            Invite.Dead -> return@post call.respondHtml(HttpStatusCode.NotFound) { deadInvitePage() }
        }
        val me = sessionPlayer() ?: return@post redirect("/login?returnTo=${"/i/$token".urlEncoded()}")
        if (me.name == null) return@post redirect("/welcome?returnTo=${"/i/$token".urlEncoded()}")
        when (val decision = app.commands.handle(JoinGameDayDecider, JoinGameDay(gameDay.id, me.id))) {
            is Decision.Accepted -> redirect("/gamedays/${gameDay.id}")
            is Decision.Rejected -> call.respondHtml(HttpStatusCode.Conflict) { deadInvitePage(decision.reason) }
        }
    }

    get("/i/{token}/og.png") {
        val gameDay = (resolve(call.parameters["token"]) as? Invite.Valid)?.gameDay
            ?: return@get call.respond(HttpStatusCode.NotFound)
        val host = login.names(listOf(gameDay.host))[gameDay.host] ?: "Someone"
        call.response.header(HttpHeaders.CacheControl, "public, max-age=300")
        call.respondBytes(OgImage.png(gameDay.preview(host, shelf = 0)), ContentType.Image.PNG)
    }
}

private fun GameDaySummary.preview(host: String, shelf: Int) =
    InvitePreview(host, title, date.format(fullDate), place, going = players.size, shelf = shelf)

private fun InvitePreview.description() =
    listOf(date, place, "$going going", "$shelf ${if (shelf == 1) "game" else "games"} on the shelf").filter { it.isNotBlank() }.joinToString(" · ")

private fun HTML.deadInvitePage(reason: String = "This invite link doesn't work anymore") =
    page("Invite link", appBar = { appBarTitle("Gorefest") }) {
        div("gf-stack") {
            h1("gf-h1") { +reason }
            p("gf-muted") { +"The host may have made a new one. Ask them for the latest link." }
        }
    }

private fun HTML.endedPage(gameDay: GameDaySummary) =
    page(gameDay.title, appBar = { appBarTitle("Gorefest") }) {
        div("gf-stack") {
            p("gf-kicker") { +gameDay.date.long() }
            h1("gf-h1") { +"${gameDay.title} has ended" }
            p("gf-muted") { +"This game day is over. See you at the next one." }
            a("/", classes = "gf-link") { +"Your game days" }
        }
    }

