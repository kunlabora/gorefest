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
import gorefest.shell.web.request.bringRequests
import gorefest.shell.web.request.ownerRequests
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
            val summary = summaryFor(app, gameDay, me, login)
            val requests = ownerRequests(app, gameDay, me, login)
            call.respondHtml { bringPage(me, gameDay, rows, wishList(app, gameDay, me), app.bggAccounts.isLinked(me.id), summary, requests) }
        }
    }

    /** The list under the search box, fetched again when a game was brought from a search result. */
    get("/gamedays/{id}/bring/list") {
        authenticated {
            val me = contextOf<CurrentPlayer>()
            val gameDay = memberGameDay(app, me) ?: return@authenticated call.respond(HttpStatusCode.NotFound)
            val rows = bringList(app, gameDay, me)
            val linked = app.bggAccounts.isLinked(me.id)
            respondFragment { bringList(gameDay, rows, linked) }
        }
    }

    /** The shelf count, who brought what and the tray, fetched again when the shelf changed. */
    get("/gamedays/{id}/bring/summary") {
        authenticated {
            val me = contextOf<CurrentPlayer>()
            val gameDay = memberGameDay(app, me) ?: return@authenticated call.respond(HttpStatusCode.NotFound)
            val summary = summaryFor(app, gameDay, me, login)
            respondFragment { bringSummary(gameDay, summary) }
        }
    }

    /** The shelf with my hearts, fetched again when the shelf changed. */
    get("/gamedays/{id}/bring/wishes") {
        authenticated {
            val me = contextOf<CurrentPlayer>()
            val gameDay = memberGameDay(app, me) ?: return@authenticated call.respond(HttpStatusCode.NotFound)
            val rows = wishList(app, gameDay, me)
            respondFragment { wishList(gameDay, rows) }
        }
    }

    get("/gamedays/{id}/bring/search") {
        authenticated {
            val me = contextOf<CurrentPlayer>()
            val gameDay = memberGameDay(app, me) ?: return@authenticated call.respond(HttpStatusCode.NotFound)
            val query = call.parameters["q"].orEmpty()
            val result = app.gameSearch.search(query)
            val shelf = app.shelves.of(gameDay.id)
            respondFragment { searchResults(gameDay, result.games.map { bringRow(it, shelf, me) }, result.bggUnavailable, result.nothingFor(query)) }
        }
    }

    post("/gamedays/{id}/shelf/{game}") {
        authenticated {
            val me = contextOf<CurrentPlayer>()
            val (gameDay, game) = shelfTarget(app, me) ?: return@authenticated call.respond(HttpStatusCode.NotFound)
            val form = call.receiveParameters()
            val bringing = form["bringing"] == "true"
            val decision = app.commands.handle(BringGameDecider, BringGame(gameDay.id, me.id, game.id, bringing))
            respondBringDecision(app, gameDay, game, me, decision, rowPrefix = form["row"] ?: "bring", login)
        }
    }

    post("/gamedays/{id}/shelf/{game}/wish") {
        authenticated {
            val me = contextOf<CurrentPlayer>()
            val (gameDay, game) = shelfTarget(app, me) ?: return@authenticated call.respond(HttpStatusCode.NotFound)
            val form = call.receiveParameters()
            val wants = form["wants"] == "true"
            val decision = app.commands.handle(SetPlayWishDecider, SetPlayWish(gameDay.id, me.id, game.id, wants))
            val row = wishRow(game, app.shelves.of(gameDay.id), me)
            // The wish list is the one that changed, but other rows' counts may be stale: ask for it again.
            call.response.header("HX-Trigger", SHELF_CHANGED)
            respondFragment(if (decision is Decision.Rejected) HttpStatusCode.Conflict else HttpStatusCode.OK) {
                wishItem(gameDay, row, error = (decision as? Decision.Rejected)?.reason?.message())
            }
        }
    }
}

/** My collection (if linked), plus anything I brought that isn't in it (found by search). */
private suspend fun bringList(app: App, gameDay: GameDaySummary, me: CurrentPlayer): List<BringRow> {
    val owned = if (app.bggAccounts.isLinked(me.id)) app.collectionSync.collection(me.id) else emptySet()
    val shelf = app.shelves.of(gameDay.id)
    val ids = owned + shelf.filter { me.id in it.owners }.map { it.game }
    return ids.mapNotNull(app.catalog::get).sortedBy { it.name.lowercase() }.map { bringRow(it, shelf, me).copy(owned = it.id in owned) }
}

private suspend fun summaryFor(app: App, gameDay: GameDaySummary, me: CurrentPlayer, login: Login): ShelfSummary {
    val shelf = app.shelves.of(gameDay.id)
    val counts = shelf.flatMap { it.owners }.groupingBy { it }.eachCount()
    val names = login.names(counts.keys)
    val byPlayer = counts.map { (id, count) -> ShelfOwner(id, names[id] ?: "Someone", count) }
        .sortedWith(compareByDescending<ShelfOwner> { it.count }.thenBy { it.name })
    val mine = shelf.filter { me.id in it.owners }.mapNotNull { app.catalog[it.game] }
    return ShelfSummary(shelf.size, byPlayer, mine)
}

/** Everything on the shelf, in the order it was brought, with my hearts. */
private fun wishList(app: App, gameDay: GameDaySummary, me: CurrentPlayer): List<WishRow> {
    val shelf = app.shelves.of(gameDay.id)
    return shelf.mapNotNull { entry -> app.catalog[entry.game]?.let { wishRow(it, shelf, me) } }
}

private fun wishRow(game: Game, shelf: List<ShelfGame>, me: CurrentPlayer): WishRow {
    val eager = shelf.firstOrNull { it.game == game.id }?.eager.orEmpty()
    return WishRow(game, wants = me.id in eager, wishers = eager.size)
}

private fun RoutingContext.shelfTarget(app: App, me: CurrentPlayer): Pair<GameDaySummary, Game>? {
    val gameDay = memberGameDay(app, me) ?: return null
    val game = call.parameters["game"]?.toIntOrNull()?.let { app.catalog[BggId(it)] } ?: return null
    return gameDay to game
}

private suspend fun RoutingContext.respondBringDecision(
    app: App, gameDay: GameDaySummary, game: Game, me: CurrentPlayer, decision: Decision<*>, rowPrefix: String, login: Login,
) {
    if (rowPrefix == "request") {
        // "Bring it" on a request: the same decision as ticking the game, answered with the requests section; the list below looks again.
        call.response.header("HX-Trigger", BRING_LIST_CHANGED)
        val requests = ownerRequests(app, gameDay, me, login)
        return respondFragment(if (decision is Decision.Rejected) HttpStatusCode.Conflict else HttpStatusCode.OK) { bringRequests(gameDay, requests) }
    }
    val row = bringRow(game, app.shelves.of(gameDay.id), me)
    val prefix = if (rowPrefix == "search") "search" else "bring"
    // A search row isn't in the list below it, so tell the list to look again.
    call.response.header("HX-Trigger", if (prefix == "search") BRING_LIST_CHANGED else SHELF_CHANGED)
    respondFragment(if (decision is Decision.Rejected) HttpStatusCode.Conflict else HttpStatusCode.OK) {
        bringItem(gameDay, row, readOnly = gameDay.closed, error = (decision as? Decision.Rejected)?.reason?.message(), prefix = prefix)
    }
}

private fun bringRow(game: Game, shelf: List<ShelfGame>, me: CurrentPlayer): BringRow {
    val entry = shelf.firstOrNull { it.game == game.id }
    return BringRow(game, bringing = entry != null && me.id in entry.owners)
}
