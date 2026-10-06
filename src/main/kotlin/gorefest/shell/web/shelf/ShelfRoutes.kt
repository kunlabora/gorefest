package gorefest.shell.web.shelf

import gorefest.core.BggId
import gorefest.core.eventsourcing.Decision
import gorefest.core.game.Game
import gorefest.core.gameday.BringGame
import gorefest.core.gameday.BringGameDecider
import gorefest.core.gameday.GameDaySummary
import gorefest.core.gameday.SetPlayWish
import gorefest.core.gameday.SetPlayWishDecider
import gorefest.core.gameday.ShelfGame
import gorefest.shell.App
import gorefest.shell.auth.Login
import gorefest.shell.web.CurrentPlayer
import gorefest.shell.web.message
import gorefest.shell.web.authenticated
import gorefest.shell.web.bgg.collection
import gorefest.shell.web.memberGameDay
import gorefest.shell.web.respondFragment
import io.ktor.http.HttpStatusCode
import io.ktor.server.html.respondHtml
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import io.ktor.server.routing.post

context(login: Login)
fun Route.shelfRoutes(app: App) {
    get("/gamedays/{id}/bring") {
        authenticated {
            val me = contextOf<CurrentPlayer>()
            val gameDay = memberGameDay(app, me) ?: return@authenticated call.respond(HttpStatusCode.NotFound)
            val rows = bringList(app, gameDay, me)
            call.respondHtml { bringPage(gameDay, rows, app.bggAccounts.isLinked(me.id)) }
        }
    }

    /** The list under the search box, fetched again when a game was brought from a search result. */
    get("/gamedays/{id}/bring/list") {
        authenticated {
            val me = contextOf<CurrentPlayer>()
            val gameDay = memberGameDay(app, me) ?: return@authenticated call.respond(HttpStatusCode.NotFound)
            val rows = bringList(app, gameDay, me)
            respondFragment { bringList(gameDay, rows) }
        }
    }

    get("/gamedays/{id}/bring/search") {
        authenticated {
            val me = contextOf<CurrentPlayer>()
            val gameDay = memberGameDay(app, me) ?: return@authenticated call.respond(HttpStatusCode.NotFound)
            val result = app.gameSearch.search(call.parameters["q"].orEmpty())
            val shelf = app.shelves.of(gameDay.id)
            respondFragment { searchResults(gameDay, result.games.map { bringRow(it, shelf, me) }, result.bggUnavailable) }
        }
    }

    post("/gamedays/{id}/shelf/{game}") {
        authenticated {
            val me = contextOf<CurrentPlayer>()
            val (gameDay, game) = shelfTarget(app, me) ?: return@authenticated call.respond(HttpStatusCode.NotFound)
            val form = call.receiveParameters()
            val bringing = form["bringing"] == "true"
            val decision = app.commands.handle(BringGameDecider, BringGame(gameDay.id, me.id, game.id, bringing))
            respondBringDecision(app, gameDay, game, me, decision, rowPrefix = form["row"] ?: "bring")
        }
    }

    post("/gamedays/{id}/shelf/{game}/wish") {
        authenticated {
            val me = contextOf<CurrentPlayer>()
            val (gameDay, game) = shelfTarget(app, me) ?: return@authenticated call.respond(HttpStatusCode.NotFound)
            val form = call.receiveParameters()
            val wants = form["wants"] == "true"
            val decision = app.commands.handle(SetPlayWishDecider, SetPlayWish(gameDay.id, me.id, game.id, wants))
            respondBringDecision(app, gameDay, game, me, decision, rowPrefix = form["row"] ?: "bring")
        }
    }
}

/** My collection (if linked), plus anything I brought that isn't in it (found by search). */
private suspend fun bringList(app: App, gameDay: GameDaySummary, me: CurrentPlayer): List<BringRow> {
    val owned = if (app.bggAccounts.isLinked(me.id)) app.collectionSync.collection(me.id) else emptySet()
    val shelf = app.shelves.of(gameDay.id)
    val ids = owned + shelf.filter { me.id in it.owners }.map { it.game }
    return ids.mapNotNull(app.catalog::get).sortedBy { it.name.lowercase() }.map { bringRow(it, shelf, me) }
}

private fun RoutingContext.shelfTarget(app: App, me: CurrentPlayer): Pair<GameDaySummary, Game>? {
    val gameDay = memberGameDay(app, me) ?: return null
    val game = call.parameters["game"]?.toIntOrNull()?.let { app.catalog[BggId(it)] } ?: return null
    return gameDay to game
}

private suspend fun RoutingContext.respondBringDecision(
    app: App, gameDay: GameDaySummary, game: Game, me: CurrentPlayer, decision: Decision<*>, rowPrefix: String,
) {
    val row = bringRow(game, app.shelves.of(gameDay.id), me)
    val prefix = if (rowPrefix == "search") "search" else "bring"
    // A search row isn't in the list below it, so tell the list to look again.
    if (prefix == "search") call.response.header("HX-Trigger", BRING_LIST_CHANGED)
    respondFragment(if (decision is Decision.Rejected) HttpStatusCode.Conflict else HttpStatusCode.OK) {
        bringItem(gameDay, row, readOnly = gameDay.closed, error = (decision as? Decision.Rejected)?.reason?.message(), prefix = prefix)
    }
}

private fun bringRow(game: Game, shelf: List<ShelfGame>, me: CurrentPlayer): BringRow {
    val entry = shelf.firstOrNull { it.game == game.id }
    return BringRow(game, bringing = entry != null && me.id in entry.owners, wants = entry != null && me.id in entry.eager)
}
