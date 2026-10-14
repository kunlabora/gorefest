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
import gorefest.shell.App
import gorefest.shell.auth.Login
import gorefest.shell.web.CurrentPlayer
import gorefest.shell.web.message
import gorefest.shell.web.authenticated
import gorefest.shell.web.bgg.collection
import gorefest.shell.web.components.of
import gorefest.shell.web.memberGameDay
import gorefest.shell.web.redirect
import gorefest.shell.web.zone
import gorefest.shell.web.respondFragment
import gorefest.shell.web.safeReturnTo
import gorefest.shell.web.todayIn
import gorefest.shell.web.urlEncoded
import io.ktor.http.HttpStatusCode
import io.ktor.server.html.respondHtml
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlin.uuid.Uuid

private const val MAX_MINUTES = 24 * 60

/** Morning, lunch or evening, by the clock where the game day is. */
fun checkpointLabel(gameDay: GameDaySummary, clock: java.time.Clock): String {
    val hour = clock.instant().atZone(zone(gameDay)).hour
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

    get("/gamedays/{id}/log/search") {
        authenticated {
            val me = contextOf<CurrentPlayer>()
            val gameDay = memberGameDay(app, me) ?: return@authenticated call.respond(HttpStatusCode.NotFound)
            val query = call.parameters["q"].orEmpty()
            val result = app.gameSearch.search(query)
            // Shelf games are tiles already: a second radio for the same game would repeat its id.
            val onShelf = logChoices(app, gameDay).map { it.id }.toSet()
            respondFragment { logSearchResults(result.games.filter { it.id !in onShelf }, result.bggUnavailable, result.nothingFor(query)) }
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
            // A game that isn't on the shelf can be logged too (found by search): keep its tile when the form comes back.
            val shelf = logChoices(app, gameDay)
            val view = LogFormView(gameDay, if (game == null || shelf.any { it.id == game.id }) shelf else shelf + game, login.names(gameDay.players))
            if (game == null) return@authenticated respondFragment { logForm(view, me, form, "Pick the game you played") }
            val log = PlayLogged(gameDay.id, game.id, players, minutes, me.id, app.clock.instant())
            when (val decision = app.commands.handle(LogPlayDecider, log)) {
                is Decision.Accepted -> redirect("/gamedays/${gameDay.id}/log/compare?game=${game.id}&minutes=$minutes&${players.joinToString("&") { "players=$it" }}")
                is Decision.Rejected -> respondFragment { logForm(view, me, form, decision.reason.message()) }
            }
        }
    }

    get("/gamedays/{id}/log/compare") {
        authenticated {
            val me = contextOf<CurrentPlayer>()
            val gameDay = memberGameDay(app, me) ?: return@authenticated call.respond(HttpStatusCode.NotFound)
            val game = call.parameters["game"]?.toIntOrNull()?.let(::BggId)?.let(app.catalog::get) ?: return@authenticated redirect("/gamedays/${gameDay.id}")
            val players = call.parameters.getAll("players").orEmpty()
                .mapNotNull { runCatching { PlayerId(Uuid.parse(it)) }.getOrNull() }.filter { it in gameDay.players }.distinct()
            val minutes = call.parameters["minutes"]?.toIntOrNull()?.takeIf { it in 0..MAX_MINUTES } ?: 0
            val tableLink = "/gamedays/${gameDay.id}/table?" + players.joinToString("&") { "players=${it.toString().urlEncoded()}" }
            // "Which did you enjoy more?" is about your own play: logging someone else's table asks nothing.
            if (me.id !in players) return@authenticated redirect(tableLink)
            val player = PlayerGames(app.tastes.of(me.id), app.collectionSync.collection(me.id))
            val candidates = (player.history.played.filterValues { it }.keys + player.owned + player.history.comparedGames).mapNotNull(app.catalog::get)
            val other = app.wizard.partner(player, game.id, candidates, app.clock.instant()) ?: return@authenticated redirect(tableLink)
            call.respondHtml { logComparePage(LogCompareView(gameDay, game, other, players.size, minutes.toString(), tableLink), me) }
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
            val playedIt = a != null && app.plays.on(gameDay.id).any { it.game == a && me.id in it.players }
            if (a != null && b != null && playedIt) when (val answer = form["answer"]) {
                "A", "B", "BOTH_GREAT", "BOTH_MEH" ->
                    app.commands.handle(RecordComparisonDecider, ComparisonRecorded(me.id, a, b, ComparisonResult.valueOf(answer), Basis.PLAYED, app.clock.instant()))
                "UNPLAYED" -> app.commands.handle(DeclarePlayStatusDecider, GamePlayStatusDeclared(me.id, b, played = false))
            }
            redirect(next)
        }
    }

    watchedRoutes(app)
}

/** Watching a table play (U9) can be told from the day itself until the game day closes, like logging a play. */
private fun watchOpen(app: App, gameDay: GameDaySummary) = !gameDay.closed && gameDay.date <= app.clock.todayIn(gameDay.timeZone)

/** The whole shelf, minus what you've played yourself: the log may not be filled in yet, so we don't guess who watched what. */
private fun watchChoices(app: App, gameDay: GameDaySummary, me: CurrentPlayer): List<Game> {
    val played = app.tastes.of(me.id).played
    return app.shelves.of(gameDay.id).mapNotNull { app.catalog[it.game] }.filter { played[it.id] != true }
}

context(login: Login)
private fun Route.watchedRoutes(app: App) {
    get("/gamedays/{id}/watched") {
        authenticated {
            val me = contextOf<CurrentPlayer>()
            val gameDay = memberGameDay(app, me) ?: return@authenticated call.respond(HttpStatusCode.NotFound)
            if (!watchOpen(app, gameDay)) return@authenticated redirect("/gamedays/${gameDay.id}")
            call.respondHtml { watchedPage(WatchedView(gameDay, watchChoices(app, gameDay, me)), checkpointLabel(gameDay, app.clock), me) }
        }
    }

    get("/gamedays/{id}/watched/search") {
        authenticated {
            val me = contextOf<CurrentPlayer>()
            val gameDay = memberGameDay(app, me) ?: return@authenticated call.respond(HttpStatusCode.NotFound)
            val query = call.parameters["q"].orEmpty()
            val result = app.gameSearch.search(query)
            val onShelf = watchChoices(app, gameDay, me).map { it.id }.toSet()
            respondFragment { logSearchResults(result.games.filter { it.id !in onShelf }, result.bggUnavailable, result.nothingFor(query), id = WATCHED_RESULTS) }
        }
    }

    get("/gamedays/{id}/watched/compare") {
        authenticated {
            val me = contextOf<CurrentPlayer>()
            val gameDay = memberGameDay(app, me) ?: return@authenticated call.respond(HttpStatusCode.NotFound)
            val hub = "/gamedays/${gameDay.id}"
            if (!watchOpen(app, gameDay)) return@authenticated redirect(hub)
            val game = call.parameters["game"]?.toIntOrNull()?.let(::BggId)?.let(app.catalog::get) ?: return@authenticated redirect("$hub/watched")
            val player = PlayerGames(app.tastes.of(me.id), app.collectionSync.collection(me.id))
            val candidates = (player.history.played.filterValues { it }.keys + player.owned + player.history.comparedGames).mapNotNull(app.catalog::get)
            // Nothing you've played to hold it against: there's no question to ask yet.
            val other = app.wizard.partner(player, game.id, candidates, app.clock.instant())
                ?: return@authenticated call.respondHtml { watchedNothingPage(gameDay, game, me) }
            call.respondHtml { watchedComparePage(WatchedCompareView(gameDay, game, other), me) }
        }
    }

    post("/gamedays/{id}/watched/compare") {
        authenticated {
            val me = contextOf<CurrentPlayer>()
            val gameDay = memberGameDay(app, me) ?: return@authenticated call.respond(HttpStatusCode.NotFound)
            val form = call.receiveParameters()
            val a = form["a"]?.toIntOrNull()?.let(::BggId)
            val b = form["b"]?.toIntOrNull()?.let(::BggId)
            val answer = form["answer"]
            if (watchOpen(app, gameDay) && a != null && b != null && answer in WATCHED_ANSWERS)
                app.commands.handle(RecordComparisonDecider, ComparisonRecorded(me.id, a, b, ComparisonResult.valueOf(answer!!), Basis.WATCHED, app.clock.instant()))
            redirect("/gamedays/${gameDay.id}")
        }
    }
}

private val WATCHED_ANSWERS = setOf("A", "B", "BOTH_GREAT", "BOTH_MEH")

/** Shelf games not logged yet today come first: that's likeliest what was just played. */
private fun logChoices(app: App, gameDay: GameDaySummary): List<Game> {
    val logged = app.plays.on(gameDay.id).map { it.game }.toSet()
    val shelf = app.shelves.of(gameDay.id).mapNotNull { app.catalog[it.game] }
    return shelf.sortedBy { it.id in logged }
}
