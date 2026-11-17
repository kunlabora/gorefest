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
import gorefest.core.gameday.PlayKey
import gorefest.core.gameday.RemovePlay
import gorefest.core.gameday.RemovePlayDecider
import gorefest.core.gameday.key
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
import io.ktor.http.Parameters
import io.ktor.server.routing.RoutingContext
import java.time.Instant
import kotlin.math.abs
import io.ktor.server.html.respondHtml
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlin.uuid.Uuid

private const val MAX_MINUTES = 24 * 60

/** Morning, lunch or evening, by the clock where the game day is; after the day, just that. */
fun checkpointLabel(gameDay: GameDaySummary, clock: java.time.Clock): String {
    if (gameDay.ended) return "After the game day"
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
            val game = call.parameters["game"]?.toIntOrNull()?.let(::BggId)?.let(app.catalog::get)
            val view = logFormView(app, gameDay, game)
            val values = logValues(app, gameDay, me, game)
            val removed = game?.name?.takeIf { call.parameters["removed"] == "true" }
            call.respondHtml { logPage(view, checkpointLabel(gameDay, app.clock), me, values, removed) }
        }
    }

    get("/gamedays/{id}/log/search") {
        authenticated {
            val me = contextOf<CurrentPlayer>()
            val gameDay = memberGameDay(app, me) ?: return@authenticated call.respond(HttpStatusCode.NotFound)
            val query = call.parameters["q"].orEmpty()
            val result = app.gameSearch.search(query)
            // Shelf games are tiles already: a second radio for the same game would repeat its id.
            // So are games added to the form beside the shelf (a removed play's, found by search before): `also`.
            val onShelf = logChoices(app, gameDay).map { it.id }.toSet() + call.parameters.getAll("also").orEmpty().mapNotNull { it.toIntOrNull()?.let(::BggId) }
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
            val view = logFormView(app, gameDay, game)
            if (game == null) return@authenticated respondFragment { logForm(view, me, form, "Pick the game you played") }
            val log = PlayLogged(gameDay.id, game.id, players, minutes, me.id, app.clock.instant())
            when (val decision = app.commands.handle(LogPlayDecider, log)) {
                // Logging the same game again, by whoever was asked about it the first time, asks nothing more (U23).
                is Decision.Accepted -> if (form["again"] == game.id.toString()) redirect(tableLink(gameDay, form.playersAt(gameDay)))
                else redirect("/gamedays/${gameDay.id}/log/compare?game=${game.id}&minutes=$minutes&${players.joinToString("&") { "players=$it" }}")
                is Decision.Rejected -> respondFragment { logForm(view, me, form, decision.reason.message()) }
            }
        }
    }

    get("/gamedays/{id}/log/compare") {
        authenticated {
            val me = contextOf<CurrentPlayer>()
            val gameDay = memberGameDay(app, me) ?: return@authenticated call.respond(HttpStatusCode.NotFound)
            val game = call.parameters["game"]?.toIntOrNull()?.let(::BggId)?.let(app.catalog::get) ?: return@authenticated redirect("/gamedays/${gameDay.id}")
            val players = call.parameters.playersAt(gameDay)
            val minutes = call.parameters["minutes"]?.toIntOrNull()?.takeIf { it in 0..MAX_MINUTES } ?: 0
            val tableLink = tableLink(gameDay, players)
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

    /** Take back a play logged by mistake (U23), then log it again from a filled-in form. */
    post("/gamedays/{id}/plays/remove") {
        authenticated {
            val me = contextOf<CurrentPlayer>()
            val gameDay = memberGameDay(app, me) ?: return@authenticated call.respond(HttpStatusCode.NotFound)
            val form = call.receiveParameters()
            val game = form["game"]?.toIntOrNull()?.let(::BggId)
            val loggedBy = form["loggedBy"]?.let { runCatching { PlayerId(Uuid.parse(it)) }.getOrNull() }
            val at = form["at"]?.let { runCatching { Instant.parse(it) }.getOrNull() }
            if (game == null || loggedBy == null || at == null) return@authenticated call.respond(HttpStatusCode.BadRequest)
            val key = PlayKey(gameDay.id, game, loggedBy, at)
            val play = app.plays.on(gameDay.id).firstOrNull { it.key == key }
            when (val decision = app.commands.handle(RemovePlayDecider, RemovePlay(key, me.id, app.clock.instant()))) {
                // Refused (a stale page: someone else's play, or one already gone): back to the hub, which shows how things stand.
                is Decision.Rejected -> redirect("/gamedays/${gameDay.id}")
                is Decision.Accepted -> {
                    // Already gone (a double tap): back to the hub, where it no longer shows.
                    if (play == null) return@authenticated redirect("/gamedays/${gameDay.id}")
                    val duration = DURATIONS.minBy { abs(it.second - play.minutes) }.first
                    // The question after a play went to whoever logged it, if they played: only they skip it when logging the same game again.
                    val asked = me.id == play.loggedBy && me.id in play.players
                    redirect(
                        "/gamedays/${gameDay.id}/log?removed=true&game=${play.game}&duration=${duration.urlEncoded()}&" +
                            (if (asked) "again=${play.game}&" else "") + play.players.joinToString("&") { "players=$it" },
                    )
                }
            }
        }
    }

    watchedRoutes(app)
}

/** The players named in [this] who are at [gameDay], each once. */
private fun Parameters.playersAt(gameDay: GameDaySummary) = getAll("players").orEmpty().mapNotNull { runCatching { PlayerId(Uuid.parse(it)) }.getOrNull() }.filter { it in gameDay.players }.distinct()

/**
 * The log form's games: the shelf, plus [game] if it isn't on it (found by search, or a removed play's) so its tile
 * stays when the form comes back.
 */
context(login: Login)
private suspend fun logFormView(app: App, gameDay: GameDaySummary, game: Game?): LogFormView {
    val shelf = logChoices(app, gameDay)
    val offShelf = listOfNotNull(game?.takeIf { g -> shelf.none { it.id == g.id } })
    return LogFormView(gameDay, shelf + offShelf, login.names(gameDay.players), offShelf.map { it.id })
}

/** After logging: suggestions for that table, or the hub once the game day is over (no next game to pick there). */
private fun tableLink(gameDay: GameDaySummary, players: List<PlayerId>) = if (gameDay.ended) "/gamedays/${gameDay.id}"
else "/gamedays/${gameDay.id}/table?" + players.joinToString("&") { "players=${it.toString().urlEncoded()}" }

/**
 * What the log form starts with (U23): the game, table and length asked for (a suggestion, or a play just removed);
 * otherwise the table of my last play on this game day; otherwise just me. Nobody outside the game day, no unknown length.
 */
private fun RoutingContext.logValues(app: App, gameDay: GameDaySummary, me: CurrentPlayer, game: Game?): Parameters {
    val asked = call.parameters.playersAt(gameDay)
    val lastTable = app.plays.on(gameDay.id).lastOrNull { me.id in it.players }?.players?.filter { it in gameDay.players }
    val players = asked.ifEmpty { lastTable.orEmpty() }
    val duration = call.parameters["duration"]?.takeIf { d -> DURATIONS.any { it.first == d } }
    return Parameters.build {
        if (game != null) append("game", game.id.toString())
        if (players.isNotEmpty()) appendAll("players", players.map { it.toString() })
        if (duration != null) append("duration", duration)
        call.parameters["again"]?.takeIf { game != null && it == game.id.toString() }?.let { append("again", it) }
    }
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
            respondFragment { logSearchResults(result.games.filter { it.id !in onShelf }, result.bggUnavailable, result.nothingFor(query), id = WATCHED_RESULTS, question = WATCHED_QUESTION) }
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
            val candidates = reallyPlayed(app, me).mapNotNull(app.catalog::get)
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
            // Only what the page could have offered: a game we know that you haven't played, against one you have (BUG-031).
            val offered = a != null && b != null && app.catalog[a] != null && app.tastes.of(me.id).played[a] != true && b in reallyPlayed(app, me)
            if (watchOpen(app, gameDay) && offered && answer in WATCHED_ANSWERS)
                app.commands.handle(RecordComparisonDecider, ComparisonRecorded(me.id, a, b, ComparisonResult.valueOf(answer!!), Basis.WATCHED, app.clock.instant()))
            redirect("/gamedays/${gameDay.id}")
        }
    }
}

private val WATCHED_ANSWERS = setOf("A", "B", "BOTH_GREAT", "BOTH_MEH")

/**
 * Games [me] really played: logged, said so, or compared as played. Owning a game, or having a vibe about it, isn't
 * playing it (BUG-030), so the watched question never holds a game against one you don't know either.
 */
private fun reallyPlayed(app: App, me: CurrentPlayer): Set<BggId> {
    val history = app.tastes.of(me.id)
    return history.played.filterValues { it }.keys +
        history.comparisons.filter { it.basis == Basis.PLAYED }.flatMap { listOf(it.a, it.b) }
            .filter { history.played[it] != false }
}

/** Shelf games not logged yet today come first: that's likeliest what was just played. */
private fun logChoices(app: App, gameDay: GameDaySummary): List<Game> {
    val logged = app.plays.on(gameDay.id).map { it.game }.toSet()
    val shelf = app.shelves.of(gameDay.id).mapNotNull { app.catalog[it.game] }
    return shelf.sortedBy { it.id in logged }
}
