package gorefest.shell.web.gamenight

import gorefest.core.GameNightId
import gorefest.core.gameday.ShelfGame
import gorefest.core.gamenight.GameNightSummary
import gorefest.shell.App
import gorefest.shell.auth.Login
import gorefest.shell.web.CurrentPlayer
import gorefest.shell.web.authenticated
import gorefest.shell.web.respondFragment
import gorefest.shell.web.suggestions.TableEvent
import gorefest.shell.web.suggestions.suggestions
import gorefest.shell.web.suggestions.tablePage
import gorefest.shell.web.suggestions.tableView
import gorefest.shell.web.todayIn
import io.ktor.http.HttpStatusCode
import io.ktor.server.html.respondHtml
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get

/** A game night (U11): its page, and the same "pick a game for my table" as a game day, with the plan as the shelf. */
context(login: Login)
fun Route.gameNightRoutes(app: App) {
    fun event(night: GameNightSummary) = TableEvent("/gamenights/${night.id}", night.title, night.players, tasteRound = null)

    /** The plan as a shelf: the bringer owns it, and everyone who said yes wants it. */
    fun shelf(night: GameNightSummary) = night.plan.mapNotNull { planned ->
        app.catalog[planned.game]?.let { it to ShelfGame(planned.game, listOf(planned.bringer), planned.wantedBy.toSet()) }
    }

    get("/gamenights/{id}") {
        authenticated {
            val me = contextOf<CurrentPlayer>()
            val night = memberGameNight(app, me) ?: return@authenticated call.respond(HttpStatusCode.NotFound)
            val view = GameNightView(
                night = night,
                names = login.names(night.players + night.plan.map { it.bringer }),
                plan = night.plan.mapNotNull { planned -> app.catalog[planned.game]?.let { it to planned } },
                missing = night.missing.mapNotNull(app.catalog::get),
                today = app.clock.todayIn(night.timeZone),
                me = me,
            )
            call.respondHtml { gameNightPage(view) }
        }
    }

    get("/gamenights/{id}/table") {
        authenticated {
            val me = contextOf<CurrentPlayer>()
            val night = memberGameNight(app, me) ?: return@authenticated call.respond(HttpStatusCode.NotFound)
            val view = tableView(app, login, event(night), shelf(night), call.parameters, me)
            call.respondHtml { tablePage(view) }
        }
    }

    get("/gamenights/{id}/suggestions") {
        authenticated {
            val me = contextOf<CurrentPlayer>()
            val night = memberGameNight(app, me) ?: return@authenticated call.respond(HttpStatusCode.NotFound)
            val view = tableView(app, login, event(night), shelf(night), call.parameters, me)
            respondFragment { suggestions(view) }
        }
    }
}

/** The game night in the `{id}` path parameter, if [me] is on it. Everyone else gets nothing (404). */
private fun RoutingContext.memberGameNight(app: App, me: CurrentPlayer): GameNightSummary? =
    call.parameters["id"]?.let { runCatching { GameNightId.parse(it) }.getOrNull() }
        ?.let(app.gameNights::of)?.takeIf { me.id in it.players }
