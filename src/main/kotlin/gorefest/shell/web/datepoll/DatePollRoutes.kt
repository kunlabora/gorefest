package gorefest.shell.web.datepoll

import gorefest.core.DatePollId
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
    suspend fun view(poll: DatePollSummary, me: CurrentPlayer) = DatePollView(
        poll = poll,
        names = login.names(poll.participants),
        viewer = me,
        today = clock.todayIn(poll.timeZone),
        inviteLink = "${config.baseUrl}/p/${app.invites.token(poll.id)}",
        gameDay = poll.picked?.let { app.gameDaysById[it.gameDay] },
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
            val command = PickDate(poll.id, me.id, date ?: LocalDate.MIN, GameDayId(ids.next()), clock.todayIn(poll.timeZone))
            when (val decision = app.commands.handle(PickDateDecider, command)) {
                is Decision.Accepted -> redirect("/gamedays/${command.gameDay}")
                is Decision.Rejected -> {
                    val results = view(app.datePolls.of(poll.id) ?: poll, me)
                    respondFragment(HttpStatusCode.Conflict) { datePollResults(results, decision.reason.message()) }
                }
            }
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
        if (me?.name != null && app.invites.isJoinMarker(call.parameters["join"], me.id, "/p/${call.parameters["token"]}")) return@get join(poll, me)
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
