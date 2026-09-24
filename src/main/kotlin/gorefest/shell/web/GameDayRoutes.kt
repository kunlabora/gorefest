package gorefest.shell.web

import gorefest.core.GameDayId
import gorefest.core.eventsourcing.Decision
import gorefest.core.gameday.CreateGameDay
import gorefest.core.gameday.CreateGameDayDecider
import gorefest.core.gameday.GameDaySummary
import gorefest.core.gameday.JoinGameDay
import gorefest.core.gameday.JoinGameDayDecider
import gorefest.shell.App
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.server.html.respondHtml
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
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
import kotlinx.html.stream.createHTML
import kotlinx.html.ul
import kotlinx.html.InputType
import kotlinx.html.small
import java.time.LocalDate
import java.time.ZoneId
import io.ktor.http.ContentType
import io.ktor.server.response.respondText
import kotlinx.html.TagConsumer
import gorefest.shell.IdGenerator
import java.time.Clock

private const val DEFAULT_TIME_ZONE = "Europe/Brussels"

context(ids: IdGenerator, clock: Clock)
fun Route.gameDayRoutes(app: App) {
    get("/") {
        authenticated {
            val gameDays = app.gameDays.state.values.sortedBy { it.date }
            call.respondHtml {
                page("Game days") {
                    h1 { +"Game days" }
                    ul { gameDays.forEach { li { a("/gamedays/${it.id}") { +"${it.title} — ${it.date}" } } } }
                    h2 { +"Host a game day" }
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
            call.respondHtml {
                page(gameDay.title) {
                    a("/") { +"← all game days" }
                    h1 { +gameDay.title }
                    p { +"${gameDay.date} · ${gameDay.place}" }
                    h2 { +"Players" }
                    consumer.players(gameDay)
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
                is Decision.Accepted -> respondFragment(HttpStatusCode.OK) { players(app.gameDays.state.getValue(gameDayId)) }
                is Decision.Rejected -> respondFragment(HttpStatusCode.OK) { p { id = "players"; +decision.reason } }
            }
        }
    }
}

private fun RoutingContext.gameDayFromPath(app: App): GameDaySummary? =
    call.parameters["id"]?.let { runCatching { GameDayId.parse(it) }.getOrNull() }?.let(app.gameDays.state::get)

context(me: CurrentPlayer)
private fun TagConsumer<*>.players(gameDay: GameDaySummary) {
    div {
        id = "players"
        ul {
            gameDay.players.forEach { player ->
                li {
                    +player.toString().take(8)
                    if (player == gameDay.host) small { +" host" }
                    if (player == me.id) small { +" (you)" }
                }
            }
        }
        if (me.id !in gameDay.players && !gameDay.ended) {
            button {
                hx("post", "/gamedays/${gameDay.id}/join")
                hx("target", "#players")
                hx("swap", "outerHTML")
                hyperscript("on click add @aria-busy='true' to me")
                +"Join"
            }
        }
    }
}

private fun TagConsumer<*>.createGameDayForm(error: String? = null, values: Parameters = Parameters.Empty) {
    form(action = "/gamedays", method = kotlinx.html.FormMethod.post) {
        hx("post", "/gamedays")
        hx("swap", "outerHTML")
        if (error != null) p { +error }
        label { +"Title"; input(InputType.text, name = "title") { placeholder = "Gorefest #7"; required = true; value = values["title"].orEmpty() } }
        label { +"Date"; input(InputType.date, name = "date") { required = true; value = values["date"].orEmpty() } }
        label { +"Place"; input(InputType.text, name = "place") { value = values["place"].orEmpty() } }
        input(InputType.hidden, name = "timeZone") {
            value = DEFAULT_TIME_ZONE
            hyperscript("init set my value to Intl.DateTimeFormat().resolvedOptions().timeZone")
        }
        button { +"Create" }
    }
}

private fun zoneOrDefault(id: String?): ZoneId =
    id?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.of(DEFAULT_TIME_ZONE)

/** Full redirect for plain form posts, `HX-Redirect` for htmx requests. */
private suspend fun RoutingContext.redirect(to: String) {
    if (call.request.headers["HX-Request"] == "true") {
        call.response.header("HX-Redirect", to)
        call.respond(HttpStatusCode.NoContent)
    } else {
        call.respondRedirect(to)
    }
}

/** Renders a single top-level element for htmx to swap in. */
private suspend fun RoutingContext.respondFragment(status: HttpStatusCode, content: TagConsumer<String>.() -> Unit) {
    val html = createHTML().apply(content).finalize()
    call.respondText(html, ContentType.Text.Html, status)
}
