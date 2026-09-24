package gorefest.shell.web

import gorefest.core.Basis
import gorefest.core.BggId
import gorefest.core.ComparisonRecorded
import gorefest.core.ComparisonResult
import gorefest.core.GamePlayStatusDeclared
import gorefest.core.PlayLogged
import gorefest.core.PlayerId
import gorefest.core.bgg.Game
import gorefest.core.eventsourcing.Decision
import gorefest.core.gameday.GameDaySummary
import gorefest.core.gameday.LogPlayDecider
import gorefest.core.taste.DeclarePlayStatusDecider
import gorefest.core.taste.PlayerGames
import gorefest.core.taste.RecordComparisonDecider
import gorefest.core.taste.TasteHistory
import gorefest.core.taste.Wizard
import gorefest.shell.App
import gorefest.shell.auth.Login
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.server.html.respondHtml
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.FormMethod
import kotlinx.html.InputType
import kotlinx.html.TagConsumer
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.fieldSet
import kotlinx.html.form
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.legend
import kotlinx.html.p
import kotlinx.html.role
import kotlinx.html.span
import kotlinx.html.strong
import java.time.ZoneId
import kotlin.uuid.Uuid

/** Duration options: label to minutes. */
private val DURATIONS = listOf("15m" to 15, "30m" to 30, "45m" to 45, "1h" to 60, "90m" to 90, "2h" to 120, "3h+" to 180)

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
            val names = login.names(gameDay.players)
            call.respondHtml {
                page(
                    "What did you play?",
                    appBar = {
                        backLink("/gamedays/${gameDay.id}", gameDay.title)
                        appBarTitle(gameDay.title)
                    },
                    dock = {
                        button(type = ButtonType.submit, classes = "gf-btn gf-btn--primary gf-btn--lg gf-btn--block") {
                            attributes["form"] = "log-form"
                            span("gf-spin") { attributes["aria-hidden"] = "true" }
                            +"Next"
                        }
                    },
                ) {
                    div("gf-stack") {
                        p("gf-kicker") { +checkpointLabel(gameDay, app.clock) }
                        h1("gf-h1") { +"What did you play?" }
                    }
                    consumer.logForm(gameDay, logChoices(app, gameDay), names, me, Parameters.Empty, error = null)
                }
            }
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
            val names = login.names(gameDay.players)
            if (game == null) {
                return@authenticated respondFragment { logForm(gameDay, logChoices(app, gameDay), names, me, form, "Pick the game you played") }
            }
            val log = PlayLogged(gameDay.id, game.id, players, minutes, me.id, app.clock.instant())
            when (val decision = app.commands.handle(LogPlayDecider, log)) {
                is Decision.Accepted -> redirect("/gamedays/${gameDay.id}/log/compare?game=${game.id}&minutes=$minutes&${players.joinToString("&") { "players=$it" }}")
                is Decision.Rejected -> respondFragment { logForm(gameDay, logChoices(app, gameDay), names, me, form, decision.reason) }
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
            val player = PlayerGames(app.tastes.state[me.id] ?: TasteHistory(), app.collectionSync.collection(me.id))
            val candidates = (player.history.played.filterValues { it }.keys + player.owned + player.history.comparedGames).mapNotNull(app.catalog::get)
            val other = app.wizard.partner(player, game.id, candidates, app.clock.instant()) ?: return@authenticated redirect(tableLink)
            call.respondHtml {
                page("One quick question", appBar = { appBarTitle(gameDay.title); a(tableLink, classes = "gf-btn gf-btn--ghost gf-btn--sm") { attributes["style"] = "margin-left: auto"; +"Skip" } }, mainClasses = "gf-wizard") {
                    div("gf-notice gf-notice--good") {
                        icon("check")
                        p { +"Logged "; strong { +game.name }; +": ${players.size} ${if (players.size == 1) "player" else "players"}, ${call.parameters["minutes"]} min." }
                    }
                    div("gf-stack") {
                        p("gf-kicker") { +"One quick question" }
                        h1("gf-h1") { +"Which did you enjoy more?" }
                    }
                    form(action = "/gamedays/${gameDay.id}/log/compare", method = FormMethod.post, classes = "gf-wizard__form") {
                        input(InputType.hidden, name = "a") { value = game.id.toString() }
                        input(InputType.hidden, name = "b") { value = other.id.toString() }
                        input(InputType.hidden, name = "next") { value = tableLink }
                        div("gf-duel gf-deal") {
                            pick(game, "A")
                            span("gf-duel__or") { attributes["aria-hidden"] = "true"; +"or" }
                            pick(other, "B")
                        }
                        div("gf-answers") {
                            answer("UNPLAYED", "Haven't played", "unplayed")
                            answer("BOTH_MEH", "Both meh", "meh")
                            answer("BOTH_GREAT", "Both great", "star", great = true)
                        }
                    }
                }
            }
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
    val logged = app.plays.state[gameDay.id].orEmpty().map { it.game }.toSet()
    val shelf = app.shelves.state[gameDay.id].orEmpty().mapNotNull { app.catalog[it.game] }
    return shelf.sortedBy { it.id in logged }
}

private fun TagConsumer<*>.logForm(
    gameDay: GameDaySummary, games: List<Game>, names: Map<PlayerId, String>, me: CurrentPlayer, values: Parameters, error: String?,
) = form(action = "/gamedays/${gameDay.id}/log", method = FormMethod.post, classes = "gf-stack") {
    id = "log-form"
    hx("post", "/gamedays/${gameDay.id}/log")
    hx("swap", "outerHTML")
    if (error != null) div("gf-notice gf-notice--error") { role = "alert"; icon("alert"); p { +error } }
    val chosenGame = values["game"] ?: games.firstOrNull()?.id?.toString()
    if (games.isEmpty()) p("gf-muted") { +"Nothing on the shelf yet. Bring the game first, then log it." }
    div("gf-tiles") {
        games.forEach { game ->
            label("gf-tile") {
                input(InputType.radio, name = "game") { value = game.id.toString(); checked = value == chosenGame }
                art(game)
                span("gf-tile__name") { +game.name }
            }
        }
    }
    div("gf-section") {
        h2("gf-h2") { +"Who played?" }
        val chosen = values.getAll("players")?.toSet() ?: setOf(me.id.toString())
        div("gf-chips") {
            gameDay.players.forEach { id ->
                label("gf-chip") {
                    input(InputType.checkBox, name = "players") { value = id.toString(); checked = id.toString() in chosen }
                    avatar(id, names[id], size = "gf-avatar--sm")
                    span("gf-chip__check") { attributes["aria-hidden"] = "true"; icon("check") }
                    +(names[id] ?: "Unknown player")
                }
            }
        }
    }
    div("gf-section") {
        h2("gf-h2") { +"Roughly how long?" }
        val duration = values["duration"] ?: "45m"
        fieldSet("gf-seg") {
            attributes["style"] = "border: 1px solid var(--line); margin: 0"
            legend("gf-vh") { +"Duration" }
            DURATIONS.forEach { (label, _) ->
                label { input(InputType.radio, name = "duration") { value = label; checked = label == duration }; +label }
            }
        }
    }
}

private fun FlowContent.pick(game: Game, key: String) = button(type = ButtonType.submit, classes = "gf-pick") {
    name = "answer"
    value = key
    span("gf-pick__key") { attributes["aria-hidden"] = "true"; +key }
    art(game)
    span("gf-pick__name") { +game.name }
    span("gf-pick__facts") { +game.facts }
    span("gf-spin") { attributes["aria-hidden"] = "true" }
}

private fun FlowContent.answer(value: String, label: String, iconName: String, great: Boolean = false) =
    button(type = ButtonType.submit, classes = if (great) "gf-answer gf-answer--great" else "gf-answer") {
        name = "answer"
        this.value = value
        icon(iconName)
        +label
    }
