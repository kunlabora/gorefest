package gorefest.shell.web

import gorefest.core.eventsourcing.Decision
import gorefest.core.gameday.GameDaySummary
import gorefest.core.gameday.JoinGameDay
import gorefest.core.gameday.JoinGameDayDecider
import gorefest.shell.App
import gorefest.shell.AppConfig
import gorefest.shell.InviteLinks
import gorefest.shell.auth.Login
import gorefest.shell.web.components.full
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.html.respondHtml
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

/** What an invite token resolves to. */
private sealed interface Invite {
    data class Valid(val gameDay: GameDaySummary) : Invite
    data class Ended(val gameDay: GameDaySummary) : Invite
    data object Dead : Invite
}

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
        val shelf = app.shelves.state[gameDay.id].orEmpty().mapNotNull { app.catalog[it.game] }
        val landing = InviteLanding(
            gameDay = gameDay,
            names = names,
            shelf = shelf,
            preview = gameDay.preview(names[gameDay.host] ?: "Someone", shelf = shelf.size),
            baseUrl = config.baseUrl,
            path = "/i/$token",
            loggedIn = me != null,
        )
        call.respondHtml { invitePage(landing) }
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
        val shelf = app.shelves.state[gameDay.id].orEmpty().size
        call.respondBytes(OgImage.png(gameDay.preview(host, shelf = shelf)), ContentType.Image.PNG)
    }
}

private fun GameDaySummary.preview(host: String, shelf: Int) =
    InvitePreview(host, title, date.full(), place, going = players.size, shelf = shelf)
