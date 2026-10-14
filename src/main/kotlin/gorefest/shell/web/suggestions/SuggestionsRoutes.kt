package gorefest.shell.web.suggestions

import gorefest.core.PlayerId
import gorefest.shell.web.components.SOMEONE
import gorefest.core.game.Game
import gorefest.core.gameday.GameDaySummary
import gorefest.core.gameday.ShelfGame
import gorefest.core.matching.TablePlayer
import gorefest.core.taste.PlayerGames
import gorefest.shell.App
import gorefest.shell.auth.Login
import gorefest.shell.web.CurrentPlayer
import gorefest.shell.web.authenticated
import gorefest.shell.web.bgg.collection
import gorefest.shell.web.memberGameDay
import gorefest.shell.web.respondFragment
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.server.html.respondHtml
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlin.uuid.Uuid

context(login: Login)
fun Route.suggestionsRoutes(app: App) {
    suspend fun tableView(gameDay: GameDaySummary, params: Parameters, me: CurrentPlayer): TableView {
        val shelf = app.shelves.of(gameDay.id).mapNotNull { entry -> app.catalog[entry.game]?.let { it to entry } }
        return tableView(app, login, TableEvent.of(gameDay), shelf, params, me)
    }

    get("/gamedays/{id}/table") {
        authenticated {
            val me = contextOf<CurrentPlayer>()
            val gameDay = memberGameDay(app, me) ?: return@authenticated call.respond(HttpStatusCode.NotFound)
            val view = tableView(gameDay, call.parameters, me)
            call.respondHtml { tablePage(view) }
        }
    }

    get("/gamedays/{id}/suggestions") {
        authenticated {
            val me = contextOf<CurrentPlayer>()
            val gameDay = memberGameDay(app, me) ?: return@authenticated call.respond(HttpStatusCode.NotFound)
            val view = tableView(gameDay, call.parameters, me)
            respondFragment { suggestions(view) }
        }
    }
}

/** Suggestions for a table at [event], from [shelf]: the same matching for a game day and a game night. */
suspend fun tableView(app: App, login: Login, event: TableEvent, shelf: List<Pair<Game, ShelfGame>>, params: Parameters, me: CurrentPlayer): TableView {
    // The form always sends TABLE_SET, so "no players" means nobody; without it (first page load) it means everyone.
    val picked = params.getAll("players").orEmpty().takeIf { params.contains(TABLE_SET) || it.isNotEmpty() }
    val chosen = picked?.mapNotNull { runCatching { PlayerId(Uuid.parse(it)) }.getOrNull() }
        ?.filter { it in event.players } ?: event.players
    val budget = TIMES.firstOrNull { it.first == params["time"] }?.second
    val names = login.names(event.players)
    val table = chosen.map { id ->
        TablePlayer(id, names[id] ?: SOMEONE, PlayerGames(app.tastes.of(id), app.collectionSync.collection(id)))
    }
    val suggestions = app.matcher.suggest(table, shelf, budget, app.clock.instant(), speed = app.groupSpeed(chosen))
    val ticked = params.getAll("players")?.toSet() ?: emptySet<String>().takeIf { params.contains(TABLE_SET) }
    return TableView(event, names, ticked, chosen, params["time"] ?: "Any", suggestions, table.size, me)
}
