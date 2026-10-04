package gorefest.shell.web.gameday

import gorefest.core.GameDayId
import gorefest.core.eventsourcing.Decision
import gorefest.core.gameday.CreateGameDay
import gorefest.core.gameday.CreateGameDayDecider
import gorefest.core.gameday.EditGameDay
import gorefest.core.gameday.EditGameDayDecider
import gorefest.core.gameday.GameDaySummary
import gorefest.core.gameday.GameDays
import gorefest.core.gameday.RotateInviteLink
import gorefest.core.gameday.RotateInviteLinkDecider
import gorefest.core.taste.PlayerGames
import gorefest.shell.App
import gorefest.shell.AppConfig
import gorefest.shell.IdGenerator
import gorefest.shell.auth.Login
import gorefest.shell.web.CurrentPlayer
import gorefest.shell.web.message
import gorefest.shell.web.DEFAULT_TIME_ZONE
import gorefest.shell.web.todayIn
import gorefest.shell.web.authenticated
import gorefest.shell.web.bgg.collection
import gorefest.shell.web.checkpoint.checkpointLabel
import gorefest.shell.web.components.errorNotice
import gorefest.shell.web.components.nextStep
import gorefest.shell.web.memberGameDay
import gorefest.shell.web.redirect
import gorefest.shell.web.respondFragment
import gorefest.shell.web.taste.TOP_UP_AFTER
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

private val createForm = GameDayFormView("Host a game day", "/" to "All game days", "New game day", "/gamedays", "Create game day", editing = false)

private fun editForm(gameDay: GameDaySummary) =
    GameDayFormView("Edit game day", "/gamedays/${gameDay.id}" to gameDay.title, "Edit game day", "/gamedays/${gameDay.id}/edit", "Save changes", editing = true)

context(ids: IdGenerator, clock: Clock, login: Login, config: AppConfig)
fun Route.gameDayRoutes(app: App) {
    get("/") {
        authenticated {
            val me = contextOf<CurrentPlayer>()
            val gameDays = with(GameDays) { app.gameDaysById.forPlayer(me.id) { zone -> clock.todayIn(zone) } }
            call.respondHtml { homePage(gameDays, clock.todayIn(DEFAULT_TIME_ZONE), bggLinked = app.bggAccounts.isLinked(me.id)) }
        }
    }

    get("/gamedays/new") {
        authenticated { call.respondHtml { gameDayFormPage(createForm) } }
    }

    post("/gamedays") {
        authenticated {
            val form = call.receiveParameters()
            val command = CreateGameDay(
                gameDay = GameDayId(ids.next()),
                host = contextOf<CurrentPlayer>().id,
                title = form["title"].orEmpty(),
                date = form.date(),
                place = form["place"].orEmpty(),
                timeZone = form["timeZone"] ?: DEFAULT_TIME_ZONE,
                today = clock.todayIn(form["timeZone"]),
            )
            when (val decision = app.commands.handle(CreateGameDayDecider, command)) {
                is Decision.Accepted -> redirect("/gamedays/${command.gameDay}")
                is Decision.Rejected -> respondFragment { gameDayForm(createForm, decision.reason.message(), form) }
            }
        }
    }

    get("/gamedays/{id}/edit") {
        authenticated {
            val gameDay = gameDayFromPath(app)?.takeIf { it.host == contextOf<CurrentPlayer>().id }
                ?: return@authenticated call.respond(HttpStatusCode.NotFound)
            val values = Parameters.build {
                append("title", gameDay.title)
                append("date", gameDay.date.toString())
                append("place", gameDay.place)
            }
            call.respondHtml { gameDayFormPage(editForm(gameDay), values) }
        }
    }

    post("/gamedays/{id}/edit") {
        authenticated {
            val gameDay = memberGameDay(app, contextOf<CurrentPlayer>()) ?: return@authenticated call.respond(HttpStatusCode.NotFound)
            val form = call.receiveParameters()
            val command = EditGameDay(
                gameDay = gameDay.id,
                editor = contextOf<CurrentPlayer>().id,
                title = form["title"].orEmpty(),
                date = form.date(),
                place = form["place"].orEmpty(),
                today = clock.todayIn(gameDay.timeZone),
            )
            when (val decision = app.commands.handle(EditGameDayDecider, command)) {
                is Decision.Accepted -> redirect("/gamedays/${gameDay.id}")
                is Decision.Rejected -> respondFragment { gameDayForm(editForm(gameDay), decision.reason.message(), form) }
            }
        }
    }

    get("/gamedays/{id}") {
        authenticated {
            val me = contextOf<CurrentPlayer>()
            val gameDay = memberGameDay(app, me) ?: return@authenticated call.respond(HttpStatusCode.NotFound)
            val shelf = app.shelves.of(gameDay.id).mapNotNull { entry -> app.catalog[entry.game]?.let { it to entry } }
            val hub = GameDayHub(
                gameDay = gameDay,
                names = login.names(gameDay.players),
                shelf = shelf,
                played = app.plays.on(gameDay.id).mapNotNull { log -> app.catalog[log.game]?.let { it to log } },
                next = nextStep(app, gameDay, me, shelf.map { it.first }),
                inviteLink = inviteLink(app, gameDay),
            )
            call.respondHtml { gameDayPage(hub) }
        }
    }

    post("/gamedays/{id}/invite") {
        authenticated {
            val gameDay = memberGameDay(app, contextOf<CurrentPlayer>()) ?: return@authenticated call.respond(HttpStatusCode.NotFound)
            when (val decision = app.commands.handle(RotateInviteLinkDecider, RotateInviteLink(gameDay.id, contextOf<CurrentPlayer>().id))) {
                is Decision.Accepted -> {
                    val rotated = app.gameDaysById.getValue(gameDay.id)
                    respondFragment { invite(rotated, inviteLink(app, rotated)) }
                }
                is Decision.Rejected -> respondFragment(HttpStatusCode.Forbidden) { errorNotice(decision.reason.message(), id = "invite") }
            }
        }
    }
}

/** Before the day: taste round, then top-up or favourites. On the day: the checkpoint. After: nothing. */
context(clock: Clock)
private suspend fun nextStep(app: App, gameDay: GameDaySummary, me: CurrentPlayer, shelf: List<gorefest.core.game.Game>): NextStep? {
    if (gameDay.ended) return null
    if (gameDay.date == clock.todayIn(gameDay.timeZone)) return NextStep.Checkpoint(checkpointLabel(gameDay, clock))
    val history = app.tastes.of(me.id)
    val answers = history.comparisons.size
    if (answers < TOP_UP_AFTER) return NextStep.TasteRound
    val uncertain = app.topUpWizard.uncertain(PlayerGames(history, app.collectionSync.collection(me.id)), shelf, clock.instant()).size
    return if (uncertain > 0) NextStep.TopUp(uncertain) else NextStep.Favourites(answers)
}

context(config: AppConfig)
private fun inviteLink(app: App, gameDay: GameDaySummary) = "${config.baseUrl}/i/${app.invites.token(gameDay)}"

private fun RoutingContext.gameDayFromPath(app: App): GameDaySummary? =
    call.parameters["id"]?.let { runCatching { GameDayId.parse(it) }.getOrNull() }?.let(app.gameDaysById::get)

private fun Parameters.date(): LocalDate = this["date"]?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.MIN
