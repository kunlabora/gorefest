package gorefest.shell.web

import gorefest.core.GameDayId
import gorefest.core.eventsourcing.Decision
import gorefest.core.gameday.CreateGameDay
import gorefest.core.gameday.CreateGameDayDecider
import gorefest.core.gameday.GameDaySummary
import gorefest.core.gameday.JoinGameDay
import gorefest.core.gameday.JoinGameDayDecider
import gorefest.core.PlayerId
import gorefest.shell.App
import gorefest.shell.auth.Login
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.server.html.respondHtml
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.html.FlowContent
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.li
import kotlinx.html.p
import kotlinx.html.ul
import kotlinx.html.InputType
import kotlinx.html.DIV
import kotlinx.html.role
import kotlinx.html.section
import kotlinx.html.span
import kotlinx.html.style
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.html.TagConsumer
import gorefest.shell.IdGenerator
import java.time.Clock

private const val DEFAULT_TIME_ZONE = "Europe/Brussels"

context(ids: IdGenerator, clock: Clock, login: Login)
fun Route.gameDayRoutes(app: App) {
    get("/") {
        authenticated {
            val gameDays = app.gameDays.state.values.sortedBy { it.date }
            val today = LocalDate.now(clock.withZone(ZoneId.of(DEFAULT_TIME_ZONE)))
            call.respondHtml {
                page(
                    "Game days",
                    appBar = { appBarTitle("Gorefest"); logoutButton() },
                    dock = {
                        a("/gamedays/new", classes = "gf-btn gf-btn--primary gf-btn--lg gf-btn--block") { icon("plus"); +"Host a game day" }
                    },
                ) {
                    h1("gf-h1") { +"Your game days" }
                    if (gameDays.isEmpty()) {
                        emptyState("No game days yet", "Got an invite? Tap the link in your group chat. Or host one yourself.")
                    } else {
                        div("gf-stack") { gameDays.forEach { dayCard(it, today) } }
                    }
                }
            }
        }
    }

    get("/gamedays/new") {
        authenticated {
            call.respondHtml {
                page("Host a game day", appBar = { backLink("/", "All game days"); appBarTitle("New game day") }) {
                    h1("gf-h1") { +"Host a game day" }
                    consumer.createGameDayForm()
                }
            }
        }
    }

    post("/gamedays") {
        authenticated {
            val form = call.receiveParameters()
            val command = CreateGameDay(
                gameDay = GameDayId(ids.next()),
                host = contextOf<CurrentPlayer>().id,
                title = form["title"].orEmpty(),
                date = form["date"]?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.MIN,
                place = form["place"].orEmpty(),
                timeZone = form["timeZone"] ?: DEFAULT_TIME_ZONE,
                today = LocalDate.now(clock.withZone(zoneOrDefault(form["timeZone"]))),
            )
            when (val decision = app.commands.handle(CreateGameDayDecider, command)) {
                is Decision.Accepted -> redirect("/gamedays/${command.gameDay}")
                is Decision.Rejected -> respondFragment(HttpStatusCode.OK) { createGameDayForm(decision.reason, form) }
            }
        }
    }

    get("/gamedays/{id}") {
        authenticated {
            val gameDay = gameDayFromPath(app) ?: return@authenticated call.respond(HttpStatusCode.NotFound)
            val names = login.names(gameDay.players)
            call.respondHtml {
                page(gameDay.title, appBar = { backLink("/", "All game days"); appBarTitle("Game days") }) {
                    div("gf-stack") {
                        p("gf-kicker") { +listOf(gameDay.date.long(), gameDay.place).filter { it.isNotBlank() }.joinToString(" · ") }
                        h1("gf-h1") { +gameDay.title }
                        if (gameDay.ended) span("gf-tag") { +"Ended" }
                    }
                    section("gf-section") {
                        h2("gf-h2") { +"Players" }
                        consumer.players(gameDay, names)
                    }
                }
            }
        }
    }

    post("/gamedays/{id}/join") {
        authenticated {
            val gameDayId = call.parameters["id"]?.let { runCatching { GameDayId.parse(it) }.getOrNull() }
                ?: return@authenticated call.respond(HttpStatusCode.NotFound)
            val command = JoinGameDay(gameDayId, contextOf<CurrentPlayer>().id)
            when (val decision = app.commands.handle(JoinGameDayDecider, command)) {
                is Decision.Accepted -> {
                    val gameDay = app.gameDays.state.getValue(gameDayId)
                    val names = login.names(gameDay.players)
                    respondFragment { players(gameDay, names) }
                }
                is Decision.Rejected -> respondFragment(HttpStatusCode.OK) {
                    div("gf-notice gf-notice--error") {
                        id = "players"
                        role = "alert"
                        icon("alert")
                        p { +decision.reason }
                    }
                }
            }
        }
    }
}

private fun RoutingContext.gameDayFromPath(app: App): GameDaySummary? =
    call.parameters["id"]?.let { runCatching { GameDayId.parse(it) }.getOrNull() }?.let(app.gameDays.state::get)

context(me: CurrentPlayer)
private fun TagConsumer<*>.players(gameDay: GameDaySummary, names: Map<PlayerId, String>) {
    div("gf-stack") {
        id = "players"
        if (gameDay.players.isNotEmpty()) ul("gf-list") {
            gameDay.players.forEach { player ->
                li("gf-row") {
                    // No name: the player never finished signing up, or was erased.
                    val name = names[player]
                    avatar(player, name)
                    span("gf-row__main") {
                        span("gf-row__title") { +(name ?: "Unknown player") }
                        if (player == me.id) span("gf-row__sub") { +"You" }
                    }
                    if (player == gameDay.host) span("gf-tag gf-tag--brand") { +"Host" }
                }
            }
        }
        if (me.id !in gameDay.players && !gameDay.ended) {
            button(classes = "gf-btn gf-btn--primary gf-btn--lg gf-btn--block") {
                hx("post", "/gamedays/${gameDay.id}/join")
                hx("target", "#players")
                hx("swap", "outerHTML")
                hyperscript("on click set @aria-busy to 'true'")
                span("gf-spin") { attributes["aria-hidden"] = "true" }
                +"Join ${gameDay.title}"
            }
        }
    }
}

private fun FlowContent.dayCard(gameDay: GameDaySummary, today: LocalDate) {
    a("/gamedays/${gameDay.id}", classes = "gf-daycard") {
        span("gf-date") {
            span("gf-date__month") { +if (gameDay.date == today) "Today" else gameDay.date.monthShort() }
            +" " // flex ignores it; screen readers and text extraction need it
            span("gf-date__day") { +gameDay.date.dayOfMonth.toString() }
        }
        span {
            style = "min-width: 0"
            span("gf-daycard__title") { +gameDay.title }
            span("gf-daycard__meta") {
                +listOf(gameDay.place, "${gameDay.players.size} going").filter { it.isNotBlank() }.joinToString(" · ")
            }
            if (gameDay.ended) span("gf-tag") { style = "margin-top: 6px"; +"Ended" }
        }
        span {
            style = "display: flex; transform: rotate(180deg); color: var(--ink-muted)"
            icon("back")
        }
    }
}

private fun TagConsumer<*>.createGameDayForm(error: String? = null, values: Parameters = Parameters.Empty) {
    form(action = "/gamedays", method = kotlinx.html.FormMethod.post, classes = "gf-section") {
        hx("post", "/gamedays")
        hx("swap", "outerHTML")
        if (error != null) div("gf-notice gf-notice--error") {
            role = "alert"
            icon("alert")
            p { +error }
        }
        field("title", "Title", hint = "Whatever your group calls it.") {
            input(InputType.text, name = "title", classes = "gf-input") { id = "title"; placeholder = "Gorefest #7"; required = true; value = values["title"].orEmpty() }
        }
        field("date", "Date") {
            input(InputType.date, name = "date", classes = "gf-input") { id = "date"; required = true; value = values["date"].orEmpty() }
        }
        field("place", "Where", hint = "Shown on the invite. Leave out the house number if you like.") {
            input(InputType.text, name = "place", classes = "gf-input") { id = "place"; value = values["place"].orEmpty() }
        }
        input(InputType.hidden, name = "timeZone") {
            value = DEFAULT_TIME_ZONE
            hyperscript("init set my value to Intl.DateTimeFormat().resolvedOptions().timeZone")
        }
        button(classes = "gf-btn gf-btn--primary gf-btn--lg gf-btn--block") {
            span("gf-spin") { attributes["aria-hidden"] = "true" }
            +"Create game day"
        }
    }
}

private fun FlowContent.field(id: String, label: String, hint: String? = null, input: DIV.() -> Unit) = div("gf-field") {
    label("gf-label") { htmlFor = id; +label }
    input()
    if (hint != null) p("gf-hint") { +hint }
}

private fun zoneOrDefault(id: String?): ZoneId =
    id?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.of(DEFAULT_TIME_ZONE)

