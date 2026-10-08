package gorefest.shell.web.invite

import gorefest.core.eventsourcing.Decision
import gorefest.core.gameday.GameDaySummary
import gorefest.core.gameday.JoinGameDay
import gorefest.core.gameday.JoinGameDayDecider
import gorefest.shell.App
import gorefest.shell.AppConfig
import gorefest.shell.auth.Login
import gorefest.shell.auth.Player
import gorefest.shell.gameday.InviteLinks
import gorefest.shell.web.CurrentPlayer
import gorefest.shell.web.message
import gorefest.shell.web.components.full
import gorefest.shell.web.gameday.invite
import gorefest.shell.web.redirect
import gorefest.shell.web.sessionPlayer
import gorefest.shell.web.urlEncoded
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

/** What an invite token resolves to. */
private sealed interface Invite {
    data class Valid(val gameDay: GameDaySummary) : Invite
    data class Ended(val gameDay: GameDaySummary) : Invite
    data class Cancelled(val gameDay: GameDaySummary) : Invite
    data object Dead : Invite
}

context(login: Login, config: AppConfig)
fun Route.inviteRoutes(app: App, invites: InviteLinks) {
    fun resolve(token: String?): Invite {
        val (id, version) = token?.let(invites::parse) ?: return Invite.Dead
        val gameDay = app.gameDaysById[id] ?: return Invite.Dead
        return when {
            gameDay.cancelled -> Invite.Cancelled(gameDay)
            gameDay.ended -> Invite.Ended(gameDay)
            gameDay.inviteVersion != version -> Invite.Dead
            else -> Invite.Valid(gameDay)
        }
    }

    suspend fun RoutingContext.join(gameDay: GameDaySummary, me: Player) {
        when (val decision = app.commands.handle(JoinGameDayDecider, JoinGameDay(gameDay.id, me.id))) {
            is Decision.Accepted -> redirect("/gamedays/${gameDay.id}")
            is Decision.Rejected -> call.respondHtml(HttpStatusCode.Conflict) { deadInvitePage(decision.reason.message()) }
        }
    }

    get("/i/{token}") {
        val token = call.parameters["token"]!!
        val gameDay = when (val invite = resolve(token)) {
            is Invite.Valid -> invite.gameDay
            is Invite.Ended -> return@get call.respondHtml(HttpStatusCode.Gone) { endedPage(invite.gameDay) }
            // Members land on the hub, which says Cancelled; everyone else is told it's off.
            is Invite.Cancelled ->
                return@get if (sessionPlayer()?.id in invite.gameDay.players) redirect("/gamedays/${invite.gameDay.id}")
                else call.respondHtml(HttpStatusCode.Gone) { cancelledPage(invite.gameDay) }
            Invite.Dead -> return@get call.respondHtml(HttpStatusCode.NotFound) { deadInvitePage() }
        }
        val me = sessionPlayer()
        if (me != null && me.id in gameDay.players) return@get redirect("/gamedays/${gameDay.id}")
        // Back from signing up or logging in for this invite: the player already said yes, so join without asking again.
        if (me?.name != null && invites.isJoinMarker(call.parameters["join"], me.id, "/i/$token")) return@get join(gameDay, me)

        val shelf = app.shelves.of(gameDay.id).mapNotNull { entry -> app.catalog[entry.game]?.let { it to entry } }
        val names = login.names(gameDay.players + shelf.flatMap { it.second.owners })
        val landing = InviteLanding(
            gameDay = gameDay,
            names = names,
            shelf = shelf,
            preview = gameDay.preview(names[gameDay.host] ?: "Someone", shelf = shelf.size),
            baseUrl = config.baseUrl,
            path = "/i/$token",
            loggedIn = me != null,
            me = me?.name?.let { CurrentPlayer(me.id, it) },
        )
        call.respondHtml { invitePage(landing) }
    }

    post("/i/{token}/join") {
        val token = call.parameters["token"]!!
        val gameDay = when (val invite = resolve(token)) {
            is Invite.Valid -> invite.gameDay
            is Invite.Ended -> return@post call.respondHtml(HttpStatusCode.Gone) { endedPage(invite.gameDay) }
            is Invite.Cancelled -> return@post call.respondHtml(HttpStatusCode.Gone) { cancelledPage(invite.gameDay) }
            Invite.Dead -> return@post call.respondHtml(HttpStatusCode.NotFound) { deadInvitePage() }
        }
        // The login (and the welcome step) carry this path along, so the invite survives them and joins on the way back.
        val comeBack = "/i/$token?join=1".urlEncoded()
        val me = sessionPlayer() ?: return@post redirect("/login?returnTo=$comeBack")
        if (me.name == null) return@post redirect("/welcome?returnTo=$comeBack")
        join(gameDay, me)
    }

    get("/i/{token}/og.png") {
        val gameDay = (resolve(call.parameters["token"]) as? Invite.Valid)?.gameDay
            ?: return@get call.respond(HttpStatusCode.NotFound)
        val host = login.names(listOf(gameDay.host))[gameDay.host] ?: "Someone"
        call.response.header(HttpHeaders.CacheControl, "public, max-age=300")
        val shelf = app.shelves.of(gameDay.id).size
        call.respondBytes(OgImage.png(gameDay.preview(host, shelf = shelf)), ContentType.Image.PNG)
    }
}

private fun GameDaySummary.preview(host: String, shelf: Int) =
    InvitePreview(host, title, date.full(), place, going = players.size, shelf = shelf)
