package gorefest.shell.web.suggestions

import gorefest.core.PlayerId
import gorefest.core.gameday.GameDaySummary
import gorefest.core.matching.Suggestions
import gorefest.core.matching.TablePlayer
import gorefest.core.taste.PlayerGames
import gorefest.core.taste.TasteHistory
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
fun Route.tableRoutes(app: App) {
    suspend fun suggestionsFor(gameDay: GameDaySummary, params: Parameters, me: CurrentPlayer): Pair<Suggestions, Int> {
        val chosen = params.getAll("players")?.mapNotNull { runCatching { PlayerId(Uuid.parse(it)) }.getOrNull() }
            ?.filter { it in gameDay.players } ?: gameDay.players
        val budget = TIMES.firstOrNull { it.first == params["time"] }?.second
        val names = login.names(chosen)
        val table = chosen.map { id ->
            TablePlayer(id, names[id] ?: "Someone", PlayerGames(app.tastes.state[id] ?: TasteHistory(), app.collectionSync.collection(id)))
        }
        val shelf = app.shelves.state[gameDay.id].orEmpty().mapNotNull { entry -> app.catalog[entry.game]?.let { it to entry } }
        return app.matcher.suggest(table, shelf, budget, app.clock.instant(), speed = app.groupSpeed(chosen)) to table.size
    }

    get("/gamedays/{id}/table") {
        authenticated {
            val me = contextOf<CurrentPlayer>()
            val gameDay = memberGameDay(app, me) ?: return@authenticated call.respond(HttpStatusCode.NotFound)
            val names = login.names(gameDay.players)
            val (suggestions, size) = suggestionsFor(gameDay, call.parameters, me)
            val view = TableView(gameDay, names, call.parameters.getAll("players")?.toSet(), call.parameters["time"] ?: "Any", suggestions, size)
            call.respondHtml { tablePage(view) }
        }
    }

    get("/gamedays/{id}/suggestions") {
        authenticated {
            val me = contextOf<CurrentPlayer>()
            val gameDay = memberGameDay(app, me) ?: return@authenticated call.respond(HttpStatusCode.NotFound)
            val (suggestions, size) = suggestionsFor(gameDay, call.parameters, me)
            respondFragment { suggestions(suggestions, size) }
        }
    }
}
