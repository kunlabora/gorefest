package gorefest.shell.web.request

import gorefest.core.BggId
import gorefest.core.PlayerId
import gorefest.core.eventsourcing.Decision
import gorefest.core.game.Game
import gorefest.core.gameday.DeclineBringRequest
import gorefest.core.gameday.DeclineBringRequestDecider
import gorefest.core.gameday.GameDaySummary
import gorefest.core.gameday.RequestBring
import gorefest.core.gameday.RequestBringDecider
import gorefest.core.gameday.RequestStatus
import gorefest.core.gameday.WithdrawBringRequest
import gorefest.core.gameday.WithdrawBringRequestDecider
import gorefest.shell.App
import gorefest.shell.auth.Login
import gorefest.shell.web.CurrentPlayer
import gorefest.shell.web.authenticated
import gorefest.shell.web.memberGameDay
import gorefest.shell.web.message
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
import kotlin.uuid.Uuid

/** The most games a search lists: a few is plenty to pick from. */
private const val MAX_RESULTS = 12

context(login: Login)
fun Route.requestRoutes(app: App) {
    get("/gamedays/{id}/ask") {
        authenticated {
            val me = contextOf<CurrentPlayer>()
            val gameDay = openGameDay(app, me) ?: return@authenticated call.respond(HttpStatusCode.NotFound)
            val view = AskView(members(app, gameDay, me, login), myRequests(app, gameDay, me, login))
            call.respondHtml { with(me) { askPage(gameDay, view) } }
        }
    }

    /** Who has this? Games matching the search, in the collections of the members who allow requests. */
    get("/gamedays/{id}/ask-search") {
        authenticated {
            val me = contextOf<CurrentPlayer>()
            val gameDay = openGameDay(app, me) ?: return@authenticated call.respond(HttpStatusCode.NotFound)
            val query = call.parameters["q"].orEmpty().trim()
            val rows = if (query.length < 2) emptyList() else members(app, gameDay, me, login).flatMap { member -> askRows(app, gameDay, me, member, query) }.take(MAX_RESULTS)
            respondFragment { askResults(gameDay, rows, searched = query.length >= 2) }
        }
    }

    get("/gamedays/{id}/ask/from/{owner}") {
        authenticated {
            val me = contextOf<CurrentPlayer>()
            val gameDay = openGameDay(app, me) ?: return@authenticated call.respond(HttpStatusCode.NotFound)
            val owner = askableMember(app, gameDay, me, login) ?: return@authenticated call.respond(HttpStatusCode.NotFound)
            val rows = askRows(app, gameDay, me, owner, query = "")
            call.respondHtml { with(me) { askMemberPage(gameDay, owner, rows) } }
        }
    }

    get("/gamedays/{id}/ask/from/{owner}/games") {
        authenticated {
            val me = contextOf<CurrentPlayer>()
            val gameDay = openGameDay(app, me) ?: return@authenticated call.respond(HttpStatusCode.NotFound)
            val owner = askableMember(app, gameDay, me, login) ?: return@authenticated call.respond(HttpStatusCode.NotFound)
            val rows = askRows(app, gameDay, me, owner, call.parameters["q"].orEmpty().trim())
            respondFragment { askList(gameDay, rows, named = false) }
        }
    }

    get("/gamedays/{id}/ask/mine") {
        authenticated {
            val me = contextOf<CurrentPlayer>()
            val gameDay = openGameDay(app, me) ?: return@authenticated call.respond(HttpStatusCode.NotFound)
            val rows = myRequests(app, gameDay, me, login)
            respondFragment { myRequests(gameDay, rows) }
        }
    }

    /** The owner's section of the bring page, fetched again when the shelf changed. */
    get("/gamedays/{id}/ask/requests") {
        authenticated {
            val me = contextOf<CurrentPlayer>()
            val gameDay = openGameDay(app, me) ?: return@authenticated call.respond(HttpStatusCode.NotFound)
            val rows = ownerRequests(app, gameDay, me, login)
            respondFragment { bringRequests(gameDay, rows) }
        }
    }

    post("/gamedays/{id}/ask") {
        authenticated {
            val me = contextOf<CurrentPlayer>()
            val gameDay = memberGameDay(app, me) ?: return@authenticated call.respond(HttpStatusCode.NotFound)
            val form = call.receiveParameters()
            val owner = form["owner"]?.let(::playerId)?.takeIf { it in gameDay.players && it != me.id }
            val game = form["game"]?.toIntOrNull()?.let { app.catalog[BggId(it)] }
            // Ownership lives in the collection tables, not in the log: it's checked here, before the decider sees the command.
            if (owner == null || game == null || game.id !in app.collectionSync.collection(owner)) return@authenticated call.respond(HttpStatusCode.NotFound)
            val decision = app.commands.handle(RequestBringDecider, RequestBring(gameDay.id, me.id, owner, game.id))
            val ownerName = login.names(listOf(owner))[owner] ?: "Someone"
            val row = AskRow(game, owner, ownerName, askState(app, gameDay, me, owner, game.id))
            call.response.header("HX-Trigger", REQUESTS_CHANGED)
            respondFragment(if (decision is Decision.Rejected) HttpStatusCode.Conflict else HttpStatusCode.OK) {
                askRow(gameDay, row, named = form["named"] == "true", error = (decision as? Decision.Rejected)?.reason?.message())
            }
        }
    }

    post("/gamedays/{id}/ask/withdraw") {
        authenticated {
            val me = contextOf<CurrentPlayer>()
            val gameDay = memberGameDay(app, me) ?: return@authenticated call.respond(HttpStatusCode.NotFound)
            val form = call.receiveParameters()
            val owner = form["owner"]?.let(::playerId)
            val game = form["game"]?.toIntOrNull()?.let(::BggId)
            if (owner == null || game == null) return@authenticated call.respond(HttpStatusCode.NotFound)
            val decision = app.commands.handle(WithdrawBringRequestDecider, WithdrawBringRequest(gameDay.id, me.id, owner, game))
            val status = if (decision is Decision.Rejected) HttpStatusCode.Conflict else HttpStatusCode.OK
            val rows = myRequests(app, gameDay, me, login)
            respondFragment(status) { myRequests(gameDay, rows) }
        }
    }

    post("/gamedays/{id}/ask/decline") {
        authenticated {
            val me = contextOf<CurrentPlayer>()
            val gameDay = memberGameDay(app, me) ?: return@authenticated call.respond(HttpStatusCode.NotFound)
            val game = call.receiveParameters()["game"]?.toIntOrNull()?.let(::BggId) ?: return@authenticated call.respond(HttpStatusCode.NotFound)
            // "Not this time" on a row with several askers turns down each of them.
            val decisions = app.bringRequests.openFor(me.id, gameDay.id).filter { it.game == game }.map {
                app.commands.handle(DeclineBringRequestDecider, DeclineBringRequest(gameDay.id, me.id, game, it.asker))
            }
            val rejected = decisions.filterIsInstance<Decision.Rejected>().firstOrNull()
            val rows = ownerRequests(app, gameDay, me, login)
            respondFragment(if (rejected != null) HttpStatusCode.Conflict else HttpStatusCode.OK) { bringRequests(gameDay, rows) }
        }
    }
}

/** The games people ask me to bring on [gameDay], one row per game with everyone who asks. */
suspend fun ownerRequests(app: App, gameDay: GameDaySummary, me: CurrentPlayer, login: Login): List<OwnerRequest> {
    if (gameDay.closed) return emptyList()
    val open = app.bringRequests.openFor(me.id, gameDay.id)
    val names = login.names(open.map { it.asker }.toSet())
    return open.groupBy { it.game }.mapNotNull { (game, requests) ->
        app.catalog[game]?.let { OwnerRequest(it, requests.map { r -> r.asker to (names[r.asker] ?: "Someone") }) }
    }
}

private fun RoutingContext.openGameDay(app: App, me: CurrentPlayer) = memberGameDay(app, me)?.takeIf { !it.closed }

private fun playerId(s: String) = runCatching { PlayerId(Uuid.parse(s)) }.getOrNull()

/** The other members who can be asked: they joined, and they allow requests. */
private suspend fun members(app: App, gameDay: GameDaySummary, me: CurrentPlayer, login: Login): List<AskMember> {
    val others = gameDay.players.filter { it != me.id && app.bringRequests.accepts(it) }
    val names = login.names(others)
    return others.map { AskMember(it, names[it] ?: "Unknown player") }.sortedBy { it.name.lowercase() }
}

/** The member in the `{owner}` path parameter, if they can be asked. */
private suspend fun RoutingContext.askableMember(app: App, gameDay: GameDaySummary, me: CurrentPlayer, login: Login): AskMember? {
    val owner = call.parameters["owner"]?.let(::playerId) ?: return null
    return members(app, gameDay, me, login).firstOrNull { it.id == owner }
}

/** [owner]'s collection (games matching [query] by name), each with what asking looks like for me. */
private suspend fun askRows(app: App, gameDay: GameDaySummary, me: CurrentPlayer, owner: AskMember, query: String): List<AskRow> =
    app.collectionSync.collection(owner.id).mapNotNull(app.catalog::get)
        .filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
        .sortedBy { it.name.lowercase() }
        .map { AskRow(it, owner.id, owner.name, askState(app, gameDay, me, owner.id, it.id)) }

private fun askState(app: App, gameDay: GameDaySummary, me: CurrentPlayer, owner: PlayerId, game: BggId): AskState {
    if (app.shelves.of(gameDay.id).any { it.game == game && owner in it.owners }) return AskState.BRINGING
    return when (app.bringRequests.by(me.id, gameDay.id).lastOrNull { it.owner == owner && it.game == game }?.status) {
        RequestStatus.OPEN -> AskState.ASKED
        RequestStatus.DECLINED -> AskState.DECLINED
        RequestStatus.ANSWERED, null -> AskState.CAN_ASK
    }
}

/** My requests, one per game and owner (the latest), with how they went. */
private suspend fun myRequests(app: App, gameDay: GameDaySummary, me: CurrentPlayer, login: Login): List<MyRequest> {
    val mine = app.bringRequests.by(me.id, gameDay.id)
    val names = login.names(mine.map { it.owner }.toSet())
    return mine.groupBy { it.owner to it.game }.values.map { it.last() }.mapNotNull { request ->
        app.catalog[request.game]?.let { MyRequest(it, request.owner, names[request.owner] ?: "Someone", request.status) }
    }
}
