package gorefest.shell.web

import gorefest.core.PlayerId
import gorefest.core.gameday.GameDaySummary
import gorefest.core.matching.Matcher
import gorefest.core.matching.Suggestions
import gorefest.core.matching.TablePlayer
import gorefest.core.taste.PlayerGames
import gorefest.core.taste.TasteHistory
import gorefest.shell.App
import gorefest.shell.auth.Login
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.server.html.respondHtml
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.InputType
import kotlinx.html.TagConsumer
import kotlinx.html.article
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.fieldSet
import kotlinx.html.form
import kotlinx.html.h2
import kotlinx.html.h3
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.legend
import kotlinx.html.p
import kotlinx.html.section
import kotlinx.html.span
import kotlin.uuid.Uuid

private val matcher = Matcher()

/** Time options: label to budget in minutes (null: any). */
private val TIMES = listOf("Any" to null, "45m" to 45, "90m" to 90, "2h" to 120, "3h+" to 240)

context(login: Login)
fun Route.tableRoutes(app: App) {
    suspend fun suggestionsFor(gameDay: GameDaySummary, params: Parameters, me: CurrentPlayer): Pair<Suggestions, Int> {
        val chosen = params.getAll("players")?.mapNotNull { runCatching { PlayerId(Uuid.parse(it)) }.getOrNull() }
            ?.filter { it in gameDay.players } ?: gameDay.players
        val budget = TIMES.firstOrNull { it.first == params["time"] }?.second
        val names = login.names(chosen)
        val table = chosen.map { id ->
            TablePlayer(id, names[id] ?: "Someone", PlayerGames(app.tastes.state[id] ?: TasteHistory(), app.collectionSync.collection(id)))
        }
        val shelf = app.shelves.state[gameDay.id].orEmpty().mapNotNull { entry -> app.catalog[entry.game]?.let { it to entry } }
        return matcher.suggest(table, shelf, budget, app.clock.instant(), speed = app.groupSpeed(chosen)) to table.size
    }

    get("/gamedays/{id}/table") {
        authenticated {
            val me = contextOf<CurrentPlayer>()
            val gameDay = memberGameDay(app, me) ?: return@authenticated call.respond(HttpStatusCode.NotFound)
            val names = login.names(gameDay.players)
            val (suggestions, size) = suggestionsFor(gameDay, call.parameters, me)
            val chosen = call.parameters.getAll("players")?.toSet()
            val time = call.parameters["time"] ?: "Any"
            call.respondHtml {
                page("Pick a game", appBar = { backLink("/gamedays/${gameDay.id}", gameDay.title); appBarTitle("${gameDay.title} · Pick a game") }) {
                    form(classes = "gf-section") {
                        id = "table-form"
                        hx("get", "/gamedays/${gameDay.id}/suggestions")
                        hx("trigger", "change")
                        hx("target", "#suggestions")
                        hx("swap", "outerHTML")
                        h2("gf-h2") { +"Who's at the table?" }
                        fieldSet("gf-chips") {
                            attributes["style"] = "border: 0; padding: 0; margin: 0"
                            legend("gf-vh") { +"Players" }
                            gameDay.players.forEach { id ->
                                label("gf-chip") {
                                    input(InputType.checkBox, name = "players") {
                                        value = id.toString()
                                        checked = chosen == null || id.toString() in chosen
                                    }
                                    avatar(id, names[id], size = "gf-avatar--sm")
                                    span("gf-chip__check") { attributes["aria-hidden"] = "true"; icon("check") }
                                    +(names[id] ?: "Unknown player")
                                }
                            }
                        }
                        fieldSet("gf-seg") {
                            attributes["style"] = "border: 1px solid var(--line); margin: 0"
                            legend("gf-vh") { +"Time" }
                            TIMES.forEach { (label, _) ->
                                label {
                                    input(InputType.radio, name = "time") {
                                        id = "time-$label"
                                        value = label
                                        checked = label == time
                                    }
                                    +label
                                }
                            }
                        }
                    }
                    consumer.suggestions(suggestions, size)
                }
            }
        }
    }

    get("/gamedays/{id}/suggestions") {
        authenticated {
            val me = contextOf<CurrentPlayer>()
            val gameDay = memberGameDay(app, me) ?: return@authenticated call.respond(HttpStatusCode.NotFound)
            val (suggestions, size) = suggestionsFor(gameDay, call.parameters, me)
            respondFragment { suggestions(suggestions, size) }
        }
    }
}

private fun TagConsumer<*>.suggestions(suggestions: Suggestions, tableSize: Int) = section("gf-section") {
    id = "suggestions"
    attributes["aria-live"] = "polite"
    when (suggestions) {
        is Suggestions.Found -> {
            h2("gf-h2") { +"Play this" }
            suggestions.top.forEachIndexed { i, s ->
                article(if (i == 0) "gf-game gf-game--large" else "gf-game") {
                    span("gf-game__rank") { +"${i + 1}" }
                    art(s.game, if (i == 0) "gf-art--wide" else "")
                    div("gf-game__body") {
                        h3("gf-game__name") { +s.game.name }
                        p("gf-game__meta") { icon("clock"); +"~${s.minutes} min for ${if (i == 0) "the $tableSize of you" else "$tableSize"}" }
                        p("gf-game__reason") { icon("spark"); +s.reason }
                    }
                }
            }
        }
        is Suggestions.NothingFits -> emptyState(suggestions.title, suggestions.text) {
            val relax = suggestions.relaxTo
            if (relax != null) {
                val label = TIMES.firstOrNull { it.second == relax }?.first ?: "Any"
                div("gf-stack") {
                    attributes["style"] = "width: 100%"
                    relaxButton(label, "Try ${Matcher.duration(relax)}: ${suggestions.fitsThen} ${if (suggestions.fitsThen == 1) "game fits" else "games fit"}")
                }
            }
        }
    }
}

/** Picks that time option in the form above, which re-asks for suggestions. */
private fun FlowContent.relaxButton(timeLabel: String, text: String) = button(type = ButtonType.button, classes = "gf-btn gf-btn--primary gf-btn--block") {
    hyperscript("on click set #time-$timeLabel.checked to true then send change to #table-form")
    +text
}
