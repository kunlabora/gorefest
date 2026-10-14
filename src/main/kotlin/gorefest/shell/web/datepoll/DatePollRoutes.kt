package gorefest.shell.web.datepoll

import gorefest.core.DatePollId
import gorefest.core.gameday.MAX_WISHES
import gorefest.core.datepoll.ProposePollGameDecider
import gorefest.core.datepoll.ProposePollGame
import gorefest.core.datepoll.AnswerPollGameDecider
import gorefest.core.datepoll.AnswerPollGame
import gorefest.core.datepoll.AnswerBringDecider
import gorefest.core.datepoll.AnswerBring
import gorefest.core.game.Game
import gorefest.core.PlayerId
import gorefest.core.GameNightId
import gorefest.core.BggId
import gorefest.core.GameDayId
import gorefest.core.datepoll.AnswerDatePoll
import gorefest.core.datepoll.AnswerDatePollDecider
import gorefest.core.datepoll.CreateDatePoll
import gorefest.core.datepoll.CreateDatePollDecider
import gorefest.core.datepoll.DatePollSummary
import gorefest.core.datepoll.JoinDatePoll
import gorefest.core.datepoll.JoinDatePollDecider
import gorefest.core.datepoll.PickDate
import gorefest.core.datepoll.PickDateDecider
import gorefest.core.eventsourcing.Decision
import gorefest.shell.App
import gorefest.shell.auth.Player
import gorefest.shell.AppConfig
import gorefest.shell.IdGenerator
import gorefest.shell.auth.Login
import gorefest.shell.web.CurrentPlayer
import gorefest.shell.web.DEFAULT_TIME_ZONE
import gorefest.shell.web.authenticated
import gorefest.shell.web.components.Nav
import gorefest.shell.web.components.SiteBar
import gorefest.shell.web.invite.deadInvitePage
import gorefest.shell.web.message
import gorefest.shell.web.redirect
import gorefest.shell.web.respondFragment
import gorefest.shell.web.sessionPlayer
import gorefest.shell.web.todayIn
import gorefest.shell.web.urlEncoded
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.server.html.respondHtml
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import java.time.Clock
import java.time.LocalDate
import kotlin.uuid.Uuid

context(ids: IdGenerator, clock: Clock, login: Login, config: AppConfig)
fun Route.datePollRoutes(app: App) {
    suspend fun view(poll: DatePollSummary, me: CurrentPlayer, gamesError: String? = null) = DatePollView(
        poll = poll,
        names = login.names(poll.participants),
        viewer = me,
        today = clock.todayIn(poll.timeZone),
        inviteLink = "${config.baseUrl}/p/${app.invites.token(poll.id)}",
        gameDay = poll.picked?.gameDay?.let { app.gameDaysById[it] },
        gameNight = poll.picked?.gameNight?.let(app.gameNights::of),
        games = if (poll.asksAboutGames) pollGames(app, poll, me, gamesError) else null,
    )

    get("/polls/new") {
        authenticated { call.respondHtml { datePollFormPage(SiteBar(contextOf<CurrentPlayer>(), Nav.GAME_DAYS)) } }
    }

    get("/polls/new/date") {
        authenticated { respondFragment { dateField() } }
    }

    post("/polls") {
        authenticated {
            val form = call.receiveParameters()
            val command = CreateDatePoll(
                poll = DatePollId(ids.next()),
                host = contextOf<CurrentPlayer>().id,
                title = form["title"].orEmpty(),
                place = form["place"].orEmpty(),
                dates = form.dates("dates"),
                timeZone = form["timeZone"] ?: DEFAULT_TIME_ZONE,
                today = clock.todayIn(form["timeZone"]),
                asksAboutGames = form["games"] == "true",
            )
            when (val decision = app.commands.handle(CreateDatePollDecider, command)) {
                is Decision.Accepted -> redirect("/polls/${command.poll}")
                is Decision.Rejected -> respondFragment { datePollForm(decision.reason.message(), form) }
            }
        }
    }

    get("/polls/{id}") {
        authenticated {
            val me = contextOf<CurrentPlayer>()
            val poll = memberPoll(app, me) ?: return@authenticated call.respond(HttpStatusCode.NotFound)
            val page = view(poll, me)
            call.respondHtml { datePollPage(page) }
        }
    }

    post("/polls/{id}/answer") {
        authenticated {
            val me = contextOf<CurrentPlayer>()
            val poll = memberPoll(app, me) ?: return@authenticated call.respond(HttpStatusCode.NotFound)
            val available = call.receiveParameters().dates("dates").toSet()
            val decision = app.commands.handle(AnswerDatePollDecider, AnswerDatePoll(poll.id, me.id, available))
            val current = app.datePolls.of(poll.id) ?: poll
            val error = (decision as? Decision.Rejected)?.reason?.message()
            val results = view(current, me)
            respondFragment(if (error != null) HttpStatusCode.Conflict else HttpStatusCode.OK) { datePollResults(results, error) }
        }
    }

    post("/polls/{id}/pick") {
        authenticated {
            val me = contextOf<CurrentPlayer>()
            val poll = memberPoll(app, me) ?: return@authenticated call.respond(HttpStatusCode.NotFound)
            val date = call.receiveParameters()["date"]?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val command = PickDate(poll.id, me.id, date ?: LocalDate.MIN, GameDayId(ids.next()), clock.todayIn(poll.timeZone), GameNightId(ids.next()))
            when (val decision = app.commands.handle(PickDateDecider, command)) {
                is Decision.Accepted -> redirect(if (poll.asksAboutGames) "/gamenights/${command.gameNight}" else "/gamedays/${command.gameDay}")
                is Decision.Rejected -> {
                    val results = view(app.datePolls.of(poll.id) ?: poll, me)
                    respondFragment(HttpStatusCode.Conflict) { datePollResults(results, decision.reason.message()) }
                }
            }
        }
    }

    // Games (U11): plain forms that come back to the poll, so the dates' results and the games always agree.

    suspend fun RoutingContext.afterGames(poll: DatePollSummary, me: CurrentPlayer, decision: Decision<*>) {
        when (decision) {
            is Decision.Accepted -> redirect("/polls/${poll.id}#poll-games")
            is Decision.Rejected -> {
                val page = view(app.datePolls.of(poll.id) ?: poll, me, decision.reason.message())
                call.respondHtml(HttpStatusCode.Conflict) { datePollPage(page) }
            }
        }
    }

    get("/polls/{id}/games/search") {
        authenticated {
            val me = contextOf<CurrentPlayer>()
            val poll = memberPoll(app, me)?.takeIf { it.host == me.id && it.asksAboutGames } ?: return@authenticated call.respond(HttpStatusCode.NotFound)
            val query = call.parameters["q"].orEmpty().trim()
            val pool = gamePool(app, poll, me).filter { (game, _) -> query.isNotEmpty() && game.name.contains(query, ignoreCase = true) }
            val found = if (query.length < 2) null else app.gameSearch.search(query)
            val names = login.names(pool.flatMap { it.second }.distinct())
            val known = pool.map { it.first.id }.toSet()
            respondFragment {
                pollGameSearchResults(poll, pool, names, found?.games.orEmpty().filter { it.id !in known && it.id !in poll.proposals }, found?.bggUnavailable == true, found?.nothingFor(query))
            }
        }
    }

    post("/polls/{id}/games") {
        authenticated {
            val me = contextOf<CurrentPlayer>()
            val poll = memberPoll(app, me) ?: return@authenticated call.respond(HttpStatusCode.NotFound)
            // Only games we know (found by search, or in someone's collection) go in: the page needs a name and a cover.
            val game = call.receiveParameters()["game"]?.toIntOrNull()?.let(::BggId)?.takeIf { app.catalog[it] != null }
                ?: return@authenticated call.respond(HttpStatusCode.NotFound)
            afterGames(poll, me, app.commands.handle(ProposePollGameDecider, ProposePollGame(poll.id, me.id, game)))
        }
    }

    post("/polls/{id}/games/{game}/remove") {
        authenticated {
            val me = contextOf<CurrentPlayer>()
            val poll = memberPoll(app, me) ?: return@authenticated call.respond(HttpStatusCode.NotFound)
            val game = call.parameters["game"]?.toIntOrNull()?.let(::BggId) ?: return@authenticated call.respond(HttpStatusCode.NotFound)
            afterGames(poll, me, app.commands.handle(ProposePollGameDecider, ProposePollGame(poll.id, me.id, game, proposed = false)))
        }
    }

    post("/polls/{id}/games/{game}/answer") {
        authenticated {
            val me = contextOf<CurrentPlayer>()
            val poll = memberPoll(app, me) ?: return@authenticated call.respond(HttpStatusCode.NotFound)
            val game = call.parameters["game"]?.toIntOrNull()?.let(::BggId) ?: return@authenticated call.respond(HttpStatusCode.NotFound)
            val yes = call.receiveParameters()["yes"]?.toBooleanStrictOrNull() ?: return@authenticated call.respond(HttpStatusCode.BadRequest)
            afterGames(poll, me, app.commands.handle(AnswerPollGameDecider, AnswerPollGame(poll.id, me.id, game, yes)))
        }
    }

    post("/polls/{id}/games/{game}/bring") {
        authenticated {
            val me = contextOf<CurrentPlayer>()
            val poll = memberPoll(app, me) ?: return@authenticated call.respond(HttpStatusCode.NotFound)
            val game = call.parameters["game"]?.toIntOrNull()?.let(::BggId) ?: return@authenticated call.respond(HttpStatusCode.NotFound)
            val bringing = call.receiveParameters()["bringing"]?.toBooleanStrictOrNull() ?: return@authenticated call.respond(HttpStatusCode.BadRequest)
            afterGames(poll, me, app.commands.handle(AnswerBringDecider, AnswerBring(poll.id, me.id, game, bringing)))
        }
    }

    // The way in: a signed link, like a game day's. Joining only happens through it.
    fun resolve(token: String?): DatePollSummary? = token?.let(app.invites::parsePoll)?.let(app.datePolls::of)

    suspend fun RoutingContext.join(poll: DatePollSummary, me: Player) {
        when (val decision = app.commands.handle(JoinDatePollDecider, JoinDatePoll(poll.id, me.id))) {
            is Decision.Accepted -> redirect("/polls/${poll.id}")
            is Decision.Rejected -> call.respondHtml(HttpStatusCode.Conflict) { deadInvitePage(decision.reason.message()) }
        }
    }

    get("/p/{token}") {
        val poll = resolve(call.parameters["token"]) ?: return@get call.respondHtml(HttpStatusCode.NotFound) { deadInvitePage() }
        val me = sessionPlayer()
        // Someone already in the poll goes to it, picked or not: that's where the result (and the game day) is.
        if (me != null && me.id in poll.participants) return@get redirect("/polls/${poll.id}")
        if (!poll.open) return@get call.respondHtml(HttpStatusCode.Gone) { deadInvitePage("The host already picked a date") }
        // Back from signing up or logging in for this invite: join without asking again.
        if (me?.name != null && app.invites.isJoinMarker(call.parameters["join"], me.id, "/p/${call.parameters["token"]}")) {
            return@get if (me.approved) join(poll, me) else redirect("/pending?returnTo=${"/p/${call.parameters["token"]}".urlEncoded()}")
        }
        val path = "/p/${call.parameters["token"]}"
        val landing = PollInviteView(poll, login.names(poll.participants), loggedIn = me != null, path = path)
        call.respondHtml { pollInvitePage(landing) }
    }

    post("/p/{token}/join") {
        val token = call.parameters["token"]
        val poll = resolve(token) ?: return@post call.respondHtml(HttpStatusCode.NotFound) { deadInvitePage() }
        // The login (and the welcome step) carry this path along, so the invite survives them and joins on the way back.
        val comeBack = "/p/$token?join=1".urlEncoded()
        val me = sessionPlayer() ?: return@post redirect("/login?returnTo=$comeBack")
        if (me.name == null) return@post redirect("/welcome?returnTo=$comeBack")
        if (!me.approved) return@post redirect("/pending?returnTo=${"/p/$token".urlEncoded()}")
        join(poll, me)
    }
}

/** The poll in the `{id}` path parameter, if [me] joined it. Everyone else gets nothing (404). */
private fun RoutingContext.memberPoll(app: App, me: CurrentPlayer): DatePollSummary? =
    call.parameters["id"]?.let { runCatching { DatePollId(Uuid.parse(it)) }.getOrNull() }
        ?.let(app.datePolls::of)?.takeIf { me.id in it.participants }

/** Valid ISO dates among the values of [name]; blanks and junk are dropped. */
private fun Parameters.dates(name: String): List<LocalDate> =
    getAll(name).orEmpty().mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }

/**
 * Games the host can put to the poll from people's collections: their own, and those of people in the poll who let
 * others ask them to bring games (the same switch as on a game day's ask screen). Each with who has it.
 */
private suspend fun gamePool(app: App, poll: DatePollSummary, me: CurrentPlayer): List<Pair<Game, List<PlayerId>>> {
    if (me.id != poll.host) return emptyList()
    val owners = poll.participants.filter { it == me.id || app.bringRequests.accepts(it) }
    val byGame = mutableMapOf<BggId, MutableList<PlayerId>>()
    owners.forEach { owner -> app.collectionSync.collection(owner).forEach { byGame.getOrPut(it) { mutableListOf() } += owner } }
    return byGame.filterKeys { it !in poll.proposals }.mapNotNull { (id, who) -> app.catalog[id]?.let { it to who.toList() } }.sortedBy { it.first.name.lowercase() }
}

/** The games part of a poll page: each game with its answers and who brings it, and for the host the games they can add. */
private suspend fun pollGames(app: App, poll: DatePollSummary, me: CurrentPlayer, error: String?): PollGamesView {
    val mine = app.collectionSync.collection(me.id)
    val rows = poll.proposals.mapNotNull { id ->
        app.catalog[id]?.let { game ->
            PollGameRow(
                game = game,
                yes = poll.yesFor(id),
                no = poll.noFor(id),
                bringers = poll.bringers(id),
                myAnswer = poll.gameAnswers[me.id]?.get(id),
                myBring = poll.bringAnswers[me.id]?.get(id),
                inMyCollection = id in mine,
            )
        }
    }
    return PollGamesView(rows, pool = gamePool(app, poll, me), yesLeft = (MAX_WISHES - poll.yesCount(me.id)).coerceAtLeast(0), error = error)
}
