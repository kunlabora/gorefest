package gorefest.shell.web.checkpoint

import gorefest.core.Basis
import gorefest.core.BggId
import gorefest.core.ComparisonRecorded
import gorefest.core.ComparisonResult
import gorefest.core.GamePlayStatusDeclared
import gorefest.core.PlayLogged
import gorefest.core.PlayerId
import gorefest.core.eventsourcing.Decision
import gorefest.core.game.Game
import gorefest.core.gameday.GameDaySummary
import gorefest.core.gameday.LogPlayDecider
import gorefest.core.taste.DeclarePlayStatusDecider
import gorefest.core.taste.PlayerGames
import gorefest.core.taste.RecordComparisonDecider
import gorefest.core.taste.TasteHistory
import gorefest.shell.App
import gorefest.shell.auth.Login
import gorefest.shell.web.CurrentPlayer
import gorefest.shell.web.authenticated
import gorefest.shell.web.bgg.collection
import gorefest.shell.web.components.of
import gorefest.shell.web.memberGameDay
import gorefest.shell.web.redirect
import gorefest.shell.web.respondFragment
import gorefest.shell.web.safeReturnTo
import io.ktor.http.HttpStatusCode
import io.ktor.server.html.respondHtml
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import java.time.ZoneId
import kotlin.uuid.Uuid

/** Morning, lunch or evening, by the clock where the game day is. */
fun checkpointLabel(gameDay: GameDaySummary, clock: java.time.Clock): String {
    val hour = clock.instant().atZone(runCatching { ZoneId.of(gameDay.timeZone) }.getOrDefault(ZoneId.of("UTC"))).hour
    return when {
        hour < 11 -> "Morning checkpoint"
        hour < 16 -> "Lunch checkpoint"
        else -> "Evening checkpoint"
    }
}

context(login: Login)
fun Route.checkpointRoutes(app: App) {
    get("/gamedays/{id}/log") {
        authenticated {
            val me = contextOf<CurrentPlayer>()
            val gameDay = memberGameDay(app, me) ?: return@authenticated call.respond(HttpStatusCode.NotFound)
            val view = LogFormView(gameDay, logChoices(app, gameDay), login.names(gameDay.players))
            call.respondHtml { logPage(view, checkpointLabel(gameDay, app.clock), me) }
        }
    }

    post("/gamedays/{id}/log") {
        authenticated {
            val me = contextOf<CurrentPlayer>()
            val gameDay = memberGameDay(app, me) ?: return@authenticated call.respond(HttpStatusCode.NotFound)
            val form = call.receiveParameters()
            val game = form["game"]?.toIntOrNull()?.let(::BggId)?.let(app.catalog::get)
            val players = form.getAll("players").orEmpty().mapNotNull { runCatching { PlayerId(Uuid.parse(it)) }.getOrNull() }
            val minutes = DURATIONS.firstOrNull { it.first == form["duration"] }?.second ?: 0
            val view = LogFormView(gameDay, logChoices(app, gameDay), login.names(gameDay.players))
            if (game == null) return@authenticated respondFragment { logForm(view, me, form, "Pick the game you played") }
            val log = PlayLogged(gameDay.id, game.id, players, minutes, me.id, app.clock.instant())
            when (val decision = app.commands.handle(LogPlayDecider, log)) {
                is Decision.Accepted -> redirect("/gamedays/${gameDay.id}/log/compare?game=${game.id}&minutes=$minutes&${players.joinToString("&") { "players=$it" }}")
                is Decision.Rejected -> respondFragment { logForm(view, me, form, decision.reason) }
            }
        }
    }

    get("/gamedays/{id}/log/compare") {
        authenticated {
            val me = contextOf<CurrentPlayer>()
            val gameDay = memberGameDay(app, me) ?: return@authenticated call.respond(HttpStatusCode.NotFound)
            val game = call.parameters["game"]?.toIntOrNull()?.let(::BggId)?.let(app.catalog::get) ?: return@authenticated redirect("/gamedays/${gameDay.id}")
            val players = call.parameters.getAll("players").orEmpty()
            val tableLink = "/gamedays/${gameDay.id}/table?" + players.joinToString("&") { "players=$it" }
            val player = PlayerGames(app.tasteByPlayer[me.id] ?: TasteHistory(), app.collectionSync.collection(me.id))
            val candidates = (player.history.played.filterValues { it }.keys + player.owned + player.history.comparedGames).mapNotNull(app.catalog::get)
            val other = app.wizard.partner(player, game.id, candidates, app.clock.instant()) ?: return@authenticated redirect(tableLink)
            call.respondHtml { logComparePage(LogCompareView(gameDay, game, other, players.size, call.parameters["minutes"].orEmpty(), tableLink)) }
        }
    }

    post("/gamedays/{id}/log/compare") {
        authenticated {
            val me = contextOf<CurrentPlayer>()
            val gameDay = memberGameDay(app, me) ?: return@authenticated call.respond(HttpStatusCode.NotFound)
            val form = call.receiveParameters()
            val a = form["a"]?.toIntOrNull()?.let(::BggId)
            val b = form["b"]?.toIntOrNull()?.let(::BggId)
            val next = safeReturnTo(form["next"]).takeIf { it.startsWith("/gamedays/${gameDay.id}/") } ?: "/gamedays/${gameDay.id}"
            if (a != null && b != null) when (val answer = form["answer"]) {
                "A", "B", "BOTH_GREAT", "BOTH_MEH" ->
                    app.commands.handle(RecordComparisonDecider, ComparisonRecorded(me.id, a, b, ComparisonResult.valueOf(answer), Basis.PLAYED, app.clock.instant()))
                "UNPLAYED" -> app.commands.handle(DeclarePlayStatusDecider, GamePlayStatusDeclared(me.id, b, played = false))
            }
            redirect(next)
        }
    }
}

/** Shelf games not logged yet today come first: that's likeliest what was just played. */
private fun logChoices(app: App, gameDay: GameDaySummary): List<Game> {
    val logged = app.playsByGameDay[gameDay.id].orEmpty().map { it.game }.toSet()
    val shelf = app.shelfByGameDay[gameDay.id].orEmpty().mapNotNull { app.catalog[it.game] }
    return shelf.sortedBy { it.id in logged }
}
